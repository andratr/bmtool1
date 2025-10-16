package org.learningjava.bmtool1.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.port.ChatLLMPort;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.domain.model.*;
import org.learningjava.bmtool1.domain.service.ChatRegistry;
import org.learningjava.bmtool1.domain.service.prompting.PromptBuilder;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for orchestration logic (with metrics recording)
 */
class QueryRagUseCaseTest {

    private static final String LLM = "llama3";
    private static final String EMB = "text-embedding-3-large";
    private EmbeddingPort embedding;
    private VectorStorePort store;
    private ChatRegistry chatRegistry;
    private PromptBuilder prompts;
    private ChatLLMPort chat;
    private ExperimentStorePort experiments;   // NEW
    private QueryRagUseCase useCase;

    @BeforeEach
    void setUp() {
        embedding = mock(EmbeddingPort.class);
        store = mock(VectorStorePort.class);
        chatRegistry = mock(ChatRegistry.class);
        prompts = mock(PromptBuilder.class);
        chat = mock(ChatLLMPort.class);
        experiments = mock(ExperimentStorePort.class); // NEW

        when(experiments.upsert(any(Experiment.class))).thenReturn(42L); // avoid NPE

        useCase = new QueryRagUseCase(embedding, store, chatRegistry, prompts, experiments); // NEW DI
    }

    // --- helpers -------------------------------------------------------------

    private RetrievalResult hit(double score, String plsql, String java, String plType, String jType) {
        BlockMapping mapping = new BlockMapping(
                "pid", "pairName", plsql, java, plType, jType, null
        );
        return new RetrievalResult(mapping, score);
    }

    private Experiment captureSingleExperimentUpsert() {
        ArgumentCaptor<Experiment> cap = ArgumentCaptor.forClass(Experiment.class);
        verify(experiments, times(1)).upsert(cap.capture());
        return cap.getValue();
    }

    // --- tests ----------------------------------------------------------------

    @Test
    void usesRagPrompt_when_hits_above_threshold_and_records_experiment() {
        // Arrange
        String question = "How do we insert employee?";
        float[] vec = new float[]{0.1f, 0.2f};
        when(embedding.embed(question)).thenReturn(vec);

        // One above threshold (0.75), one below (0.55)
        var hits = List.of(
                hit(0.75, "PL/SQL A", "Java A", "INSERT_STATEMENT", "METHOD"),
                hit(0.55, "PL/SQL B", "Java B", "CONDITION", "METHOD")
        );
        when(store.query(eq(question), eq(vec), eq(5))).thenReturn(hits);

        when(chatRegistry.get("ollama")).thenReturn(chat);
        when(prompts.buildRagPrompt(eq(question), anyList())).thenReturn("RAG_PROMPT");
        when(chat.chat("RAG_PROMPT", LLM)).thenReturn("THE_ANSWER");

        // Act
        Answer answer = useCase.ask(new Query(question), 5, "ollama", LLM, EMB);

        // Assert
        assertNotNull(answer);
        assertEquals("THE_ANSWER", answer.text());
        assertEquals(1, answer.retrievalResults().size(), "only the >= 0.60 hit should remain");

        // Verify interactions
        verify(embedding).embed(question);
        verify(store).query(question, vec, 5);
        verify(prompts).buildRagPrompt(eq(question), argThat(list ->
                list.size() == 1 && list.get(0).score() >= 0.60));
        verify(chatRegistry).get("ollama");
        verify(chat).chat("RAG_PROMPT", LLM);

        // Metrics were recorded
        Experiment exp = captureSingleExperimentUpsert();
        assertEquals(5, exp.k());
        assertEquals(EMB, exp.embeddingModel());
        assertEquals(LLM, exp.llmModel());
        assertEquals(2, exp.numSamples());   // total retrieved
        assertEquals(1, exp.numRagSamples()); // after threshold
        assertNotNull(exp.prompt());
        assertEquals(LocalDate.now(), exp.experimentDate());

        verifyNoMoreInteractions(chat, prompts, store, embedding);
    }

    @Test
    void falls_back_to_general_prompt_when_no_hits_and_still_records_experiment() {
        // Arrange
        String question = "What is eventCode?";
        when(embedding.embed(question)).thenReturn(new float[]{0.3f});
        when(store.query(eq(question), any(), eq(3))).thenReturn(List.of()); // no context
        when(chatRegistry.get("ollama")).thenReturn(chat);

        when(chat.chat(startsWith("You are a precise assistant"), eq(LLM)))
                .thenReturn("FALLBACK_ANSWER");

        // Act
        Answer answer = useCase.ask(new Query(question), 3, "ollama", LLM, EMB);

        // Assert
        assertEquals("FALLBACK_ANSWER", answer.text());
        assertTrue(answer.retrievalResults().isEmpty());

        // PromptBuilder must NOT be called
        verifyNoInteractions(prompts);
        verify(chatRegistry).get("ollama");

        // Metrics were recorded (0 hits total, 0 kept)
        Experiment exp = captureSingleExperimentUpsert();
        assertEquals(0, exp.numSamples());
        assertEquals(0, exp.numRagSamples());
        assertEquals(EMB, exp.embeddingModel());
        assertEquals(LLM, exp.llmModel());
        assertTrue(exp.prompt().startsWith("You are a precise assistant"));
    }

    @Test
    void treats_threshold_as_inclusive_0_60_and_records_experiment() {
        // Arrange
        String question = "Show transformation method";
        when(embedding.embed(question)).thenReturn(new float[]{0.7f});

        var hits = List.of(
                hit(0.60, "PL/SQL exact threshold", "Java X", "INSERT_STATEMENT", "METHOD"),
                hit(0.59, "PL/SQL below", "Java Y", "ASSIGNMENT_CONST_STRING", "METHOD")
        );
        when(store.query(eq(question), any(), eq(2))).thenReturn(hits);

        when(chatRegistry.get("ollama")).thenReturn(chat);
        when(prompts.buildRagPrompt(eq(question), anyList())).thenReturn("RAG_PROMPT_T");
        when(chat.chat("RAG_PROMPT_T", LLM)).thenReturn("THRESHOLD_OK");

        // Act
        Answer answer = useCase.ask(new Query(question), 2, "ollama", LLM, EMB);

        // Assert
        assertEquals("THRESHOLD_OK", answer.text());
        assertEquals(1, answer.retrievalResults().size(), "0.60 should be kept, 0.59 filtered out");
        verify(chatRegistry).get("ollama");

        // Metrics recorded reflect 2 retrieved, 1 kept
        Experiment exp = captureSingleExperimentUpsert();
        assertEquals(2, exp.numSamples());
        assertEquals(1, exp.numRagSamples());
        assertEquals(2, exp.k());
    }

    @Test
    void throws_if_unknown_provider_and_does_not_record() {
        when(chatRegistry.get("unknown")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                useCase.ask(new Query("Q"), 3, "unknown", "modelX", EMB));

        verifyNoInteractions(embedding, store, prompts, experiments);
    }

    @Test
    void handles_null_hits_as_empty_and_falls_back_and_records_zero_counts() {
        String q = "Null hits?";
        when(embedding.embed(q)).thenReturn(new float[]{0.2f});
        when(store.query(eq(q), any(), eq(4))).thenReturn(null); // simulate null from store
        when(chatRegistry.get("ollama")).thenReturn(chat);
        when(chat.chat(startsWith("You are a precise assistant"), eq("l3")))
                .thenReturn("FALLBACK");

        Answer answer = useCase.ask(new Query(q), 4, "ollama", "l3", EMB);

        assertEquals("FALLBACK", answer.text());
        assertTrue(answer.retrievalResults().isEmpty());
        verifyNoInteractions(prompts);
        verify(chatRegistry).get("ollama");

        Experiment exp = captureSingleExperimentUpsert();
        assertEquals(0, exp.numSamples());
        assertEquals(0, exp.numRagSamples());
        assertEquals(4, exp.k());
        assertEquals(EMB, exp.embeddingModel());
        assertEquals("l3", exp.llmModel());
    }
}
