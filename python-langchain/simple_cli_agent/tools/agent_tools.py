"""
组装 Agent 全部 tools：读/写/ls/run_command。
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
    返回 [read_file, write_file, ls, run_command]。

    ls / run_command 与文件工具共享同一 FileWorkspace 根目录。
    """
    tools = list(make_file_tools(workspace))
    guard = CommandGuard(blocked_patterns)
    runner = ShellRunner(
        workspace=workspace,
        guard=guard,
        timeout_seconds=shell_timeout_seconds,
    )

    def ls(path: str = ".") -> str:
        """List files and directories under a workspace path (non-recursive)."""
        return workspace.list_dir(path)

    def run_command(command: str) -> str:
        """
        Run a shell command with cwd=workspace root.
        Dangerous commands may be blocked by configured policy.
        """
        return runner.run(command)

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
