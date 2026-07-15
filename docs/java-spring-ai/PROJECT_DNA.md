# Project DNA · java-spring-ai

> 与姊妹子项目 [`python-langchain`](../python-langchain/) 同一学习目标；  
> 技术栈为 **Java + Spring AI**。  
> 共享 monorepo 约束见 [`../PROJECT_DNA.md`](../PROJECT_DNA.md)。

---

## DNA-01 · 学习验证优先

首要目的是用可跑 demo **验证 agent / tool use 理论**，不是交付产品。

## DNA-02 · 先框架封装，后底层拆解

基于 **Spring AI（ChatClient + `@Tool`）** 快速实现，不先手搓 agent runtime。

## DNA-03 · 体量要薄，对标要轻

对齐 Pi 量级；**不对齐** Claude Code / Codex。

## DNA-04 · 形态：终端极简 code 助手

终端 CLI + LLM + tool use；工具至少 **读文件 / 写文件** + 路径门禁。

## DNA-05 · 交互：多轮 + 本轮结束交还用户

Turn-based：本轮 tool 循环结束 → 停住等人。

## DNA-06 · 成功线以「走通有体感」为准

多轮 + tool use + 结束等人 + 可观测（Logback + HTTP jsonl）。

## DNA-07 · 与 Python 版对照学习

同一能力切片，换框架实现：

| 概念 | Python（`python-langchain`） | Java（本子项目） |
|------|------------------------------|------------------|
| 模型客户端 | LangChain `ChatOpenAI` | Spring AI OpenAI + `ChatClient` |
| Agent 循环 | `create_agent` | `ChatClient` tool calling |
| Tool 定义 | `StructuredTool` | `@Tool` |
| 多轮 | `MemorySaver` + thread_id | `ChatMemory` + conversationId |
| 配置 | `.env` | `application.yml` / `application-local.yml`（**不读 .env**） |
| 可观测 | console + HTTP jsonl | Logback + HTTP jsonl |

## 使用方式

改需求前先问：是在服务「走通验证 / 对照 Spring AI」，还是在做重？  
**代码目录**：[`../../java-spring-ai/`](../../java-spring-ai/)
