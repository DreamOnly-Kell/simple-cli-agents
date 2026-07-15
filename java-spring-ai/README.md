# java-spring-ai

> 父 monorepo：[`simple-cli-agents`](../) · 姊妹实现：[`python-langchain`](../python-langchain/)

用 **Spring AI** 实现的极简终端 code agent 学习 demo。

> **功能收口：** 当前工具面见 [`docs/PROJECT_DNA.md`](../docs/PROJECT_DNA.md)。历史实现规划以 DNA + 代码为准。

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

### 常用 `app.*` 配置

| 属性 | 含义 | 默认 |
|------|------|------|
| `app.workspace-root` | 文件/ls/grep/edit 沙箱根；`run_command` cwd | `${user.dir}` |
| `app.verbose` | Logback 逻辑轨迹 | `true` |
| `app.http-log` / `app.http-log-dir` | HTTP JSONL | `true` / `logs` |
| `app.shell-timeout-seconds` | `run_command` 超时 | `30` |
| `app.shell-blocked-patterns` | 高危命令子串列表；**空列表回退代码默认** | 见 `application.yml` |

## 已实现能力

- OpenAI **兼容** Chat Completions
- 终端多轮 + turn-based 等人（`ChatMemory` + conversationId）
- **Tools**（与 Python 语义对齐；`@Tool` 暴露给模型）：

| Tool 名（模型可见） | 实现 | 说明 |
|---------------------|------|------|
| `readFile` | `FileTools` → `FileWorkspace` | 读文本；可截断 |
| `writeFile` | 同上 | 整文件覆盖 |
| `edit_file` | 同上 | 唯一匹配局部替换 |
| `ls` | 同上 | 非递归列目录 |
| `grep` | 同上 | 应用层子串搜索 |
| `run_command` | `TerminalTools` → `ShellRunner` + `CommandGuard` | 工作区根 shell；策略拦截 |

- 工作区**路径门禁**（文件类 tool）；shell 仅子串策略 + 超时，**非** OS 沙箱
- **可观测双开**
  - **Logback**：逻辑轨迹（`cli.trace`）+ `logs/app.log`
  - **JSONL**：`logs/http-session-*.jsonl`（HTTP wire，脱敏）

## 包结构

```text
com.example.simplecliagent
├── SimpleCliAgentApplication   # 入口，无 Web
├── cli.ReplRunner              # REPL
├── config
│   ├── AiConfig                # ChatClient + SYSTEM_PROMPT + tools 注册
│   ├── AppProperties           # app.*（含 shell 策略）
│   └── ConnectionDiagnostics   # 启动时检查 api-key / base-url 是否写反
├── tools
│   ├── FileWorkspace           # 路径门禁 + 读/写/edit/ls/grep
│   ├── FileTools               # @Tool：read/write/edit/ls/grep
│   ├── CommandGuard            # 高危命令子串拦截
│   ├── ShellRunner             # 进程执行 + 超时/截断
│   └── TerminalTools           # @Tool：run_command
└── observability.*             # Logback 轨迹 + JSONL + HTTP 拦截器
```

## 测试

```bash
mvn test
```

不依赖真实 LLM（沙箱、shell 策略、JSONL 脱敏等单测）。

## 文档

- [`PROJECT_DNA.md`](../docs/java-spring-ai/PROJECT_DNA.md)
- [`2026-07-12-implementation-plan.md`](../docs/java-spring-ai/2026-07-12-implementation-plan.md)
- 共享 DNA：[`docs/PROJECT_DNA.md`](../docs/PROJECT_DNA.md)
- 父仓功能矩阵：[`../README_CN.md`](../README_CN.md)

## 范围外

无 OS 沙箱的任意 shell、语义/正则代码索引、git 专用工具、真·补丁编辑、产品级 Memory、MCP、对齐 Claude Code 等——与 Python 版 DNA 一致。

> **已在范围内：** 应用层 `grep`、策略拦截的 `run_command`、`edit_file` / `ls`。
