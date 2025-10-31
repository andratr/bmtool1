package org.learningjava.bmtool1.domain.service.templateCreator;

import org.learningjava.bmtool1.domain.model.pairs.Block;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventCodeUtil {

    private static final Map<String, String> PREFIX_TO_TYPE = Map.of(
            "EXP_", "Customer",
            "LIM_", "Limit",
            "ACC_", "Account",
            "TRX_", "Transaction"
            // add more mappings here
    );

    public static String buildEventCodeFromBlocks(List<Block> blocks) {
        String excCode = null;
        String excCategory = null;
        String solCode = null;

        for (Block block : blocks) {
            if ("ASSIGNMENT_CONST_STRING".equals(block.type())) {
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
        }

        return buildEventCode(excCategory, excCode, solCode);
    }

    public static String mapInputTypeFromEventCode(String eventCode, String fallback) {
        if (eventCode == null) return fallback;

        for (Map.Entry<String, String> entry : PREFIX_TO_TYPE.entrySet()) {
            if (eventCode.toUpperCase(Locale.ROOT).startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return fallback;
    }

    private static String extractValue(String assignment) {
        int idx = assignment.indexOf(":=");
        if (idx == -1) return null;
        int start = assignment.indexOf("'", idx);
        int end = assignment.indexOf("'", start + 1);
        if (start != -1 && end != -1) {
            return assignment.substring(start + 1, end).trim();
        }
        return null;
    }

    private static String buildEventCode(String category, String code, String sol) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        if (category != null) parts.add(category.toUpperCase());
        if (code != null) parts.add(code.toUpperCase());
        if (sol != null) parts.add(sol.toUpperCase());

        if (parts.isEmpty()) {
            return "UNKNOWN_EVENT";
        }

        return String.join("_", parts);
    }
}
