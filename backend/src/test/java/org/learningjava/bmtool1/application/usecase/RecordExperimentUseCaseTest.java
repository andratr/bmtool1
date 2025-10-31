// src/test/java/org/learningjava/bmtool1/application/usecase/RecordExperimentUseCaseTest.java
package org.learningjava.bmtool1.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.domain.model.analytics.Experiment;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordExperimentUseCaseTest {

    private ExperimentStorePort experiments;
    private RecordExperimentUseCase useCase;

    @BeforeEach
    void setUp() {
        experiments = mock(ExperimentStorePort.class);
        useCase = new RecordExperimentUseCase(experiments);
    }

    @Test
    void record_buildsExperiment_and_returnsGeneratedId() {
        // Arrange
        LocalDate day = LocalDate.of(2025, 10, 15);
        when(experiments.upsert(any(Experiment.class))).thenReturn(123L);

        int fwHits = 7;
        int docHits = 5;
        int kFw = 25;
        int kDoc = 12;

        String prompt = "test prompt";
        String emb = "text-embedding-3-large";
        String llm = "llama3";

        Double ccc = 0.82;
        Double timeMs = 185.0;
        Double co2g = 12.1;

        Integer pTok = 1400;
        Integer cTok = 600;
        String technique = "RAG_STANDARD";

        // Act
        long id = useCase.record(
                day,
                fwHits, docHits, kFw, kDoc,
                prompt, emb, llm,
                ccc, timeMs, co2g,
                pTok, cTok,
                technique
        );

        // Assert
        assertEquals(123L, id);

        ArgumentCaptor<Experiment> cap = ArgumentCaptor.forClass(Experiment.class);
        verify(experiments, times(1)).upsert(cap.capture());
        Experiment e = cap.getValue();

        assertNull(e.id(), "id should be null before DB insert");
        assertEquals(day, e.experimentDate());

        // NEW shape assertions
        assertEquals(fwHits, e.fwHitsCount());
        assertEquals(docHits, e.docHitsCount());
        assertEquals(kFw, e.kFw());
        assertEquals(kDoc, e.kDoc());
        assertEquals(prompt, e.prompt());
        assertEquals(emb, e.embeddingModel());
        assertEquals(llm, e.llmModel());

        assertEquals(ccc, e.metric1Ccc());
        assertEquals(timeMs, e.metric2TimeMs());
        assertEquals(co2g, e.metric3Co2G());
        assertEquals(pTok, e.metric4PromptTok());
        assertEquals(cTok, e.metric5CompletionTok());
        assertEquals(Integer.valueOf(pTok + cTok), e.metric6TotalTok());
        assertEquals(technique, e.promptingTechnique());

        verifyNoMoreInteractions(experiments);
    }

    @Test
    void record_allowsNullOptionalMetricsAndTokens() {
        // Arrange
        LocalDate day = LocalDate.of(2025, 10, 16);
        when(experiments.upsert(any(Experiment.class))).thenReturn(7L);

        // Act
        long id = useCase.record(
                day,
                1, 2,   // fwHits, docHits
                3, 4,   // kFw, kDoc
                "p",
                "emb-small",
                "gpt-4.1",
                null, null, null, // ccc, time, co2
                null, null,       // promptTok, completionTok
                null              // technique
        );

        // Assert
        assertEquals(7L, id);

        ArgumentCaptor<Experiment> cap = ArgumentCaptor.forClass(Experiment.class);
        verify(experiments).upsert(cap.capture());
        Experiment e = cap.getValue();

        assertNull(e.metric1Ccc());
        assertNull(e.metric2TimeMs());
        assertNull(e.metric3Co2G());
        assertNull(e.metric4PromptTok());
        assertNull(e.metric5CompletionTok());
        assertNull(e.metric6TotalTok());
        assertNull(e.promptingTechnique());
    }

    @Test
    void legacy_overload_derives_counts_and_ks_and_computes_totalTok_null() {
        // Arrange (legacy callers)
        when(experiments.upsert(any(Experiment.class))).thenReturn(42L);
        LocalDate day = LocalDate.of(2025, 10, 20);

        // Act
        long id = useCase.record(
                day,
                12, 5, 6,              // numSamples, numRagSamples, k
                "legacy prompt", "emb", "llm",
                0.5, 100.0, 2.0
        );

        assertEquals(42L, id);

        ArgumentCaptor<Experiment> cap = ArgumentCaptor.forClass(Experiment.class);
        verify(experiments).upsert(cap.capture());
        Experiment e = cap.getValue();

        // Derived splits
        assertEquals(7, e.fwHitsCount()); // 12 - 5
        assertEquals(5, e.docHitsCount());
        assertEquals(6, e.kFw());
        assertEquals(6, e.kDoc());
        // Tokens unknown
        assertNull(e.metric4PromptTok());
        assertNull(e.metric5CompletionTok());
        assertNull(e.metric6TotalTok());
    }
}
