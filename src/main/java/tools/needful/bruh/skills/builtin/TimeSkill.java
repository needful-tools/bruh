package tools.needful.bruh.skills.builtin;

import tools.needful.bruh.skills.Skill;
import tools.needful.bruh.skills.SkillContext;
import tools.needful.bruh.skills.SkillResult;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class TimeSkill implements Skill {

    @Override
    public String getName() {
        return "time";
    }

    @Override
    public String getDescription() {
        return "Returns current date and time";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        String currentTime = ZonedDateTime.now()
            .format(DateTimeFormatter.RFC_1123_DATE_TIME);

        return SkillResult.success("Current time: " + currentTime);
    }
}
