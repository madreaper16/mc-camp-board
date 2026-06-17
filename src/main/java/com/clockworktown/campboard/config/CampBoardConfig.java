package com.clockworktown.campboard.config;

public class CampBoardConfig {
    private boolean craftingEnabled = true;
    private boolean allBoardsGlobal = false;
    private boolean suggestionsEnabled = true;
    private boolean materialsEnabled = true;
    private boolean anyoneCanWithdrawMaterials = true;
    private boolean taskHelpingEnabled = true;
    private boolean largeBoard = false;
    private boolean breakable = true;
    private String projectCreation = "ADMIN_ONLY";
    private int maxProjectLeaders = 3;
    private int maxDescriptionLength = 250;
    private int backupRetention = 10;

    public boolean craftingEnabled() {
        return craftingEnabled;
    }

    public boolean allBoardsGlobal() {
        return allBoardsGlobal;
    }

    public int backupRetention() {
        return backupRetention;
    }

    public boolean suggestionsEnabled() {
        return suggestionsEnabled;
    }

    public boolean materialsEnabled() {
        return materialsEnabled;
    }

    public boolean anyoneCanWithdrawMaterials() {
        return anyoneCanWithdrawMaterials;
    }

    public boolean taskHelpingEnabled() {
        return taskHelpingEnabled;
    }

    public boolean largeBoard() {
        return largeBoard;
    }

    public boolean breakable() {
        return breakable;
    }

    public String projectCreation() {
        return projectCreation == null || projectCreation.isBlank() ? "ADMIN_ONLY" : projectCreation;
    }

    public int maxProjectLeaders() {
        return Math.max(1, Math.min(3, maxProjectLeaders));
    }

    public int maxDescriptionLength() {
        return Math.max(50, Math.min(250, maxDescriptionLength));
    }

    public void setCraftingEnabled(boolean craftingEnabled) {
        this.craftingEnabled = craftingEnabled;
    }

    public void setAllBoardsGlobal(boolean allBoardsGlobal) {
        this.allBoardsGlobal = allBoardsGlobal;
    }

    public void setBackupRetention(int backupRetention) {
        this.backupRetention = Math.max(1, backupRetention);
    }

    public void setSuggestionsEnabled(boolean suggestionsEnabled) {
        this.suggestionsEnabled = suggestionsEnabled;
    }

    public void setMaterialsEnabled(boolean materialsEnabled) {
        this.materialsEnabled = materialsEnabled;
    }

    public void setAnyoneCanWithdrawMaterials(boolean anyoneCanWithdrawMaterials) {
        this.anyoneCanWithdrawMaterials = anyoneCanWithdrawMaterials;
    }

    public void setTaskHelpingEnabled(boolean taskHelpingEnabled) {
        this.taskHelpingEnabled = taskHelpingEnabled;
    }

    public void setLargeBoard(boolean largeBoard) {
        this.largeBoard = largeBoard;
    }

    public void setBreakable(boolean breakable) {
        this.breakable = breakable;
    }

    public void setProjectCreation(String projectCreation) {
        this.projectCreation = "ANYONE".equalsIgnoreCase(projectCreation) ? "ANYONE" : "ADMIN_ONLY";
    }

    public void setMaxProjectLeaders(int maxProjectLeaders) {
        this.maxProjectLeaders = Math.max(1, Math.min(3, maxProjectLeaders));
    }

    public void setMaxDescriptionLength(int maxDescriptionLength) {
        this.maxDescriptionLength = Math.max(50, Math.min(250, maxDescriptionLength));
    }
}
