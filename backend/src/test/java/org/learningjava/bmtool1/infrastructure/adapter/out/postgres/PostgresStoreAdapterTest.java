// src/test/java/org/learningjava/bmtool1/infrastructure/adapter/out/postgres/PostgresStoreAdapterTest.java
package org.learningjava.bmtool1.infrastructure.adapter.out.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.domain.model.analytics.Experiment;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresStoreAdapterTest extends PostgresTestBase {
    private ExperimentStorePort store;

    @BeforeEach
    void init() {
        store = new PostgresStoreAdapter(dataSource());
        store.ensureSchema(); // creates table, indexes, unique constraint
    }

    @Test
    void upsert_returnsGeneratedId_and_findById_roundTrips() {
        var e = new Experiment(
                null, LocalDate.of(2025, 10, 1),
                7, 5,              // fwHitsCount, docHitsCount
                25, 12,            // kFw, kDoc
                "prompt A",
                "text-embedding-3-large", "gpt-4.1",
                0.81, 180.0, 12.3, // metrics
                1000, 800, 1800,   // tokens
                "RAG_STANDARD"     // technique
        );

        long id = store.upsert(e);
        assertTrue(id > 0);

        var loaded = store.findById(id);
        assertTrue(loaded.isPresent());
        assertEquals("gpt-4.1", loaded.get().llmModel());
        assertEquals(25, loaded.get().kFw());
        assertEquals(12, loaded.get().kDoc());
        assertEquals(Integer.valueOf(1800), loaded.get().metric6TotalTok());
    }

    @Test
    void upsert_conflictOnNaturalKey_updatesMetrics_and_keepsSameRow() {
        var day = LocalDate.of(2025, 10, 2);
        String prompt = "same prompt";
        String emb = "text-embedding-3-large";
        String llm = "gpt-4.1";
        int kFw = 10, kDoc = 10;
        String technique = "RAG_STANDARD";

        long id1 = store.upsert(new Experiment(
                null, day,
                4, 6,
                kFw, kDoc,
                prompt, emb, llm,
                0.70, 200.0, 9.5,
                800, 400, 1200,
                technique
        ));

        long id2 = store.upsert(new Experiment(
                null, day,
                5, 5,                   // different hits counts
                kFw, kDoc,
                prompt, emb, llm,       // SAME natural key set
                0.85, 150.0, 8.8,       // updated metrics
                900, 500, 1400,
                technique               // SAME technique
        ));

        assertEquals(id1, id2, "Upsert should hit the same row due to natural UNIQUE constraint");

        var loaded = store.findById(id1).orElseThrow();
        assertEquals(0.85, loaded.metric1Ccc(), 1e-9);
        assertEquals(150.0, loaded.metric2TimeMs(), 1e-9);
        assertEquals(8.8, loaded.metric3Co2G(), 1e-9);
        assertEquals(Integer.valueOf(1400), loaded.metric6TotalTok());
        // counts are updated too
        assertEquals(5, loaded.fwHitsCount());
        assertEquals(5, loaded.docHitsCount());
    }

    @Test
    void listByDateRange_returnsOnlyInRange() {
        store.upsert(new Experiment(null, LocalDate.of(2025, 10, 3),
                3, 7, 8, 8, "p1",
                "text-embedding-3-small", "gpt-4.1",
                0.6, 170.0, 8.0,
                null, null, null, "RAG_STANDARD"));
        store.upsert(new Experiment(null, LocalDate.of(2025, 10, 5),
                4, 6, 8, 8, "p2",
                "text-embedding-3-small", "gpt-4.1",
                0.7, 165.0, 8.2,
                null, null, null, "RAG_STANDARD"));
        store.upsert(new Experiment(null, LocalDate.of(2025, 10, 8),
                5, 5, 8, 8, "p3",
                "text-embedding-3-small", "gpt-4.1",
                0.75, 160.0, 8.5,
                null, null, null, "RAG_STANDARD"));

        var rows = store.listByDateRange(LocalDate.of(2025, 10, 4), LocalDate.of(2025, 10, 7));
        assertEquals(1, rows.size());
        assertEquals(LocalDate.of(2025, 10, 5), rows.get(0).experimentDate());
    }

    @Test
    void listByModels_filtersCorrectly() {
        store.upsert(new Experiment(null, LocalDate.of(2025, 10, 9),
                6, 4, 10, 10, "pX",
                "text-embedding-3-large", "gpt-4.1",
                0.8, 150.0, 9.0,
                null, null, null, "RAG_STANDARD"));
        store.upsert(new Experiment(null, LocalDate.of(2025, 10, 9),
                6, 4, 10, 10, "pY",
                "text-embedding-3-large", "llama-405b",
                0.78, 160.0, 9.2,
                null, null, null, "RAG_STANDARD"));

        var rows = store.listByModels("text-embedding-3-large", "gpt-4.1");
        assertTrue(rows.stream().allMatch(r ->
                r.embeddingModel().equals("text-embedding-3-large") && r.llmModel().equals("gpt-4.1")));
    }
}
