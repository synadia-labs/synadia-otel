# NATS Open Telemetery (OTeL) example

The aim of this project is to illustrate how to make the native NATS tracing capabilities compatible with Open Telemetry.

There are three aspects to this:
+ The **Producer**: In the Producer client code we need to:
  +  insert certain headers, that the NATS server can recognize and generate trace events
  +  include OTEL logic that will emit a client trace event
+ The **Consumer**: In the Consumer Client code, we need to extract the headers from the message, and generate a trace event.
+ The **OTEL Exporter**: The Exporter subscribes to trace events (both client and server), converts the NATS wire format into OTEL wire format, and sends the ensuing OTEL message to Jaeger for visualization.

The **docs/synadia-otel-tracing.docx** document contains details on the architecture and on how to execute the demo. 

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | jdk 23 + | https://openjdk.org/install/ |
| maven|3.9.9+|https://maven.apache.org/download.cgi|
| nats-server| 2.12+| https://nats.io/download/|
| docker |v29.7.2 |https://docs.docker.com/get-started/get-docker/| 
| docker-compose | v5.5.0 | https://docs.docker.com/compose/install/ |
| Jaeger | v2.2.0 | https://www.jaegertracing.io/download/ | 

Note: you don't need to downloads NATS server or Jaeger as the <ins>docker-compose.yml</ins> file will pull them automatically from DockerHub


## Demos

| Demo | Topic | Docs |
|---|---|---|
| [`async-stream-flushing`](cmd/async-stream-flushing/) | JetStream async stream flushing — KubeCon benchmarks (sync vs async, R1 vs R3) | [README](cmd/async-stream-flushing/kubecon/README.md) |
| [`delayed-message-scheduling`](cmd/delayed-message-scheduling/) | JetStream Message Scheduler — deferred and recurring delivery | [README](cmd/delayed-message-scheduling/cli-demos/README.md) |
| [`distributed-counter-crdt`](cmd/distributed-counter-crdt/) | JetStream distributed counter streams — CLI walkthrough and cross-domain CRDT convergence | [CLI](cmd/distributed-counter-crdt/cli-demos/README.md) · [Go](cmd/distributed-counter-crdt/crdt-convergence/README.md) |

## Repo Layout

```
cmd/
  async-stream-flushing/
    kubecon/                    # bench scripts, HTML visualizers, conf
  delayed-message-scheduling/
    cli-demos/                  # step-by-step CLI walkthrough + quick reference
    go-demos/                   # Go demo
  distributed-counter-crdt/
    cli-demos/                  # CLI walkthrough + quick reference
    crdt-convergence/           # Go demo: cross-domain CRDT convergence
      conf/                     # east + west server configs (demo-specific)
      Taskfile.yml              # start / stop / run / reset
server/
  Taskfile.yml                  # shared server lifecycle tasks (reference for per-demo Taskfiles)
  conf/
    shared.conf                 # shared auth / accounts config
    single/                     # single-node config
    cluster/                    # 3-node cluster configs
    super/                      # supercluster + leaf node configs
  bin/                          # downloaded nats-server binary (gitignored)
  data/                         # JetStream store dirs (gitignored)
  logs/                         # server logs (gitignored)
```
