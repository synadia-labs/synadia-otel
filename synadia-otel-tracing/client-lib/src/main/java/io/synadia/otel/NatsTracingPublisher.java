package io.synadia.otel;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.synadia.otel.propagation.NatsHeaderSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a NATS Connection to publish messages with OTel trace context injected
 * into message headers (traceparent / tracestate).
 *
 * The traceparent header is also read by NATS Server 2.11+ to trigger
 * server-side message path tracing (ADR-41) when the account is configured
 * with msg_trace.dest.
 */
public class NatsTracingPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsTracingPublisher.class);
    private static final String NATS_TRACE_DEST_HEADER = "Nats-Trace-Dest";

    private final Connection nc;
    private final Tracer tracer;
    private final TextMapPropagator propagator;
    private final String serverTraceDest;  // optional: per-message Nats-Trace-Dest override
    private JetStream js;

    NatsTracingPublisher(Connection nc, OpenTelemetry openTelemetry, String serverTraceDest) {
        this.nc             = nc;
        this.tracer         = openTelemetry.getTracer("io.synadia.otel", "1.0.0");
        this.propagator     = openTelemetry.getPropagators().getTextMapPropagator();
        this.serverTraceDest = serverTraceDest;
    }

    // --- Core NATS publish ---

    public void publish(String subject, byte[] data) {
        publish(subject, data, Context.current());
    }

    public void publish(String subject, byte[] data, Context parentContext) {
        Span span = tracer.spanBuilder("nats.publish")
                .setParent(parentContext)
                .setSpanKind(SpanKind.PRODUCER)
                .setAttribute("messaging.system", "nats")
                .setAttribute("messaging.destination", subject)
                .setAttribute("messaging.destination_kind", "topic")
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            Message msg = buildMessage(subject, null, data);
            nc.publish(msg);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw new RuntimeException("NATS publish failed for subject: " + subject, e);
        } finally {
            span.end();
        }
    }

    // --- JetStream publish ---

    public void publishJetStream(String subject, byte[] data) throws Exception {
        publishJetStream(subject, data, Context.current());
    }

    public void publishJetStream(String subject, byte[] data, Context parentContext) throws Exception {
        Span span = tracer.spanBuilder("nats.jetstream.publish")
                .setParent(parentContext)
                .setSpanKind(SpanKind.PRODUCER)
                .setAttribute("messaging.system", "nats")
                .setAttribute("messaging.destination", subject)
                .setAttribute("messaging.destination_kind", "stream")
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            if (js == null) js = nc.jetStream();
            Message msg = buildMessage(subject, null, data);
            js.publish(msg);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // --- Request/reply ---

    public io.nats.client.Message request(String subject, byte[] data, java.time.Duration timeout)
            throws Exception {
        return request(subject, data, timeout, Context.current());
    }

    public io.nats.client.Message request(String subject, byte[] data, java.time.Duration timeout,
                                          Context parentContext) throws Exception {
        Span span = tracer.spanBuilder("nats.request")
                .setParent(parentContext)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("messaging.system", "nats")
                .setAttribute("messaging.destination", subject)
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            Message msg = buildMessage(subject, null, data);
            return nc.request(msg, timeout);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // --- helpers ---

    private Message buildMessage(String subject, String replyTo, byte[] data) {
        Headers headers = new Headers();
        // Inject traceparent / tracestate into headers
        propagator.inject(Context.current(), headers, NatsHeaderSetter.INSTANCE);
        // Optionally inject Nats-Trace-Dest for per-message server tracing (ADR-41)
        if (serverTraceDest != null && !serverTraceDest.isBlank()) {
            headers.add(NATS_TRACE_DEST_HEADER, serverTraceDest);
        }
        return NatsMessage.builder()
                .subject(subject)
                .replyTo(replyTo)
                .headers(headers)
                .data(data)
                .build();
    }
}
