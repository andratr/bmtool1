package org.learningjava.bmtool1.domain.model;

public record Block(
        String type,        // e.g. METHOD, STATEMENT, ASSIGNMENT
        String text,        // full snippet, including method signature
        String sourcePath,
        boolean isHelper
) {
    // Convenience constructor (no helpers by default)
    public Block(String type, String text, String sourcePath) {
        this(type, text, sourcePath, false);
    }
}
