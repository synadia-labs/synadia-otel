package io.synadia.otel.propagation;

import io.nats.client.impl.Headers;
import io.opentelemetry.context.propagation.TextMapSetter;

/**
 * Injects OTel context (traceparent, tracestate) into NATS message headers.
 * The NATS server reads traceparent automatically for server-side message path tracing (ADR-41).
 */
public class NatsHeaderSetter implements TextMapSetter<Headers> {

    public static final NatsHeaderSetter INSTANCE = new NatsHeaderSetter();

    private NatsHeaderSetter() {}

    @Override
    public void set(Headers carrier, String key, String value) {
        if (carrier != null && key != null && value != null) {
            carrier.add(key, value);
        }
    }
}
