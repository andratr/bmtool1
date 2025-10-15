package org.learningjava.bmtool1.infrastructure.adapter.in.web.debugControllers;

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
public class DebugControllers {

    private final EmbeddingPort embedding;
    private final VectorStorePort store;

    public DebugControllers(EmbeddingPort embedding, VectorStorePort store) {
        this.embedding = embedding;
        this.store = store;
    }

    @GetMapping("/searchRAG")
    public List<RetrievalResult> search(@RequestParam String q,
                                        @RequestParam(defaultValue = "5") int k) {
        float[] qVec = embedding.embed(q);
        return store.query(q, qVec, k);
    }

    @GetMapping("/ef")
    public List<RetrievalResult> seardch(@RequestParam String q,
                                         @RequestParam(defaultValue = "5") int k) {
        float[] qVec = embedding.embed(q);
        return store.query(q, qVec, k);
    }

}
