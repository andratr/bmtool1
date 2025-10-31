package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.domain.model.pairs.Block;
import org.learningjava.bmtool1.domain.model.query.TargetJavaClassForConsumer;
import org.learningjava.bmtool1.domain.service.templateCreator.TemplateCreator;
import org.learningjava.bmtool1.infrastructure.adapter.in.template.JavaGeneratorAdapter;
import org.learningjava.bmtool1.infrastructure.adapter.out.blockASTParser.PlsqlBlockExtractorAdapter;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TemplateUseCase {
    private final PlsqlBlockExtractorAdapter extractor = new PlsqlBlockExtractorAdapter();
    private final TemplateCreator mapper = new TemplateCreator();
    private final JavaGeneratorAdapter generator;

    public TemplateUseCase() throws Exception {
        this.generator = new JavaGeneratorAdapter();
    }

    public static void main(String[] args) throws Exception {
        Path inputDir = Path.of("C:\\Users\\40744\\Desktop\\bmtool1\\files\\anon-pairs");
        Path outputDir = Path.of("output");

        TemplateUseCase useCase = new TemplateUseCase();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.plsql")) {
            for (Path plsqlFile : stream) {
                try {
                    useCase.migrate(plsqlFile, outputDir);
                } catch (Exception e) {
                    System.err.println("❌ Failed for " + plsqlFile + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public void migrate(Path plsqlFile, Path outputDir) throws Exception {
        List<Block> blocks = extractor.extract(plsqlFile);
        TargetJavaClassForConsumer model = mapper.map(blocks);

        // Class name is eventCode (mapper already sets it)
        String rawClassName = model.className();
        String safeClassName = sanitizeClassName(rawClassName);

        // Extract number from filename (e.g. pair-7.plsql -> 7)
        int pairNumber = extractPairNumber(plsqlFile.getFileName().toString());

        String fileName = pairNumber + "_" + safeClassName + ".java";
        Path outputFile = outputDir.resolve(fileName);

        String javaSource = generator.generate(
                new TargetJavaClassForConsumer(
                        model.packageName(),
                        safeClassName,        // sanitized class name
                        model.inputType(),
                        model.methods()
                )
        );

        Files.createDirectories(outputDir);
        Files.writeString(outputFile, javaSource);

        System.out.println("✅ Generated: " + outputFile.toAbsolutePath());
    }

    private String sanitizeClassName(String name) {
        // Replace illegal chars with _
        String sanitized = name.replaceAll("[^A-Za-z0-9_]", "_");

        // Ensure it doesn’t start with a digit
        if (Character.isDigit(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    private int extractPairNumber(String fileName) {
        // Expected format: pair-7.plsql
        try {
            int dash = fileName.indexOf('-');
            int dot = fileName.indexOf('.');
            if (dash != -1 && dot != -1) {
                return Integer.parseInt(fileName.substring(dash + 1, dot));
            }
        } catch (Exception ignored) {
        }
        return 0; // fallback if parsing fails
    }
}
