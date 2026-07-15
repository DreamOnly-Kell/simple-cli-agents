"""
组装 Agent 全部 tools：读 / 写 / edit / grep / ls / run_command。

学习要点：
- 文件类 tool 与 shell tool 共享同一 FileWorkspace 根，保证「路径语义」一致。
- make_file_tools 只产出读写与搜索；ls / run_command 在本模块追加，避免 files 依赖 shell。
- StructuredTool 的 name/description 会进入请求体 tools schema，影响模型何时调用。
"""

from __future__ import annotations

from langchain_core.tools import StructuredTool

from simple_cli_agent.tools.files import FileWorkspace, make_file_tools
from simple_cli_agent.tools.shell import CommandGuard, ShellRunner


def make_agent_tools(
    workspace: FileWorkspace,
    *,
    blocked_patterns: tuple[str, ...] | list[str],
    shell_timeout_seconds: int = 30,
) -> list[StructuredTool]:
    """
    返回完整 tool 列表：``[read_file, write_file, edit_file, grep, ls, run_command]``。

    参数:
        workspace: 共享沙箱根（read/write/edit/ls/grep 与 shell cwd 都基于它）
        blocked_patterns: 传给 CommandGuard 的拦截子串列表
        shell_timeout_seconds: run_command 超时秒数

    返回:
        可供 ``create_agent(..., tools=...)`` 直接使用的 StructuredTool 列表。
    """
    # 文件类先装：read/write/edit/grep（实现都在 FileWorkspace）
    tools = list(make_file_tools(workspace))

    # Guard/Runner 闭包捕获：每个 agent 一份策略，cwd = 同一 workspace.root
    guard = CommandGuard(blocked_patterns)
    runner = ShellRunner(
        workspace=workspace,
        guard=guard,
        timeout_seconds=shell_timeout_seconds,
    )

    def ls(path: str = ".") -> str:
        # 实现在 FileWorkspace.list_dir；注册放这里，与 run_command 同一组装入口
        return workspace.list_dir(path)

    def run_command(command: str) -> str:
        # 策略与超时在 ShellRunner 内处理；此处只做 tool 薄封装
        return runner.run(command)

    # StructuredTool.name 进入 tools schema，须与 SYSTEM_PROMPT / Java @Tool 对齐
    tools.extend(
        [
            StructuredTool.from_function(
                func=ls,
                name="ls",
                description=(
                    "List files and subdirectories under a path in the workspace "
                    "(non-recursive). Arg path: relative path, default '.'."
                ),
            ),
            StructuredTool.from_function(
                func=run_command,
                name="run_command",
                description=(
                    "Execute a shell command with working directory set to the workspace root. "
                    "High-risk commands are blocked by policy. Arg command: the shell command string."
                ),
            ),
        ]
    )
    return tools
