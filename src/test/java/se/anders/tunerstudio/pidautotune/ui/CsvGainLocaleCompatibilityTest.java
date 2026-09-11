package se.anders.tunerstudio.pidautotune.ui;

import org.junit.Test;
import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;
import se.anders.tunerstudio.pidautotune.log.IdleLogData;
import se.anders.tunerstudio.pidautotune.log.MslLogReader;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CsvGainLocaleCompatibilityTest {
    @Test
    public void explicitMachineGainColumnsOverrideHumanReadableDecimalCommaMetadata() throws Exception {
        File file = csv(
                "# PID gains at observer start: P 9,99 / I 8,88 / D 7,77\n" +
                "Time,P Gain,I Gain,D Gain,RPM,Idle: Target RPM\n" +
                "s,,,,RPM,RPM\n" +
                "0.000,1.250,0.500,10.750,800,800\n" +
                "0.050,1.250,0.500,10.750,805,800\n");
        try {
            IdleLogData data = new MslLogReader().read(file);
            assertTrue(data.getMetadata().get(0).contains("P 1.25 / I 0.5 / D 10.75"));

            TuneSnapshot snapshot = resolveLiveGains(data);
            assertEquals(1.25, snapshot.getP(), 0.0000001);
            assertEquals(0.5, snapshot.getI(), 0.0000001);
            assertEquals(10.75, snapshot.getD(), 0.0000001);
        } finally {
            file.delete();
        }
    }

    @Test
    public void historicalDecimalCommaGainMetadataStillImportsWithoutMachineColumns() throws Exception {
        File file = csv(
                "# PID gains at observer start: P 1,25 / I 0,5 / D 10,75\n" +
                "Time,RPM,Idle: Target RPM\n" +
                "s,RPM,RPM\n" +
                "0.000,800,800\n" +
                "0.050,805,800\n");
        try {
            IdleLogData data = new MslLogReader().read(file);
            assertTrue(data.getMetadata().get(0).contains("P 1.25 / I 0.5 / D 10.75"));

            TuneSnapshot snapshot = resolveLiveGains(data);
            assertEquals(1.25, snapshot.getP(), 0.0000001);
            assertEquals(0.5, snapshot.getI(), 0.0000001);
            assertEquals(10.75, snapshot.getD(), 0.0000001);
        } finally {
            file.delete();
        }
    }

    @Test
    public void tuneSignatureUsesMachineDecimalPointUnderSwedishLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("sv", "SE"));
            TuneSnapshot snapshot = new TuneSnapshot(1.25, 0.5, 10.75, "test", false);
            assertEquals("P 1.25 / I 0.5 / D 10.75", snapshot.toSignature());
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static TuneSnapshot resolveLiveGains(IdleLogData data) throws Exception {
        Method method = PidAutotunePanel.class.getDeclaredMethod(
                "applyLiveGainMetadata", IdleLogData.class, TuneSnapshot.class);
        method.setAccessible(true);
        return (TuneSnapshot) method.invoke(null, data, TuneSnapshot.unknown("fallback"));
    }

    private static File csv(String content) throws Exception {
        File file = File.createTempFile("pid-tuner-gain-locale-", ".csv");
        Files.write(file.toPath(), content.getBytes(StandardCharsets.ISO_8859_1));
        return file;
    }
}
