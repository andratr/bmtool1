//package org.learningjava.bmtool1.adapters.in.web;
//
//import org.learningjava.bmtool1.application.usecase.ExtractSnippetsUseCase;
//import org.springframework.web.bind.annotation.*;
//
//import java.nio.file.Path;
//
//@RestController
//@RequestMapping("/snippets")
//public class SnippetExtractionController {
//
//    private final ExtractSnippetsUseCase extractor;
//
//    public SnippetExtractionController(ExtractSnippetsUseCase extractor) {
//        this.extractor = extractor;
//    }
//
//    /**
//     * Extract snippets from a given PL/SQL file path and store them in snippets.json
//     *
//     * Example:
//     * POST /snippets/extract?file=files/anon-pairs/pair-1.plsql
//     */
//    @PostMapping("/extract")
//    public ExtractionResponse extract(@RequestParam String file) throws Exception {
//        extractor.extract(Path.of(file));
//        return new ExtractionResponse("✅ Extracted snippets from " + file);
//    }
//
//    // ---------- DTO ----------
//    public record ExtractionResponse(String message) {}
//}
