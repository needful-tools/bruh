package tools.needful.bruh.skills;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SkillRouter uses an LLM to intelligently determine which skill(s) to use for a given query.
 *
 * This allows for:
 * - Dynamic skill selection without hardcoded rules
 * - Multiple skills to be used for a single query
 * - Easy addition of new skills without modifying routing logic
 */
@Slf4j
@Component
public class SkillRouter {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private SkillRegistry skillRegistry;

    /**
     * Routes a query to one or more appropriate skills.
     *
     * @param query The user's query
     * @return List of skill names to execute (may be empty if no skills are needed)
     */
    public List<String> routeToSkills(String query) {
        String skillDescriptions = skillRegistry.getSkillDescriptions();

        String prompt = String.format("""
            You are a skill routing assistant. Given a user query and a list of available skills,
            determine which skill(s) should be used to answer the query.

            Available Skills:
            %s

            User Query: "%s"

            Instructions:
            - Select ONE or MORE skills that are relevant to answering this query
            - If multiple skills could help, list all of them
            - If NO skills are applicable, respond with "NONE"
            - Respond ONLY with skill names, comma-separated (e.g., "slack-search, time")
            - Use exact skill names from the list above

            Skills to use:
            """,
            skillDescriptions,
            query
        );

        ChatClient chatClient = chatClientBuilder.build();
        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content()
            .toLowerCase()
            .trim();

        log.info("LLM skill routing response: {}", response);

        // Parse the response
        List<String> selectedSkills = parseSkillSelection(response);

        log.info("Query routed to skills: {}", selectedSkills);
        return selectedSkills;
    }

    /**
     * Parses the LLM response to extract skill names
     */
    private List<String> parseSkillSelection(String response) {
        if (response.contains("none") || response.isEmpty()) {
            return new ArrayList<>();
        }

        // Split by comma and clean up
        return Arrays.stream(response.split("[,;]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .filter(s -> !s.equals("none"))
            .filter(s -> skillRegistry.getSkill(s) != null) // Verify skill exists
            .collect(Collectors.toList());
    }
}
