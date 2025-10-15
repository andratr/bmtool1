package org.learningjava.bmtool1.domain.service.ingest;

import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.domain.model.Block;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockMapperTest {

    @Test
    void maps_assignment_const_string_to_eventCode_method() {
        BlockMapper mapper = new BlockMapper();

        var plsql = List.of(new Block(
                "ASSIGNMENT_CONST_STRING",
                "v_exc_code := 'E123';",
                "a.sql"
        ));

        var java = List.of(new Block(
                "METHOD",
                """
                public class X {
                  String eventCode() { return "E123"; }
                }
                """,
                "X.java"
        ));

        var out = mapper.map(plsql, java);
        assertFalse(out.isEmpty(), "Expected mapping via eventCode rule");
    }

    @Test
    void maps_condition_to_predicate_method() {
        BlockMapper mapper = new BlockMapper();

        var plsql = List.of(new Block(
                "CONDITION",
                "IF x IS NOT NULL THEN NULL; END IF;",
                "cond.sql"
        ));

        var java = List.of(new Block(
                "METHOD",
                """
                public boolean predicate(Object x) { return x != null; }
                """,
                "Y.java"
        ));

        var out = mapper.map(plsql, java);
        assertFalse(out.isEmpty(), "Expected mapping via predicate rule");
    }

    @Test
    void maps_insert_to_transformation_method() {
        BlockMapper mapper = new BlockMapper();

        var plsql = List.of(new Block(
                "INSERT_STATEMENT",
                "INSERT INTO T(A) VALUES (1);",
                "ins.sql"
        ));

        var java = List.of(new Block(
                "METHOD",
                """
                public void transformation() { /* insert logic */ }
                """,
                "Z.java"
        ));

        var out = mapper.map(plsql, java);
        assertFalse(out.isEmpty(), "Expected mapping via transformation rule");
    }
}
