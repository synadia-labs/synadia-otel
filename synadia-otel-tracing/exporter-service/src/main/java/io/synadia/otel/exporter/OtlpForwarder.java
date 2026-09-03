package io.synadia.otel.exporter;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;

/**
 * Forwards SpanData to a Jaeger instance (or any OTel Collector) via OTLP/gRPC.
 * Jaeger 1.35+ accepts OTLP on port 4317 natively.
 */
public class OtlpForwarder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OtlpForwarder.class);

    private final SpanExporter delegate;

    private OtlpForwarder(SpanExporter delegate) {
        this.delegate = delegate;
    }

    public static OtlpForwarder create(String jaegerEndpoint) {
        SpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(jaegerEndpoint)
                .setTimeout(Duration.ofSeconds(10))
                .build();
        log.info("OtlpForwarder configured → {}", jaegerEndpoint);
        return new OtlpForwarder(exporter);
    }

    public CompletableResultCode export(Collection<SpanData> spans) {
        if (spans.isEmpty()) return CompletableResultCode.ofSuccess();
        CompletableResultCode result = delegate.export(spans);
        result.whenComplete(() -> {
            if (!result.isSuccess()) {
                log.warn("OTLP export failed for {} span(s)", spans.size());
            }
        });
        return result;
    }

    public void flush() {
        delegate.flush();
    }

    @Override
    public void close() {
        delegate.shutdown();
    }
}
