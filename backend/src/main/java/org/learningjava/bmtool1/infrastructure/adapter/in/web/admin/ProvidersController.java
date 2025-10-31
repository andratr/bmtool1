package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import org.learningjava.bmtool1.infrastructure.adapter.out.openrouter.OpenRouterModelsService;
import org.learningjava.bmtool1.infrastructure.adapter.out.ollama.OllamaModelsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/")
public class ProvidersController {

    private final OpenRouterModelsService openrouter;
    private final OllamaModelsService ollama;

    public ProvidersController(OpenRouterModelsService openrouter,
                               OllamaModelsService ollama) {
        this.openrouter = openrouter;
        this.ollama = ollama;
    }

    @GetMapping(value = "/providers", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ProviderDto> listProviders() {
        List<ProviderDto> providers = new ArrayList<>();

        // Ollama (local)
        List<ModelDto> ollamaModels = map(ollama.listModels());
        if (!ollamaModels.isEmpty()) {
            providers.add(new ProviderDto("ollama", "Ollama (local)", ollamaModels));
        } else {
            // Optionally add a couple of suggested tags if none installed (comment out if not desired)
            providers.add(new ProviderDto("ollama", "Ollama (local)", List.of(
                    new ModelDto("llama3.1:8b", "Llama3.1:8b", "ollama", "8B", "N/A")
            )));
        }

        // OpenRouter (cloud, free)
        List<ModelDto> orModels = map(openrouter.listFreeModels());
        providers.add(new ProviderDto("openrouter", "OpenRouter (cloud)", orModels));

        return providers;
    }

    // --- tiny mapping helpers to unify DTOs ---
    private static List<ModelDto> map(List<? extends Record> src) {
        List<ModelDto> list = new ArrayList<>();
        for (Record r : src) {
            if (r instanceof OpenRouterModelsService.ModelDto m) {
                list.add(new ModelDto(m.id(), m.label(), m.provider(), m.parameters(), m.context()));
            } else if (r instanceof OllamaModelsService.ModelDto m) {
                list.add(new ModelDto(m.id(), m.label(), m.provider(), m.parameters(), m.context()));
            }
        }
        return list;
    }

    public record ProviderDto(String id, String label, List<ModelDto> models) {}
    public record ModelDto(String id, String label, String provider, String parameters, String context) {}
}
