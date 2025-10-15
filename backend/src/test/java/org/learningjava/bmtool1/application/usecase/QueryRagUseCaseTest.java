package org.learningjava.bmtool1.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.port.ChatLLMPort;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.domain.model.Answer;
import org.learningjava.bmtool1.domain.model.BlockMapping;
import org.learningjava.bmtool1.domain.model.Query;
import org.learningjava.bmtool1.domain.model.RetrievalResult;
import org.learningjava.bmtool1.domain.service.ChatRegistry;
import org.learningjava.bmtool1.domain.service.prompting.PromptBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for orchestration logic
 */
class QueryRagUseCaseTest {

    private EmbeddingPort embedding;
    private VectorStorePort store;
    private ChatRegistry chatRegistry;
    private PromptBuilder prompts;
    private ChatLLMPort chat;

    private QueryRagUseCase useCase;

    @BeforeEach
    void setUp() {
        embedding    = mock(EmbeddingPort.class);
        store        = mock(VectorStorePort.class);
        chatRegistry = mock(ChatRegistry.class);
        prompts      = mock(PromptBuilder.class);
        chat         = mock(ChatLLMPort.class);

        useCase = new QueryRagUseCase(embedding, store, chatRegistry, prompts);
    }

    // --- helpers -------------------------------------------------------------

    private RetrievalResult hit(double score, String plsql, String java, String plType, String jType) {
        BlockMapping mapping = new BlockMapping(
                "pid", "pairName", plsql, java, plType, jType, null
        );
        return new RetrievalResult(mapping, score);
    }

    // --- tests ----------------------------------------------------------------

    @Test
    void usesRagPrompt_when_hits_above_threshold() {
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
        when(chat.chat("RAG_PROMPT", "llama3")).thenReturn("THE_ANSWER");

        // Act
        Answer answer = useCase.ask(new Query(question), 5, "ollama", "llama3");

        // Assert
        assertNotNull(answer);
        assertEquals("THE_ANSWER", answer.text());
        assertEquals(1, answer.retrievalResults().size(), "only the >= 0.60 hit should remain");

        // Verify interactions
        verify(embedding).embed(question);
        verify(store).query(question, vec, 5);
        verify(prompts).buildRagPrompt(eq(question), argThat(list ->
                list.size() == 1 && list.get(0).score() >= 0.60));
        verify(chatRegistry).get("ollama");          // explicit verification
        verify(chat).chat("RAG_PROMPT", "llama3");

        // Don't include chatRegistry here, since it *was* called
        verifyNoMoreInteractions(chat, prompts, store, embedding);
    }

    @Test
    void falls_back_to_general_prompt_when_no_hits() {
        // Arrange
        String question = "What is eventCode?";
        when(embedding.embed(question)).thenReturn(new float[]{0.3f});
        when(store.query(eq(question), any(), eq(3))).thenReturn(List.of()); // no context
        when(chatRegistry.get("ollama")).thenReturn(chat);

        // fallback prompt is built inside useCase (not via PromptBuilder)
        when(chat.chat(startsWith("You are a precise assistant"), eq("llama3")))
                .thenReturn("FALLBACK_ANSWER");

        // Act
        Answer answer = useCase.ask(new Query(question), 3, "ollama", "llama3");

        // Assert
        assertEquals("FALLBACK_ANSWER", answer.text());
        assertTrue(answer.retrievalResults().isEmpty());

        // PromptBuilder must NOT be called
        verifyNoInteractions(prompts);
        verify(chatRegistry).get("ollama");
    }

    @Test
    void treats_threshold_as_inclusive_0_60() {
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
        when(chat.chat("RAG_PROMPT_T", "llama3")).thenReturn("THRESHOLD_OK");

        // Act
        Answer answer = useCase.ask(new Query(question), 2, "ollama", "llama3");

        // Assert
        assertEquals("THRESHOLD_OK", answer.text());
        assertEquals(1, answer.retrievalResults().size(), "0.60 should be kept, 0.59 filtered out");
        verify(chatRegistry).get("ollama");
    }

    @Test
    void throws_if_unknown_provider() {
        when(chatRegistry.get("unknown")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                useCase.ask(new Query("Q"), 3, "unknown", "model"));
        verifyNoInteractions(embedding, store, prompts);
    }

    @Test
    void handles_null_hits_as_empty_and_falls_back() {
        String q = "Null hits?";
        when(embedding.embed(q)).thenReturn(new float[]{0.2f});
        when(store.query(eq(q), any(), eq(4))).thenReturn(null); // simulate null from store
        when(chatRegistry.get("ollama")).thenReturn(chat);
        when(chat.chat(startsWith("You are a precise assistant"), eq("l3")))
                .thenReturn("FALLBACK");

        Answer answer = useCase.ask(new Query(q), 4, "ollama", "l3");

        assertEquals("FALLBACK", answer.text());
        assertTrue(answer.retrievalResults().isEmpty());
        verifyNoInteractions(prompts);
        verify(chatRegistry).get("ollama");
    }
}
