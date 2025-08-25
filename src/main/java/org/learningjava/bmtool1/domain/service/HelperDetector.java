package org.learningjava.bmtool1.domain.service;

import org.learningjava.bmtool1.domain.model.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects helper methods and private constants/fields among Java blocks.
 * A block is considered a helper if:
 *   - It is a private method
 *   - It is a private field/constant
 *   - OR it is referenced inside a "main" method
 */
public class HelperDetector {

    private static final Logger log = LoggerFactory.getLogger(HelperDetector.class);

    public Map<Block, List<Block>> detectHelpers(List<Block> javaBlocks) {
        log.info("🔎 Starting helper detection on {} Java blocks", javaBlocks.size());

        // Collect all candidate names (methods + fields)
        Set<String> allNames = extractNames(javaBlocks);

        // Identify helpers
        List<Block> helpers = javaBlocks.stream()
                .filter(b -> isHelperByStructure(b) || isHelperByUsage(b, javaBlocks, allNames))
                .peek(b -> log.debug(
                        "✅ Marked as HELPER: firstLine='{}...' (len={}) [reason={}]",
                        firstLine(b.text()), b.text().length(), explainWhy(b, javaBlocks, allNames)))
                .toList();

        // Map each main method -> helpers from same file
        Map<Block, List<Block>> result = javaBlocks.stream()
                .filter(b -> "METHOD".equals(b.type()) && !helpers.contains(b))
                .collect(Collectors.toMap(
                        main -> main,
                        main -> helpers.stream()
                                .filter(h -> h.sourcePath().equals(main.sourcePath()))
                                .toList()
                ));

        log.info("➡️ Found {} helper blocks and {} main methods",
                helpers.size(), result.size());
        return result;
    }

    // ---- Structural helper detection ----
    private boolean isHelperByStructure(Block block) {
        String code = block.text();
        return ("METHOD".equals(block.type()) && code.contains("private "))
                || ("FIELD".equals(block.type()) && code.contains("private "));
    }

    // ---- Usage-based helper detection ----
    private boolean isHelperByUsage(Block block, List<Block> javaBlocks, Set<String> allNames) {
        String name = extractName(block);
        if (name == null) return false;

        // If this block is referenced inside any main method => helper
        for (Block main : javaBlocks) {
            if ("METHOD".equals(main.type()) && !isHelperByStructure(main)) {
                if (main.text().contains(name + "(") || main.text().contains(name + " ")) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> extractNames(List<Block> javaBlocks) {
        Set<String> names = new HashSet<>();
        for (Block b : javaBlocks) {
            String n = extractName(b);
            if (n != null) names.add(n);
        }
        return names;
    }

    private String extractName(Block block) {
        if ("METHOD".equals(block.type())) {
            String firstLine = firstLine(block.text());
            if (firstLine.contains("(")) {
                String beforeParen = firstLine.substring(0, firstLine.indexOf("("));
                String[] parts = beforeParen.trim().split("\\s+");
                return parts[parts.length - 1];
            }
        } else if ("FIELD".equals(block.type())) {
            String firstLine = firstLine(block.text());
            String[] parts = firstLine.replace(";", "").trim().split("\\s+");
            return parts[parts.length - 1];
        }
        return null;
    }

    // ---- Explain why something is a helper ----
    private String explainWhy(Block block, List<Block> javaBlocks, Set<String> allNames) {
        if (isHelperByStructure(block)) {
            if ("METHOD".equals(block.type())) return "private method";
            if ("FIELD".equals(block.type())) return "private field/constant";
        }
        if (isHelperByUsage(block, javaBlocks, allNames)) {
            return "referenced inside main method";
        }
        return "unknown";
    }

    private String firstLine(String code) {
        return code.lines().findFirst().orElse(code);
    }
}
