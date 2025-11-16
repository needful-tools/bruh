package tools.needful.bruh.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentResponse {
    private String answer;
    private String expertName;
    private String skillName;
}
