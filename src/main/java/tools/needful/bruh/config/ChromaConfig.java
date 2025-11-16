package tools.needful.bruh.config;

import org.springframework.ai.chroma.ChromaApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.ChromaVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChromaConfig {

    @Value("${spring.ai.vectorstore.chroma.client.host}")
    private String chromaUrl;

    @Value("${spring.ai.vectorstore.chroma.collection-name}")
    private String collectionName;

    @Bean
    public ChromaApi chromaApi() {
        return new ChromaApi(chromaUrl);
    }

    @Bean
    public ChromaVectorStore chromaVectorStore(
            ChromaApi chromaApi,
            EmbeddingModel embeddingModel) {

        return new ChromaVectorStore(
            embeddingModel,
            chromaApi,
            collectionName,
            true  // initialize schema
        );
    }
}
