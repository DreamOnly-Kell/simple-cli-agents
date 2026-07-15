# Plan · python-langchain（学习用）

> 对齐：[`PROJECT_DNA.md`](./PROJECT_DNA.md) + [`HANDOFF.md`](./HANDOFF.md)  
> 目标：用 LangChain **跑通** 多轮 + 读/写文件 tool + 本轮结束等人 + 可观测  
> **代码目录**：[`../../python-langchain/`](../../python-langchain/)  
> **状态**：v1 任务 **已完成**（checkbox 保留为历史拆分记录）

---

## 0. 完成定义（Done）

1. 终端可多轮对话（历史能接上）  
2. Agent 能按需调用 **读文件 / 改文件**  
3. 模型本轮结束后，CLI **停住等待**下一句输入  
4. 能看见 tool 调用与 HTTP jsonl（体感验证）  

非目标：Claude Code 级能力、手搓 runtime、权限产品化、多 agent、git/搜索等。

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

## 3. 本 plan 之外（仍推迟）

- 手写 ReAct / 自建 runtime  
- Shell / 搜索 / git  
- 会话持久化、skills、对齐 Claude Code 完整交互  

---

## 4. 下一步（可选）

- 与 `java-spring-ai` 对照学习同一理论节点  
- 需要加能力时先改 DNA / Handoff，再开新 plan  
