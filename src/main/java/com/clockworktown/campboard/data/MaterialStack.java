package com.clockworktown.campboard.data;

public class MaterialStack {
    private String itemId;
    private int requested;
    private int stored;

    public MaterialStack(String itemId, int requested) {
        this.itemId = itemId;
        this.requested = Math.max(0, requested);
        this.stored = 0;
    }

    public String itemId() {
        return itemId;
    }

    public int requested() {
        return requested;
    }

    public int stored() {
        return stored;
    }

    public int remaining() {
        return Math.max(0, requested - stored);
    }

    public int contribute(int amount) {
        int accepted = Math.min(Math.max(0, amount), remaining());
        stored += accepted;
        return accepted;
    }

    public int withdraw(int amount) {
        int removed = Math.min(Math.max(0, amount), stored);
        stored -= removed;
        return removed;
    }

    public void adjustStored(int amount) {
        stored = Math.max(0, amount);
    }

    public void adjustRequested(int amount) {
        requested = Math.max(0, amount);
        stored = Math.min(stored, requested);
    }
}
