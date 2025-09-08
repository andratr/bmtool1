package org.learningjava.bmtool1.adapters.in.web;

import org.learningjava.bmtool1.application.port.SnippetRepository;
import org.learningjava.bmtool1.domain.model.PlsqlSnippet;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/snippets")
public class SnippetQueryController {

    private final SnippetRepository repo;

    public SnippetQueryController(SnippetRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/translated")
    public List<PlsqlSnippet> allTranslated() {
        return repo.findAllTranslated();
    }

    @GetMapping("/translated/grouped")
    public Map<String, List<PlsqlSnippet>> allTranslatedGrouped() {
        return repo.findAllTranslatedGrouped();
    }

    @GetMapping("/translated/{eventCode}")
    public List<PlsqlSnippet> translatedByEvent(@PathVariable String eventCode) {
        return repo.findTranslatedByEventCode(eventCode);
    }


}
