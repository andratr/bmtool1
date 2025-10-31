package org.learningjava.bmtool1.domain.service.ingest;

import org.junit.jupiter.api.Test;
import org.learningjava.bmtool1.domain.model.pairs.Block;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HelperDetectorTest {

    private final HelperDetector detector = new HelperDetector();

    @Test
    void marks_private_method_as_helper() {
        Block privateMethod = new Block(
                "METHOD",
                "private String trim(String s) { return s == null ? null : s.trim(); }",
                "X.java"
        );

        List<Block> out = detector.detectHelpers(List.of(privateMethod));

        assertEquals(1, out.size());
        assertTrue(out.get(0).isHelper(), "private methods should be marked as helpers");
        assertEquals("METHOD", out.get(0).type());
        assertEquals(privateMethod.text(), out.get(0).text());
    }

    @Test
    void marks_predicate_method_as_helper_when_not_overridden() {
        Block predicateMethod = new Block(
                "METHOD",
                "public Predicate<String> isLongerThan(int n) { return s -> s != null && s.length() > n; }",
                "X.java"
        );

        List<Block> out = detector.detectHelpers(List.of(predicateMethod));

        assertTrue(out.get(0).isHelper(), "methods containing 'Predicate' (and not @Override) should be helpers");
    }

    @Test
    void does_not_mark_public_non_predicate_method_as_helper() {
        Block publicMethod = new Block(
                "METHOD",
                "public void process() { System.out.println(\"ok\"); }",
                "X.java"
        );

        List<Block> out = detector.detectHelpers(List.of(publicMethod));

        assertFalse(out.get(0).isHelper(), "plain public methods should NOT be helpers");
        // for non-helpers, the same instance is returned
        assertSame(publicMethod, out.get(0), "non-helper blocks should be returned as-is");
    }

    @Test
    void does_not_mark_overridden_predicate_method_as_helper() {
        Block overriddenPredicate = new Block(
                "METHOD",
                "@Override\npublic Predicate<String> apply() { return s -> true; }",
                "X.java"
        );

        List<Block> out = detector.detectHelpers(List.of(overriddenPredicate));

        assertFalse(out.get(0).isHelper(), "@Override should prevent helper marking even if 'Predicate' is present");
    }

    @Test
    void preserves_non_method_blocks_unchanged() {
        Block field = new Block("FIELD", "private int x = 1;", "X.java");
        Block statement = new Block("STATEMENT", "return;", "X.java");

        List<Block> out = detector.detectHelpers(List.of(field, statement));

        assertFalse(out.get(0).isHelper());
        assertFalse(out.get(1).isHelper());
        assertSame(field, out.get(0));
        assertSame(statement, out.get(1));
    }

    @Test
    void preserves_existing_helper_flag_when_not_recomputed() {
        Block alreadyHelper = new Block(
                "METHOD",
                "public void helperByConvention() {}",
                "X.java",
                true // pre-marked as helper
        );

        List<Block> out = detector.detectHelpers(List.of(alreadyHelper));

        // Because it doesn't match private/Predicate rules, detector returns the SAME instance
        assertSame(alreadyHelper, out.get(0));
        assertTrue(out.get(0).isHelper(), "pre-marked helper should remain a helper");
    }
}
