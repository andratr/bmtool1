package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import org.learningjava.bmtool1.application.usecase.IngestPairsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/rag")
public class RagAdminController {

    private static final Logger log = LoggerFactory.getLogger(RagAdminController.class);

    // Only types that can form SQL–Java pairs
    private static final Set<String> ALLOWED_EXT = Set.of("sql", "plsql", "pkb", "pks", "java");

    private final IngestPairsUseCase ingest;
    private final JobRegistry jobs;
    private final Executor executor;

    public RagAdminController(IngestPairsUseCase ingest,
                              JobRegistry jobs,
                              @Qualifier("applicationTaskExecutor") Executor executor) {
        this.ingest = ingest;
        this.jobs = jobs;
        this.executor = executor;
    }

    // --- Ingest by server/container directory path
    @PostMapping("/ingest")
    public Map<String, Object> ingestDirectory(@RequestParam String rootDir) {
        String jobId = jobs.start("RAG", 0);

        if (rootDir == null || rootDir.isBlank()) {
            jobs.fail(jobId, "rootDir is blank");
            return Map.of("jobId", jobId);
        }
        Path dir = Path.of(rootDir);
        if (!Files.isDirectory(dir)) {
            jobs.fail(jobId, "Directory not found: " + rootDir);
            return Map.of("jobId", jobId);
        }

        jobs.update(jobId, 0, "Scanning: " + rootDir);

        executor.execute(() -> {
            try {
                log.info("[{}] Ingest start: {}", jobId, rootDir);
                var mappings = ingest.ingestDirectory(rootDir);

                int count = (mappings == null) ? 0 : mappings.size();
                if (count == 0) {
                    jobs.fail(jobId, "No SQL–Java pairs found in " + rootDir);
                    log.warn("[{}] No pairs found in {}", jobId, rootDir);
                } else {
                    jobs.done(jobId, "Ingested " + count + " mappings");
                    log.info("[{}] Ingest done: {} mappings", jobId, count);
                }
            } catch (Exception e) {
                jobs.fail(jobId, e.getMessage());
                log.error("[{}] Ingest failed: {}", jobId, e.toString(), e);
            }
        });

        return Map.of("jobId", jobId);
    }

    // --- Browser folder upload → temp dir → ingest
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAndIngest(
            @RequestParam(value = "files", required = false) List<MultipartFile> files) throws IOException {

        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files provided");
        }

        validateFiles(files);

        Path tempDir = Files.createTempDirectory("rag-upload-");
        saveUploadedFiles(files, tempDir);

        String jobId = jobs.start("RAG", files.size());
        jobs.update(jobId, 0, "Uploaded " + files.size() + " files; ingesting…");

        executor.execute(() -> {
            try {
                log.info("[{}] Upload ingest start: {}", jobId, tempDir);
                var mappings = ingest.ingestDirectory(tempDir.toString());

                int count = (mappings == null) ? 0 : mappings.size();
                if (count == 0) {
                    jobs.fail(jobId, "No SQL–Java pairs found in upload");
                    log.warn("[{}] No pairs from upload {}", jobId, tempDir);
                } else {
                    jobs.done(jobId, "Ingested " + count + " mappings");
                    log.info("[{}] Upload ingest done: {} mappings", jobId, count);
                }
            } catch (Exception e) {
                jobs.fail(jobId, e.getMessage());
                log.error("[{}] Upload ingest failed: {}", jobId, e.toString(), e);
            } finally {
                try {
                    deleteRecursively(tempDir);
                    log.info("[{}] Cleaned {}", jobId, tempDir);
                } catch (Exception cleanup) {
                    log.warn("[{}] Cleanup failed for {}: {}", jobId, tempDir, cleanup.toString());
                }
            }
        });

        return Map.of("jobId", jobId);
    }

    @GetMapping("/jobs/{id}")
    public JobRegistry.JobStatus status(@PathVariable("id") String id) {
        return jobs.get(id);
    }

    // ---------- helpers ----------

    private void validateFiles(List<MultipartFile> files) {
        int plsqlCount = 0, javaCount = 0;

        for (MultipartFile f : files) {
            String name = f.getOriginalFilename();
            if (name == null || name.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A file has no name");
            }
            String lower = name.toLowerCase();
            String ext = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "";

            if (!ALLOWED_EXT.contains(ext)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "File type not allowed: " + name);
            }

            if (ext.equals("java")) javaCount++;
            if (ext.equals("sql") || ext.equals("plsql") || ext.equals("pkb") || ext.equals("pks")) plsqlCount++;
        }

        if (plsqlCount == 0 || javaCount == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upload must include at least one PL/SQL file (.sql/.plsql/.pkb/.pks) and one .java file");
        }
    }

    private void saveUploadedFiles(List<MultipartFile> files, Path tempDir) throws IOException {
        for (MultipartFile file : files) {
            String rel = file.getOriginalFilename();
            if (rel == null || rel.isBlank()) continue;
            Path dest = tempDir.resolve(rel).normalize();
            if (!dest.startsWith(tempDir)) {
                throw new SecurityException("Invalid path segment in upload: " + rel);
            }
            Files.createDirectories(dest.getParent());
            file.transferTo(dest.toFile());
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }
}
