package org.learningjava.bmtool1.domain.model;

import java.util.List;

public record BlockMapping(
        String pairId,
        String plsqlSnippet,
        String javaSnippet,
        String plsqlType,
        String javaType,
        List<String> javaHelpers
) {}
