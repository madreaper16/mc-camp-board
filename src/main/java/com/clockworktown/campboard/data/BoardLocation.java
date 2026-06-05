package com.clockworktown.campboard.data;

public record BoardLocation(String dimension, int x, int y, int z) {
    public BoardLocation convertedPair() {
        if ("minecraft:the_nether".equals(dimension)) {
            return new BoardLocation("minecraft:overworld", x * 8, y, z * 8);
        }

        if ("minecraft:overworld".equals(dimension)) {
            return new BoardLocation("minecraft:the_nether", floorDiv(x, 8), y, floorDiv(z, 8));
        }

        return this;
    }

    private static int floorDiv(int value, int divisor) {
        return Math.floorDiv(value, divisor);
    }
}
