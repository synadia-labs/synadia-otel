package io.synadia.otel.exporter;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.synadia.otel.exporter.model.MsgTraceEvent;
import io.synadia.otel.exporter.model.MsgTraceEvent.TraceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a NATS Server MsgTraceEvent (ADR-41) into a list of OTel SpanData objects.
 *
 * Correlation:
 * - The original message's traceparent header (in request.header) gives us the
 *   client's traceId and spanId.
 * - All server spans share the same traceId → appear in the same Jaeger trace.
 * - Ingress span is a child of the client publish span (parentSpanId = client spanId).
 * - Egress/JetStream/etc. spans are children of the Ingress span.
 */
public class ServerTraceConverter {

    private static final Logger log = LoggerFactory.getLogger(ServerTraceConverter.class);
    private static final String W3C_TRACEPARENT = "traceparent";
    private static final InstrumentationScopeInfo NATS_SERVER_SCOPE =
            InstrumentationScopeInfo.create("io.nats.server", "2.11", null);

    public List<SpanData> convert(MsgTraceEvent event) {
        List<SpanData> spans = new ArrayList<>();
        if (event.events == null || event.events.isEmpty()) {
            log.debug("MsgTraceEvent has no events, skipping");
            return spans;
        }

        // Extract client's traceId and parentSpanId from the original traceparent header
        String traceparent = event.getHeader(W3C_TRACEPARENT);
        TraceIds ids = parseTraceparent(traceparent);
        if (ids == null) {
            log.debug("No valid traceparent in MsgTraceEvent (subj={}), skipping server spans",
                      event.getIngressSubject());
            return spans;
        }

        String subject = event.getIngressSubject();
        String account = event.getIngressAccount();

        Resource serverResource = Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"),
                "nats-server:" + (event.server != null ? event.server.name : "unknown"),
                AttributeKey.stringKey("nats.server.name"),
                event.server != null ? event.server.name : "unknown",
                AttributeKey.stringKey("nats.server.cluster"),
                event.server != null && event.server.cluster != null ? event.server.cluster : "",
                AttributeKey.stringKey("nats.server.version"),
                event.server != null ? event.server.version : ""
        ));

        long eventStartNanos = event.server != null
                ? parseTimestamp(event.server.time)
                : System.currentTimeMillis() * 1_000_000L;

        // Find the Ingress span ID — used as parent for sibling spans
        String ingressSpanId = null;
        for (TraceRecord rec : event.events) {
            if ("in".equals(rec.type)) {
                ingressSpanId = generateSpanId(ids.traceId, event, rec);
                break;
            }
        }

        TraceFlags flags = TraceFlags.fromHex(ids.traceFlags, 0);

        for (TraceRecord rec : event.events) {
            String spanId     = generateSpanId(ids.traceId, event, rec);
            String parentId;
            String spanName;

            switch (rec.type != null ? rec.type : "") {
                case "in" -> {
                    parentId = ids.clientSpanId;   // child of client publish span
                    spanName = "nats.server.ingress";
                }
                case "eg" -> {
                    parentId = ingressSpanId != null ? ingressSpanId : ids.clientSpanId;
                    spanName = "nats.server.egress";
                }
                case "js" -> {
                    parentId = ingressSpanId != null ? ingressSpanId : ids.clientSpanId;
                    spanName = "nats.server.jetstream";
                }
                case "sm" -> {
                    parentId = ingressSpanId != null ? ingressSpanId : ids.clientSpanId;
                    spanName = "nats.server.subject_mapping";
                }
                case "se" -> {
                    parentId = ingressSpanId != null ? ingressSpanId : ids.clientSpanId;
                    spanName = "nats.server.stream_export";
                }
                case "si" -> {
                    parentId = ingressSpanId != null ? ingressSpanId : ids.clientSpanId;
                    spanName = "nats.server.service_import";
                }
                default -> {
                    parentId = ingressSpanId != null ? ingressSpanId : ids.clientSpanId;
                    spanName = "nats.server." + rec.type;
                }
            }

            SpanContext spanCtx   = SpanContext.create(ids.traceId, spanId, flags, TraceState.getDefault());
            SpanContext parentCtx = SpanContext.create(ids.traceId, parentId, flags, TraceState.getDefault());

            long recTs = rec.timestamp != null ? parseTimestamp(rec.timestamp) : eventStartNanos;
            long endTs = recTs + 1_000_000L;  // 1ms placeholder — actual duration not in event

            StatusData status = (rec.error != null && !rec.error.isBlank())
                    ? StatusData.create(StatusCode.ERROR, rec.error)
                    : StatusData.unset();

            spans.add(ReconstitutedSpanData.builder()
                    .spanContext(spanCtx)
                    .parentSpanContext(parentCtx)
                    .resource(serverResource)
                    .scopeInfo(NATS_SERVER_SCOPE)
                    .name(spanName)
                    .kind(SpanKind.INTERNAL)
                    .startEpochNanos(recTs)
                    .endEpochNanos(endTs)
                    .attributes(buildAttributes(rec, subject, account))
                    .status(status)
                    .build());
        }

        log.info("Converted {} server span(s) for subject '{}' traceId={}",
                spans.size(), subject, ids.traceId);
        return spans;
    }

    private Attributes buildAttributes(TraceRecord rec, String subject, String account) {
        var b = Attributes.builder();
        b.put(AttributeKey.stringKey("messaging.system"), "nats");
        if (subject  != null) b.put(AttributeKey.stringKey("messaging.destination"), subject);
        if (account  != null) b.put(AttributeKey.stringKey("nats.account"), account);
        if (rec.kind >= 0)   b.put(AttributeKey.stringKey("nats.connection_kind"),
                                   MsgTraceEvent.kindName(rec.kind));
        if (rec.name != null) b.put(AttributeKey.stringKey("nats.connection_name"), rec.name);
        if (rec.cid  != 0)   b.put(AttributeKey.longKey("nats.connection_id"), rec.cid);
        if (rec.hop  != null && !rec.hop.isBlank())
                              b.put(AttributeKey.stringKey("nats.hop"), rec.hop);
        if (rec.stream != null) b.put(AttributeKey.stringKey("nats.stream"), rec.stream);
        if (rec.queue  != null) b.put(AttributeKey.stringKey("nats.queue"), rec.queue);
        if (rec.mappedTo != null) b.put(AttributeKey.stringKey("nats.mapping.to"), rec.mappedTo);
        return b.build();
    }

    /**
     * Generates a deterministic 16-char hex span ID so the same event record
     * always maps to the same span ID.
     */
    private String generateSpanId(String traceId, MsgTraceEvent event, TraceRecord rec) {
        long seed = (long) traceId.hashCode() * 31
                  + (event.server != null ? event.server.seq : 0)
                  + (rec.type != null ? rec.type.hashCode() : 0)
                  + rec.cid;
        return String.format("%016x", seed & Long.MAX_VALUE);
    }

    /** Parses W3C traceparent: 00-{traceId}-{spanId}-{flags} */
    private TraceIds parseTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) return null;
        String[] parts = traceparent.split("-");
        if (parts.length < 4) return null;
        return new TraceIds(parts[1], parts[2], parts[3]);
    }

    private long parseTimestamp(String iso) {
        if (iso == null || iso.isBlank()) return System.currentTimeMillis() * 1_000_000L;
        try {
            return Instant.parse(iso).toEpochMilli() * 1_000_000L;
        } catch (DateTimeParseException e) {
            return System.currentTimeMillis() * 1_000_000L;
        }
    }

    private record TraceIds(String traceId, String clientSpanId, String traceFlags) {}
}
