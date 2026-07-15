# simple-cli-agents

极简终端 Code Agent 学习 monorepo：同一「根能力」切片，两套框架实现对照。

> **根能力一句话**  
> 人 ↔ 模型 ↔ tool call ↔ 工具结果回灌 ↔ 再决策 ↔ 本轮结束交还人。  
> 完整产品还需要能力扩展、安全边界、工程可靠性与产品体验——体验只是其中一块。

## 子项目

| 目录 | 技术栈 | 启动 |
|------|--------|------|
| [`python-langchain/`](./python-langchain/) | Python + LangChain | 见该目录 `START.md` |
| [`java-spring-ai/`](./java-spring-ai/) | Java + Spring AI | 见该目录 `START.md` |

```text
simple-cli-agents/
├── README.md                 # 本文件
├── docs/                     # 全部设计/计划/DNA 文档
│   ├── PROJECT_DNA.md        # 共享决策约束
│   ├── python-langchain/
│   └── java-spring-ai/
├── .gitignore
├── session.txt               # 本地会话续聊 id（gitignore）
├── python-langchain/         # Python 实现
└── java-spring-ai/           # Java 实现
```

## 已实现能力对照

两边验收线相同：**多轮对话 · 会调 tool · 本轮结束后等人 · 行为可观测**。

| 功能 | Python (`python-langchain`) | Java (`java-spring-ai`) |
|------|----------------------------|-------------------------|
| 终端 REPL 多轮对话 | ✅ `cli.py` + 进程内 memory | ✅ `ReplRunner` + `ChatMemory` |
| 本轮结束交还用户 | ✅ agent `invoke` 返回后再 `input` | ✅ `ChatClient.call()` 返回后再读 stdin |
| 读文件 tool | ✅ `read_file` | ✅ `readFile`（`@Tool`） |
| 写文件 tool（整文件覆盖） | ✅ `write_file` | ✅ `writeFile` |
| 工作区路径门禁（应用层沙箱） | ✅ `FileWorkspace` | ✅ `FileWorkspace` |
| OpenAI **兼容** Chat Completions | ✅ `ChatOpenAI` + base_url | ✅ Spring AI OpenAI starter |
| 系统提示（code 助手 + 工具说明） | ✅ `SYSTEM_PROMPT` | ✅ `AiConfig.SYSTEM_PROMPT` |
| 逻辑层轨迹（tool / 轮次） | ✅ console Callback | ✅ Logback `cli.trace` |
| HTTP wire 日志（JSONL，脱敏） | ✅ httpx hooks → `logs/http-session-*.jsonl` | ✅ RestClient 拦截器 → 同结构 jsonl |
| 配置 | ✅ `.env` + CLI 参数 | ✅ `application.yml` / `application-local.yml`（**不读 .env**） |
| 单测（不依赖真 LLM） | ✅ 沙箱 / 配置 / JSONL 脱敏 | ✅ 沙箱 / JSONL / 配置校验 |
| 启动文档 | ✅ README / START | ✅ README / START |
| 共享 DNA / 总览 | 父目录 `docs/PROJECT_DNA.md`、`README.md` | 同左 |

**刻意未做（两边一致）**：Shell / 搜索 / git / 补丁编辑、OS 级沙箱、流式优先、持久会话产品、子 agent、MCP、对齐 Claude Code / Codex 完整度。

## 最短启动

**Python**

```bash
cd python-langchain
uv sync --python 3.14 --extra dev
# 配置 .env 后
uv run python -m simple_cli_agent
```

**Java**

```bash
cd java-spring-ai
# 配置 application-local.yml 后
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

请在**各自子目录**内执行构建/运行（不要在父目录直接跑，避免工作区路径指错）。

## 实现映射（框架 API）

| 概念 | Python | Java |
|------|--------|------|
| 模型 | `ChatOpenAI` | Spring AI OpenAI + `ChatClient` |
| Agent 循环 | `create_agent` | `ChatClient` + tool calling |
| Tool | `StructuredTool` | `@Tool` |
| 多轮 | `MemorySaver` | `ChatMemory` |
| 配置 | `.env` | `application.yml` / `application-local.yml` |
| 可观测 | console + HTTP jsonl | Logback + HTTP jsonl |

## 文档

- 共享：[`docs/PROJECT_DNA.md`](./docs/PROJECT_DNA.md)
- Python：`python-langchain/README.md`、`START.md`；计划/交接见 [`docs/python-langchain/`](./docs/python-langchain/)
- Java：`java-spring-ai/README.md`、`START.md`；实现计划见 [`docs/java-spring-ai/`](./docs/java-spring-ai/)
