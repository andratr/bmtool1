package org.learningjava.bmtool1.infrastructure.adapter.out.blockASTParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.learningjava.bmtool1.domain.model.pairs.Block;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaBlockExtractorAdapterTest {

    @TempDir
    Path tmp;

    // ----- Flexible reflectors -------------------------------------------------

    private static String tryCall(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                if (m.getReturnType() == String.class) {
                    Object v = m.invoke(target);
                    if (v != null) return (String) v;
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static String tryField(Object target, String... fieldNames) {
        for (String name : fieldNames) {
            try {
                Field f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(target);
                if (v instanceof String s) return s;
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static String tryRecordComponent(Object target, String... componentNames) {
        if (!target.getClass().isRecord()) return null;
        RecordComponent[] comps = target.getClass().getRecordComponents();
        for (String name : componentNames) {
            for (RecordComponent rc : comps) {
                if (rc.getName().equals(name) && rc.getType() == String.class) {
                    try {
                        Method accessor = rc.getAccessor();
                        Object v = accessor.invoke(target);
                        if (v != null) return (String) v;
                    } catch (ReflectiveOperationException ignored) {}
                }
            }
        }
        return null;
    }

    private static String tryNthString(Object target, int index) {
        // For records: pick nth String component (0=type, 1=code, 2=source by convention)
        if (target.getClass().isRecord()) {
            RecordComponent[] comps = target.getClass().getRecordComponents();
            int seen = 0;
            for (RecordComponent rc : comps) {
                if (rc.getType() == String.class) {
                    if (seen == index) {
                        try {
                            return (String) rc.getAccessor().invoke(target);
                        } catch (ReflectiveOperationException ignored) {}
                    }
                    seen++;
                }
            }
        }
        // For POJOs: pick nth declared String field
        Field[] fs = target.getClass().getDeclaredFields();
        int seen = 0;
        for (Field f : fs) {
            if (f.getType() == String.class) {
                f.setAccessible(true);
                try {
                    Object v = f.get(target);
                    if (seen == index && v instanceof String s) return s;
                } catch (IllegalAccessException ignored) {}
                seen++;
            }
        }
        return null;
    }

    private static String typeOf(Block b) {
        String v =
                tryCall(b, "type", "getType", "kind", "getKind", "category", "getCategory");
        if (v == null) v = tryField(b, "type", "kind", "category");
        if (v == null) v = tryRecordComponent(b, "type", "kind", "category");
        if (v == null) v = tryNthString(b, 0); // often first string is type
        if (v == null) throw new IllegalStateException("Cannot resolve Block.type");
        return v;
    }

    private static String codeOf(Block b) {
        String v =
                tryCall(b, "code", "getCode", "text", "getText", "content", "getContent", "body", "getBody");
        if (v == null) v = tryField(b, "code", "text", "content", "body");
        if (v == null) v = tryRecordComponent(b, "code", "text", "content", "body");
        if (v == null) v = tryNthString(b, 1); // often second string is the code/text
        if (v == null) throw new IllegalStateException("Cannot resolve Block.code/text/content");
        return v;
    }

    private static String sourceOf(Block b) {
        String v =
                tryCall(b, "source", "getSource", "path", "getPath", "file", "getFile");
        if (v == null) v = tryField(b, "source", "path", "file");
        if (v == null) v = tryRecordComponent(b, "source", "path", "file");
        if (v == null) v = tryNthString(b, 2); // often third string is the source/path
        if (v == null) throw new IllegalStateException("Cannot resolve Block.source");
        return v;
    }

    // ----- Tests ----------------------------------------------------------------

    @Test
    void extracts_method_field_and_statements_from_simple_class() throws Exception {
        Path javaFile = tmp.resolve("Sample.java");
        String src = """
                package demo;

                public class Sample {
                    private int count = 42;

                    public String greet(String name) {
                        int n = count + 1;
                        return "Hi, " + name + " (" + n + ")";
                    }
                }
                """;
        Files.writeString(javaFile, src);

        JavaBlockExtractorAdapter adapter = new JavaBlockExtractorAdapter();
        List<Block> blocks = adapter.extract(javaFile);

        assertNotNull(blocks);
        assertFalse(blocks.isEmpty(), "Expected some blocks to be extracted");

        boolean hasMethod = blocks.stream()
                .anyMatch(b -> "METHOD".equalsIgnoreCase(typeOf(b)) && codeOf(b).contains("greet("));
        boolean hasField  = blocks.stream()
                .anyMatch(b -> "FIELD".equalsIgnoreCase(typeOf(b)) && codeOf(b).contains("int count"));
        boolean hasStmt   = blocks.stream()
                .anyMatch(b -> "STATEMENT".equalsIgnoreCase(typeOf(b)));

        assertTrue(hasMethod, "Should detect a METHOD block for greet()");
        assertTrue(hasField,  "Should detect a FIELD block for count");
        assertTrue(hasStmt,   "Should produce at least one STATEMENT block");

        assertTrue(blocks.stream().allMatch(b -> sourceOf(b).endsWith("Sample.java")));
    }

    @Test
    void empty_file_yields_zero_blocks() throws Exception {
        Path javaFile = tmp.resolve("Empty.java");
        Files.writeString(javaFile, ""); // empty content

        JavaBlockExtractorAdapter adapter = new JavaBlockExtractorAdapter();
        List<Block> blocks = adapter.extract(javaFile);

        assertNotNull(blocks);
        // Some grammars still produce a root node with no statements; your adapter adds blocks only on listeners,
        // so an empty file should yield zero blocks.
        assertEquals(0, blocks.size(), "Empty file should yield no blocks");
    }

    @Test
    void multiple_methods_and_fields_are_all_detected() throws Exception {
        Path javaFile = tmp.resolve("Multi.java");
        String src = """
                public class Multi {
                    private String a;
                    int b = 10;

                    void m1() { int x = 1; }
                    static int m2(int k) { return k * 2; }
                }
                """;
        Files.writeString(javaFile, src);

        JavaBlockExtractorAdapter adapter = new JavaBlockExtractorAdapter();
        List<Block> blocks = adapter.extract(javaFile);

        long methodCount = blocks.stream()
                .filter(b -> "METHOD".equalsIgnoreCase(typeOf(b))).count();
        long fieldCount  = blocks.stream()
                .filter(b -> "FIELD".equalsIgnoreCase(typeOf(b))).count();

        assertTrue(methodCount >= 2, "Expected at least two METHOD blocks (m1, m2)");
        assertTrue(fieldCount  >= 2, "Expected at least two FIELD blocks (a, b)");
    }
}
