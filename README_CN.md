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
| **本仓库** | Agent 循环 + 原生 tool calling + 文件/终端工具 + 可观测 |
| **产品级 Agent** | 上述内核 **+** 更大工具面、上下文运营、OS 沙箱、可靠与 UX |

后续层次（OS 沙箱、产品记忆、MCP、体验打磨）属于 **能力 / 安全 / 工程 / 体验**，不只是「体验优化」。

---

## 核心思路

1. **根能力优先** — 先搞懂真实轮次循环，再堆产品功能。  
2. **先框架封装** — 用 LangChain / Spring AI 的 tool-calling agent，v1 不手搓 runtime。  
3. **工具面薄但真实** — 多轮、文件读写 + 唯一匹配编辑 + ls + 应用层 grep + 策略拦截 shell、可观测。  
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
     工具（工作区约束）                 可观测
     read / write / edit               逻辑轨迹 + HTTP JSONL
     ls / grep / run_command
```

### Tool 在协议里怎么传

OpenAI 兼容请求里，工具是**顶层 `tools` 数组**（function schema），不是塞进 `content` 字符串。  
调用意图在响应的 **`tool_calls`**；执行结果以 **`role: tool`** 消息回灌。

### 工具清单（双栈语义对齐）

| 工具 | 行为 |
|------|------|
| `read_file` | 工作区内 UTF-8 读；超大文件软截断 |
| `write_file` | 整文件覆盖；自动建父目录 |
| `edit_file` | **恰好一次**替换 `old_str`；0/多次 → Error，文件不改 |
| `ls` | 非递归列目录；目录名尾部 `/` |
| `grep` | 应用层子串搜索（`path:line:snippet`）；跳过 `.venv`/`target`/…；有结果上限 |
| `run_command` | 在工作区根执行 shell；可配置高危拦截；超时与输出截断 |

共同规则：

- **文件类** tool 走路径门禁（`../` / 根外绝对路径 → `Error: ...`）。shell 的 cwd 是工作区根，但**不是**完整 OS 沙箱。  
- 失败返回以 `Error:` 开头的字符串，便于模型下一跳调整。

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
| 安全局部编辑（唯一匹配） | `edit_file` | `edit_file`（`@Tool`） |
| 工作区内文本搜索 | `grep` | `grep`（`@Tool`） |
| 列目录 tool | `ls` | `ls`（`@Tool`） |
| 终端命令 tool | `run_command` | `run_command`（`@Tool`） |
| 配置拦截高危 shell 命令 | `SHELL_BLOCKED_PATTERNS` | `app.shell-blocked-patterns` |
| 工作区路径门禁 | `FileWorkspace` | `FileWorkspace` |
| OpenAI 兼容 Chat Completions | `ChatOpenAI` | Spring AI OpenAI starter |
| 系统提示 | `SYSTEM_PROMPT` | `AiConfig.SYSTEM_PROMPT` |
| 逻辑轨迹 | console Callback | Logback `cli.trace` |
| HTTP JSONL（脱敏） | httpx hooks | RestClient 拦截器 |
| 配置 | `.env` + CLI | `application.yml` / `application-local.yml`（**不读 .env**） |
| 单测（无真 LLM） | pytest | JUnit 5 |

**刻意未做：** 无策略/无 OS 沙箱的裸 shell、正则/语义代码索引、git 专用工具、真·补丁编辑、流式优先、产品级持久记忆、多 agent、MCP、对齐 Claude Code / Codex。

> 说明：应用层 `grep` 与**带策略拦截**的 `run_command` **已在范围内**（见上表）。

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
# 自行创建 application-local.yml（gitignore）或改 application.yml
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
| [docs/README.md](./docs/README.md) | 文档索引（**DNA 为边界真源**） |
| [docs/PROJECT_DNA.md](./docs/PROJECT_DNA.md) | 共享决策约束 + 当前工具面 |
| [docs/python-langchain/](./docs/python-langchain/) | Python DNA / 交接 / 历史 plan·design |
| [docs/java-spring-ai/](./docs/java-spring-ai/) | Java DNA + 历史实现规划 |
| 各子项目 `README.md` / `START.md` | 分栈安装与运行 |

**收口说明：** 功能面暂停在当前 6 tools。文档冲突时以 DNA + 本 README + 代码为准，历史 spec/plan 仅作过程记录。

---

## License

[MIT](./LICENSE)
