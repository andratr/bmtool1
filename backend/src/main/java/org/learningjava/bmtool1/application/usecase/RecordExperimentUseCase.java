package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.domain.model.Experiment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class RecordExperimentUseCase {
    private final ExperimentStorePort experiments;

    public RecordExperimentUseCase(ExperimentStorePort experiments) {
        this.experiments = experiments;
    }

    public long record(LocalDate day, int numSamples, int numRagSamples, int k,
                       String prompt, String embeddingModel, String llmModel,
                       Double ccc, Double timeMs, Double co2g) {
        Experiment e = new Experiment(null, day, numSamples, numRagSamples, k,
                prompt, embeddingModel, llmModel, ccc, timeMs, co2g);
        return experiments.upsert(e);
    }
}
