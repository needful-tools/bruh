package tools.needful.bruh.experts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ExpertRouter {

    @Autowired
    private ExpertRegistry expertRegistry;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    public List<String> routeToExperts(String query) {
        String prompt = buildRoutingPrompt(query);

        ChatClient chatClient = chatClientBuilder.build();
        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        // Parse response to get expert names
        // Simple implementation: look for expert names in response
        List<String> selectedExperts = expertRegistry.getAllExperts().stream()
            .map(Expert::getName)
            .filter(name -> response.toLowerCase().contains(name.toLowerCase()))
            .collect(Collectors.toList());

        if (selectedExperts.isEmpty()) {
            // Default to bot expert if nothing matches
            selectedExperts.add("bot");
        }

        log.info("Routed query to experts: {}", selectedExperts);
        return selectedExperts;
    }

    private String buildRoutingPrompt(String query) {
        String expertsList = expertRegistry.getAllExperts().stream()
            .map(e -> String.format("- %s: Expert on %s domain",
                                    e.getName(), e.getName()))
            .collect(Collectors.joining("\n"));

        return String.format("""
            You are a routing system. Determine which expert(s) should answer this query.

            Available Experts:
            %s

            User Query: "%s"

            Rules:
            - If query is about the bot itself, choose "bot"
            - If query spans multiple domains, list multiple experts
            - List expert names only, separated by commas

            Expert names to consult:
            """,
            expertsList, query
        );
    }
}
