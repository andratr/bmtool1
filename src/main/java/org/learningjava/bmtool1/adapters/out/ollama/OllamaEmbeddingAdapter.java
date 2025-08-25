package org.learningjava.bmtool1.adapters.out.ollama;

import okhttp3.*;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingAdapter.class);

    private static final MediaType JSON = MediaType.parse("application/json");
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper om = new ObjectMapper();
    private final String baseUrl;
    private final String model;

    public OllamaEmbeddingAdapter(String baseUrl, String model) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        return embedOne(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) out.add(embedOne(t));
        return out;
    }

    private float[] embedOne(String text) {
        try {
            ObjectNode body = om.createObjectNode();
            body.put("model", model);
            body.put("prompt", text); // PROMPT! NOT INPUT

            Request req = new Request.Builder()
                    .url(baseUrl + "/api/embeddings")
                    .post(RequestBody.create(om.writeValueAsBytes(body), JSON))
                    .build();

            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    log.warn("Ollama embed failed: HTTP {} {}", resp.code(), resp.message());
                    throw new IOException("Ollama embed failed: " + resp);
                }
                String s = resp.body() != null ? resp.body().string() : "{}";
                if (log.isDebugEnabled()) {
                    log.debug("Ollama raw embedding response: {}", s);
                }
                JsonNode json = om.readTree(s);

                if (json.has("embedding")) {
                    float[] v = toFloatArray(json.get("embedding"));
                    log.debug("Embedding dim={} for text preview='{}...'", v.length, preview(text));
                    return v;
                }
                if (json.has("embeddings") && json.get("embeddings").isArray() && json.get("embeddings").size() > 0) {
                    JsonNode first = json.get("embeddings").get(0);
                    float[] v = first.isArray()
                            ? toFloatArray(first)
                            : (first.has("embedding") ? toFloatArray(first.get("embedding")) : new float[0]);
                    log.debug("Embedding dim={} for text preview='{}...'", v.length, preview(text));
                    return v;
                }

                log.warn("Unexpected embeddings payload from Ollama: {}", s);
                throw new IOException("Unexpected embeddings payload from Ollama");
            }
        } catch (IOException e) {
            log.error("Embedding failed for model '{}' at {}: {}", model, baseUrl, e.getMessage());
            throw new RuntimeException("Embedding failed for model '" + model + "' at " + baseUrl +
                    ". Check model is pulled and API reachable.", e);
        }
    }

    private static String preview(String text) {
        return text.replace("\n", " ").substring(0, Math.min(40, text.length()));
    }

    private float[] toFloatArray(JsonNode arr) throws IOException {
        if (arr == null || !arr.isArray()) {
            throw new IOException("Expected numeric array, got: " + arr);
        }
        float[] v = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            v[i] = (float) arr.get(i).asDouble();
        }
        return v;
    }
}
