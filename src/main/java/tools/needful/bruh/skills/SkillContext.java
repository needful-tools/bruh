package tools.needful.bruh.skills;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkillContext {
    private String query;
    private String userId;
    private String channelId;
    private String messageTs;      // Timestamp of the message
    private String threadTs;       // Thread timestamp (if message is in a thread)
}
