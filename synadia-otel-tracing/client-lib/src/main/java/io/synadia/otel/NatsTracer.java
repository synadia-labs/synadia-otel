package io.synadia.otel;

import io.nats.client.Connection;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.synadia.otel.exporter.NatsSpanExporter;

/**
 * Entry point for the Synadia NATS OTel client library.
 *
 * Usage:
 * <pre>
 *   TracingConfig config = TracingConfig.fromFile("tracing.properties");
 *   Connection nc = Nats.connect(config.getNatsUrl());
 *   NatsTracer tracer = NatsTracer.builder().connection(nc).config(config).build();
 *
 *   NatsTracingPublisher pub = tracer.publisher();
 *   NatsTracingSubscriber sub = tracer.subscriber();
 * </pre>
 */
public class NatsTracer implements AutoCloseable {

    private final OpenTelemetry openTelemetry;
    private final SdkTracerProvider tracerProvider;  // null when tracing is disabled
    private final Connection nc;
    private final TracingConfig config;

    private NatsTracer(OpenTelemetry openTelemetry, SdkTracerProvider tracerProvider,
                       Connection nc, TracingConfig config) {
        this.openTelemetry  = openTelemetry;
        this.tracerProvider = tracerProvider;
        this.nc             = nc;
        this.config         = config;
    }

    public static Builder builder() {
        return new Builder();
    }

    public NatsTracingPublisher publisher() {
        String serverTraceDest = config.isTracingEnabled() ? config.getServerTraceDest() : "";
        return new NatsTracingPublisher(nc, openTelemetry, serverTraceDest);
    }

    public NatsTracingSubscriber subscriber() {
        return new NatsTracingSubscriber(openTelemetry);
    }

    /** Access the underlying OpenTelemetry instance for advanced use. */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    @Override
    public void close() {
        if (tracerProvider != null) {
            tracerProvider.forceFlush();
            tracerProvider.close();
        }
    }

    // -------------------------------------------------------------------------

    public static class Builder {
        private Connection nc;
        private TracingConfig config = new TracingConfig();

        public Builder connection(Connection nc) {
            this.nc = nc;
            return this;
        }

        public Builder config(TracingConfig config) {
            this.config = config;
            return this;
        }

        public NatsTracer build() {
            if (nc == null) throw new IllegalStateException("NATS Connection must be provided");

            if (!config.isTracingEnabled()) {
                // No-op: no spans created, no headers injected, no telemetry traffic on NATS
                return new NatsTracer(OpenTelemetry.noop(), null, nc, config);
            }

            NatsSpanExporter exporter = new NatsSpanExporter(nc, config.getTracesSubject());

            Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(
                    AttributeKey.stringKey("service.name"), config.getServiceName()
                ))
            );

            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                    .setResource(resource)
                    .build();

            OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();

            return new NatsTracer(openTelemetry, tracerProvider, nc, config);
        }
    }
}
