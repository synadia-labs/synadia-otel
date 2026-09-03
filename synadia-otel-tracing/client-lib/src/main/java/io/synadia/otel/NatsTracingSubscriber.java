package io.synadia.otel;

import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.synadia.otel.propagation.NatsHeaderGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps NATS MessageHandlers to extract incoming OTel trace context and
 * create child spans for each received message.
 *
 * For JetStream messages, server-side metadata (stream sequence, server timestamp)
 * is captured as span attributes, enabling transit time measurement.
 */
public class NatsTracingSubscriber {

    private static final Logger log = LoggerFactory.getLogger(NatsTracingSubscriber.class);

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    NatsTracingSubscriber(OpenTelemetry openTelemetry) {
        this.tracer     = openTelemetry.getTracer("io.synadia.otel", "1.0.0");
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    /**
     * Wraps a MessageHandler for core NATS subscriptions.
     */
    public MessageHandler wrap(String subject, MessageHandler handler) {
        return msg -> handleMessage(msg, subject, "nats.subscribe", false, handler);
    }

    /**
     * Wraps a MessageHandler for JetStream consumers.
     * Captures JetStream server metadata (stream, sequence, server timestamp) as span attributes.
     */
    public MessageHandler wrapJetStream(String subject, MessageHandler handler) {
        return msg -> handleMessage(msg, subject, "nats.jetstream.subscribe", true, handler);
    }

    private void handleMessage(Message msg, String subject, String spanName,
                                boolean captureJsMetadata, MessageHandler handler) throws InterruptedException {
        // Extract trace context from incoming headers (the publisher's traceparent)
        Context extractedContext = propagator.extract(Context.root(), msg, NatsHeaderGetter.INSTANCE);

        Span span = tracer.spanBuilder(spanName)
                .setParent(extractedContext)
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute("messaging.system", "nats")
                .setAttribute("messaging.destination", subject)
                .setAttribute("messaging.destination_kind", captureJsMetadata ? "stream" : "topic")
                .startSpan();

        if (captureJsMetadata) {
            addJetStreamMetadata(span, msg);
        }

        try (Scope ignored = span.makeCurrent()) {
            handler.onMessage(msg);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Extracts JetStream server-injected headers and adds them as span attributes.
     * These allow calculation of server storage time and delivery latency.
     */
    private void addJetStreamMetadata(Span span, Message msg) {
        if (msg.getHeaders() == null) return;
        String stream = msg.getHeaders().getFirst("Nats-Stream");
        String seq    = msg.getHeaders().getFirst("Nats-Sequence");
        String ts     = msg.getHeaders().getFirst("Nats-Time-Stamp");
        if (stream != null) span.setAttribute("nats.stream", stream);
        if (seq    != null) span.setAttribute("nats.sequence", seq);
        if (ts     != null) span.setAttribute("nats.server_timestamp", ts);
    }
}
