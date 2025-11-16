package tools.needful.bruh.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ExpertAnswer {
    private String expertName;
    private String answer;
    private List<String> sources;
    private double confidence;
}
