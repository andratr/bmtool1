// src/main/java/org/learningjava/bmtool1/domain/service/PromptBuilder.java
package org.learningjava.bmtool1.domain.service;

import org.learningjava.bmtool1.domain.model.RetrievalResult;

import java.util.List;

public class PromptBuilder {

    private final PromptRepository repo;

    public PromptBuilder(PromptRepository repo) {
        this.repo = repo;
    }

    public String buildRagPrompt(String question, List<RetrievalResult> context) {
        String base = repo.getPrompt("rag");

        StringBuilder sb = new StringBuilder();
        sb.append(base).append("\n\n");

        sb.append("Question: ").append(question).append("\n\n");
        sb.append("<docs>\n");

        for (var r : context) {
            var m = r.mapping();
            sb.append("### Pair ").append(m.pairId())
                    .append(" (score ").append(String.format("%.3f", r.score())).append(")\n")
                    .append("[PLSQL - ").append(m.plsqlType()).append("]\n")
                    .append(m.plsqlSnippet()).append("\n\n")
                    .append("[JAVA - ").append(m.javaType()).append("]\n")
                    .append(m.javaSnippet()).append("\n\n");
        }

        sb.append("</docs>\n");
        return sb.toString();
    }
}
