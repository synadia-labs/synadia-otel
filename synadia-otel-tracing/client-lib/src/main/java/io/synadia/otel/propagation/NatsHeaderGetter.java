package io.synadia.otel.propagation;

import io.nats.client.Message;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.Collections;

/**
 * Extracts OTel context (traceparent, tracestate) from incoming NATS message headers.
 */
public class NatsHeaderGetter implements TextMapGetter<Message> {

    public static final NatsHeaderGetter INSTANCE = new NatsHeaderGetter();

    private NatsHeaderGetter() {}

    @Override
    public String get(Message carrier, String key) {
        if (carrier == null || carrier.getHeaders() == null) return null;
        return carrier.getHeaders().getFirst(key);
    }

    @Override
    public Iterable<String> keys(Message carrier) {
        if (carrier == null || carrier.getHeaders() == null) return Collections.emptyList();
        return carrier.getHeaders().keySet();
    }
}
