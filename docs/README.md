# 文档索引 · simple-cli-agents

> **决策边界以 DNA + 代码为准。** 历史 plan/spec 只保留 v1 拆分过程，可能未全文改写 post-v1 工具。

## 现行（请优先读）

| 文档 | 说明 |
|------|------|
| [`PROJECT_DNA.md`](./PROJECT_DNA.md) | **共享 DNA**：意图、两层循环、当前工具面、配置约定 |
| [`python-langchain/PROJECT_DNA.md`](./python-langchain/PROJECT_DNA.md) | Python 栈锚点与决策含义 |
| [`java-spring-ai/PROJECT_DNA.md`](./java-spring-ai/PROJECT_DNA.md) | Java 栈锚点与对照表 |
| 根目录 [`../README.md`](../README.md) / [`../README_CN.md`](../README_CN.md) | monorepo 功能矩阵与启动 |
| 子项目 `README.md` / `START.md` | 分栈安装与运行 |

## 历史 / 交接（v1 过程记录）

| 文档 | 说明 |
|------|------|
| [`python-langchain/HANDOFF.md`](./python-langchain/HANDOFF.md) | 意图交接（已按现行工具修订范围表） |
| [`python-langchain/PLAN.md`](./python-langchain/PLAN.md) | v1 任务 checkbox 历史 |
| [`python-langchain/superpowers/specs/…`](./python-langchain/superpowers/specs/2026-07-12-simple-cli-agent-design.md) | 设计 spec 快照（文首标注 post-v1） |
| [`java-spring-ai/2026-07-12-implementation-plan.md`](./java-spring-ai/2026-07-12-implementation-plan.md) | Java 实现规划快照 |

## 当前能力一句话

双栈终端 code agent：**多轮 + tool use + 本轮交还用户 + 可观测**；  
tools = `read` / `write` / `edit`（唯一匹配）/ `ls` / `grep` / `run_command`（策略拦截）。  
路径门禁 ≠ OS 沙箱。
