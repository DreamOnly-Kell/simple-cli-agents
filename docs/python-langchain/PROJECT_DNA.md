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

**决策含义**：优先 `create_agent` / tool / message 抽象把闭环跑通；底层机制可在有体感后再拆。

---

## DNA-03 · 体量要薄，对标要轻

**约束**：demo 保持极简；参考 Pi Agent 体量，**不对齐 Claude Code / Codex**。

**决策含义**：功能膨胀、权限体系、复杂 UX、多 agent、长期记忆等默认不做。

---

## DNA-04 · 形态：终端极简 code 助手

**约束**：终端 CLI + LLM + tool use；工具至少 **读文件 / 写文件** + 工作区路径门禁。

**决策含义**：不做 Web/IDE 优先；工具集以最小闭环为上限。

---

## DNA-05 · 交互：多轮 + 本轮结束交还用户

**约束**：简单多轮；tool use；本轮结束后 **停止并等待**下一句用户输入（turn-based）。

**决策含义**：标准人机轮次；不空转、不常驻自主决策。

---

## DNA-06 · 成功线以「走通有体感」为准

**约束**：多轮 + tool use + 结束等人 + 可观测（console / HTTP jsonl）即可视为当前阶段成功。

**决策含义**：以理论能否对照真实行为为准，不以产品 checklist 为准。

---

## 使用方式

- 写方案 / 写代码前：扫一遍 DNA，问「是在服务验证，还是在做重？」
- 与 Handoff / Plan：DNA 管「做什么对」；[`HANDOFF.md`](./HANDOFF.md) / [`PLAN.md`](./PLAN.md) 管「这次具体做什么」。
- 代码目录：monorepo 下 [`../../python-langchain/`](../../python-langchain/)
