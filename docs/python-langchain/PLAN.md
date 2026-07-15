# Plan · python-langchain（学习用）

> **状态**：历史任务拆分；**v1 已完成**。  
> **现行边界**：[`PROJECT_DNA.md`](./PROJECT_DNA.md) + 共享 DNA + 代码（含 edit/ls/grep/run_command）。  
> 对齐：[`HANDOFF.md`](./HANDOFF.md)  
> 原目标：用 LangChain **跑通** 多轮 + 读/写文件 tool + 本轮结束等人 + 可观测  
> **代码目录**：[`../../python-langchain/`](../../python-langchain/)

---

## 0. 完成定义（Done · v1 根引擎）

1. 终端可多轮对话（历史能接上）  
2. Agent 能按需调用 **读文件 / 写文件**（v1 最小集；现行完整工具见 DNA-04）  
3. 模型本轮结束后，CLI **停住等待**下一句输入  
4. 能看见 tool 调用与 HTTP jsonl（体感验证）  

非目标（相对产品）：Claude Code 级能力、手搓 runtime、OS 沙箱、多 agent、语义索引、MCP 等。

---

## 1. 任务拆分

### Task 1 — 项目骨架 ✅

- [x] Python 包结构 `simple_cli_agent/` + `pyproject.toml` / `uv.lock`  
- [x] 依赖：langchain、langchain-openai、httpx、python-dotenv  
- [x] `.env.example`（不提交真实 key）  
- [x] 入口：`python -m simple_cli_agent`  

### Task 2 — LLM 接入 + 多轮 ✅

- [x] Chat Model（OpenAI 兼容）  
- [x] 会话历史（MemorySaver / thread_id）  
- [x] REPL：`input` → 调 agent → 打印 → 再 `input`  
- [x] 退出：`exit` / `quit` / Ctrl-C  

### Task 3 — 文件工具 ✅

- [x] `read_file` / `write_file`  
- [x] LangChain tool 注册  
- [x] 工作区路径门禁（`FileWorkspace`）  

### Task 4 — Agent + Tool Use + 可观测 ✅

- [x] `create_agent` 绑定 model + tools  
- [x] 本轮结束后交还 CLI  
- [x] console 轨迹 + HTTP jsonl 脱敏落盘  

### Task 5 — 端到端自检 ✅

| # | 场景 | 期望 |
|---|------|------|
| 1 | 闲聊 | 可不调 tool；结束后等人 |
| 2 | 读文件 | 调 read；内容进回答 |
| 3 | 写文件 | 调 write；磁盘可核对 |
| 4 | 连续两轮 | 上下文仍在 |
| 5 | 答完 | CLI 停在 prompt |

---

## 2. 关键技术决策（已定）

| 点 | 选择 |
|----|------|
| Agent API | LangChain `create_agent` |
| 写文件 | 整文件覆盖 |
| 可观测 | console + `logs/http-session-*.jsonl` |
| Provider | OpenAI 兼容 only（改 base_url） |

---

## 3. 本 plan 之外（仍推迟 / 非本 plan 范围）

- 手写 ReAct / 自建 runtime  
- 无策略裸 shell、OS 沙箱、语义索引、git 专用工具  
- 会话持久化、MCP、对齐 Claude Code 完整交互  

> 说明：应用层 `grep`、策略拦截的 `run_command`、`edit_file` / `ls` **已在后续迭代落地**；现行边界见 DNA。

---

## 4. 下一步（可选 · 学习向）

- 与 `java-spring-ai` 对照同一任务，对照 `tools` / `tool_calls` / JSONL  
- 需要加能力时先改 DNA / Handoff，再开新 plan  
