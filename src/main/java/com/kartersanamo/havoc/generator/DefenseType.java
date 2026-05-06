package com.kartersanamo.havoc.generator;

/**
 * V1 wall segment types. Expand outward from the inner base; each repeat adds one concentric band.
 */
public enum DefenseType {
    FLAT_WALL,
    REGEN_WALL,
    SAND_WALL;

    public int thicknessPerRepeat() {
        switch (this) {
            case REGEN_WALL:
                return 4;
            case FLAT_WALL:
                return 2;
            case SAND_WALL:
                return 2;
            default:
                return 3;
        }
    }

    public String displayName() {
        switch (this) {
            case REGEN_WALL:
                return "Regen";
            case SAND_WALL:
                return "Sand";
            case FLAT_WALL:
            default:
                return "Flat";
        }
    }
}
