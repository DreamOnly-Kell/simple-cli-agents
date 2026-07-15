"""
模型构造：创建指向「OpenAI 兼容 Chat Completions」端点的 ChatOpenAI。

设计取舍:
    - 只支持 OpenAI 兼容接口（改 base_url 即可打 DeepSeek / LM Studio / Ollama 兼容端等）
    - 关闭 streaming，保证 HTTP 日志里是完整 request/response body
    - 可选注入带日志的 httpx.Client，实现线级可观测
"""

from __future__ import annotations

from datetime import datetime

from langchain_openai import ChatOpenAI

from simple_cli_agent.config import AppConfig
from simple_cli_agent.http_logging import build_logging_http_client


def build_chat_model(config: AppConfig) -> ChatOpenAI:
    """
    根据 AppConfig 构建 ChatOpenAI 实例。

    步骤:
        1. 填入 model / api_key / base_url 等基础参数
        2. 固定 streaming=False、temperature=0（tool calling 更稳、日志更好读）
        3. 若开启 http_log：创建 session 级 jsonl 文件，并注入 http_client

    返回:
        可直接交给 create_agent(model, ...) 的聊天模型对象。
    """
    # 组装传给 ChatOpenAI 的关键字参数（后面可能追加 http_client）
    kwargs: dict = {
        "model": config.model,
        # 部分本地服务不校验 key，但仍要求非空字段；EMPTY 作占位
        "api_key": config.api_key or "EMPTY",
        # OpenAI 兼容服务的根 URL（含 /v1）
        "base_url": config.base_url,
        # v1 不做流式：一次响应完整 body，HTTP 日志更好对照
        "streaming": False,
        # 温度 0：减少 tool 参数乱填，利于学习复现
        "temperature": 0,
        # 禁用 langchain-openai 默认 TCP keepalive 注入。
        # 否则它会替换 httpx transport，并在启动时打出一大段
        # "injected a custom httpx transport..." 警告（看起来像报错）。
        "http_socket_options": (),
    }
    if config.http_log:
        # 确保日志目录存在
        config.http_log_dir.mkdir(parents=True, exist_ok=True)
        # 每次进程启动一个新 session 文件，避免多会话混在同一文件难读
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        log_path = config.http_log_dir / f"http-session-{stamp}.jsonl"
        # 关键：把带 hooks 的 httpx.Client 交给 ChatOpenAI / OpenAI SDK
        kwargs["http_client"] = build_logging_http_client(log_path)
        # 启动时打印路径，方便用户立刻去 tail 日志
        print(f"[http-log] writing exchanges to {log_path}")
    return ChatOpenAI(**kwargs)
