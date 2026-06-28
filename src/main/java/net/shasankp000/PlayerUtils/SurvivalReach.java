package net.shasankp000.PlayerUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class SurvivalReach {
    private static final double STRICT_MINING_REACH = 4.75;

    private SurvivalReach() {
    }

    public static boolean isWithinStrictMiningReach(Vec3 feetPosition, BlockPos targetPos) {
        return Math.sqrt(targetPos.distToCenterSqr(feetPosition)) <= STRICT_MINING_REACH;
    }
}
