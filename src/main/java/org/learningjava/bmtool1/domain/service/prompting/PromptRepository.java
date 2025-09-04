package org.learningjava.bmtool1.domain.service.prompting;

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
                    4. If no exact single mapping is found, you MUST compose the complete Java code by combining relevant fragments from <docs>.\s
                       - Reuse predicates, functions, eventCode(), or helpers as shown in <docs>.
                       - Do not invent logic that is not present in <docs>.
                    5. If truly nothing in <docs> is relevant, reply with: "Not found in knowledge base."
                    6. Keep answers concise, but when asked to transform code, output the **whole compilable Java class**.
                    
                    Task:
                    Provide the whole transformed Java code for this PL/SQL snippet.
                    
            """
    );

    public String getPrompt(String key) {
        return prompts.getOrDefault(key, "");
    }
}
