# java-spring-ai

> 父 monorepo：[`simple-cli-agents`](../) · 姊妹实现：[`python-langchain`](../python-langchain/)

用 **Spring AI** 实现的极简终端 code agent 学习 demo。

## 定位

> 实现的是 coding agent **最简单也最根本**的一块：  
> **人 ↔ 模型 ↔ tool call ↔ 工具结果回灌 ↔ 再决策 ↔ 本轮结束交还人。**  
> 完整产品还需要能力扩展、安全边界、工程可靠性与产品体验——体验只是其中一块。  
> 共享 DNA 见 [`docs/PROJECT_DNA.md`](../docs/PROJECT_DNA.md)。

**请在本目录 `java-spring-ai/` 下执行 Maven 命令**（避免 `user.dir` 指到父目录导致工作区错误）。

## 构建说明（重要）

| 工具 | 用途 |
|------|------|
| **Maven（唯一构建方式）** | `mvn test` / `mvn spring-boot:run` / `mvn package` |

- Java：**21**（`pom.xml` 中 `java.version`）
- Spring Boot：**3.5.3**
- Spring AI：**1.1.8**（BOM）

## 配置与启动

**只使用 Spring 配置**（`application.yml` / `application-local.yml` / 系统环境变量占位符 / `--spring.ai.openai.*=`）。  
**不读取** `.env` 文件（与 Python 子项目不同）。

详见 [`START.md`](./START.md)。最短路径：

```bash
# 在 application.yml 或 application-local.yml 中填写 api-key / base-url / model
mvn spring-boot:run
# 或
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## v1 能力

- OpenAI **兼容** Chat Completions
- 终端多轮 + turn-based 等人（`ChatMemory` + conversationId）
- `readFile` / `writeFile` + 工作区路径门禁
- **可观测双开**
  - **Logback**：逻辑轨迹（`cli.trace`）+ `logs/app.log`
  - **JSONL**：`logs/http-session-*.jsonl`（HTTP wire，脱敏）

## 包结构

```text
com.example.simplecliagent
├── SimpleCliAgentApplication   # 入口，无 Web
├── cli.ReplRunner              # REPL
├── config.AiConfig / AppProperties
├── tools.FileWorkspace / FileTools   # @Tool
└── observability.*             # Logback 轨迹 + JSONL + HTTP 拦截器
```

## 测试

```bash
mvn test
```

不依赖真实 LLM（沙箱 + JSONL 脱敏）。

## 文档

- [`PROJECT_DNA.md`](../docs/java-spring-ai/PROJECT_DNA.md)
- [`2026-07-12-implementation-plan.md`](../docs/java-spring-ai/2026-07-12-implementation-plan.md)
- 共享 DNA：[`docs/PROJECT_DNA.md`](../docs/PROJECT_DNA.md)

## 范围外

Shell / 搜索 / git / OS 沙箱 / 对齐 Claude Code 等——与 Python 版 DNA 一致。
