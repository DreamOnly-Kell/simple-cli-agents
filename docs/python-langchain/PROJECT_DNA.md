# Project DNA · python-langchain

> 决策影响源：本子项目设计 / 实现 / 评审前先读。  
> 共享 monorepo 约束见 [`../PROJECT_DNA.md`](../PROJECT_DNA.md)。  
> 不是 PRD；可增删，不冻结。

---

## DNA-01 · 学习验证优先

**约束**：首要目的是 **学习 LLM agent / tool use 思路，并用可跑 demo 验证理论理解**，不是交付生产级产品。

**决策含义**：选型、范围、工期都服从「能否更快更清楚地验证理解」；不为「像产品」增加复杂度。

---

## DNA-02 · 先框架封装，后底层拆解

**约束**：基于 **LangChain** 封装快速实现，不先从零手搓 agent runtime。

**决策含义**：优先 `create_agent` + `StructuredTool` + `MemorySaver` 把闭环跑通；  
内层 model⇄tool 循环由框架执行，用 console / HTTP jsonl **观察**，需要时再拆。

**代码锚点**：

| 概念 | 位置 |
|------|------|
| 外层 REPL | `simple_cli_agent/cli.py` → `input` → `agent.invoke` |
| 组装 agent | `simple_cli_agent/agent.py` → `create_agent` |
| tools | `tools/files.py` + `tools/shell.py` + `tools/agent_tools.py` |
| 多轮 | `MemorySaver` + 固定 `thread_id` |
| 可观测 | `tracing.py` + `http_logging.py` |

---

## DNA-03 · 体量要薄，对标要轻

**约束**：demo 保持极简；参考 Pi Agent 体量，**不对齐 Claude Code / Codex**。

**决策含义**：OS 沙箱、真·补丁、语义索引、多 agent、MCP、产品记忆默认不做。  
当前工具面见 DNA-04；再增能力须能服务「看清循环 / 对照理论」，而非堆日用功能。

---

## DNA-04 · 形态：终端极简 code 助手 + 当前工具面

**约束**：终端 CLI + OpenAI 兼容接口 + tool use + **工作区路径门禁**。

**当前工具**（`make_agent_tools` 组装顺序）：

1. `read_file` / `write_file` / `edit_file` / `grep`（`FileWorkspace`）  
2. `ls` / `run_command`（`list_dir` + `CommandGuard`/`ShellRunner`）  

**shell 策略**：`SHELL_BLOCKED_PATTERNS`（`||` 分隔；`none` 关闭）+ `SHELL_TIMEOUT_SECONDS`。

**决策含义**：文件 tool 管路径逃逸；shell 只做子串拦截 + cwd 钉根——**路径门禁 ≠ OS 沙箱**。

---

## DNA-05 · 交互：两层循环

**外层（人机）**：`cli.main` 循环 — `input("you> ")` → `invoke` → 打印 → 再 `input`。  
**内层（agent）**：单次 `invoke` 内 model ⇄ tools 多跳，直到模型不再发 `tool_calls`。

**决策含义**：标准 turn-based；不空转、不常驻自主决策。  
「本轮结束」= **invoke 返回**，不是「模型说了一句就停」。

---

## DNA-06 · 成功线以「走通有体感」为准

**约束**：多轮 + tool use + 结束等人 + 可观测（console / HTTP jsonl）即可视为成功。

**决策含义**：以理论能否对照真实行为为准；优先在 jsonl 里看见 `tools` / `tool_calls` / `role: tool`。

---

## 使用方式

- 写方案 / 写代码前：扫一遍 DNA，问「是在服务验证，还是在做重？」  
- 与 Handoff / Plan：DNA 管「什么对」；[`HANDOFF.md`](./HANDOFF.md) / [`PLAN.md`](./PLAN.md) 管「这次具体做什么」（若过期以 DNA + 代码为准）。  
- 代码目录：[`../../python-langchain/`](../../python-langchain/)  
