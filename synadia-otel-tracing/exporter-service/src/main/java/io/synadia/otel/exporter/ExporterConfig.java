package io.synadia.otel.exporter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration for the NATS OTel exporter service.
 * Load from a .properties file or configure via constructor.
 *
 * Properties:
 *   nats.url                   — NATS server URL
 *   client.traces.subject      — Subject where client-lib publishes span JSON
 *   server.traces.subject      — Subject where NATS server publishes MsgTraceEvent JSON (ADR-41)
 *   jaeger.endpoint            — OTLP/gRPC endpoint for Jaeger (or any OTel Collector)
 */
public class ExporterConfig {

    private String natsUrl              = "nats://localhost:4222";
    private String clientTracesSubject  = "telemetry.traces";
    private String serverTracesSubject  = "telemetry.server.traces";
    private String jaegerEndpoint       = "http://localhost:4317";

    public ExporterConfig() {}

    // --- factory ---

    public static ExporterConfig fromFile(String path) throws IOException {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(path)) {
            props.load(is);
        }
        return fromProperties(props);
    }

    public static ExporterConfig fromClasspath(String resourceName) throws IOException {
        Properties props = new Properties();
        try (InputStream is = ExporterConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) throw new IOException("Resource not found: " + resourceName);
            props.load(is);
        }
        return fromProperties(props);
    }

    public static ExporterConfig fromProperties(Properties props) {
        ExporterConfig cfg = new ExporterConfig();
        cfg.natsUrl             = props.getProperty("nats.url",                cfg.natsUrl);
        cfg.clientTracesSubject = props.getProperty("client.traces.subject",   cfg.clientTracesSubject);
        cfg.serverTracesSubject = props.getProperty("server.traces.subject",   cfg.serverTracesSubject);
        cfg.jaegerEndpoint      = props.getProperty("jaeger.endpoint",         cfg.jaegerEndpoint);
        return cfg;
    }

    // --- getters ---

    public String getNatsUrl()             { return natsUrl; }
    public String getClientTracesSubject() { return clientTracesSubject; }
    public String getServerTracesSubject() { return serverTracesSubject; }
    public String getJaegerEndpoint()      { return jaegerEndpoint; }

    @Override
    public String toString() {
        return "ExporterConfig{natsUrl='" + natsUrl + "', clientTracesSubject='" + clientTracesSubject +
               "', serverTracesSubject='" + serverTracesSubject + "', jaegerEndpoint='" + jaegerEndpoint + "'}";
    }
}
