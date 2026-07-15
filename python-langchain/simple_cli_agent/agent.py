"""
Agent 组装：把「模型 + 工具 + 系统提示 + 多轮记忆」绑成一条 LangChain agent 图。

学习路径上的位置:
    CLI 收用户输入 → agent.invoke →（内部）模型 ⇄ tools 多跳 → 返回 messages
    本模块只负责「建好这张图」，不负责 REPL 交互。
"""

from __future__ import annotations

from langchain.agents import create_agent
from langchain_openai import ChatOpenAI
# MemorySaver：进程内 checkpointer，用 thread_id 区分会话，实现多轮历史
from langgraph.checkpoint.memory import MemorySaver

from simple_cli_agent.config import AppConfig
from simple_cli_agent.tools.files import FileWorkspace, make_file_tools

# 系统提示：告诉模型自己的角色、有哪些工具、路径规则、何时停止
# 这是「策略层」约束，和 tool schema（能力层）互补
SYSTEM_PROMPT = """You are a minimal terminal coding assistant for learning agent/tool-use.

You have two tools:
- read_file(path): read a text file under the workspace
- write_file(path, content): create or overwrite a text file under the workspace

Rules:
- Prefer tools when the user asks about or wants to change files.
- Paths are relative to the workspace root.
- Be concise. After finishing the user's request for this turn, stop and wait
  (do not invent extra tasks).
"""


def build_agent(model: ChatOpenAI, config: AppConfig):
    """
    创建可多轮调用的 tool-calling agent。

    做了什么:
        1. 用 config.workspace_root 建 FileWorkspace 沙箱
        2. make_file_tools 得到 read_file / write_file
        3. MemorySaver 作为 checkpointer（同 thread_id 跨 invoke 保留历史）
        4. create_agent 组装官方推荐的单条 agent 路径（方案 A）

    参数:
        model:  build_chat_model 得到的 ChatOpenAI
        config: 至少使用 workspace_root

    返回:
        (agent, checkpointer)
        - agent: 编译后的 StateGraph，支持 .invoke({"messages": [...]}, config=...)
        - checkpointer: 目前 CLI 只用 agent；返回 checkpointer 便于测试或以后扩展

    多轮原理（对照 DNA-05）:
        每次 invoke 传入新的 HumanMessage，checkpointer 按 thread_id 合并历史；
        单次 invoke 内部可多次 model↔tool，直到模型不再发起 tool_calls，invoke 才返回，
        外层 CLI 再 input() 等待用户——这就是「本轮结束交还用户」。
    """
    # 沙箱根 = 配置里的工作区
    workspace = FileWorkspace(config.workspace_root)
    # 绑定到该 workspace 的 LangChain tools
    tools = make_file_tools(workspace)
    # 内存 checkpointer：进程退出即丢，符合 v1「不持久化会话」
    checkpointer = MemorySaver()
    # create_agent: LangChain 1.x 推荐入口，内部是 LangGraph 状态机循环
    agent = create_agent(
        model,  # 未预先 bind_tools；由 create_agent 负责绑定 tools
        tools=tools,
        system_prompt=SYSTEM_PROMPT,
        checkpointer=checkpointer,
    )
    return agent, checkpointer
