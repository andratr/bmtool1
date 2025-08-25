package org.learningjava.bmtool1.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.learningjava.bmtool1.domain.config.MappingConfig;
import org.learningjava.bmtool1.domain.config.MappingRule;
import org.learningjava.bmtool1.domain.model.Block;
import org.learningjava.bmtool1.domain.model.BlockMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

public class BlockMapper {
    private static final Logger log = LoggerFactory.getLogger(BlockMapper.class);
    private final List<MappingRule> rules;
    private final HelperDetector helperDetector = new HelperDetector();

    public BlockMapper() {
        try (InputStream in = getClass().getResourceAsStream("/rules.yml")) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            MappingConfig config = mapper.readValue(in, MappingConfig.class);
            this.rules = config.getRules();
            log.info("Loaded {} mapping rules from rules.yml", rules.size());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load mapping rules", e);
        }
    }

    public List<BlockMapping> map(List<Block> plsqlBlocks, List<Block> javaBlocks) {
        log.info("Mapping {} PL/SQL blocks to {} Java blocks", plsqlBlocks.size(), javaBlocks.size());

        Map<Block, List<Block>> helpersByMain = helperDetector.detectHelpers(javaBlocks);
        List<BlockMapping> mappings = new ArrayList<>();

        for (Block plsql : plsqlBlocks) {
            // collect all candidates instead of findFirst
            List<Block> candidates = javaBlocks.stream()
                    .filter(j -> isEquivalent(plsql, j))
                    .toList();

            if (!candidates.isEmpty()) {
                // choose the best candidate (shortest text usually = more specific, e.g. eventCode)
                Block javaBlock = candidates.stream()
                        .min(Comparator.comparingInt(b -> b.text().length()))
                        .orElse(candidates.get(0));

                String pairId = UUID.randomUUID().toString();

                List<Block> candidateHelpers = helpersByMain.getOrDefault(javaBlock, List.of());
                List<String> helpers = filterUsedHelpers(javaBlock, candidateHelpers);

                // set to null if none are used
                if (helpers.isEmpty()) {
                    helpers = null;
                }

                mappings.add(new BlockMapping(
                        pairId,
                        plsql.text(),
                        javaBlock.text(),
                        plsql.type(),
                        javaBlock.type(),
                        helpers
                ));

                log.debug("Mapped [{}:{}] -> [{}:{}] with {} helpers ({} candidates found)",
                        plsql.type(), shorten(plsql.text()),
                        javaBlock.type(), shorten(javaBlock.text()),
                        helpers == null ? 0 : helpers.size(),
                        candidates.size());
            } else {
                log.warn("No mapping found for PLSQL block: {} ({})",
                        plsql.type(), plsql.sourcePath());
            }
        }

        return mappings;
    }

    private boolean isEquivalent(Block plsql, Block java) {
        for (MappingRule rule : rules) {
            if (plsql.type().equals(rule.getPlsqlType())
                    && java.type().equals(rule.getJavaType())) {

                // Check optional plsqlContains
                if (rule.getPlsqlContains() != null && !rule.getPlsqlContains().isBlank()) {
                    // extract the left-hand side variable (before :=)
                    String lhs = plsql.text().split(":=", 2)[0].trim().toLowerCase();

                    if (!lhs.matches(".*(" + rule.getPlsqlContains().toLowerCase() + ").*")) {
                        continue; // doesn’t match → skip
                    }
                }


                String javaText = java.text().toLowerCase();
                String contains = rule.getJavaContains().toLowerCase();

                // Prefer method signature matches
                if (javaText.contains(" " + contains + "(") || javaText.startsWith(contains + "(")) {
                    return true;
                }

                // fallback: substring match
                if (javaText.contains(contains)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Keep only helpers that are actually referenced in the javaBlock body.
     */
    private List<String> filterUsedHelpers(Block javaBlock, List<Block> helpers) {
        String body = javaBlock.text();

        return helpers.stream()
                .filter(helper -> {
                    String firstLine = helper.text().lines().findFirst().orElse("").trim();

                    // Extract identifier (method or field name)
                    String identifier = null;
                    if (firstLine.contains("(")) {
                        // method signature
                        identifier = firstLine
                                .substring(0, firstLine.indexOf("(")) // before '('
                                .replaceAll(".*\\s", ""); // last token
                    } else if (firstLine.contains("=")) {
                        // field declaration
                        identifier = firstLine
                                .substring(0, firstLine.indexOf("=")) // before '='
                                .replaceAll(".*\\s", ""); // last token
                    }

                    return identifier != null && body.contains(identifier);
                })
                .map(Block::text)
                .toList();
    }

    private String shorten(String text) {
        return text.length() > 40 ? text.substring(0, 37) + "..." : text;
    }
}
