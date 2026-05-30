package namdesktop.lens;

import java.util.List;
import java.util.UUID;

public record DoneItemRow(UUID id, String title,
                          String parentTitle, UUID parentId,
                          String projectPath,
                          List<String> tags, List<String> inheritedTags,
                          boolean hasResources) {}
