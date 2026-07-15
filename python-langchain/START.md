# 启动速查（uv + Python 3.14）

> 完整说明见 [README.md](./README.md)。

## 前置

确认 `uv` 在 PATH 里（你已装在 `~/.local/bin/uv` 时）：

```bash
# 若 `uv --version` 找不到，先加 PATH（可写进 ~/.zshrc）
export PATH="$HOME/.local/bin:$PATH"

uv --version
# 应能看到：uv 0.x.x ...
```

你的 Python：`3.14.6`（可用 `uv python list` 查看）。

---

## 三步启动（推荐 uv 方式）

在**本子项目目录** `python-langchain/`（有 `pyproject.toml` / `uv.lock`）：

```bash
cd /path/to/simple-cli-agents/python-langchain

# 1) 用 3.14 创建/同步虚拟环境并安装本项目 + 开发依赖
uv sync --python 3.14 --extra dev

# 2) 配置密钥（首次）
cp -n .env.example .env
# 编辑 .env：OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_MODEL

# 3) 启动主入口（uv run 会自动用项目 .venv）
uv run python -m simple_cli_agent
```

看到 `you>` 就可以对话。退出：`exit` / `quit` / `Ctrl-C`。

等价入口：

```bash
uv run simple-cli-agent
```

---

## 常用 uv 命令对照

| 目的 | 命令 |
|------|------|
| 装依赖（含 pytest） | `uv sync --python 3.14 --extra dev` |
| 只装运行依赖 | `uv sync --python 3.14` |
| 跑 Agent | `uv run python -m simple_cli_agent` |
| 跑测试 | `uv run pytest -v` |
| 指定本地模型 | 见下方 |
| 看当前 venv 的 Python | `uv run python -V` |

### 命令行覆盖配置（不改 .env）

```bash
uv run python -m simple_cli_agent \
  --base-url http://localhost:1234/v1 \
  --model local-model \
  --api-key EMPTY
```

### 可选：固定项目用 3.14

```bash
uv python pin 3.14
uv sync --extra dev
```

会在目录生成/更新 `.python-version`，之后 `uv sync` / `uv run` 默认跟 3.14。

---

## 和旧的 venv/pip 方式关系

- **推荐**：`uv sync` + `uv run ...`（读 `uv.lock`，可复现）
- 不必再 `source .venv/bin/activate` 才能跑（`uv run` 会替你选环境）
- 若你手动 activate：`source .venv/bin/activate` 后也可直接 `python -m simple_cli_agent`

---

## 主入口是什么

| 方式 | 命令 |
|------|------|
| uv + 模块（推荐） | `uv run python -m simple_cli_agent` |
| uv + 脚本 | `uv run simple-cli-agent` |
| 代码位置 | `simple_cli_agent/__main__.py` → `cli.main()` |

## 只跑测试（不需要 Key）

```bash
uv sync --python 3.14 --extra dev
uv run pytest -v
```
