package org.learningjava.bmtool1.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.domain.model.Experiment;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        // Act
        long id = useCase.record(
                day,
                120, 60, 25,
                "test prompt",
                "text-embedding-3-large",
                "llama3",
                0.82, 185.0, 12.1
        );

        // Assert
        assertEquals(123L, id);

        ArgumentCaptor<Experiment> cap = ArgumentCaptor.forClass(Experiment.class);
        verify(experiments, times(1)).upsert(cap.capture());
        Experiment e = cap.getValue();

        assertNull(e.id(), "id should be null before DB insert");
        assertEquals(day, e.experimentDate());
        assertEquals(120, e.numSamples());
        assertEquals(60, e.numRagSamples());
        assertEquals(25, e.k());
        assertEquals("test prompt", e.prompt());
        assertEquals("text-embedding-3-large", e.embeddingModel());
        assertEquals("llama3", e.llmModel());
        assertEquals(0.82, e.metric1Ccc());
        assertEquals(185.0, e.metric2TimeMs());
        assertEquals(12.1, e.metric3Co2G());

        verifyNoMoreInteractions(experiments);
    }

    @Test
    void record_allowsNullOptionalMetrics() {
        // Arrange
        LocalDate day = LocalDate.of(2025, 10, 16);
        when(experiments.upsert(any(Experiment.class))).thenReturn(7L);

        // Act
        long id = useCase.record(
                day,
                10, 5, 3,
                "p",
                "emb-small",
                "gpt-4.1",
                null, null, null
        );

        // Assert
        assertEquals(7L, id);

        ArgumentCaptor<Experiment> cap = ArgumentCaptor.forClass(Experiment.class);
        verify(experiments).upsert(cap.capture());
        Experiment e = cap.getValue();

        assertNull(e.metric1Ccc());
        assertNull(e.metric2TimeMs());
        assertNull(e.metric3Co2G());
    }
}
