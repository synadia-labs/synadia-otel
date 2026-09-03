package io.synadia.otel.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.synadia.otel.exporter.model.MsgTraceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Subscribes to the NATS server trace event subject (ADR-41 MsgTraceEvent JSON),
 * converts each event to OTel spans, and forwards to Jaeger via OtlpForwarder.
 *
 * The NATS server must be configured with msg_trace.dest pointing to this subject,
 * or clients must inject the Nats-Trace-Dest header per message.
 *
 * Server trace events are correlated with client spans via the traceparent header
 * embedded in the MsgTraceEvent's original message headers.
 */
public class ServerTraceEventCollector {

    private static final Logger log = LoggerFactory.getLogger(ServerTraceEventCollector.class);

    private final Connection nc;
    private final String subject;
    private final ServerTraceConverter converter;
    private final OtlpForwarder forwarder;
    private final ObjectMapper mapper = new ObjectMapper();
    private Dispatcher dispatcher;

    public ServerTraceEventCollector(Connection nc, String subject,
                                     ServerTraceConverter converter, OtlpForwarder forwarder) {
        this.nc        = nc;
        this.subject   = subject;
        this.converter = converter;
        this.forwarder = forwarder;
    }

    public void start() {
        dispatcher = nc.createDispatcher(msg -> {
            try {
                log.info("Received server trace event ({} bytes)", msg.getData().length);
                MsgTraceEvent event = mapper.readValue(msg.getData(), MsgTraceEvent.class);
                List<SpanData> spans = converter.convert(event);
                if (!spans.isEmpty()) {
                    forwarder.export(spans);
                } else {
                    log.info("Server trace event produced no spans (missing traceparent?)");
                }
            } catch (Exception e) {
                log.error("Failed to process server trace event", e);
                log.error("Raw payload: {}", new String(msg.getData()));
            }
        });
        dispatcher.subscribe(subject);
        log.info("ServerTraceEventCollector listening on '{}'", subject);
    }

    public void stop() {
        if (dispatcher != null) dispatcher.unsubscribe(subject);
    }
}
