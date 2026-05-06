package com.kartersanamo.havoc.generator;

import java.util.ArrayList;
import java.util.List;

public final class BaseTemplateValidation {

    private BaseTemplateValidation() {
    }

    public static List<String> validate(BaseTemplateDefinition def, int maxSections, int maxRepeatPerSection) {
        List<String> errors = new ArrayList<String>();
        if (def == null) {
            errors.add("Definition is null.");
            return errors;
        }
        int sz = def.getSizeChunksOdd();
        if (sz != 1 && sz != 3 && sz != 5) {
            errors.add("Size must be 1, 3, or 5 chunks.");
        }
        List<DefenseSection> sec = def.getSections();
        if (sec.isEmpty()) {
            errors.add("Add at least one wall section.");
        }
        if (sec.size() > maxSections) {
            errors.add("Too many sections (max " + maxSections + ").");
        }
        for (int i = 0; i < sec.size(); i++) {
            DefenseSection s = sec.get(i);
            if (s.getRepeats() < 1) {
                errors.add("Section " + (i + 1) + " repeats must be at least 1.");
            }
            if (s.getRepeats() > maxRepeatPerSection) {
                errors.add("Section " + (i + 1) + " repeats exceed max (" + maxRepeatPerSection + ").");
            }
        }
        int thick = def.totalThicknessBlocks();
        if (thick > 256) {
            errors.add("Total wall depth is too large (" + thick + " blocks). Reduce repeats or sections.");
        }
        return errors;
    }
}
