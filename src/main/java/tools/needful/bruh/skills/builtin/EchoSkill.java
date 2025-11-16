package tools.needful.bruh.skills.builtin;

import tools.needful.bruh.skills.Skill;
import tools.needful.bruh.skills.SkillContext;
import tools.needful.bruh.skills.SkillResult;
import org.springframework.stereotype.Component;

@Component
public class EchoSkill implements Skill {

    @Override
    public String getName() {
        return "echo";
    }

    @Override
    public String getDescription() {
        return "Echoes back the input query";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        return SkillResult.success("Echo: " + context.getQuery());
    }
}
