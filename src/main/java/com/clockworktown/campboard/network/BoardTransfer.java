package com.clockworktown.campboard.network;

import java.util.ArrayList;
import java.util.List;

public final class BoardTransfer {
    public static final int CHUNK_SIZE = 8192;
    public static final int MAX_CHUNKS = 1024;
    private String boardId;
    private String transferId;
    private boolean opening;
    private int expected;
    private int next;
    private long started;
    private final StringBuilder contents = new StringBuilder();

    public static List<String> split(String json) {
        if (json.length() > CHUNK_SIZE * MAX_CHUNKS) throw new IllegalArgumentException("Board exceeds the 8,388,608-character transfer limit.");
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < json.length();) {
            int end = Math.min(json.length(), start + CHUNK_SIZE);
            if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1))) end--;
            chunks.add(json.substring(start, end));
            start = end;
        }
        if (chunks.isEmpty()) chunks.add("");
        if (chunks.size() > MAX_CHUNKS) throw new IllegalArgumentException("Too many board chunks.");
        return chunks;
    }

    public Result accept(String board, String transfer, boolean open, int index, int count, String fragment, long now) {
        if (count < 1 || count > MAX_CHUNKS || index < 0 || index >= count || fragment.length() > CHUNK_SIZE) {
            reset();
            throw new IllegalArgumentException("Invalid board transfer.");
        }
        if (index == 0) {
            reset();
            boardId = board;
            transferId = transfer;
            opening = open;
            expected = count;
            started = now;
        }
        if (now - started > 30_000 || !board.equals(boardId) || !transfer.equals(transferId)
                || count != expected || open != opening || index != next) {
            reset();
            throw new IllegalArgumentException("Incomplete board transfer.");
        }
        contents.append(fragment);
        next++;
        if (next != expected) return null;
        Result result = new Result(boardId, contents.toString(), opening);
        reset();
        return result;
    }

    public void reset() {
        boardId = null;
        transferId = null;
        contents.setLength(0);
        expected = next = 0;
    }

    public record Result(String boardId, String json, boolean opening) { }
}
