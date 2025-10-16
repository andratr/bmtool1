package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import org.learningjava.bmtool1.application.usecase.FrameworkIngestionUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/framework")
public class FrameworkAdminController {

    // Allow-list for framework uploads (PDFs explicitly rejected below)
    private static final Set<String> ALLOWED_EXT = Set.of(
            "java", "kt", "xml", "properties", "txt", "md", "json", "yaml", "yml"
    );
    private final FrameworkIngestionUseCase useCase;
    private final JobRegistry jobs;

    public FrameworkAdminController(FrameworkIngestionUseCase useCase, JobRegistry jobs) {
        this.useCase = useCase;
        this.jobs = jobs;
    }

    /**
     * Existing path-based ingest
     */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestParam("pkg") List<String> pkgs) {
        if (pkgs == null || pkgs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one package must be provided");
        }

        String jobId = jobs.start("FRAMEWORK", pkgs.size());
        CompletableFuture.runAsync(() -> {
            try {
                int n = useCase.ingest(pkgs.toArray(String[]::new));
                if (n <= 0) {
                    jobs.fail(jobId, "No framework symbols ingested for packages: " + pkgs);
                } else {
                    jobs.done(jobId, "Ingested " + n + " framework symbols from " + pkgs.size() + " package(s).");
                }
            } catch (Exception e) {
                jobs.fail(jobId, e.getMessage());
            }
        });
        return Map.of("jobId", jobId);
    }

    /**
     * New: browser folder upload → temp dir → discover packages → ingest
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadFramework(@RequestParam("files") List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files provided");
        }

        // Validate extensions and require at least one Java/Kotlin source
        validateFiles(files);

        Path tempDir = Files.createTempDirectory("framework-upload-");
        saveUploadedFiles(files, tempDir);

        String jobId = jobs.start("FRAMEWORK", files.size());
        CompletableFuture.runAsync(() -> {
            try {
                // 1) Discover package names from uploaded sources
                Set<String> pkgs = discoverPackages(tempDir);

                if (pkgs.isEmpty()) {
                    jobs.fail(jobId, "No package declarations found in uploaded sources.");
                    return;
                }

                // 2) Ingest by discovered packages
                int n = useCase.ingest(pkgs.toArray(String[]::new));
                if (n <= 0) {
                    jobs.fail(jobId, "No framework symbols ingested from discovered packages: " + pkgs);
                } else {
                    jobs.done(jobId, "Ingested " + n + " framework symbols from " + pkgs.size() + " package(s).");
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

    @GetMapping("/ping")
    public String ping() {
        return "PIIING!";
    }

    // ---------- helpers ----------

    private void validateFiles(List<MultipartFile> files) {
        boolean hasSource = false;

        for (MultipartFile f : files) {
            String name = f.getOriginalFilename();
            if (name == null || name.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A file has no name");
            }
            String lower = name.toLowerCase(Locale.ROOT);
            String ext = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "";

            if ("pdf".equals(ext)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDFs are not allowed: " + name);
            }
            if (!ALLOWED_EXT.contains(ext)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File type not allowed: " + name);
            }
            if (ext.equals("java") || ext.equals("kt")) {
                hasSource = true;
            }
        }

        if (!hasSource) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Upload must include at least one Java/Kotlin source file (.java or .kt)"
            );
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
     * Extract distinct Java/Kotlin package names from uploaded sources.
     */
    private Set<String> discoverPackages(Path root) throws IOException {
        final Set<String> pkgs = new HashSet<>();

        try (var paths = Files.walk(root)) {
            List<Path> sourceFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".java") || name.endsWith(".kt");
                    })
                    .collect(Collectors.toList());

            for (Path p : sourceFiles) {
                String pkg = readFirstPackageDecl(p);
                if (pkg != null && !pkg.isBlank()) pkgs.add(pkg);
            }
        }
        return pkgs;
    }

    /**
     * Read the first 'package ...;' line from a source file (fast scan).
     */
    private String readFirstPackageDecl(Path source) {
        try (var in = Files.newInputStream(source);
             var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lines = 0;
            while ((line = r.readLine()) != null && lines++ < 200) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
                    continue;
                }
                if (line.startsWith("package ")) {
                    int semi = line.indexOf(';');
                    String decl = (semi >= 0) ? line.substring(8, semi) : line.substring(8);
                    return decl.trim();
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * Recursive delete helper.
     */
    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
