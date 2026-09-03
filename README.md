# NATS Open Telemetery (OTeL) example

The aim of this project is to illustrate how to make the native NATS tracing capabilities compatible with Open Telemetry.

There are three aspects to this:
+ The **Producer**: In the Producer client code we need to:
  +  insert certain headers, that the NATS server can recognize and generate trace events
  +  include OTEL logic that will emit a client trace event
+ The **Consumer**: In the Consumer Client code, we need to extract the headers from the message, and generate a trace event.
+ The **OTEL Exporter**: The Exporter subscribes to trace events (both client and server), converts the NATS wire format into OTEL wire format, and sends the ensuing OTEL message to Jaeger for visualization.

The **docs/synadia-otel-tracing.docx** document conains details on the architecrure and on how to execute the demo. 

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Go | 1.22+ | [go.dev/dl](https://go.dev/dl/) |
| Task | any | [taskfile.dev](https://taskfile.dev) |
| nats CLI | 0.3+ | `go install github.com/nats-io/natscli/nats@latest` |

`nats-server` is downloaded automatically by the Task commands — no manual install needed.

## Shared Server Harness

`server/Taskfile.yml` manages the NATS server lifecycle. The binary is fetched from `binaries.nats.dev` on first use and cached at `server/bin/`. Run these from the repo root:

```bash
task server:single       # single-node JetStream (port 4222)
task server:cluster      # 3-node JetStream cluster (ports 4222–4224)
task server:super        # supercluster: 3 clusters × 3 nodes + 7 leaf nodes (16 servers)

task server:stop         # stop all running servers and wipe data
task server:status       # health check all known endpoints

task server:reset:single   # wipe + restart single-node
task server:reset:cluster  # wipe + restart 3-node cluster
task server:reset:super    # wipe + restart supercluster
```

After starting, a NATS CLI context is saved and selected so `nats` commands work without a `--server` flag.

Demos that need their own server topology (e.g. specific ports or JetStream domains) include their own `Taskfile.yml` and `conf/` — see the demo's README for details.

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
