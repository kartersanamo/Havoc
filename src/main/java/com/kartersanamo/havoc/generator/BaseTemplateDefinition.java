package com.kartersanamo.havoc.generator;

import com.kartersanamo.havoc.base.BaseDifficulty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Editable definition for a procedurally generated Havoc base shell (walls only in V1).
 */
public final class BaseTemplateDefinition {

    private final BaseDifficulty difficulty;
    private int sizeChunksOdd;
    private boolean slabFloorBetweenWalls;
    private final ArrayList<DefenseSection> sections;

    public BaseTemplateDefinition(BaseDifficulty difficulty, int sizeChunksOdd, boolean slabFloorBetweenWalls, List<DefenseSection> sections) {
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.sizeChunksOdd = sizeChunksOdd;
        this.slabFloorBetweenWalls = slabFloorBetweenWalls;
        this.sections = new ArrayList<DefenseSection>(sections == null ? Collections.<DefenseSection>emptyList() : sections);
    }

    public static BaseTemplateDefinition defaultFor(BaseDifficulty difficulty) {
        List<DefenseSection> list = new ArrayList<DefenseSection>();
        switch (difficulty) {
            case HARD:
                list.add(new DefenseSection(DefenseType.FLAT_WALL, 2));
                list.add(new DefenseSection(DefenseType.REGEN_WALL, 2));
                list.add(new DefenseSection(DefenseType.SAND_WALL, 1));
                return new BaseTemplateDefinition(difficulty, 5, true, list);
            case MEDIUM:
                list.add(new DefenseSection(DefenseType.FLAT_WALL, 2));
                list.add(new DefenseSection(DefenseType.REGEN_WALL, 1));
                return new BaseTemplateDefinition(difficulty, 3, true, list);
            case EASY:
            default:
                list.add(new DefenseSection(DefenseType.FLAT_WALL, 1));
                list.add(new DefenseSection(DefenseType.SAND_WALL, 1));
                return new BaseTemplateDefinition(difficulty, 1, true, list);
        }
    }

    public BaseTemplateDefinition copy() {
        return new BaseTemplateDefinition(difficulty, sizeChunksOdd, slabFloorBetweenWalls, new ArrayList<DefenseSection>(sections));
    }

    public BaseDifficulty getDifficulty() {
        return difficulty;
    }

    public int getSizeChunksOdd() {
        return sizeChunksOdd;
    }

    public void setSizeChunksOdd(int sizeChunksOdd) {
        this.sizeChunksOdd = sizeChunksOdd;
    }

    public boolean isSlabFloorBetweenWalls() {
        return slabFloorBetweenWalls;
    }

    public void setSlabFloorBetweenWalls(boolean slabFloorBetweenWalls) {
        this.slabFloorBetweenWalls = slabFloorBetweenWalls;
    }

    public List<DefenseSection> getSections() {
        return Collections.unmodifiableList(sections);
    }

    public ArrayList<DefenseSection> getSectionsMutable() {
        return sections;
    }

    public int totalThicknessBlocks() {
        int t = 0;
        for (DefenseSection s : sections) {
            t += s.getType().thicknessPerRepeat() * Math.max(0, s.getRepeats());
        }
        return t;
    }
}
