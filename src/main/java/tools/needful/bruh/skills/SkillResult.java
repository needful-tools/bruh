package tools.needful.bruh.skills;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkillResult {
    private boolean success;
    private String result;
    private String error;

    public static SkillResult success(String result) {
        return SkillResult.builder()
            .success(true)
            .result(result)
            .build();
    }

    public static SkillResult error(String error) {
        return SkillResult.builder()
            .success(false)
            .error(error)
            .build();
    }
}
