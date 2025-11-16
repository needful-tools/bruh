package tools.needful.bruh.vectorization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
public class DocumentLoader {

    public List<Document> loadRecursively(File directory) {
        List<Document> documents = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.toString().toLowerCase();
                    return fileName.endsWith(".md") ||
                           fileName.endsWith(".txt");
                })
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);

                        Document doc = new Document(content);
                        doc.getMetadata().put("source", path.getFileName().toString());
                        doc.getMetadata().put("path", path.toString());

                        documents.add(doc);
                    } catch (IOException e) {
                        log.error("Failed to read file: {}", path, e);
                    }
                });
        } catch (IOException e) {
            log.error("Failed to walk directory: {}", directory, e);
        }

        return documents;
    }
}
