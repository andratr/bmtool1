// src/main/java/org/learningjava/bmtool1/domain/service/prompting/PromptingTechnique.java
package org.learningjava.bmtool1.domain.service.prompting;

public enum PromptingTechnique {
    ZERO_SHOT,              // classic; concise
    RAG_STANDARD,           // your current composite (framework + docs)
    FRAMEWORK_FIRST,        // emphasize API usage
    FEW_SHOT,               // add exemplars
    JSON_STRUCTURED,        // ask for strict JSON
    CRITIQUE_AND_REVISE     // 2-pass self-review
}
