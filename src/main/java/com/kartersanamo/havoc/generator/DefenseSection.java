package com.kartersanamo.havoc.generator;

import java.util.Objects;

public final class DefenseSection {

    private final DefenseType type;
    private final int repeats;

    public DefenseSection(DefenseType type, int repeats) {
        this.type = Objects.requireNonNull(type, "type");
        this.repeats = repeats;
    }

    public DefenseType getType() {
        return type;
    }

    public int getRepeats() {
        return repeats;
    }

    public DefenseSection withRepeats(int r) {
        return new DefenseSection(type, r);
    }

    public DefenseSection withType(DefenseType t) {
        return new DefenseSection(t, repeats);
    }
}
