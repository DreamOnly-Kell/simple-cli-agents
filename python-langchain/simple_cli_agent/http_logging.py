"""
HTTP 线级日志：尽量还原 OpenAI 兼容接口的真实请求/响应，写入 JSONL 文件。

学习要点:
- Console 轨迹看的是 LangChain「逻辑层」messages/tool_calls；
  本模块看的是 httpx 发出去的 HTTP（更接近 wire）。
- 必须脱敏 Authorization 等头，避免 API key 落盘。
- 通过注入自定义 httpx.Client 给 ChatOpenAI/OpenAI SDK，从而拦截所有 chat/completions 调用。
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import httpx

# 这些请求头里通常带密钥，落盘前必须替换成占位符
_SENSITIVE_HEADER_KEYS = {
    "authorization",
    "api-key",
    "x-api-key",
    "proxy-authorization",
}


def redact_headers(headers: dict[str, Any] | httpx.Headers) -> dict[str, str]:
    """
    复制 headers 并把敏感字段打码。

    用途:
        写日志前统一调用，保证磁盘上不出现明文 token。

    规则:
        - Authorization: Bearer xxx → Bearer ***
        - 其它敏感 key → ***
        - 非敏感头原样字符串化保留（便于对照 Content-Type 等）
    """
    out: dict[str, str] = {}
    # httpx.Headers 与 dict 都支持 .items()
    items = headers.items() if hasattr(headers, "items") else []
    for key, value in items:
        lk = str(key).lower()
        if lk in _SENSITIVE_HEADER_KEYS:
            # Bearer 形态保留前缀，方便看出是哪种认证方式
            if lk == "authorization" and str(value).lower().startswith("bearer "):
                out[str(key)] = "Bearer ***"
            else:
                out[str(key)] = "***"
        else:
            out[str(key)] = str(value)
    return out


def _try_parse_body(raw: bytes | str | None) -> Any:
    """
    把 HTTP body 尽量解析成 JSON 对象；解析失败则退回字符串或长度信息。

    这样日志里 tools / messages / tool_calls 字段可直接展开阅读，
    而不只是一大坨转义字符串。
    """
    if raw is None:
        return None
    if isinstance(raw, bytes):
        if not raw:
            return None
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            # 二进制响应：只记长度，避免乱码污染日志
            return {"_raw_base64_len": len(raw)}
    else:
        text = raw
    text = text.strip()
    if not text:
        return None
    try:
        # OpenAI chat/completions 的 body 几乎总是 JSON
        return json.loads(text)
    except json.JSONDecodeError:
        # 非 JSON 时保留原文，便于排查
        return text


class HttpJsonlLogger:
    """
    JSONL（JSON Lines）追加写入器：一行 = 一次 HTTP 请求/响应往返。

    为什么用 JSONL:
        - 实现简单（open append）
        - 方便 `tail -f` / 按行 jq
        - 一次会话可以有多次 model 调用（agent 多跳 tool）
    """

    def __init__(self, path: str | Path) -> None:
        """
        指定日志文件路径；若父目录不存在则创建。

        参数 path: 例如 logs/http-session-20260712-153000.jsonl
        """
        self.path = Path(path)
        # 确保 logs/ 一类目录存在
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def write_exchange(
        self,
        request: dict[str, Any],
        response: dict[str, Any] | None = None,
        error: str | None = None,
    ) -> None:
        """
        追加一条「请求 + 可选响应」记录到 jsonl 文件。

        参数:
            request:  至少含 method/url/headers/body
            response: 至少含 status_code/headers/body（失败时可 None）
            error:    可选错误字符串

        注意:
            headers 会在写入时再次 redact，调用方即使传入明文也会被打码。
        """
        # 组装一条完整记录；ts 用 UTC 方便跨时区对照
        record: dict[str, Any] = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "direction": "exchange",
            "request": {
                **request,
                # 覆盖 headers：强制脱敏
                "headers": redact_headers(request.get("headers") or {}),
            },
        }
        if response is not None:
            record["response"] = {
                **response,
                "headers": redact_headers(response.get("headers") or {}),
            }
        if error:
            record["error"] = error
        # 追加模式；ensure_ascii=False 让中文可读；default=str 兜底不可序列化对象
        with self.path.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(record, ensure_ascii=False, default=str) + "\n")


def build_logging_http_client(
    log_path: str | Path,
    *,
    transport: httpx.BaseTransport | None = None,
    timeout: float = 120.0,
) -> httpx.Client:
    """
    构造带「请求/响应钩子」的同步 httpx.Client，供 ChatOpenAI(http_client=...) 注入。

    工作原理:
        1. request hook: 缓存请求 body 到 request.extensions
        2. response hook: 读完整响应 body，与请求一起写入 jsonl
        3. OpenAI SDK / LangChain 走这个 client 时，每次 chat/completions 都会被记录

    参数:
        log_path:  jsonl 输出路径
        transport: 可选自定义传输（测试用 MockTransport；生产默认真实网络）
        timeout:   默认 120s，本地大模型/慢网更稳

    返回:
        已配置 event_hooks 的 httpx.Client（调用方/SDK 负责 close，进程退出也会回收）。
    """
    logger = HttpJsonlLogger(log_path)

    def on_request(request: httpx.Request) -> None:
        """
        请求发出前触发：把 body 存进 extensions，供 on_response 配对使用。

        为何缓存 body:
            response 阶段仍能拿到 request 对象，但 body 流可能已被消费；
            提前读 request.content 可稳定拿到完整请求体。
        """
        body = request.content
        request.extensions["http_log_body"] = body

    def on_response(response: httpx.Response) -> None:
        """
        响应返回后触发：拼装 request+response 并落盘。

        response.read() 会把 body 载入内存；httpx 会缓存 content，
        后续 SDK 再读 response 内容通常仍可用。
        """
        request = response.request
        # 取 on_request 缓存的原始请求体
        req_body = request.extensions.get("http_log_body")
        # 确保响应体可读（非流式场景下完整 body 最适合学习）
        response.read()
        logger.write_exchange(
            request={
                "method": request.method,
                "url": str(request.url),
                "headers": dict(request.headers),
                "body": _try_parse_body(req_body),
            },
            response={
                "status_code": response.status_code,
                "headers": dict(response.headers),
                "body": _try_parse_body(response.content),
            },
        )

    # httpx event_hooks：按阶段挂回调列表
    event_hooks = {"request": [on_request], "response": [on_response]}
    kwargs: dict[str, Any] = {
        "event_hooks": event_hooks,
        "timeout": timeout,
    }
    # 测试可注入 MockTransport，不发真实网络
    if transport is not None:
        kwargs["transport"] = transport
    return httpx.Client(**kwargs)
