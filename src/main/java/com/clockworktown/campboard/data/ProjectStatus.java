package com.clockworktown.campboard.data;

public enum ProjectStatus {
    PLANNED("Planned"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    BACK_BURNER("Back Burner"),
    COMPLETED("Completed"),
    ARCHIVED("Archived");

    private final String displayName;

    ProjectStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
