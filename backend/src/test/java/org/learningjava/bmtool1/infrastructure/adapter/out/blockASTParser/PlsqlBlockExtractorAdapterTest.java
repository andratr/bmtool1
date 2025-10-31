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

class PlsqlBlockExtractorAdapterTest {

    @TempDir
    Path tmp;

    // -------- flexible accessors (work with record/POJO/fields) ----------------

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
        for (RecordComponent rc : target.getClass().getRecordComponents()) {
            for (String name : componentNames) {
                if (rc.getName().equals(name) && rc.getType() == String.class) {
                    try {
                        return (String) rc.getAccessor().invoke(target);
                    } catch (ReflectiveOperationException ignored) {}
                }
            }
        }
        return null;
    }

    private static String tryNthString(Object target, int index) {
        if (target.getClass().isRecord()) {
            int seen = 0;
            for (RecordComponent rc : target.getClass().getRecordComponents()) {
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
        int seen = 0;
        for (Field f : target.getClass().getDeclaredFields()) {
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
        String v = tryCall(b, "type", "getType", "kind", "getKind", "category", "getCategory");
        if (v == null) v = tryField(b, "type", "kind", "category");
        if (v == null) v = tryRecordComponent(b, "type", "kind", "category");
        if (v == null) v = tryNthString(b, 0);
        if (v == null) throw new IllegalStateException("Cannot resolve Block.type");
        return v;
    }

    private static String codeOf(Block b) {
        String v = tryCall(b, "code", "getCode", "text", "getText", "content", "getContent", "body", "getBody");
        if (v == null) v = tryField(b, "code", "text", "content", "body");
        if (v == null) v = tryRecordComponent(b, "code", "text", "content", "body");
        if (v == null) v = tryNthString(b, 1);
        if (v == null) throw new IllegalStateException("Cannot resolve Block.code/text/content");
        return v;
    }

    private static String sourceOf(Block b) {
        String v = tryCall(b, "source", "getSource", "path", "getPath", "file", "getFile");
        if (v == null) v = tryField(b, "source", "path", "file");
        if (v == null) v = tryRecordComponent(b, "source", "path", "file");
        if (v == null) v = tryNthString(b, 2);
        if (v == null) throw new IllegalStateException("Cannot resolve Block.source");
        return v;
    }

    // ------------------------- tests -------------------------------------------

    @Test
    void extracts_assignment_if_insert_and_exception_handler() throws Exception {
        Path f = tmp.resolve("sample.plsql");
        String src = """
                DECLARE
                  v_exc_code VARCHAR2(20);
                  v_is_active CHAR(1) := 'Y';
                BEGIN
                  v_exc_code := 'DAYPSTDCHK1';
                  IF v_is_active = 'Y' THEN
                    INSERT INTO my_table(id, txt)
                    VALUES(1, 'hello');
                  END IF;
                EXCEPTION
                  WHEN OTHERS THEN
                    utils.handleError(SQLCODE, SQLERRM);
                END;
                /
                """;
        Files.writeString(f, src);

        PlsqlBlockExtractorAdapter adapter = new PlsqlBlockExtractorAdapter();
        List<Block> blocks = adapter.extract(f);

        assertNotNull(blocks);
        assertFalse(blocks.isEmpty(), "Expected blocks from PLSQL script");

        boolean hasAssignConst = blocks.stream()
                .anyMatch(b -> "ASSIGNMENT_CONST_STRING".equalsIgnoreCase(typeOf(b))
                        && codeOf(b).contains("v_exc_code:='DAYPSTDCHK1'".replace(":", " : ").replace(" ", ""))  // tolerant to no/extra spaces in getText()
                        ||     "ASSIGNMENT_CONST_STRING".equalsIgnoreCase(typeOf(b))
                        && codeOf(b).contains("v_exc_code:='DAYPSTDCHK1'"));

        boolean hasIf = blocks.stream()
                .anyMatch(b -> "CONDITION".equalsIgnoreCase(typeOf(b))
                        && codeOf(b).toUpperCase().contains("IF")
                        && codeOf(b).toUpperCase().contains("THEN"));

        boolean hasInsert = blocks.stream()
                .anyMatch(b -> "INSERT_STATEMENT".equalsIgnoreCase(typeOf(b))
                        && codeOf(b).toUpperCase().contains("INSERT INTO")
                        && codeOf(b).contains("\n")); // preserved whitespace

        boolean hasException = blocks.stream()
                .anyMatch(b -> "EXCEPTION_HANDLER".equalsIgnoreCase(typeOf(b))
                        && codeOf(b).toUpperCase().contains("WHENOTHERS"));

        assertTrue(hasAssignConst, "Should detect assignment with constant string");
        assertTrue(hasIf,          "Should detect IF condition");
        assertTrue(hasInsert,      "Should detect multi-line INSERT with whitespace preserved");
        assertTrue(hasException,   "Should detect exception handler");

        assertTrue(blocks.stream().allMatch(b -> sourceOf(b).endsWith("sample.plsql")));
    }

    @Test
    void empty_file_yields_zero_blocks() throws Exception {
        Path f = tmp.resolve("empty.plsql");
        Files.writeString(f, "");

        PlsqlBlockExtractorAdapter adapter = new PlsqlBlockExtractorAdapter();
        List<Block> blocks = adapter.extract(f);

        assertNotNull(blocks);
        assertEquals(0, blocks.size(), "Empty PLSQL should yield 0 blocks");
    }

    @Test
    void preserves_insert_whitespace() throws Exception {
        Path f = tmp.resolve("insert_only.plsql");
        String src = """
                BEGIN
                  INSERT INTO t (a, b)
                  VALUES ( 1,
                           'x' );
                END;
                /
                """;
        Files.writeString(f, src);

        PlsqlBlockExtractorAdapter adapter = new PlsqlBlockExtractorAdapter();
        List<Block> blocks = adapter.extract(f);

        String insert = blocks.stream()
                .filter(b -> "INSERT_STATEMENT".equalsIgnoreCase(typeOf(b)))
                .map(PlsqlBlockExtractorAdapterTest::codeOf)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No INSERT_STATEMENT block"));

        assertTrue(insert.contains("\n"), "INSERT block should preserve newlines/whitespace");
        assertTrue(insert.toUpperCase().contains("INSERT INTO"), "Should contain INSERT INTO");
    }
}
