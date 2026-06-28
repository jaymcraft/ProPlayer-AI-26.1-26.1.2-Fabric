package net.shasankp000.PathFinding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathTracerCommandTest {

    @Test
    void formatsPlayerCommand() {
        assertEquals("/player kip move forward", PathTracer.BotSegmentManager.formatPlayerCommand("kip", "move forward"));
        assertEquals("/player kip stop", PathTracer.BotSegmentManager.formatPlayerCommand("kip", "stop"));
    }
}
