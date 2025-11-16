package tools.needful.bruh.skills;

public interface Skill {
    String getName();
    String getDescription();
    SkillResult execute(SkillContext context);
}
