package io.synadia.otel.examples;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.synadia.otel.NatsTracer;
import io.synadia.otel.NatsTracingSubscriber;
import io.synadia.otel.TracingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates subscribing to NATS messages with OTel trace context extraction.
 * Each received message creates a child span linked to the publisher's trace.
 *
 * Run alongside ProducerExample to see full end-to-end traces in Jaeger.
 */
public class ConsumerExample {

    private static final Logger log = LoggerFactory.getLogger(ConsumerExample.class);

    public static void main(String[] args) throws Exception {
        TracingConfig config = args.length > 0
                ? TracingConfig.fromFile(args[0])
                : TracingConfig.fromClasspath("example-tracing.properties");

        log.info("Connecting to NATS at {}", config.getNatsUrl());
        Connection nc = Nats.connect(config.getNatsUrl());

        try (NatsTracer tracer = NatsTracer.builder().connection(nc).config(config).build()) {
            NatsTracingSubscriber subscriber = tracer.subscriber();

            Dispatcher d = nc.createDispatcher(
                subscriber.wrap("example.events", msg -> {
                    String payload = new String(msg.getData());
                    log.info("Received: {}", payload);
                    // Business logic here — runs inside the subscriber span
                })
            );
            d.subscribe("example.events");

            log.info("Consumer listening on 'example.events'. Press Ctrl+C to stop.");
            Thread.currentThread().join();
        } finally {
            nc.close();
        }
    }
}
