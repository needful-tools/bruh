package tools.needful.bruh.experts;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class Expert {
    private String name;
    private String description;
    private int documentCount;
    private int chunkCount;

    @Builder.Default
    private LocalDateTime indexedAt = LocalDateTime.now();
}
