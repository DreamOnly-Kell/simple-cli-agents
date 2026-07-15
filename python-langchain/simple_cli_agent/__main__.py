"""
模块入口：支持 `python -m simple_cli_agent` 启动。

Python 会在执行 -m 时加载本文件；我们把控制权交给 cli.main，
并用其返回值作为进程退出码。
"""

# 从 CLI 模块导入主函数
from simple_cli_agent.cli import main

# 将 main 的 int 退出码转成进程状态；非 0 表示启动/配置失败
raise SystemExit(main())
