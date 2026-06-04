package namdesktop.lens;

import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DueLens {

    public record Result(
            List<DueItemRow> overdue,
            List<DueItemRow> today,
            List<DueItemRow> thisWeek,
            List<DueItemRow> later) {}

    public Result items(NamWorkspace workspace) {
        return items(workspace, LocalDate.now());
    }

    public Result items(NamWorkspace workspace, LocalDate today) {
        var structural = structuralIds(workspace);
        var all = workspace.getNodes().values().stream()
                .filter(n -> !structural.contains(n.getId()))
                .filter(n -> !n.isProject())
                .filter(n -> n.getStatus() != NodeStatus.DONE)
                .filter(n -> n.getDueAt() != null)
                .map(n -> {
                    var parent = workspace.getParent(n.getId())
                            .filter(p -> !structural.contains(p.getId()))
                            .orElse(null);
                    var projectPath = parent != null ? buildProjectPath(workspace, parent.getId(), structural) : null;
                    var ownTags  = n.getTags();
                    var inherited = workspace.effectiveTags(n.getId()).stream()
                            .filter(t -> !ownTags.contains(t)).sorted().toList();
                    return new DueItemRow(n.getId(), n.getTitle(), n.getStatus(),
                            projectPath,
                            parent != null ? parent.getId() : null,
                            List.copyOf(ownTags), inherited, n.getDueAt());
                })
                .toList();

        var byDate = Comparator.comparing(DueItemRow::dueAt);
        return new Result(
                all.stream().filter(r -> r.dueAt().isBefore(today))
                        .sorted(byDate).toList(),
                all.stream().filter(r -> r.dueAt().isEqual(today))
                        .sorted(byDate).toList(),
                all.stream().filter(r -> r.dueAt().isAfter(today) && !r.dueAt().isAfter(today.plusDays(7)))
                        .sorted(byDate).toList(),
                all.stream().filter(r -> r.dueAt().isAfter(today.plusDays(7)))
                        .sorted(byDate).toList());
    }

    private static String buildProjectPath(NamWorkspace workspace, UUID projectId, Set<UUID> structural) {
        var path = workspace.buildPath(projectId);
        var sb = new StringBuilder();
        for (var node : path) {
            if (structural.contains(node.getId())) continue;
            if (!sb.isEmpty()) sb.append(" > ");
            sb.append(node.getTitle());
        }
        return sb.toString();
    }

    private Set<UUID> structuralIds(NamWorkspace workspace) {
        return Stream.of(
                workspace.getRootNodeId(),
                workspace.getInboxNodeId(),
                workspace.getProjectsNodeId(),
                workspace.getNextActionsNodeId()
        ).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
