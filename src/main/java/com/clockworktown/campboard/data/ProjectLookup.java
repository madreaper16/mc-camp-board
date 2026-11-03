package com.clockworktown.campboard.data;

import java.util.Map;
import java.util.Optional;

public final class ProjectLookup {
    private ProjectLookup() { }

    public static Optional<Match> unique(Map<String, BoardState> boards, String projectId) {
        Match result = null;
        for (var entry : boards.entrySet()) {
            var project = entry.getValue().findProject(projectId);
            if (project.isPresent()) {
                if (result != null) throw new IllegalArgumentException("Project ID is ambiguous. Look at its board or supply a board ID.");
                result = new Match(entry.getKey(), entry.getValue(), project.get());
            }
        }
        return Optional.ofNullable(result);
    }

    public record Match(String boardId, BoardState state, Project project) { }
}
