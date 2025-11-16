package tools.needful.bruh.skills;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    @Autowired
    public SkillRegistry(List<Skill> skillBeans) {
        skillBeans.forEach(skill -> {
            skills.put(skill.getName().toLowerCase(), skill);
            log.info("Registered skill: {}", skill.getName());
        });
    }

    public Skill getSkill(String name) {
        return skills.get(name.toLowerCase());
    }

    public List<Skill> getAllSkills() {
        return List.copyOf(skills.values());
    }

    /**
     * Returns a formatted string describing all available skills for LLM consumption
     */
    public String getSkillDescriptions() {
        StringBuilder sb = new StringBuilder();
        skills.values().forEach(skill -> {
            sb.append(String.format("- %s: %s\n", skill.getName(), skill.getDescription()));
        });
        return sb.toString();
    }
}
