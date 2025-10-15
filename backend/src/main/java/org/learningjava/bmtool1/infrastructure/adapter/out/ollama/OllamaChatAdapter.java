package org.learningjava.bmtool1.infrastructure.adapter.out.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.learningjava.bmtool1.application.port.ChatLLMPort;
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

    public OllamaChatAdapter(@Value("${bmtool1.ollama.url}") String baseUrl) {
        this.http = defaultClient();  // always use the default client here //todo
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(600))
                .readTimeout(Duration.ofMinutes(3))
                .retryOnConnectionFailure(true)
                .build();
    }


    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public String chat(String prompt, String model) {
        try {
            return doChat(model, prompt);
        } catch (IOException e) {
            throw new RuntimeException("Ollama chat failed: " + e.getMessage(), e);
        }
    }


    private String doChat(String modelName, String prompt) throws IOException {
        var body = Map.of(
                "model", modelName,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", false,
                "options", Map.of(
                        "num_ctx", 512,
                        "temperature", 0
                )
        );
        var req = new Request.Builder()
                .url(baseUrl + "/api/chat")
                .header("Accept", "application/json")
                .post(RequestBody.create(om.writeValueAsBytes(body), MediaType.parse("application/json")))
                .build();

        try (var resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String bodyStr = resp.body() != null ? resp.body().string() : "";
                throw new IOException("HTTP " + resp.code() + " - " + resp.message() + " | body=" + bodyStr);
            }
            var raw = resp.body() != null ? resp.body().string() : "{}";
            var json = om.readTree(raw);
            var text = json.path("message").path("content").asText(null);
            if (text == null) text = json.path("response").asText("");
            return text;
        }
    }

    private void pullModel(String name) throws IOException {
        var body = Map.of("name", name, "stream", false);
        var req = new Request.Builder()
                .url(baseUrl + "/api/pull")
                .post(RequestBody.create(om.writeValueAsBytes(body), MediaType.parse("application/json")))
                .build();
        try (var resp = http.newCall(req).execute()) {
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
