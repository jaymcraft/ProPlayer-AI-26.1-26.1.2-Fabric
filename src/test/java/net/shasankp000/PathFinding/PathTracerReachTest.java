package net.shasankp000.PathFinding;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathTracerReachTest {

    @Test
    void doesNotReachWhenStillInPreviousHorizontalBlock() {
        BlockPos current = new BlockPos(306, 72, -107);
        BlockPos target = new BlockPos(305, 72, -107);

        boolean reached = PathTracer.BotSegmentManager.hasReachedTarget(
                current,
                305.95,
                72.0,
                -106.5,
                target,
                false
        );

        assertFalse(reached);
    }

    @Test
    void reachesWhenCurrentBlockMatchesTarget() {
        BlockPos current = new BlockPos(305, 72, -107);
        BlockPos target = new BlockPos(305, 72, -107);

        boolean reached = PathTracer.BotSegmentManager.hasReachedTarget(
                current,
                305.5,
                72.0,
                -106.5,
                target,
                false
        );

        assertTrue(reached);
    }

    @Test
    void allowsSmallSlopeYDifferenceWithMatchingHorizontalBlock() {
        BlockPos current = new BlockPos(304, 72, -104);
        BlockPos target = new BlockPos(304, 71, -104);

        boolean reached = PathTracer.BotSegmentManager.hasReachedTarget(
                current,
                304.5,
                72.0,
                -103.5,
                target,
                false
        );

        assertTrue(reached);
    }

    @Test
    void jumpToleranceDoesNotAllowWrongHorizontalBlock() {
        BlockPos current = new BlockPos(306, 73, -107);
        BlockPos target = new BlockPos(305, 72, -107);

        boolean reached = PathTracer.BotSegmentManager.hasReachedTarget(
                current,
                305.6,
                73.0,
                -106.5,
                target,
                true
        );

        assertFalse(reached);
    }
}
