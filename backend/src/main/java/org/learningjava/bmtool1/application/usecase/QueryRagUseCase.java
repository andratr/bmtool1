package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.ChatLLMPort;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.domain.service.ChatRegistry;
import org.learningjava.bmtool1.domain.model.Answer;
import org.learningjava.bmtool1.domain.model.Query;
import org.learningjava.bmtool1.domain.model.RetrievalResult;
import org.learningjava.bmtool1.domain.service.prompting.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public QueryRagUseCase(EmbeddingPort embedding,
                           VectorStorePort store,
                           ChatRegistry chatRegistry,
                           PromptBuilder prompts) {
        this.embedding = embedding;
        this.store = store;
        this.chatRegistry = chatRegistry;
        this.prompts = prompts;
    }

    /**
     * Ask a question with RAG
     */
    public Answer ask(Query q, int k, String providerId, String model) {
        // 1) Get provider
        ChatLLMPort chat = chatRegistry.get(providerId);
        if (chat == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerId);
        }

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
                : hits.stream()
                .filter(r -> r.score() >= MIN_SCORE_THRESHOLD)
                .toList();

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
        String llmAnswer = chat.chat(prompt, model);
        return new Answer(llmAnswer, filteredHits);
    }
}
