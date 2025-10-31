package org.learningjava.bmtool1.infrastructure.adapter.out.ollama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OllamaModelsService {
    private static final Logger log = LoggerFactory.getLogger(OllamaModelsService.class);

    private final RestTemplate rest = new RestTemplate();
    private final String baseUrl;

    public OllamaModelsService(@Value("${OLLAMA_BASE_URL:http://ollama:11434}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<ModelDto> listModels() {
        // GET /api/tags returns installed models
        String url = baseUrl + "/api/tags";
        try {
            Map resp = rest.getForObject(url, Map.class);
            Object data = resp == null ? null : resp.get("models");
            if (!(data instanceof List<?> list)) return List.of();

            List<ModelDto> out = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String name = String.valueOf(m.get("name"));          // e.g. "llama3.1:8b"
                String parameterSize = String.valueOf(orDefault(m, "parameter_size", "N/A"));
                out.add(new ModelDto(name, prettyName(name), "ollama", parameterSize, "N/A"));
            }
            // stable ordering: bigger first, then name
            out.sort(Comparator.comparing((ModelDto md) -> md.parameters).reversed().thenComparing(md -> md.id));
            return out;
        } catch (Exception e) {
            log.warn("Ollama tags fetch failed ({}). Returning empty list.", e.toString());
            return List.of();
        }
    }

    private static Object orDefault(Map<?, ?> map, String key, Object def) {
        Object v = map.get(key);
        return (v != null) ? v : def;
    }


    private static String prettyName(String id) {
        // simple label = id
        return id;
    }

    public record ModelDto(String id, String label, String provider, String parameters, String context) {}
}
