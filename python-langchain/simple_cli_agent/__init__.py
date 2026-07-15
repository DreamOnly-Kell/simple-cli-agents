"""
Simple CLI Agent 包根。

学习用极简终端 code agent：
OpenAI 兼容接口 + LangChain create_agent + 文件/终端 tools + 双通道可观测。

当前 tools：read_file / write_file / edit_file / grep / ls / run_command
（路径门禁 + shell 策略拦截；详见 docs/PROJECT_DNA.md）。

入口：cli.main / ``python -m simple_cli_agent``。
"""

__version__ = "0.1.0"
