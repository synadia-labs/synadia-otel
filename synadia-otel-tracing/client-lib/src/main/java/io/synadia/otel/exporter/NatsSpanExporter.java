package io.synadia.otel.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.synadia.otel.model.SpanEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * OTel SpanExporter that serialises completed spans to JSON and publishes them
 * to a configurable NATS subject for pickup by the exporter-service.
 */
public class NatsSpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(NatsSpanExporter.class);

    private final Connection nc;
    private final String subject;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean shutdown = false;

    public NatsSpanExporter(Connection nc, String subject) {
        this.nc = nc;
        this.subject = subject;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        if (shutdown) return CompletableResultCode.ofFailure();
        for (SpanData span : spans) {
            try {
                SpanEnvelope envelope = SpanEnvelope.from(span);
                byte[] payload = mapper.writeValueAsBytes(envelope);
                nc.publish(subject, payload);
            } catch (Exception e) {
                log.error("Failed to export span '{}' to NATS subject '{}'", span.getName(), subject, e);
            }
        }
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        try {
            nc.flush(java.time.Duration.ofSeconds(2));
        } catch (Exception e) {
            log.warn("Flush failed", e);
            return CompletableResultCode.ofFailure();
        }
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        shutdown = true;
        return CompletableResultCode.ofSuccess();
    }
}
