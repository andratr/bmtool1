package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.FrameworkStorePort;
import org.learningjava.bmtool1.infrastructure.adapter.out.FrameworkSymbolExtractor;
import org.springframework.stereotype.Service;

@Service
public class FrameworkIngestionUseCase {
    private final FrameworkStorePort store;
    private final EmbeddingPort embedding;
    private final FrameworkSymbolExtractor extractor;

    public FrameworkIngestionUseCase(FrameworkStorePort store,
                                     EmbeddingPort embedding,
                                     FrameworkSymbolExtractor extractor) {
        this.store = store;
        this.embedding = embedding;
        this.extractor = extractor;
    }

    public int ingest(String... basePackages) {
        store.ensureSchema();
        var symbols = extractor.extract(basePackages); // build from reflection
        var vectors = symbols.stream()
                .map(s -> embedding.embed((s.symbol() + " " + s.methodSignature() + " " + s.snippet()).trim()))
                .toList();
        store.upsertSymbols(symbols, vectors);
        return symbols.size();
    }
}
