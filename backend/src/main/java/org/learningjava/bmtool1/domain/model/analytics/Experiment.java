package org.learningjava.bmtool1.domain.model.analytics;

import java.time.LocalDate;

public record Experiment(
        Long id,
        LocalDate experimentDate,
        int fwHitsCount,
        int docHitsCount,
        int kFw,
        int kDoc,
        String prompt,
        String embeddingModel,
        String llmModel,
        Double metric1Ccc,
        Double metric2TimeMs,
        Double metric3Co2G,
        Integer metric4PromptTok,
        Integer metric5CompletionTok,
        Integer metric6TotalTok,
        String promptingTechnique
) { }
