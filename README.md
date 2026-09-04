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
|  General tracing + OTEL export| use a Lef Node - Hub node configuration; publish on Leaf node, consume on Hub node - trace interaction | docs/synadia-otel-tracing.docx, Demo chapter |

## Repo Layout

```
synadia-otel-tracing/                                                                                                                                                                                              
  │                                                                                                                                                                                                                
  ├── pom.xml                                          — parent (Java 17, OTel BOM 1.40.0, 3 modules)                                                                                                                
  │                                                                                                                                                                                                                  
  ├── client-lib/                                      — library JAR for application use                                                                                                                             
  │   ├── pom.xml                                                                                                                                                                                                    
  │   └── src/main/                                                                                                                                                                                                
  │       ├── java/io/synadia/otel/
  │       │   ├── NatsTracer.java                      — entry point / builder; creates OTel SDK or noop
  │       │   ├── NatsTracingPublisher.java            — publish/request with traceparent injection
  │       │   ├── NatsTracingSubscriber.java           — subscribe with traceparent extraction
  │       │   ├── TracingConfig.java                   — config loader (.properties); incl. tracing.enabled
  │       │   ├── exporter/
  │       │   │   └── NatsSpanExporter.java            — SpanExporter: serialises spans → JSON → NATS
  │       │   ├── model/
  │       │   │   ├── SpanEnvelope.java                — JSON transport model for a single span
  │       │   │   └── SpanEventModel.java              — JSON transport model for span events
  │       │   └── propagation/
  │       │       ├── NatsHeaderSetter.java            — TextMapSetter over NATS Headers
  │       │       └── NatsHeaderGetter.java            — TextMapGetter over NATS Message
  │       └── resources/
  │           └── tracing.properties                   — default config (nats.url, traces.subject, etc.)
  │
  ├── exporter-service/                                — standalone fat JAR; bridges NATS → Jaeger
  │   ├── pom.xml
  │   └── src/main/
  │       ├── java/io/synadia/otel/exporter/
  │       │   ├── Main.java                            — entry point; wires up collectors
  │       │   ├── ExporterConfig.java                  — config loader (nats.url, subjects, jaeger.endpoint)
  │       │   ├── ClientSpanCollector.java             — telemetry.traces → ReconstitutedSpanData → Jaeger
  │       │   ├── ServerTraceEventCollector.java       — telemetry.server.traces → ServerTraceConverter → Jaeger
  │       │   ├── ServerTraceConverter.java            — MsgTraceEvent JSON → OTel SpanData list
  │       │   ├── ReconstitutedSpanData.java           — implements SpanData from SpanEnvelope
  │       │   ├── OtlpForwarder.java                   — OtlpGrpcSpanExporter wrapper → Jaeger :4317
  │       │   └── model/
  │       │       └── MsgTraceEvent.java               — ADR-41 wire format (server/request/events)
  │       └── resources/
  │           └── config.properties                    — default config
  │
  ├── examples/                                        — runnable demo apps
  │   ├── pom.xml                                      — builds producer.jar + consumer.jar
  │   └── src/main/
  │       ├── java/io/synadia/otel/examples/
  │       │   ├── ProducerExample.java                 — publishes messages on leaf node
  │       │   └── ConsumerExample.java                 — subscribes on hub node
  │       └── resources/
  │           └── example-tracing.properties           — fallback config for examples
  │
  ├── config/                                          — runtime config files (not bundled in JARs)
  │   ├── producer-leaf.properties                     — connects to leaf :4223, server.trace.dest set
  │   └── consumer-hub.properties                      — connects to hub :4222
  │
  ├── nats/                                            — NATS server config files
  │   ├── hub.conf                                     — port 4222, leafnodes port 7422
  │   └── leaf.conf                                    — port 4223, connects to nats-hub:7422
  │
  └── docker-compose.yml                               — nats-hub + nats-leaf + jaeger (all-in-one)

```
