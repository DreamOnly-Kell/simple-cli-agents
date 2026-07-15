"""
终端 REPL 入口：解析参数、组装 agent、循环读用户输入并 invoke。

交互契约（验收线）:
    1. 多轮：同一 thread_id + checkpointer
    2. 能用 tools：agent 内自动执行
    3. 本轮 invoke 返回后打印结果，再阻塞在 input() —— 不空转
"""

from __future__ import annotations

import argparse
import sys
import uuid

from langchain_core.messages import AIMessage, HumanMessage

from simple_cli_agent.agent import build_agent
from simple_cli_agent.config import load_config
from simple_cli_agent.model import build_chat_model
from simple_cli_agent.tracing import ConsoleTraceHandler


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    """
    定义并解析命令行参数。

    约定:
        大部分选项 default=None，表示「未在命令行指定」，
        交给 load_config 回退到环境变量/默认值。
        --quiet / --no-http-log 是显式关闭开关，会映射成 False。
    """
    p = argparse.ArgumentParser(
        description="Minimal LangChain terminal code agent (OpenAI-compatible)"
    )
    # 连接 OpenAI 兼容端点
    p.add_argument("--base-url", default=None, help="OpenAI-compatible API base URL")
    p.add_argument("--model", default=None, help="Model name")
    p.add_argument("--api-key", default=None, help="API key (else OPENAI_API_KEY)")
    # 文件工具沙箱
    p.add_argument("--workspace", default=None, help="Workspace root for file tools")
    # 可观测性：verbose 与 quiet 互斥逻辑在 main 里处理
    p.add_argument("--verbose", action="store_true", default=None, help="Console traces on")
    p.add_argument("--quiet", action="store_true", help="Disable console traces")
    p.add_argument("--verbose-full", action="store_true", default=None, help="No truncation")
    p.add_argument("--http-log", action="store_true", default=None, help="Enable HTTP jsonl log")
    p.add_argument("--no-http-log", action="store_true", help="Disable HTTP jsonl log")
    p.add_argument("--http-log-dir", default=None, help="Directory for HTTP logs")
    return p.parse_args(argv)


def _extract_final_text(result: dict) -> str:
    """
    从 agent.invoke 返回的 state 里取出「最终给用户看的助手正文」。

    为何需要:
        result["messages"] 里会有多条 AIMessage（中间步可能只有 tool_calls、content 为空）。
        从后往前找第一条带非空 content 的 AIMessage，通常就是本轮最终回复。

    兼容 content 形态:
        - str: 直接用
        - list[block]: 部分模型返回多模态块，抽取 type==text 的 text 字段拼接
    """
    messages = result.get("messages") or []
    # 倒序：最新的 AI 消息优先
    for msg in reversed(messages):
        if isinstance(msg, AIMessage):
            content = msg.content
            # 多模态/块列表形态
            if isinstance(content, list):
                parts = []
                for block in content:
                    if isinstance(block, dict) and block.get("type") == "text":
                        parts.append(block.get("text", ""))
                    elif isinstance(block, str):
                        parts.append(block)
                content = "".join(parts)
            # 跳过「只有 tool_calls、正文为空」的中间 AI 消息
            if content:
                return str(content)
    return ""


def main(argv: list[str] | None = None) -> int:
    """
    程序主入口：加载配置 → 构建模型与 agent → 进入 REPL。

    返回:
        进程退出码：0 正常退出；1 配置错误（如缺少 API key）。

    异常策略:
        单轮 agent.invoke 失败只打印 error 并继续循环，
        避免一次网络错误把整个学习会话打挂。
    """
    args = _parse_args(argv)

    # --- 把 CLI 开关折叠成 load_config 能理解的 Optional[bool] ---
    verbose: bool | None
    if args.quiet:
        # 显式安静优先
        verbose = False
    elif args.verbose:
        verbose = True
    else:
        # 未指定 → 跟环境变量 VERBOSE 默认
        verbose = None

    http_log: bool | None
    if args.no_http_log:
        http_log = False
    elif args.http_log:
        http_log = True
    else:
        http_log = None

    # 合并 env + CLI
    config = load_config(
        api_key=args.api_key,
        base_url=args.base_url,
        model=args.model,
        workspace=args.workspace,
        verbose=verbose,
        # 只有显式 --verbose-full 时传 True；否则让 env 决定
        verbose_full=True if args.verbose_full else None,
        http_log=http_log,
        http_log_dir=args.http_log_dir,
    )

    # 启动期 fail-fast：没有 key 时多数云 API 会失败，提前提示更清晰
    if not config.api_key:
        print(
            "Error: OPENAI_API_KEY is required (or pass --api-key). "
            "For local servers that ignore auth, set a dummy value e.g. OPENAI_API_KEY=EMPTY",
            file=sys.stderr,
        )
        return 1

    # 启动横幅：确认当前连的是哪、沙箱在哪
    print(f"workspace: {config.workspace_root}")
    print(f"model: {config.model} @ {config.base_url}")
    print("commands: exit | quit | Ctrl-C  —  type a message to chat\n")

    # 组装链路：model（可带 HTTP 日志）→ agent（tools + memory）
    # 构建失败时给出明确错误，避免只看到底层一长串 stack
    try:
        model = build_chat_model(config)
        agent, _ = build_agent(model, config)
    except Exception as exc:  # noqa: BLE001
        print(f"启动失败（创建 model/agent 时出错）: {exc}", file=sys.stderr)
        return 1
    # 同一 REPL 会话共用一个 thread_id，checkpointer 才能串起多轮
    thread_id = str(uuid.uuid4())
    # verbose 时挂 console 回调；quiet 时为 None，不传 callbacks
    tracer = ConsoleTraceHandler(verbose_full=config.verbose_full) if config.verbose else None

    # --- REPL 主循环：input → invoke → print → 再 input ---
    while True:
        try:
            # 阻塞等待用户；这是 DNA-05「交还控制权」的外层体现
            user_text = input("you> ").strip()
        except (EOFError, KeyboardInterrupt):
            # Ctrl-D / Ctrl-C：优雅退出
            print("\nbye")
            return 0

        if not user_text:
            # 空行忽略，不浪费一次 LLM 调用
            continue
        if user_text.lower() in {"exit", "quit"}:
            print("bye")
            return 0

        # 新 turn 分隔线（仅 verbose）
        if tracer:
            tracer.begin_turn()

        # LangGraph/LangChain 运行配置
        run_config: dict = {
            # thread_id 绑定 checkpointer 中的对话状态
            "configurable": {"thread_id": thread_id},
            # 防止 tool 死循环；学习场景 40 步足够
            "recursion_limit": 40,
        }
        if tracer:
            # 把 console 追踪挂到这一次 invoke
            run_config["callbacks"] = [tracer]

        try:
            # 只传入「本轮新消息」；历史由 checkpointer 按 thread_id 恢复
            result = agent.invoke(
                {"messages": [HumanMessage(content=user_text)]},
                config=run_config,
            )
        except Exception as exc:  # noqa: BLE001 — 学习 REPL：吞掉异常保持会话
            print(f"error: {exc}", file=sys.stderr)
            continue

        # 抽出最终助手文本；verbose 下中间过程已在 callback 打印
        final = _extract_final_text(result)
        if final:
            print(f"assistant> {final}")
        elif not config.verbose:
            # quiet 模式又没有正文时给个占位，避免「像卡死」
            print("assistant> (no text content)")
        # 循环回到 input() —— 本轮正式结束


if __name__ == "__main__":
    # 直接 python simple_cli_agent/cli.py 时也能跑
    raise SystemExit(main())
