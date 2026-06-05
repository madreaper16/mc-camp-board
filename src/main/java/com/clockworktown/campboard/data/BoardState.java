package com.clockworktown.campboard.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BoardState {
    private int schemaVersion = 1;
    private List<Project> projects = new ArrayList<>();
    private List<Suggestion> suggestions = new ArrayList<>();

    public int schemaVersion() {
        return schemaVersion;
    }

    public List<Project> projects() {
        return projects;
    }

    public List<Suggestion> suggestions() {
        return suggestions;
    }

    public Optional<Project> findProject(String id) {
        return projects.stream().filter(project -> project.id().equals(id)).findFirst();
    }
}
