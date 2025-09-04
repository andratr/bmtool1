package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.ChatLLMPort;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.SnippetRepository;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.config.ChatRegistry;
import org.learningjava.bmtool1.domain.model.PlsqlSnippet;
import org.learningjava.bmtool1.domain.model.RetrievalResult;
import org.learningjava.bmtool1.domain.service.prompting.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TranslateSnippetsUseCase {
    private static final Logger log = LoggerFactory.getLogger(TranslateSnippetsUseCase.class);

    private static final double MIN_SCORE_THRESHOLD = 0.60;

    private final SnippetRepository repo;
    private final VectorStorePort vectorStore;
    private final EmbeddingPort embedding;
    private final ChatRegistry chatRegistry;
    private final PromptBuilder prompts;

    public TranslateSnippetsUseCase(SnippetRepository repo,
                                    VectorStorePort vectorStore,
                                    EmbeddingPort embedding,
                                    ChatRegistry chatRegistry,
                                    PromptBuilder prompts) {
        this.repo = repo;
        this.vectorStore = vectorStore;
        this.embedding = embedding;
        this.chatRegistry = chatRegistry;
        this.prompts = prompts;
    }

    /**
     * Translate all pending snippets using the chosen provider/model.
     */
    public void translate(int k, String providerId, String model) {
        ChatLLMPort chat = chatRegistry.get(providerId);
        if (chat == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerId);
        }

        List<PlsqlSnippet> pending = repo.findPending();
        if (pending.isEmpty()) {
            log.warn("No pending snippets found for translation.");
            return;
        }

        for (PlsqlSnippet snippet : pending) {
            log.info("🔹 Starting translation for snippet [eventCode={}, type={}, domain={}]",
                    snippet.eventCode(), snippet.type(), snippet.domain());

            // 1) Embed
            float[] vec = embedding.embed(snippet.content());
            if (vec == null || vec.length == 0) {
                log.warn("Embedding failed for snippet {} (eventCode={})",
                        snippet.type(), snippet.eventCode());
                continue;
            }
            log.debug("📐 Embedding vector built (dim={})", vec.length);

            // 2) Retrieve examples
            List<RetrievalResult> hits = vectorStore.query(snippet.content(), vec, k);
            List<RetrievalResult> filtered = (hits == null) ? List.of()
                    : hits.stream()
                    .filter(r -> r.score() >= MIN_SCORE_THRESHOLD)
                    .toList();
            log.info("📚 Retrieved {} examples, {} passed threshold {}",
                    (hits == null ? 0 : hits.size()),
                    filtered.size(),
                    MIN_SCORE_THRESHOLD);

            // Print retrieved examples for debug
            for (RetrievalResult r : filtered) {
                log.debug("➡ Context hit (score={}):\nPLSQL:\n{}\nJAVA:\n{}",
                        r.score(),
                        r.mapping().plsqlSnippet(),
                        r.mapping().javaSnippet());
            }

            // 3) Build prompt
            String question = "Translate this " + snippet.type() +
                    " PL/SQL block into a Java method for the " + snippet.domain() + " domain.";
            String prompt = filtered.isEmpty()
                    ? "You are a precise assistant for translating PL/SQL into Java.\n\nTask: " + question
                    + "\n\nSnippet:\n" + snippet.content()
                    : prompts.buildRagPrompt(question, filtered)
                    + "\n\nSnippet:\n" + snippet.content();

            log.info("📝 Final prompt for {} ({} chars):\n{}\n--- END PROMPT ---",
                    snippet.eventCode(), prompt.length(), prompt);

            // 4) Call LLM
            String javaCode = chat.chat(prompt, model);
            log.info("🤖 LLM output for {}:\n{}\n--- END OUTPUT ---",
                    snippet.eventCode(), javaCode);

            // 5) Save
            repo.saveTranslated(snippet.withJavaTranslation(javaCode));
            log.info("✅ Saved translation for snippet {} (eventCode={}) using {}/{}",
                    snippet.type(), snippet.eventCode(), providerId, model);
        }
    }
}
