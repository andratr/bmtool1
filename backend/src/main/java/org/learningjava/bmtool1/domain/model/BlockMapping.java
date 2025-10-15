package org.learningjava.bmtool1.domain.model;

import java.util.List;

// Mapping between PLSQL snippet and Java snippet to be stored
public record BlockMapping(
        String pairId,
        String pairName,
        String plsqlSnippet,
        String javaSnippet,
        String plsqlType,
        String javaType,
        List<String> javaHelpers //java helpers, to be added to the java mapping using the class HelperDetector
) {
}
