// src/main/java/org/learningjava/bmtool1/application/usecase/Orchestrator.java
package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.ChatLLMPort;
import org.learningjava.bmtool1.application.port.ChatLLMPort.ChatResult;
import org.learningjava.bmtool1.application.port.ChatLLMPort.Usage;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.application.port.FrameworkStorePort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.domain.model.analytics.Experiment;
import org.learningjava.bmtool1.domain.model.framework.FrameworkRetrievalResult;
import org.learningjava.bmtool1.domain.model.framework.FrameworkSymbol;
import org.learningjava.bmtool1.domain.model.pairs.BlockMapping;
import org.learningjava.bmtool1.domain.model.pairs.RetrievalResult;
import org.learningjava.bmtool1.domain.model.query.Answer;
import org.learningjava.bmtool1.domain.model.query.Query;
import org.learningjava.bmtool1.domain.service.ChatRegistry;
import org.learningjava.bmtool1.domain.service.co2.CarbonEstimator;
import org.learningjava.bmtool1.domain.service.prompting.PromptBuilder;
import org.learningjava.bmtool1.domain.service.prompting.PromptingTechnique;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private static final double MIN_DOC_SCORE = 0.60;
    private static final double SYNTHETIC_FW_SCORE = 1.0;
    private static final int DOC_PROMPT_LIMIT = Integer.MAX_VALUE;
    private static final int PER_SNIPPET_CHAR_LIMIT = 4000;

    private final EmbeddingPort embedding;
    private final VectorStorePort docStore;
    private final FrameworkStorePort fwStore;
    private final ChatRegistry chatRegistry;
    private final PromptBuilder prompts;
    private final ExperimentStorePort experiments;
    private final CarbonEstimator carbon; // ⬅ injected

    public Orchestrator(EmbeddingPort embedding,
                        VectorStorePort docStore,
                        FrameworkStorePort fwStore,
                        ChatRegistry chatRegistry,
                        PromptBuilder prompts,
                        ExperimentStorePort experiments,
                        CarbonEstimator carbon) {
        this.embedding = embedding;
        this.docStore = docStore;
        this.fwStore = fwStore;
        this.chatRegistry = chatRegistry;
        this.prompts = prompts;
        this.experiments = experiments;
        this.carbon = carbon;
    }

    public Answer askWithFramework(Query q,
                                   int kDocs,
                                   int kFramework,
                                   String providerId,
                                   String llmModel,
                                   String embeddingModel,
                                   List<String> mustHaveTags,
                                   PromptingTechnique technique) {

        ChatLLMPort chat = chatRegistry.get(providerId);
        if (chat == null) throw new IllegalArgumentException("Unknown provider: " + providerId);

        long t0 = System.nanoTime();

        // 1) Embedding
        float[] qVec = embedding.embed(q.question());

        // 2) Retrieve docs
        List<RetrievalResult> docHits = Optional.ofNullable(
                docStore.query(q.question(), qVec, kDocs)
        ).orElseGet(List::of);
        List<RetrievalResult> docFiltered = docHits.stream()
                .filter(r -> r.score() >= MIN_DOC_SCORE)
                .toList();

        // 3) Retrieve framework
        List<FrameworkSymbol> fwRaw = Optional.ofNullable(
                fwStore.retrieve(q.question(), qVec, kFramework,
                        mustHaveTags == null ? List.of() : mustHaveTags)
        ).orElseGet(List::of);
        List<FrameworkRetrievalResult> fwHits = fwRaw.stream()
                .limit(kFramework)
                .map(s -> new FrameworkRetrievalResult(s, SYNTHETIC_FW_SCORE))
                .toList();

        // 4) Build prompt
        String prompt = prompts.build(
                technique, q.question(), docFiltered, fwHits,
                PER_SNIPPET_CHAR_LIMIT, DOC_PROMPT_LIMIT
        );

        if (log.isDebugEnabled()) {
            log.debug("this is the prompt ({} chars) [technique={}]:\n{}\n--- END PROMPT ---",
                    prompt.length(), technique, prompt);
        }

        // 5) Call LLM **with usage**
        ChatResult res = chat.chatWithUsage(prompt, llmModel);
        String llmAnswer = res.text();
        Usage usage = res.usage(); // may be null

        long elapsedMs = Math.max(1L, Math.round((System.nanoTime() - t0) / 1_000_000.0));

        Integer promptTok = usage == null ? null : usage.promptTokens();
        Integer complTok  = usage == null ? null : usage.completionTokens();
        Integer totalTok  = (promptTok == null && complTok == null)
                ? null
                : ( (promptTok == null ? 0 : promptTok) + (complTok == null ? 0 : complTok) );

        double gramsCO2 = carbon.estimateGramsCO2(promptTok, complTok, (long) elapsedMs, providerId, llmModel);

        // 6) Persist experiment with the new shape
        try {
            Experiment exp = new Experiment(
                    null,
                    LocalDate.now(),
                    /* fwHitsCount  */ fwHits.size(),
                    /* docHitsCount */ docFiltered.size(),
                    /* kFw          */ kFramework,
                    /* kDoc         */ kDocs,
                    /* prompt       */ prompt,
                    /* embedding    */ embeddingModel,
                    /* llm          */ llmModel,
                    /* metric1Ccc   */ null,
                    /* metric2TimeMs*/ (double) elapsedMs,
                    /* metric3Co2G  */ gramsCO2,
                    /* metric4Prompt*/ promptTok,
                    /* metric5Compl */ complTok,
                    /* metric6Total */ totalTok,
                    /* technique    */ technique.name()
            );
            long id = experiments.upsert(exp);
            log.debug("Recorded experiment id={} (fwUsed={}, docUsed={}, kFw={}, kDoc={}, tokPrompt={}, tokCompletion={}, co2={}g, ms={})",
                    id, exp.fwHitsCount(), exp.docHitsCount(), exp.kFw(), exp.kDoc(),
                    promptTok, complTok, String.format(Locale.ROOT,"%.2f", gramsCO2), elapsedMs);
        } catch (Exception e) {
            log.warn("Experiment logging failed (non-fatal): {}", e.toString());
        }

        // 7) Final answer (+ sources appendix)
        String sources = buildSourcesAppendix(docFiltered);
        String finalAnswer = llmAnswer + "\n\n---\nSources used (docs/code chunks):\n" + sources;

        return new Answer(finalAnswer, docFiltered, fwHits);
    }

    /* helpers for appendix (unchanged from your version) */
    private String buildSourcesAppendix(List<RetrievalResult> docHits) {
        if (docHits == null || docHits.isEmpty()) return "No documents were used.";
        StringBuilder sb = new StringBuilder(1024);
        for (int i = 0; i < docHits.size(); i++) {
            RetrievalResult r = docHits.get(i);
            sb.append("\n").append(renderDocHeader(i + 1, r)).append(renderDocBody(r));
        }
        return sb.toString();
    }
    private String renderDocHeader(int index, RetrievalResult r) {
        if (r == null || r.mapping() == null) return "[DOC " + index + " | (no mapping)]\n";
        String id = "", name = "";
        if (r.mapping() instanceof BlockMapping bm) {
            id = bm.pairId() == null ? "" : bm.pairId();
            name = bm.pairName() == null ? "" : bm.pairName();
        }
        return "[DOC %d | score=%s%s%s]\n".formatted(
                index,
                String.format(Locale.ROOT, "%.3f", r.score()),
                id.isBlank() ? "" : " | id=" + id,
                name.isBlank() ? "" : " | name=" + name
        );
    }
    private String renderDocBody(RetrievalResult r) {
        if (r == null || !(r.mapping() instanceof BlockMapping bm)) {
            return "(unsupported mapping type; expected BlockMapping)\n";
        }
        StringBuilder sb = new StringBuilder();
        if ((bm.plsqlType() != null && !bm.plsqlType().isBlank())
                || (bm.javaType() != null && !bm.javaType().isBlank())) {
            sb.append("- types: ");
            if (bm.plsqlType() != null && !bm.plsqlType().isBlank()) sb.append("plsql=").append(bm.plsqlType());
            if (bm.plsqlType() != null && !bm.plsqlType().isBlank() && bm.javaType() != null && !bm.javaType().isBlank()) sb.append(", ");
            if (bm.javaType() != null && !bm.javaType().isBlank()) sb.append("java=").append(bm.javaType());
            sb.append("\n");
        }
        if (bm.plsqlSnippet() != null && !bm.plsqlSnippet().isBlank()) {
            sb.append("\n-- PL/SQL\n```\n").append(truncate(bm.plsqlSnippet(), PER_SNIPPET_CHAR_LIMIT)).append("\n```\n");
        } else {
            sb.append("\n-- PL/SQL\n(no plsqlSnippet available)\n");
        }
        if (bm.javaSnippet() != null && !bm.javaSnippet().isBlank()) {
            sb.append("\n-- Java\n```\n").append(truncate(bm.javaSnippet(), PER_SNIPPET_CHAR_LIMIT)).append("\n```\n");
        }
        if (bm.javaHelpers() != null && !bm.javaHelpers().isEmpty()) {
            sb.append("\n- javaHelpers: ").append(String.join(", ", bm.javaHelpers())).append("\n");
        }
        return sb.toString();
    }
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + " …";
    }
}
