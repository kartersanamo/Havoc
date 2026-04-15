package com.kartersanamo.havoc.world;

import org.bukkit.World;

/**
 * Fixed-size column (dx x dz x world height) block snapshot using 1.8 type + data bytes.
 * Flat index order: x inner, then z, then y.
 */
public final class ColumnBoxSnapshot {

    private final int minX;
    private final int minZ;
    private final int dx;
    private final int dz;
    private final int height;
    private final short[] packed;

    public ColumnBoxSnapshot(int minX, int minZ, int dx, int dz, int height) {
        this.minX = minX;
        this.minZ = minZ;
        this.dx = dx;
        this.dz = dz;
        this.height = height;
        this.packed = new short[dx * dz * height];
    }

    public static ColumnBoxSnapshot capture(World world, int minX, int minZ, int dx, int dz, int height) {
        ColumnBoxSnapshot s = new ColumnBoxSnapshot(minX, minZ, dx, dz, height);
        s.captureColumns(world, 0, dx * dz);
        return s;
    }

    /**
     * Captures [startColumn, startColumn+columnCount) where column index is x + z*dx.
     */
    public void captureColumns(World world, int startColumn, int columnCount) {
        int total = dx * dz;
        int end = Math.min(total, Math.max(0, startColumn) + Math.max(0, columnCount));
        for (int col = Math.max(0, startColumn); col < end; col++) {
            int ix = col % dx;
            int iz = col / dx;
            int wx = minX + ix;
            int wz = minZ + iz;
            for (int y = 0; y < height; y++) {
                org.bukkit.block.Block bl = world.getBlockAt(wx, y, wz);
                packed[flat(ix, iz, y)] = pack(bl.getTypeId(), (byte) bl.getData());
            }
        }
    }

    public void applyPartial(World world, int startFlatIndex, int count) {
        int max = packed.length;
        int end = Math.min(max, startFlatIndex + count);
        for (int i = startFlatIndex; i < end; i++) {
            int ix = i % dx;
            int rem = i / dx;
            int iz = rem % dz;
            int y = rem / dz;
            int wx = minX + ix;
            int wz = minZ + iz;
            unpackSet(world, wx, y, wz, packed[i]);
        }
    }

    public void applyAll(World world) {
        applyPartial(world, 0, packed.length);
    }

    public int volume() {
        return packed.length;
    }

    public boolean matchesWorld(World world, int flatIndex) {
        int ix = flatIndex % dx;
        int rem = flatIndex / dx;
        int iz = rem % dz;
        int y = rem / dz;
        int wx = minX + ix;
        int wz = minZ + iz;
        short want = packed[flatIndex];
        org.bukkit.block.Block bl = world.getBlockAt(wx, y, wz);
        short have = pack(bl.getTypeId(), (byte) bl.getData());
        return want == have;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getDx() {
        return dx;
    }

    public int getDz() {
        return dz;
    }

    public int getHeight() {
        return height;
    }

    private int flat(int ix, int iz, int y) {
        return ix + dx * (iz + dz * y);
    }

    private static short pack(int typeId, byte data) {
        return (short) ((typeId & 0xFFF) << 4 | (data & 0x0F));
    }

    private static void unpackSet(World world, int x, int y, int z, short packed) {
        int id = (packed >> 4) & 0xFFF;
        int data = packed & 0x0F;
        world.getBlockAt(x, y, z).setTypeIdAndData(id, (byte) data, false);
    }
}
