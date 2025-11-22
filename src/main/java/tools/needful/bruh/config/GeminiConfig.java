package tools.needful.bruh.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.needful.bruh.gemini.GeminiApiChatModel;
import tools.needful.bruh.gemini.GeminiApiClient;
import tools.needful.bruh.gemini.GeminiEmbeddingClient;
import tools.needful.bruh.gemini.GeminiEmbeddingModel;

/**
 * Configuration for Gemini API integration
 */
@Configuration
public class GeminiConfig {

    @Bean
    public ChatModel chatModel(GeminiApiClient geminiApiClient) {
        return new GeminiApiChatModel(geminiApiClient);
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    public EmbeddingModel embeddingModel(GeminiEmbeddingClient embeddingClient) {
        return new GeminiEmbeddingModel(embeddingClient);
    }
}
