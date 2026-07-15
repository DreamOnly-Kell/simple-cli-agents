# java-spring-ai · 实现规划

> **状态**：v1 **已实现**（Maven 构建）  
> **代码目录**：[`../../java-spring-ai/`](../../java-spring-ai/)  
> **姊妹代码**：[`../../python-langchain/`](../../python-langchain/)  
> **目标**：用 **Spring AI 1.1.x + Boot 3.5** 复刻 Python 版「根能力」学习 demo  
> **构建**：**仅 Maven（`pom.xml`）**

---

## 1. 目标与非目标

### 1.1 目标（v1 Done）

1. 终端多轮对话（进程内历史）  
2. Tool use：`readFile` / `writeFile`（工作区路径门禁）  
3. 本轮结束后 CLI **等待**下一句用户输入  
4. **可观测双开**：  
   - Logback：逻辑层 tool / 轮次轨迹  
   - HTTP JSONL：`logs/http-session-*.jsonl`（脱敏，对齐 Python）  

技术锁定：

- **Java 21**  
- **Spring Boot 3.5.3** + **Spring AI 1.1.8**  
- **仅 OpenAI 兼容** Chat Completions（`base-url` + `api-key` + `model`）  
- 无 Web：`spring.main.web-application-type=none`  

### 1.2 非目标

- 对齐 Claude Code / Codex  
- Anthropic 原生、多 Provider、子 agent、MCP  
- Shell / 搜索 / git / patch  
- 读取 `.env` 文件（配置只走 Spring：`application.yml` / `application-local.yml`）  
- Gradle 构建  

---

## 2. 与 Python 版能力映射

| 能力 | `python-langchain` | `java-spring-ai` |
|------|--------------------|------------------|
| 入口 | `uv run python -m simple_cli_agent` | `mvn spring-boot:run`（在子目录内） |
| 配置 | `.env` | `application.yml` + `application-local.yml`（profile=local） |
| 模型 | `ChatOpenAI` | Spring AI OpenAI starter |
| Agent | `create_agent` | `ChatClient` + tools + tool-calling 循环 |
| 读/写文件 | `FileWorkspace` + StructuredTool | `FileWorkspace` + `@Tool` |
| 多轮 | MemorySaver + thread_id | ChatMemory + conversationId |
| 逻辑轨迹 | Callback / console | Logback `cli.trace` |
| HTTP 日志 | httpx hooks → jsonl | RestClient 拦截器 → jsonl |
| 测试 | pytest | JUnit 5（沙箱 / 脱敏，不依赖 LLM） |

---

## 3. 架构（已落地）

```text
ReplRunner (stdin 循环)
  → ChatClient.prompt().user(...).call()
  → print → input

ChatClient
  · defaultSystem(SYSTEM_PROMPT)
  · defaultTools(FileTools)
  · MessageChatMemoryAdvisor

OpenAiChatModel          FileTools (@Tool)
  (+ RestClient 拦截器)    → FileWorkspace (path jail)
       ↓
  logs/http-session-*.jsonl
```

### 主要类

| 类 | 职责 |
|----|------|
| `SimpleCliAgentApplication` | 无 Web 入口 |
| `cli.ReplRunner` | REPL |
| `config.AiConfig` / `AppProperties` | ChatClient、Memory、配置 |
| `config.ConnectionDiagnostics` | 启动时校验 base-url / api-key 是否写反 |
| `tools.FileWorkspace` / `FileTools` | 沙箱读写 + `@Tool` |
| `observability.LogicalTrace` | Logback 逻辑轨迹 |
| `observability.HttpJsonlLogger` / `HttpLoggingInterceptor` | wire JSONL |

包名：`com.example.simplecliagent`。

---

## 4. 配置要点（已定）

```yaml
# application.yml / application-local.yml
spring:
  ai:
    openai:
      api-key: ...                    # 密钥，不要写成 URL
      base-url: https://api.example.com  # 不要带 /v1；实际请求 {base}/v1/chat/completions
      chat:
        options:
          model: ...
```

- **不读 `.env`**  
- 推荐：`application-local.yml` + `-Dspring-boot.run.profiles=local`  
- 可用环境变量：`SPRING_AI_OPENAI_API_KEY` / `SPRING_AI_OPENAI_BASE_URL` / `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL`  
- `base-url` 与 `api-key` **禁止写反**（启动诊断会校验）  

---

## 5. 工具与沙箱

与 Python 语义对齐：

- `readFile` / `writeFile`：相对 workspace 根；逃逸拒绝；UTF-8；写覆盖  
- 过大读截断；错误返回 `Error: ...` 字符串  

---

## 6. 可观测性（已定：双开）

| 通道 | 技术 | 默认 |
|------|------|------|
| 逻辑 | Logback → 控制台 + `logs/app.log` | 开 |
| Wire | RestClient 拦截 → `logs/http-session-*.jsonl` | 开（`app.http-log`） |

JSONL 字段尽量与 Python 同构：`request` / `response`、headers 脱敏。

---

## 7. 任务进度

| Task | 内容 | 状态 |
|------|------|------|
| T0 | Maven 骨架、无 Web | ✅ |
| T1 | AppProperties + OpenAI 配置 | ✅ |
| T2 | FileWorkspace + 单测 | ✅ |
| T3 | FileTools + ChatClient | ✅ |
| T4 | ChatMemory + REPL 多轮 | ✅ |
| T5 | Logback 逻辑轨迹 | ✅ |
| T6 | HTTP JSONL + README/START | ✅ |
| T7 | 手工自检（依赖真实 API） | 由使用者在本地完成 |

---

## 8. 目录（monorepo）

```text
simple-cli-agents/
├── docs/
│   ├── PROJECT_DNA.md
│   └── java-spring-ai/
│       ├── PROJECT_DNA.md
│       └── 2026-07-12-implementation-plan.md   # 本文
├── java-spring-ai/          # 实现代码
│   ├── pom.xml
│   ├── src/main/java/...
│   └── src/main/resources/application.yml
└── python-langchain/        # 姊妹实现
```

---

## 9. 风险与注意

| 风险 | 处理 |
|------|------|
| base-url 为空或写成密钥 | ConnectionDiagnostics 启动失败并提示 |
| base-url 带 `/v1` | 文档约定写主机根；Spring AI 拼 `/v1/chat/completions` |
| 在父目录启动 | `user.dir` 错误 → 必须在 `java-spring-ai/` 下 `mvn` |
| HTTP 拦截依赖 RestClient | 若某版本不走 RestClient，jsonl 可能为空；Logback 仍可用 |

---

## 10. 下一步（可选）

- 本地配置 `application-local.yml` 跑通自检剧本  
- 与 Python 子项目并排对照 `logs/http-session-*.jsonl`  
