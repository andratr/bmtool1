package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.usecase.IngestPairsUseCase;
import org.learningjava.bmtool1.domain.model.pairs.BlockMapping;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MVC slice tests for RagAdminController.
 */
@WebMvcTest(RagAdminController.class)
class RagAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private IngestPairsUseCase ingest;

    @MockBean
    private JobRegistry jobs;

    // Controller injects this with @Qualifier("applicationTaskExecutor")
    @MockBean(name = "applicationTaskExecutor")
    private Executor executor;

    @BeforeEach
    void runAsyncInline() {
        // Run tasks immediately so we can assert interactions deterministically
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(executor).execute(any(Runnable.class));
    }

    // ---------- /rag/ingest ----------

    @Test
    void ingestDirectory_blankRootDir_marksJobFailed() throws Exception {
        given(jobs.start(eq("RAG"), eq(0))).willReturn("job-blank");

        mvc.perform(post("/rag/ingest").param("rootDir", ""))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId", equalTo("job-blank")));

        verify(jobs).fail(eq("job-blank"), contains("blank"));
        verifyNoInteractions(ingest);
    }

    @Test
    void ingestDirectory_nonexistentDir_marksJobFailed() throws Exception {
        given(jobs.start(eq("RAG"), eq(0))).willReturn("job-missing");

        mvc.perform(post("/rag/ingest").param("rootDir", "/definitely/not/here"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", equalTo("job-missing")));

        verify(jobs).fail(eq("job-missing"), contains("not found"));
        verifyNoInteractions(ingest);
    }

    @Test
    void ingestDirectory_validDir_runsUseCase_andMarksDone() throws Exception {
        Path temp = Files.createTempDirectory("rag-test-");
        try {
            given(jobs.start(eq("RAG"), eq(0))).willReturn("job-ok");
            // Return two mocked BlockMapping items
            given(ingest.ingestDirectory(eq(temp.toString())))
                    .willReturn(List.of(mock(BlockMapping.class), mock(BlockMapping.class)));

            mvc.perform(post("/rag/ingest").param("rootDir", temp.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId", equalTo("job-ok")));

            verify(jobs).update(eq("job-ok"), eq(0), contains("Scanning"));
            verify(ingest).ingestDirectory(eq(temp.toString()));
            verify(jobs).done(eq("job-ok"), contains("2"));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // ---------- /rag/upload ----------

    @Test
    void upload_noFiles_returns400() throws Exception {
        mvc.perform(multipart("/rag/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason(is("No files provided")));

        verifyNoInteractions(ingest);
        verify(jobs, never()).start(anyString(), anyInt());
    }

    @Test
    void upload_unsupportedExtension_returns415() throws Exception {
        MockMultipartFile bad = new MockMultipartFile(
                "files", "notes.exe", "application/octet-stream", new byte[]{1, 2});

        mvc.perform(multipart("/rag/upload").file(bad))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(status().reason(containsString("not allowed")));

        verifyNoInteractions(ingest);
        verify(jobs, never()).start(anyString(), anyInt());
    }

    @Test
    void upload_onlyJava_returns400() throws Exception {
        MockMultipartFile onlyJava = new MockMultipartFile(
                "files", "src/Main.java", "text/x-java-source", "class A{}".getBytes());

        mvc.perform(multipart("/rag/upload").file(onlyJava))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason(containsString("at least one PL/SQL")));

        verifyNoInteractions(ingest);
        verify(jobs, never()).start(anyString(), anyInt());
    }

    @Test
    void upload_javaAndSql_startsJob_savesFiles_callsIngest_andMarksDone() throws Exception {
        MockMultipartFile javaFile = new MockMultipartFile(
                "files", "svc/AService.java", "text/x-java-source", "class A{}".getBytes());
        MockMultipartFile sqlFile = new MockMultipartFile(
                "files", "db/schema/pkg.pkb", "text/plain", "create or replace package body...".getBytes());

        given(jobs.start(eq("RAG"), anyInt())).willReturn("job-up");
        // Return one mocked BlockMapping
        given(ingest.ingestDirectory(anyString()))
                .willReturn(List.of(mock(BlockMapping.class)));

        mvc.perform(multipart("/rag/upload")
                        .file(javaFile)
                        .file(sqlFile)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", equalTo("job-up")));

        verify(jobs).update(eq("job-up"), eq(0), contains("Uploaded"));

        ArgumentCaptor<String> tempDirCaptor = ArgumentCaptor.forClass(String.class);
        verify(ingest).ingestDirectory(tempDirCaptor.capture());

        verify(jobs).done(eq("job-up"), contains("1"));
        // cleanup happens inside the task
    }

    // ---------- /rag/jobs/{id} ----------

    @Test
    void jobStatus_returnsSnapshot() throws Exception {
        JobRegistry.JobStatus snap = new JobRegistry.JobStatus(
                "j-1", "RAG", JobRegistry.JobState.DONE, "ok", 10, 10);
        given(jobs.get("j-1")).willReturn(snap);

        mvc.perform(get("/rag/jobs/{id}", "j-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo("j-1")))
                .andExpect(jsonPath("$.type", equalTo("RAG")))
                .andExpect(jsonPath("$.state", equalTo("DONE")))
                .andExpect(jsonPath("$.message", equalTo("ok")))
                .andExpect(jsonPath("$.processed", equalTo(10)))
                .andExpect(jsonPath("$.total", equalTo(10)));
    }
}
