package io.synadia.otel.model;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-serialisable representation of a completed OTel span.
 * Published to NATS by NatsSpanExporter; consumed by the exporter-service.
 */
public class SpanEnvelope {
    public String traceId;
    public String spanId;
    public String parentSpanId;      // null if root span
    public String traceFlags;        // 2-char hex, e.g. "01"
    public String traceState;        // W3C tracestate, may be empty
    public String name;
    public String spanKind;          // INTERNAL|SERVER|CLIENT|PRODUCER|CONSUMER
    public long   startEpochNanos;
    public long   endEpochNanos;
    public Map<String, Object> attributes;
    public List<SpanEventModel> events;
    public String statusCode;        // UNSET|OK|ERROR
    public String statusMessage;
    public String serviceName;
    public String scopeName;
    public String scopeVersion;

    public SpanEnvelope() {}

    public static SpanEnvelope from(SpanData span) {
        SpanEnvelope env = new SpanEnvelope();
        env.traceId      = span.getSpanContext().getTraceId();
        env.spanId       = span.getSpanContext().getSpanId();
        env.traceFlags   = span.getSpanContext().getTraceFlags().asHex();
        // Serialise TraceState as W3C format: key1=value1,key2=value2
        StringBuilder tsBuilder = new StringBuilder();
        span.getSpanContext().getTraceState().forEach((k, v) -> {
            if (tsBuilder.length() > 0) tsBuilder.append(',');
            tsBuilder.append(k).append('=').append(v);
        });
        env.traceState   = tsBuilder.toString();
        env.parentSpanId = span.getParentSpanContext().isValid()
                           ? span.getParentSpanContext().getSpanId() : null;
        env.name              = span.getName();
        env.spanKind          = span.getKind().name();
        env.startEpochNanos   = span.getStartEpochNanos();
        env.endEpochNanos     = span.getEndEpochNanos();
        env.statusCode        = span.getStatus().getStatusCode().name();
        env.statusMessage     = span.getStatus().getDescription();

        // Resource service.name
        env.serviceName = span.getResource().getAttributes()
                              .get(AttributeKey.stringKey("service.name"));

        env.scopeName    = span.getInstrumentationScopeInfo().getName();
        env.scopeVersion = span.getInstrumentationScopeInfo().getVersion();

        // Attributes — flatten to Map<String, Object>
        env.attributes = new HashMap<>();
        span.getAttributes().forEach((k, v) -> env.attributes.put(k.getKey(), v));

        // Events
        env.events = new ArrayList<>();
        for (EventData event : span.getEvents()) {
            Map<String, Object> evtAttrs = new HashMap<>();
            event.getAttributes().forEach((k, v) -> evtAttrs.put(k.getKey(), v));
            env.events.add(new SpanEventModel(event.getName(), event.getEpochNanos(), evtAttrs));
        }

        return env;
    }
}
