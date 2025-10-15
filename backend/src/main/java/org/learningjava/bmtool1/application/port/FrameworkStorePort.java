package org.learningjava.bmtool1.application.port;

import org.learningjava.bmtool1.domain.model.FrameworkSymbol;

import java.util.List;

public interface FrameworkStorePort {
    void ensureSchema();  // ensures FrameworkSnippet schema
    void upsertSymbols(List<FrameworkSymbol> symbols, List<float[]> vectors);
    List<FrameworkSymbol> retrieve(String query, float[] queryVec, int k, List<String> mustHaveTags);
}