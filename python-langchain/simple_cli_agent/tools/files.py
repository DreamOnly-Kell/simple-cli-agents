"""
文件工具模块：给 Agent 提供「读 / 写 / 局部编辑 / 列目录 / 文本搜索」能力。

学习要点：
- Tool 在 agent 循环里不是普通 Python 调用，而是模型通过 tool_calls 选中后由框架执行。
- 必须做路径沙箱，否则模型（或恶意 prompt）可能读写工作区外的文件。
- 出错时返回字符串错误信息（而不是抛异常），这样模型能在下一轮看到失败原因并调整。
- edit_file 用「唯一匹配」降低误改风险；grep 在应用层搜索，避免依赖 shell 再做二次门禁。
"""

from __future__ import annotations

import os
from pathlib import Path

# LangChain 的结构化工具：根据函数签名/描述生成 JSON Schema，供模型做 tool calling
from langchain_core.tools import StructuredTool


class FileWorkspace:
    """
    工作区路径守卫 + 文件读写 / 列目录 / 搜索实现。

    把所有文件操作限制在一个 root 目录下，避免 `../` 逃逸到系统其它位置。
    Agent 的 read/write/edit/ls/grep tool 都通过本类访问磁盘，而不是直接用 pathlib。
    """

    # 默认跳过的噪音目录名（basename 匹配，用于 grep 遍历）
    DEFAULT_SKIP_DIR_NAMES: frozenset[str] = frozenset(
        {
            ".venv",
            "venv",
            "target",
            "__pycache__",
            ".git",
            "node_modules",
            "logs",
            ".mypy_cache",
            ".pytest_cache",
            ".gradle",
            "dist",
            "build",
        }
    )

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
        # Path 的 `/`：若 path 已是绝对路径，左侧 root 会被丢弃，只保留 path。
        # 所以无论相对/绝对，都必须再 relative_to，不能只靠拼接。
        candidate = (self.root / path).resolve()
        try:
            # 成功 ⇒ candidate 在 root 之下（含 root 自身）；失败抛 ValueError
            candidate.relative_to(self.root)
        except ValueError:
            # 对 agent 不抛异常：Error 字符串进 tool 结果，模型下一跳可改 path
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

    def edit_file(self, path: str, old_str: str, new_str: str) -> str:
        """
        唯一匹配局部替换：old_str 在全文中必须恰好出现 1 次才写盘。

        为何要「唯一」:
            write_file 是整文件覆盖，误写代价大；edit 要求片段唯一，
            0 次（定位错）或多于 1 次（片段太宽）都拒绝，且保证文件字节不变。
        """
        # str.count("") 对任意文本都返回 len+1，必须先禁空 old_str
        if old_str is None or old_str == "":
            return "Error: old_str must be a non-empty string"
        # None 当删除该片段；空串 "" 同样表示删除
        if new_str is None:
            new_str = ""

        resolved = self.resolve(path)
        if isinstance(resolved, str):
            return resolved
        if not resolved.exists():
            return f"Error: file not found: {path}"
        if not resolved.is_file():
            return f"Error: not a file: {path}"

        try:
            text = resolved.read_text(encoding="utf-8")
        except OSError as exc:
            return f"Error: failed to read '{path}': {exc}"

        # str.count 为非重叠计数，与后面 replace(..., 1) 的语义一致
        count = text.count(old_str)
        if count == 0:
            return f"Error: old_str not found in '{path}' (0 matches); file unchanged"
        if count > 1:
            # 关键：多匹配时绝不写盘，避免只改第一处却让模型以为改完了
            return (
                f"Error: old_str found {count} times in '{path}' "
                f"(must be unique); file unchanged"
            )

        # count 已保证为 1；count=1 仍写出，防止以后改逻辑时误全量替换
        updated = text.replace(old_str, new_str, 1)
        try:
            resolved.write_text(updated, encoding="utf-8")
        except OSError as exc:
            return f"Error: failed to write '{path}': {exc}"
        return (
            f"Edited {path}: replaced 1 unique match "
            f"({len(old_str)} -> {len(new_str)} chars)"
        )

    def list_dir(self, path: str = ".") -> str:
        """
        非递归列目录。目录名加尾部 ``/``；目录优先、名称大小写不敏感排序。
        """
        # tool schema 里 path 常被模型省略或传空，归一成工作区根
        if path is None or str(path).strip() == "":
            path = "."
        resolved = self.resolve(path)
        if isinstance(resolved, str):
            return resolved
        if not resolved.exists():
            return f"Error: path not found: {path}"
        if not resolved.is_dir():
            return f"Error: not a directory: {path}"
        try:
            # key: (是否文件, 小写名) → 目录在前（False < True），同组字典序
            entries = sorted(
                resolved.iterdir(),
                key=lambda p: (not p.is_dir(), p.name.lower()),
            )
        except OSError as exc:
            return f"Error: failed to list '{path}': {exc}"
        if not entries:
            return f"(empty directory) {path}"
        lines: list[str] = []
        for entry in entries:
            # 尾部 / 仅展示用，不改变真实路径；模型靠它区分 dir/file
            name = entry.name + ("/" if entry.is_dir() else "")
            lines.append(name)
        return "\n".join(lines)

    def grep(
        self,
        pattern: str,
        path: str = ".",
        *,
        max_results: int = 50,
        max_line_chars: int = 200,
        skip_dir_names: frozenset[str] | set[str] | None = None,
    ) -> str:
        """
        在工作区内做文本搜索（子串匹配，大小写敏感）。

        设计选择（学习 demo）:
            - 应用层实现，不走 shell grep：路径仍受 resolve 沙箱约束
            - 简单子串而非正则：降低模型误用复杂度，语义与 str 包含一致
            - 跳过 .venv/target/__pycache__ 等噪音目录
            - 限制命中行数与单行 snippet 长度，防止 tool 结果撑爆上下文

        返回:
            多行 ``rel_path:line_no:snippet``；无命中时返回说明字符串；
            达到 max_results 时追加截断提示。
        """
        if pattern is None or pattern == "":
            return "Error: pattern must be a non-empty string"
        if path is None or str(path).strip() == "":
            path = "."
        # 0/负数会让截断逻辑无意义或死循环风险，抬到下限
        max_results = max(1, int(max_results))
        max_line_chars = max(20, int(max_line_chars))
        skip = self.DEFAULT_SKIP_DIR_NAMES if skip_dir_names is None else frozenset(skip_dir_names)

        # 与 read/edit 同一套路径门禁：禁止搜到工作区外
        resolved = self.resolve(path)
        if isinstance(resolved, str):
            return resolved
        if not resolved.exists():
            return f"Error: path not found: {path}"

        hits: list[str] = []
        truncated = False

        def consider_file(file_path: Path) -> None:
            """扫描单文件；命中写 hits；达 max_results 置 truncated 并停止本文件。"""
            nonlocal truncated
            if truncated:
                return
            try:
                # 二进制/坏编码：解码失败则静默跳过（不当 Error，避免打断整次 grep）
                text = file_path.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                return
            try:
                # as_posix：统一 /，结果格式跨平台一致
                rel = file_path.relative_to(self.root).as_posix()
            except ValueError:
                return
            # splitlines 去掉行尾换行；行号 1-based，对齐常见 grep 输出
            for i, line in enumerate(text.splitlines(), start=1):
                if pattern not in line:  # 大小写敏感；非正则
                    continue
                snippet = line.replace("\t", " ")
                if len(snippet) > max_line_chars:
                    snippet = snippet[:max_line_chars] + "..."
                hits.append(f"{rel}:{i}:{snippet}")
                if len(hits) >= max_results:
                    truncated = True
                    return

        if resolved.is_file():
            consider_file(resolved)
        elif resolved.is_dir():
            # 必须 topdown=True，才能在进入前改 dirnames 剪枝
            for dirpath, dirnames, filenames in os.walk(resolved, topdown=True):
                # 原地赋值：os.walk 会读被修改后的 dirnames，噪音目录整棵不进
                dirnames[:] = [d for d in dirnames if d not in skip]
                base = Path(dirpath)
                for name in filenames:
                    if truncated:
                        break
                    consider_file(base / name)
                if truncated:
                    break
        else:
            return f"Error: not a file or directory: {path}"

        if not hits:
            return f"No matches for pattern {pattern!r} under {path}"
        body = "\n".join(hits)
        if truncated:
            body += f"\n...[truncated at {max_results} matches]"
        return body


def make_file_tools(workspace: FileWorkspace) -> list[StructuredTool]:
    """
    把 FileWorkspace 的方法包装成 LangChain StructuredTool 列表。

    作用:
        - 闭包绑定同一个 workspace 实例，所有 tool 共享沙箱根
        - name + description 会进入发给模型的 tools schema，影响模型何时调用

    返回:
        [read_file, write_file, edit_file, grep]，供 create_agent(..., tools=...) 使用。
        （ls / run_command 由 agent_tools.make_agent_tools 追加。）
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

    def edit_file(path: str, old_str: str, new_str: str) -> str:
        """
        （暴露给模型的 tool 实现）唯一匹配局部编辑。

        参数:
            path: 相对路径
            old_str: 必须在文件中恰好出现一次的原文片段
            new_str: 替换内容（可为空串表示删除）
        """
        return workspace.edit_file(path, old_str, new_str)

    def grep(pattern: str, path: str = ".") -> str:
        """
        （暴露给模型的 tool 实现）工作区内文本搜索。

        参数:
            pattern: 非空子串（大小写敏感）
            path: 相对路径，默认 "."（整棵工作区树，已跳过噪音目录）
        """
        return workspace.grep(pattern, path)

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
        StructuredTool.from_function(
            func=edit_file,
            name="edit_file",
            description=(
                "Safely edit a file by replacing exactly one occurrence of old_str with new_str. "
                "Fails without modifying the file if old_str is missing or appears more than once. "
                "Args: path (relative), old_str, new_str."
            ),
        ),
        StructuredTool.from_function(
            func=grep,
            name="grep",
            description=(
                "Search for a text pattern under a workspace path (application-level, not shell). "
                "Returns lines as rel_path:line_no:snippet. Skips .venv/target/__pycache__/etc. "
                "Args: pattern (required), path (relative, default '.')."
            ),
        ),
    ]
