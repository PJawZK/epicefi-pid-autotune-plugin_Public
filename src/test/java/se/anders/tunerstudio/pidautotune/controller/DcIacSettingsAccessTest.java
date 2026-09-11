package se.anders.tunerstudio.pidautotune.controller;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DcIacSettingsAccessTest {
    @Test
    public void unavailableSharedPauseDoesNotBlockDcIacOnlyConfiguration() {
        DcIacSettingsAccess.Snapshot snapshot = new DcIacSettingsAccess.Snapshot(0.0, 100.0, null);
        assertFalse(snapshot.isMotorControlPauseKnown());
        assertFalse(snapshot.isMotorControlPaused());
        assertFalse(snapshot.arePidGainsKnown());
    }

    @Test
    public void exposedActiveSharedPauseStillBlocksEvidence() {
        DcIacSettingsAccess.Snapshot snapshot = new DcIacSettingsAccess.Snapshot(0.0, 100.0, Boolean.TRUE);
        assertTrue(snapshot.isMotorControlPauseKnown());
        assertTrue(snapshot.isMotorControlPaused());
    }

    @Test
    public void pidGainsAreAvailableForReadOnlyM3EvidenceModel() {
        DcIacSettingsAccess.Snapshot snapshot = new DcIacSettingsAccess.Snapshot(
                0.0, 100.0, Boolean.FALSE,
                Double.valueOf(5.0), Double.valueOf(0.5), Double.valueOf(0.01));
        assertTrue(snapshot.arePidGainsKnown());
        assertEquals(5.0, snapshot.getPFactor(), 0.000001);
        assertEquals(0.5, snapshot.getIFactor(), 0.000001);
        assertEquals(0.01, snapshot.getDFactor(), 0.000001);
    }
}
