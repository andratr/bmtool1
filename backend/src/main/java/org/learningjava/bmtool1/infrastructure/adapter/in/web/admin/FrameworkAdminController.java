package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import org.learningjava.bmtool1.application.usecase.IngestFrameworkUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/framework")
public class FrameworkAdminController {

    private static final Set<String> BLOCKED_EXT = Set.of("pdf");
    private static final boolean EXTRA = Boolean.getBoolean("bmtool.framework.debug");
    private static final String DEFAULT_JAVA_RELEASE =
            System.getProperty("bmtool.upload.javaRelease", "21");

    private final IngestFrameworkUseCase useCase;
    private final JobRegistry jobs;

    public FrameworkAdminController(IngestFrameworkUseCase useCase, JobRegistry jobs) {
        this.useCase = useCase;
        this.jobs = jobs;
    }

    // ----------------------------- endpoints -----------------------------

    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestParam("pkg") List<String> pkgs) {
        if (pkgs == null || pkgs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one package must be provided");
        }
        String jobId = jobs.start("FRAMEWORK", pkgs.size());
        CompletableFuture.runAsync(() -> {
            try {
                int n = useCase.ingest(pkgs.toArray(String[]::new));
                if (n <= 0) jobs.fail(jobId, "No framework symbols ingested for packages: " + pkgs);
                else jobs.done(jobId, "Ingested " + n + " framework symbols from " + pkgs.size() + " package(s).");
            } catch (Exception e) {
                jobs.fail(jobId, e.getMessage());
            }
        });
        return Map.of("jobId", jobId);
    }


    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadFramework(@RequestParam("files") List<MultipartFile> files,
                                               @RequestParam(value = "debug", required = false, defaultValue = "false") boolean debug) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files provided");
        }
        validateFiles(files);

        Path tempDir = Files.createTempDirectory("framework-upload-");
        saveUploadedFiles(files, tempDir); // includes sniffing .txt/no-ext → *.java copy

        String jobId = jobs.start("FRAMEWORK", files.size());
        CompletableFuture.runAsync(() -> {
            DebugLog dbg = new DebugLog(debug);
            try {
                dbg.log("== /framework/upload run at %s ==", LocalDateTime.now());
                dbg.log("Saved upload into %s", tempDir);
                dbg.log("Java runtime: java.version=%s, vendor=%s", System.getProperty("java.version"), System.getProperty("java.vendor"));
                dbg.log("OS: %s %s (%s)", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
                dbg.log("Requested --release: %s", DEFAULT_JAVA_RELEASE);

                Set<String> pkgs = discoverPackages(tempDir);
                dbg.log("Discovered %d packages: %s", pkgs.size(), pkgs);


                boolean includeAll = pkgs.isEmpty();
                if (includeAll) {
                    dbg.log("No package declarations found; proceeding with default (unnamed) package.");
                }

                Path classesDir = tempDir.resolve("classes");
                Files.createDirectories(classesDir);
                List<Path> javaFiles = collectJavaSources(tempDir);
                dbg.log("Java files considered for compilation: %d", javaFiles.size());
                if (javaFiles.isEmpty()) {
                    jobs.fail(jobId, dbg.finish("No Java-like sources found after upload/sniffing."));
                    return;
                }

                // Summarize a few file names to help the user see what actually got picked up
                List<String> firstFiles = javaFiles.stream()
                        .map(tempDir::relativize)
                        .map(Path::toString)
                        .limit(10)
                        .toList();
                dbg.log("First files: %s", firstFiles);

                CompileResult cr = compileJavaSources(javaFiles, classesDir, dbg);
                if (!cr.ok) {
                    String msg = buildCompileFailureMessage(cr, debug);
                    jobs.fail(jobId, dbg.finish(msg));
                    return;
                }

                String[] basePkgs = includeAll ? new String[0] : pkgs.toArray(String[]::new);
                int n = useCase.ingestFromClassesDir(classesDir, basePkgs);
                dbg.log("useCase.ingestFromClassesDir returned: %d", n);

                if (n <= 0) {
                    String msg = "No framework symbols ingested from compiled classes.\n" +
                            "- Check base packages: " + (includeAll ? "(all packages, including default)" : pkgs) + "\n" +
                            "- Ensure your classes expose public methods/constructors.\n" +
                            (dbg.enabled ? ("Debug: " + dbg.path) : "");
                    jobs.fail(jobId, dbg.finish(msg));
                } else {
                    String msg = "Ingested " + n + " framework symbols from " +
                            (includeAll ? "all packages" : (pkgs.size() + " package(s)")) + "." +
                            (dbg.enabled ? (" Debug: " + dbg.path) : "");
                    jobs.done(jobId, dbg.finish(msg));
                }
            } catch (Exception e) {
                jobs.fail(jobId, dbg.finish("ERROR: " + e.getClass().getName() + ": " + e.getMessage()));
            } finally {
                try { deleteRecursively(tempDir); } catch (Exception ignored) {}
            }
        });

        return Map.of("jobId", jobId);
    }

    @GetMapping("/jobs/{id}")
    public JobRegistry.JobStatus status(@PathVariable("id") String id) {
        return jobs.get(id);
    }

    @GetMapping("/ping")
    public String ping() { return "PIIING!"; }

    // ----------------------------- validation & saving -----------------------------

    private void validateFiles(List<MultipartFile> files) {
        boolean hasTextLike = false;
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename();
            if (name == null || name.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A file has no name");
            }
            if (isIgnoredMetaName(name)) continue;

            String lower = name.replace('\\', '/').toLowerCase(Locale.ROOT);
            String base  = lower.substring(lower.lastIndexOf('/') + 1);
            String ext   = base.contains(".") ? base.substring(base.lastIndexOf('.') + 1) : "";

            if (BLOCKED_EXT.contains(ext)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File type not allowed: " + name);
            }
            if (!base.endsWith(".class") && !base.endsWith(".jar")) hasTextLike = true;
        }
        if (!hasTextLike) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upload must include at least one text-like source (java/txt/no extension).");
        }
    }

    private void saveUploadedFiles(List<MultipartFile> files, Path tempDir) throws IOException {
        for (MultipartFile file : files) {
            String rel = file.getOriginalFilename();
            if (rel == null || rel.isBlank()) continue;
            if (isIgnoredMetaName(rel)) continue;

            Path dest = tempDir.resolve(rel).normalize();
            if (!dest.startsWith(tempDir)) {
                throw new SecurityException("Invalid path segment in upload: " + rel);
            }
            Path parent = dest.getParent();
            if (parent != null) Files.createDirectories(parent);

            file.transferTo(dest.toFile());

            String name = dest.getFileName().toString();
            String lower = name.toLowerCase(Locale.ROOT);
            boolean noExt  = !name.contains(".");
            boolean txtExt = lower.endsWith(".txt");
            if ((noExt || txtExt) && looksLikeJavaSource(dest)) {
                String javaName = noExt ? (name + ".java") : (name.substring(0, name.length() - 4) + ".java");
                Path javaCopy = dest.resolveSibling(javaName);
                Files.copy(dest, javaCopy, StandardCopyOption.REPLACE_EXISTING);
                if (EXTRA) System.out.println("[upload] created temporary Java copy: " + javaCopy);
            }
        }
    }

    // ----------------------------- discovery & compilation -----------------------------

    private Set<String> discoverPackages(Path root) throws IOException {
        final Set<String> pkgs = new HashSet<>();
        try (var paths = Files.walk(root)) {
            List<Path> sourceFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> !isMetaPath(p))
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".java") || looksLikeJavaSource(p);
                    })
                    .collect(Collectors.toList());
            for (Path p : sourceFiles) {
                String pkg = readFirstPackageDecl(p);
                if (pkg != null && !pkg.isBlank()) pkgs.add(pkg);
            }
        }
        return pkgs;
    }

    private List<Path> collectJavaSources(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> !isMetaPath(p))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private static final class CompileResult {
        final boolean ok;
        final List<String> diagnosticsTop;
        final int diagCount;
        final int filesCount;
        final String javaRelease;
        final String javaVersion;
        final String classpathSummary;
        final List<String> pickedFilesTop;

        CompileResult(boolean ok, List<String> diagnosticsTop, int diagCount,
                      int filesCount, String javaRelease, String javaVersion,
                      String classpathSummary, List<String> pickedFilesTop) {
            this.ok = ok;
            this.diagnosticsTop = diagnosticsTop;
            this.diagCount = diagCount;
            this.filesCount = filesCount;
            this.javaRelease = javaRelease;
            this.javaVersion = javaVersion;
            this.classpathSummary = classpathSummary;
            this.pickedFilesTop = pickedFilesTop;
        }
    }

    private CompileResult compileJavaSources(List<Path> javaFiles, Path outDir, DebugLog dbg) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No system Java compiler available. Run the server with a JDK, not a JRE.");
        }
        dbg.log("Compiling %d Java files → %s", javaFiles.size(), outDir);

        DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<>();
        List<String> options = new ArrayList<>(List.of(
                "-d", outDir.toString(),
                "--release", DEFAULT_JAVA_RELEASE,
                "-parameters"
        ));

        String cp = "";
        try {
            cp = buildUploadClasspath(outDir.getParent(), dbg);
            if (!cp.isBlank()) {
                options.add("-classpath");
                options.add(cp);
            }
        } catch (IOException e) {
            dbg.log("WARN: building classpath failed: %s", e.getMessage());
        }

        String cpSummary = summarizeClasspath(cp);

        List<String> firstFiles = javaFiles.stream()
                .map(outDir.getParent()::relativize)
                .map(Path::toString)
                .limit(8).toList();

        boolean ok;
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(dc, null, StandardCharsets.UTF_8)) {
            var units = fm.getJavaFileObjectsFromFiles(javaFiles.stream().map(Path::toFile).collect(Collectors.toList()));
            ok = Boolean.TRUE.equals(compiler.getTask(null, fm, dc, options, null, units).call());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var diags = dc.getDiagnostics();
        if (!diags.isEmpty()) {
            int shown = 0, MAX = 50;
            dbg.log("javac produced %d diagnostics; showing up to %d:", diags.size(), MAX);
            for (var d : diags) {
                dbg.log("[%s] %s:%s:%s %s",
                        d.getKind(),
                        d.getSource() != null ? d.getSource().getName() : "<no-source>",
                        d.getLineNumber(),
                        d.getColumnNumber(),
                        d.getMessage(Locale.ROOT));
                if (++shown >= MAX) break;
            }
        }
        long missing = diags.stream().filter(x -> {
            String m = x.getMessage(Locale.ROOT).toLowerCase(Locale.ROOT);
            return m.contains("cannot find symbol") || (m.contains("package") && m.contains("does not exist"));
        }).count();
        if (missing > 0) {
            dbg.log("HINT: %d missing-type errors — include required dependency JARs under a 'lib/' folder in your upload.", missing);
        }

        List<String> diagnosticsTop = diags.stream()
                .limit(8)
                .map(d -> String.format(Locale.ROOT, "[%s] %s:%d:%d %s",
                        d.getKind(),
                        d.getSource() != null ? new File(d.getSource().getName()).getName() : "<no-source>",
                        d.getLineNumber(),
                        d.getColumnNumber(),
                        d.getMessage(Locale.ROOT).replace('\n', ' ')
                ))
                .toList();

        dbg.log("Compilation status: %s", ok);

        return new CompileResult(
                ok,
                diagnosticsTop,
                diags.size(),
                javaFiles.size(),
                DEFAULT_JAVA_RELEASE,
                System.getProperty("java.version"),
                cpSummary,
                firstFiles
        );
    }

    private static String summarizeClasspath(String cp) {
        if (cp == null || cp.isBlank()) return "(empty)";
        String sep = System.getProperty("path.separator");
        String[] parts = cp.split(Pattern.quote(sep));
        int total = parts.length;
        List<String> shortList = Arrays.stream(parts)
                .map(p -> {
                    String name = p;
                    int slash = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
                    if (slash >= 0 && slash + 1 < p.length()) name = p.substring(slash + 1);
                    return name;
                })
                .limit(6).toList();
        return total + " entries (e.g. " + String.join(", ", shortList) + (total > 6 ? ", ..." : "") + ")";
    }

    private static String buildUploadClasspath(Path tempDir, DebugLog dbg) throws IOException {
        String serverCp = System.getProperty("java.class.path", "");
        List<String> parts = new ArrayList<>();
        if (!serverCp.isBlank()) parts.add(serverCp);

        Path libDir = tempDir.resolve("lib");
        if (Files.isDirectory(libDir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(libDir, "*.jar")) {
                for (Path p : ds) parts.add(p.toAbsolutePath().toString());
            }
        }

        String cp = String.join(System.getProperty("path.separator"), parts);
        if (dbg.enabled) dbg.log("javac classpath entries: %d", parts.size());
        return cp;
    }

    // ----------------------------- utilities -----------------------------

    private boolean looksLikeJavaSource(Path p) {
        String fn = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fn.endsWith(".class") || fn.endsWith(".jar")) return false;
        try (var in = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line; int lines = 0;
            while ((line = in.readLine()) != null && lines++ < 400) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("//") || s.startsWith("/*") || s.startsWith("*")) continue;
                if (TYPE_PATTERN.matcher(s).find()) return true; // accept default package
            }
        } catch (IOException ignored) {}
        return false;
    }

    private static final Pattern TYPE_PATTERN =
            Pattern.compile("\\b(class|interface|enum|record)\\b\\s+[A-Za-z_][A-Za-z0-9_]*");

    private String readFirstPackageDecl(Path source) {
        try {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            var m = Pattern.compile("^\\s*package\\s+([A-Za-z_]\\w*(?:\\.\\w+)*)", Pattern.MULTILINE).matcher(content);
            return m.find() ? m.group(1) : null;
        } catch (IOException ignored) { }
        return null;
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    private boolean isMetaPath(Path p) {
        for (Path part : p) {
            String s = part.toString().toLowerCase(Locale.ROOT);
            if (s.startsWith(".") || s.equals("__macosx")) return true;
        }
        return false;
    }

    private static boolean isIgnoredMetaName(String originalName) {
        if (originalName == null || originalName.isBlank()) return true;
        String norm = originalName.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (norm.contains("/.") || norm.contains("__macosx/")) return true;
        int slash = norm.lastIndexOf('/');
        String base = (slash >= 0) ? norm.substring(slash + 1) : norm;
        if (base.isBlank()) return true;
        if (base.startsWith(".")) return true;
        if (base.equals("thumbs.db")) return true;
        if (base.equals("desktop.ini")) return true;
        return false;
    }

    // ----------------------------- debug/log helpers -----------------------------

    private static final class DebugLog {
        final boolean enabled;
        final Path path;
        final StringBuilder sb = new StringBuilder();

        DebugLog(boolean enabled) {
            this.enabled = enabled;
            this.path = enabled ? makeTemp() : null;
            if (enabled) log("Debug log: %s", path);
        }

        void log(String fmt, Object... args) {
            if (!enabled) return;
            sb.append(String.format(Locale.ROOT, fmt, args)).append('\n');
        }

        String finish(String finalMsg) {
            if (enabled) {
                try {
                    Files.writeString(path, sb.append("\n").append(finalMsg).append("\n").toString(), StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
                return finalMsg + " (debug in " + path + ")";
            }
            return finalMsg;
        }

        private Path makeTemp() {
            try {
                return Files.createTempFile("framework-upload-debug-", ".log");
            } catch (IOException e) {
                return Paths.get(System.getProperty("java.io.tmpdir"), "framework-upload-debug-" + UUID.randomUUID() + ".log");
            }
        }


    }

    private static String buildCompileFailureMessage(CompileResult cr, boolean debugEnabled) {
        StringBuilder sb = new StringBuilder();
        sb.append("Compilation failed for uploaded sources.\n");
        sb.append("- Files compiled: ").append(cr.filesCount).append('\n');
        sb.append("- Java --release: ").append(cr.javaRelease)
                .append(" (runtime: ").append(cr.javaVersion).append(")\n");
        sb.append("- Classpath: ").append(cr.classpathSummary).append('\n');

        if (cr.pickedFilesTop != null && !cr.pickedFilesTop.isEmpty()) {
            sb.append("- First files: ").append(String.join(", ", cr.pickedFilesTop)).append('\n');
        }

        if (cr.diagnosticsTop != null && !cr.diagnosticsTop.isEmpty()) {
            sb.append("\nTop diagnostics (").append(cr.diagCount).append(" total):\n");
            for (String d : cr.diagnosticsTop) {
                sb.append("  ").append(d).append('\n');
            }
        } else {
            sb.append("\nNo diagnostics captured from javac (unexpected). Ensure files are readable and UTF-8.\n");
        }

        if (!debugEnabled) {
            sb.append("\nTip: re-run with ?debug=true to get a debug log path with full diagnostics.");
        }
        return sb.toString();
    }

}
