package tools.needful.bruh.slack;

import tools.needful.bruh.agent.AgentCore;
import tools.needful.bruh.model.AgentResponse;
import com.slack.api.bolt.App;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.AppMentionEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SlackEventListener {

    @Autowired
    private App slackApp;

    @Autowired
    private AgentCore agentCore;

    // Track processed events to prevent duplicates (event_id -> expiry_time)
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void registerListeners() {
        // Listen for app mentions
        slackApp.event(AppMentionEvent.class, (event, ctx) -> {
            String eventId = event.getEventId();

            // Check if we've already processed this event (deduplication)
            if (processedEvents.contains(eventId)) {
                log.info("Skipping duplicate event: {}", eventId);
                return ctx.ack();
            }

            // Mark event as processed
            processedEvents.add(eventId);

            // Schedule cleanup of this event ID after 2 minutes
            cleanupScheduler.schedule(() -> processedEvents.remove(eventId), 2, TimeUnit.MINUTES);

            // Acknowledge immediately (within 3 seconds)
            ctx.ack();

            // Process asynchronously in a separate thread
            CompletableFuture.runAsync(() -> handleAppMention(event.getEvent(), ctx));

            return ctx.ack();
        });

        log.info("Slack event listeners registered");
    }

    private void handleAppMention(AppMentionEvent event, EventContext ctx) {
        try {
            String text = event.getText();
            String userId = event.getUser();
            String channelId = event.getChannel();
            String messageTs = event.getTs();
            String threadTs = event.getThreadTs(); // null if not in a thread

            log.info("Received mention in channel {}: {}", channelId, text);

            // Remove bot mention from text
            String query = text.replaceAll("<@[A-Z0-9]+>", "").trim();

            // Process with agent
            AgentResponse response = agentCore.handleQuery(query, userId, channelId, messageTs, threadTs);

            // Send response in thread
            ctx.client().chatPostMessage(req -> req
                .channel(channelId)
                .threadTs(threadTs != null ? threadTs : messageTs)
                .text(response.getAnswer())
            );

        } catch (Exception e) {
            log.error("Error handling app mention", e);

            try {
                ctx.client().chatPostMessage(req -> req
                    .channel(event.getChannel())
                    .threadTs(event.getTs())
                    .text("Sorry, I encountered an error processing your request.")
                );
            } catch (Exception ex) {
                log.error("Error sending error message", ex);
            }
        }
    }
}
