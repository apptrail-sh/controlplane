# Cluster Topology: Environments, Cells, and Clusters

AppTrail uses a flexible topology model to organize and track workloads across your Kubernetes infrastructure. This
document explains the core concepts and how they work together.

## Overview

**Physical Hierarchy** (what exists in Kubernetes):

```
Cluster (e.g., production-gke-us-east1)
└── Namespace (e.g., payments)
    └── Workload (Deployment, StatefulSet, DaemonSet)
```

**Logical Resolution** (how AppTrail interprets workloads):

```
Workload Instance
├── Environment (resolved from cluster or namespace config)
└── Cell (optional, for progressive rollouts)
```

The key insight is that a single Kubernetes cluster can host workloads belonging to different environments. AppTrail
resolves each workload's environment and cell based on its cluster and namespace.

## Concepts

### Environment

An **environment** represents a deployment stage in your software delivery lifecycle.

| Property   | Description                                             |
|------------|---------------------------------------------------------|
| `name`     | Unique identifier (e.g., `staging`, `production`)       |
| `order`    | Sort order for UI display (lower = earlier in pipeline) |
| `metadata` | Key-value pairs for templating (e.g., GCP project ID)   |

**Common examples:**

- `development` (order: 0)
- `staging` (order: 1)
- `production` (order: 2)

Environments help you:

- Filter workloads by deployment stage
- Track version progression across stages
- Configure environment-specific notifications

### Cell

A **cell** is a logical deployment unit within an environment, used for progressive rollouts and blast radius isolation.
Cells are defined as first-class entities under environments, and clusters reference them by name.

| Property | Description                                               |
|----------|-----------------------------------------------------------|
| `name`   | Unique identifier (e.g., `canary`, `prod`, `us-east`)     |
| `order`  | Rollout order within environment (lower = deployed first) |
| `alias`  | Human-friendly display name (optional)                    |

**Use cases:**

1. **Canary deployments**: Deploy to a small subset before full rollout
   ```
   production/canary (order: 0)  →  production/prod (order: 1)
   ```

2. **Regional rollouts**: Deploy region by region
   ```
   production/us-east (order: 0)  →  production/eu-west (order: 1)  →  production/apac (order: 2)
   ```

3. **Tenant isolation**: Separate cells per customer or tier
   ```
   production/enterprise (order: 0)  →  production/standard (order: 1)
   ```

Cells help you:

- Visualize rollout progression in the UI
- Limit blast radius of failed deployments
- Implement progressive delivery strategies

### Cluster

A **cluster** is a Kubernetes cluster where workloads run. Each cluster belongs to one environment and optionally one
cell.

| Property      | Description                               |
|---------------|-------------------------------------------|
| `environment` | The environment this cluster belongs to   |
| `alias`       | Human-readable display name (optional)    |
| `cell`        | The cell configuration for this cluster   |
| `namespaces`  | Namespace-level cell overrides (optional) |

**Cluster ID format**: The cluster ID reported by the agent (e.g., `production-gke-us-east1`) serves as the unique
identifier.

## Configuration

Configure cluster topology in `application.yml` or a profile-specific config:

```yaml
app:
  clusters:
    # Environment definitions with cells as first-class entities
    environments:
      - name: staging
        order: 1
        metadata:
          gcp-project: my-staging-project
        cells:
          - name: stg01
            alias: "stg.stg01"
            order: 0
          - name: stg02
            alias: "stg.stg02"
            order: 1

      - name: production
        order: 2
        metadata:
          gcp-project: my-production-project
        cells:
          - name: canary
            alias: "prd.canary"
            order: 0
          - name: shard01
            alias: "prd.shard01"
            order: 1
          - name: shard02
            alias: "prd.shard02"
            order: 2

    # Cluster definitions reference cells by name
    definitions:
      # Staging clusters
      staging-gke-us-east1:
        environment: staging
        cell: stg01

      staging-gke-eu-west1:
        environment: staging
        cell: stg02

      # Production clusters
      prod-canary-cluster:
        environment: production
        cell: canary

      prod-shard01-cluster:
        environment: production
        cell: shard01

      prod-shard02-cluster:
        environment: production
        cell: shard02
```

### Namespace-Level Overrides

For multi-tenant clusters, you can assign different namespaces to different environments and cells:

```yaml
definitions:
  # Multi-tenant cluster hosting multiple environments
  shared-cluster-eks:
    environment: development  # Default for unconfigured namespaces
    namespaces:
      dev:
        environment: development
      staging:
        environment: staging
        cell: stg01  # References cell defined in staging environment
      prod:
        environment: production
        cell: shard01  # References cell defined in production environment

  # Cell override only (environment inherited from cluster)
  production-gke-us-east1:
    environment: production
    cell: shard01
    namespaces:
      beta-customers:
        cell: canary  # References cell defined in production environment
```

This enables several deployment patterns:

1. **Shared development cluster**: One cluster serves dev, staging, and production namespaces
2. **Namespace-based cells**: Different namespaces get different cells for progressive rollouts
3. **Hybrid approach**: Mix of dedicated clusters and shared clusters

## Resolution Logic

When the Control Plane receives an event from an agent, it resolves the topology:

1. **Environment**: Resolution order:
    1. Check `definitions[clusterId].namespaces[namespace].environment` (namespace-level override)
    2. Fall back to `definitions[clusterId].environment` (cluster-level default)
    3. Falls back to agent-provided environment if cluster is not configured

2. **Cell**: Resolution order:
    1. Check `definitions[clusterId].namespaces[namespace].cell` (namespace-level cell name)
    2. Fall back to `definitions[clusterId].cell` (cluster-level cell name)
    3. Look up cell definition from `environments[environment].cells` to get order and alias
    4. Returns `null` if no cell is configured

3. **Location Alias**: Resolution order (for human-friendly display):
    1. Cell alias (from `environments[environment].cells[cell].alias`)
    2. Fall back to cluster alias (`definitions[clusterId].alias`)
    3. Returns `null` if neither configured (frontend can display cluster name)

## API Endpoints

### Get Environments with Cells

```
GET /api/v1/environments
```

Response:

```json
{
  "environments": [
    {
      "name": "staging",
      "order": 1,
      "cells": [
        {
          "name": "stg01",
          "order": 0,
          "alias": "stg.stg01"
        },
        {
          "name": "stg02",
          "order": 1,
          "alias": "stg.stg02"
        }
      ]
    },
    {
      "name": "production",
      "order": 2,
      "cells": [
        {
          "name": "canary",
          "order": 0,
          "alias": "prd.canary"
        },
        {
          "name": "shard01",
          "order": 1,
          "alias": "prd.shard01"
        }
      ]
    }
  ]
}
```

### Workload Instances

Each workload instance includes its resolved topology:

```json
{
  "id": 123,
  "namespace": "payments",
  "environment": "production",
  "cell": "canary",
  "cluster": {
    "id": 1,
    "name": "production-gke-us-east1-canary"
  }
}
```

## Template Variables

Cell and environment data is available in quick links and metrics query templates:

| Variable                       | Description                |
|--------------------------------|----------------------------|
| `{{instance.environment}}`     | Environment name           |
| `{{instance.cell}}`            | Cell name                  |
| `{{cluster.name}}`             | Cluster ID                 |
| `{{environment.metadata.KEY}}` | Environment metadata value |

Example quick link:

```yaml
urlTemplate: "https://grafana.example.com/d/k8s?var-env={{instance.environment}}&var-cell={{instance.cell}}"
```

## Best Practices

1. **Use consistent naming**: Establish naming conventions for clusters that include environment and region hints (e.g.,
   `production-gke-us-east1`)

2. **Order cells by risk**: Lower order = deployed first. Put canary/beta cells before production cells.

3. **Deduplicate cells**: Multiple clusters can share the same cell. This is useful for regional deployments that should
   be treated as equivalent.

4. **Use metadata for templating**: Store project IDs, region codes, and other environment-specific values in `metadata`
   for use in quick links.

5. **Start simple**: You don't need cells if you have a simple setup. Environment alone is sufficient for basic
   deployments.
