package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.application.port.SnippetRepository;
import org.learningjava.bmtool1.adapters.in.template.JavaGeneratorAdapter;
import org.learningjava.bmtool1.domain.model.PlsqlSnippet;
import org.learningjava.bmtool1.domain.model.template.TargetJavaClassForConsumer;

import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class AssembleClassUseCase {

    private final SnippetRepository repo;
    private final JavaGeneratorAdapter generator;

    public AssembleClassUseCase(SnippetRepository repo, JavaGeneratorAdapter generator) {
        this.repo = repo;
        this.generator = generator;
    }

    public void assemble(String eventCode, Path outputDir) throws Exception {
        List<PlsqlSnippet> snippets = repo.findTranslatedByEventCode(eventCode);
        if (snippets.isEmpty()) {
            System.out.println("⚠️ No translated snippets for " + eventCode);
            return;
        }

        String inputType = snippets.get(0).domain();
        String className = eventCode;

        List<String> methods = snippets.stream()
                .map(PlsqlSnippet::javaTranslation)
                .collect(Collectors.toList());

        TargetJavaClassForConsumer model = new TargetJavaClassForConsumer(
                "org.learningjava.rules",
                className,
                inputType,
                methods
        );

        String javaSource = generator.generate(model);
        Path outputFile = outputDir.resolve(className + ".java");

        Files.createDirectories(outputDir);
        Files.writeString(outputFile, javaSource);

        System.out.println("✅ Assembled " + outputFile.toAbsolutePath());
    }
}
