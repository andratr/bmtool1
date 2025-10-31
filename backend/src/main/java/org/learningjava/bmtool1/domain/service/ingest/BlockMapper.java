package org.learningjava.bmtool1.domain.service.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.learningjava.bmtool1.domain.model.pairs.Block;
import org.learningjava.bmtool1.domain.model.pairs.BlockMapping;
import org.learningjava.bmtool1.domain.policy.MappingConfig;
import org.learningjava.bmtool1.domain.policy.MappingRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;


//used in ingest pair use case
@Component
public class BlockMapper {
    private static final Logger log = LoggerFactory.getLogger(BlockMapper.class);
    private final List<MappingRule> rules;
    private final HelperDetector helperDetector = new HelperDetector();

    public BlockMapper() {
        try (InputStream in = getClass().getResourceAsStream("/policy_rules/rules.yml")) {
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

        // Ensure helpers are tagged
        List<Block> taggedBlocks = helperDetector.detectHelpers(javaBlocks);
        List<Block> helpers = taggedBlocks.stream().filter(Block::isHelper).toList();
        List<Block> mains = taggedBlocks.stream().filter(b -> !b.isHelper()).toList();

        List<BlockMapping> mappings = new ArrayList<>();

        for (Block plsql : plsqlBlocks) {
            List<Block> candidates = mains.stream()
                    .filter(j -> isEquivalent(plsql, j))
                    .toList();

            if (!candidates.isEmpty()) {
                Block javaBlock = candidates.stream()
                        .min(Comparator.comparingInt(b -> b.text().length()))
                        .orElse(candidates.get(0));

                String pairId = UUID.randomUUID().toString();

                List<String> usedHelpers = filterUsedHelpers(javaBlock, helpers);
                if (usedHelpers.isEmpty()) {
                    usedHelpers = null;
                }

                String pairName = plsql.sourcePath() != null
                        ? Path.of(plsql.sourcePath()).getFileName().toString().replace(".plsql", "")
                        : "unknown";

                mappings.add(new BlockMapping(
                        pairId,
                        pairName, // NEW
                        plsql.text(),
                        javaBlock.text(),
                        plsql.type(),
                        javaBlock.type(),
                        usedHelpers
                ));

                log.debug("Mapped [{}:{}] -> [{}:{}] with {} helpers",
                        plsql.type(), shorten(plsql.text()),
                        javaBlock.type(), shorten(javaBlock.text()),
                        usedHelpers == null ? 0 : usedHelpers.size());
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
                    String lhs = plsql.text().split(":=", 2)[0].trim().toLowerCase();
                    if (!lhs.matches(".*(" + rule.getPlsqlContains().toLowerCase() + ").*")) {
                        continue;
                    }
                }

                String javaText = java.text().toLowerCase();
                String contains = rule.getJavaContains().toLowerCase();

                if (javaText.contains(" " + contains + "(") || javaText.startsWith(contains + "(")) {
                    return true;
                }
                if (javaText.contains(contains)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> filterUsedHelpers(Block javaBlock, List<Block> helpers) {
        String body = javaBlock.text();

        return helpers.stream()
                .filter(helper -> {
                    String firstLine = helper.text().lines().findFirst().orElse("").trim();
                    String identifier = null;

                    if (firstLine.contains("(")) {
                        identifier = firstLine
                                .substring(0, firstLine.indexOf("("))
                                .replaceAll(".*\\s", "");
                    } else if (firstLine.contains("=")) {
                        identifier = firstLine
                                .substring(0, firstLine.indexOf("="))
                                .replaceAll(".*\\s", "");
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
