# Simple CLI Agent — Design Spec

> **Status:** Approved and implemented (v1) under monorepo `simple-cli-agents/python-langchain`  
> **Aligns with:** [`PROJECT_DNA.md`](../../PROJECT_DNA.md), [`HANDOFF.md`](../../HANDOFF.md), [`PLAN.md`](../../PLAN.md); shared DNA [`../../../PROJECT_DNA.md`](../../../PROJECT_DNA.md)  
> **Architecture choice:** Approach A (single LangChain agent path), OpenAI-compatible only  
> **Code:** [`../../../../python-langchain/`](../../../../python-langchain/)

---

## 1. Purpose

Build a **minimal terminal code-assistant demo** in Python + LangChain so the author can **learn and verify** LLM agent / tool-use theory through a runnable loop.

Success is **walk-through + observability**, not product completeness.

---

## 2. Goals and Non-Goals

### Goals (v1 Done)

1. Multi-turn terminal chat (in-process history).
2. Tool use: **read_file** and **write_file** inside a workspace sandbox.
3. Turn-based control: after the model finishes a turn (no more tool calls / agent invoke returns), CLI **blocks on next user input**.
4. **Dual observability:**
   - **Console:** logical LLM I/O + tool trace (learning-oriented).
   - **HTTP log files:** as close as practical to raw OpenAI-compatible HTTP request/response.
5. Single model integration surface: **OpenAI-compatible Chat Completions** only (`base_url` + `api_key` + `model`).

### Non-Goals (deferred; later branches if needed)

- Anthropic native API, OpenAI Responses API as a separate product path.
- Per-provider runtimes (former Approach B).
- Shell execution, code search, git tools, multi-agent, durable sessions.
- Streaming responses.
- Rich TUI, permission product UX, Claude Code–level features.
- Hand-rolled agent runtime (use LangChain encapsulation first — DNA-02).

---

## 3. Constraints (from Project DNA)

| ID | Constraint |
|----|------------|
| DNA-01 | Learning/verification first |
| DNA-02 | LangChain wrappers first; no custom runtime v1 |
| DNA-03 | Thin scope; Pi-like lightness, not Claude Code |
| DNA-04 | Terminal mini code assistant: LLM + read/write tools |
| DNA-05 | Multi-turn + end-of-turn returns control to user |
| DNA-06 | “Works with clear feel” is enough |

---

## 4. Architecture

### 4.1 Overview

```
CLI (REPL)
  → config (env + CLI overrides)
  → model factory (ChatOpenAI, OpenAI-compatible only)
       ↳ httpx client with HTTP logging hooks → logs/*.jsonl
  → tools (read_file, write_file + workspace root sandbox)
  → LangChain tool-calling agent (single path)
       ↳ console VerboseCallback → stdout
  → invoke returns → print final text → wait for input()
```

One agent loop for all backends. Different OpenAI-compatible servers (official OpenAI, DeepSeek, LM Studio, Ollama OpenAI endpoint, etc.) differ **only by configuration** (`OPENAI_BASE_URL`, key, model)—not by separate code paths.

### 4.2 Components

| Module | Responsibility |
|--------|----------------|
| `cli.py` | Argparse, REPL loop, exit handling, wire verbose/http-log flags |
| `config.py` | Load `.env`, defaults, merge CLI overrides |
| `model.py` | Build `ChatOpenAI` + inject instrumented `httpx` client |
| `agent.py` | Bind model + tools + system prompt; create agent; attach console callback |
| `tools/files.py` | `read_file` / `write_file` with path sandbox |
| `tracing.py` | Console formatting (`>>>` / `<<<` / tool lines) |
| `http_logging.py` | Capture HTTP exchange, redact secrets, append jsonl |
| `__main__.py` | `python -m simple_cli_agent` entry |

### 4.3 Package layout

```
simple_cli_agent/
  __init__.py
  __main__.py
  cli.py
  config.py
  model.py
  agent.py
  tracing.py
  http_logging.py
  tools/
    __init__.py
    files.py
tests/
  test_files.py
  test_http_logging.py   # redaction + path helpers (no live LLM required)
pyproject.toml
.env.example
README.md
logs/                    # gitignored; runtime HTTP logs
```

Dependency set (indicative): `langchain`, `langchain-openai`, `openai`, `httpx`, `python-dotenv`; dev: `pytest`.

Pin versions at implement time to a known-good set; use the **current LangChain-recommended single API** for tool-calling agents (e.g. `create_agent` or equivalent stable entry)—one path only.

---

## 5. Configuration

### 5.1 Environment (`.env.example`)

```bash
OPENAI_API_KEY=sk-...
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-4o-mini
WORKSPACE_ROOT=.

# Observability (learning defaults: on)
VERBOSE=1
VERBOSE_FULL=0
HTTP_LOG=1
HTTP_LOG_DIR=logs
```

### 5.2 CLI overrides

```text
python -m simple_cli_agent \
  --base-url http://localhost:1234/v1 \
  --model local-model \
  --workspace . \
  --verbose | --quiet \
  --http-log | --no-http-log \
  --http-log-dir logs
```

No `--provider` enum: compatibility is entirely `base_url` + key + model.

---

## 6. Runtime behavior

### 6.1 REPL (turn-based)

1. Print prompt; read user line.
2. `exit` / `quit` / EOF → terminate cleanly; Ctrl-C → clean exit.
3. Pass user text into agent for **one turn** (agent may run multiple model↔tool steps internally).
4. When agent invoke completes, print final assistant text (if not already shown via callbacks).
5. **Do not** auto-start another model turn; block on next `input()`.

### 6.2 Multi-turn history

Keep conversation state **in process** for the session. Prefer the shortest LangChain-supported pattern that preserves Human / AI / Tool messages across turns (agent-managed state or explicit message list passed each invoke). No disk persistence of chat history in v1.

### 6.3 System prompt (minimal)

Instruct the model that it is a small coding assistant with `read_file` and `write_file` only; prefer tools for file content; stay inside the workspace; be concise.

---

## 7. Tools

### 7.1 `read_file(path: str) -> str`

- Resolve `path` against `WORKSPACE_ROOT`.
- Reject paths that escape the root after `resolve()` (return error string to the model, do not crash CLI).
- Read text (UTF-8); on failure return error string.
- Optional soft truncation for extremely large files with a note of original length (console already truncates display; tool may return full content up to a generous cap, e.g. 100_000 chars, with notice).

### 7.2 `write_file(path: str, content: str) -> str`

- Same sandbox rules as read.
- Create parent directories as needed.
- **Overwrite** entire file (no diff/patch engine).
- Return a short success message (path + byte/char count).

### 7.3 Out of tool scope

Shell, search, delete-tree, network—v1 does not expose them.

---

## 8. Observability

### 8.1 Console (logical trace)

**Default on** (`VERBOSE=1`). `--quiet` disables.

Via LangChain callbacks (or equivalent hooks):

- On model start: dump logical input (messages summary/full, tool names).
- On model end: dump content + `tool_calls`.
- On tool start/end: name, args, result summary.

**Format (normative style):**

```text
──────── turn N ────────
>>> LLM request
  ...
<<< LLM response
  content: ...
  tool_calls: ...
>>> tool read_file
  args: {...}
  result: (N chars) "..."
assistant> ...
you>
```

- Truncate long fields unless `VERBOSE_FULL=1`.
- Never print API keys.

### 8.2 HTTP log files (wire-level)

**Default on** (`HTTP_LOG=1`). `--no-http-log` disables.

**Mechanism:** custom `httpx.Client` (or `AsyncClient` if stack requires—prefer sync for v1 simplicity) with request/response hooks, injected into the OpenAI client used by `ChatOpenAI`.

**Capture per exchange:**

- Request: method, URL, headers, body (parsed JSON if possible, else raw text).
- Response: status code, headers, body (parsed JSON if possible).

**Storage:** append-only JSONL under `HTTP_LOG_DIR`, e.g.  
`logs/http-session-YYYYMMDD-HHMMSS.jsonl`  
one line = one request/response pair.

**Redaction (required):**

- `Authorization`, `api-key`, `x-api-key` → placeholder (`Bearer ***` / `***`).
- Do not write secrets in plain text.

**Explicit limitations:**

- Logs reflect HTTP as sent/received by the SDK (field order / default fields may differ from a hand-written curl).
- v1 is **non-streaming** so bodies are complete and readable.
- Local file tool I/O is **not** HTTP and appears only on the console tool trace.

### 8.3 Independence

Console verbose and HTTP logging are independently toggleable.

---

## 9. Error handling

| Failure | Behavior |
|---------|----------|
| Path escape / IO error in tools | Return error string to model; CLI continues |
| LLM / network / HTTP 4xx–5xx | Print error to console; log response if any; **stay in REPL** |
| Missing API key | Fail fast at startup with clear message |
| Invalid config | Fail fast at startup |

---

## 10. Testing

### Automated (no live LLM required)

- Workspace sandbox: allow in-root paths; reject `..` escape.
- `write_file` / `read_file` round-trip in a temp directory.
- HTTP log redaction: authorization header never stored raw.
- JSONL writer creates parent dirs and appends valid lines.

### Manual checklist (live API or local OpenAI-compatible server)

| # | Action | Expect |
|---|--------|--------|
| 1 | Chitchat | No unnecessary tools; turn ends; waits |
| 2 | Read existing file | `read_file` on console + HTTP log shows tool_calls; answer uses content |
| 3 | Write/overwrite file | Disk changes; console + HTTP show write tool |
| 4 | Second turn refers to first | Context preserved |
| 5 | Inspect `logs/*.jsonl` | Full chat/completions request/response JSON visible (redacted auth) |

---

## 11. Implementation notes for the next phase

1. Prefer **sync** REPL + sync agent invoke for simplicity.
2. Disable streaming on the chat model so HTTP bodies stay whole.
3. Gitignore `logs/`, `.env`, and virtualenvs.
4. README: how to point `OPENAI_BASE_URL` at OpenAI / DeepSeek / LM Studio / Ollama-compatible endpoints with one codepath.
5. Later branches may add Anthropic, Responses API, or Approach B—**without** requiring v1 redesign if factory boundaries stay thin (`model.py` / `http_logging.py` only).

---

## 12. Traceability

| Requirement source | Spec section |
|--------------------|--------------|
| Handoff multi-turn + tools + wait | §2, §6, §7 |
| DNA thin / LangChain first | §2 Non-Goals, §4 |
| OpenAI-compatible only (user decision) | §2, §4, §5 |
| Console logical trace (user) | §8.1 |
| HTTP raw-ish log files (user) | §8.2 |
| Approach A simplified (user) | §4 |

---

## 13. Approval

- Architecture Approach A + OpenAI-compatible only: **approved in chat**.
- Dual observability (console + HTTP jsonl): **approved in chat**.
- This file: **pending user review of the written spec**.
