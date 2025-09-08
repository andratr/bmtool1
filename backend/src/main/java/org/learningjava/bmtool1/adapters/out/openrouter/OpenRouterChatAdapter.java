package org.learningjava.bmtool1.adapters.out.openrouter;

import org.learningjava.bmtool1.application.port.ChatLLMPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


@Component
public class OpenRouterChatAdapter implements ChatLLMPort {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiKey;

    public OpenRouterChatAdapter(
            @Value("${OPENROUTER_API_KEY:}") String envKey,
            @Value("${OPENROUTER_API_KEY_FILE:/run/secrets/openrouter_api_key}") String apiKeyFilePath
    ) {
        String key = envKey == null ? "" : envKey.trim();
        if (key.isBlank()) {
            try {
                Path path = Path.of(apiKeyFilePath);
                if (Files.exists(path)) {
                    key = Files.readString(path).trim();
                }
            } catch (IOException ignored) {
                // If the file can't be read, we'll proceed without a key and fail lazily on use
            }
        }
        this.apiKey = key;
    }

    @Override
    public String id() {
        return "openrouter";
    }

    @Override
    public String chat(String prompt, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenRouter API key not configured. Set OPENROUTER_API_KEY or mount OPENROUTER_API_KEY_FILE.");
        }
        String url = "https://openrouter.ai/api/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("HTTP-Referer", "http://localhost");
        headers.set("X-Title", "bmtool1");

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a precise assistant."),
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        Map choice = (Map) ((List) response.getBody().get("choices")).get(0);
        Map message = (Map) choice.get("message");

        return (String) message.get("content");
    }
}
