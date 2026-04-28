package com.kartersanamo.havoc.admin;

enum BaseAdminGuiSortMode {
    ACTIVE_FIRST("ACTIVE first"),
    DISTANCE("Distance"),
    DIFFICULTY("Difficulty");

    final String label;

    BaseAdminGuiSortMode(String label) {
        this.label = label;
    }

    BaseAdminGuiSortMode next() {
        BaseAdminGuiSortMode[] vals = values();
        return vals[(ordinal() + 1) % vals.length];
    }
}
