package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.ChatLLMPort;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.domain.model.Answer;
import org.learningjava.bmtool1.domain.model.Experiment;
import org.learningjava.bmtool1.domain.model.Query;
import org.learningjava.bmtool1.domain.model.RetrievalResult;
import org.learningjava.bmtool1.domain.service.ChatRegistry;
import org.learningjava.bmtool1.domain.service.prompting.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class QueryRagUseCase {
    private static final Logger log = LoggerFactory.getLogger(QueryRagUseCase.class);

    // threshold: only keep docs with score >= 0.60
    private static final double MIN_SCORE_THRESHOLD = 0.60;

    private final EmbeddingPort embedding;
    private final VectorStorePort store;
    private final ChatRegistry chatRegistry;
    private final PromptBuilder prompts;
    private final ExperimentStorePort experiments; // NEW

    public QueryRagUseCase(EmbeddingPort embedding,
                           VectorStorePort store,
                           ChatRegistry chatRegistry,
                           PromptBuilder prompts,
                           ExperimentStorePort experiments) { // NEW
        this.embedding = embedding;
        this.store = store;
        this.chatRegistry = chatRegistry;
        this.prompts = prompts;
        this.experiments = experiments; // NEW
    }

    /**
     * Ask a question with RAG and record an Experiment row.
     *
     * @param q              the query
     * @param k              top-k retrieval
     * @param providerId     LLM provider id (for ChatRegistry)
     * @param llmModel       LLM model name to persist
     * @param embeddingModel Embedding model name to persist
     */
    public Answer ask(Query q, int k, String providerId, String llmModel, String embeddingModel) {
        // 1) Get provider
        ChatLLMPort chat = chatRegistry.get(providerId);
        if (chat == null) throw new IllegalArgumentException("Unknown provider: " + providerId);

        long t0 = System.nanoTime();

        // 2) Embed question
        float[] qVec = embedding.embed(q.question());
        if (qVec == null) {
            log.warn("Embedding returned null for question '{}'", q.question());
        } else if (qVec.length == 0) {
            log.warn("Embedding returned empty vector for question '{}'", q.question());
        } else if (log.isDebugEnabled()) {
            log.debug("Embedding built for question: '{}' (dim={}) sample=[{}]",
                    q.question(), qVec.length,
                    qVec.length > 3 ? qVec[0] + ", " + qVec[1] + ", " + qVec[2] : qVec[0]);
        }

        // 3) Retrieve context
        List<RetrievalResult> hits = store.query(q.question(), qVec, k);

        List<RetrievalResult> filteredHits = (hits == null) ? List.of()
                : hits.stream().filter(r -> r.score() >= MIN_SCORE_THRESHOLD).toList();

        if (hits == null) {
            log.error("Vector store returned null result list for '{}'", q.question());
        } else if (hits.isEmpty()) {
            log.warn("Vector store returned no context for '{}' with k={}", q.question(), k);
        }

        if (filteredHits.isEmpty()) {
            log.warn("No hits above threshold {} for '{}'. Falling back to general knowledge.",
                    MIN_SCORE_THRESHOLD, q.question());
        }

        // 4) Build prompt
        String prompt = filteredHits.isEmpty()
                ? "You are a precise assistant for software code questions.\n\nQuestion: " + q.question()
                : prompts.buildRagPrompt(q.question(), filteredHits);

        if (log.isDebugEnabled()) {
            log.debug("Final RAG prompt ({} chars):\n{}\n----- END PROMPT -----",
                    prompt.length(), prompt);
        }

        // 5) Call chosen LLM provider
        String llmAnswer = chat.chat(prompt, llmModel);

        long elapsedMs = Math.round((System.nanoTime() - t0) / 1_000_000.0);

        // 6) Record experiment (best-effort; don't break the user flow)
        try {
            int numSamples = (hits == null) ? 0 : hits.size();
            int numRagSamples = filteredHits.size();

            Experiment exp = new Experiment(
                    null,                   // id (DB-generated)
                    LocalDate.now(),        // experiment_date
                    numSamples,
                    numRagSamples,
                    k,
                    prompt,
                    embeddingModel,
                    llmModel,
                    null,                   // metric1_ccc (unknown here)
                    (double) elapsedMs,     // metric2_time_ms
                    null                    // metric3_co2_g (unknown here)
            );
            long id = experiments.upsert(exp);
            if (log.isDebugEnabled()) log.debug("Recorded experiment id={}", id);
        } catch (Exception e) {
            log.warn("Failed to record experiment metrics (non-fatal): {}", e.toString());
        }

        return new Answer(llmAnswer, filteredHits);
    }
}
