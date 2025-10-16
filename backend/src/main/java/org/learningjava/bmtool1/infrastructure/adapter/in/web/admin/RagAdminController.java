package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import org.learningjava.bmtool1.application.usecase.IngestPairsUseCase;
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
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/rag")
public class RagAdminController {

    // Only types that can form SQL–Java pairs
    private static final Set<String> ALLOWED_EXT = Set.of("sql", "plsql", "pkb", "pks", "java");
    private final IngestPairsUseCase ingest;
    private final JobRegistry jobs;

    public RagAdminController(IngestPairsUseCase ingest, JobRegistry jobs) {
        this.ingest = ingest;
        this.jobs = jobs;
    }

    // --- Existing: ingest by server directory path
    @PostMapping("/ingest")
    public Map<String, Object> ingestDirectory(@RequestParam String rootDir) {
        String jobId = jobs.start("RAG", 0);
        CompletableFuture.runAsync(() -> {
            try {
                var mappings = ingest.ingestDirectory(rootDir);
                if (mappings == null || mappings.isEmpty()) {
                    jobs.fail(jobId, "No SQL–Java pairs found in " + rootDir);
                } else {
                    jobs.done(jobId, "Ingested " + mappings.size() + " mappings");
                }
            } catch (Exception e) {
                jobs.fail(jobId, e.getMessage());
            }
        });
        return Map.of("jobId", jobId);
    }

    // --- New: browser folder upload → temp dir → ingest
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAndIngest(@RequestParam("files") List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files provided");
        }

        // Validate set and require at least one PL/SQL and one Java file
        validateFiles(files);

        Path tempDir = Files.createTempDirectory("rag-upload-");
        saveUploadedFiles(files, tempDir);

        String jobId = jobs.start("RAG", files.size());
        CompletableFuture.runAsync(() -> {
            try {
                var mappings = ingest.ingestDirectory(tempDir.toString());

                if (mappings == null || mappings.isEmpty()) {
                    jobs.fail(jobId, "No SQL–Java pairs found in upload");
                } else {
                    jobs.done(jobId, "Ingested " + mappings.size() + " mappings");
                }
            } catch (Exception e) {
                jobs.fail(jobId, e.getMessage());
            } finally {
                try {
                    deleteRecursively(tempDir);
                } catch (Exception ignored) {
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

            if ("pdf".equals(ext)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDFs are not allowed: " + name);
            }
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

    /**
     * Persist uploaded files under tempDir, preserving subfolders from original filename (webkitRelativePath).
     */
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

    /**
     * Simple recursive deletion of a directory tree.
     */
    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
