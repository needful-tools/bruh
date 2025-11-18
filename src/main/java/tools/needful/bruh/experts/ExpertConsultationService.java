package tools.needful.bruh.experts;

import tools.needful.bruh.model.ExpertAnswer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExpertConsultationService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    /**
     * Retrieves relevant documents from vector store for a query.
     * This is the RETRIEVAL step - does not generate an answer yet.
     *
     * @param expertName Expert to query (null for all experts)
     * @param query User's question
     * @return List of relevant documents
     */
    public List<Document> retrieveRelevantDocuments(String expertName, String query) {
        log.info("Retrieving documents from expert '{}' for query: {}", expertName, query);

        // Build search request with optional expert filter
        SearchRequest.Builder builder = SearchRequest.builder()
            .query(query)
            .topK(5)
            .similarityThreshold(0.7);

        if (expertName != null) {
            builder.filterExpression(
                new FilterExpressionBuilder()
                    .eq("expert", expertName)
                    .build()
            );
        }

        SearchRequest searchRequest = builder.build();

        // Search Chroma
        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

        log.info("Retrieved {} relevant documents", relevantDocs.size());
        return relevantDocs;
    }

    /**
     * Legacy method - kept for backward compatibility.
     * Prefer using retrieveRelevantDocuments() + synthesis step.
     */
    @Deprecated
    public ExpertAnswer consultExpert(String expertName, String query) {
        log.info("Consulting expert '{}' for query: {}", expertName, query);

        List<Document> relevantDocs = retrieveRelevantDocuments(expertName, query);

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant documents found for expert: {}", expertName);
            return ExpertAnswer.builder()
                .expertName(expertName)
                .answer("I don't have information on that topic in my knowledge base.")
                .confidence(0.0)
                .build();
        }

        // Generate answer using LLM
        String context = relevantDocs.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = buildPrompt(expertName, context, query);

        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        // Extract sources
        List<String> sources = relevantDocs.stream()
            .map(doc -> (String) doc.getMetadata().get("source"))
            .distinct()
            .collect(Collectors.toList());

        return ExpertAnswer.builder()
            .expertName(expertName)
            .answer(answer)
            .sources(sources)
            .confidence(0.8)
            .build();
    }

    private String buildPrompt(String expertName, String context, String query) {
        return String.format("""
            You are the %s expert. Answer this question using ONLY the provided context.
            If the context doesn't contain the answer, say so clearly.

            Context:
            %s

            Question: %s

            Provide a clear, concise answer. Mention which sources you used if relevant.
            """,
            expertName, context, query
        );
    }
}
