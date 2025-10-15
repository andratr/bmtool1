package org.learningjava.bmtool1.infrastructure.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.learningjava.bmtool1.application.usecase.IngestPairsUseCase;
import org.learningjava.bmtool1.domain.model.BlockMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ingest")
public class IngestController {
    private final IngestPairsUseCase useCase;

    public IngestController(IngestPairsUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public List<MappingDTO> ingest(@Valid @RequestBody IngestRequest req) {
        try {
            var mappings = useCase.ingestDirectory(req.rootDir());
            return mappings.stream().map(MappingDTO::from).toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to ingest directory: " + req.rootDir(), e);
        }
    }

    public record IngestRequest(@NotBlank String rootDir) {
    }

    public record MappingDTO(
            String pairId,
            String plsqlType,
            String javaType,
            String plsqlSnippet,
            String javaSnippet
    ) {
        static MappingDTO from(BlockMapping m) {
            return new MappingDTO(
                    m.pairId(),
                    m.plsqlType(),
                    m.javaType(),
                    m.plsqlSnippet(),
                    m.javaSnippet()
            );
        }
    }

}
