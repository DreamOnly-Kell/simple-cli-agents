# simple-cli-agents

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

极简**终端 Code Agent** 学习 monorepo：同一「根能力」用两套框架实现对照——

| 子项目 | 技术栈 |
|--------|--------|
| [`python-langchain/`](./python-langchain/) | Python + LangChain |
| [`java-spring-ai/`](./java-spring-ai/) | Java + Spring AI |

> **English:** [README.md](./README.md)

---

## 这是什么（不是什么）

**根能力一句话**

```text
人 ↔ 模型 ↔ tool call ↔ 执行工具 ↔ 结果回灌 ↔ 再决策
     → 本轮结束 → 等待下一句用户输入
```

本仓交付的是 coding agent **最小可运行内核（发动机）**，不是 Claude Code / Codex 级产品。

| 层级 | 角色 |
|------|------|
| **本仓库** | Agent 循环 + 原生 tool calling + 读写作 + 可观测 |
| **产品级 Agent** | 上述内核 **+** 大工具面、上下文运营、权限/沙箱、可靠与 UX |

后续能力（搜索、shell、OS 沙箱、产品记忆、打磨体验）属于 **能力 / 安全 / 工程 / 体验**，不只是「体验优化」。

---

## 核心思路

1. **根能力优先** — 先搞懂真实轮次循环，再堆产品功能。  
2. **先框架封装** — 用 LangChain / Spring AI 的 tool-calling agent，v1 不手搓 runtime。  
3. **范围要薄** — 多轮、两个文件工具、turn-based 交还用户、可观测。  
4. **仅 OpenAI 兼容接口** — 改 `base_url` 即可对接官方 / 网关 / 本地服务。  
5. **双实现对照** — 同一行为切片，不同框架，便于对照学习。  
6. **路径门禁 ≠ OS 沙箱** — 仅工作区路径检查，不是容器 / Seatbelt / bubblewrap。  

共享决策约束：[`docs/PROJECT_DNA.md`](./docs/PROJECT_DNA.md)。

---

## 实现方式

### Agent 循环（两边相同）

```text
┌─────────────────────────────────────────────────────────┐
│  CLI REPL                                                │
│  读一行 → 一次 agent/ChatClient 调用 → 打印 → 再等人      │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│  框架 tool-calling 循环（本轮内可多跳）                    │
│  1. 发送 messages + tools schema                         │
│  2. 模型可能返回 tool_calls                              │
│  3. 运行时本地执行工具                                   │
│  4. 回灌 tool 结果 → 再调模型                            │
│  5. 无 tool_calls 时结束 → 返回最终文本                   │
└───────────────────────────┬─────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
     文件工具（沙箱读写）              可观测
     read / write                  逻辑轨迹 + HTTP JSONL
```

### Tool 在协议里怎么传

OpenAI 兼容请求里，工具是**顶层 `tools` 数组**（function schema），不是塞进 `content` 字符串。  
调用意图在响应的 **`tool_calls`**；执行结果以 **`role: tool`** 消息回灌。

### 文件工具

- 路径解析到 workspace 根下；拒绝逃逸。  
- UTF-8 读（可截断）；写 = 整文件覆盖。  
- 失败返回 `Error: ...` 字符串，便于模型调整。

### 多轮

- 仅进程内记忆（进程退出即丢）。  
- Python：checkpointer + `thread_id`。  
- Java：`ChatMemory` + `conversationId`。

### 可观测（学习向）

| 通道 | 作用 |
|------|------|
| **逻辑轨迹** | 轮次、tool 名/参数/结果（console / Logback） |
| **HTTP JSONL** | 尽量还原 `chat/completions` 往返（密钥脱敏）→ `logs/` |

---

## 仓库结构

```text
simple-cli-agents/
├── README.md / README_CN.md
├── LICENSE
├── docs/                      # 设计 / 计划 / DNA
├── python-langchain/          # 在此目录运行
└── java-spring-ai/            # 在此目录运行
```

请先 **`cd` 进子项目** 再构建/运行，避免工作区路径指错。

---

## 已实现能力对照

两边验收线相同：**多轮 · tool use · 结束后等人 · 可观测**。

| 功能 | Python | Java |
|------|--------|------|
| 终端 REPL 多轮 | `cli.py` + memory | `ReplRunner` + `ChatMemory` |
| 本轮结束交还用户 | `invoke` 返回后 | `ChatClient.call()` 返回后 |
| 读文件 tool | `read_file` | `readFile`（`@Tool`） |
| 写文件 tool（覆盖） | `write_file` | `writeFile` |
| 工作区路径门禁 | `FileWorkspace` | `FileWorkspace` |
| OpenAI 兼容 Chat Completions | `ChatOpenAI` | Spring AI OpenAI starter |
| 系统提示 | `SYSTEM_PROMPT` | `AiConfig.SYSTEM_PROMPT` |
| 逻辑轨迹 | console Callback | Logback `cli.trace` |
| HTTP JSONL（脱敏） | httpx hooks | RestClient 拦截器 |
| 配置 | `.env` + CLI | `application.yml` / `application-local.yml`（**不读 .env**） |
| 单测（无真 LLM） | pytest | JUnit 5 |

**刻意未做：** Shell / 搜索 / git / 补丁编辑、OS 沙箱、流式优先、产品级持久记忆、多 agent、MCP、对齐 Claude Code / Codex。

---

## 实现映射（框架 API）

| 概念 | Python | Java |
|------|--------|------|
| 模型 | `ChatOpenAI` | Spring AI OpenAI + `ChatClient` |
| Agent 循环 | `create_agent` | `ChatClient` + tool calling |
| Tool | `StructuredTool` | `@Tool` |
| 多轮 | `MemorySaver` | `ChatMemory` |
| 配置 | `.env` | Spring 配置文件 |
| 可观测 | console + JSONL | Logback + JSONL |

---

## 最短启动

### Python

```bash
cd python-langchain
uv sync --extra dev
cp -n .env.example .env   # 填写 OPENAI_API_KEY / BASE_URL / MODEL
uv run python -m simple_cli_agent
```

详见 [`python-langchain/START.md`](./python-langchain/START.md)

### Java

```bash
cd java-spring-ai
# 复制 application-local.yml.example → application-local.yml
# 填写 api-key、base-url（不要带 /v1）、model
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

详见 [`java-spring-ai/START.md`](./java-spring-ai/START.md)

> **base-url：** Spring AI 请求 `{base-url}/v1/chat/completions`。写主机根（如 `https://api.openai.com`），不要写 `.../v1`。**不要把 api-key 和 base-url 写反。**

---

## 文档

| 文档 | 说明 |
|------|------|
| [README.md](./README.md) | English |
| [docs/PROJECT_DNA.md](./docs/PROJECT_DNA.md) | 共享决策约束 |
| [docs/python-langchain/](./docs/python-langchain/) | Python 交接 / 计划 / 设计 |
| [docs/java-spring-ai/](./docs/java-spring-ai/) | Java DNA / 实现计划 |
| 各子项目 `README.md` / `START.md` | 分栈安装与运行 |

---

## License

[MIT](./LICENSE)
