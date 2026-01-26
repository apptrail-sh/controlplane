# AppTrail Control Plane

Central API and data repository for tracking Kubernetes workload versions across clusters.

## What it does

AppTrail Control Plane receives workload events from Kubernetes controllers, stores version history in PostgreSQL, and
provides a REST API for querying workload data. It aggregates information from multiple clusters and environments into a
single source of truth.

## Features

* **Multi-cluster tracking** - Track workloads across staging, production, and other environments
* **Version history** - Complete audit trail of all version changes with timestamps
* **REST API** - Query workloads, clusters, and recent changes via HTTP endpoints
* **Event ingestion** - Receive workload events from AppTrail controllers via HTTP or GCP Pub/Sub
* **Flexible topology** - Supports namespace-per-environment or cluster-per-environment setups
* **Kubernetes-native** - Uses standard `app.kubernetes.io/version`, `app.kubernetes.io/name` and
  `app.kubernetes.io/part-of` labels
* **Deployment metrics** - Track deployment frequency, lead time and change failure rate over time with built-in metrics
* **Prometheus alerting** - Integrate with Prometheus to display alert status alongside workloads
* **Quick links** - Configurable templated URLs for GCP logs, Grafana dashboards, and kubectl commands
* **Team scorecards** - Analytics and metrics per team
* **Cell/shard support** - Track progressive rollouts across cells or shards within an environment

### Roadmap

* **Configurable notifications** - Subscribe to version changes and receive alerts via Slack in channels or direct
  messages
* **Deployment tracing** - Trace deployments back to Git commits and PRs for GitOps workflows - GitHub PR integration
* **Promotion reminders** - Get notified when a new version is available in staging but not yet promoted to production,
  configurable reminders per team or workload

## Prerequisites

- Java 21
- Gradle (or use the included wrapper `./gradlew`)
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

Configuration is managed via environment variables. The following variables are available:

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

Architecture overview:

```
src/main/kotlin/sh/apptrail/controlplane/
├── application/      # Use cases and service layer
├── infrastructure/   # External adapters (persistence, HTTP clients, notifications)
└── web/              # REST API controllers and DTOs
```

**Application** (`application/`) - Business logic and orchestration. Defines use cases and service interfaces.

**Infrastructure** (`infrastructure/`) - External adapters including:

- `persistence/` - JPA repositories and database adapters
- `ingress/` - HTTP and Pub/Sub event ingestion from agents
- `notification/` - Slack webhook integrations
- `config/` - Spring configuration

**Web** (`web/`) - REST API layer with controllers, DTOs, and exception handling.

## Database Migrations

Migrations are managed by Flyway and located in:

```
src/main/resources/db/migration/
```

### Migration Naming Convention

```
V<timestamp>__<description>.sql
```

Example: `V202501251030__add_users_table.sql`

### Creating a New Migration

```bash
touch src/main/resources/db/migration/V$(date +%Y%m%d%H%M%S)__description.sql
```

Migrations run automatically on application startup.

## Testing

```bash
# Run all tests
./gradlew test
```

- Test framework: JUnit 5
- Test files: `*Tests.kt` in `src/test/kotlin`
- Tests use the same package structure as main source code

## API Endpoints

The REST API provides the following main endpoints:

```
GET  /api/v1/workloads           - List all workloads
GET  /api/v1/workloads/:id       - Get workload details
GET  /api/v1/workloads/:id/history - Get version history
GET  /api/v1/clusters            - List all clusters
POST /api/v1/events              - Receive events from agents
```

## Development

### Local Dependencies

The `compose.yaml` file provides local development dependencies:

- PostgreSQL on port 5432
- Grafana LGTM stack (optional) on ports 3000, 4317, 4318

### Code Style

- Kotlin 2.x with 2-space indentation
- Follow IntelliJ IDEA Kotlin defaults
