package tools.needful.bruh.gemini;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI EmbeddingModel implementation that uses the Gemini Embedding API
 */
public class GeminiEmbeddingModel extends AbstractEmbeddingModel {

    private final GeminiEmbeddingClient embeddingClient;

    public GeminiEmbeddingModel(GeminiEmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = new ArrayList<>();

        // Extract text from the request inputs
        for (Object input : request.getInstructions()) {
            if (input instanceof String) {
                texts.add((String) input);
            } else if (input instanceof Document) {
                texts.add(((Document) input).getText());
            }
        }

        // Call Gemini Embedding API for each text
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            List<Double> embeddingValues = embeddingClient.embed(texts.get(i));

            // Convert List<Double> to float[]
            float[] embeddingArray = new float[embeddingValues.size()];
            for (int j = 0; j < embeddingValues.size(); j++) {
                embeddingArray[j] = embeddingValues.get(j).floatValue();
            }

            embeddings.add(new Embedding(embeddingArray, i));
        }

        return new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata());
    }

    @Override
    public float[] embed(Document document) {
        List<Double> embeddingValues = embeddingClient.embed(document.getText());

        // Convert List<Double> to float[]
        float[] embeddingArray = new float[embeddingValues.size()];
        for (int j = 0; j < embeddingValues.size(); j++) {
            embeddingArray[j] = embeddingValues.get(j).floatValue();
        }

        return embeddingArray;
    }

    @Override
    public float[] embed(String text) {
        List<Double> embeddingValues = embeddingClient.embed(text);

        // Convert List<Double> to float[]
        float[] embeddingArray = new float[embeddingValues.size()];
        for (int j = 0; j < embeddingValues.size(); j++) {
            embeddingArray[j] = embeddingValues.get(j).floatValue();
        }

        return embeddingArray;
    }

    @Override
    public int dimensions() {
        // text-embedding-004 produces 768-dimensional embeddings
        return 768;
    }
}
