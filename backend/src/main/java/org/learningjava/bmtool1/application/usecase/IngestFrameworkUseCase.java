package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.FrameworkStorePort;
import org.learningjava.bmtool1.domain.model.framework.FrameworkSymbol;
import org.learningjava.bmtool1.infrastructure.adapter.out.FrameworkSymbolExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;

@Service
public class IngestFrameworkUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestFrameworkUseCase.class);
    private static final boolean EXTRA = Boolean.getBoolean("bmtool.framework.debug");

    private final FrameworkStorePort store;
    private final EmbeddingPort embedding;
    private final FrameworkSymbolExtractor extractor;

    public IngestFrameworkUseCase(FrameworkStorePort store,
                                  EmbeddingPort embedding,
                                  FrameworkSymbolExtractor extractor) {
        this.store = store;
        this.embedding = embedding;
        this.extractor = extractor;
    }

    // ------------------- public API (classpath-based) -------------------

    /** Scan using the current thread context ClassLoader (whatever is already on the app classpath). */
    public int ingest(String... basePackages) {
        final boolean includeAll = includeAll(basePackages);
        final String[] prefixes = normalizeBasePackages(basePackages);

        log.debug("Ingest: start basePackages={} (includeAll={})",
                Arrays.toString(prefixes), includeAll);

        store.ensureSchema();

        List<FrameworkSymbol> symbols = dedupe(
                extractor.extract(prefixes)
        );

        int n = symbols.size();
        log.debug("Ingest: extractor produced {} symbols", n);
        preview(symbols);

        if (n <= 0) {
            logZeroResultHint("Ingest", prefixes);
            return 0;
        }

        List<float[]> vectors = symbols.stream()
                .map(s -> embedding.embed(safeJoin(s)))
                .toList();
        log.debug("Ingest: built {} embedding vectors", vectors.size());

        store.upsertSymbols(symbols, vectors);
        log.debug("Ingest: upserted {} symbols", n);
        return n;
    }

    public int ingestWithClassLoader(ClassLoader cl, String... basePackages) {
        final boolean includeAll = includeAll(basePackages);
        final String[] prefixes = normalizeBasePackages(basePackages);

        log.debug("Ingest(CL): start basePackages={} (includeAll={})",
                Arrays.toString(prefixes), includeAll);

        if (cl instanceof URLClassLoader ucl) {
            log.debug("Ingest(CL): URLClassLoader URLs={}", Arrays.toString(ucl.getURLs()));
        } else {
            log.debug("Ingest(CL): cl={}", cl.getClass().getName());
        }

        store.ensureSchema();

        List<FrameworkSymbol> symbols = dedupe(
                extractor.extractWithClassLoader(cl, prefixes)
        );

        int n = symbols.size();
        log.debug("Ingest(CL): extractor produced {} symbols", n);
        preview(symbols);

        if (n <= 0) {
            if (cl instanceof URLClassLoader u) {
                log.warn("Ingest(CL): URLClassLoader URLs: {}", Arrays.toString(u.getURLs()));
            }
            logZeroResultHint("Ingest(CL)", prefixes);
            return 0;
        }

        List<float[]> vectors = symbols.stream()
                .map(s -> embedding.embed(safeJoin(s)))
                .toList();
        log.debug("Ingest(CL): built {} embedding vectors", vectors.size());

        store.upsertSymbols(symbols, vectors);
        log.debug("Ingest(CL): upserted {} symbols", n);
        return n;
    }

    public int ingestFromClassesDir(Path classesDir, String... basePackages) {
        Objects.requireNonNull(classesDir, "classesDir");

        final boolean includeAll = includeAll(basePackages);
        final String[] prefixes = normalizeBasePackages(basePackages);

        log.debug("Ingest(CLASSES_DIR): root={}, basePackages={} (includeAll={})",
                classesDir, Arrays.toString(prefixes), includeAll);

        try (URLClassLoader cl = new URLClassLoader(
                new URL[]{ classesDir.toUri().toURL() },
                IngestFrameworkUseCase.class.getClassLoader()
        )) {
            if (EXTRA) log.debug("Ingest(CLASSES_DIR): URLS={}", Arrays.toString(cl.getURLs()));
            return ingestWithClassLoader(cl, prefixes);
        } catch (Exception e) {
            log.error("Ingest(CLASSES_DIR): error {}", e.toString(), e);
            throw new RuntimeException(e);
        }
    }

    // ------------------- helpers -------------------

    private static boolean includeAll(String... basePackages) {
        if (basePackages == null || basePackages.length == 0) return true;
        for (String p : basePackages) {
            if (p != null && !p.isBlank()) return false;
        }
        return true;
    }

    private static String[] normalizeBasePackages(String... basePackages) {
        if (includeAll(basePackages)) return new String[0];
        return Arrays.stream(basePackages)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private static String safeJoin(FrameworkSymbol s) {
        String a = s.symbol() == null ? "" : s.symbol();
        String b = s.methodSignature() == null ? "" : s.methodSignature();
        String c = s.snippet() == null ? "" : s.snippet();
        return (a + " " + b + " " + c).trim();
    }

    private static List<FrameworkSymbol> dedupe(List<FrameworkSymbol> in) {
        if (in == null || in.isEmpty()) return List.of();
        record Key(String cls, String sym, String sig) {}
        Map<Key, FrameworkSymbol> map = new LinkedHashMap<>();
        for (FrameworkSymbol s : in) {
            Key k = new Key(s.className(), s.symbol(), s.methodSignature());
            map.putIfAbsent(k, s);
        }
        return new ArrayList<>(map.values());
    }

    private void logZeroResultHint(String where, String... basePackages) {
        log.warn("{}: 0 symbols. Hints:\n" +
                        "  1) Do the compiled classes land under the provided base packages? basePackages={}\n" +
                        "  2) Ensure sources actually compiled (check compile diagnostics).\n" +
                        "  3) Ensure FrameworkSymbolExtractor filters by ANY provided base package (no hard-coded prefix).\n" +
                        "  4) If classes need extra dependencies at scan time, put them on the parent classloader.",
                where, Arrays.toString(basePackages));
    }

    private void preview(List<FrameworkSymbol> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.debug("Preview: no symbols.");
            return;
        }
        int limit = Math.min(5, symbols.size());
        for (int i = 0; i < limit; i++) {
            FrameworkSymbol s = symbols.get(i);
            log.debug("  [{}] {} | {} | {} | kind={} | tags={}",
                    i,
                    s.className(), s.symbol(), s.methodSignature(), s.kind(), s.tags());
        }
        if (symbols.size() > limit) {
            log.debug("  ... ({} more symbols)", symbols.size() - limit);
        }
    }
}
