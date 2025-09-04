package org.learningjava.bmtool1.domain.model;

public record PlsqlSnippet(
        String eventCode,        // e.g. EXP_EADIFR9CCY_DEFAULTED_DEFAULTED
        String domain,           // e.g. Customer, Limit, Account
        String type,             // e.g. INSERT, CONDITION, EXCEPTION
        String content,          // raw PL/SQL text
        String javaTranslation   // null initially, filled after RAG
) {
    public PlsqlSnippet withJavaTranslation(String translation) {
        return new PlsqlSnippet(eventCode, domain, type, content, translation);
    }
}
