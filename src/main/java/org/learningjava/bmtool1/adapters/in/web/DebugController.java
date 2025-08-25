package org.learningjava.bmtool1.adapters.in.web;

import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.domain.model.RetrievalResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/debug")
public class DebugController {

    private final EmbeddingPort embedding;
    private final VectorStorePort store;

    public DebugController(EmbeddingPort embedding, VectorStorePort store) {
        this.embedding = embedding;
        this.store = store;
    }

    @GetMapping("/search")
    public List<RetrievalResult> search(@RequestParam String q,
                                        @RequestParam(defaultValue = "5") int k) {
        float[] qVec = embedding.embed(q);
        return store.query(q, qVec, k);
    }
}
