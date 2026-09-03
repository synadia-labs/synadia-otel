package io.synadia.otel.exporter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

import java.util.List;

/**
 * Immutable SpanData implementation used to reconstruct spans received as JSON
 * (client spans) or converted from MsgTraceEvent (server spans) for forwarding
 * via OtlpGrpcSpanExporter to Jaeger.
 */
public class ReconstitutedSpanData implements SpanData {

    private final SpanContext spanContext;
    private final SpanContext parentSpanContext;
    private final Resource resource;
    private final InstrumentationScopeInfo scopeInfo;
    private final String name;
    private final SpanKind kind;
    private final long startEpochNanos;
    private final long endEpochNanos;
    private final Attributes attributes;
    private final List<EventData> events;
    private final List<LinkData> links;
    private final StatusData status;

    private ReconstitutedSpanData(Builder builder) {
        this.spanContext       = builder.spanContext;
        this.parentSpanContext = builder.parentSpanContext;
        this.resource          = builder.resource;
        this.scopeInfo         = builder.scopeInfo;
        this.name              = builder.name;
        this.kind              = builder.kind;
        this.startEpochNanos   = builder.startEpochNanos;
        this.endEpochNanos     = builder.endEpochNanos;
        this.attributes        = builder.attributes;
        this.events            = builder.events;
        this.links             = builder.links;
        this.status            = builder.status;
    }

    public static Builder builder() { return new Builder(); }

    @Override
    @SuppressWarnings("deprecation")
    public InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
        return InstrumentationLibraryInfo.create(scopeInfo.getName(), scopeInfo.getVersion());
    }

    @Override public SpanContext getSpanContext()       { return spanContext; }
    @Override public SpanContext getParentSpanContext() { return parentSpanContext; }
    @Override public Resource getResource()             { return resource; }
    @Override public InstrumentationScopeInfo getInstrumentationScopeInfo() { return scopeInfo; }
    @Override public String getName()                   { return name; }
    @Override public SpanKind getKind()                 { return kind; }
    @Override public long getStartEpochNanos()          { return startEpochNanos; }
    @Override public long getEndEpochNanos()            { return endEpochNanos; }
    @Override public Attributes getAttributes()         { return attributes; }
    @Override public List<EventData> getEvents()        { return events; }
    @Override public List<LinkData> getLinks()          { return links; }
    @Override public StatusData getStatus()             { return status; }
    @Override public boolean hasEnded()                 { return true; }
    @Override public int getTotalRecordedEvents()       { return events.size(); }
    @Override public int getTotalRecordedLinks()        { return links.size(); }
    @Override public int getTotalAttributeCount()       { return attributes.size(); }

    public static class Builder {
        private SpanContext spanContext       = SpanContext.getInvalid();
        private SpanContext parentSpanContext = SpanContext.getInvalid();
        private Resource resource            = Resource.getDefault();
        private InstrumentationScopeInfo scopeInfo = InstrumentationScopeInfo.empty();
        private String name                  = "";
        private SpanKind kind                = SpanKind.INTERNAL;
        private long startEpochNanos         = 0;
        private long endEpochNanos           = 0;
        private Attributes attributes        = Attributes.empty();
        private List<EventData> events       = List.of();
        private List<LinkData> links         = List.of();
        private StatusData status            = StatusData.unset();

        public Builder spanContext(SpanContext v)             { spanContext = v;       return this; }
        public Builder parentSpanContext(SpanContext v)       { parentSpanContext = v; return this; }
        public Builder resource(Resource v)                  { resource = v;          return this; }
        public Builder scopeInfo(InstrumentationScopeInfo v) { scopeInfo = v;         return this; }
        public Builder name(String v)                        { name = v;              return this; }
        public Builder kind(SpanKind v)                      { kind = v;              return this; }
        public Builder startEpochNanos(long v)               { startEpochNanos = v;   return this; }
        public Builder endEpochNanos(long v)                 { endEpochNanos = v;     return this; }
        public Builder attributes(Attributes v)              { attributes = v;        return this; }
        public Builder events(List<EventData> v)             { events = v;            return this; }
        public Builder links(List<LinkData> v)               { links = v;             return this; }
        public Builder status(StatusData v)                  { status = v;            return this; }

        public ReconstitutedSpanData build() { return new ReconstitutedSpanData(this); }
    }
}
