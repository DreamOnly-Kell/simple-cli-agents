# Project DNA · simple-cli-agents

> 父 monorepo 共享决策约束。两子项目实现同一意图，技术栈不同。  
> 子项目补充：[`python-langchain/PROJECT_DNA.md`](./python-langchain/PROJECT_DNA.md)、[`java-spring-ai/PROJECT_DNA.md`](./java-spring-ai/PROJECT_DNA.md)。  
> 代码：`python-langchain/`、`java-spring-ai/`（与 `docs/` 同级）。

---

## DNA-01 · 学习验证优先

首要目的是用可跑 demo **验证 agent / tool use 理论**，不是交付产品。

## DNA-02 · 先框架封装，后底层拆解

- Python：LangChain 封装  
- Java：Spring AI（ChatClient + Tool）  
不先手搓 agent runtime。

## DNA-03 · 体量要薄，对标要轻

不对齐 Claude Code / Codex 完整产品。

## DNA-04 · 形态：终端极简 code 助手

LLM + tool use；工具至少 **读文件 / 写文件** + 工作区路径门禁。

## DNA-05 · 交互：多轮 + 本轮结束交还用户

Turn-based，不空转。

## DNA-06 · 成功线以「走通有体感」为准

多轮 + tool use + 结束等人 + 可观测（逻辑轨迹 / wire 日志）。

## DNA-07 · 双实现对照

同一能力切片，两套框架；差异在 API 与工程结构，不在产品目标。
