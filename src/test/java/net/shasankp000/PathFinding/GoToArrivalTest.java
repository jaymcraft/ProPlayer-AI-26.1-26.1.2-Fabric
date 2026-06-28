package net.shasankp000.PathFinding;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoToArrivalTest {

    @Test
    void acceptsAlreadyAtTargetWithSmallYDifference() {
        assertTrue(GoTo.hasArrivedAtTarget(
                new BlockPos(333, 67, 510),
                new BlockPos(333, 66, 510)
        ));
    }

    @Test
    void rejectsOneBlockHorizontalMiss() {
        assertFalse(GoTo.hasArrivedAtTarget(
                new BlockPos(334, 67, 510),
                new BlockPos(333, 67, 510)
        ));
    }
}
