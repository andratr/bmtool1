package org.learningjava.bmtool1.infrastructure.adapter.out;

import org.learningjava.bmtool1.domain.model.framework.FrameworkSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

@Component
public class FrameworkSymbolExtractor {

    private static final Logger log = LoggerFactory.getLogger(FrameworkSymbolExtractor.class);
    private static final boolean EXTRA = Boolean.getBoolean("bmtool.framework.debug");

    // -------------------- entry points --------------------

    public List<FrameworkSymbol> extract(String... basePackages) {
        return extractWithClassLoader(Thread.currentThread().getContextClassLoader(), basePackages);
    }

    public List<FrameworkSymbol> extractWithClassLoader(ClassLoader cl, String... basePackages) {
        long t0 = System.nanoTime();

        final String[] bases = normalizeBases(basePackages);
        final boolean includeAll = bases.length == 0;

        log.debug("Extractor: start; basePackages={} (includeAll={})", Arrays.toString(bases), includeAll);
        if (cl instanceof URLClassLoader ucl && EXTRA) {
            log.debug("Extractor: URLClassLoader URLs={}", Arrays.toString(ucl.getURLs()));
        }

        List<Class<?>> classes = includeAll ? List.of() : findClassesViaResources(cl, bases);

        if (classes.isEmpty()) {
            if (EXTRA) log.debug("Extractor: resource scan found 0; falling back to URL scan");
            classes = findClassesViaUrls(cl, bases); // bases may be empty ⇒ scan all
        }

        classes = dedupe(classes);
        log.debug("Extractor: discovered {} classes for bases={} (includeAll={})",
                classes.size(), Arrays.toString(bases), includeAll);
        if (EXTRA && !classes.isEmpty()) {
            classes.stream().limit(10).forEach(c -> log.debug("  class: {}", c.getName()));
            if (classes.size() > 10) log.debug("  ... ({} more)", classes.size() - 10);
        }

        List<FrameworkSymbol> out = new ArrayList<>();
        int clsIdx = 0;
        for (Class<?> clazz : classes) {
            if (!inBases(clazz, bases)) {
                if (EXTRA) log.debug("  - skip (outside base): {}", clazz.getName());
                continue;
            }
            String kind = kindOf(clazz);
            List<String> baseTags = defaultTags(clazz, kind);

            int before = out.size();

            // public methods
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

            // public constructors
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

            int produced = out.size() - before;
            if ((++clsIdx <= 20) || EXTRA) {
                log.debug("  + {} -> {} symbols [kind={}]", clazz.getName(), produced, kind);
            }
        }

        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.debug("Extractor: total symbols={} in {} ms", out.size(), ms);
        return out;
    }

    // -------------------- scanning helpers --------------------

    private List<Class<?>> findClassesViaResources(ClassLoader cl, String[] basePackages) {
        List<Class<?>> classes = new ArrayList<>();
        for (String pkg : basePackages) {
            String path = pkg.replace('.', '/');
            try {
                Enumeration<URL> resources = cl.getResources(path);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    log.debug("scan(resource) {} -> {}", pkg, url);
                    if ("file".equals(url.getProtocol())) {
                        classes.addAll(findInDirectory(cl, new java.io.File(url.getPath()), pkg));
                    } else if ("jar".equals(url.getProtocol())) {
                        classes.addAll(findInJarUrl(cl, url, pkg));
                    }
                }
            } catch (IOException ignored) {}
        }
        return classes;
    }

    private List<Class<?>> findClassesViaUrls(ClassLoader cl, String[] basePackages) {
        List<Class<?>> out = new ArrayList<>();
        List<URL> urls = new ArrayList<>();

        if (cl instanceof URLClassLoader ucl) {
            urls.addAll(Arrays.asList(ucl.getURLs()));
        } else {
            try {
                var m = cl.getClass().getMethod("getURLs");
                Object v = m.invoke(cl);
                if (v instanceof URL[] arr) urls.addAll(Arrays.asList(arr));
            } catch (Throwable ignored) {}
        }

        if (urls.isEmpty()) {
            if (EXTRA) log.debug("URL scan: no URLs available on {}", cl.getClass().getName());
            return out;
        }

        boolean includeAll = basePackages.length == 0;

        if (EXTRA) {
            log.debug("URL scan: scanning {} URL(s) (includeAll={})", urls.size(), includeAll);
            urls.forEach(u -> log.debug("  url: {}", u));
        }

        for (URL url : urls) {
            if (!"file".equals(url.getProtocol())) continue;
            String path = url.getPath();
            if (path == null) continue;

            // JAR
            if (path.endsWith(".jar")) {
                out.addAll(findInJarFileUrl(cl, url, basePackages));
                continue;
            }

            // Directory root
            Path root;
            try { root = Paths.get(URI.create(url.toString())); }
            catch (Exception e) { continue; }
            if (!Files.isDirectory(root)) continue;

            try {
                if (includeAll) {
                    out.addAll(findInDirectoryRoot(cl, root));
                } else {
                    for (String base : basePackages) {
                        if (base == null || base.isBlank()) continue;
                        String bp = base.trim();
                        Path pkgDir = root.resolve(bp.replace('.', '/'));
                        if (Files.isDirectory(pkgDir)) {
                            out.addAll(findInDirectoryPath(cl, pkgDir, bp));
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
        return dedupe(out);
    }

    private List<Class<?>> findInJarUrl(ClassLoader cl, URL jarUrl, String basePkg) {
        try {
            String spec = jarUrl.getFile();
            int bang = spec.indexOf('!');
            if (bang < 0) return List.of();
            String jarPart = spec.substring(0, bang);
            URL fileUrl = new URL(jarPart);
            return scanJarForBase(cl, fileUrl, new String[]{ basePkg });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Class<?>> findInJarFileUrl(ClassLoader cl, URL fileJarUrl, String[] basePkgs) {
        return scanJarForBase(cl, fileJarUrl, basePkgs);
    }

    private List<Class<?>> scanJarForBase(ClassLoader cl, URL jarFileUrl, String[] basePkgs) {
        List<Class<?>> out = new ArrayList<>();
        if (EXTRA) log.debug("scan(jar) url={}", jarFileUrl);
        try (JarInputStream jis = new JarInputStream(jarFileUrl.openStream())) {
            JarEntry e;
            while ((e = jis.getNextJarEntry()) != null) {
                String name = e.getName();
                if (!name.endsWith(".class") || name.contains("$")) continue;
                String fqcn = name.substring(0, name.length() - 6).replace('/', '.');
                if (!startsWithAny(fqcn, basePkgs)) continue;
                try {
                    Class<?> c = (cl == null)
                            ? Class.forName(fqcn, false, Thread.currentThread().getContextClassLoader())
                            : Class.forName(fqcn, false, cl);
                    out.add(c);
                } catch (Throwable ignored) {}
            }
        } catch (IOException ignored) {}
        return out;
    }

    private List<Class<?>> findInDirectoryRoot(ClassLoader cl, Path root) throws IOException {
        List<Class<?>> out = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String fn = p.getFileName().toString();
                if (!fn.endsWith(".class") || fn.contains("$")) continue;
                Path rel = root.relativize(p);
                String relDots = rel.toString().replace('/', '.').replace('\\', '.');
                String stem = relDots.substring(0, relDots.length() - 6);
                String fqcn = stem; //
                try {
                    Class<?> c = (cl == null)
                            ? Class.forName(fqcn, false, Thread.currentThread().getContextClassLoader())
                            : Class.forName(fqcn, false, cl);
                    out.add(c);
                } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    private List<Class<?>> findInDirectory(ClassLoader cl, java.io.File dir, String pkg) {
        List<Class<?>> out = new ArrayList<>();
        if (dir == null || !dir.exists()) return out;
        for (var f : Objects.requireNonNull(dir.listFiles())) {
            if (f.isDirectory()) out.addAll(findInDirectory(cl, f, pkg + "." + f.getName()));
            else if (f.getName().endsWith(".class") && !f.getName().contains("$")) {
                String name = pkg + '.' + f.getName().substring(0, f.getName().length() - 6);
                try {
                    Class<?> c = (cl == null)
                            ? Class.forName(name, false, Thread.currentThread().getContextClassLoader())
                            : Class.forName(name, false, cl);
                    out.add(c);
                } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    private List<Class<?>> findInDirectoryPath(ClassLoader cl, Path pkgDir, String basePkg) throws IOException {
        List<Class<?>> out = new ArrayList<>();
        try (var walk = Files.walk(pkgDir)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String fn = p.getFileName().toString();
                if (!fn.endsWith(".class") || fn.contains("$")) continue;
                Path rel = pkgDir.relativize(p);
                String suffix = rel.toString().replace('/', '.').replace('\\', '.');
                String fqcn = basePkg + "." + suffix.substring(0, suffix.length() - 6);
                try {
                    Class<?> c = (cl == null)
                            ? Class.forName(fqcn, false, Thread.currentThread().getContextClassLoader())
                            : Class.forName(fqcn, false, cl);
                    out.add(c);
                } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    private List<Class<?>> dedupe(List<Class<?>> in) {
        LinkedHashMap<String, Class<?>> map = new LinkedHashMap<>();
        for (Class<?> c : in) map.putIfAbsent(c.getName(), c);
        return new ArrayList<>(map.values());
    }

    // -------------------- filtering / utils --------------------

    private boolean inBases(Class<?> c, String... basePackages) {
        if (basePackages == null || basePackages.length == 0) return true;
        String n = c.getName();
        for (String base : basePackages) {
            if (base == null || base.isBlank()) continue;
            String b = base.trim();
            if (n.equals(b) || n.startsWith(b + ".")) return true;
        }
        return false;
    }

    private boolean startsWithAny(String fqcn, String[] bases) {
        if (bases == null || bases.length == 0) return true;
        for (String b : bases) {
            if (b == null || b.isBlank()) continue;
            String base = b.trim();
            if (fqcn.equals(base) || fqcn.startsWith(base + ".")) return true;
        }
        return false;
    }

    private String[] normalizeBases(String... basePackages) {
        if (basePackages == null || basePackages.length == 0) return new String[0];
        return Arrays.stream(basePackages)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    // -------------------- tagging / signature helpers --------------------

    private String kindOf(Class<?> c) {
        String p = c.getPackageName();
        if (p.contains(".dto.") || p.endsWith(".dto") || p.contains(".data.")) return "dto";
        if (p.contains(".model.") || p.contains(".domain.") || p.endsWith(".domain")) return "value-object";
        if (p.contains(".service.") || p.contains(".api.") || p.endsWith(".api")) return "service";
        if (p.contains(".foundation") || p.endsWith(".foundation")) return "context";
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
                "(" + Arrays.stream(m.getParameterTypes()).map(this::simple).reduce((x, y) -> x + ", " + y).orElse("") + ")";
    }

    private String signatureOf(Constructor<?> c) {
        String name = c.getDeclaringClass().getSimpleName();
        return name + "(" + Arrays.stream(c.getParameterTypes()).map(this::simple).reduce((x, y) -> x + ", " + y).orElse("") + ")";
    }

    private String simple(Class<?> t) {
        if (t.isArray()) return simple(t.getComponentType()) + "[]";
        return t.getSimpleName();
    }

    private String snippetFor(Class<?> clazz, Method m, String kind) {
        String cls = clazz.getSimpleName();
        if (Modifier.isStatic(m.getModifiers())) {
            if (m.getName().equals("parse") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                return "var x = " + cls + ".parse(\"EUR-100.00\");";
            }
            if (m.getName().equals("zero")) {
                return "var x = " + cls + ".zero(\"EUR\");";
            }
        }
        if (m.getName().equals("signum")) {
            return "if (x.signum() >= 0) { /* ... */ }";
        }
        if ("dto".equals(kind) && m.getName().startsWith("set") && m.getParameterCount() == 1) {
            return "var d = new " + cls + "();\n" +
                    "d." + m.getName() + "(" + placeholder(m.getParameterTypes()[0]) + ");";
        }
        return "// use " + cls + "#" + m.getName() + "()";
    }

    private String snippetFor(Class<?> clazz, Constructor<?> c, String kind) {
        String cls = clazz.getSimpleName();
        if ("value-object".equals(kind) || "dto".equals(kind)) {
            String args = Arrays.stream(c.getParameterTypes()).map(this::placeholder).reduce((x, y) -> x + ", " + y).orElse("");
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
}
