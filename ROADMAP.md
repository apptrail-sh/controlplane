# AppTrail Control Plane Roadmap

Future features and improvements planned for the Control Plane.

## Planned Features

### Promotion reminders and policies
Notifications when versions aren't promoted to higher environments within a configurable time window.
Environments can be configured with promotion deadlines.



### Deployment Tracing
Trace deployments back to Git commits and PRs to answer "what changed in this release?"
### Slack Integration
Send deployment notifications and alerts to Slack channels.
### Workload subscription
Allow users to subscribe to specific workloads for targeted notifications and updates.


## Ideas (not planned yet)
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
