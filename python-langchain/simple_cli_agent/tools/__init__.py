"""
tools 子包：导出 Agent 可用的工具工厂与工作区类型。

- files: FileWorkspace + make_file_tools（read/write/edit/grep）
- shell: CommandGuard / ShellRunner / 默认拦截列表
- agent_tools: make_agent_tools（在文件 tools 上追加 ls / run_command）
"""

from simple_cli_agent.tools.agent_tools import make_agent_tools
from simple_cli_agent.tools.files import FileWorkspace, make_file_tools
from simple_cli_agent.tools.shell import (
    DEFAULT_BLOCKED_PATTERNS,
    CommandGuard,
    ShellRunner,
)

__all__ = [
    "FileWorkspace",
    "make_file_tools",
    "make_agent_tools",
    "DEFAULT_BLOCKED_PATTERNS",
    "CommandGuard",
    "ShellRunner",
]
