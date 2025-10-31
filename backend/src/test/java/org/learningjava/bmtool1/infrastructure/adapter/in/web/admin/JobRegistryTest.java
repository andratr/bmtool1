package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobRegistryTest {

    @Test
    void start_initializesRunningWithTotals() {
        JobRegistry reg = new JobRegistry();

        String id = reg.start("RAG", 5);
        assertNotNull(id);

        JobRegistry.JobStatus s = reg.get(id);
        assertNotNull(s);
        assertEquals(id, s.id());
        assertEquals("RAG", s.type());
        assertEquals(JobRegistry.JobState.RUNNING, s.state());
        assertEquals("Started", s.message());
        assertEquals(0, s.processed());
        assertEquals(5, s.total());
    }

    @Test
    void start_negativeTotalIsClampedToZero() {
        JobRegistry reg = new JobRegistry();

        String id = reg.start("ETL", -10);
        JobRegistry.JobStatus s = reg.get(id);
        assertEquals(0, s.total());
    }

    @Test
    void update_overwritesProcessedAndMessageButKeepsTypeTotal() {
        JobRegistry reg = new JobRegistry();
        String id = reg.start("RAG", 7);

        reg.update(id, 3, "Scanning…");
        JobRegistry.JobStatus s = reg.get(id);

        assertEquals(JobRegistry.JobState.RUNNING, s.state());
        assertEquals("RAG", s.type());
        assertEquals(7, s.total());
        assertEquals(3, s.processed());
        assertEquals("Scanning…", s.message());
    }

    @Test
    void update_nullMessageKeepsPreviousMessage() {
        JobRegistry reg = new JobRegistry();
        String id = reg.start("RAG", 2);

        reg.update(id, 1, "Phase 1");
        reg.update(id, 2, null);

        JobRegistry.JobStatus s = reg.get(id);
        assertEquals(2, s.processed());
        assertEquals("Phase 1", s.message());
    }

    @Test
    void update_onUnknownIdCreatesRunningJobWithDefaults() {
        JobRegistry reg = new JobRegistry();

        String missing = "does-not-exist";
        reg.update(missing, 4, "Created implicitly");
        JobRegistry.JobStatus s = reg.get(missing);

        assertNotNull(s);
        assertEquals(JobRegistry.JobState.RUNNING, s.state());
        assertEquals("RAG", s.type()); // default per implementation
        assertEquals(0, s.total());    // default per implementation
        assertEquals(4, s.processed());
        assertEquals("Created implicitly", s.message());
    }

    @Test
    void done_setsDoneAndProcessedEqualsTotal_messageDefaultsIfNull() {
        JobRegistry reg = new JobRegistry();
        String id = reg.start("RAG", 9);

        reg.done(id, null);
        JobRegistry.JobStatus s = reg.get(id);

        assertEquals(JobRegistry.JobState.DONE, s.state());
        assertEquals(9, s.total());
        assertEquals(9, s.processed());
        assertEquals("Done", s.message());
    }

    @Test
    void fail_setsFailedAndKeepsProcessedAndTotal_messageDefaultsIfNull() {
        JobRegistry reg = new JobRegistry();
        String id = reg.start("RAG", 10);
        reg.update(id, 6, "Halfway");

        reg.fail(id, null);
        JobRegistry.JobStatus s = reg.get(id);

        assertEquals(JobRegistry.JobState.FAILED, s.state());
        assertEquals(6, s.processed());
        assertEquals(10, s.total());
        assertEquals("Failed", s.message());
    }

    @Test
    void done_onUnknownIdCreatesJobThenMarksDone() {
        JobRegistry reg = new JobRegistry();
        String id = "random-id";

        reg.done(id, "All good");
        JobRegistry.JobStatus s = reg.get(id);

        assertEquals(JobRegistry.JobState.DONE, s.state());
        assertEquals("RAG", s.type());
        assertEquals("All good", s.message());
        assertEquals(0, s.total());
        assertEquals(0, s.processed());
    }

    @Test
    void fail_onUnknownIdCreatesJobThenMarksFailed() {
        JobRegistry reg = new JobRegistry();
        String id = "random-id";

        reg.fail(id, "Boom");
        JobRegistry.JobStatus s = reg.get(id);

        assertEquals(JobRegistry.JobState.FAILED, s.state());
        assertEquals("RAG", s.type());
        assertEquals("Boom", s.message());
        assertEquals(0, s.total());
        assertEquals(0, s.processed());
    }

    @Test
    void get_unknownIdReturnsNull() {
        JobRegistry reg = new JobRegistry();
        assertNull(reg.get("nope"));
    }
}
