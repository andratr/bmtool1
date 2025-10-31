// src/main/java/org/learningjava/bmtool1/infrastructure/adapter/in/web/ExperimentsController.java
package org.learningjava.bmtool1.infrastructure.adapter.in.web;

import org.learningjava.bmtool1.application.port.ExperimentStorePort;
import org.learningjava.bmtool1.domain.model.analytics.Experiment;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/experiments")
public class ExperimentsController {

    private final ExperimentStorePort store;

    public ExperimentsController(ExperimentStorePort store) {
        this.store = store;
    }

    @GetMapping
    public List<Experiment> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String embedding,
            @RequestParam(required = false) String llm
    ) {
        LocalDate today = LocalDate.now();
        LocalDate fromDate = (from != null) ? from : today.minusDays(14);
        LocalDate toDate = (to != null) ? to : today;

        List<Experiment> rows = store.listByDateRange(fromDate, toDate);

        if (embedding != null && !embedding.isBlank()) {
            String e = embedding.toLowerCase();
            rows = rows.stream()
                    .filter(x -> x.embeddingModel() != null && x.embeddingModel().toLowerCase().contains(e))
                    .toList();
        }
        if (llm != null && !llm.isBlank()) {
            String m = llm.toLowerCase();
            rows = rows.stream()
                    .filter(x -> x.llmModel() != null && x.llmModel().toLowerCase().contains(m))
                    .toList();
        }
        return rows;
    }
}
