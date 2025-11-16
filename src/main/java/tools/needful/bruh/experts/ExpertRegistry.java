package tools.needful.bruh.experts;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExpertRegistry {

    private final Map<String, Expert> experts = new ConcurrentHashMap<>();

    public void register(Expert expert) {
        experts.put(expert.getName().toLowerCase(), expert);
    }

    public Expert getExpert(String name) {
        return experts.get(name.toLowerCase());
    }

    public List<Expert> getAllExperts() {
        return new ArrayList<>(experts.values());
    }

    public int count() {
        return experts.size();
    }

    public boolean hasExpert(String name) {
        return experts.containsKey(name.toLowerCase());
    }
}
