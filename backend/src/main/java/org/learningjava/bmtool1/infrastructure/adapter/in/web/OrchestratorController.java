package org.learningjava.bmtool1.infrastructure.adapter.in.web;

import org.learningjava.bmtool1.application.usecase.Orchestrator;
import org.learningjava.bmtool1.domain.model.query.Answer;
import org.learningjava.bmtool1.domain.model.query.Query;
import org.learningjava.bmtool1.domain.service.prompting.PromptingTechnique;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/orchestrator")
public class OrchestratorController {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorController.class);

    private final Orchestrator orchestrator;

    public OrchestratorController(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/ask")
    public Answer ask(
            @RequestParam("q") String question,
            @RequestParam(value = "kDocs", defaultValue = "6") int kDocs,
            @RequestParam(value = "kFramework", defaultValue = "6") int kFramework,
            @RequestParam("provider") String providerId,
            @RequestParam("llmModel") String llmModel,
            @RequestParam("embeddingModel") String embeddingModel,
            @RequestParam(value = "tags", required = false) List<String> tagsParam,
            @RequestParam(value = "prompting", defaultValue = "RAG_STANDARD") String prompting
    ) {
        List<String> tags = normalizeTags(tagsParam);

        PromptingTechnique technique;
        try {
            technique = PromptingTechnique.valueOf(prompting.toUpperCase(Locale.ROOT).trim());
        } catch (Exception ex) {
            technique = PromptingTechnique.RAG_STANDARD; // safe default
        }

        if (log.isDebugEnabled()) {
            log.debug("ask: q='{}', kDocs={}, kFramework={}, provider={}, llmModel={}, embeddingModel={}, tags={}, prompting={}",
                    question, kDocs, kFramework, providerId, llmModel, embeddingModel, tags, technique);
        }

        return orchestrator.askWithFramework(
                new Query(question),
                kDocs,
                kFramework,
                providerId,
                llmModel,
                embeddingModel,
                tags,
                technique
        );
    }

    /* -------- helpers -------- */

    /** Accept both repeated &tags=x&tags=y and CSV: &tags=x,y */
    private static List<String> normalizeTags(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        return raw.stream()
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }
}
