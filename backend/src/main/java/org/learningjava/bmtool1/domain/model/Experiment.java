package org.learningjava.bmtool1.domain.model;

import java.time.LocalDate;

public record Experiment(
        Long id,                 // nullable before insert
        LocalDate experimentDate,
        int numSamples,
        int numRagSamples,
        int k,
        String prompt,
        String embeddingModel,
        String llmModel,
        Double metric1Ccc,
        Double metric2TimeMs,
        Double metric3Co2G
) {
}
