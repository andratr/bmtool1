package org.learningjava.bmtool1.adapters.in.web;

import org.learningjava.bmtool1.application.usecase.TranslateSnippetsUseCase;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/snippets")
public class SnippetTranslationController {

    private final TranslateSnippetsUseCase translator;

    public SnippetTranslationController(TranslateSnippetsUseCase translator) {
        this.translator = translator;
    }

    @PostMapping("/translate")
    public TranslationResponse translate(@RequestBody TranslationRequest req) {
        translator.translate(req.k(), req.provider(), req.model());
        return new TranslationResponse("✅ Translation completed for all pending snippets.");
    }

    // ---------- DTOs ----------
    public record TranslationRequest(int k, String provider, String model) {}
    public record TranslationResponse(String message) {}
}
