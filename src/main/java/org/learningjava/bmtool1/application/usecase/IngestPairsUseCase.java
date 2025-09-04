package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.PairReaderPort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.domain.model.SourcePair;
import org.learningjava.bmtool1.domain.model.Block;
import org.learningjava.bmtool1.domain.model.BlockMapping;
import org.learningjava.bmtool1.domain.service.templateCreator.BlockMapper;
import org.learningjava.bmtool1.domain.service.ASTParser.PlsqlBlockExtractor;
import org.learningjava.bmtool1.domain.service.ASTParser.JavaBlockExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Use case: Ingest PL/SQL ↔ Java pairs and map blocks to methods.
 */
public class IngestPairsUseCase {

    private final PairReaderPort pairReader;
    private final BlockMapper blockMapper = new BlockMapper();
    private final PlsqlBlockExtractor plsqlExtractor = new PlsqlBlockExtractor();
    private final JavaBlockExtractor javaExtractor = new JavaBlockExtractor();
    private final VectorStorePort store;
    private final EmbeddingPort embedding;
    private static final Logger log = LoggerFactory.getLogger(IngestPairsUseCase.class);

    public IngestPairsUseCase(PairReaderPort pairReader,
                              VectorStorePort store,
                              EmbeddingPort embedding) {
        this.pairReader = pairReader;
        this.store = store;
        this.embedding = embedding;
    }

    /**
     * Discover all pairs under a directory and produce block-to-method mappings.
     */
    public List<BlockMapping> ingestDirectory(String rootDir) throws Exception {
        List<SourcePair> pairs = pairReader.discoverPairs(rootDir);
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

        // ✅ Persist into vector store
        if (!allMappings.isEmpty()) {
            List<float[]> vectors = allMappings.stream()
                    .map(m -> embedding.embed(m.plsqlSnippet() + " " + m.javaSnippet()))
                    .toList();

            store.ensureSchema();
            store.upsertMappings(allMappings, vectors);
        }

        return allMappings;
    }
}
