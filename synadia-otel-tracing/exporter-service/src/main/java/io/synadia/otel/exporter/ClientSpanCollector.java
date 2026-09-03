package io.synadia.otel.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.synadia.otel.model.SpanEnvelope;
import io.synadia.otel.model.SpanEventModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Subscribes to the client-span NATS subject, deserialises SpanEnvelope JSON,
 * reconstitutes OTel SpanData, and forwards to Jaeger via OtlpForwarder.
 */
public class ClientSpanCollector {

    private static final Logger log = LoggerFactory.getLogger(ClientSpanCollector.class);

    private final Connection nc;
    private final String subject;
    private final OtlpForwarder forwarder;
    private final ObjectMapper mapper = new ObjectMapper();
    private Dispatcher dispatcher;

    public ClientSpanCollector(Connection nc, String subject, OtlpForwarder forwarder) {
        this.nc       = nc;
        this.subject  = subject;
        this.forwarder = forwarder;
    }

    public void start() {
        dispatcher = nc.createDispatcher(msg -> {
            try {
                SpanEnvelope env = mapper.readValue(msg.getData(), SpanEnvelope.class);
                ReconstitutedSpanData span = toSpanData(env);
                forwarder.export(List.of(span));
            } catch (Exception e) {
                log.error("Failed to process client span from subject '{}'", subject, e);
            }
        });
        dispatcher.subscribe(subject);
        log.info("ClientSpanCollector listening on '{}'", subject);
    }

    public void stop() {
        if (dispatcher != null) dispatcher.unsubscribe(subject);
    }

    private ReconstitutedSpanData toSpanData(SpanEnvelope env) {
        TraceFlags flags = TraceFlags.fromByte(
                (byte) Integer.parseInt(env.traceFlags != null ? env.traceFlags : "01", 16));
        TraceState traceState = (env.traceState != null && !env.traceState.isBlank())
                ? TraceState.builder().build()   // simplified: full tracestate parsing is optional
                : TraceState.getDefault();

        SpanContext spanCtx = SpanContext.create(env.traceId, env.spanId, flags, traceState);
        SpanContext parentCtx = (env.parentSpanId != null && !env.parentSpanId.isBlank())
                ? SpanContext.create(env.traceId, env.parentSpanId, flags, traceState)
                : SpanContext.getInvalid();

        Resource resource = Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"),
                env.serviceName != null ? env.serviceName : "unknown"));

        InstrumentationScopeInfo scope = InstrumentationScopeInfo.create(
                env.scopeName != null ? env.scopeName : "io.synadia.otel",
                env.scopeVersion, null);

        Attributes attrs = buildAttributes(env.attributes);
        List<EventData> events = buildEvents(env.events);

        StatusData status = switch (env.statusCode != null ? env.statusCode : "UNSET") {
            case "OK"    -> StatusData.ok();
            case "ERROR" -> StatusData.create(StatusCode.ERROR,
                    env.statusMessage != null ? env.statusMessage : "");
            default      -> StatusData.unset();
        };

        return ReconstitutedSpanData.builder()
                .spanContext(spanCtx)
                .parentSpanContext(parentCtx)
                .resource(resource)
                .scopeInfo(scope)
                .name(env.name)
                .kind(parseKind(env.spanKind))
                .startEpochNanos(env.startEpochNanos)
                .endEpochNanos(env.endEpochNanos)
                .attributes(attrs)
                .events(events)
                .status(status)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Attributes buildAttributes(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return Attributes.empty();
        AttributesBuilder b = Attributes.builder();
        map.forEach((k, v) -> {
            if (v instanceof String s)   b.put(AttributeKey.stringKey(k), s);
            else if (v instanceof Number n) b.put(AttributeKey.longKey(k), n.longValue());
            else if (v instanceof Boolean bool) b.put(AttributeKey.booleanKey(k), bool);
            else if (v != null) b.put(AttributeKey.stringKey(k), v.toString());
        });
        return b.build();
    }

    private List<EventData> buildEvents(List<SpanEventModel> models) {
        if (models == null) return List.of();
        List<EventData> result = new ArrayList<>();
        for (SpanEventModel m : models) {
            result.add(EventData.create(m.epochNanos, m.name, buildAttributes(m.attributes)));
        }
        return result;
    }

    private SpanKind parseKind(String kind) {
        if (kind == null) return SpanKind.INTERNAL;
        return switch (kind.toUpperCase()) {
            case "SERVER"   -> SpanKind.SERVER;
            case "CLIENT"   -> SpanKind.CLIENT;
            case "PRODUCER" -> SpanKind.PRODUCER;
            case "CONSUMER" -> SpanKind.CONSUMER;
            default         -> SpanKind.INTERNAL;
        };
    }
}
