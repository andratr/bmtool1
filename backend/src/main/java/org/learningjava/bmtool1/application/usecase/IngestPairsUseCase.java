package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.BlockExtractorPort;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.PairReaderPort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.domain.model.Block;
import org.learningjava.bmtool1.domain.model.BlockMapping;
import org.learningjava.bmtool1.domain.model.SourcePair;
import org.learningjava.bmtool1.domain.service.ingest.BlockMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class IngestPairsUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestPairsUseCase.class);

    private final PairReaderPort pairReader;
    private final BlockMapper blockMapper;
    private final BlockExtractorPort plsqlExtractor;
    private final BlockExtractorPort javaExtractor;
    private final VectorStorePort store;
    private final EmbeddingPort embedding;

    public IngestPairsUseCase(
            PairReaderPort pairReader,
            @Qualifier("plsqlBlockExtractor") BlockExtractorPort plsqlExtractor,
            @Qualifier("javaBlockExtractor") BlockExtractorPort javaExtractor,
            BlockMapper blockMapper,
            VectorStorePort store,
            EmbeddingPort embedding
    ) {
        this.pairReader = pairReader;
        this.plsqlExtractor = plsqlExtractor;
        this.javaExtractor = javaExtractor;
        this.blockMapper = blockMapper;
        this.store = store;
        this.embedding = embedding;
    }

    public List<BlockMapping> ingestDirectory(String rootDir) throws Exception {
        List<SourcePair> pairs = pairReader.discoverPairs(rootDir);

        if (pairs == null || pairs.isEmpty()) {
            log.warn("No SQL–Java pairs found in {}", rootDir);
            throw new IllegalStateException("No SQL–Java pairs found in " + rootDir);
        }

        List<BlockMapping> allMappings = new ArrayList<>();

        for (var p : pairs) {
            Path plsqlPath = Path.of(p.plsqlPath());
            Path javaPath  = Path.of(p.javaPath());

            List<Block> plsqlBlocks = plsqlExtractor.extract(plsqlPath);
            List<Block> javaBlocks  = javaExtractor.extract(javaPath);

            var mappings = blockMapper.map(plsqlBlocks, javaBlocks);

            for (var m : mappings) {
                log.info("""
                                🔗 Mapping found:
                                [ {} ] {}  ->  [ {} ] {}
                                ------------------------------------------------
                                PLSQL:
                                {}
                                
                                JAVA:
                                {}
                                
                                JAVA HELPERS (IF ANY):
                                {}
                                ------------------------------------------------
                                """,
                        p.plsqlPath(), m.plsqlType(),
                        p.javaPath(), m.javaType(),
                        m.plsqlSnippet(),
                        m.javaSnippet(),
                        m.javaHelpers()
                );
            }

            allMappings.addAll(mappings);
        }

        if (!allMappings.isEmpty()) {
            store.ensureSchema();
            List<float[]> vectors = allMappings.stream()
                    .map(m -> embedding.embed(m.plsqlSnippet() + " " + m.javaSnippet()))
                    .toList();

            store.upsertMappings(allMappings, vectors);
        } else {
            log.warn("Pairs were discovered, but no block mappings resulted for {}", rootDir);
        }

        return allMappings;
    }
}
