package org.learningjava.bmtool1.infrastructure.adapter.out;

import org.learningjava.bmtool1.domain.model.FrameworkSymbol;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

@Component
public class FrameworkSymbolExtractor {

    public List<FrameworkSymbol> extract(String... basePackages) {
        List<Class<?>> classes = findClasses(basePackages);
        List<FrameworkSymbol> out = new ArrayList<>();

        for (Class<?> clazz : classes) {
            if (!isFrameworkClass(clazz)) continue;

            String kind = kindOf(clazz);
            List<String> baseTags = defaultTags(clazz, kind);

            // methods
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

            // constructors (treat as factories for DTOs/value-objects)
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

    // ---------- helpers ----------

    private boolean isFrameworkClass(Class<?> c) {
        String n = c.getName();
        // adjust to your namespaces:
        return n.startsWith("com.example.dq.foundation");
    }

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
        // minimal, safe defaults—tweak per class
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
        if (kind.equals("dto") && m.getName().startsWith("set") && m.getParameterCount()==1) {
            String field = Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
            return "var d = new " + cls + "();\nd." + m.getName() + "(" + placeholder(m.getParameterTypes()[0]) + ");";
        }
        // generic fallback
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

    // ------------ very simple classpath scanning (works for exploded builds) ------------
    private List<Class<?>> findClasses(String... basePackages) {
        List<Class<?>> classes = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String pkg : basePackages) {
            String path = pkg.replace('.', '/');
            try {
                Enumeration<URL> resources = cl.getResources(path);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    if (url.getProtocol().equals("file")) {
                        classes.addAll(findInDirectory(new java.io.File(url.getPath()), pkg));
                    } else if (url.getProtocol().equals("jar")) {
                        classes.addAll(findInJar(url, pkg));
                    }
                }
            } catch (IOException ignored) {}
        }
        return classes;
    }

    private List<Class<?>> findInDirectory(java.io.File dir, String pkg) {
        List<Class<?>> out = new ArrayList<>();
        if (!dir.exists()) return out;
        for (var f : Objects.requireNonNull(dir.listFiles())) {
            if (f.isDirectory()) out.addAll(findInDirectory(f, pkg + "." + f.getName()));
            else if (f.getName().endsWith(".class") && !f.getName().contains("$")) {
                String name = pkg + '.' + f.getName().substring(0, f.getName().length() - 6);
                try { out.add(Class.forName(name)); } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    private List<Class<?>> findInJar(URL jarUrl, String basePkg) {
        List<Class<?>> out = new ArrayList<>();
        String pkgPath = basePkg.replace('.', '/');
        String spec = jarUrl.getFile();
        String jarPath = spec.substring(5, spec.indexOf("!"));
        try (JarInputStream jis = new JarInputStream(new URL("file:" + jarPath).openStream())) {
            JarEntry e;
            while ((e = jis.getNextJarEntry()) != null) {
                String name = e.getName();
                if (name.endsWith(".class") && name.startsWith(pkgPath) && !name.contains("$")) {
                    String cls = name.replace('/', '.').substring(0, name.length() - 6);
                    try { out.add(Class.forName(cls)); } catch (Throwable ignored) {}
                }
            }
        } catch (IOException ignored) {}
        return out;
    }
}
