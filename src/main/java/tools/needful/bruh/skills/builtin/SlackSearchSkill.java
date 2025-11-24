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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of evaluating search sufficiency with extracted context
 */
class SufficiencyResult {
    private final boolean sufficient;
    private final String extractedContext;

    public SufficiencyResult(boolean sufficient, String extractedContext) {
        this.sufficient = sufficient;
        this.extractedContext = extractedContext;
    }

    public boolean isSufficient() {
        return sufficient;
    }

    public String getExtractedContext() {
        return extractedContext;
    }
}

/**
 * Slack Search Skill provides intelligent search across Slack conversations.
 *
 * This skill leverages Slack's search APIs with LLM-guided query optimization:
 * - Search messages across the workspace with iterative refinement
 * - Retrieve conversation history from channels
 * - Retrieve thread context
 * - Find relevant historical context
 *
 * The skill uses:
 * - conversations.replies API for thread context (always checked first)
 * - search.messages API for workspace-wide searches with smart query generation
 * - conversations.history API for channel-specific queries
 * - LLM to determine appropriate search scope and optimize queries
 */
@Slf4j
@Component
public class SlackSearchSkill implements Skill {

    private static final int MAX_SLACK_SEARCH_RESULTS_COUNT = 100;
    private static final int MAX_ITERATIONS = 3;

    @Autowired
    private App slackApp;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Value("${slack.bot.token}")
    private String botToken;

    @Value("${slack.user.token:}")
    private String userToken;

    @Value("${slack.workspace.domain:}")
    private String workspaceDomain;

    private String botUserId = null; // Cached bot user ID

    @Override
    public String getName() {
        return "slack-search";
    }

    @Override
    public String getDescription() {
        return "Intelligently search Slack conversations using LLM-guided query optimization. " +
               "Searches across workspace, channels, and threads with iterative refinement to find the most relevant messages.";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        try {
            String query = context.getQuery();
            String channelId = context.getChannelId();
            String threadTs = context.getThreadTs();

            log.info("Executing Slack search for query: {} in channel: {}, thread: {}",
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
            log.error("Error executing Slack search skill", e);
            return SkillResult.error("Failed to search Slack: " + e.getMessage());
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
     * Searches for messages across the entire workspace using iterative LLM-guided search
     */
    private SkillResult searchWorkspaceMessages(String query) throws IOException, SlackApiException {
        log.info("Starting smart workspace search for: {}", query);

        List<MatchedItem> allResults = new ArrayList<>();
        List<String> attemptedQueries = new ArrayList<>();
        SufficiencyResult sufficiencyResult = null;

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            log.info("Search iteration {}/{}", iteration + 1, MAX_ITERATIONS);

            // Generate optimized Slack search query using LLM
            String slackQuery = generateSlackSearchQuery(query, attemptedQueries, allResults);
            attemptedQueries.add(slackQuery);

            log.info("Generated Slack query: {}", slackQuery);

            // Execute search
            List<MatchedItem> newResults = executeSlackSearch(slackQuery);

            if (newResults.isEmpty()) {
                log.info("No results for query: {}", slackQuery);
            } else {
                // Merge and deduplicate results
                allResults = mergeResults(allResults, newResults);
                log.info("Total unique results so far: {}", allResults.size());
            }

            // Check if we have sufficient results and extract relevant context
            if (!allResults.isEmpty()) {
                sufficiencyResult = areSearchResultsSufficient(query, allResults, iteration + 1);
                if (sufficiencyResult.isSufficient()) {
                    log.info("Search results deemed sufficient after {} iteration(s)", iteration + 1);
                    break;
                }
            }

            if (iteration < MAX_ITERATIONS - 1) {
                log.info("Results insufficient, will refine search query");
            }
        }

        if (allResults.isEmpty()) {
            return SkillResult.success("No messages found matching: " + query);
        }

        // Use extracted context from LLM (compressed and relevant)
        // instead of raw formatted results
        String extractedContext = sufficiencyResult != null
            ? sufficiencyResult.getExtractedContext()
            : formatSearchResults(allResults); // Fallback to raw formatting

        String summary = String.format("Relevant information from Slack (searched %d iteration(s), found %d messages):\n\n%s",
            attemptedQueries.size(),
            allResults.size(),
            extractedContext
        );

        return SkillResult.success(summary);
    }

    /**
     * Generates an optimized Slack search query using LLM
     */
    private String generateSlackSearchQuery(String userQuery, List<String> previousQueries, List<MatchedItem> previousResults) {
        try {
            ChatClient chatClient = chatClientBuilder.build();

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("You are a Slack search query optimizer. Generate an effective Slack search query.\n\n");
            promptBuilder.append("USER'S QUESTION: \"").append(userQuery).append("\"\n\n");

            if (previousQueries.isEmpty()) {
                promptBuilder.append("Generate a Slack search query that will help find relevant messages to answer the user's question.\n");
            } else {
                promptBuilder.append("Previous search attempts:\n");
                for (int i = 0; i < previousQueries.size(); i++) {
                    promptBuilder.append(i + 1).append(". Query: \"").append(previousQueries.get(i)).append("\"\n");
                }
                promptBuilder.append("\n");

                if (previousResults.isEmpty()) {
                    promptBuilder.append("Previous searches found NO results.\n");
                } else {
                    promptBuilder.append("Previous searches found ").append(previousResults.size())
                        .append(" results, but they were insufficient.\n");
                    promptBuilder.append("Sample of what was found:\n");
                    previousResults.stream().limit(3).forEach(item ->
                        promptBuilder.append("- ").append(truncate(item.getText(), 100)).append("\n")
                    );
                }
                promptBuilder.append("\nGenerate a DIFFERENT search query that takes a new approach to find relevant information.\n");
            }

            promptBuilder.append("\nSlack search tips:\n");
            promptBuilder.append("- Use keywords from the question\n");
            promptBuilder.append("- Try different word variations and synonyms\n");
            promptBuilder.append("- Use specific terms mentioned in the question\n");
            promptBuilder.append("- Keep it simple - 2-5 keywords work best\n");
            promptBuilder.append("- You can use operators like 'from:@user' or 'in:#channel' if relevant\n\n");
            promptBuilder.append("Respond with ONLY the search query, nothing else.\n");

            String response = chatClient.prompt()
                .user(promptBuilder.toString())
                .call()
                .content()
                .trim();

            // Clean up the response (remove quotes if LLM added them)
            response = response.replaceAll("^[\"']|[\"']$", "");

            return response;

        } catch (Exception e) {
            log.warn("Error generating search query, using original query", e);
            return userQuery; // Fallback to original query
        }
    }

    /**
     * Executes a Slack search and returns results, filtering out bot mentions
     */
    private List<MatchedItem> executeSlackSearch(String slackQuery) throws IOException, SlackApiException {
        // Use user token for search (bot tokens not allowed)
        String searchToken = userToken != null && !userToken.isEmpty() ? userToken : botToken;

        SearchMessagesResponse response = slackApp.client()
            .searchMessages(req -> req
                .token(searchToken)
                .query(slackQuery)
                .count(MAX_SLACK_SEARCH_RESULTS_COUNT)
                .sort("timestamp")
                .sortDir("desc")
            );

        if (!response.isOk()) {
            log.warn("Search failed: {}", response.getError());
            return List.of();
        }

        SearchResult searchResult = response.getMessages();
        if (searchResult == null || searchResult.getMatches() == null) {
            return List.of();
        }

        // Filter out messages that mention the bot (usually questions TO the bot, not answers)
        List<MatchedItem> filtered = filterBotMentions(searchResult.getMatches());

        int afterBotFilter = filtered.size();
        if (afterBotFilter < searchResult.getMatches().size()) {
            log.info("Filtered out {} bot mention(s) from search results",
                searchResult.getMatches().size() - afterBotFilter);
        }

        // Filter out private channels to prevent leaking private information
        filtered = filterPrivateChannels(filtered);

        if (filtered.size() < afterBotFilter) {
            log.info("Filtered out {} private channel message(s) from search results",
                afterBotFilter - filtered.size());
        }

        return filtered;
    }

    /**
     * Gets the bot's user ID (cached after first call)
     */
    private String getBotUserId() {
        if (botUserId != null) {
            return botUserId;
        }

        try {
            var response = slackApp.client().authTest(req -> req.token(botToken));
            if (response.isOk()) {
                botUserId = response.getUserId();
                log.info("Bot user ID: {}", botUserId);
            }
        } catch (Exception e) {
            log.warn("Failed to get bot user ID", e);
        }

        return botUserId;
    }

    /**
     * Filters out messages that mention the bot (these are usually questions TO the bot)
     */
    private List<MatchedItem> filterBotMentions(List<MatchedItem> messages) {
        String botId = getBotUserId();
        if (botId == null) {
            return messages; // Can't filter if we don't know the bot ID
        }

        return messages.stream()
            .filter(msg -> {
                String text = msg.getText();
                if (text == null) {
                    return true;
                }

                // Filter out if message mentions the bot by ID (<@U12345>) or name (@bruh)
                boolean mentionsBot = text.contains("<@" + botId + ">") ||
                                    text.toLowerCase().contains("@bruh");

                return !mentionsBot; // Keep messages that DON'T mention the bot
            })
            .collect(Collectors.toList());
    }

    /**
     * Filters out messages from private channels to prevent information leakage
     */
    private List<MatchedItem> filterPrivateChannels(List<MatchedItem> messages) {
        return messages.stream()
            .filter(msg -> {
                var channel = msg.getChannel();
                if (channel == null || channel.getId() == null) {
                    return false; // Skip if no channel info
                }

                String channelId = channel.getId();

                // Slack channel ID prefixes:
                // C = Public channel
                // G = Private channel (group)
                // D = Direct message
                // Other prefixes exist but are less common

                // Only keep messages from public channels (starting with "C")
                boolean isPublicChannel = channelId.startsWith("C");

                if (!isPublicChannel) {
                    log.debug("Filtering out message from non-public channel: {} (ID: {})",
                        channel.getName() != null ? channel.getName() : "Unknown",
                        channelId);
                }

                return isPublicChannel;
            })
            .collect(Collectors.toList());
    }

    /**
     * Merges and deduplicates search results based on message timestamp and channel
     */
    private List<MatchedItem> mergeResults(List<MatchedItem> existing, List<MatchedItem> newResults) {
        List<MatchedItem> merged = new ArrayList<>(existing);

        for (MatchedItem newItem : newResults) {
            boolean isDuplicate = existing.stream().anyMatch(existingItem ->
                isSameMessage(existingItem, newItem)
            );

            if (!isDuplicate) {
                merged.add(newItem);
            }
        }

        return merged;
    }

    /**
     * Checks if two matched items represent the same message
     */
    private boolean isSameMessage(MatchedItem item1, MatchedItem item2) {
        String ts1 = item1.getTs();
        String ts2 = item2.getTs();

        if (ts1 == null || ts2 == null) {
            return false;
        }

        // Compare timestamps and channel
        boolean sameTimestamp = ts1.equals(ts2);

        String channel1 = item1.getChannel() != null ? item1.getChannel().getId() : null;
        String channel2 = item2.getChannel() != null ? item2.getChannel().getId() : null;
        boolean sameChannel = (channel1 != null && channel1.equals(channel2));

        return sameTimestamp && sameChannel;
    }

    /**
     * Uses LLM to determine if search results are sufficient and extract relevant context
     */
    private SufficiencyResult areSearchResultsSufficient(String userQuery, List<MatchedItem> results, int iterationNumber) {
        try {
            ChatClient chatClient = chatClientBuilder.build();

            // Format results for LLM evaluation
            StringBuilder resultsText = new StringBuilder();
            for (int i = 0; i < Math.min(results.size(), 10); i++) {
                MatchedItem item = results.get(i);
                String channel = item.getChannel() != null ? item.getChannel().getName() : "Unknown";
                String channelId = item.getChannel() != null ? item.getChannel().getId() : null;
                String username = item.getUsername() != null ? item.getUsername() : "Unknown";
                String text = item.getText() != null ? item.getText() : "";
                String timestamp = item.getTs();

                // Include link for context extraction
                String link = buildSlackPermalink(channelId, timestamp);

                resultsText.append(String.format("[%d] #%s - %s: %s\n    Link: %s\n",
                    i + 1, channel, username, truncate(text, 200), link));
            }

            String prompt = String.format(
                "Evaluate if these Slack search results contain enough information to answer the user's question.\n\n" +
                "USER'S QUESTION: \"%s\"\n\n" +
                "SEARCH RESULTS (iteration %d, %d total messages):\n%s\n\n" +
                "YOUR TASK:\n" +
                "1. Determine if this data is SUFFICIENT to provide a helpful answer\n" +
                "2. Extract and summarize ONLY the relevant information that answers the question\n\n" +
                "RESPOND IN THIS FORMAT:\n" +
                "SUFFICIENT: YES or NO\n" +
                "SUMMARY: [Concise summary of relevant information with Slack links in markdown format]\n\n" +
                "If SUFFICIENT=NO, provide a brief summary of what you found and what's missing.\n" +
                "If SUFFICIENT=YES, provide a comprehensive summary with inline links.\n\n" +
                "Your response:",
                userQuery,
                iterationNumber,
                results.size(),
                resultsText.toString()
            );

            String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content()
                .trim();

            // Parse response
            boolean sufficient = response.toUpperCase().contains("SUFFICIENT: YES");

            // Extract summary (everything after "SUMMARY:")
            String summary = "";
            int summaryIndex = response.indexOf("SUMMARY:");
            if (summaryIndex != -1) {
                summary = response.substring(summaryIndex + 8).trim();
            } else {
                // Fallback: use entire response if format not followed
                summary = response;
            }

            log.info("LLM sufficiency evaluation: {}", sufficient ? "SUFFICIENT" : "INSUFFICIENT");
            return new SufficiencyResult(sufficient, summary);

        } catch (Exception e) {
            log.warn("Error evaluating search sufficiency, assuming insufficient", e);
            return new SufficiencyResult(false, "Error evaluating results: " + e.getMessage());
        }
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
     * Formats search results into readable text with links
     */
    private String formatSearchResults(List<MatchedItem> matches) {
        return matches.stream()
            .limit(10)
            .map(match -> {
                String text = match.getText() != null ? match.getText() : "";
                String username = match.getUsername() != null ? match.getUsername() : "Unknown";
                String channelName = match.getChannel() != null ? match.getChannel().getName() : "Unknown";
                String channelId = match.getChannel() != null ? match.getChannel().getId() : null;
                String timestamp = match.getTs();

                // Build Slack permalink
                String link = buildSlackPermalink(channelId, timestamp);

                return String.format("• [#%s] %s: %s\n  Link: %s",
                    channelName,
                    username,
                    truncate(text, 150),
                    link
                );
            })
            .collect(Collectors.joining("\n\n"));
    }

    /**
     * Builds a Slack permalink to a message
     * Format: https://WORKSPACE.slack.com/archives/CHANNEL_ID/pTIMESTAMP
     */
    private String buildSlackPermalink(String channelId, String timestamp) {
        if (channelId == null || timestamp == null) {
            return "[Link unavailable]";
        }

        // If workspace domain is configured, use it
        if (workspaceDomain != null && !workspaceDomain.isEmpty()) {
            // Convert timestamp "1234567890.123456" to "p1234567890123456"
            String permalinkTs = "p" + timestamp.replace(".", "");
            return String.format("https://%s.slack.com/archives/%s/%s",
                workspaceDomain, channelId, permalinkTs);
        }

        // Fallback: Use Slack's client method to get permalink (requires API call)
        try {
            var response = slackApp.client().chatGetPermalink(req -> req
                .token(botToken)
                .channel(channelId)
                .messageTs(timestamp)
            );

            if (response.isOk() && response.getPermalink() != null) {
                return response.getPermalink();
            }
        } catch (Exception e) {
            log.warn("Failed to get permalink for message", e);
        }

        return String.format("[Message in #%s]", channelId);
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
