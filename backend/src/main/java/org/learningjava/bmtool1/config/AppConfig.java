package org.learningjava.bmtool1.config;


import org.learningjava.bmtool1.application.port.EmbeddingPort;
import org.learningjava.bmtool1.application.port.FrameworkStorePort;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.infrastructure.adapter.out.ollama.OllamaEmbeddingAdapter;
import org.learningjava.bmtool1.infrastructure.adapter.out.weaviate.WeaviateFrameworkStoreAdapter;
import org.learningjava.bmtool1.infrastructure.adapter.out.weaviate.WeaviateVectorStoreAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    //objects with external dependencies
    @Bean
    EmbeddingPort embedding(@Value("${bmtool1.ollama.url}") String url,
                            @Value("${bmtool1.ollama.embeddingModel}") String model) {
        return new OllamaEmbeddingAdapter(url, model);
    }

    @Bean
    VectorStorePort store(@Value("${bmtool1.weaviate.url}") String wUrl,
                          @Value("${bmtool1.weaviate.apiKey:}") String apiKey,
                          @Value("${bmtool1.weaviate.className}") String className) {
        return new WeaviateVectorStoreAdapter(wUrl, apiKey, className);
    }

    @Bean
    FrameworkStorePort frameworkStore(
            @Value("${bmtool1.weaviate.url}") String url,
            @Value("${bmtool1.weaviate.apiKey:}") String apiKey
    ) {
        return new WeaviateFrameworkStoreAdapter(url, apiKey, "FrameworkSnippet");
    }

}
