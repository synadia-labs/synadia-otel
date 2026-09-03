package io.synadia.otel.exporter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Maps the JSON payload emitted by NATS Server 2.11+ message path tracing (ADR-41).
 *
 * Actual wire format (from nats-server/server/msgtrace.go):
 * {
 *   "server": { "name": "...", "cluster": "...", "ver": "..." },
 *   "request": {
 *     "header": { "traceparent": ["00-xxx-yyy-01"], ... },
 *     "msgsize": 42
 *   },
 *   "hops": 0,
 *   "events": [
 *     { "type": "in", "ts": "...", "kind": 0, "cid": 5, "acc": "$G", "subj": "foo" },
 *     { "type": "eg", "ts": "...", "kind": 0, "cid": 6, "sub": "foo", "hop": "" }
 *   ]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsgTraceEvent {

    @JsonProperty("server")  public ServerInfo server;
    @JsonProperty("request") public MsgTraceRequest request;
    @JsonProperty("hops")    public int hops;
    /** The array of trace records — field name is "events" in the wire format. */
    @JsonProperty("events")  public List<TraceRecord> events;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerInfo {
        @JsonProperty("name")      public String name;
        @JsonProperty("cluster")   public String cluster;
        @JsonProperty("ver")       public String version;
        @JsonProperty("seq")       public long seq;
        @JsonProperty("jetstream") public boolean jetstream;
        @JsonProperty("time")      public String time;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MsgTraceRequest {
        /** Original message headers: key → list of values (same as HTTP header map). */
        @JsonProperty("header")  public Map<String, List<String>> header;
        @JsonProperty("msgsize") public int msgSize;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TraceRecord {
        /** Event type: "in"=Ingress, "eg"=Egress, "sm"=SubjectMapping,
         *              "se"=StreamExport, "si"=ServiceImport, "js"=JetStream */
        @JsonProperty("type") public String type;
        @JsonProperty("ts")   public String timestamp;  // ISO-8601

        // --- Ingress / Egress common ---
        /** Connection kind as int: 0=Client, 1=Router, 2=Gateway, 3=LeafNode, 4=Account, 5=System */
        @JsonProperty("kind")  public int kind;
        @JsonProperty("cid")   public long cid;
        @JsonProperty("name")  public String name;
        @JsonProperty("error") public String error;

        // --- Ingress ---
        @JsonProperty("acc")  public String account;
        @JsonProperty("subj") public String subject;

        // --- Egress ---
        @JsonProperty("hop")   public String hop;   // hop identifier for DAG reconstruction
        @JsonProperty("sub")   public String subscribeSubject;
        @JsonProperty("queue") public String queue;

        // --- JetStream ---
        @JsonProperty("stream")      public String stream;
        @JsonProperty("nointerest")  public boolean noInterest;

        // --- Subject Mapping ---
        @JsonProperty("to") public String mappedTo;
    }

    /** Extracts the first value of a header from the original message headers. */
    public String getHeader(String key) {
        if (request == null || request.header == null) return null;
        // Try exact key first, then case-insensitive fallback
        List<String> values = request.header.get(key);
        if (values == null) {
            for (Map.Entry<String, List<String>> entry : request.header.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    values = entry.getValue();
                    break;
                }
            }
        }
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /** Returns the subject from the Ingress record, or null if not found. */
    public String getIngressSubject() {
        if (events == null) return null;
        for (TraceRecord r : events) {
            if ("in".equals(r.type) && r.subject != null) return r.subject;
        }
        return null;
    }

    /** Returns the account from the Ingress record, or null if not found. */
    public String getIngressAccount() {
        if (events == null) return null;
        for (TraceRecord r : events) {
            if ("in".equals(r.type) && r.account != null) return r.account;
        }
        return null;
    }

    public static String kindName(int kind) {
        return switch (kind) {
            case 0 -> "Client";
            case 1 -> "Router";
            case 2 -> "Gateway";
            case 3 -> "LeafNode";
            case 4 -> "Account";
            case 5 -> "System";
            default -> "Unknown";
        };
    }
}
