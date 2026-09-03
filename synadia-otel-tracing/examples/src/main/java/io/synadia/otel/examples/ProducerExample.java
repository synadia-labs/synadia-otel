package io.synadia.otel.examples;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.synadia.otel.NatsTracer;
import io.synadia.otel.NatsTracingPublisher;
import io.synadia.otel.TracingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates publishing NATS messages with OTel trace context.
 *
 * Prerequisites:
 *   1. NATS server running (with msg_trace.dest configured for server tracing)
 *   2. exporter-service running
 *   3. Jaeger running (docker run -p 4317:4317 -p 16686:16686 jaegertracing/all-in-one)
 *
 * Run: java -cp ... io.synadia.otel.examples.ProducerExample [config.properties]
 */
public class ProducerExample {

    private static final Logger log = LoggerFactory.getLogger(ProducerExample.class);

    public static void main(String[] args) throws Exception {
        TracingConfig config = args.length > 0
                ? TracingConfig.fromFile(args[0])
                : TracingConfig.fromClasspath("example-tracing.properties");

        log.info("Connecting to NATS at {}", config.getNatsUrl());
        Connection nc = Nats.connect(config.getNatsUrl());

        try (NatsTracer tracer = NatsTracer.builder().connection(nc).config(config).build()) {
            NatsTracingPublisher publisher = tracer.publisher();

            for (int i = 1; i <= 10; i++) {
                String payload = "Hello from producer, message #" + i;
                publisher.publish("example.events", payload.getBytes());
                log.info("Published message #{}", i);
                Thread.sleep(500);
            }

            // JetStream example (requires a stream named EXAMPLE to exist)
            // publisher.publishJetStream("example.js.events", "JetStream message".getBytes());

            log.info("Done. Check Jaeger at http://localhost:16686");
        } finally {
            nc.close();
        }
    }
}
