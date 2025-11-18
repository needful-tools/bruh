package tools.needful.bruh.vectorization;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class DocumentChunker {

    @Value("${agent.experts.chunk-size:500}")
    private int chunkSize;

    @Value("${agent.experts.chunk-overlap:50}")
    private int chunkOverlap;

    public List<Document> chunk(List<Document> documents) {
        List<Document> chunks = new ArrayList<>();

        for (Document doc : documents) {
            chunks.addAll(chunkDocument(doc));
        }

        return chunks;
    }

    private List<Document> chunkDocument(Document doc) {
        List<Document> chunks = new ArrayList<>();
        String content = doc.getText();

        // Simple paragraph-based chunking
        List<String> paragraphs = Arrays.asList(content.split("\n\n+"));

        for (String para : paragraphs) {
            if (para.trim().isEmpty()) {
                continue;
            }

            Document chunk = new Document(para.trim());
            chunk.getMetadata().putAll(doc.getMetadata());
            chunks.add(chunk);
        }

        return chunks;
    }
}
