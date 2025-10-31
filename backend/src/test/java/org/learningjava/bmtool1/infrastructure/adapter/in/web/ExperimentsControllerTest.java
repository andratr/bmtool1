// src/test/java/org/learningjava/bmtool1/infrastructure/adapter/in/web/ExperimentsControllerTest.java
package org.learningjava.bmtool1.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.domain.model.analytics.Experiment;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExperimentsControllerTest {

    private ExperimentStorePort store;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(ExperimentStorePort.class);

        // Controller under test
        ExperimentsController controller = new ExperimentsController(store);

        // Configure Jackson to serialize Java time types as ISO-8601 strings
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter(om);

        // Build standalone MockMvc with our converter
        mvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setMessageConverters(json)
                .build();
    }

    @Test
    void list_returnsRows_withQueryParamsApplied() throws Exception {
        // Arrange with NEW Experiment signature
        var rows = List.of(
                new Experiment(
                        1L, LocalDate.of(2025, 10, 10),
                        7, 5,                 // fwHitsCount, docHitsCount
                        25, 12,               // kFw, kDoc
                        "prompt A",
                        "text-embedding-3-large",
                        "llama3",
                        0.81, 185.0, 12.3,    // metrics 1..3
                        1400, 600, 2000,      // tokens p/c/t
                        "RAG_STANDARD"        // technique
                ),
                new Experiment(
                        2L, LocalDate.of(2025, 10, 11),
                        3, 1,
                        10, 10,
                        "prompt B",
                        "text-embedding-3-large",
                        "llama3",
                        null, 160.0, null,
                        null, null, null,
                        null
                )
        );
        when(store.listByDateRange(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 15)))
                .thenReturn(rows);

        // Act + Assert
        mvc.perform(get("/experiments")
                        .param("from", "2025-10-01")
                        .param("to", "2025-10-15")
                        .param("embedding", "text-embedding-3-large")
                        .param("llm", "llama3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))

                // Row 0 basic fields
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].experimentDate", is("2025-10-10")))
                .andExpect(jsonPath("$[0].embeddingModel", is("text-embedding-3-large")))
                .andExpect(jsonPath("$[0].llmModel", is("llama3")))
                .andExpect(jsonPath("$[0].kFw", is(25)))
                .andExpect(jsonPath("$[0].kDoc", is(12)))
                .andExpect(jsonPath("$[0].fwHitsCount", is(7)))
                .andExpect(jsonPath("$[0].docHitsCount", is(5)))

                // Row 0 metrics and tokens
                .andExpect(jsonPath("$[0].metric1Ccc", is(closeTo(0.81, 1e-9))))
                .andExpect(jsonPath("$[0].metric2TimeMs", is(closeTo(185.0, 1e-9))))
                .andExpect(jsonPath("$[0].metric3Co2G", is(closeTo(12.3, 1e-9))))
                .andExpect(jsonPath("$[0].metric4PromptTok", is(1400)))
                .andExpect(jsonPath("$[0].metric5CompletionTok", is(600)))
                .andExpect(jsonPath("$[0].metric6TotalTok", is(2000)))
                .andExpect(jsonPath("$[0].promptingTechnique", is("RAG_STANDARD")))

                // Row 1 has nulls for metrics/tokens/technique
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].experimentDate", is("2025-10-11")))
                .andExpect(jsonPath("$[1].metric1Ccc", is(nullValue())))
                .andExpect(jsonPath("$[1].metric3Co2G", is(nullValue())))
                .andExpect(jsonPath("$[1].metric4PromptTok", is(nullValue())))
                .andExpect(jsonPath("$[1].promptingTechnique", is(nullValue())));

        verify(store).listByDateRange(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 15));
        verifyNoMoreInteractions(store);
    }

    @Test
    void list_usesDefaultDateRange_whenNoParamsProvided() throws Exception {
        when(store.listByDateRange(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        mvc.perform(get("/experiments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));

        ArgumentCaptor<LocalDate> fromCap = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(store).listByDateRange(fromCap.capture(), toCap.capture());

        LocalDate from = fromCap.getValue();
        LocalDate to = toCap.getValue();
        assertNotNull(from);
        assertNotNull(to);
        assertFalse(from.isAfter(to), "from should be <= to");
        // loose sanity check: default range is roughly ~2 weeks
        assertTrue(!from.isAfter(to.minusDays(10)));
        verifyNoMoreInteractions(store);
    }

    @Test
    void filters_are_caseInsensitive_and_substring() throws Exception {
        // Arrange: return a mixed set; controller will filter in-memory
        var rows = List.of(
                new Experiment(10L, LocalDate.of(2025, 10, 10),
                        4, 6, 10, 12,
                        "p1", "Text-Embedding-3-Large", "LLAMA3",
                        0.7, 120.0, 5.0,
                        900, 400, 1300,
                        "RAG_STANDARD"),
                new Experiment(11L, LocalDate.of(2025, 10, 10),
                        2, 3, 8, 8,
                        "p2", "bge-small", "gpt-4o",
                        0.6, 140.0, 6.0,
                        null, null, null,
                        null),
                new Experiment(12L, LocalDate.of(2025, 10, 10),
                        1, 2, 6, 6,
                        "p3", "text-embedding-3-large-v2", "llama3.1",
                        0.8, 110.0, 4.5,
                        null, null, null,
                        "FEW_SHOT")
        );
        when(store.listByDateRange(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 15)))
                .thenReturn(rows);

        // Act + Assert: looking for "text-embedding-3-large" + "llama3" (case-insensitive, substring)
        mvc.perform(get("/experiments")
                        .param("from", "2025-10-01")
                        .param("to", "2025-10-15")
                        .param("embedding", "text-embedding-3-large")
                        .param("llm", "llama3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))            // ids 10 and 12 should match
                .andExpect(jsonPath("$[0].id", is(10)))
                .andExpect(jsonPath("$[1].id", is(12)));

        verify(store).listByDateRange(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 15));
        verifyNoMoreInteractions(store);
    }

    @Test
    void invalid_dateParam_returns_400() throws Exception {
        mvc.perform(get("/experiments")
                        .param("from", "2025-13-40") // invalid
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        // store should not be called
        verifyNoInteractions(store);
    }

    @Test
    void null_metrics_and_tokens_serialize_as_null() throws Exception {
        LocalDate d = LocalDate.parse("2025-10-09");

        var rows = List.of(
                new Experiment(99L, d,
                        10, 5, 3, 3,
                        "prompt X", "emb", "llm",
                        null, null, null,
                        null, null, null,
                        null)
        );
        when(store.listByDateRange(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 15)))
                .thenReturn(rows);

        mvc.perform(get("/experiments")
                        .param("from", "2025-10-01")
                        .param("to", "2025-10-15")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(99)))
                .andExpect(jsonPath("$[0].metric1Ccc", is(nullValue())))
                .andExpect(jsonPath("$[0].metric2TimeMs", is(nullValue())))
                .andExpect(jsonPath("$[0].metric3Co2G", is(nullValue())))
                .andExpect(jsonPath("$[0].metric4PromptTok", is(nullValue())))
                .andExpect(jsonPath("$[0].metric5CompletionTok", is(nullValue())))
                .andExpect(jsonPath("$[0].metric6TotalTok", is(nullValue())))
                .andExpect(jsonPath("$[0].promptingTechnique", is(nullValue())));

        verify(store).listByDateRange(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 15));
        verifyNoMoreInteractions(store);
    }
}
