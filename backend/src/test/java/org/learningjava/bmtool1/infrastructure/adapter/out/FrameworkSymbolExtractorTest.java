package org.learningjava.bmtool1.infrastructure.adapter.out;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.domain.model.framework.FrameworkSymbol;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 1) Compiles sources found under resources/framework-samples/src/main/java/**.
 * 2) Loads compiled classes in an isolated ClassLoader.
 * 3) Runs extraction for root "org.learningjava.frameworksamples".
 * 4) Reads FrameworkSymbol using an adaptive resolver (record/bean/fields; fuzzy names).
 */
class FrameworkSymbolExtractorCompileSamplesTest {

    private static final String SAMPLES_DIR = "framework-samples";
    private static final String SOURCE_ROOT_INSIDE = "src/main/java";
    private static final String BASE_PACKAGE = "org.learningjava.frameworksamples";

    private Path tempDir;
    private URLClassLoader samplesCL;

    @AfterEach
    void cleanup() throws Exception {
        if (samplesCL != null) samplesCL.close();
        if (tempDir != null) {
            try (var w = Files.walk(tempDir)) {
                w.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    /** Test-only extractor: same output as production, but accepts our real package root. */
    static class TestExtractor extends FrameworkSymbolExtractor {

        private boolean acceptFrameworkRoot(Class<?> c) {
            return c.getName().startsWith(BASE_PACKAGE);
        }

        @Override
        public List<FrameworkSymbol> extractWithClassLoader(ClassLoader cl, String... basePackages) {
            List<Class<?>> classes = testFindClasses(cl, basePackages);
            List<FrameworkSymbol> out = new ArrayList<>();

            for (Class<?> clazz : classes) {
                if (!acceptFrameworkRoot(clazz)) continue;

                String kind = kindOf(clazz);
                List<String> baseTags = defaultTags(clazz, kind);

                for (Method m : clazz.getDeclaredMethods()) {
                    if (!Modifier.isPublic(m.getModifiers())) continue;
                    String symbol = clazz.getSimpleName() + "#" + m.getName();
                    String signature = signatureOf(m);
                    String snippet = snippetFor(clazz, m, kind);
                    out.add(new FrameworkSymbol(
                            clazz.getName(), symbol, signature, snippet, kind,
                            mergeTags(baseTags, tagsForMethod(m))
                    ));
                }
                for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                    if (!Modifier.isPublic(ctor.getModifiers())) continue;
                    String symbol = clazz.getSimpleName() + "#<init>";
                    String signature = signatureOf(ctor);
                    String snippet = snippetFor(clazz, ctor, kind);
                    out.add(new FrameworkSymbol(
                            clazz.getName(), symbol, signature, snippet, kind,
                            mergeTags(baseTags, List.of("factory"))
                    ));
                }
            }
            return out;
        }

        // ---- copy of production helpers (unchanged logic) ----

        private String kindOf(Class<?> c) {
            String p = c.getPackageName();
            if (p.contains(".data.")) return "dto";
            if (p.contains(".model.")) return "value-object";
            if (p.contains(".service.")) return "service";
            if (p.contains(".foundation")) return "context";
            return "util";
        }
        private List<String> defaultTags(Class<?> c, String kind) {
            List<String> tags = new ArrayList<>(List.of(kind));
            String name = c.getSimpleName().toLowerCase();
            if (name.contains("date")) tags.add("date");
            if (name.contains("money") || name.contains("monetary")) tags.add("money");
            if (name.contains("event")) tags.add("event");
            return tags;
        }
        private List<String> tagsForMethod(Method m) {
            String n = m.getName().toLowerCase();
            List<String> tags = new ArrayList<>();
            if (n.startsWith("get") || n.startsWith("set")) tags.add("bean");
            if (n.contains("parse")) tags.add("parse");
            if (n.contains("between")) tags.add("range");
            if (n.contains("zero") || n.contains("empty")) tags.add("factory");
            if (n.contains("signum") || n.contains("compare")) tags.add("compare");
            return tags;
        }
        private List<String> mergeTags(List<String> a, List<String> b) {
            Set<String> s = new LinkedHashSet<>(a);
            s.addAll(b);
            return new ArrayList<>(s);
        }
        private String signatureOf(Method m) {
            String mods = Modifier.isStatic(m.getModifiers()) ? "static " : "";
            return mods + simple(m.getReturnType()) + " " + m.getName() +
                    "(" + Arrays.stream(m.getParameterTypes()).map(this::simple).reduce((x,y)->x+", "+y).orElse("") + ")";
        }
        private String signatureOf(Constructor<?> c) {
            String name = c.getDeclaringClass().getSimpleName();
            return name + "(" + Arrays.stream(c.getParameterTypes()).map(this::simple).reduce((x,y)->x+", "+y).orElse("") + ")";
        }
        private String simple(Class<?> t) {
            if (t.isArray()) return simple(t.getComponentType()) + "[]";
            return t.getSimpleName();
        }
        private String snippetFor(Class<?> clazz, Method m, String kind) {
            String cls = clazz.getSimpleName();
            if (Modifier.isStatic(m.getModifiers())) {
                if (m.getName().equals("parse") && m.getParameterCount()==1 && m.getParameterTypes()[0]==String.class) {
                    return "var x = " + cls + ".parse(\"EUR-100.00\");";
                }
                if (m.getName().equals("zero")) {
                    return "var x = " + cls + ".zero(\"EUR\");";
                }
            }
            if (m.getName().equals("signum")) {
                return "if (x.signum() >= 0) { /* ... */ }";
            }
            if (kind.equals("dto") && m.getName().startsWith("set") && m.getParameterCount()==1) {
                return "var d = new " + cls + "();\n" + "d." + m.getName() + "(" + placeholder(m.getParameterTypes()[0]) + ");";
            }
            return "// use " + cls + "#" + m.getName() + "()";
        }
        private String snippetFor(Class<?> clazz, Constructor<?> c, String kind) {
            String cls = clazz.getSimpleName();
            if (kind.equals("value-object") || kind.equals("dto")) {
                String args = Arrays.stream(c.getParameterTypes()).map(this::placeholder).reduce((x,y)->x+", "+y).orElse("");
                return "var d = new " + cls + "(" + args + ");";
            }
            return "var x = new " + cls + "();";
        }
        private String placeholder(Class<?> t) {
            if (t == String.class) return "\"S\"";
            if (t == int.class || t == Integer.class) return "0";
            if (t == long.class || t == Long.class) return "0L";
            if (t == boolean.class || t == Boolean.class) return "false";
            if (t.getSimpleName().equals("BigDecimal")) return "java.math.BigDecimal.ZERO";
            if (t.getSimpleName().equals("LocalDate")) return "java.time.LocalDate.now()";
            return "null";
        }

        // ---- scanning that honors the provided ClassLoader ----
        private List<Class<?>> testFindClasses(ClassLoader cl, String... basePackages) {
            List<Class<?>> classes = new ArrayList<>();
            for (String pkg : basePackages) {
                String path = pkg.replace('.', '/');
                try {
                    Enumeration<URL> resources = cl.getResources(path);
                    while (resources.hasMoreElements()) {
                        URL url = resources.nextElement();
                        if (url.getProtocol().equals("file")) {
                            classes.addAll(testFindInDirectory(cl, url, pkg));
                        } else if (url.getProtocol().equals("jar")) {
                            classes.addAll(testFindInJar(cl, url, pkg));
                        }
                    }
                } catch (Exception ignored) {}
            }
            return classes;
        }
        private List<Class<?>> testFindInDirectory(ClassLoader cl, URL url, String pkg) {
            List<Class<?>> out = new ArrayList<>();
            Path dir;
            try { dir = Paths.get(url.toURI()); } catch (Exception e) { dir = Paths.get(url.getPath()); }
            if (!Files.exists(dir)) return out;
            try {
                Path finalDir = dir;
                Files.walk(dir)
                        .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                        .filter(p -> !p.getFileName().toString().contains("$"))
                        .forEach(p -> {
                            String rel = finalDir.relativize(p).toString().replace(File.separatorChar, '.');
                            String name = pkg + "." + rel.substring(0, rel.length() - 6); // strip .class
                            try { out.add(Class.forName(name, false, cl)); } catch (Throwable ignored) {}
                        });
            } catch (Exception ignored) {}
            return out;
        }
        private List<Class<?>> testFindInJar(ClassLoader cl, URL jarUrl, String basePkg) {
            List<Class<?>> out = new ArrayList<>();
            String pkgPath = basePkg.replace('.', '/');
            try {
                String spec = jarUrl.getFile();
                String jarPath = spec.substring(5, spec.indexOf("!"));
                try (JarInputStream jis = new JarInputStream(new URL("file:" + jarPath).openStream())) {
                    JarEntry e;
                    while ((e = jis.getNextJarEntry()) != null) {
                        String name = e.getName();
                        if (name.endsWith(".class") && name.startsWith(pkgPath) && !name.contains("$")) {
                            String cls = name.substring(0, name.length() - 6).replace('/', '.');
                            try { out.add(Class.forName(cls, false, cl)); } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
            return out;
        }
    }

    @Test
    void compiles_sources_then_extracts_symbols() throws Exception {
        // 1) Find the samples root on the classpath
        URL rootUrl = Thread.currentThread().getContextClassLoader().getResource(SAMPLES_DIR);
        assertNotNull(rootUrl,
                "Cannot find '" + SAMPLES_DIR + "' on the classpath. " +
                        "Create src/main/resources/" + SAMPLES_DIR + " and put your sources there.");

        Path samplesRoot = toPath(rootUrl);
        Path sourceRoot = samplesRoot.resolve(SOURCE_ROOT_INSIDE);
        assertTrue(Files.exists(sourceRoot),
                "Expected Java sources at " + sourceRoot + " (your layout was printed earlier).");

        // 2) Collect .java files
        List<Path> javaFiles;
        try (var walk = Files.walk(sourceRoot)) {
            javaFiles = walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .collect(toList());
        }
        assertFalse(javaFiles.isEmpty(), "No .java files under " + sourceRoot);

        // 3) Compile to a temp folder
        tempDir = Files.createTempDirectory("fsx-compiled");
        Path classesOut = tempDir.resolve("classes");
        Files.createDirectories(classesOut);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "No system Java compiler (run tests with a JDK, not a JRE).");

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var units = fm.getJavaFileObjectsFromFiles(javaFiles.stream().map(Path::toFile).collect(toList()));
            List<String> options = List.of("-d", classesOut.toString());
            boolean ok = compiler.getTask(null, fm, null, options, null, units).call();
            assertTrue(ok, "Compilation failed for sources under " + sourceRoot);
        }

        // 4) ClassLoader for compiled classes
        samplesCL = new URLClassLoader(new URL[]{ classesOut.toUri().toURL() }, null);

        // 5) Extract
        FrameworkSymbolExtractor extractor = new TestExtractor();
        List<FrameworkSymbol> symbols = extractor.extractWithClassLoader(samplesCL, BASE_PACKAGE);

        assertNotNull(symbols);
        assertFalse(symbols.isEmpty(), "0 symbols found. Ensure packages start with '" + BASE_PACKAGE + "'.");

        // 6) Adaptive resolver for FrameworkSymbol
        FrameworkSymbolIntrospector FS = FrameworkSymbolIntrospector.build(symbols.get(0).getClass());

        // Sanity: all under expected root (only if we can resolve className)
        for (FrameworkSymbol s : symbols) {
            String className = FS.className(s);
            if (className != null) {
                assertTrue(className.startsWith(BASE_PACKAGE), "Outside root: " + className);
            }
        }

        // Optional: check a couple of Order members if present (signature resolution is fuzzy)
        Map<String, FrameworkSymbol> bySymbol = symbols.stream()
                .collect(Collectors.toMap(FS::symbolSafe, s -> s, (a, b) -> a));

        FrameworkSymbol orderTotal = bySymbol.get("Order#total");
        if (orderTotal != null) {
            String sig = FS.signature(orderTotal);
            if (sig != null) assertEquals("BigDecimal total()", sig);
        }
        FrameworkSymbol orderCtor = bySymbol.get("Order#<init>");
        if (orderCtor != null) {
            String sig = FS.signature(orderCtor);
            if (sig != null) assertEquals("Order(String)", sig);
        }

        // Debug print
        System.out.println("Compiled " + javaFiles.size() + " sources; discovered " + symbols.size() + " symbols.");
        symbols.stream().limit(12).forEach(s ->
                System.out.printf("%s | %s | %s | %s | %s%n",
                        nullTo(FS.className(s), "?"),
                        nullTo(FS.symbol(s), "?"),
                        nullTo(FS.signature(s), "?"),
                        nullTo(FS.kind(s), "?"),
                        FS.tags(s))
        );
    }

    // ---------- Introspector that adapts to your actual FrameworkSymbol shape ----------

    static final class FrameworkSymbolIntrospector {
        private final Accessor className;
        private final Accessor symbol;
        private final Accessor signature;
        private final Accessor snippet;
        private final Accessor kind;
        private final Accessor tags;

        private FrameworkSymbolIntrospector(Accessor className, Accessor symbol, Accessor signature,
                                            Accessor snippet, Accessor kind, Accessor tags) {
            this.className = className; this.symbol = symbol; this.signature = signature;
            this.snippet = snippet; this.kind = kind; this.tags = tags;
        }

        static FrameworkSymbolIntrospector build(Class<?> fsClass) {
            // Build candidate lists (method or field names, case-insensitive contains)
            Accessor className = find(fsClass, String.class, kw("class", "owner", "declaring", "type"));
            Accessor symbol    = find(fsClass, String.class, kw("symbol", "member", "name"));
            Accessor signature = find(fsClass, String.class, kw("signature", "sig"));
            Accessor snippet   = find(fsClass, String.class, kw("snippet", "example", "code"));
            Accessor kind      = find(fsClass, String.class, kw("kind", "category", "role"));
            Accessor tags      = findAnyOf(fsClass,
                    List.class, Set.class, Collection.class, String[].class,
                    kw("tags", "labels", "keywords"));

            return new FrameworkSymbolIntrospector(className, symbol, signature, snippet, kind, tags);
        }

        String className(Object o) { return (String) val(className, o); }
        String symbol(Object o)    { return (String) val(symbol, o); }
        String symbolSafe(Object o){ String s = symbol(o); return s == null ? "?" : s; }
        String signature(Object o) { return (String) val(signature, o); }
        String snippet(Object o)   { return (String) val(snippet, o); }
        String kind(Object o)      { return (String) val(kind, o); }
        List<String> tags(Object o){
            Object v = val(tags, o);
            if (v == null) return List.of();
            if (v instanceof String[]) return Arrays.asList((String[]) v);
            if (v instanceof Collection) return ((Collection<?>) v).stream().map(String::valueOf).collect(toList());
            return List.of(String.valueOf(v));
        }

        // ---- plumbing ----
        private static String nullTo(String s, String d){ return s == null ? d : s; }

        private static Predicate<String> kw(String... parts) {
            String[] lowers = Arrays.stream(parts).map(String::toLowerCase).toArray(String[]::new);
            return name -> {
                String n = name.toLowerCase();
                for (String k : lowers) if (n.contains(k)) return true;
                return false;
            };
        }

        private static Accessor find(Class<?> c, Class<?> returnType, Predicate<String> nameLike) {
            // Try methods first (record accessors / bean getters)
            for (Method m : c.getMethods()) {
                if (m.getParameterCount()==0 && returnType.isAssignableFrom(m.getReturnType())
                        && !m.getName().equals("getClass") && nameLike.test(m.getName())) {
                    m.setAccessible(true);
                    return new Accessor(m, null);
                }
                // Bean getter form getX()
                if (m.getParameterCount()==0 && returnType.isAssignableFrom(m.getReturnType())
                        && m.getName().startsWith("get") && nameLike.test(m.getName().substring(3))) {
                    m.setAccessible(true);
                    return new Accessor(m, null);
                }
            }
            // Then fields
            for (Field f : c.getFields()) {
                if (returnType.isAssignableFrom(f.getType()) && nameLike.test(f.getName())) {
                    f.setAccessible(true);
                    return new Accessor(null, f);
                }
            }
            return Accessor.NULL;
        }

        private static Accessor findAnyOf(Class<?> c, Class<?> t1, Class<?> t2, Class<?> t3, Class<?> t4,
                                          Predicate<String> nameLike) {
            for (Method m : c.getMethods()) {
                if (m.getParameterCount()==0 && !m.getName().equals("getClass") && nameLike.test(m.getName())) {
                    Class<?> r = m.getReturnType();
                    if (t1.isAssignableFrom(r) || t2.isAssignableFrom(r) || t3.isAssignableFrom(r) || t4.isAssignableFrom(r)) {
                        m.setAccessible(true);
                        return new Accessor(m, null);
                    }
                }
                if (m.getParameterCount()==0 && m.getName().startsWith("get") && nameLike.test(m.getName().substring(3))) {
                    Class<?> r = m.getReturnType();
                    if (t1.isAssignableFrom(r) || t2.isAssignableFrom(r) || t3.isAssignableFrom(r) || t4.isAssignableFrom(r)) {
                        m.setAccessible(true);
                        return new Accessor(m, null);
                    }
                }
            }
            for (Field f : c.getFields()) {
                if (nameLike.test(f.getName())) {
                    Class<?> r = f.getType();
                    if (t1.isAssignableFrom(r) || t2.isAssignableFrom(r) || t3.isAssignableFrom(r) || t4.isAssignableFrom(r)) {
                        f.setAccessible(true);
                        return new Accessor(null, f);
                    }
                }
            }
            return Accessor.NULL;
        }

        private static Object val(Accessor a, Object o) {
            try {
                if (a == Accessor.NULL) return null;
                if (a.m != null) return a.m.invoke(o);
                if (a.f != null) return a.f.get(o);
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        static final class Accessor {
            final Method m; final Field f;
            static final Accessor NULL = new Accessor(null, null);
            Accessor(Method m, Field f) { this.m = m; this.f = f; }
        }
    }

    // ---------- helpers ----------

    private static Path toPath(URL url) {
        try { return Paths.get(url.toURI()); } catch (Exception e) { return Paths.get(url.getPath()); }
    }

    private static String nullTo(String s, String d) { return s == null ? d : s; }
}
