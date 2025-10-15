package org.learningjava.bmtool1.domain.service.templateCreator;

import org.learningjava.bmtool1.domain.model.Block;
import org.learningjava.bmtool1.domain.model.TargetJavaClassForConsumer;

import java.util.*;

public class TemplateCreator {

    // ---- prefix → inputType mapping ----
    private static final Map<String, String> PREFIX_TO_TYPE = Map.of(
            "EXP_", "Customer",
            "LIM_", "Limit",
            "ACC_", "Account",
            "TRX_", "Transaction"
            // add more mappings here
    );

    public TargetJavaClassForConsumer map(List<Block> blocks) {
        List<String> methods = new ArrayList<>();

        String excCode = null;
        String excCategory = null;
        String solCode = null;

        for (Block block : blocks) {
            switch (block.type()) {
                case "ASSIGNMENT_CONST_STRING" -> {
                    String text = block.text();
                    String lhs = text.split(":=")[0].trim().toLowerCase();

                    if (lhs.equals("v_exc_code")) {
                        excCode = extractValue(text);
                    } else if (lhs.equals("v_exc_category")) {
                        excCategory = extractValue(text);
                    } else if (lhs.equals("v_sol_code")) {
                        solCode = extractValue(text);
                    }
                }

                case "CONDITION" -> methods.add("""
                            @Override
                            public java.util.function.Predicate<%s> predicate(ConsumerDqContext context) {
                                return item -> /* condition from PL/SQL: %s */ true;
                            }
                        """.formatted("Object", block.text())); // temporary placeholder, replaced later

                case "INSERT_STATEMENT" -> {
                    String sql = block.text();
                    methods.add("""
                                @Override
                                public java.util.function.Function<%s, DataQualityEvent> transformation(ConsumerDqContext context) {
                                    return item -> {
                                        // TODO: Translate the following PL/SQL into Java transformation logic
                                        /*
                                        %s
                                        */
                                        return null;
                                    };
                                }
                            """.formatted("Object", sql)); // temporary placeholder, replaced later
                }

                case "EXCEPTION_HANDLER" -> methods.add("""
                            // TODO: Handle exception mapping here
                            // from PL/SQL: %s
                        """.formatted(block.text()));
            }
        }

        // ---- Build eventCode and className strictly from assignments ----
        String eventCode = buildEventCode(excCategory, excCode, solCode);
        String className = eventCode; // identical

        // Resolve input type from prefix mapping
        String detectedInputType = mapInputTypeFromEventCode(eventCode, "Unknown");

        System.out.println("DEBUG: Final eventCode=" + eventCode
                + ", className=" + className
                + ", detectedInputType=" + detectedInputType);

        // Add eventCode() method at the top
        methods.add(0, """
                    @Override
                    public String eventCode() {
                        return "%s";
                    }
                """.formatted(eventCode));

        // Replace placeholder "Object" with actual detectedInputType
        methods.replaceAll(m -> m.replace("Object", detectedInputType));

        return new TargetJavaClassForConsumer(
                "org.learningjava.rules",
                className,
                detectedInputType,
                methods
        );
    }

    // --- helpers ---

    private String extractValue(String assignment) {
        int idx = assignment.indexOf(":=");
        if (idx == -1) return null;
        int start = assignment.indexOf("'", idx);
        int end = assignment.indexOf("'", start + 1);
        if (start != -1 && end != -1) {
            return assignment.substring(start + 1, end).trim();
        }
        return null;
    }

    private String buildEventCode(String category, String code, String sol) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        if (category != null) parts.add(category.toUpperCase());
        if (code != null) parts.add(code.toUpperCase());
        if (sol != null) parts.add(sol.toUpperCase());

        if (parts.isEmpty()) {
            return "UNKNOWN_EVENT";
        }

        return String.join("_", parts);
    }

    /**
     * Override detectedInputType using prefix mapping from eventCode.
     */
    private String mapInputTypeFromEventCode(String eventCode, String fallback) {
        if (eventCode == null) return fallback;

        for (Map.Entry<String, String> entry : PREFIX_TO_TYPE.entrySet()) {
            if (eventCode.toUpperCase(Locale.ROOT).startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return fallback;
    }
}
