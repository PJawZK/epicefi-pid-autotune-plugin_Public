package se.anders.tunerstudio.pidautotune.controller;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DcIacBiasSettingsAccessTest {
    @Test
    public void interpolatesConfiguredBiasAndReportsSegment() {
        DcIacBiasSettingsAccess.Snapshot snapshot = new DcIacBiasSettingsAccess.Snapshot(
                new double[] { 0.0, 10.0, 20.0, 40.0 },
                new double[] { -40.0, -20.0, 30.0, 50.0 },
                -30.0, 30.0);
        assertEquals(5.0, snapshot.interpolate(15.0), 0.0001);
        assertEquals(35.0, snapshot.interpolate(25.0), 0.0001);
        assertEquals("20↔40%", snapshot.segmentLabel(25.0));
        assertTrue(snapshot.areIntegralLimitsKnown());
    }
}
