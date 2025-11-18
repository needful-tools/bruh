package tools.needful.bruh.skills.builtin;

import tools.needful.bruh.skills.Skill;
import tools.needful.bruh.skills.SkillContext;
import tools.needful.bruh.skills.SkillResult;
import com.slack.api.bolt.App;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.search.SearchMessagesResponse;
import com.slack.api.model.MatchedItem;
import com.slack.api.model.Message;
import com.slack.api.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Data Access Skill provides access to Slack conversation data.
 *
 * This skill leverages Slack's Data Access APIs to:
 * - Search messages across the workspace
 * - Retrieve conversation history from channels
 * - Summarize channel discussions
 * - Find relevant historical context
 *
 * The skill uses:
 * - search.messages API for workspace-wide searches
 * - conversations.history API for channel-specific queries
 * - Future: assistant.search.context for AI-powered semantic search
 */
@Slf4j
@Component
public class DataAccessSkill implements Skill {

    @Autowired
    private App slackApp;

    @Value("${slack.bot.token}")
    private String botToken;

    @Override
    public String getName() {
        return "data-access";
    }

    @Override
    public String getDescription() {
        return "Search Slack conversations, retrieve channel history, and access historical message data. " +
               "Can search across workspace or specific channels.";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        try {
            String query = context.getQuery();
            String channelId = context.getChannelId();

            log.info("Executing data access for query: {} in channel: {}", query, channelId);

            // Determine if this is a channel-specific query or workspace-wide search
            if (isChannelSpecificQuery(query)) {
                return searchChannelHistory(channelId, query);
            } else {
                return searchWorkspaceMessages(query);
            }

        } catch (Exception e) {
            log.error("Error executing data access skill", e);
            return SkillResult.error("Failed to access Slack data: " + e.getMessage());
        }
    }

    /**
     * Searches for messages across the entire workspace
     */
    private SkillResult searchWorkspaceMessages(String query) throws IOException, SlackApiException {
        log.info("Searching workspace for: {}", query);

        SearchMessagesResponse response = slackApp.client()
            .searchMessages(req -> req
                .token(botToken)
                .query(query)
                .count(20)  // Limit to 20 results
                .sort("timestamp")
                .sortDir("desc")
            );

        if (!response.isOk()) {
            return SkillResult.error("Search failed: " + response.getError());
        }

        SearchResult searchResult = response.getMessages();
        if (searchResult == null || searchResult.getMatches().isEmpty()) {
            return SkillResult.success("No messages found matching: " + query);
        }

        // Format results
        String formattedResults = formatSearchResults(searchResult.getMatches());
        String summary = String.format("Found %d messages matching '%s':\n\n%s",
            searchResult.getTotal(),
            query,
            formattedResults
        );

        return SkillResult.success(summary);
    }

    /**
     * Retrieves recent history from a specific channel
     */
    private SkillResult searchChannelHistory(String channelId, String query) throws IOException, SlackApiException {
        log.info("Retrieving channel history for: {}", channelId);

        ConversationsHistoryResponse response = slackApp.client()
            .conversationsHistory(req -> req
                .token(botToken)
                .channel(channelId)
                .limit(100)  // Get last 100 messages
            );

        if (!response.isOk()) {
            return SkillResult.error("Failed to retrieve channel history: " + response.getError());
        }

        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            return SkillResult.success("No messages found in this channel.");
        }

        // Filter messages based on query keywords if provided
        List<Message> filteredMessages = filterMessagesByQuery(messages, query);

        String formattedResults = formatMessages(filteredMessages);
        String summary = String.format("Found %d relevant messages in this channel:\n\n%s",
            filteredMessages.size(),
            formattedResults
        );

        return SkillResult.success(summary);
    }

    /**
     * Determines if the query is asking for channel-specific information
     */
    private boolean isChannelSpecificQuery(String query) {
        String lowerQuery = query.toLowerCase();
        return lowerQuery.contains("this channel")
            || lowerQuery.contains("here")
            || lowerQuery.contains("recent")
            || lowerQuery.contains("history");
    }

    /**
     * Filters messages by query keywords
     */
    private List<Message> filterMessagesByQuery(List<Message> messages, String query) {
        if (query == null || query.trim().isEmpty()) {
            return messages.stream().limit(10).collect(Collectors.toList());
        }

        String[] keywords = query.toLowerCase().split("\\s+");

        return messages.stream()
            .filter(msg -> {
                String text = msg.getText() != null ? msg.getText().toLowerCase() : "";
                for (String keyword : keywords) {
                    if (text.contains(keyword)) {
                        return true;
                    }
                }
                return false;
            })
            .limit(10)
            .collect(Collectors.toList());
    }

    /**
     * Formats search results into readable text
     */
    private String formatSearchResults(List<MatchedItem> matches) {
        return matches.stream()
            .limit(10)
            .map(match -> {
                String text = match.getText() != null ? match.getText() : "";
                String username = match.getUsername() != null ? match.getUsername() : "Unknown";
                String channel = match.getChannel() != null ? match.getChannel().getName() : "Unknown";

                return String.format("• [#%s] %s: %s",
                    channel,
                    username,
                    truncate(text, 150)
                );
            })
            .collect(Collectors.joining("\n"));
    }

    /**
     * Formats messages into readable text
     */
    private String formatMessages(List<Message> messages) {
        return messages.stream()
            .map(msg -> {
                String text = msg.getText() != null ? msg.getText() : "";
                String user = msg.getUser() != null ? msg.getUser() : "Unknown";

                return String.format("• %s: %s",
                    user,
                    truncate(text, 150)
                );
            })
            .collect(Collectors.joining("\n"));
    }

    /**
     * Truncates text to specified length
     */
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
