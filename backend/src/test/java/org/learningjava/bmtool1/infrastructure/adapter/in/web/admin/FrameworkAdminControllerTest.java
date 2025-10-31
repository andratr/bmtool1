package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.application.usecase.IngestFrameworkUseCase;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FrameworkAdminController.class)
class FrameworkAdminControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @MockBean
    IngestFrameworkUseCase useCase;

    @MockBean
    JobRegistry jobs;

    // ---------- /framework/ping ----------

    @Test
    void ping_returnsPiiing() throws Exception {
        mvc.perform(get("/framework/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("PIIING!"));
    }

    // ---------- /framework/ingest ----------

    @Test
    void ingest_400_whenNoPkgs() throws Exception {
        mvc.perform(post("/framework/ingest"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_returnsJobId_and_startsJob() throws Exception {
        when(jobs.start(eq("FRAMEWORK"), eq(2))).thenReturn("job-123");
        // Async path: stub so it won’t explode if it runs during test
        when(useCase.ingest(any(String[].class))).thenReturn(5);
        doNothing().when(jobs).done(eq("job-123"), anyString());
        doNothing().when(jobs).fail(eq("job-123"), anyString());

        mvc.perform(post("/framework/ingest")
                        .param("pkg", "com.acme.one")
                        .param("pkg", "com.acme.two"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId", equalTo("job-123")));

        Mockito.verify(jobs).start("FRAMEWORK", 2);
    }

    // ---------- /framework/upload (sources) ----------

    @Test
    void upload_400_whenNoFiles() throws Exception {
        mvc.perform(multipart("/framework/upload"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_415_whenPdfProvided() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile(
                "files", "spec.pdf", "application/pdf", new byte[]{1, 2, 3});
        mvc.perform(multipart("/framework/upload").file(pdf))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void upload_400_whenNoJavaOrKotlinSources() throws Exception {
        MockMultipartFile readme = new MockMultipartFile(
                "files", "README.md", "text/markdown", "# hi".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/framework/upload").file(readme))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_acceptsAllowedAndStartsJob_returnsJobId() throws Exception {
        // mix of allowed files; must include at least one .java or .kt
        MockMultipartFile src = new MockMultipartFile(
                "files",
                "src/main/java/com/acme/demo/Thing.java",
                "text/x-java-source",
                ("package com.acme.demo;\n" +
                        "public class Thing { public void x(){} }").getBytes(StandardCharsets.UTF_8));

        MockMultipartFile props = new MockMultipartFile(
                "files", "src/main/resources/app.properties", "text/plain",
                "k=v".getBytes(StandardCharsets.UTF_8));

        when(jobs.start(eq("FRAMEWORK"), eq(2))).thenReturn("job-u1");
        when(useCase.ingest(any(String[].class))).thenReturn(7); // async success
        doNothing().when(jobs).done(eq("job-u1"), anyString());
        doNothing().when(jobs).fail(eq("job-u1"), anyString());

        mvc.perform(multipart("/framework/upload")
                        .file(src)
                        .file(props))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId", equalTo("job-u1")));

        Mockito.verify(jobs).start("FRAMEWORK", 2);
    }

    // ---------- /framework/upload-jar ----------

    @Test
    void uploadJar_400_whenNoJar() throws Exception {
        mvc.perform(multipart("/framework/upload-jar")
                        .param("pkg", "com.acme"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadJar_415_whenWrongExtension() throws Exception {
        MockMultipartFile notJar = new MockMultipartFile(
                "jar", "lib.txt", "text/plain", "nope".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/framework/upload-jar")
                        .file(notJar)
                        .param("pkg", "com.acme"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadJar_400_whenNoPkgs() throws Exception {
        MockMultipartFile jar = new MockMultipartFile(
                "jar", "lib.jar", "application/java-archive", new byte[]{0x50, 0x4B, 0x03, 0x04}); // zip header ok

        mvc.perform(multipart("/framework/upload-jar").file(jar))
                .andExpect(status().isBadRequest());
    }

}
