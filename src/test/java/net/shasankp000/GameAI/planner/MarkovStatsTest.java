package net.shasankp000.GameAI.planner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkovStatsTest {

    @Test
    void startsWithZeroCountsAndTotal() {
        MarkovStats stats = new MarkovStats();

        assertEquals(0, stats.getTotal());
        assertEquals(0, stats.getCount((byte) 7));
    }

    @Test
    void recordsTransitionsByActionAndTotal() {
        MarkovStats stats = new MarkovStats();

        stats.recordTransition((byte) 3);
        stats.recordTransition((byte) 3);
        stats.recordTransition((byte) 4);

        assertEquals(2, stats.getCount((byte) 3));
        assertEquals(1, stats.getCount((byte) 4));
        assertEquals(3, stats.getTotal());
    }

    @Test
    void treatsNegativeActionIdsAsUnsignedBytes() {
        MarkovStats stats = new MarkovStats();

        stats.recordTransition((byte) 255);
        stats.recordTransition((byte) 255);

        assertEquals(2, stats.getCount((byte) 255));
        assertEquals(2, stats.getTotal());
    }

    @Test
    void calculatesLaplaceSmoothedProbability() {
        MarkovStats stats = new MarkovStats();

        stats.recordTransition((byte) 2);
        stats.recordTransition((byte) 2);
        stats.recordTransition((byte) 5);

        assertEquals(3.0 / 7.0, stats.getProbability((byte) 2, 4), 0.000001);
        assertEquals(1.0 / 7.0, stats.getProbability((byte) 9, 4), 0.000001);
    }
}
