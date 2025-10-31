// src/main/java/org/learningjava/bmtool1/infrastructure/adapter/out/ollama/OllamaEmbeddingAdapter.java
package org.learningjava.bmtool1.infrastructure.adapter.out.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.domain.service.co2.CarbonEstimator;   // ⬅️ add
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingAdapter.class);

    private static final MediaType JSON = MediaType.parse("application/json");
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper om = new ObjectMapper();
    private final String baseUrl;
    private final String model;
    private final CarbonEstimator carbon; // ⬅️ add

    public OllamaEmbeddingAdapter(String baseUrl, String model) {
        this(baseUrl, model, null);
    }

    // convenience ctor to inject CarbonEstimator where you wire this bean
    public OllamaEmbeddingAdapter(String baseUrl, String model, CarbonEstimator carbon) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.carbon = carbon;
    }

    private static String preview(String text) {
        return text.replace("\n", " ").substring(0, Math.min(40, text.length()));
    }

    @Override public float[] embed(String text) { return embedOne(text); }

    @Override public List<float[]> embedBatch(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) out.add(embedOne(t));
        return out;
    }

    private float[] embedOne(String text) {
        long t0 = System.nanoTime(); // ⬅️ timing start
        try {
            ObjectNode body = om.createObjectNode();
            body.put("model", model);
            body.put("prompt", text); // Ollama expects "prompt"

            Request req = new Request.Builder()
                    .url(baseUrl + "/api/embeddings")
                    .post(RequestBody.create(om.writeValueAsBytes(body), JSON))
                    .build();

            try (Response resp = http.newCall(req).execute()) {
                long t1 = System.nanoTime(); // ⬅️ timing end
                long latencyMs = Math.max(1L, Math.round((t1 - t0) / 1_000_000.0));

                if (!resp.isSuccessful()) {
                    log.warn("Ollama embed failed: HTTP {} {}", resp.code(), resp.message());
                    throw new IOException("Ollama embed failed: " + resp);
                }
                String s = resp.body() != null ? resp.body().string() : "{}";
                if (log.isDebugEnabled()) log.debug("Ollama raw embedding response: {}", s);

                JsonNode json = om.readTree(s);
                float[] v;

                if (json.has("embedding")) {
                    v = toFloatArray(json.get("embedding"));
                } else if (json.has("embeddings") && json.get("embeddings").isArray() && json.get("embeddings").size() > 0) {
                    JsonNode first = json.get("embeddings").get(0);
                    v = first.isArray()
                            ? toFloatArray(first)
                            : (first.has("embedding") ? toFloatArray(first.get("embedding")) : new float[0]);
                } else {
                    log.warn("Unexpected embeddings payload from Ollama: {}", s);
                    throw new IOException("Unexpected embeddings payload from Ollama");
                }

                log.debug("Embedding dim={} for text preview='{}...'", v.length, preview(text));

                // CO2 (no tokens -> time-based). Use provider "ollama" and embedding model id.
                if (carbon != null) {
                    double g = carbon.estimateGramsCO2(null, null, latencyMs, "ollama", model);
                    log.debug("CO2(Embed/Ollama): model={}, latencyMs={}, gramsCO2e={}",
                            model, latencyMs, String.format("%.2f", g));
                }

                return v;
            }
        } catch (IOException e) {
            log.error("Embedding failed for model '{}' at {}: {}", model, baseUrl, e.getMessage());
            throw new RuntimeException("Embedding failed for model '" + model + "' at " + baseUrl +
                    ". Check model is pulled and API reachable.", e);
        }
    }

    private float[] toFloatArray(JsonNode arr) throws IOException {
        if (arr == null || !arr.isArray()) throw new IOException("Expected numeric array, got: " + arr);
        float[] v = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) v[i] = (float) arr.get(i).asDouble();
        return v;
    }
}
