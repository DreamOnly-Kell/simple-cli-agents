# Handoff · python-langchain（学习用 Code Agent）

> 来源：intent-partner 对话整理（user-owned 草稿）  
> 用途：给后续设计 / 实现一个起点  
> 性质：交接简报，不是 PRD  
>
> **配套文档**  
> - 本子项目 DNA：[`PROJECT_DNA.md`](./PROJECT_DNA.md)  
> - 共享 DNA：[`../PROJECT_DNA.md`](../PROJECT_DNA.md)  
> - 实施计划：[`PLAN.md`](./PLAN.md)  
> - 设计 spec：[`superpowers/specs/2026-07-12-simple-cli-agent-design.md`](./superpowers/specs/2026-07-12-simple-cli-agent-design.md)  
> - **代码位置**：[`../../python-langchain/`](../../python-langchain/)

---

## 1. 这次要做什么

做一个 **基于 LangChain 的 Python 终端 agent demo**：极简 **code 助手**，用来 **学习并验证** LLM agent / tool use 理论。

- **验证优先**：先走通、有体感  
- **不是产品**：不对齐 Claude Code / Codex  
- **参考体量**：Pi Agent 一类轻量 agent  

---

## 2. 稳定取向

1. 目的是学习验证，不是交付可用产品  
2. 先借 LangChain 封装快速实现  
3. 范围要薄：多轮 + 有限 tools + 本轮结束后等人  
4. 对标要轻：Pi 量级  
5. 形态是终端 code 助手：LLM + tool use + 读/写文件  

---

## 3. 范围内（In Scope）

| 项 | 说明 |
|----|------|
| 技术栈 | Python + LangChain |
| 交互 | 终端 CLI；**多轮对话**；turn-based |
| 能力 | OpenAI 兼容 Chat Completions + **原生 tool calling** |
| 工具（当前） | `read_file` / `write_file` / `edit_file` / `ls` / `grep` / `run_command`（路径门禁 + shell 策略拦截） |
| 轮次 | 本轮 `invoke` 结束后 **等待**下一句用户输入 |
| 可观测 | console 逻辑轨迹 + HTTP jsonl（已实现） |

> 以 [`PROJECT_DNA.md`](./PROJECT_DNA.md) / [`../PROJECT_DNA.md`](../PROJECT_DNA.md) 为现行边界；本表若与 DNA 冲突，以 DNA + 代码为准。

---

## 4. 范围外

- Claude Code 级产品能力  
- 手写完整 agent runtime  
- IDE / 插件形态  
- 无策略裸 shell / OS 沙箱、语义索引、git 专用工具、真·补丁、多 agent、产品级长期记忆、MCP  

---

## 5. 验收线

1. 能简单多轮对话  
2. 能使用 agent 提供的 tool（读 / 搜 / 改 / 列目录 / 受控 shell）  
3. 能感知本轮结束，停下并等待下一轮用户输入  
4. 能在 console 或 jsonl 中对照 `tools` / `tool_calls` / tool 回灌  

**状态（相对 monorepo 现状）**：上述验收线在 `python-langchain` 子项目中 **已落地**；本文件保留为意图与边界记录。

---

## 6. 一句话

> **Python + LangChain 的极简终端 code agent 学习 demo**——多轮、文件/终端 tool use、本轮结束后等人；参考 Pi 的轻；先借框架快速走通，用实践验证理论。
