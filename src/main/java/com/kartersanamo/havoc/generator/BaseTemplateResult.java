package com.kartersanamo.havoc.generator;

import com.sk89q.worldedit.CuboidClipboard;

/**
 * Generated clipboard plus schematic anchor (from min corner) for breach + config.
 */
public final class BaseTemplateResult {

    private final CuboidClipboard clipboard;
    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;

    public BaseTemplateResult(CuboidClipboard clipboard, int anchorX, int anchorY, int anchorZ) {
        this.clipboard = clipboard;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
    }

    public CuboidClipboard getClipboard() {
        return clipboard;
    }

    public int getAnchorX() {
        return anchorX;
    }

    public int getAnchorY() {
        return anchorY;
    }

    public int getAnchorZ() {
        return anchorZ;
    }
}
