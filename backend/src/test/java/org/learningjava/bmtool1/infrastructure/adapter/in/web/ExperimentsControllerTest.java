package org.learningjava.bmtool1.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.domain.model.Experiment;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
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
        // Arrange
        var rows = List.of(
                new Experiment(1L, LocalDate.of(2025, 10, 10), 120, 60, 25,
                        "prompt A", "text-embedding-3-large", "llama3",
                        0.81, 185.0, 12.3),
                new Experiment(2L, LocalDate.of(2025, 10, 11), 90, 40, 10,
                        "prompt B", "text-embedding-3-large", "llama3",
                        null, 160.0, null)
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
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].experimentDate", is("2025-10-10")))
                .andExpect(jsonPath("$[0].embeddingModel", is("text-embedding-3-large")))
                .andExpect(jsonPath("$[0].llmModel", is("llama3")))
                .andExpect(jsonPath("$[0].k", is(25)))
                .andExpect(jsonPath("$[0].numSamples", is(120)))
                .andExpect(jsonPath("$[0].numRagSamples", is(60)));

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
        org.junit.jupiter.api.Assertions.assertNotNull(from);
        org.junit.jupiter.api.Assertions.assertNotNull(to);
        org.junit.jupiter.api.Assertions.assertFalse(from.isAfter(to), "from should be <= to");
        org.junit.jupiter.api.Assertions.assertTrue(!from.isAfter(to.minusDays(10)), "default range should be roughly ~2 weeks");
        verifyNoMoreInteractions(store);
    }

    @Test
    void filters_are_caseInsensitive_and_substring() throws Exception {
        // Arrange: return a mixed set; controller will filter in-memory
        var rows = List.of(
                new Experiment(10L, LocalDate.of(2025, 10, 10), 100, 50, 10,
                        "p1", "Text-Embedding-3-Large", "LLAMA3", 0.7, 120.0, 5.0),
                new Experiment(11L, LocalDate.of(2025, 10, 10), 80, 40, 10,
                        "p2", "bge-small", "gpt-4o", 0.6, 140.0, 6.0),
                new Experiment(12L, LocalDate.of(2025, 10, 10), 60, 30, 10,
                        "p3", "text-embedding-3-large-v2", "llama3.1", 0.8, 110.0, 4.5)
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
    void null_metrics_serialize_as_null() throws Exception {
        LocalDate d = LocalDate.parse("2025-10-09");

        var rows = List.of(
                new Experiment(99L, d, 10, 5, 3,
                        "prompt X", "emb", "llm",
                        null, null, null)
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
                .andExpect(jsonPath("$[0].metric3Co2G", is(nullValue())));

        verify(store).listByDateRange(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 15));
        verifyNoMoreInteractions(store);
    }

}
