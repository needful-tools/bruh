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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for direct Gemini API calls using API key authentication
 */
@Slf4j
@Service
public class GeminiApiClient {

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.0-flash}")
    private String model;

    @Value("${gemini.api.temperature:0.7}")
    private Double temperature;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiApiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate content using the Gemini API
     */
    public String generateContent(String prompt) {
        try {
            String url = String.format(GEMINI_API_URL, model);

            // Build request body
            Map<String, Object> requestBody = buildRequestBody(prompt);

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-goog-api-key", apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Make API call
            log.debug("Calling Gemini API with model: {}", model);
            String response = restTemplate.exchange(url, HttpMethod.POST, request, String.class).getBody();

            // Parse response
            return parseResponse(response);

        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Failed to generate content from Gemini API: " + e.getMessage(), e);
        }
    }

    /**
     * Build the request body matching Gemini API format
     */
    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> part = new HashMap<>();
        part.put("parts", List.of(textPart));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(part));

        // Add generation config if temperature is set
        if (temperature != null) {
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", temperature);
            requestBody.put("generationConfig", generationConfig);
        }

        return requestBody;
    }

    /**
     * Parse the Gemini API response to extract the generated text
     */
    private String parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // Navigate: candidates[0].content.parts[0].text
            JsonNode candidates = root.path("candidates");
            if (candidates.isEmpty()) {
                throw new RuntimeException("No candidates in response");
            }

            JsonNode content = candidates.get(0).path("content");
            JsonNode parts = content.path("parts");
            if (parts.isEmpty()) {
                throw new RuntimeException("No parts in response");
            }

            JsonNode text = parts.get(0).path("text");
            if (text.isMissingNode()) {
                throw new RuntimeException("No text in response");
            }

            return text.asText();

        } catch (Exception e) {
            log.error("Error parsing Gemini API response: {}", response, e);
            throw new RuntimeException("Failed to parse Gemini API response: " + e.getMessage(), e);
        }
    }
}
