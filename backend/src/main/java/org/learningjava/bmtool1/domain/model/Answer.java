package org.learningjava.bmtool1.domain.model;

import java.util.List;

public record Answer(String text, List<RetrievalResult> retrievalResults) {
}