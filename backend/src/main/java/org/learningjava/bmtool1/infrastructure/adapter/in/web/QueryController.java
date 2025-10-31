//package org.learningjava.bmtool1.infrastructure.adapter.in.web;
//
//import org.learningjava.bmtool1.application.usecase.QueryRagUseCase;
//import org.learningjava.bmtool1.domain.model.query.Answer;
//import org.learningjava.bmtool1.domain.model.query.Query;
//import org.learningjava.bmtool1.domain.model.pairs.RetrievalResult;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/query")
//public class QueryController {
//
//    private final QueryRagUseCase queryRag;
//
//    public QueryController(QueryRagUseCase queryRag) {
//        this.queryRag = queryRag;
//    }
//
//    @PostMapping
//    public QueryResponse ask(@RequestBody QueryRequest req) {
//        // fallback if old clients don’t send embeddingModel
//        String embeddingModel = (req.embeddingModel() == null || req.embeddingModel().isBlank())
//                ? " nomic-embed-text"
//                : req.embeddingModel();
//
//        Answer answer = queryRag.ask(
//                new Query(req.question()),
//                req.k(),
//                req.provider(),
//                req.model(),
//                embeddingModel
//        );
//
//        return new QueryResponse(answer.text(), answer.retrievalResults());
//    }
//
//    // ---------- DTOs ----------
//    public record QueryRequest(
//            String question,
//            int k,
//            String provider,
//            String model,
//            String embeddingModel // NEW//todo
//    ) {
//    }
//
//    public record QueryResponse(String text, List<RetrievalResult> citations) {
//    }
//}
