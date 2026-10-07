package com.bpmforbusiness.laya.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP client for a self-hosted Laya server speaking the Jev-compatible
 * {@code POST /v1/systemone} wire protocol. Request body:
 * <pre>
 *   { "state": "...", "questions": { <qid>: {type, instructions, criteria} } }
 * </pre>
 * Response:
 * <pre>
 *   { "model": "...", "answers": { <qid>: {type, ...typed fields} }, "usage": {...} }
 * </pre>
 * The connection details (base URL + optional bearer key) come from config.
 */
public final class LayaClient {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Duration timeout;

    public LayaClient(String baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8000" : baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    /** POST /v1/systemone; returns the full response as a JsonNode. */
    public JsonNode systemOne(JsonNode state, JsonNode questions) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.set("state", state);
        body.set("questions", questions);

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/v1/systemone"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (apiKey != null && !apiKey.isBlank()) {
            b.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Laya /v1/systemone HTTP " + resp.statusCode()
                    + ": " + truncate(resp.body()));
        }
        return mapper.readTree(resp.body());
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }
}