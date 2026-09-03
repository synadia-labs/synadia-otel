package io.synadia.otel.exporter;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Synadia NATS OTel Exporter Service.
 *
 * Usage:
 *   java -jar exporter-service.jar [config-file.properties]
 *
 * If no config file is specified, loads config.properties from the classpath.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        ExporterConfig config = args.length > 0
                ? ExporterConfig.fromFile(args[0])
                : ExporterConfig.fromClasspath("config.properties");

        log.info("Starting NATS OTel Exporter Service");
        log.info("Config: {}", config);

        Connection nc = Nats.connect(
                Options.builder()
                        .server(config.getNatsUrl())
                        .reconnectWait(java.time.Duration.ofSeconds(2))
                        .maxReconnects(-1)
                        .connectionListener((conn, type) ->
                                log.info("NATS connection event: {}", type))
                        .build());

        OtlpForwarder forwarder = OtlpForwarder.create(config.getJaegerEndpoint());
        ServerTraceConverter converter = new ServerTraceConverter();

        ClientSpanCollector clientCollector = new ClientSpanCollector(
                nc, config.getClientTracesSubject(), forwarder);
        ServerTraceEventCollector serverCollector = new ServerTraceEventCollector(
                nc, config.getServerTracesSubject(), converter, forwarder);

        clientCollector.start();
        serverCollector.start();

        log.info("Exporter service running. Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down exporter service...");
            clientCollector.stop();
            serverCollector.stop();
            forwarder.flush();
            forwarder.close();
            try { nc.close(); } catch (Exception e) { log.warn("Error closing NATS", e); }
            log.info("Shutdown complete.");
        }, "shutdown-hook"));

        Thread.currentThread().join();
    }
}
