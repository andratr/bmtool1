package org.learningjava.bmtool1.domain.model.pairs;

import java.util.Objects;

public record Block(
        String type,        // e.g. METHOD, STATEMENT, ASSIGNMENT
        String text,        // full snippet, including method signature
        String sourcePath,
        boolean isHelper
) {

    public Block(String type, String text, String sourcePath) {
        this(type, text, sourcePath, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Block other)) return false;
        return Objects.equals(sourcePath, other.sourcePath)
                && Objects.equals(type, other.type)
                && Objects.equals(text, other.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourcePath, type, text);
    }

}
