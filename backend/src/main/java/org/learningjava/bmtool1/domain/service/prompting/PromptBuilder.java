// src/main/java/org/learningjava/bmtool1/domain/service/prompting/PromptBuilder.java
package org.learningjava.bmtool1.domain.service.prompting;

import org.learningjava.bmtool1.domain.model.framework.FrameworkRetrievalResult;
import org.learningjava.bmtool1.domain.model.pairs.RetrievalResult;

import java.util.List;

public interface PromptBuilder {
    String build(
            PromptingTechnique technique,
            String question,
            List<RetrievalResult> docHits,
            List<FrameworkRetrievalResult> fwHits,
            int perSnippetCharLimit,
            int docPromptLimit
    );
}
