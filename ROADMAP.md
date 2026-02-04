# AppTrail Control Plane Roadmap

Future features and improvements planned for the Control Plane.

## Planned Features

### Workload topology
Visualize the workload topology (pods, nodes, regions, etc.) in a graph format.

### Promotion reminders and policies

Notifications when versions aren't promoted to higher environments within a configurable time window.
Environments can be configured with promotion deadlines.

### Slack Integration

Send deployment notifications and alerts to Slack channels.

\+ User mapping for personalized notifications.

### Workload subscription

Allow users to subscribe to specific workloads for targeted notifications and updates.

## Ideas (not planned yet)

### Deployment Tracing / Version summaries with IA

Trace deployments back to Git commits and PRs to answer "what changed in this release?"

\+ Generate human-readable summaries of version changes using AI.

\+ Highlight key changes, bug fixes, and new features.

### Infrastructure Improvements

#### Real-Time Updates (SSE)

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

#### Future Scaling (1000+ nodes)

For very large deployments:

- Sharded agent watching by namespace
- Multiple worker goroutines for event processing
- Kafka/Pub/Sub for event streaming
- Time-series database for high-volume metrics
