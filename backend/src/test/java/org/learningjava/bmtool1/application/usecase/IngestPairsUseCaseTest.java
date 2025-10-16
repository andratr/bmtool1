package org.learningjava.bmtool1.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.port.BlockExtractorPort;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.PairReaderPort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.domain.model.Block;
import org.learningjava.bmtool1.domain.model.BlockMapping;
import org.learningjava.bmtool1.domain.model.PairId;
import org.learningjava.bmtool1.domain.model.SourcePair;
import org.learningjava.bmtool1.domain.service.ingest.BlockMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestPairsUseCaseTest {

    private PairReaderPort pairReader;
    private BlockExtractorPort plsqlExtractor;
    private BlockExtractorPort javaExtractor;
    private VectorStorePort store;
    private EmbeddingPort embedding;
    private BlockMapper mapper;   // we’ll mock to avoid rules.yml

    private IngestPairsUseCase useCase;

    @BeforeEach
    void setUp() {
        pairReader = mock(PairReaderPort.class);
        plsqlExtractor = mock(BlockExtractorPort.class);
        javaExtractor = mock(BlockExtractorPort.class);
        store = mock(VectorStorePort.class);
        embedding = mock(EmbeddingPort.class);
        mapper = mock(BlockMapper.class);

        useCase = new IngestPairsUseCase(
                pairReader, plsqlExtractor, javaExtractor, mapper, store, embedding
        );
    }

    @Test
    void ingests_pairs_and_upserts_when_mappings_exist() throws Exception {
        // Arrange
        when(pairReader.discoverPairs("root"))
                .thenReturn(List.of(new SourcePair(new PairId("id-1"), "a.sql", "A.java")));

        var plsqlBlock = new Block("INSERT_STATEMENT", "INSERT INTO T ...", "a.sql");
        var javaBlock = new Block("METHOD", "void transformation(){}", "A.java");

        when(plsqlExtractor.extract(Path.of("a.sql"))).thenReturn(List.of(plsqlBlock));
        when(javaExtractor.extract(Path.of("A.java"))).thenReturn(List.of(javaBlock));

        var mapping = new BlockMapping(
                "pid-1", "pair-a", plsqlBlock.text(), javaBlock.text(),
                plsqlBlock.type(), javaBlock.type(), null
        );
        when(mapper.map(List.of(plsqlBlock), List.of(javaBlock))).thenReturn(List.of(mapping));

        when(embedding.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        // Act
        List<BlockMapping> out = useCase.ingestDirectory("root");

        // Assert
        assertEquals(1, out.size());
        verify(store).ensureSchema();
        verify(embedding, times(1)).embed(contains("INSERT INTO T"));
        verify(store, times(1)).upsertMappings(eq(out), anyList());
        verifyNoMoreInteractions(store, embedding);
    }

    @Test
    void does_not_call_store_when_no_pairs_found() throws Exception {
        when(pairReader.discoverPairs("root")).thenReturn(List.of());

        List<BlockMapping> out = useCase.ingestDirectory("root");

        assertTrue(out.isEmpty());
        verifyNoInteractions(store, embedding, plsqlExtractor, javaExtractor);
    }

    @Test
    void does_not_call_store_when_no_mappings_found() throws Exception {
        when(pairReader.discoverPairs("root"))
                .thenReturn(List.of(new SourcePair(new PairId("id-1"), "a.sql", "A.java")));

        when(plsqlExtractor.extract(Path.of("a.sql"))).thenReturn(List.of());
        when(javaExtractor.extract(Path.of("A.java"))).thenReturn(List.of());
        when(mapper.map(anyList(), anyList())).thenReturn(List.of());

        List<BlockMapping> out = useCase.ingestDirectory("root");

        assertTrue(out.isEmpty());
        verifyNoInteractions(store, embedding);
    }

    @Test
    void propagates_extractor_exception() throws Exception {
        when(pairReader.discoverPairs("root"))
                .thenReturn(List.of(new SourcePair(new PairId("id-1"), "a.sql", "A.java")));

        when(plsqlExtractor.extract(Path.of("a.sql")))
                .thenThrow(new RuntimeException("parser boom"));

        assertThrows(RuntimeException.class, () -> useCase.ingestDirectory("root"));
        verifyNoInteractions(store, embedding);
    }

    @Test
    void embeds_once_per_mapping() throws Exception {
        when(pairReader.discoverPairs("root"))
                .thenReturn(List.of(new SourcePair(new PairId("id-1"), "a.sql", "A.java"),
                        new SourcePair(new PairId("id-2"), "b.sql", "B.java")));

        var p1 = new Block("INSERT_STATEMENT", "P1", "a.sql");
        var j1 = new Block("METHOD", "J1", "A.java");
        var p2 = new Block("ASSIGNMENT_CONST_STRING", "P2", "b.sql");
        var j2 = new Block("METHOD", "J2", "B.java");

        when(plsqlExtractor.extract(Path.of("a.sql"))).thenReturn(List.of(p1));
        when(javaExtractor.extract(Path.of("A.java"))).thenReturn(List.of(j1));
        when(plsqlExtractor.extract(Path.of("b.sql"))).thenReturn(List.of(p2));
        when(javaExtractor.extract(Path.of("B.java"))).thenReturn(List.of(j2));

        var m1 = new BlockMapping("id1", "pair1", p1.text(), j1.text(), p1.type(), j1.type(), null);
        var m2 = new BlockMapping("id2", "pair2", p2.text(), j2.text(), p2.type(), j2.type(), null);

        when(mapper.map(List.of(p1), List.of(j1))).thenReturn(List.of(m1));
        when(mapper.map(List.of(p2), List.of(j2))).thenReturn(List.of(m2));
        when(embedding.embed(anyString())).thenReturn(new float[]{0.3f});

        List<BlockMapping> out = useCase.ingestDirectory("root");

        assertEquals(2, out.size());
        verify(store).ensureSchema();
        verify(embedding, times(2)).embed(anyString());
        verify(store, times(1)).upsertMappings(eq(out), anyList());
    }
}
