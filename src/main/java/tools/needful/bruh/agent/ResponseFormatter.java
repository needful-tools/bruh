package tools.needful.bruh.agent;

import tools.needful.bruh.model.AgentResponse;
import tools.needful.bruh.model.ExpertAnswer;
import org.springframework.stereotype.Component;

@Component
public class ResponseFormatter {

    public AgentResponse formatExpertAnswer(ExpertAnswer expertAnswer) {
        StringBuilder response = new StringBuilder();
        response.append(expertAnswer.getAnswer());

        if (expertAnswer.getSources() != null && !expertAnswer.getSources().isEmpty()) {
            response.append("\n\n📚 Sources:\n");
            expertAnswer.getSources().forEach(source ->
                response.append("• ").append(source).append("\n")
            );
        }

        return AgentResponse.builder()
            .answer(response.toString())
            .expertName(expertAnswer.getExpertName())
            .build();
    }
}
