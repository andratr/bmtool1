package org.learningjava.bmtool1.domain.service;

import java.util.Map;

public class PromptRepository {

    private static final Map<String, String> prompts = Map.of(
            "rag",
            """
            You are a precise assistant for analyzing PL/SQL ↔ Java code pairs.
            RULES:
            1. Use only the text inside <docs>...</docs> to answer.
            2. <docs> contains PL/SQL procedures/functions and their corresponding Java classes, methods, and interfaces.
            3. If the question is about meta-information (e.g., class name, method signature, implemented interfaces), give a direct factual answer.
            4. If the answer cannot be found in <docs>, reply with: "Not found in knowledge base." Do not guess.
            5. Keep answers concise and precise.
            """
    );

    public String getPrompt(String key) {
        return prompts.getOrDefault(key, "");
    }
}
