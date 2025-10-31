package org.learningjava.bmtool1.infrastructure.adapter.out.openrouter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class OpenRouterModelsService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterModelsService.class);

    private final RestTemplate rest = new RestTemplate();
    private final String baseUrl;
    private final String apiKey;
    private final String referer;
    private final String title;

    public OpenRouterModelsService(
            @Value("${openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${OPENROUTER_API_KEY:}") String envKey,
            @Value("${OPENROUTER_API_KEY_FILE:/run/secrets/openrouter_api_key}") String apiKeyFilePath,
            @Value("${openrouter.referer:http://localhost}") String referer,
            @Value("${openrouter.title:bmtool1}") String title
    ) {
        this.baseUrl = baseUrl;
        this.referer = referer;
        this.title = title;

        String key = envKey == null ? "" : envKey.trim();
        if (key.isBlank()) {
            try {
                Path path = Path.of(apiKeyFilePath);
                if (Files.exists(path)) key = Files.readString(path).trim();
            } catch (IOException ignored) { }
        }
        this.apiKey = key;
        log.debug("OpenRouterModelsService init: baseUrl={}, referer={}, title={}", baseUrl, referer, title);
    }

    public List<ModelDto> listFreeModels() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENROUTER_API_KEY missing — returning empty model list");
            return List.of();
        }

        String url = baseUrl + "/models";
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + apiKey);
        h.set("HTTP-Referer", referer);
        h.set("X-Title", title);

        ResponseEntity<Map> resp;
        try {
            resp = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Map.class);
        } catch (Exception e) {
            log.error("Failed to fetch OpenRouter models: {}", e.toString());
            return List.of();
        }

        Object dataObj = resp.getBody() == null ? null : resp.getBody().get("data");
        if (!(dataObj instanceof List<?> list)) return List.of();

        List<ModelDto> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String id = String.valueOf(m.get("id"));
            Map<String, Object> pricing = asMap(m.get("pricing"));
            boolean isFreeTag = id != null && id.endsWith(":free");
            boolean pricedFree = "0".equals(String.valueOf(pricing.get("prompt")))
                    && "0".equals(String.valueOf(pricing.get("completion")));
            if (isFreeTag || pricedFree) {
                String name = String.valueOf(orDefault(m, "name", id));
                out.add(new ModelDto(id, name, extractProvider(id), "-", extractContext(m)));
            }
        }
        // sort for stable UI
        out.sort(Comparator.comparing(a -> a.id));
        return out;
    }

    private static Object orDefault(Map<?, ?> map, String key, Object def) {
        Object v = map.get(key);
        return (v != null) ? v : def;
    }

    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map<?, ?> mm) ? (Map<String, Object>) mm : Map.of();
    }

    private static String extractProvider(String id) {
        // e.g. "deepseek/deepseek-r1:free" -> "deepseek"
        if (id == null) return "";
        int slash = id.indexOf('/');
        return (slash > 0) ? id.substring(0, slash) : "";
    }

    private static String extractContext(Map<?, ?> model) {
        // OpenRouter schema varies; try top_provider.context_length or context_length
        Object topProv = model.get("top_provider");
        if (topProv instanceof Map<?, ?> tp) {
            Object ctx = tp.get("context_length");
            if (ctx != null) return String.valueOf(ctx);
        }
        Object ctx = model.get("context_length");
        return ctx == null ? "N/A" : String.valueOf(ctx);
    }

    // DTO to re-use in the controller response
    public record ModelDto(String id, String label, String provider, String parameters, String context) {}
}
