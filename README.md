# AppTrail Control Plane

Central API and data repository for tracking Kubernetes workload versions across clusters.

## What it does

AppTrail Control Plane receives workload events from Kubernetes controllers, stores version history in PostgreSQL, and provides a REST API for querying workload data. It aggregates information from multiple clusters and environments into a single source of truth.

## Features

* **Multi-cluster tracking** - Track workloads across staging, production, and other environments
* **Version history** - Complete audit trail of all version changes with timestamps
* **REST API** - Query workloads, clusters, and recent changes via HTTP endpoints
* **Event ingestion** - Receive workload (deployments, statefulsets, etc) events from AppTrail controllers
* **Flexible topology** - Supports namespace-per-environment or cluster-per-environment setups
* **Kubernetes-native** - Uses standard `app.kubernetes.io/version`, `app.kubernetes.io/name` and `app.kubernetes.io/part-of` labels
* **Configurable notifications** - Subscribe to version changes and receive alerts via Slack in channels or direct messages
* **Deployment tracing** - Trace deployments back to Git commits and PRs for GitOps workflows - Github PR integration
* **Promotion reminders** - Get notified when a new version is available in staging but not yet promoted to production, configurable reminders per team or workload (after X hours/days)
* **Deployment metrics** - Track deployment frequency, lead time and change failure rate over time with built-in metrics
