package org.learningjava.bmtool1.config;


import org.learningjava.bmtool1.adapters.out.fs.FileSystemPairReader;
import org.learningjava.bmtool1.adapters.out.ollama.OllamaEmbeddingAdapter;
import org.learningjava.bmtool1.adapters.out.weaviate.WeaviateVectorStoreAdapter;
import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.PairReaderPort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.application.usecase.IngestPairsUseCase;
import org.learningjava.bmtool1.application.usecase.QueryRagUseCase;
import org.learningjava.bmtool1.domain.service.prompting.PromptBuilder;
import org.learningjava.bmtool1.domain.service.prompting.PromptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    PairReaderPort pairReader(){ return new FileSystemPairReader(); }

    @Bean
    EmbeddingPort embedding(@Value("${bmtool1.ollama.url}") String url,
                            @Value("${bmtool1.ollama.embeddingModel}") String model){
        return new OllamaEmbeddingAdapter(url, model);
    }

    @Bean
    VectorStorePort store(@Value("${bmtool1.weaviate.url}") String wUrl,
                          @Value("${bmtool1.weaviate.apiKey:}") String apiKey,
                          @Value("${bmtool1.weaviate.className}") String className){
        return new WeaviateVectorStoreAdapter(wUrl, apiKey, className);
    }

    @Bean
    PromptRepository promptRepository() {
        return new PromptRepository();
    }

    @Bean
    PromptBuilder promptBuilder(PromptRepository repo) {
        return new PromptBuilder(repo);
    }

    @Bean
    IngestPairsUseCase ingest(PairReaderPort pairReader,
                              VectorStorePort store,
                              EmbeddingPort embedding) {
        return new IngestPairsUseCase(pairReader, store, embedding);
    }

    @Bean
    QueryRagUseCase queryRagUseCase(EmbeddingPort embedding,
                                    VectorStorePort store,
                                    ChatRegistry chatRegistry,
                                    PromptRepository repo) {
        return new QueryRagUseCase(embedding, store, chatRegistry, new PromptBuilder(repo));
    }


}