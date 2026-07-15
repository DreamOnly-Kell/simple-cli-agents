# Project DNA · simple-cli-agents

> 父 monorepo 共享决策约束。两子项目实现同一意图，技术栈不同。  
> 子项目补充：[`python-langchain/PROJECT_DNA.md`](./python-langchain/PROJECT_DNA.md)、[`java-spring-ai/PROJECT_DNA.md`](./java-spring-ai/PROJECT_DNA.md)。  
> 代码：`python-langchain/`、`java-spring-ai/`（与 `docs/` 同级）。  
> **性质**：决策影响源，不是 PRD；可随实现如实修订，但不为「变厚」而改初衷。

---

## DNA-01 · 学习验证优先

首要目的是用可跑 demo **验证 agent / tool use 理论**，不是交付产品。

## DNA-02 · 先框架封装，后底层拆解

- Python：LangChain `create_agent`  
- Java：Spring AI `ChatClient` + `@Tool`  

不先手搓 agent runtime；循环细节在有体感后再对照 wire 日志拆解。

## DNA-03 · 体量要薄，对标要轻

不对齐 Claude Code / Codex 完整产品。  
允许工具面随学习需要**小幅变厚**（见 DNA-04），但默认不做 OS 沙箱、真·补丁编辑器、语义索引、多 agent、MCP、产品级记忆。

## DNA-04 · 形态：终端极简 code 助手 + 当前工具面

**形态**：终端 CLI + OpenAI 兼容 Chat Completions + 原生 tool calling。

**当前已实现的工具面**（双栈语义对齐，模型可见名可能大小写略异）：

| 工具 | 作用 | 关键约束 |
|------|------|----------|
| `read_file` | 读工作区文本 | 路径门禁；超大文件软截断 |
| `write_file` | 整文件覆盖写 | 路径门禁；自动建父目录 |
| `edit_file` | 唯一匹配局部替换 | 0/多次匹配 → Error，文件不改 |
| `ls` | 非递归列目录 | 路径门禁；目录名尾 `/` |
| `grep` | 应用层子串搜索 | 路径门禁；跳过噪音目录；有命中上限 |
| `run_command` | 在工作区根执行 shell | **子串策略拦截**高危命令；超时/输出截断；**非** OS 沙箱 |

**路径门禁**：文件类 tool 限制在 workspace 根内。  
**shell**：cwd 钉在 workspace 根，但命令仍可能触达根外路径——这是 v1 明确取舍，不是完整沙箱。

## DNA-05 · 交互：两层循环（必须分清）

### 外层 · 人机轮次（turn-based）

```text
用户 input → 一次 agent.invoke / ChatClient.call → 打印 → 再 input
```

- 进程内多轮记忆（Python：`MemorySaver` + `thread_id`；Java：`ChatMemory` + `conversationId`）  
- **本轮返回后必须交还用户**，不空转、不常驻自主决策  

### 内层 · 模型 ⇄ tool（单次 invoke/call 内部）

```text
发 messages + tools schema
  → 模型可能返回 tool_calls
  → 运行时本地执行 tool
  → 回灌 role=tool 结果
  → 再调模型
  → 直到无 tool_calls → 本轮结束
```

- tools 在请求**顶层 `tools` 数组**，不在 content 字符串里  
- 策略（system prompt）与能力（tool schema）分离  

## DNA-06 · 成功线以「走通有体感」为准

多轮 + tool use + 结束等人 + 可观测（逻辑轨迹 / HTTP JSONL）即可视为当前阶段成功。  
对照学习时优先看：`tools` / `tool_calls` / tool 结果回灌 / 本轮何时停止。

## DNA-07 · 双实现对照

同一能力切片，两套框架；差异在 API 与工程结构，不在产品目标。  
加能力时**两边一起长**，避免只在一侧堆功能。

## DNA-08 · 配置与可观测（当前约定）

| 项 | Python | Java |
|----|--------|------|
| 配置 | `.env` + CLI | `application.yml` / `application-local.yml`（**不读 .env**） |
| shell 策略 | `SHELL_BLOCKED_PATTERNS` / `SHELL_TIMEOUT_SECONDS` | `app.shell-blocked-patterns` / `app.shell-timeout-seconds` |
| 逻辑轨迹 | console callbacks | Logback `cli.trace` |
| wire 日志 | HTTP JSONL（脱敏） | HTTP JSONL（脱敏） |

## 使用方式

- 写方案 / 写代码前：扫 DNA，问「是在服务验证 / 对照，还是在做重？」  
- 改工具面：先改本文件 DNA-04，再改代码与 README，避免文档滞后。  
