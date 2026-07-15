"""
文件工具模块：给 Agent 提供「读文件 / 写文件」能力。

学习要点：
- Tool 在 agent 循环里不是普通 Python 调用，而是模型通过 tool_calls 选中后由框架执行。
- 必须做路径沙箱，否则模型（或恶意 prompt）可能读写工作区外的文件。
- 出错时返回字符串错误信息（而不是抛异常），这样模型能在下一轮看到失败原因并调整。
"""

from __future__ import annotations

from pathlib import Path

# LangChain 的结构化工具：根据函数签名/描述生成 JSON Schema，供模型做 tool calling
from langchain_core.tools import StructuredTool


class FileWorkspace:
    """
    工作区路径守卫。

    把所有文件操作限制在一个 root 目录下，避免 `../` 逃逸到系统其它位置。
    Agent 的 read/write tool 都通过本类访问磁盘，而不是直接用 pathlib。
    """

    def __init__(self, root: str | Path) -> None:
        """
        初始化工作区根目录。

        参数:
            root: 允许读写的根路径（相对或绝对均可）。

        处理:
            - expanduser: 把 `~` 展开成用户主目录
            - resolve: 变成绝对路径并解析符号链接，后续沙箱判断更可靠
        """
        # 统一成绝对路径，避免相对路径在进程 cwd 变化时漂移
        self.root = Path(root).expanduser().resolve()

    def resolve(self, path: str) -> Path | str:
        """
        将用户/模型给出的 path 解析为工作区内的绝对路径。

        返回值有两种形态（刻意用 union，方便调用方分支）:
            - Path: 路径合法且落在 root 内
            - str:  错误信息（路径逃逸），调用方应直接返回给模型

        沙箱原理:
            先拼 root/path 再 resolve，再要求结果 relative_to(root) 成功。
            若 path 含 `..` 或绝对路径指向 root 外，relative_to 会抛 ValueError。
        """
        # 注意：若 path 是绝对路径，Path 的 `/` 运算会忽略左侧 root，只保留 path
        # 因此后面的 relative_to 检查仍然必要
        candidate = (self.root / path).resolve()
        try:
            # 能 relative_to 成功 ⇒ candidate 在 self.root 之下（含 root 自身）
            candidate.relative_to(self.root)
        except ValueError:
            # 逃逸：不抛异常给 agent 框架，返回可读错误字符串
            return f"Error: path escape denied for '{path}' (must stay under {self.root})"
        return candidate

    def read_file(self, path: str) -> str:
        """
        读取工作区内的文本文件内容。

        流程:
            1. resolve 做沙箱校验
            2. 检查存在性、是否为普通文件
            3. UTF-8 读入全文
            4. 超大文件做软截断，避免把超长内容塞进模型上下文

        返回:
            成功时为文件正文；失败时为以 "Error:" 开头的说明（给 LLM 看）。
        """
        # 先做路径守卫
        resolved = self.resolve(path)
        # resolve 失败时返回的是 str 错误信息
        if isinstance(resolved, str):
            return resolved
        if not resolved.exists():
            return f"Error: file not found: {path}"
        if not resolved.is_file():
            # 例如 path 指向目录
            return f"Error: not a file: {path}"
        try:
            # 统一按 UTF-8 文本处理（学习 demo 不处理二进制）
            text = resolved.read_text(encoding="utf-8")
        except OSError as exc:
            # 权限不足、IO 错误等
            return f"Error: failed to read '{path}': {exc}"
        # 防止单文件过大占满上下文窗口
        max_chars = 100_000
        if len(text) > max_chars:
            return (
                text[:max_chars]
                + f"\n\n...[truncated, total {len(text)} chars, showing first {max_chars}]"
            )
        return text

    def write_file(self, path: str, content: str) -> str:
        """
        在工作区内创建或覆盖写入文本文件。

        语义（v1 刻意极简）:
            - 整文件覆盖，不做 diff/patch
            - 父目录不存在时自动创建

        返回:
            成功时的简短确认（路径 + 写入字符数）；失败时 Error 字符串。
        """
        resolved = self.resolve(path)
        if isinstance(resolved, str):
            return resolved
        try:
            # parents=True: 递归创建中间目录；exist_ok: 已存在不报错
            resolved.parent.mkdir(parents=True, exist_ok=True)
            # 覆盖写；学习 demo 不做备份
            resolved.write_text(content, encoding="utf-8")
        except OSError as exc:
            return f"Error: failed to write '{path}': {exc}"
        # 返回给模型的「工具结果」，模型会据此组织自然语言回复
        return f"Wrote {len(content)} chars to {path}"


def make_file_tools(workspace: FileWorkspace) -> list[StructuredTool]:
    """
    把 FileWorkspace 的方法包装成 LangChain StructuredTool 列表。

    作用:
        - 闭包绑定同一个 workspace 实例，所有 tool 共享沙箱根
        - name + description 会进入发给模型的 tools schema，影响模型何时调用

    返回:
        [read_file_tool, write_file_tool]，供 create_agent(..., tools=...) 使用。
    """

    def read_file(path: str) -> str:
        """
        （暴露给模型的 tool 实现）读取工作区文件。

        参数 path: 相对工作区根的路径。description 会进 schema，模型靠它理解参数。
        """
        return workspace.read_file(path)

    def write_file(path: str, content: str) -> str:
        """
        （暴露给模型的 tool 实现）写入/覆盖工作区文件。

        参数:
            path: 相对路径
            content: 完整文件正文（覆盖语义）
        """
        return workspace.write_file(path, content)

    return [
        # from_function 会根据签名推断参数类型，并附上 description 给模型
        StructuredTool.from_function(
            func=read_file,
            name="read_file",  # 模型 tool_calls 里出现的名字
            description=(
                "Read the full text content of a file inside the workspace. "
                "Arg path: relative path from workspace root."
            ),
        ),
        StructuredTool.from_function(
            func=write_file,
            name="write_file",
            description=(
                "Create or overwrite a text file inside the workspace. "
                "Args: path (relative), content (full file text to write)."
            ),
        ),
    ]
