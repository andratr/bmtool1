package org.learningjava.bmtool1.adapters.out.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.learningjava.bmtool1.application.port.SnippetRepository;
import org.learningjava.bmtool1.domain.model.PlsqlSnippet;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class FileSnippetRepository implements SnippetRepository {

    private final Path path = Path.of("snippets.json");
    private final ObjectMapper om = new ObjectMapper();

    private List<PlsqlSnippet> load() {
        if (!Files.exists(path)) return new ArrayList<>();
        try {
            return Arrays.asList(om.readValue(path.toFile(), PlsqlSnippet[].class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveAll(List<PlsqlSnippet> snippets) {
        try {
            om.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), snippets);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void savePending(PlsqlSnippet snippet) {
        var all = new ArrayList<>(load());
        all.add(snippet);
        saveAll(all);
    }

    @Override
    public List<PlsqlSnippet> findPending() {
        return load().stream()
                .filter(s -> s.javaTranslation() == null || s.javaTranslation().isBlank())
                .toList();
    }

    @Override
    public void saveTranslated(PlsqlSnippet snippet) {
        var all = new ArrayList<>(load());
        all.removeIf(s -> s.eventCode().equals(snippet.eventCode())
                && s.type().equals(snippet.type())
                && s.content().equals(snippet.content()));
        all.add(snippet);
        saveAll(all);
    }

    @Override
    public List<PlsqlSnippet> findTranslatedByEventCode(String eventCode) {
        return Collections.singletonList(load().stream()
                .filter(s -> eventCode.equals(s.eventCode()) && s.javaTranslation() != null)
                .findFirst()
                .orElse(null));
    }

    @Override
    public List<PlsqlSnippet> findAllTranslated() {
        return load().stream()
                .filter(s -> s.javaTranslation() != null && !s.javaTranslation().isBlank())
                .toList();
    }

    @Override
    public Map<String, List<PlsqlSnippet>> findAllTranslatedGrouped() {
        return findAllTranslated().stream()
                .collect(Collectors.groupingBy(PlsqlSnippet::eventCode));
    }

}
