# Control Plane

Central API and data repository for aggregating workload data from agents.

**Language:** Kotlin 2.3.10
**Framework:** Spring Boot 4.0.2, Java 21

## Commands

```bash
./gradlew bootRun            # Run locally (uses compose.yaml for PostgreSQL)
./gradlew test               # Run JUnit 5 tests
./gradlew build              # Compile, test, assemble
./gradlew clean              # Reset workspace
```

## Architecture

Follows hexagonal/clean architecture:

```
src/main/kotlin/sh/apptrail/controlplane/
├── application/      # Use cases and service layer
├── infrastructure/   # External adapters (persistence, HTTP clients, notifications)
└── web/              # HTTP controllers and DTOs (REST API)
```

**Application** (`application/`):
- `service/` - Business logic and orchestration
- `model/` - Application-specific models

**Infrastructure** (`infrastructure/`):
- `persistence/` - JPA repositories and database adapters
- `ingress/` - HTTP API for receiving events from agents
- `notification/` - Slack/webhook integrations
- `alerting/` - Prometheus alerting
- `gitprovider/` - GitHub integration
- `config/` - Spring configuration

**Web** (`web/`):
- `controller/` - REST API endpoints
- `dto/` - Request/response objects

## Database

- **PostgreSQL 18** via Docker Compose
- **Flyway** for migrations in `src/main/resources/db/migration/`
- Migration naming: `V<timestamp>__<description>.sql` (e.g., `V202502141030__add_users_table.sql`)

### Creating a Migration

```bash
# In src/main/resources/db/migration/
touch V$(date +%Y%m%d%H%M%S)__add_feature.sql
```

## Local Development

Uses `compose.yaml` for dependencies:
- PostgreSQL on port 5432
- Grafana LGTM stack on ports 3000, 4317, 4318

Configuration: `src/main/resources/application.yml`

## Gotchas

- Uses Kotlin 2.3.10 with 2-space indentation
- No formatter/linter configured - follow IntelliJ IDEA Kotlin defaults
- Package naming: lowercase (e.g., `sh.apptrail.controlplane`)
- Test files: `*Tests.kt` in `src/test/kotlin`
