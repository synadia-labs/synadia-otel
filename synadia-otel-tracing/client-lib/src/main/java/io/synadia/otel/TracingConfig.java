package io.synadia.otel;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration for the client-side NATS tracing library.
 * Load from a .properties file or configure programmatically via the builder.
 *
 * Properties:
 *   nats.url                  — NATS server URL (default: nats://localhost:4222)
 *   service.name              — OTel service name tag (default: nats-app)
 *   traces.subject            — NATS subject for client span export (default: telemetry.traces)
 *   server.trace.dest         — Subject injected as Nats-Trace-Dest header per message (optional)
 *                               Leave blank to rely on account-level server config instead.
 *   tracing.enabled           — Master switch: false disables all tracing (default: true)
 */
public class TracingConfig {

    private String  natsUrl           = "nats://localhost:4222";
    private String  serviceName       = "nats-app";
    private String  tracesSubject     = "telemetry.traces";
    private String  serverTraceDest   = "";   // empty = don't inject Nats-Trace-Dest
    private boolean tracingEnabled    = true;

    public TracingConfig() {}

    // --- factory ---

    public static TracingConfig fromFile(String path) throws IOException {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(path)) {
            props.load(is);
        }
        return fromProperties(props);
    }

    public static TracingConfig fromClasspath(String resourceName) throws IOException {
        Properties props = new Properties();
        try (InputStream is = TracingConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) throw new IOException("Resource not found: " + resourceName);
            props.load(is);
        }
        return fromProperties(props);
    }

    public static TracingConfig fromProperties(Properties props) {
        TracingConfig cfg = new TracingConfig();
        cfg.natsUrl         = props.getProperty("nats.url",           cfg.natsUrl);
        cfg.serviceName     = props.getProperty("service.name",        cfg.serviceName);
        cfg.tracesSubject   = props.getProperty("traces.subject",      cfg.tracesSubject);
        cfg.serverTraceDest = props.getProperty("server.trace.dest",   cfg.serverTraceDest);
        cfg.tracingEnabled  = Boolean.parseBoolean(props.getProperty("tracing.enabled", "true"));
        return cfg;
    }

    // --- builder ---

    public TracingConfig natsUrl(String url)            { this.natsUrl = url;             return this; }
    public TracingConfig serviceName(String name)       { this.serviceName = name;        return this; }
    public TracingConfig tracesSubject(String subject)  { this.tracesSubject = subject;   return this; }
    public TracingConfig serverTraceDest(String dest)   { this.serverTraceDest = dest;    return this; }
    public TracingConfig tracingEnabled(boolean enabled){ this.tracingEnabled = enabled;  return this; }

    // --- getters ---

    public String  getNatsUrl()         { return natsUrl; }
    public String  getServiceName()     { return serviceName; }
    public String  getTracesSubject()   { return tracesSubject; }
    public String  getServerTraceDest() { return serverTraceDest; }
    public boolean isTracingEnabled()   { return tracingEnabled; }
    public boolean isServerTraceDestEnabled() { return serverTraceDest != null && !serverTraceDest.isBlank(); }

    @Override
    public String toString() {
        return "TracingConfig{natsUrl='" + natsUrl + "', serviceName='" + serviceName +
               "', tracesSubject='" + tracesSubject + "', serverTraceDest='" + serverTraceDest +
               "', tracingEnabled=" + tracingEnabled + "}";
    }
}
