package tools.needful.bruh.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

/**
 * Manual configuration for ChromaDB VectorStore
 */
@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.chroma.client.host:localhost}")
    private String chromaHost;

    @Value("${spring.ai.vectorstore.chroma.client.port:8000}")
    private int chromaPort;

    @Value("${spring.ai.vectorstore.chroma.collection-name:bruh-experts}")
    private String collectionName;

    @Bean
    public ChromaApi chromaApi(ObjectMapper objectMapper) {
        String baseUrl = String.format("http://%s:%d", chromaHost, chromaPort);
        return new ChromaApi(baseUrl, RestClient.builder(), objectMapper);
    }

    @Bean
    public VectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        if  (chromaApi.getTenant(ChromaApiConstants.DEFAULT_TENANT_NAME) == null) {
            chromaApi.createTenant(ChromaApiConstants.DEFAULT_TENANT_NAME);
        }

        if (chromaApi.getDatabase(ChromaApiConstants.DEFAULT_TENANT_NAME,
                ChromaApiConstants.DEFAULT_DATABASE_NAME) == null) {
            chromaApi.createDatabase(ChromaApiConstants.DEFAULT_TENANT_NAME, ChromaApiConstants.DEFAULT_DATABASE_NAME);
        }

        if (chromaApi.getCollection(ChromaApiConstants.DEFAULT_TENANT_NAME, ChromaApiConstants.DEFAULT_DATABASE_NAME,
                collectionName) == null) {
            var request = new ChromaApi.CreateCollectionRequest(collectionName);
            chromaApi.createCollection(ChromaApiConstants.DEFAULT_TENANT_NAME,
                    ChromaApiConstants.DEFAULT_DATABASE_NAME, request);
        }

        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .tenantName(ChromaApiConstants.DEFAULT_TENANT_NAME)
                .databaseName(ChromaApiConstants.DEFAULT_DATABASE_NAME)
                .collectionName(collectionName)
                .initializeSchema(true)
                .initializeImmediately(true)
                .build();
    }
}
