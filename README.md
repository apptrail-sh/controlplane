# AppTrail Control Plane

Central API and data repository for the AppTrail application-centric observability platform. Tracks Kubernetes workload versions, correlates infrastructure context (pods, nodes), and provides real-time visibility across clusters.

## What it does

AppTrail Control Plane receives events from Kubernetes agents, stores workload and infrastructure state in PostgreSQL, and provides a REST API for querying data. It aggregates information from multiple clusters into a single source of truth, with workloads as the primary focus and infrastructure (pods, nodes) providing context.

```
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER (Core)                 │
│  Workloads → Versions → Deployments → Releases → DORA      │
├─────────────────────────────────────────────────────────────┤
│                 INFRASTRUCTURE CONTEXT                      │
│  Pods → Nodes → Resource Pressure → Scheduling Context      │
└─────────────────────────────────────────────────────────────┘
```

## Features

### Core (Application-Centric)

* **Multi-cluster tracking** - Track workloads across staging, production, and other environments
* **Version history** - Complete audit trail of all version changes with timestamps
* **Deployment metrics** - Track deployment frequency, lead time, and change failure rate (DORA)
* **Release correlation** - Link versions to Git releases with changelog and author information
* **Team scorecards** - Analytics and metrics aggregated per team
* **Cell/shard support** - Track progressive rollouts across cells or shards

### Infrastructure Context

* **Pod tracking** - See pods backing each workload with status, restarts, and container info
* **Node correlation** - Understand where pods are scheduled and node resource pressure
* **Workload ↔ Pod ↔ Node correlation** - Answer "why is my app unhealthy?" with infrastructure context
* **Real-time updates** - SSE streaming for live infrastructure changes

### Integrations

* **Prometheus alerting** - Display firing alerts alongside workloads
* **GitHub releases** - Fetch release notes and authors from GitHub
* **Slack notifications** - Subscribe to deployment events per team/environment
* **Quick links** - Configurable URLs for logs, dashboards, and kubectl commands

### Roadmap

* **Deep links** - Direct links to Grafana, Loki, and Jaeger for each workload
* **Promotion reminders** - Notifications when staging versions aren't promoted to production
* **Deployment tracing** - Trace deployments back to Git commits and PRs

## Prerequisites

- Java 21
- Gradle (or use `./gradlew`)
- Docker (for PostgreSQL via Docker Compose)

## Quick Start

```bash
# Start the server (PostgreSQL auto-starts via compose.yaml)
./gradlew bootRun

# Build (compile, test, assemble)
./gradlew build

# Run tests only
./gradlew test

# Reset workspace
./gradlew clean
```

The server starts on port **3000** by default.

## Configuration

Configuration is managed via environment variables.

### Database

| Variable      | Description         | Default    |
|---------------|---------------------|------------|
| `DB_USERNAME` | PostgreSQL username | `apptrail` |
| `DB_PASSWORD` | PostgreSQL password | `apptrail` |

### GitHub Integration

| Variable                        | Description                                      | Default                  |
|---------------------------------|--------------------------------------------------|--------------------------|
| `GITHUB_APP_ENABLED`            | Enable GitHub App integration                    | `false`                  |
| `GITHUB_APP_ID`                 | GitHub App ID                                    | -                        |
| `GITHUB_APP_PRIVATE_KEY_PATH`   | Path to GitHub App private key file              | -                        |
| `GITHUB_APP_PRIVATE_KEY_BASE64` | Base64-encoded private key (alternative to path) | -                        |
| `GITHUB_API_BASE_URL`           | GitHub API base URL (for GitHub Enterprise)      | `https://api.github.com` |

### Notifications

| Variable                | Description                             | Default                 |
|-------------------------|-----------------------------------------|-------------------------|
| `NOTIFICATIONS_ENABLED` | Enable deployment notifications         | `false`                 |
| `SLACK_WEBHOOK_URL`     | Slack webhook URL for notifications     | -                       |
| `FRONTEND_BASE_URL`     | Frontend URL for links in notifications | `http://localhost:5173` |

### Prometheus Alerting

| Variable                | Description                            | Default                               |
|-------------------------|----------------------------------------|---------------------------------------|
| `PROMETHEUS_ENABLED`    | Enable Prometheus alerting integration | `false`                               |
| `PROMETHEUS_BASE_URL`   | Prometheus server URL                  | `http://localhost:8481`               |
| `PROMETHEUS_QUERY_PATH` | Prometheus query API path              | `/select/0:0/prometheus/api/v1/query` |
| `PROMETHEUS_TIMEOUT_MS` | Query timeout in milliseconds          | `5000`                                |

### GCP Pub/Sub Ingestion

| Variable              | Description                        | Default |
|-----------------------|------------------------------------|---------|
| `PUBSUB_ENABLED`      | Enable GCP Pub/Sub event ingestion | `false` |
| `PUBSUB_SUBSCRIPTION` | Pub/Sub subscription name          | -       |

### Other

| Variable                    | Description                                    | Default |
|-----------------------------|------------------------------------------------|---------|
| `RELEASE_FETCH_INTERVAL_MS` | Interval for fetching release info from GitHub | `30000` |

## Architecture

See [ARCHITECTURE.md](./ARCHITECTURE.md) for detailed architecture documentation.

### High-Level Structure

```
src/main/kotlin/sh/apptrail/controlplane/
├── domain/           # Core business entities and domain logic
├── application/      # Use cases and service layer
├── infrastructure/   # External adapters (persistence, HTTP, notifications)
└── web/              # REST API controllers and DTOs
```

### Data Model

```
Cluster
└── Node (infrastructure context)
    └── Pod → WorkloadInstance (correlation)

Workload (logical entity: name, kind, team)
└── WorkloadInstance (per cluster/namespace)
    ├── VersionHistory (timeline of deployments)
    │   └── Release (Git metadata)
    └── Pod[] (infrastructure context)
        └── Node
```

## Database Migrations

Migrations are managed by Flyway in `src/main/resources/db/migration/`.

### Naming Convention

```
V<timestamp>__<description>.sql
```

Example: `V20260129120000__create_pods_table.sql`

### Creating a New Migration

```bash
touch src/main/resources/db/migration/V$(date +%Y%m%d%H%M%S)__description.sql
```

Migrations run automatically on startup.

## API Endpoints

### Workloads (Core)

```
GET  /api/v1/workloads              - List all workloads with instances
GET  /api/v1/workloads/:id          - Get workload details
PATCH /api/v1/workloads/:id         - Update workload metadata
GET  /api/v1/workloads/:id/history  - Get version history
GET  /api/v1/workloads/:id/metrics  - Get deployment metrics
```

### Infrastructure

```
GET  /api/v1/clusters/:id/nodes              - List nodes in cluster
GET  /api/v1/clusters/:id/nodes/:name        - Get node details
GET  /api/v1/clusters/:id/nodes/:name/pods   - Get pods on node
GET  /api/v1/workload-instances/:id/pods     - Get pods for workload instance
GET  /api/v1/clusters/:id/infrastructure/stream - SSE stream for updates
```

### Other

```
GET  /api/v1/clusters      - List all clusters
GET  /api/v1/teams         - List all teams
GET  /api/v1/environments  - List all environments
GET  /api/v1/alerts        - Get alert overview
```

### Ingestion

```
POST /ingest/v1/agent/events       - Receive single event from agent
POST /ingest/v1/agent/events/batch - Receive batched events from agent
```

## Testing

```bash
./gradlew test
```

- Test framework: JUnit 5
- Test files: `*Tests.kt` in `src/test/kotlin`
- Tests use the same package structure as main source code

## Development

### Local Dependencies

The `compose.yaml` file provides:

- PostgreSQL on port 5432
- Grafana LGTM stack (optional) on ports 3000, 4317, 4318

### Code Style

- Kotlin 2.x with 2-space indentation
- Follow IntelliJ IDEA Kotlin defaults
- Package naming: lowercase (e.g., `sh.apptrail.controlplane`)
