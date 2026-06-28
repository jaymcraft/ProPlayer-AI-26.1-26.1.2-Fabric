package net.shasankp000.FunctionCaller;

import net.minecraft.core.BlockPos;
import net.shasankp000.PlayerUtils.SurvivalReach;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningReachTest {

    @Test
    void rejectsKnownTooFarStonePositionFromSpeedrunLog() {
        assertFalse(SurvivalReach.isWithinStrictMiningReach(
                new Vec3(334.5, 67.0, 510.5),
                new BlockPos(328, 66, 510)
        ));
    }

    @Test
    void acceptsAdjacentMiningStandPosition() {
        assertTrue(SurvivalReach.isWithinStrictMiningReach(
                new Vec3(329.5, 66.0, 510.5),
                new BlockPos(328, 66, 510)
        ));
    }
}
