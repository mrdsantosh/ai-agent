# AI Agent — Banking Assistant

A conversational AI agent specialised in banking, built with Spring AI and a locally-running Ollama LLM. The agent maintains multi-tier persistent memory across conversations so it can recall user preferences and prior context on every session.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| AI Orchestration | Spring AI 1.1 |
| LLM | Ollama — `gemma4:e2b` (local, no cloud required) |
| Streaming | Project Reactor (`Flux`) / SSE |
| Persistence | PostgreSQL 16 + pgvector |
| Chat Memory | Spring AI JDBC Chat Memory Repository |
| UI | Thymeleaf + server-sent events |
| Observability | Micrometer, Prometheus, Grafana |
| Dev Infrastructure | Docker Compose (auto-managed by Spring Boot) |

---

## Architecture

```
Browser
  │  GET /           → Thymeleaf chat UI
  │  POST /api/chat/message    (streaming)
  │  POST /api/chat/summarize
  ▼
WebViewController / ChatController
  ▼
ChatService  ──────────────────────────────────────────────────────┐
  │  builds prompt from full conversation history                  │
  ▼                                                                │
ChatMemoryService  (three-tier memory)                             │
  ├── Session Memory   — last 20 messages (active conversation)    │
  ├── Context Memory   — up to 10 AI-generated summaries           │
  └── Preferences Memory — 1 merged user profile record            │
        │ all tiers backed by                                       │
        ▼                                                          │
  JdbcChatMemoryRepository (PostgreSQL)                            │
                                                                   │
ConversationSummaryService  ◄──────────────────────────────────────┘
  │  on /summarize: reads session, calls LLM to produce
  │  ===PREFERENCES=== and ===CONTEXT=== sections,
  │  persists to context/preferences tiers, clears session
  ▼
Ollama (localhost:11434)
```

### Memory Flow

```
New session
  └─► load preferences + past summaries from DB
        └─► ask LLM to compress them into a single context message
              └─► inject as SystemMessage into session → chat begins

End of session  (/api/chat/summarize)
  └─► extract preferences (static user info)
  └─► extract context (topics, decisions, pending items)
  └─► save both to DB, clear session memory
```

---

## Features

- **Streaming responses** — answers stream token-by-token to the browser via `Flux<String>`.
- **Three-tier persistent memory** — session, summarised context, and user preferences are stored in PostgreSQL and survive restarts.
- **Automatic context injection** — on the first message of a new session the agent silently loads and compresses prior context so conversations feel continuous.
- **Conversation summarisation** — `POST /api/chat/summarize` triggers an LLM-powered summary that separates static preferences from dynamic context and persists both.
- **Multi-user support** — each user gets an isolated memory namespace; the `userId` is resolved from Spring Security `Principal` in production or from the request body in development.
- **Fully local LLM** — no data leaves the machine; Ollama serves the model on `localhost:11434`.
- **Observability** — token usage metrics (`gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`) are emitted via Micrometer and scraped by Prometheus; dashboards are available in Grafana at `http://localhost:3000`.
- **Zero-config infrastructure** — Docker Compose is started and stopped automatically by Spring Boot; containers for PostgreSQL, pgAdmin, Prometheus, and Grafana are all managed.

---

## Running Locally

### Prerequisites

- Java 21
- Maven
- [Ollama](https://ollama.com) with the `gemma4:e2b` model pulled (`ollama pull gemma4:e2b`)
- Docker Desktop (running)

### Start

```bash
mvn spring-boot:run
```

Spring Boot will automatically start all Docker Compose services (PostgreSQL, pgAdmin, Prometheus, Grafana) and initialise the database schema.

| Service | URL |
|---|---|
| Chat UI | http://localhost:8080 |
| pgAdmin | http://localhost:5050 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
| Actuator | http://localhost:8080/actuator |

### Stop (graceful)

```bash
curl -X POST http://localhost:8080/actuator/shutdown
```

This stops the app cleanly and brings down all Docker Compose containers.

---

## API

### `POST /api/chat/message`
Stream a chat response.

```json
{ "prompt": "What are the cheapest flights to Rome?", "userId": "alice" }
```

Returns a streaming octet-stream of text chunks.

### `POST /api/chat/summarize`
Summarise the current session, persist preferences and context to the database, and clear the session.

```json
{ "userId": "alice" }
```
