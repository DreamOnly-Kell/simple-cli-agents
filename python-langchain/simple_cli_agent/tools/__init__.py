"""
tools 子包：导出 Agent 可用的工具工厂与工作区类型。

当前只有文件读写；以后若加 shell/搜索等，可在此统一 re-export，
保持 `from simple_cli_agent.tools import ...` 的稳定导入面。
"""

from simple_cli_agent.tools.files import FileWorkspace, make_file_tools

# 明确公开 API，避免 from tools import * 时泄漏内部符号
__all__ = ["FileWorkspace", "make_file_tools"]
