package org.learningjava.bmtool1.domain.model.query;

import org.learningjava.bmtool1.domain.model.framework.FrameworkRetrievalResult;
import org.learningjava.bmtool1.domain.model.pairs.RetrievalResult;

import java.util.List;

public record Answer(
        String text,
        List<RetrievalResult> retrievalResults,
        List<FrameworkRetrievalResult> frameworkResults
) {}
