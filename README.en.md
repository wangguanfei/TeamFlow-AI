# TeamFlow AI Enterprise Collaboration Platform

> AI-native enterprise collaboration platform with RBAC, knowledge RAG, workflow orchestration, real-time notification, file asset management, and cloud-native delivery.

English | [中文](./README.md)

<table>
  <tr>
    <td width="50%"><img src="./login.jpg" alt="TeamFlow AI login screen" width="100%" /></td>
    <td width="50%"><img src="./ai.jpg" alt="TeamFlow AI assistant screen" width="100%" /></td>
  </tr>

</table>

## Live Demo

- Demo URL: <http://81.69.248.152/login>
- Demo account: `demo` / `demo123456` (read-only demo account)

## Overview

TeamFlow AI is an AI-native enterprise collaboration platform built for teams, engineering organizations, and knowledge-intensive workflows. It is more than an admin dashboard template. It provides a full-stack application foundation that connects authentication, RBAC, project collaboration, task workflow, knowledge management, file assets, AI assistants, real-time notifications, and cloud-native deployment.

The project is designed as a production-minded SaaS backend and frontend system. It features clear domain boundaries, strong permission control, consistent API contracts, and end-to-end business flows that can be demonstrated, studied, extended, and self-hosted.

## Highlights

- **AI-native collaboration**: chat, knowledge Q&A, document summary, code generation, SQL assistant, DeepSeek / OpenAI-compatible provider, and MockAIProvider fallback.
- **Complete RBAC pipeline**: JWT, Spring Security, role-permission model, dynamic menus, button-level permissions, and API-level authorization, with user permissions cached in Redis to eliminate repeated lookups in the JWT filter on every request.
- **Lightweight local RAG**: Qdrant vector database + bge-small-zh local Embedding service + BM25 keyword retrieval + RRF fusion ranking, runnable on 2C2G; Markdown, TXT, PDF, and DOCX import, automatic slice refresh on publish, end-to-end knowledge Q&A.
- **Project and task workflow**: projects, members, tags, task list, Kanban drag-and-drop, Gantt view, comments, worklogs, attachments, and executors.
- **Enterprise file center**: MinIO storage, upload, preview, download, sharing, business archive, and large-file support.
- **Real-time notification**: unread badges, search, read status, deletion, and WebSocket push.
- **Production-minded engineering**: unified API result, global exception handling, TraceId, OpenAPI, Docker Compose, Nginx reverse proxy, and health checks.
- **Open-source ready**: modular architecture, bootstrap data, typed frontend clients, and practical deployment scripts.

## Architecture

```text
Browser
  |
  |  Vue 3 + TypeScript + Vite + Pinia + Element Plus
  v
Nginx / Vite Dev Proxy
  |
  |  REST API / WebSocket / SSE
  v
Spring Boot 3 Application
  |
  |-- Spring Security + JWT + RBAC
  |-- Project / Task / Knowledge / File / AI / Notification Modules
  |-- MyBatis-Plus Data Access
  |
  |-- MySQL 8           relational data
  |-- Redis             permission cache and runtime support
  |-- MinIO             object storage
  |-- AI Provider       DeepSeek / OpenAI compatible / Mock fallback
  |-- Qdrant            vector database (RAG)
  |-- Embedding Service bge-small-zh local embedding (RAG)
```

## Tech Stack

| Layer | Technologies |
| --- | --- |
| Frontend | Vue 3, TypeScript, Vite, Pinia, Vue Router, Element Plus, Axios, ECharts, MdEditorV3, SortableJS |
| Backend | Java 17, Spring Boot 3, Spring Security, JWT, WebSocket, Validation, springdoc-openapi |
| Data | MySQL 8, Redis, MyBatis-Plus |
| File Storage | MinIO, multipart upload, 500MB upload limit |
| AI | OpenAI-compatible HTTP client, DeepSeek-compatible config, MockAIProvider fallback |
| RAG | Qdrant vector database, bge-small-zh local Embedding service, BM25 keyword retrieval, RRF fusion ranking |
| Deployment | Docker, Docker Compose, Nginx, health checks, reverse proxy, deploy-agent.sh host proxy |

## Feature Map

### Authentication and Permissions

- Username and password login, token refresh, and unified 401 handling.
- Self-registration is disabled for enterprise backend usage.
- Full user, role, permission, and menu management.
- Dynamic menus, button permissions, and API permissions.
- Read-only demo account `demo` is protected by backend enforcement, with only AI chat writes allowed under the daily quota.

### Dashboard

- Project, task, knowledge document, and AI message statistics.
- Project trend, member activity, and AI usage distribution.
- Aggregated current to-do tasks.
- One-click project creation from the dashboard.

### Project Collaboration

- Project CRUD, member management, and tag management.
- Newly created projects are highlighted and followed by next-step actions.
- Project statistics, detail drawer, and member roles.

### Task Workflow

- Task list, Kanban board, and Gantt view.
- Kanban drag-and-drop status updates.
- Assignee and multiple executors are modeled separately.
- Comments, worklogs, attachments, and tags.
- Task changes can trigger notifications.

### Knowledge Base and RAG

- Knowledge space create, edit, and delete; document tree management.
- Markdown document create, edit, and delete.
- Publishing, version history, rollback, and favorites.
- Markdown, TXT, PDF, and DOCX import.
- Published documents automatically refresh AI retrieval slices for knowledge Q&A.
- Lightweight local RAG: Qdrant vector store + bge-small-zh local Embedding + BM25 keyword retrieval + RRF fusion ranking, runnable on 2C2G.
- `/api/rag/status` health check, `/api/rag/rebuild` manual index rebuild.
- `RAG_ENABLED` / `RAG_LOCAL_EMBEDDING` feature flags for mode switching without code changes.

### File Center

- MinIO file upload, download, preview, and deletion.
- Batch file upload.
- Business type and business ID archiving.
- File sharing.
- 500MB upload limit with Nginx large-file proxy support.

### AI Assistant

- General chat.
- Knowledge Q&A.
- Document summary.
- Code generation.
- SQL assistant.
- DeepSeek / OpenAI-compatible API.
- MockAIProvider fallback for fully offline demos without an API key.

### System Administration

- User management: create, edit, disable, and role assignment.
- Role management: role CRUD and permission binding.
- Permission management: maintain permission codes, names, and resource paths.
- Menu management: maintain frontend dynamic routes, icons, and permission codes.
- Operation log: AOP `@Log` annotation records all write operations automatically; query by operator, time range, and module.
- Deploy management: trigger `deploy.sh` with one click from the admin page, with real-time SSE log streaming; requires `deploy-agent.sh` installed on the host and `DEPLOY_ENABLED=true`.

### Notification and Profile Center

- Notification pagination, search, unread-only filtering, mark all as read, and deletion.
- WebSocket real-time push.
- Unread badge.
- Avatar upload, profile update, password change, permission summary, and preferences.

## Project Structure

```text
.
├── backend
│   ├── src/main/java/com/teamflow/ai
│   │   ├── common              # API result, exception, security, trace, config
│   │   └── modules             # auth, user, system, project, task, knowledge, file, ai, notification, rag
│   └── src/main/resources
│       ├── application.yml
│       └── db/schema.sql       # runtime schema and demo data bootstrap
├── frontend
│   └── src
│       ├── api                 # typed API clients
│       ├── components          # shared UI components
│       ├── layouts             # main shell
│       ├── router              # dynamic route sync
│       ├── stores              # Pinia stores
│       └── views               # feature pages
├── embedding-service           # bge-small-zh local Embedding service (Flask + sentence-transformers)
├── deploy/nginx                # Nginx reverse proxy configs
├── deploy-agent.sh             # host-side deploy agent, triggered by the backend to run deploy.sh
├── docker-compose.yml
├── docker-compose.local.yml    # local Docker verification (reuses host MySQL/Redis)
├── README.md
└── README.en.md
```

## Quick Start

### Requirements

- JDK 17+
- Maven 3.9+
- Node.js 20+
- MySQL 8
- Redis 7+
- MinIO

Default local connections:

| Service | Address |
| --- | --- |
| MySQL | `127.0.0.1:3306/teamflow_ai` |
| Redis | `127.0.0.1:6379` |
| MinIO API | `http://127.0.0.1:9000` |
| MinIO Console | `http://127.0.0.1:9001` |
| Qdrant | `http://127.0.0.1:6333` |
| Embedding Service | `http://127.0.0.1:8000` |

### Start the Backend

```bash
cd backend
mvn spring-boot:run
```

The backend runs as a JAR without hot reload, so repackage and restart after changing Java code. For local development, prefer `start-local.sh`, which injects the AI environment variables automatically so a restart never silently falls back to MockAIProvider:

```bash
# Export your real key in ~/.zshrc first (the script itself contains no key)
export AI_API_KEY=sk-your-real-key

cd backend
./start-local.sh --build   # mvn package, then restart
./start-local.sh           # restart with the existing jar
```

Backend URL:

```text
http://localhost:8080
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

### Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend URL:

```text
http://localhost:5173
```

If the backend does not use port `8080`:

```bash
VITE_PROXY_TARGET=http://127.0.0.1:18080 npm run dev
```

## Default Accounts

Bootstrap data is created automatically by `DemoDataInitializer` when the application starts.

```text
Admin username: admin
Admin password: admin123456

Developer username: dev
Developer password: dev123456

Read-only demo username: demo
Read-only demo password: demo123456
```

Account roles:

- `admin`: super administrator with full system management permissions.
- `dev`: developer role for project, task, knowledge, file, AI, and notification demos.
- `demo`: read-only viewer. AI chat can use the real provider and RAG with saved chat history under a daily account quota; other create, update, delete, upload, and read-state write requests are still blocked by the backend.

## AI Configuration

The backend uses an OpenAI-compatible HTTP API. Configuration is under `teamflow.ai` and can also be overridden through environment variables:

```bash
export AI_PROVIDER=deepseek
export AI_BASE_URL=https://api.deepseek.com/v1
export AI_API_KEY=your-api-key
export AI_MODEL=deepseek-chat
export AI_DEMO_DAILY_LIMIT=100
```

Notes:

- `AI_PROVIDER` can be `deepseek`, `openai`, or another compatible provider label.
- `AI_BASE_URL` must point to a service compatible with `/chat/completions`.
- `AI_DEMO_DAILY_LIMIT` controls the demo account's daily AI call limit. It defaults to `100` and resets by the Asia/Shanghai calendar day.
- If `AI_API_KEY` is missing or the upstream provider fails, the application falls back to `MockAIProvider`.
- Do not commit real API keys. For production, inject secrets through `.env`, container environment variables, or cloud secret management.

## Docker Compose Deployment

Copy the environment template:

```bash
cp .env.example .env
```

Fill in the key settings in `.env`:

| Variable | Required | Notes |
|----------|----------|-------|
| `JWT_SECRET` | Yes | 64-byte random string |
| `MYSQL_ROOT_PASSWORD` | Yes | MySQL root password |
| `MINIO_ROOT_PASSWORD` | Yes | MinIO admin password |
| `AI_API_KEY` | Recommended | Falls back to MockAIProvider when empty |
| `AI_DEMO_DAILY_LIMIT` | No | Daily AI call limit for the demo account; default `100` |
| `RAG_ENABLED` | No | Default `true`; set to `false` to disable vector retrieval |
| `RAG_LOCAL_EMBEDDING` | No | Default `true`; set to `false` to skip the local Embedding service |
| `DEPLOY_ENABLED` | No | Default `false`; set to `true` to enable the deploy management page |

Build and start:

```bash
docker compose up -d --build
```

Start with AI environment variables:

```bash
AI_PROVIDER=deepseek \
AI_BASE_URL=https://api.deepseek.com/v1 \
AI_API_KEY=your-api-key \
AI_MODEL=deepseek-chat \
docker compose up -d --build
```

Production entry:

```text
http://localhost
```

Compose deployment notes:

- The frontend is exposed through Nginx.
- `/api/` is reverse-proxied to the backend.
- `/ws/` is reverse-proxied to the WebSocket notification service.
- `client_max_body_size 500m` supports large file uploads.
- MySQL, Redis, MinIO, and backend containers can collaborate inside the Compose network to reduce public exposure.
- MySQL and Redis health checks are configured, and the backend waits for core dependencies before startup.

## Verification Commands

Backend compile:

```bash
cd backend
mvn test
```

Frontend build:

```bash
cd frontend
npm run build
```

Port checks:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
lsof -nP -iTCP:5173 -sTCP:LISTEN
```

API checks:

```bash
curl http://localhost:8080/v3/api-docs
curl -I http://localhost:5173
```

Docker checks:

```bash
docker compose config
docker compose up -d --build
docker compose ps
curl -I http://localhost
```

## Database Initialization

The backend uses `backend/src/main/resources/db/schema.sql` for runtime database initialization.

## Why This Project Matters

TeamFlow AI combines enterprise collaboration and AI capabilities in one coherent application flow. Instead of placing AI in an isolated chat panel, it connects AI with knowledge, files, tasks, notifications, and project execution. This makes the platform a strong foundation for internal collaboration tools, AI knowledge bases, engineering productivity systems, and full-stack portfolio projects.

## Use Cases

- Full-stack engineering portfolio and interview showcase.
- Enterprise collaboration platform prototype.
- AI knowledge base and RAG application starter.
- RBAC system learning and secondary development.
- Spring Boot 3 + Vue 3 enterprise backend reference.
- Self-hosted team workspace foundation.

## Contributing

Contributions are welcome. Useful directions include:

- AI Agent workflows,
- multi-tenant organization models,
- end-to-end test coverage,
- responsive mobile experience,
- OAuth2 / SSO single sign-on support,
- Redis login rate limiting and JWT denylist.

## License

This project is released under the [MIT License](./LICENSE). You are free to use, copy, modify, merge, publish, distribute, sublicense, and sell the software, provided that the copyright notice and permission notice are included in all copies or substantial portions of the software.

Copyright (c) 2026 wangguanfei
