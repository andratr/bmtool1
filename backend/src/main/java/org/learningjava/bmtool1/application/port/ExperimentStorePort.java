package org.learningjava.bmtool1.application.port;

import org.learningjava.bmtool1.domain.model.analytics.Experiment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExperimentStorePort {
    void ensureSchema();

    // Writes (return DB id)
    long upsert(Experiment e);

    List<Long> upsertBatch(List<Experiment> experiments);

    // Reads
    Optional<Experiment> findById(long id);

    List<Experiment> listByDateRange(LocalDate fromInclusive, LocalDate toInclusive);

    List<Experiment> listByModels(String embeddingModel, String llmModel);
}
