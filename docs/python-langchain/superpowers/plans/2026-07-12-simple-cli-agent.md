# Simple CLI Agent Implementation Plan（python-langchain）

> **Status:** v1 **implemented** under monorepo path `simple-cli-agents/python-langchain/`  
> **Code:** [`../../../../python-langchain/`](../../../../python-langchain/)  
> Historical task breakdown; see also [`../../PLAN.md`](../../PLAN.md).

**Goal:** Minimal terminal code agent (LangChain + OpenAI-compatible) with read/write tools, multi-turn REPL, console traces, and HTTP jsonl logs.

**Architecture:** Single `create_agent` path; `ChatOpenAI` only; httpx hooks for wire logs; callbacks for console; workspace-sandboxed file tools.

**Tech Stack:** Python 3.11+, langchain, langchain-openai, httpx, python-dotenv, pytest.

## Global Constraints

- OpenAI-compatible Chat Completions only (no Anthropic / Responses-first path).
- No streaming required in v1.
- DNA: thin, learning-first, turn-based wait for user.

---

### Task 1: Project scaffold + file tools (TDD) ✅

**Files:** `pyproject.toml`, `simple_cli_agent/tools/files.py`, `tests/test_files.py`, `.gitignore`

### Task 2: HTTP logging redaction (TDD) ✅

**Files:** `simple_cli_agent/http_logging.py`, `tests/test_http_logging.py`

### Task 3: Config + model + tracing + agent + CLI ✅

**Files:** `config.py`, `model.py`, `tracing.py`, `agent.py`, `cli.py`, `__main__.py`

### Task 4: README + .env.example + verify tests ✅

---

Details follow the design spec: [`../specs/2026-07-12-simple-cli-agent-design.md`](../specs/2026-07-12-simple-cli-agent-design.md).
