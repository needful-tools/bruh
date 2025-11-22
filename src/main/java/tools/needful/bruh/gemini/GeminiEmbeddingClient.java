package tools.needful.bruh.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for Gemini Embedding API
 */
@Slf4j
@Service
public class GeminiEmbeddingClient {

    private static final String GEMINI_EMBEDDING_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent";

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.embedding-model:text-embedding-004}")
    private String embeddingModel;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiEmbeddingClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate embeddings for a single text
     */
    public List<Double> embed(String text) {
        try {
            String url = String.format(GEMINI_EMBEDDING_URL, embeddingModel);

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, String> part = new HashMap<>();
            part.put("text", text);
            content.put("parts", List.of(part));
            requestBody.put("content", content);

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-goog-api-key", apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Make API call
            log.debug("Calling Gemini Embedding API with model: {}", embeddingModel);
            String response = restTemplate.exchange(url, HttpMethod.POST, request, String.class).getBody();

            // Parse response
            return parseEmbeddingResponse(response);

        } catch (Exception e) {
            log.error("Error calling Gemini Embedding API", e);
            throw new RuntimeException("Failed to generate embeddings from Gemini API: " + e.getMessage(), e);
        }
    }

    /**
     * Generate embeddings for multiple texts
     */
    public List<List<Double>> embedBatch(List<String> texts) {
        List<List<Double>> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(embed(text));
        }
        return embeddings;
    }

    /**
     * Parse the Gemini Embedding API response to extract the embedding vector
     */
    private List<Double> parseEmbeddingResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // Navigate: embedding.values (array of floats)
            JsonNode embedding = root.path("embedding");
            JsonNode values = embedding.path("values");

            if (values.isMissingNode() || !values.isArray()) {
                throw new RuntimeException("No embedding values in response");
            }

            List<Double> result = new ArrayList<>();
            for (JsonNode value : values) {
                result.add(value.asDouble());
            }

            return result;

        } catch (Exception e) {
            log.error("Error parsing Gemini Embedding API response: {}", response, e);
            throw new RuntimeException("Failed to parse Gemini Embedding API response: " + e.getMessage(), e);
        }
    }
}
