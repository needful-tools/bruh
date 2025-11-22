package tools.needful.bruh.gemini;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Content;

import java.util.List;

/**
 * Spring AI ChatModel implementation that uses the direct Gemini API
 */
public class GeminiApiChatModel implements ChatModel {

    private final GeminiApiClient geminiApiClient;

    public GeminiApiChatModel(GeminiApiClient geminiApiClient) {
        this.geminiApiClient = geminiApiClient;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // Extract the user message from the prompt
        String userMessage = prompt.getInstructions().stream()
            .map(Content::getText)
            .reduce("", (a, b) -> a + "\n" + b)
            .trim();

        // Call Gemini API
        String responseText = geminiApiClient.generateContent(userMessage);

        // Wrap in Spring AI's ChatResponse format
        Generation generation = new Generation(new AssistantMessage(responseText));
        return new ChatResponse(List.of(generation));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return null;
    }
}
