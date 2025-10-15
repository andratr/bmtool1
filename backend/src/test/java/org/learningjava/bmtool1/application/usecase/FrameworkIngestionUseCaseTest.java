package org.learningjava.bmtool1.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.FrameworkStorePort;
import org.learningjava.bmtool1.domain.model.FrameworkSymbol;
import org.learningjava.bmtool1.infrastructure.adapter.out.FrameworkSymbolExtractor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FrameworkIngestionUseCaseTest {

    private FrameworkStorePort store;
    private EmbeddingPort embedding;
    private FrameworkSymbolExtractor extractor;
    private FrameworkIngestionUseCase useCase;

    @BeforeEach
    void setUp() {
        store = mock(FrameworkStorePort.class);
        embedding = mock(EmbeddingPort.class);
        extractor = mock(FrameworkSymbolExtractor.class);

        useCase = new FrameworkIngestionUseCase(store, embedding, extractor);
    }

    @Test
    void ingestsFrameworkSymbolsSuccessfully() {
        // given
        var symbol1 = new FrameworkSymbol(
                "com.example.MyClass",
                "doSomething",
                "void doSomething(String arg)",
                "some snippet",
                "service",
                List.of("service", "utility")
        );

        var symbol2 = new FrameworkSymbol(
                "com.example.OtherClass",
                "parse",
                "static Other parse(String text)",
                "usage example",
                "dto",
                List.of("dto", "parser")
        );

        when(extractor.extract("com.example.framework"))
                .thenReturn(List.of(symbol1, symbol2));

        when(embedding.embed(any())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        // when
        int count = useCase.ingest("com.example.framework");

        // then
        assertThat(count).isEqualTo(2);

        verify(store).ensureSchema();
        verify(store).upsertSymbols(anyList(), anyList());
        verify(embedding, atLeast(2)).embed(any());
        verify(extractor).extract("com.example.framework");
        verifyNoMoreInteractions(store, embedding, extractor);
    }
}
