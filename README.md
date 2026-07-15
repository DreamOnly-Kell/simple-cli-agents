# simple-cli-agents

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

Minimal **terminal code-agent** demos for learning **LLM agent + tool use** — the same “core loop” implemented twice:

| Subproject | Stack |
|------------|--------|
| [`python-langchain/`](./python-langchain/) | Python + LangChain |
| [`java-spring-ai/`](./java-spring-ai/) | Java + Spring AI |

> **Chinese documentation:** [README_CN.md](./README_CN.md)

---

## What this is (and is not)

**One-line core idea**

```text
user ↔ model ↔ tool_calls ↔ run tools ↔ feed results back ↔ decide again
     → turn ends → wait for next user input
```

This monorepo ships the **smallest runnable “engine”** of a coding agent — not a Claude Code / Codex-class product.

| Layer | Role |
|-------|------|
| **This repo** | Agent loop + native tool calling + file read/write + observability |
| **Product agents** | That engine **plus** tool surface, context ops, permissions/sandbox, reliability, UX |

Later features (search, shell, OS sandbox, memory products, polish) are **capability, safety, and product** — not “UX only.”

---

## Core ideas

1. **Root capability first** — Learn the real turn cycle before stacking product features.  
2. **Framework wrappers first** — Use LangChain / Spring AI tool-calling agents; don’t hand-roll a runtime in v1.  
3. **Thin scope** — Multi-turn chat, two file tools, turn-based handoff, observability.  
4. **OpenAI-compatible only** — Point `base_url` at any compatible endpoint (OpenAI, gateways, local servers).  
5. **Twin implementations** — Same behavior slice, different frameworks, easy to compare.  
6. **Path jail, not OS sandbox** — Workspace root checks only; not containers / Seatbelt / bubblewrap.  

Shared decision constraints: [`docs/PROJECT_DNA.md`](./docs/PROJECT_DNA.md).

---

## How it works

### Agent loop (both stacks)

```text
┌─────────────────────────────────────────────────────────┐
│  CLI REPL                                                │
│  read line → one agent/ChatClient call → print → wait    │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│  Framework tool-calling loop (may multi-hop in one turn) │
│  1. Send messages + tools schema                         │
│  2. LLM may return tool_calls                            │
│  3. Runtime executes tools locally                       │
│  4. Append tool results → call LLM again                 │
│  5. Stop when no more tool_calls → return final text     │
└───────────────────────────┬─────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
     File tools (sandbox)        Observability
     read / write                logic trace + HTTP JSONL
```

### Tools on the wire (not in `content`)

OpenAI-compatible requests expose tools as a **top-level `tools` array** (function schemas). The model’s intent appears as **`tool_calls`**; results return as **`role: tool`** messages. Tool definitions are not stuffed into free-form `content` text.

### File tools

- Resolve paths under a workspace root; reject escapes (`../`, absolute paths outside root).  
- UTF-8 read (optional truncate); write = full-file overwrite.  
- Errors return strings like `Error: ...` so the model can recover.

### Multi-turn

- In-process memory only (lost on process exit).  
- Python: checkpointer + `thread_id`.  
- Java: `ChatMemory` + `conversationId`.

### Observability (learning-oriented)

| Channel | Purpose |
|---------|---------|
| **Logic trace** | Turns, tool names/args/results (console / Logback) |
| **HTTP JSONL** | Near-raw `chat/completions` exchanges (redacted secrets) under `logs/` |

---

## Repository layout

```text
simple-cli-agents/
├── README.md / README_CN.md
├── LICENSE
├── docs/                      # design, plans, DNA (not app code)
├── python-langchain/          # run commands here
└── java-spring-ai/            # run commands here
```

Always **`cd` into a subproject** before run/build so workspace and config paths stay correct.

---

## Feature matrix

Same acceptance line: **multi-turn · tool use · wait for user · observable**.

| Feature | Python | Java |
|---------|--------|------|
| Terminal multi-turn REPL | `cli.py` + memory | `ReplRunner` + `ChatMemory` |
| End-of-turn returns control | after `invoke` | after `ChatClient.call()` |
| Read file tool | `read_file` | `readFile` (`@Tool`) |
| Write file tool (overwrite) | `write_file` | `writeFile` |
| Workspace path jail | `FileWorkspace` | `FileWorkspace` |
| OpenAI-compatible Chat Completions | `ChatOpenAI` | Spring AI OpenAI starter |
| System prompt | `SYSTEM_PROMPT` | `AiConfig.SYSTEM_PROMPT` |
| Logic trace | console callbacks | Logback `cli.trace` |
| HTTP JSONL (redacted) | httpx hooks | RestClient interceptor |
| Config | `.env` + CLI | `application.yml` / `application-local.yml` (**no `.env` file**) |
| Unit tests (no live LLM) | pytest | JUnit 5 |

**Out of scope (both):** shell, search, git, patch edits, OS sandbox, streaming-first UX, durable product memory, multi-agent, MCP, Claude Code / Codex feature parity.

---

## Framework mapping

| Concept | Python | Java |
|---------|--------|------|
| Model | `ChatOpenAI` | Spring AI OpenAI + `ChatClient` |
| Agent loop | `create_agent` | `ChatClient` + tool calling |
| Tool API | `StructuredTool` | `@Tool` |
| Multi-turn | `MemorySaver` | `ChatMemory` |
| Config | `.env` | Spring config files |
| Observability | console + JSONL | Logback + JSONL |

---

## Quick start

### Python

```bash
cd python-langchain
uv sync --extra dev
cp -n .env.example .env   # set OPENAI_API_KEY, OPENAI_BASE_URL, OPENAI_MODEL
uv run python -m simple_cli_agent
```

Details: [`python-langchain/START.md`](./python-langchain/START.md)

### Java

```bash
cd java-spring-ai
# Create application-local.yml (gitignored) or edit application.yml; set:
#   spring.ai.openai.api-key / base-url (no trailing /v1) / model
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Details: [`java-spring-ai/START.md`](./java-spring-ai/START.md)

> **base-url:** Spring AI calls `{base-url}/v1/chat/completions`. Use host root (e.g. `https://api.openai.com`), not `.../v1`. Do not swap `api-key` and `base-url`.

---

## Documentation

| Doc | Description |
|-----|-------------|
| [README_CN.md](./README_CN.md) | Chinese version of this README |
| [docs/PROJECT_DNA.md](./docs/PROJECT_DNA.md) | Shared decision constraints |
| [docs/python-langchain/](./docs/python-langchain/) | Python handoff, plan, design |
| [docs/java-spring-ai/](./docs/java-spring-ai/) | Java DNA + implementation plan |
| Subproject `README.md` / `START.md` | Stack-specific setup |

---

## License

[MIT](./LICENSE)
