package com.kartersanamo.havoc.base.lifecycle;

import com.kartersanamo.havoc.base.ActiveHavocBase;
import com.kartersanamo.havoc.base.ChunkKey;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClaimService {

    public boolean overlapsExistingFootprint(Map<UUID, ActiveHavocBase> basesById, String worldName, int ox, int oz, int w, int len) {
        int aMinX = ox;
        int aMaxX = ox + w - 1;
        int aMinZ = oz;
        int aMaxZ = oz + len - 1;
        for (ActiveHavocBase b : basesById.values()) {
            if (!b.worldName.equals(worldName)) {
                continue;
            }
            int bMinX = b.pasteOriginX;
            int bMaxX = b.pasteOriginX + b.footprintSizeX - 1;
            int bMinZ = b.pasteOriginZ;
            int bMaxZ = b.pasteOriginZ + b.footprintSizeZ - 1;
            if (rectanglesOverlap(aMinX, aMinZ, aMaxX, aMaxZ, bMinX, bMinZ, bMaxX, bMaxZ)) {
                return true;
            }
        }
        return false;
    }

    public boolean overlapsExistingClaims(Map<ChunkKey, UUID> chunkOwners, Set<ChunkKey> claimSet) {
        for (ChunkKey key : claimSet) {
            if (chunkOwners.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rectanglesOverlap(int aMinX, int aMinZ, int aMaxX, int aMaxZ, int bMinX, int bMinZ, int bMaxX, int bMaxZ) {
        return aMinX <= bMaxX && aMaxX >= bMinX && aMinZ <= bMaxZ && aMaxZ >= bMinZ;
    }
}
