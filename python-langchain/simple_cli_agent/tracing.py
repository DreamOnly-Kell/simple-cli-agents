"""
Console 逻辑轨迹：通过 LangChain Callback 打印「模型输入/输出」和「工具调用」。

与 http_logging 的分工:
    - 本模块: 框架视角的 messages、tool_calls、tool 结果（学 agent 循环）
    - http_logging: 真实 HTTP JSON（学协议字段）

Callback 机制简述:
    agent.invoke(..., config={"callbacks": [handler]}) 时，
    框架在 chat model / tool 生命周期调用 handler 的 on_* 方法。
"""

from __future__ import annotations

import json
from typing import Any

from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.messages import BaseMessage
from langchain_core.outputs import LLMResult


def _truncate(text: str, limit: int, full: bool) -> str:
    """
    按字符数截断长文本，避免 console 被刷屏。

    full=True 时不截断（对应 VERBOSE_FULL / --verbose-full）。
    截断时附带总长度提示，便于知道被省略了多少。
    """
    if full or len(text) <= limit:
        return text
    return text[:limit] + f"...[{len(text)} chars total]"


def _fmt(obj: Any, limit: int, full: bool) -> str:
    """
    把任意对象格式化成可读字符串（优先 JSON），再套用截断。

    default=str: 遇到不可 JSON 序列化的对象时转成 str，避免打印失败。
    """
    try:
        text = json.dumps(obj, ensure_ascii=False, default=str, indent=2)
    except TypeError:
        text = repr(obj)
    return _truncate(text, limit, full)


class ConsoleTraceHandler(BaseCallbackHandler):
    """
    学习用控制台追踪器。

    继承 BaseCallbackHandler 后，只需覆盖关心的 on_* 钩子。
    未覆盖的钩子走默认空实现，不影响 agent 运行。
    """

    def __init__(self, *, verbose_full: bool = False, char_limit: int = 2000) -> None:
        """
        参数:
            verbose_full: True 时 messages/结果全文打印
            char_limit:   非 full 模式下单字段最大字符数
        """
        super().__init__()
        self.verbose_full = verbose_full
        self.char_limit = char_limit
        # 用户每一轮输入对应一个 turn 编号（由 begin_turn 递增）
        self.turn = 0
        # 避免 on_chat_model_end 与 on_llm_end 对同一次调用各打印一遍
        self._printed_llm_end_ids: set[str] = set()

    def on_chain_start(
        self,
        serialized: dict[str, Any],
        inputs: dict[str, Any],
        **kwargs: Any,
    ) -> None:
        """
        任意 chain/graph 开始时的回调。

        当前有意留空（pass）：turn 边界由 CLI 在用户输入后调用 begin_turn()，
        比依赖 chain 名称更稳定（LangGraph 内部 chain 名可能变化）。
        """
        # 保留钩子位置，便于以后按 name 过滤顶层 agent 运行
        name = (serialized or {}).get("name") or ""
        if name in {"LangGraph", "agent"} or kwargs.get("run_id"):
            pass

    def begin_turn(self) -> None:
        """
        标记「用户新的一轮对话」开始。

        由 CLI 在 agent.invoke 之前调用，打印分隔线，方便对照多跳 tool 属于同一 turn。
        """
        self.turn += 1
        print(f"\n──────── turn {self.turn} ────────")

    def on_chat_model_start(
        self,
        serialized: dict[str, Any],
        messages: list[list[BaseMessage]],
        **kwargs: Any,
    ) -> None:
        """
        即将调用 Chat Model 时触发（逻辑层「请求」）。

        参数要点:
            messages: 二维列表，外层 batch、内层是发给模型的消息序列
            kwargs['invocation_params']: 可能含 tools schema 等调用参数

        打印内容对应设计里的 `>>> LLM request`。
        """
        print(">>> LLM request")
        # 压平成 [{type, content}, ...]，方便人眼扫
        flat: list[dict[str, Any]] = []
        for batch in messages:
            for m in batch:
                flat.append(
                    {
                        # human / ai / system / tool 等
                        "type": m.type,
                        "content": _truncate(str(m.content), self.char_limit, self.verbose_full),
                    }
                )
        print(f"  messages: {_fmt(flat, self.char_limit, self.verbose_full)}")
        inv = kwargs.get("invocation_params") or {}
        tools = inv.get("tools")
        if tools:
            # tools 可能是 OpenAI function schema dict，也可能是带 name 的对象
            names = []
            for t in tools:
                if isinstance(t, dict):
                    names.append(t.get("function", {}).get("name") or t.get("name") or t)
                else:
                    names.append(getattr(t, "name", str(t)))
            print(f"  tools: {names}")

    def _print_llm_result(self, response: LLMResult) -> None:
        """把 LLMResult 打印成统一的 <<< LLM response 块。"""
        print("<<< LLM response")
        # generations 结构: List[List[ChatGeneration]]
        for gen_list in response.generations:
            for gen in gen_list:
                msg = getattr(gen, "message", None)
                if msg is not None:
                    content = getattr(msg, "content", None)
                    # 没有 tool_calls 时用 []，明确「本步结束还是继续调工具」
                    tool_calls = getattr(msg, "tool_calls", None) or []
                    print(
                        f"  content: {_truncate(str(content), self.char_limit, self.verbose_full)}"
                    )
                    if tool_calls:
                        print(
                            f"  tool_calls: {_fmt(tool_calls, self.char_limit, self.verbose_full)}"
                        )
                    else:
                        print("  tool_calls: []")
                else:
                    # 极少数路径只有 text 没有 message 对象
                    print(
                        f"  text: {_truncate(str(gen.text), self.char_limit, self.verbose_full)}"
                    )

    def _mark_and_print_llm_end(self, response: LLMResult, **kwargs: Any) -> None:
        """按 run_id 去重后打印 LLM 响应。"""
        run_id = str(kwargs.get("run_id") or "")
        if run_id and run_id in self._printed_llm_end_ids:
            return
        if run_id:
            self._printed_llm_end_ids.add(run_id)
            # 集合别无限涨：只保留最近若干 id
            if len(self._printed_llm_end_ids) > 64:
                self._printed_llm_end_ids = set(list(self._printed_llm_end_ids)[-32:])
        self._print_llm_result(response)

    def on_chat_model_end(self, response: LLMResult, **kwargs: Any) -> None:
        """
        Chat Model 返回后触发（逻辑层「响应」）。

        重点观察:
            - content: 自然语言正文（可能为空，若本步只发 tool_calls）
            - tool_calls: 模型要求执行的工具列表（agent 循环的核心信号）

        打印内容对应 `<<< LLM response`。
        """
        self._mark_and_print_llm_end(response, **kwargs)

    def on_llm_end(self, response: LLMResult, **kwargs: Any) -> None:
        """
        部分调用路径只触发 on_llm_end 而不触发 on_chat_model_end。
        作为兜底，保证 console 仍能看到模型响应。
        """
        self._mark_and_print_llm_end(response, **kwargs)

    def on_tool_start(
        self,
        serialized: dict[str, Any],
        input_str: str,
        **kwargs: Any,
    ) -> None:
        """
        框架开始执行某个 tool 时触发。

        input_str: 工具入参的字符串形式（通常是 JSON）。
        与模型返回的 tool_calls 参数对照，可验证「模型要调的」和「实际执行的」是否一致。
        """
        name = (serialized or {}).get("name") or kwargs.get("name") or "tool"
        print(f">>> tool {name}")
        print(f"  args: {_truncate(str(input_str), self.char_limit, self.verbose_full)}")

    def on_tool_end(self, output: Any, **kwargs: Any) -> None:
        """
        tool 执行完成、结果即将回灌给模型时触发。

        打印长度 + 截断正文，对应设计里的 result 行。
        下一轮 on_chat_model_start 的 messages 里通常会出现 type=tool 的消息。
        """
        # 框架有时传入 ToolMessage，优先取其 content，避免 content='...' 整段 repr
        if hasattr(output, "content"):
            text = str(getattr(output, "content"))
        else:
            text = str(output)
        print(
            f"  result: ({len(text)} chars) "
            f"{_truncate(text, self.char_limit, self.verbose_full)}"
        )

    def on_tool_error(self, error: BaseException, **kwargs: Any) -> None:
        """
        tool 抛异常时触发。

        本项目的文件工具尽量返回 Error 字符串而不是抛异常；
        若仍进入此钩子，说明有未捕获异常，便于在 console 里看见。
        """
        print(f"  tool error: {error}")
