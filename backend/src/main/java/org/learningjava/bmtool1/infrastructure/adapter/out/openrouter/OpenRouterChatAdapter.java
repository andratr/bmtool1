// src/main/java/org/learningjava/bmtool1/infrastructure/adapter/out/openrouter/OpenRouterChatAdapter.java
package org.learningjava.bmtool1.infrastructure.adapter.out.openrouter;

import org.learningjava.bmtool1.application.port.ChatLLMPort;
import org.learningjava.bmtool1.domain.service.co2.CarbonEstimator;   // ⬅️ add
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OpenRouterChatAdapter implements ChatLLMPort {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterChatAdapter.class);

    private final RestTemplate rest;
    private final String apiKey;
    private final String baseUrl;
    private final String referer;
    private final String title;
    private final CarbonEstimator carbon; // ⬅️ add

    public OpenRouterChatAdapter(
            @Value("${OPENROUTER_API_KEY:}") String envKey,
            @Value("${OPENROUTER_API_KEY_FILE:/run/secrets/openrouter_api_key}") String apiKeyFilePath,
            @Value("${llm.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${llm.openrouter.referer:http://localhost}") String referer,
            @Value("${llm.openrouter.title:bmtool1}") String title,
            @Value("${llm.openrouter.timeout.ms:20000}") int timeoutMs,
            CarbonEstimator carbon // ⬅️ add
    ) {
        this.apiKey = resolveApiKey(envKey, apiKeyFilePath);
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.referer = referer;
        this.title = title;
        this.rest = buildRestTemplate(timeoutMs);
        this.carbon = carbon; // ⬅️ add
        log.debug("OpenRouterChatAdapter init: baseUrl={}, referer={}, title={}", this.baseUrl, this.referer, this.title);
    }

    @Override
    public String provider() { return "openrouter"; }

    @Override
    public String chat(String prompt, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenRouter API key not configured. Set OPENROUTER_API_KEY or mount OPENROUTER_API_KEY_FILE.");
        }
        final String url = baseUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", referer);
        headers.set("X-Title", title);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a precise assistant."),
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        long t0 = System.nanoTime(); // ⬅️ timing start
        try {
            ResponseEntity<Map> response = rest.postForEntity(url, request, Map.class);
            long t1 = System.nanoTime(); // ⬅️ timing end
            long latencyMs = Math.max(1L, Math.round((t1 - t0) / 1_000_000.0));

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("OpenRouter call failed: status=" + response.getStatusCodeValue()
                        + " body=" + String.valueOf(response.getBody()));
            }

            // ---------- usage tokens (if present) ----------
            Integer promptTok = null, completionTok = null;
            Object usage = response.getBody().get("usage");
            if (usage instanceof Map<?,?> u) {
                Object pt = u.get("prompt_tokens");
                Object ct = u.get("completion_tokens");
                if (pt instanceof Number) promptTok = ((Number) pt).intValue();
                if (ct instanceof Number) completionTok = ((Number) ct).intValue();
            }

            // CO2 estimate + log
            double g = carbon.estimateGramsCO2(promptTok, completionTok, latencyMs, provider(), model);
            log.debug("CO2(OpenRouter): model={}, promptTok={}, completionTok={}, latencyMs={}, gramsCO2e={}",
                    model, promptTok, completionTok, latencyMs, String.format("%.2f", g));

            // Parse: choices[0].message.content
            Object choices = response.getBody().get("choices");
            if (choices instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> m) {
                    Object message = m.get("message");
                    if (message instanceof Map<?, ?> mm) {
                        Object content = mm.get("content");
                        if (content != null) return String.valueOf(content);
                    }
                }
            }

            log.warn("OpenRouter response missing choices[0].message.content; returning empty string. Raw body={}", response.getBody());
            return "";
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            String bodyText = safeBody(ex);
            log.error("OpenRouter HTTP {} {} for model='{}'\nResponse body: {}\nHeaders set: Authorization(Bearer ****), HTTP-Referer={}, X-Title={}",
                    ex.getStatusCode().value(), ex.getStatusText(), model, bodyText, referer, title);
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new IllegalStateException("OpenRouter 401 Unauthorized. Check API key, required headers, and model access.", ex);
            }
            throw new IllegalStateException("OpenRouter error: " + ex.getStatusCode().value() + " " + ex.getStatusText(), ex);
        } catch (ResourceAccessException io) {
            log.error("OpenRouter connection error to {}: {}", url, io.toString());
            throw new IllegalStateException("Cannot reach OpenRouter (" + baseUrl + "). Check network / URL / timeouts.", io);
        }
    }
    // src/main/java/org/learningjava/bmtool1/infrastructure/adapter/out/openrouter/OpenRouterChatAdapter.java
    @Override
    public ChatResult chatWithUsage(String prompt, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenRouter API key not configured. Set OPENROUTER_API_KEY or mount OPENROUTER_API_KEY_FILE.");
        }
        final String url = baseUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", referer);
        headers.set("X-Title", title);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a precise assistant."),
                        Map.of("role", "user", "content", prompt)
                )
                // no estimator, no special flags; OpenRouter includes "usage" when the upstream supports it
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = rest.postForEntity(url, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("OpenRouter call failed: status=" + response.getStatusCodeValue()
                        + " body=" + String.valueOf(response.getBody()));
            }

            // content
            String content = "";
            Object choices = response.getBody().get("choices");
            if (choices instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> m) {
                    Object message = m.get("message");
                    if (message instanceof Map<?, ?> mm) {
                        Object c = mm.get("content");
                        if (c != null) content = String.valueOf(c);
                    }
                }
            }

            // usage (real values only; may be null)
            Usage usageObj = null;
            Object usage = response.getBody().get("usage");
            if (usage instanceof Map<?,?> u) {
                Integer pt = u.get("prompt_tokens") instanceof Number ? ((Number) u.get("prompt_tokens")).intValue() : null;
                Integer ct = u.get("completion_tokens") instanceof Number ? ((Number) u.get("completion_tokens")).intValue() : null;
                if (pt != null || ct != null) usageObj = new Usage(pt, ct);
            }

            return new ChatResult(content, usageObj);

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            String bodyText = safeBody(ex);
            log.error("OpenRouter HTTP {} {} for model='{}'\nResponse body: {}\nHeaders set: Authorization(Bearer ****), HTTP-Referer={}, X-Title={}",
                    ex.getStatusCode().value(), ex.getStatusText(), model, bodyText, referer, title);
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new IllegalStateException("OpenRouter 401 Unauthorized. Check API key, required headers, and model access.", ex);
            }
            throw new IllegalStateException("OpenRouter error: " + ex.getStatusCode().value() + " " + ex.getStatusText(), ex);
        } catch (ResourceAccessException io) {
            throw new IllegalStateException("Cannot reach OpenRouter (" + baseUrl + "). Check network / URL / timeouts.", io);
        }
    }



// ---- helpers ----

    private static RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutMs);
        f.setReadTimeout(timeoutMs);
        return new RestTemplate(f);
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String resolveApiKey(String envKey, String filePath) {
        String key = envKey == null ? "" : envKey.trim();
        if (!key.isBlank()) return key;
        try {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    private static String safeBody(HttpStatusCodeException ex) {
        try {
            String s = ex.getResponseBodyAsString();
            return s == null ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }
}
