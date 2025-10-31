// src/main/java/org/learningjava/bmtool1/application/usecase/RecordExperimentUseCase.java
package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.domain.model.analytics.Experiment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class RecordExperimentUseCase {
    private final ExperimentStorePort experiments;

    public RecordExperimentUseCase(ExperimentStorePort experiments) {
        this.experiments = experiments;
    }

    /**
     * Preferred: record using the NEW Experiment signature.
     *
     * @param day                 experiment date (usually LocalDate.now())
     * @param fwHitsCount         number of framework symbols kept
     * @param docHitsCount        number of document/code chunks kept
     * @param kFw                 requested top-k for framework retrieval
     * @param kDoc                requested top-k for document retrieval
     * @param prompt              full prompt text (for auditing)
     * @param embeddingModel      embedding model (e.g., "nomic-embed-text:latest")
     * @param llmModel            LLM model id (e.g., "llama3.1:8b" or "openai/gpt-4o-mini")
     * @param ccc                 optional quality metric (nullable)
     * @param timeMs              end-to-end latency in ms (nullable)
     * @param co2g                estimated CO₂ emissions in grams (nullable)
     * @param promptTok           prompt token count reported by provider (nullable)
     * @param completionTok       completion token count reported by provider (nullable)
     * @param promptingTechnique  label of technique used (e.g., "RAG_STANDARD")
     * @return                    persisted id
     */
    public long record(LocalDate day,
                       int fwHitsCount,
                       int docHitsCount,
                       int kFw,
                       int kDoc,
                       String prompt,
                       String embeddingModel,
                       String llmModel,
                       Double ccc,
                       Double timeMs,
                       Double co2g,
                       Integer promptTok,
                       Integer completionTok,
                       String promptingTechnique) {

        Integer totalTok = null;
        if (promptTok != null || completionTok != null) {
            totalTok = (promptTok == null ? 0 : promptTok) + (completionTok == null ? 0 : completionTok);
        }

        Experiment e = new Experiment(
                null,                     // id (DB autoincrement)
                day == null ? LocalDate.now() : day,
                fwHitsCount,
                docHitsCount,
                kFw,
                kDoc,
                prompt,
                embeddingModel,
                llmModel,
                ccc,
                timeMs,
                co2g,
                promptTok,
                completionTok,
                totalTok,
                promptingTechnique
        );
        return experiments.upsert(e);
    }

    /**
     * Back-compat shim for legacy callers that only knew:
     *   numSamples, numRagSamples, k
     *
     * We approximate the split:
     *   fwHitsCount  ~= max(0, numSamples - numRagSamples)
     *   docHitsCount = numRagSamples
     *   kFw = kDoc = k
     * Tokens and technique are unknown here (null).
     */
    @Deprecated
    public long record(LocalDate day,
                       int numSamples,
                       int numRagSamples,
                       int k,
                       String prompt,
                       String embeddingModel,
                       String llmModel,
                       Double ccc,
                       Double timeMs,
                       Double co2g) {

        int docHitsCount = Math.max(0, numRagSamples);
        int fwHitsCount  = Math.max(0, numSamples - numRagSamples);

        return record(
                day,
                fwHitsCount,
                docHitsCount,
                k,              // kFw
                k,              // kDoc
                prompt,
                embeddingModel,
                llmModel,
                ccc,
                timeMs,
                co2g,
                null,           // promptTok
                null,           // completionTok
                null            // promptingTechnique
        );
    }
}
