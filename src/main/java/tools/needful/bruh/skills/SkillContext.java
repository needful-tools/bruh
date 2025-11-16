package tools.needful.bruh.skills;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkillContext {
    private String query;
    private String userId;
    private String channelId;
}
