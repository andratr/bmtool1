package org.learningjava.bmtool1.domain.service.prompting;

import org.learningjava.bmtool1.domain.model.framework.FrameworkRetrievalResult;
import org.learningjava.bmtool1.domain.model.framework.FrameworkSymbol;
import org.learningjava.bmtool1.domain.model.pairs.BlockMapping;
import org.learningjava.bmtool1.domain.model.pairs.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DefaultPromptBuilder implements PromptBuilder {

    @Override
    public String build(PromptingTechnique tech,
                        String question,
                        List<RetrievalResult> docHits,
                        List<FrameworkRetrievalResult> fwHits,
                        int perSnippetLimit,
                        int docPromptLimit) {

        return switch (tech) {
            case ZERO_SHOT -> """
                You are a precise assistant for software code questions.
                Answer the user's question directly and concisely.

                === Task ===
                Question: %s
                """.formatted(question);

            case RAG_STANDARD -> buildComposite(question, docHits, fwHits, perSnippetLimit, docPromptLimit, false);

            case FRAMEWORK_FIRST -> buildComposite(question, docHits, fwHits, perSnippetLimit, docPromptLimit, true);

            case FEW_SHOT -> """
                You are a precise assistant for software code questions.

                === Examples ===
                Q: How do I map a PL/SQL cursor to a Java DTO?
                A: Describe the cursor columns, then show a Java record/class with matching fields. Provide a minimal mapping snippet.

                Q: How can I paginate PL/SQL results in Java?
                A: Use rownum/offset in PL/SQL or db-specific pagination; show a Java method calling it and mapping results.

                === Task ===
                Question: %s
                Provide a clear, step-by-step solution with minimal working snippets.
                """.formatted(question);

            case JSON_STRUCTURED -> """
                You are a precise assistant for software code questions.
                Respond ONLY with a single-line JSON object, no prose.
                Required fields: {"answer": string, "key_points": [string], "references": [string]}

                "question": "%s"
                """.formatted(escapeJson(question));

            case CRITIQUE_AND_REVISE -> """
                You are a precise assistant for software code questions.

                Step 1 — Draft:
                Produce a concise solution.

                Step 2 — Critique:
                List 2-3 risks or mistakes.

                Step 3 — Revised Answer:
                Provide the improved final answer, clearly labeled.

                === Task ===
                Question: %s
                """.formatted(question);
        };
    }

    /* ---------- RAG-style composite templates ---------- */

    private String buildComposite(String question, List<RetrievalResult> docHits,
                                  List<FrameworkRetrievalResult> fwHits,
                                  int perSnippetLimit, int docPromptLimit,
                                  boolean frameworkFirst) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("You are a precise assistant for software code questions.\n");

        if (frameworkFirst) renderFramework(sb, fwHits);
        renderDocs(sb, docHits, perSnippetLimit, docPromptLimit);
        if (!frameworkFirst) renderFramework(sb, fwHits);

        sb.append("\n=== Task ===\n")
                .append("Question: ").append(question).append("\n")
                .append("When you leverage a framework API, cite it like Class#method.");
        return sb.toString();
    }

    private void renderFramework(StringBuilder sb, List<FrameworkRetrievalResult> fwHits) {
        if (fwHits == null || fwHits.isEmpty()) return;
        sb.append("\n=== Framework API Hints (most relevant first) ===\n");
        Map<String, List<FrameworkRetrievalResult>> byClass = fwHits.stream()
                .collect(Collectors.groupingBy(fr -> fr.symbol().className(),
                        LinkedHashMap::new, Collectors.toList()));
        int classesShown = 0;
        for (var e : byClass.entrySet()) {
            if (classesShown++ >= 4) break;
            sb.append("\n# ").append(e.getKey()).append("\n");
            int perClass = 0;
            for (FrameworkRetrievalResult fr : e.getValue()) {
                if (perClass++ >= 5) break;
                FrameworkSymbol s = fr.symbol();
                sb.append("- ").append(s.symbol())
                        .append("  (kind=").append(s.kind()).append(")\n")
                        .append("  signature: ").append(s.methodSignature()).append("\n")
                        .append("  snippet:   ").append(s.snippet()).append("\n");
            }
        }
        sb.append("\nUse these APIs exactly when relevant.\n");
    }

    private void renderDocs(StringBuilder sb, List<RetrievalResult> docs, int perSnippetLimit, int docPromptLimit) {
        if (docs == null || docs.isEmpty()) return;
        sb.append("\n=== Relevant Docs / Code Chunks ===\n");
        int limit = Math.min(docPromptLimit, docs.size());
        for (int i = 0; i < limit; i++) {
            var r = docs.get(i);
            sb.append("\n").append(renderDocHeader(i + 1, r))
                    .append(renderDocBody(r, perSnippetLimit));
        }
        sb.append("\nUse these references for facts; do not invent details.\n");
    }

    private String renderDocHeader(int index, RetrievalResult r) {
        String id = "", name = "";
        if (r != null && r.mapping() instanceof BlockMapping bm) {
            id = bm.pairId() == null ? "" : bm.pairId();
            name = bm.pairName() == null ? "" : bm.pairName();
        }
        double score = r == null ? 0.0 : r.score();
        return "[DOC %d | score=%s%s%s]\n".formatted(
                index,
                String.format(Locale.ROOT, "%.3f", score),
                id.isBlank() ? "" : " | id=" + id,
                name.isBlank() ? "" : " | name=" + name
        );
    }

    private String renderDocBody(RetrievalResult r, int perSnippetLimit) {
        if (r == null || !(r.mapping() instanceof BlockMapping bm)) {
            return "(unsupported mapping type; expected BlockMapping)\n";
        }
        StringBuilder sb = new StringBuilder();
        if (nz(bm.plsqlType()) || nz(bm.javaType())) {
            sb.append("- types: ");
            if (nz(bm.plsqlType())) sb.append("plsql=").append(bm.plsqlType());
            if (nz(bm.plsqlType()) && nz(bm.javaType())) sb.append(", ");
            if (nz(bm.javaType())) sb.append("java=").append(bm.javaType());
            sb.append("\n");
        }
        if (nz(bm.plsqlSnippet())) {
            sb.append("\n-- PL/SQL\n```\n")
                    .append(trunc(bm.plsqlSnippet(), perSnippetLimit))
                    .append("\n```\n");
        } else {
            sb.append("\n-- PL/SQL\n(no plsqlSnippet available)\n");
        }
        if (nz(bm.javaSnippet())) {
            sb.append("\n-- Java\n```\n")
                    .append(trunc(bm.javaSnippet(), perSnippetLimit))
                    .append("\n```\n");
        }
        if (bm.javaHelpers() != null && !bm.javaHelpers().isEmpty()) {
            sb.append("\n- javaHelpers: ").append(String.join(", ", bm.javaHelpers())).append("\n");
        }
        return sb.toString();
    }

    private static boolean nz(String s) { return s != null && !s.isBlank(); }
    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + " …";
    }
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
