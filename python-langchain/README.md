# python-langchain

> 父 monorepo：[`simple-cli-agents`](../) · 姊妹实现：[`java-spring-ai`](../java-spring-ai/)

极简 **LangChain 终端 code agent**（学习用）：OpenAI 兼容接口 + 文件/终端 tools + 多轮对话 + 双通道可观测性。

> **功能收口：** 当前工具面见 [`docs/PROJECT_DNA.md`](../docs/PROJECT_DNA.md)（read/write/edit/ls/grep/run_command）。历史 design/plan 以 DNA 为准。

## 定位（读这个就够）

本子项目实现的是 coding agent 里**最简单、也最根本**的一块：

> **人 ↔ 模型 ↔ tool call ↔ 工具结果回灌 ↔ 再决策 ↔ 本轮结束交还人。**

相对 Claude Code、Codex CLI 等日用工程代理：这是那条链路的**最小可运行内核**（「发动机」），不是完整产品。  
共享 DNA 见 [`docs/PROJECT_DNA.md`](../docs/PROJECT_DNA.md)。

```text
本子项目 ≈ agent 循环 + 原生 tool use 的教学闭环
Claude Code / Codex ≈ 上述内核 + 厚工具层 + 上下文运营 + 权限/沙箱 + 工程 UX + 生态
```

**请在本目录 `python-langchain/` 下执行 uv / python 命令。**

---

## 从这里开始（启动说明 · uv）

所有命令在**本子项目目录** `python-langchain/` 执行（含 `pyproject.toml`、`uv.lock`、`simple_cli_agent/`）。

更短的一页速查：[`START.md`](./START.md)。

### 0. 确认 uv 可用

```bash
# 若提示 command not found，把 uv 加入 PATH（常见安装位置）
export PATH="$HOME/.local/bin:$PATH"
# 建议写入 ~/.zshrc 后执行: source ~/.zshrc

uv --version
uv python list          # 应能看到 cpython-3.14.6 ...
```

### 1. 同步环境并安装依赖（首次 / 依赖变更后）

```bash
cd /path/to/simple-cli-agents/python-langchain

# 指定 Python 3.14，安装项目 + 开发依赖（pytest）
uv sync --python 3.14 --extra dev

# 可选：固定本项目默认 Python
uv python pin 3.14
```

`uv sync` 会按 `uv.lock` 创建/更新 `.venv`，并把本包装进环境。  
之后**不必**再手写 `pip install -e .`（除非你坚持用传统 venv，见文末）。

### 2. 配置 API

```bash
cp -n .env.example .env
```

编辑 `.env`：

| 变量 | 含义 | 示例 |
|------|------|------|
| `OPENAI_API_KEY` | API 密钥（本地可填 `EMPTY`） | `sk-...` |
| `OPENAI_BASE_URL` | OpenAI **兼容**接口根地址 | `https://api.openai.com/v1` |
| `OPENAI_MODEL` | 模型名 | `gpt-4o-mini` |
| `WORKSPACE_ROOT` | 文件/ls/grep/edit 沙箱根；`run_command` 的 cwd | `.` |
| `SHELL_BLOCKED_PATTERNS` | 高危命令子串，用 `||` 分隔；`none` 关闭拦截 | 见 `.env.example` |
| `SHELL_TIMEOUT_SECONDS` | `run_command` 超时秒数 | `30` |
| `VERBOSE` / `VERBOSE_FULL` | 控制台逻辑轨迹 | `1` / `0` |
| `HTTP_LOG` / `HTTP_LOG_DIR` | HTTP JSONL 开关与目录 | `1` / `logs` |

本地兼容服务示例（LM Studio / Ollama OpenAI 端点）：

```bash
OPENAI_API_KEY=EMPTY
OPENAI_BASE_URL=http://localhost:1234/v1
OPENAI_MODEL=你的本地模型名
```

### 3. 启动 Agent（主入口）

```bash
# 推荐：uv 代管环境，直接跑模块入口
uv run python -m simple_cli_agent

# 或
uv run simple-cli-agent
```

临时覆盖配置：

```bash
uv run python -m simple_cli_agent \
  --base-url http://localhost:1234/v1 \
  --model local-model \
  --api-key EMPTY \
  --workspace .
```

### 4. 启动成功后你会看到

```text
[http-log] writing exchanges to .../logs/http-session-....jsonl
workspace: /你的/项目路径
model: gpt-4o-mini @ https://api.openai.com/v1
commands: exit | quit | Ctrl-C  —  type a message to chat

you>
```

示例对话：

```text
you> 用 ls 看一下当前目录
you> grep 一下 edit_file 在哪
you> 读一下 README.md 的前几行并总结
you> 用 edit_file 把 playground/hello.txt 里的 Hello 改成 Hi
you> 跑一下 pwd 和 ls
```

退出：`exit` / `quit` / `Ctrl-C`。本轮结束后会再次出现 `you>`。

### 5. 学习时看哪里

| 看什么 | 在哪 |
|--------|------|
| 逻辑层 messages / tool_calls | 终端 `>>>` / `<<<` |
| 原始 HTTP JSON | `logs/http-session-*.jsonl` |

```bash
uv run python -m simple_cli_agent --quiet        # 关 console 轨迹
uv run python -m simple_cli_agent --no-http-log  # 关 HTTP 日志
```

### 6. 跑测试（不需要 API Key）

```bash
uv run pytest -v
```

### 常见问题

| 现象 | 处理 |
|------|------|
| `uv: command not found` | `export PATH="$HOME/.local/bin:$PATH"`，并写入 shell 配置 |
| `OPENAI_API_KEY is required` | 配 `.env` 或 `--api-key`；本地可用 `EMPTY` |
| `No module named simple_cli_agent` | 在子项目根执行 `uv sync --extra dev`，再用 `uv run ...` |
| 用了系统 Python 3.9 | 务必 `uv sync --python 3.14` 或 `uv python pin 3.14` |
| 连不上 API | 检查 `OPENAI_BASE_URL` 是否含 `/v1`、本地服务是否启动 |
| `run_command` 被拦 | 对照 `SHELL_BLOCKED_PATTERNS` / 默认高危列表；学习用可改配置，**不建议** `none` |

### 可选：传统 venv + pip（不推荐优先）

```bash
python3.14 -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
python -m simple_cli_agent
```

---

## 功能（已实现）

- 终端 REPL，多轮上下文（进程内 `MemorySaver`，退出即丢）
- 本轮 agent 结束后等待下一次输入（turn-based）
- **Tools**（`WORKSPACE_ROOT` 下应用层路径门禁；非整机 OS 沙箱）：

| Tool | 说明 |
|------|------|
| `read_file` | 读文本；超大文件软截断 |
| `write_file` | 整文件覆盖写 |
| `edit_file` | 唯一匹配局部替换（0/多次匹配 → 不改文件） |
| `ls` | 非递归列目录；目录名带 `/` |
| `grep` | 应用层子串搜索；跳过 `.venv`/`target`/…；有命中上限 |
| `run_command` | cwd=工作区根；`CommandGuard` 子串拦截高危命令；超时与输出截断 |

- **Console**：逻辑层 LLM 请求/响应与 tool 轨迹（默认开）
- **HTTP 日志**：尽量还原 `chat/completions` 原始请求/响应 JSONL（默认开，密钥脱敏）

可验收：多轮、会调 tool、本轮结束交还用户、能在 HTTP 里对照 `tools` / `tool_calls`。

---

## 与 Claude Code / Codex 等的差距（尚未实现）

下面不是「本仓库的 bug 列表」，而是**相对完整 coding agent 产品，根能力之外通常还缺什么**。

### 1. 上下文管理（Context）

| 本仓库 | 产品级常见能力 |
|--------|----------------|
| 历史几乎整包进 messages | 压缩、摘要、按相关性取舍、token 预算 |
| 窗口靠模型/接口上限硬顶 | 主动 compact，避免长会话崩掉 |
| 靠模型自己 `read_file` / `grep` | 规则化注入：打开文件、diff、诊断、@ 引用、索引检索 |
| 固定一段 system prompt | 分层：全局说明 + 项目规则 + 动态状态 |

### 2. Memory（记忆）

| 本仓库 | 产品级常见能力 |
|--------|----------------|
| 进程内 transcript | 用户/项目级长期记忆、可编辑可遗忘 |
| 聊天记录 ≈ 全部状态 | 区分「对话原文」与「该长期影响行为的稳定信息」 |
| 无跨会话 | 会话恢复、续聊；常与项目规范文件联动 |

### 3. 权限与安全边界（Permissions / Sandbox）

| 本仓库 | 产品级常见能力 |
|--------|----------------|
| 文件 tool：路径 `resolve` + 工作区限制 | 按工具/路径/命令/域名的策略与确认流 |
| shell：可配置子串拦截 + 超时（**非** OS 沙箱） | Shell 常配合 OS 沙箱（Seatbelt / bubblewrap / 容器） |
| 一种运行模式 | 只读 / 工作区可写 / 高危全开等档位 |
| 学习用门禁 | 审计、策略下发、防 prompt injection 等（深浅不一） |

> 说明：本仓库的「沙箱」对**文件工具**是应用层路径门禁；对 **shell** 只挡高危子串，命令仍可触达 cwd 外路径。不是 Windows Sandbox / 容器那种独立执行环境。

### 4. 工具面（Tool surface）——产品差距里仍很大的一块

本仓库已有：读 / 写 / 唯一匹配编辑 / ls / 应用层 grep / 带策略 shell。产品级通常还有例如：

- 更严 shell（确认流、网络/路径白名单、OS 隔离）
- 语义/正则代码索引、大仓性能
- **真·补丁式编辑**（unified diff / 多 hunk，而非唯一子串或整文件覆盖）
- Git、测试/构建诊断、LSP
- Web/文档、MCP/插件扩展工具
- 计划、待办、子 agent 派发（视产品而定）

### 5. 任务编排与长程工作

- 多步任务拆解、进度与中断恢复  
- 子代理分工（探索 / 实现 / 评审等）  
- 与 issue、PR 等工作流衔接  

本仓库：单 agent，单轮内 tool 循环到说完为止。

### 6. 仓库理解与工程闭环

- 代码库索引、忽略规则、大仓性能  
- 改完跑测试/linter 再根据结果修改  
- monorepo、多根 workspace 习惯  

### 7. 产品交互（UX）

- 流式输出、可中断  
- diff 预览与接受/拒绝  
- 斜杠命令、会话管理、模型切换、IDE/@ 引用  

本仓库：`input()` + console/HTTP 轨迹，服务学习，不服务日用打磨。

### 8. 可靠性、成本与生态

- 重试、限流、循环熔断策略  
- prompt cache、费用提示、模型路由  
- Skills / Hooks / MCP 生态、团队共享规则  

### 层次一览（避免误解成「只差体验」）

```text
┌─────────────────────────────────────────────────────────┐
│  产品体验：流式、diff 预览、会话 UI、斜杠命令…            │  ← 好不好用
├─────────────────────────────────────────────────────────┤
│  工程可靠 / 安全：OS 沙箱、确认策略、熔断、审计…          │  ← 稳不稳、安不安全
├─────────────────────────────────────────────────────────┤
│  能力扩展：索引 / 真 patch / git / 测试 / MCP…           │  ← 能不能干活
├─────────────────────────────────────────────────────────┤
│  上下文运营与 Memory：压缩、检索、跨会话记忆…             │  ← 长会话与「记住」
├─────────────────────────────────────────────────────────┤
│  ★ 本仓库：agent 循环 + tool use + 文件/终端工具 + 可观测 │  ← 根（发动机）
└─────────────────────────────────────────────────────────┘
```

### 若只加深一条学习线（可选，非本仓库目标）

不必对齐 Claude Code；若继续练手，性价比通常较高的是：

1. 更严 shell 确认 / 输出策略  
2. 真·补丁编辑或多文件事务  
3. 简单上下文压缩 / 会话落盘  
4. 测试闭环（改完自动跑 pytest 再改）  

再往后才是子 agent、MCP、OS 沙箱等。

## 常用命令行参数

| 参数 | 含义 |
|------|------|
| `--base-url` | OpenAI 兼容 API 根 URL |
| `--model` | 模型名 |
| `--api-key` | API 密钥 |
| `--workspace` | 文件工具沙箱根（亦为 shell cwd） |
| `--quiet` | 关闭 console 轨迹 |
| `--verbose-full` | console 不截断长文本 |
| `--no-http-log` | 关闭 HTTP jsonl |
| `--http-log-dir` | HTTP 日志目录（默认 `logs`） |

> shell 拦截列表 / 超时目前通过环境变量配置（见上表），无 CLI 开关。

## 项目结构（入口在哪）

```text
python-langchain/                 ← 在这里执行命令
├── README.md / START.md
├── pyproject.toml / uv.lock
├── .env.example                  ← 复制为 .env
├── simple_cli_agent/             ← 主程序包
│   ├── __main__.py               ← python -m simple_cli_agent 入口
│   ├── cli.py                    ← REPL 主循环
│   ├── agent.py                  ← create_agent + SYSTEM_PROMPT
│   ├── config.py / model.py / ...
│   └── tools/
│       ├── files.py              ← FileWorkspace + read/write/edit/grep
│       ├── shell.py              ← CommandGuard + ShellRunner
│       └── agent_tools.py        ← 组装全部 tools（含 ls / run_command）
├── tests/                        ← pytest（含 edit/grep/shell/ls）
└── logs/                         ← 运行后生成 HTTP 日志（gitignore）
```

## 相关文档

- [`PROJECT_DNA.md`](../docs/python-langchain/PROJECT_DNA.md) — 决策约束
- [`HANDOFF.md`](../docs/python-langchain/HANDOFF.md) — 意图交接
- [Design spec](../docs/python-langchain/superpowers/specs/2026-07-12-simple-cli-agent-design.md) — 设计说明
- 共享 DNA：[`docs/PROJECT_DNA.md`](../docs/PROJECT_DNA.md)
- 父仓功能矩阵：[`../README_CN.md`](../README_CN.md)

## 范围外（v1 刻意不做）

下列默认**不在本仓库目标内**（与「差距」节一致）：

- 无 OS 沙箱的「任意 shell」、无策略拦截的生产级执行环境  
- 语义/正则代码索引、git / 测试运行器 / MCP  
- 真·补丁式编辑、流式输出、持久会话与产品级 Memory  
- OS 级沙箱、完整权限产品、子 agent  
- Anthropic 原生协议、OpenAI Responses 专用路径  
- 对齐 Claude Code / Codex 的完整体验与工程厚度  

> **已在范围内：** 应用层 `grep`、策略拦截的 `run_command`、`edit_file` / `ls`。

设计依据见 [`PROJECT_DNA.md`](../docs/python-langchain/PROJECT_DNA.md)、[`HANDOFF.md`](../docs/python-langchain/HANDOFF.md)。
