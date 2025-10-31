// src/main/java/org/learningjava/bmtool1/infrastructure/adapter/out/ollama/OllamaChatAdapter.java
package org.learningjava.bmtool1.infrastructure.adapter.out.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.learningjava.bmtool1.application.port.ChatLLMPort;
import org.learningjava.bmtool1.application.port.ChatLLMPort.ChatResult;
import org.learningjava.bmtool1.application.port.ChatLLMPort.Usage;
import org.learningjava.bmtool1.domain.service.co2.CarbonEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OllamaChatAdapter implements ChatLLMPort {

    private final OkHttpClient http;
    private final ObjectMapper om = new ObjectMapper();
    private final String baseUrl;
    private final CarbonEstimator carbon; // can be null

    private static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(600))   // big payloads/models
                .readTimeout(Duration.ofMinutes(3))      // chat can take a while
                .retryOnConnectionFailure(true)
                .build();
    }

    public OllamaChatAdapter(@Value("${bmtool1.ollama.url}") String baseUrl,
                             CarbonEstimator carbon) {
        this.http = defaultClient();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.carbon = carbon;
    }

    @Override
    public String provider() { return "ollama"; }

    /** Keep the original chat (no tokens). Uses /api/chat. */
    @Override
    public String chat(String prompt, String model) {
        try {
            return doChat(model, prompt);
        } catch (IOException e) {
            throw new RuntimeException("Ollama chat failed: " + e.getMessage(), e);
        }
    }

    /**
     * Return REAL usage when available by calling /api/generate
     * which exposes: prompt_eval_count (prompt tokens), eval_count (completion tokens).
     */
    @Override
    public ChatResult chatWithUsage(String prompt, String model) {
        try {
            return doGenerateWithUsage(model, prompt);
        } catch (IOException e) {
            throw new RuntimeException("Ollama chatWithUsage failed: " + e.getMessage(), e);
        }
    }

    /** Single-turn chat via /api/chat; returns only text. */
    private String doChat(String modelName, String prompt) throws IOException {
        var body = Map.of(
                "model", modelName,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", false,
                "options", Map.of("num_ctx", 512, "temperature", 0)
        );

        var req = new Request.Builder()
                .url(baseUrl + "/api/chat")
                .header("Accept", "application/json")
                .post(RequestBody.create(om.writeValueAsBytes(body), MediaType.parse("application/json")))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String bodyStr = resp.body() != null ? resp.body().string() : "";
                throw new IOException("HTTP " + resp.code() + " - " + resp.message() + " | body=" + bodyStr);
            }
            var raw = resp.body() != null ? resp.body().string() : "{}";
            JsonNode json = om.readTree(raw);

            // text can be in message.content or response depending on build
            String text = json.path("message").path("content").asText(null);
            if (text == null) text = json.path("response").asText("");
            return text;
        }
    }

    /** Single-turn generate via /api/generate; returns text + REAL usage tokens. */
    private ChatResult doGenerateWithUsage(String modelName, String prompt) throws IOException {
        var body = Map.of(
                "model", modelName,
                "prompt", prompt,
                "stream", false,
                "options", Map.of("num_ctx", 512, "temperature", 0)
        );

        var req = new Request.Builder()
                .url(baseUrl + "/api/generate")
                .header("Accept", "application/json")
                .post(RequestBody.create(om.writeValueAsBytes(body), MediaType.parse("application/json")))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String bodyStr = resp.body() != null ? resp.body().string() : "";
                throw new IOException("HTTP " + resp.code() + " - " + resp.message() + " | body=" + bodyStr);
            }
            var raw = resp.body() != null ? resp.body().string() : "{}";
            JsonNode json = om.readTree(raw);

            String text = json.path("response").asText("");

            Integer promptTok = json.has("prompt_eval_count") && json.get("prompt_eval_count").canConvertToInt()
                    ? json.get("prompt_eval_count").asInt() : null;
            Integer completionTok = json.has("eval_count") && json.get("eval_count").canConvertToInt()
                    ? json.get("eval_count").asInt() : null;

            // optional CO2 logging (no heuristics for tokens; this only logs if estimator is present)
            if (carbon != null && json.has("total_duration")) {
                long latencyMs = Math.round(json.get("total_duration").asDouble() / 1_000_000.0);
                double g = carbon.estimateGramsCO2(promptTok, completionTok, latencyMs, provider(), modelName);
                org.slf4j.LoggerFactory.getLogger(OllamaChatAdapter.class)
                        .debug("CO2(Ollama): model={}, promptTok={}, completionTok={}, latencyMs={}, gramsCO2e={}",
                                modelName, promptTok, completionTok, latencyMs, String.format("%.2f", g));
            }

            Usage usage = (promptTok != null || completionTok != null) ? new Usage(promptTok, completionTok) : null;
            return new ChatResult(text, usage);
        }
    }

    // ---- (optional) helpers kept from your original file ----

    private void pullModel(String name) throws IOException {
        var body = Map.of("name", name, "stream", false);
        var req = new Request.Builder()
                .url(baseUrl + "/api/pull")
                .post(RequestBody.create(om.writeValueAsBytes(body), MediaType.parse("application/json")))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String b = resp.body() != null ? resp.body().string() : "";
                throw new IOException("pull failed: HTTP " + resp.code() + " | body=" + b);
            }
        }
    }

    private boolean isModelNotFound(IOException e) {
        String m = e.getMessage();
        if (m == null) return false;
        String s = m.toLowerCase();
        return s.contains("404") && s.contains("not found");
    }

    private String stripTag(String name) {
        int idx = name.indexOf(':');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private String wrap(String prefix, Exception e) {
        String msg = e.getMessage();
        if (msg == null) msg = e.toString();
        msg = msg.replaceAll("\\s+", " ").trim();
        return prefix + ": " + msg;
    }
}
