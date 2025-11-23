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
import org.springframework.ai.chat.client.ChatClient;
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
 * - Retrieve thread context
 * - Find relevant historical context
 *
 * The skill uses:
 * - conversations.replies API for thread context (always checked first)
 * - search.messages API for workspace-wide searches
 * - conversations.history API for channel-specific queries
 * - LLM to determine appropriate search scope
 */
@Slf4j
@Component
public class DataAccessSkill implements Skill {

    @Autowired
    private App slackApp;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Value("${slack.bot.token}")
    private String botToken;

    @Value("${slack.user.token:}")
    private String userToken;

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
            String threadTs = context.getThreadTs();

            log.info("Executing data access for query: {} in channel: {}, thread: {}",
                query, channelId, threadTs);

            // Check if user explicitly requests workspace search
            if (isExplicitWorkspaceRequest(query)) {
                log.info("Explicit workspace search requested, skipping progressive search");
                return searchWorkspaceMessages(query);
            }

            // Progressive search with fallback: THREAD → CHANNEL → WORKSPACE

            // LEVEL 1: Try thread context (if in a thread)
            if (threadTs != null) {
                String threadContext = fetchThreadHistory(channelId, threadTs);
                log.info("Fetched thread context: {} characters", threadContext.length());

                if (!threadContext.trim().isEmpty()) {
                    if (isDataSufficient(query, threadContext, "thread")) {
                        log.info("Thread context sufficient, returning");
                        return SkillResult.success(
                            String.format("Thread context:\n\n%s", threadContext)
                        );
                    } else {
                        log.info("Thread context insufficient, escalating to channel search");
                    }
                }
            }

            // LEVEL 2: Try channel search
            SkillResult channelResult = searchChannelHistory(channelId, query);
            if (channelResult.isSuccess()) {
                String channelData = channelResult.getResult();
                if (isDataSufficient(query, channelData, "channel")) {
                    log.info("Channel search sufficient, returning");
                    return channelResult;
                } else {
                    log.info("Channel search insufficient, escalating to workspace search");
                }
            }

            // LEVEL 3: Final fallback - workspace search
            log.info("Performing workspace search (final level)");
            return searchWorkspaceMessages(query);

        } catch (Exception e) {
            log.error("Error executing data access skill", e);
            return SkillResult.error("Failed to access Slack data: " + e.getMessage());
        }
    }

    /**
     * Detects if the user explicitly requests workspace-wide search
     */
    private boolean isExplicitWorkspaceRequest(String query) {
        String lowerQuery = query.toLowerCase();
        return lowerQuery.contains("search workspace") ||
               lowerQuery.contains("workspace search") ||
               lowerQuery.contains("search everywhere") ||
               lowerQuery.contains("search all") ||
               lowerQuery.contains("all channels") ||
               lowerQuery.contains("across workspace") ||
               lowerQuery.contains("workspace-wide") ||
               lowerQuery.contains("in workspace") ||
               lowerQuery.contains("entire workspace");
    }

    /**
     * Uses LLM to determine if the search results are sufficient to answer the query
     */
    private boolean isDataSufficient(String query, String data, String searchLevel) {
        try {
            ChatClient chatClient = chatClientBuilder.build();

            String prompt = String.format(
                "Analyze if the following search results contain enough relevant information " +
                "to answer the user's query.\n\n" +
                "USER QUERY: \"%s\"\n\n" +
                "SEARCH RESULTS (from %s):\n%s\n\n" +
                "Does this data contain relevant and sufficient information to answer the query?\n" +
                "Reply with ONLY 'YES' if the data is relevant and sufficient.\n" +
                "Reply with ONLY 'NO' if the data is insufficient, irrelevant, or empty.\n\n" +
                "Your response:",
                query,
                searchLevel,
                data.length() > 1500 ? data.substring(0, 1500) + "..." : data
            );

            String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content()
                .trim()
                .toUpperCase();

            boolean sufficient = response.contains("YES");
            log.info("LLM sufficiency decision for {}: {}", searchLevel, sufficient);
            return sufficient;

        } catch (Exception e) {
            log.warn("Error checking data sufficiency, assuming insufficient", e);
            return false; // Escalate on error
        }
    }

    /**
     * Fetches the full thread history for context
     */
    private String fetchThreadHistory(String channelId, String threadTs) throws IOException, SlackApiException {
        var response = slackApp.client()
            .conversationsReplies(req -> req
                .token(botToken)
                .channel(channelId)
                .ts(threadTs)
                .limit(100)
            );

        if (!response.isOk()) {
            log.warn("Failed to fetch thread history: {}", response.getError());
            return "";
        }

        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        return messages.stream()
            .map(msg -> {
                String user = msg.getUser() != null ? msg.getUser() : "Unknown";
                String text = msg.getText() != null ? msg.getText() : "";
                return String.format("%s: %s", user, text);
            })
            .collect(Collectors.joining("\n"));
    }


    /**
     * Searches for messages across the entire workspace
     */
    private SkillResult searchWorkspaceMessages(String query) throws IOException, SlackApiException {
        log.info("Searching workspace for: {}", query);

        // Use user token for search (bot tokens not allowed)
        String searchToken = userToken != null && !userToken.isEmpty() ? userToken : botToken;

        SearchMessagesResponse response = slackApp.client()
            .searchMessages(req -> req
                .token(searchToken)
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
