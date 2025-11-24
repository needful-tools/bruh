package tools.needful.bruh.agent;

import tools.needful.bruh.experts.ExpertConsultationService;
import tools.needful.bruh.experts.ExpertRouter;
import tools.needful.bruh.model.AgentResponse;
import tools.needful.bruh.skills.SkillContext;
import tools.needful.bruh.skills.SkillRegistry;
import tools.needful.bruh.skills.SkillRouter;
import tools.needful.bruh.skills.SkillResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AgentCore implements a RAG (Retrieval-Augmented Generation) pattern:
 *
 * 1. RETRIEVAL: Gather context from multiple sources
 *    - Skills (e.g., query Slack for conversations)
 *    - Vector search (retrieve relevant documentation)
 *
 * 2. GENERATION: Synthesize one coherent answer with LLM
 *    - Combine all gathered context
 *    - Generate answer with proper attribution
 *    - Be explicit about uncertainty
 */
@Slf4j
@Component
public class AgentCore {

    @Autowired
    private ExpertRouter expertRouter;

    @Autowired
    private ExpertConsultationService expertConsultationService;

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private SkillRouter skillRouter;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    public AgentResponse handleQuery(String query, String userId, String channelId, String messageTs, String threadTs) {
        log.info("Handling query: {}", query);

        // STEP 1: RETRIEVAL - Gather all available context

        // 1a. Determine which skills to use and execute them
        List<String> skillData = gatherSkillData(query, userId, channelId, messageTs, threadTs);

        // 1b. Search vector store for relevant documentation
        List<Document> relevantDocs = gatherDocumentation(query);

        // STEP 2: GENERATION - Synthesize final answer
        String synthesizedAnswer = synthesizeAnswer(query, skillData, relevantDocs);

        return AgentResponse.builder()
            .answer(synthesizedAnswer)
            .build();
    }

    /**
     * RETRIEVAL: Execute skills to gather data (e.g., Slack messages, external APIs)
     */
    private List<String> gatherSkillData(String query, String userId, String channelId, String messageTs, String threadTs) {
        List<String> skillData = new ArrayList<>();

        // Use LLM to determine which skill(s) are needed
        List<String> skillNames = skillRouter.routeToSkills(query);

        if (skillNames.isEmpty()) {
            log.info("No skills needed for this query");
            return skillData;
        }

        SkillContext context = SkillContext.builder()
            .query(query)
            .userId(userId)
            .channelId(channelId)
            .messageTs(messageTs)
            .threadTs(threadTs)
            .build();

        // Execute all selected skills
        for (String skillName : skillNames) {
            var skill = skillRegistry.getSkill(skillName);
            if (skill == null) {
                log.warn("Skill not found: {}", skillName);
                continue;
            }

            log.info("Executing skill: {}", skillName);
            SkillResult result = skill.execute(context);

            if (result.isSuccess()) {
                skillData.add(String.format("[Data from %s skill]\n%s", skillName, result.getResult()));
            } else {
                log.warn("Skill {} failed: {}", skillName, result.getError());
            }
        }

        return skillData;
    }

    /**
     * RETRIEVAL: Search vector store for relevant documentation
     */
    private List<Document> gatherDocumentation(String query) {
        // Use LLM to determine which expert(s) might have relevant docs
        List<String> expertNames = expertRouter.routeToExperts(query);

        if (expertNames.isEmpty()) {
            log.info("No expert documentation needed");
            return List.of();
        }

        // For now, query first expert (could be enhanced to query all)
        String expertName = expertNames.get(0);
        List<Document> docs = expertConsultationService.retrieveRelevantDocuments(expertName, query);

        log.info("Retrieved {} documents from expert: {}", docs.size(), expertName);
        return docs;
    }

    /**
     * GENERATION: Synthesize final answer using LLM with all gathered context
     */
    private String synthesizeAnswer(String query, List<String> skillData, List<Document> docs) {
        StringBuilder contextBuilder = new StringBuilder();

        // Add skill data to context
        if (!skillData.isEmpty()) {
            contextBuilder.append("=== DATA FROM SKILLS ===\n\n");
            for (String data : skillData) {
                contextBuilder.append(data).append("\n\n");
            }
        }

        // Add documentation to context
        if (!docs.isEmpty()) {
            contextBuilder.append("=== DOCUMENTATION ===\n\n");
            for (int i = 0; i < docs.size(); i++) {
                Document doc = docs.get(i);
                String source = (String) doc.getMetadata().getOrDefault("source", "Unknown");
                contextBuilder.append(String.format("[Document %d - Source: %s]\n%s\n\n",
                    i + 1, source, doc.getText()));
            }
        }

        String context = contextBuilder.toString();

        // If no context was gathered, say so
        if (context.trim().isEmpty()) {
            return "I don't have enough information to answer that question. " +
                   "I couldn't find relevant data in Slack conversations or documentation.";
        }

        // Build synthesis prompt
        String prompt = buildSynthesisPrompt(query, context);

        // Call LLM to generate final answer
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        return answer;
    }

    /**
     * Builds the final synthesis prompt with attribution guidelines
     */
    private String buildSynthesisPrompt(String query, String context) {
        return String.format("""
            You are a helpful AI assistant. Answer the user's question using the provided context.

            IMPORTANT GUIDELINES:
            1. If the answer is in the documentation, cite the source (e.g., "According to [source]...")
            2. If the answer is based on Slack conversations, ALWAYS include the Slack links provided in the context
               - Format: "According to [this conversation](link)" or "See [this message](link)"
               - Include relevant links inline in your answer, not as a separate list at the end
            3. If you're making reasonable inferences or assumptions, be explicit (e.g., "Based on the information provided, it appears that...")
            4. If you don't know or the context doesn't contain the answer, say so clearly (e.g., "I don't have enough information to answer that")
            5. NEVER make up information that isn't in the context
            6. Be concise but complete
            7. Use markdown formatting for links

            Context:
            %s

            User Question: %s

            Answer:
            """,
            context,
            query
        );
    }
}
