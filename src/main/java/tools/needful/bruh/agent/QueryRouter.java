package tools.needful.bruh.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QueryRouter {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    public RouteDecision route(String query) {
        String prompt = String.format("""
            Analyze this query and decide how to handle it:

            Query: "%s"

            Should this be handled by:
            1. A skill (general capability like time, data access to Slack conversations/history, search)
            2. An expert (domain-specific knowledge from documentation)
            3. Both

            Respond with: SKILL, EXPERT, or BOTH
            """,
            query
        );

        ChatClient chatClient = chatClientBuilder.build();
        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content()
            .toUpperCase()
            .trim();

        RouteDecision decision = RouteDecision.EXPERT; // default
        if (response.contains("SKILL")) {
            decision = RouteDecision.SKILL;
        } else if (response.contains("BOTH")) {
            decision = RouteDecision.BOTH;
        }

        log.info("Query routed to: {}", decision);
        return decision;
    }

    public enum RouteDecision {
        SKILL, EXPERT, BOTH
    }
}
