package org.learningjava.bmtool1.domain.service.ASTParser;

import org.learningjava.bmtool1.domain.model.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HelperDetector {
    private static final Logger log = LoggerFactory.getLogger(HelperDetector.class);

    /**
     * Returns the same list of blocks, but with helpers marked (isHelper = true).
     */
    public List<Block> detectHelpers(List<Block> javaBlocks) {
        // Naive rule: mark helpers if they are private OR if referenced in another method
        return javaBlocks.stream().map(block -> {
            boolean isHelper = false;

            if ("METHOD".equals(block.type())) {
                String firstLine = block.text().lines().findFirst().orElse("").trim();

                // Example rules (tweak as needed):
                if (firstLine.startsWith("private")) {
                    isHelper = true;
                }
                if (firstLine.contains("Predicate") && !firstLine.contains("@Override")) {
                    isHelper = true;
                }
            }

            if (isHelper) {
                log.debug("✅ Marked as HELPER: {}", shorten(block.text()));
                return new Block(block.type(), block.text(), block.sourcePath(), true);
            } else {
                return block;
            }
        }).toList();
    }

    private String shorten(String text) {
        return text.length() > 60 ? text.substring(0, 57) + "..." : text;
    }
}
