# AppTrail Control Plane Architecture

This document describes the technical architecture of the AppTrail Control Plane.

## Design Philosophy

AppTrail is an **application-centric** observability platform. The core focus is on workloads (Deployments, StatefulSets, DaemonSets) and their versions. Infrastructure resources (pods, nodes) provide context rather than being the primary focus.

```
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER (Core)                 │
│  Workloads → Versions → Deployments → Releases → DORA       │
├─────────────────────────────────────────────────────────────┤
│                 INFRASTRUCTURE CONTEXT                      │
│  Pods → Nodes → Resource Pressure → Scheduling Context      │
└─────────────────────────────────────────────────────────────┘
```

## Architecture

The codebase follows

```
src/main/kotlin/sh/apptrail/controlplane/
├── domain/           # Core business entities and domain logic
├── application/      # Use cases, services, and application logic
├── infrastructure/   # External adapters (DB, HTTP clients, notifications)
└── web/              # REST API layer (controllers, DTOs)
```

### Domain Layer

Pure business logic with no external dependencies.

```
domain/
├── model/            # Core entities: Workload, Cluster, DeploymentPhase, etc.
└── event/            # Domain events
```

Key domain concepts:
- **Workload**: Logical application entity (kind + name)
- **WorkloadInstance**: Concrete deployment in a specific cluster/namespace
- **VersionHistory**: Timeline of version changes with deployment phases
- **DeploymentPhase**: `PENDING` → `PROGRESSING` → `COMPLETED` | `FAILED`

### Application Layer

Orchestrates use cases and business logic.

```
application/
├── service/          # Business services
│   ├── agent/        # AgentEventProcessorService - event ingestion
│   ├── workload/     # WorkloadService - workload management
│   ├── analytics/    # AnalyticsService, TeamScorecardService
│   ├── release/      # ReleaseService, ReleaseFetchService
│   └── notification/ # NotificationService
└── model/            # Application-specific models (DTOs between layers)
```

Key services:
- **AgentEventProcessorService**: Processes incoming events, resolves entities, manages state
- **ReleaseService**: Fetches and correlates Git releases with versions
- **AlertService**: Queries Prometheus for firing alerts
- **InstanceMetricsService**: Queries Prometheus for workload metrics

### Infrastructure Layer

External system adapters.

```
infrastructure/
├── persistence/      # JPA entities, repositories, database adapters
│   ├── entity/       # Database entities (WorkloadEntity, PodEntity, etc.)
│   └── repository/   # Spring Data JPA repositories
├── ingress/          # Event ingestion adapters
│   ├── http/         # HTTP API for agent events
│   └── pubsub/       # GCP Pub/Sub ingestion
├── alerting/         # Prometheus integration
├── gitprovider/      # GitHub API client
├── notification/     # Slack webhook client
└── config/           # Spring configuration classes
```

### Web Layer

REST API controllers and DTOs.

```
web/
├── controller/       # REST controllers
├── dto/              # Request/response objects
└── advice/           # Exception handlers
```

## Data Model

### Entity Relationships

```
clusters
    └── nodes (1:N)
    └── workload_instances (1:N)
            └── pods (1:N) ──→ nodes (N:1)
            └── version_history (1:N) ──→ releases (N:1)

workloads (1:N) ──→ workload_instances
         └──→ repositories (N:1)

repositories (1:N) ──→ releases
```

### Core Tables

**workloads** - Logical workload definitions
```sql
workloads (
    id, kind, name, team, part_of,
    repository_id, description,
    created_at, updated_at
)
```

**workload_instances** - Concrete deployments per cluster/namespace
```sql
workload_instances (
    id, workload_id, cluster_id, namespace,
    environment, cell, current_version, labels,
    first_seen_at, last_updated_at
)
```

**version_history** - Deployment timeline
```sql
version_history (
    id, workload_instance_id, previous_version, current_version,
    deployment_phase, deployment_status,
    deployment_started_at, deployment_completed_at, deployment_failed_at,
    deployment_duration_seconds, release_id, detected_at
)
```

### Infrastructure Tables

**nodes** - Kubernetes nodes (cluster-scoped)
```sql
nodes (
    id, cluster_id, name, uid, labels, status,
    first_seen_at, last_updated_at, deleted_at
)
```

**pods** - Kubernetes pods with workload correlation
```sql
pods (
    id, cluster_id, workload_instance_id, node_id,
    namespace, name, uid, labels, status,
    first_seen_at, last_updated_at, deleted_at
)
```

Key correlations:
- `pods.workload_instance_id` → Links pod to its owning workload
- `pods.node_id` → Links pod to its scheduled node

## Event Processing Pipeline

```
Agent Event
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  HTTP/Pub/Sub Ingester                                      │
│  (HttpAgentEventIngester / GCPPubSubAgentEventIngester)     │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  AgentEventProcessorService (@Transactional)                │
│  ├── Validate event                                         │
│  ├── Resolve/create cluster                                 │
│  ├── Resolve/create workload                                │
│  ├── Resolve/create workload instance                       │
│  ├── Process version change                                 │
│  │   ├── Create VersionHistory entry                        │
│  │   ├── Queue release fetch                                │
│  │   └── Publish notifications                              │
│  └── Process infrastructure events (nodes, pods)            │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  Async Processing                                           │
│  ├── ReleaseFetchService → GitHub API                       │
│  ├── NotificationService → Slack webhooks                   │
│  └── SSE Publisher → Frontend clients                       │
└─────────────────────────────────────────────────────────────┘
```

### Event Types

**Workload Events** (existing):
- Version changes detected via `app.kubernetes.io/version` label
- Deployment phase transitions: `PENDING` → `PROGRESSING` → `COMPLETED`/`FAILED`

**Infrastructure Events** (planned):
- Node created/updated/deleted
- Pod created/updated/deleted
- Pod scheduled to node
- Container status changes

## Real-Time Updates (SSE) (WIP)

Server-Sent Events for pushing infrastructure changes to the frontend.

```
Frontend                          Control Plane
   │                                    │
   │  GET /clusters/{id}/infrastructure/stream
   │ ──────────────────────────────────►│
   │                                    │
   │  SSE: event: pod                   │
   │  data: {"type":"pod","action":"updated",...}
   │ ◄──────────────────────────────────│
   │                                    │
   │  SSE: event: node                  │
   │  data: {"type":"node","action":"created",...}
   │ ◄──────────────────────────────────│
```

Implementation uses Spring WebFlux `Flux<ServerSentEvent>`.

## Scaling Considerations

### Current Design

- Single PostgreSQL instance with read replicas for queries
- Event batching from agents (2-second windows, max 100 events)
- Materialized views for expensive aggregations
- Redis caching for hot data (cluster topology, node/pod counts) (soon)

### Database Optimization

```sql
-- Materialized view for cluster summaries
CREATE MATERIALIZED VIEW cluster_pod_counts AS
SELECT cluster_id,
       COUNT(*) as total_pods,
       COUNT(*) FILTER (WHERE status->>'phase' = 'Running') as running_pods
FROM pods
WHERE deleted_at IS NULL
GROUP BY cluster_id;

-- Refresh every 5 minutes
REFRESH MATERIALIZED VIEW CONCURRENTLY cluster_pod_counts;
```

### Future Scaling (1000+ nodes) (TDB)

- Sharded agent watching by namespace
- Multiple worker goroutines for event processing 
- Kafka/Pub/Sub for event streaming
- Time-series database for high-volume metrics

## Key Patterns

### Entity Resolution

Events from agents reference entities by name. The processor resolves or creates entities:

```kotlin
fun resolveCluster(clusterId: String): ClusterEntity {
    return clusterRepository.findByName(clusterId)
        ?: clusterRepository.save(ClusterEntity(name = clusterId))
}
```

### Idempotent Processing

Events are deduplicated by checking if the current version already exists:

```kotlin
if (instance.currentVersion == event.version) {
    // Update existing version history (phase transition)
} else {
    // Create new version history entry
}
```

### Horizontal Scaling

Row-level locking for safe concurrent processing:

```sql
SELECT * FROM release_fetch_queue
WHERE status = 'pending'
FOR UPDATE SKIP LOCKED
LIMIT 10;
```

## Configuration

### Cluster Topology

Define cluster metadata and environment mapping:

```yaml
app:
  clusters:
    definitions:
      prod-us-east-1:
        alias: "US East"
        environment: production
      staging-1:
        alias: "Staging"
        environment: staging
    environments:
      - name: development
        order: 0
      - name: staging
        order: 1
      - name: production
        order: 2
```

### Metrics Queries

Configure Prometheus queries for workload metrics:

```yaml
app:
  metrics-queries:
    queries:
      - name: request_rate
        displayName: "Request Rate"
        unit: "req/s"
        query: 'sum(rate(http_requests_total{namespace="${namespace}",workload="${workload.name}"}[5m]))'
```

## Testing Strategy

- **Unit tests**: Service logic with mocked repositories
- **Integration tests**: Full request/response with test database
- **Test containers**: PostgreSQL for database tests

```kotlin
@SpringBootTest
@Testcontainers
class AgentEventProcessorServiceTests {
    @Container
    val postgres = PostgreSQLContainer("postgres:18")

    @Test
    fun `processes workload event and creates version history`() {
        // ...
    }
}
```
