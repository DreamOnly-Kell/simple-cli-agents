# Project DNA · java-spring-ai

> 与姊妹子项目 [`python-langchain`](../python-langchain/) 同一学习目标；  
> 技术栈为 **Java + Spring AI**。  
> 共享 monorepo 约束见 [`../PROJECT_DNA.md`](../PROJECT_DNA.md)。  
> 不是 PRD；可随实现如实修订。

---

## DNA-01 · 学习验证优先

首要目的是用可跑 demo **验证 agent / tool use 理论**，不是交付产品。

## DNA-02 · 先框架封装，后底层拆解

基于 **Spring AI（`ChatClient` + `@Tool`）** 快速实现，不先手搓 agent runtime。  
内层 tool calling 在 `call()` 内由框架完成；用 Logback / HTTP jsonl 观察。

**代码锚点**：

| 概念 | 位置 |
|------|------|
| 外层 REPL | `cli.ReplRunner` → `readLine` → `chatClient.prompt()…call()` |
| 组装 | `config.AiConfig`（SYSTEM_PROMPT + FileTools + TerminalTools + ChatMemory） |
| 文件 tools | `tools.FileWorkspace` + `FileTools` |
| shell | `CommandGuard` + `ShellRunner` + `TerminalTools` |
| 多轮 | `ChatMemory` + `conversationId` |
| 可观测 | `LogicalTrace` + `HttpJsonlLogger` / interceptor |

## DNA-03 · 体量要薄，对标要轻

对齐 Pi 量级；**不对齐** Claude Code / Codex。  
OS 沙箱、真·补丁、语义索引、多 agent、MCP、产品记忆默认不做。

## DNA-04 · 形态：终端极简 code 助手 + 当前工具面

终端 CLI + OpenAI 兼容 Chat Completions + `@Tool`。

**当前工具**（与 Python 语义对齐；注册名以 schema 为准）：

| 模型可见名（典型） | 实现 |
|--------------------|------|
| `readFile` / `writeFile` | `FileTools` → `FileWorkspace` |
| `edit_file` | 唯一匹配局部编辑 |
| `ls` / `grep` | 列目录 / 应用层搜索 |
| `run_command` | `TerminalTools` → Guard + Runner |

**配置**：`app.workspace-root`、`app.shell-blocked-patterns`、`app.shell-timeout-seconds`。  
**不读** `.env`（与 Python 不同）。

**路径门禁管文件 tool；shell 仅策略拦截 + cwd=根，非 OS 沙箱。**

## DNA-05 · 交互：两层循环

**外层**：`ReplRunner` — 读一行 → `call()` → 打印 → 再读。  
**内层**：单次 `call()` 内多跳 tool，直到模型不再发起 tool_calls。  
本轮结束 = **`call()` 返回**。

## DNA-06 · 成功线以「走通有体感」为准

多轮 + tool use + 结束等人 + 可观测（Logback + HTTP jsonl）。

## DNA-07 · 与 Python 版对照学习

同一能力切片，换框架实现：

| 概念 | Python（`python-langchain`） | Java（本子项目） |
|------|------------------------------|------------------|
| 模型客户端 | LangChain `ChatOpenAI` | Spring AI OpenAI + `ChatClient` |
| 外层循环 | `cli.main` + `agent.invoke` | `ReplRunner` + `ChatClient.call` |
| 内层循环 | `create_agent` 内 model⇄tool | `ChatClient` tool calling |
| Tool 定义 | `StructuredTool` | `@Tool` |
| 多轮 | `MemorySaver` + thread_id | `ChatMemory` + conversationId |
| 配置 | `.env` | Spring yml（**不读 .env**） |
| 可观测 | console + HTTP jsonl | Logback + HTTP jsonl |
| shell 策略 | `CommandGuard` / env | `CommandGuard` / `app.shell-*` |

## 使用方式

改需求前先问：是在服务「走通验证 / 对照 Spring AI」，还是在做重？  
**代码目录**：[`../../java-spring-ai/`](../../java-spring-ai/)  
