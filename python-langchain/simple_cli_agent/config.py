"""
配置模块：从环境变量 / .env / CLI 覆盖项组装运行时配置。

优先级约定（学习 demo 常见做法）:
    CLI 显式传入的非 None 值 > 环境变量 / .env > 代码默认值

这样你可以:
    - 用 .env 记常用默认
    - 用 --base-url / --model 临时指向 LM Studio / 其它兼容端点
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

from dotenv import load_dotenv

from simple_cli_agent.tools.shell import DEFAULT_BLOCKED_PATTERNS


def _env_bool(name: str, default: bool) -> bool:
    """
    把环境变量解析成布尔值。

    真值集合: 1 / true / yes / on（大小写不敏感）
    变量不存在时返回 default。
    空字符串或其它值视为 False。
    """
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class AppConfig:
    """
    进程内不可变配置快照（frozen=True 防止运行中被误改）。

    字段说明:
        api_key:        OpenAI 兼容接口的密钥；本地服务可填任意占位如 EMPTY
        base_url:       API 根，如 https://api.openai.com/v1 或 http://localhost:1234/v1
        model:          模型名，原样传给 ChatOpenAI
        workspace_root: 文件工具沙箱根目录（绝对路径）
        verbose:        是否打印 console 逻辑轨迹
        verbose_full:   console 是否不截断长文本
        http_log:       是否写 HTTP jsonl
        http_log_dir:   HTTP 日志目录
        shell_blocked_patterns: 终端命令拦截子串列表（大小写不敏感）
        shell_timeout_seconds:  run_command 超时秒数
    """

    api_key: str
    base_url: str
    model: str
    workspace_root: Path
    verbose: bool
    verbose_full: bool
    http_log: bool
    http_log_dir: Path
    shell_blocked_patterns: tuple[str, ...] = field(default_factory=lambda: DEFAULT_BLOCKED_PATTERNS)
    shell_timeout_seconds: int = 30


def _parse_blocked_patterns(raw: str | None) -> tuple[str, ...]:
    """
    解析 SHELL_BLOCKED_PATTERNS。

    格式：用 ``||`` 分隔 pattern（因 pattern 本身可能含逗号）。
    空字符串表示使用默认列表；若要显式清空拦截，设为 ``-`` 或 ``none``。
    """
    if raw is None:
        return DEFAULT_BLOCKED_PATTERNS
    text = raw.strip()
    if not text:
        return DEFAULT_BLOCKED_PATTERNS
    if text.lower() in {"-", "none", "off"}:
        return ()
    parts = [p.strip() for p in text.split("||") if p.strip()]
    return tuple(parts) if parts else DEFAULT_BLOCKED_PATTERNS


def load_config(
    *,
    api_key: str | None = None,
    base_url: str | None = None,
    model: str | None = None,
    workspace: str | None = None,
    verbose: bool | None = None,
    verbose_full: bool | None = None,
    http_log: bool | None = None,
    http_log_dir: str | None = None,
    shell_blocked_patterns: tuple[str, ...] | list[str] | None = None,
    shell_timeout_seconds: int | None = None,
    dotenv_path: str | Path | None = None,
) -> AppConfig:
    """
    合并 .env、环境变量与可选 CLI 覆盖，返回 AppConfig。

    参数全部为 keyword-only，避免位置参数顺序搞混。
    某参数为 None 表示「不覆盖」，回退到 env/默认；CLI 层应把「用户没传」映射成 None。

    参数:
        api_key / base_url / model / workspace: 连接与沙箱相关
        verbose / verbose_full / http_log / http_log_dir: 可观测性开关
        dotenv_path: 指定 .env 路径；None 时由 python-dotenv 按默认规则查找
    """
    # 把 .env 加载进 os.environ（已存在的环境变量通常不会被覆盖，取决于 dotenv 版本默认）
    load_dotenv(dotenv_path)

    # CLI 优先：显式传入则用传入值，否则读环境变量
    key = api_key if api_key is not None else os.getenv("OPENAI_API_KEY", "")
    url = (
        base_url
        if base_url is not None
        else os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    )
    mdl = model if model is not None else os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    ws = workspace if workspace is not None else os.getenv("WORKSPACE_ROOT", ".")
    # 可观测性默认对学习友好：console 开、HTTP 日志开
    verb = verbose if verbose is not None else _env_bool("VERBOSE", True)
    verb_full = (
        verbose_full if verbose_full is not None else _env_bool("VERBOSE_FULL", False)
    )
    hlog = http_log if http_log is not None else _env_bool("HTTP_LOG", True)
    hdir = (
        http_log_dir
        if http_log_dir is not None
        else os.getenv("HTTP_LOG_DIR", "logs")
    )
    if shell_blocked_patterns is not None:
        blocked = tuple(shell_blocked_patterns)
    else:
        blocked = _parse_blocked_patterns(os.getenv("SHELL_BLOCKED_PATTERNS"))
    if shell_timeout_seconds is not None:
        timeout = int(shell_timeout_seconds)
    else:
        timeout = int(os.getenv("SHELL_TIMEOUT_SECONDS", "30"))

    return AppConfig(
        # strip 去掉 key 两端空白，避免 .env 复制时多空格
        api_key=key.strip(),
        # 去掉尾部 /，避免 base_url 拼接时出现 //v1
        base_url=url.rstrip("/"),
        model=mdl,
        # 沙箱与日志路径都 resolve 成绝对路径，行为与 cwd 无关
        workspace_root=Path(ws).expanduser().resolve(),
        verbose=verb,
        verbose_full=verb_full,
        http_log=hlog,
        http_log_dir=Path(hdir).expanduser().resolve(),
        shell_blocked_patterns=blocked,
        shell_timeout_seconds=max(1, timeout),
    )
