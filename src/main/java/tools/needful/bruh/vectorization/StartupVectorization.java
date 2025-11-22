package tools.needful.bruh.vectorization;

import tools.needful.bruh.experts.Expert;
import tools.needful.bruh.experts.ExpertRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class StartupVectorization {

    @Autowired
    @Lazy
    private VectorStore vectorStore;

    @Autowired
    private DocumentLoader documentLoader;

    @Autowired
    private DocumentChunker documentChunker;

    @Autowired
    private ExpertRegistry expertRegistry;

    @Value("${agent.experts.base-path}")
    private String expertsBasePath;

    @EventListener(ApplicationReadyEvent.class)
    public void vectorizeOnStartup() {
        log.info("🚀 Starting expert discovery and vectorization...");

        File expertsDir = new File(expertsBasePath);
        if (!expertsDir.exists()) {
            log.warn("Experts directory not found: {}", expertsBasePath);
            return;
        }

        File[] expertFolders = expertsDir.listFiles(File::isDirectory);
        if (expertFolders == null || expertFolders.length == 0) {
            log.warn("No expert folders found in: {}", expertsBasePath);
            return;
        }

        for (File expertFolder : expertFolders) {
            String expertName = expertFolder.getName();
            try {
                vectorizeExpert(expertName, expertFolder);
            } catch (Exception e) {
                log.error("Failed to vectorize expert: {}", expertName, e);
            }
        }

        log.info("✅ Vectorization complete. {} experts ready.",
                 expertRegistry.count());
    }

    private void vectorizeExpert(String expertName, File expertFolder) {
        // 1. Load all docs
        List<Document> rawDocs = documentLoader.loadRecursively(expertFolder);

        if (rawDocs.isEmpty()) {
            log.warn("No documents found for expert: {}", expertName);
            return;
        }

        // 2. Chunk documents
        List<Document> chunks = documentChunker.chunk(rawDocs);

        // 3. Add metadata
        chunks.forEach(chunk -> {
            chunk.getMetadata().put("expert", expertName);
            chunk.getMetadata().put("indexed_at", Instant.now().toString());
        });

        // 4. Store in Chroma (vectorization happens automatically)
        vectorStore.add(chunks);

        // 5. Register expert
        Expert expert = Expert.builder()
            .name(expertName)
            .documentCount(rawDocs.size())
            .chunkCount(chunks.size())
            .build();

        expertRegistry.register(expert);

        log.info("✓ Vectorized expert: {} ({} chunks from {} documents)",
                 expertName, chunks.size(), rawDocs.size());
    }
}
