package io.synadia.otel.model;

import java.util.Map;

public class SpanEventModel {
    public String name;
    public long epochNanos;
    public Map<String, Object> attributes;

    public SpanEventModel() {}

    public SpanEventModel(String name, long epochNanos, Map<String, Object> attributes) {
        this.name = name;
        this.epochNanos = epochNanos;
        this.attributes = attributes;
    }
}
