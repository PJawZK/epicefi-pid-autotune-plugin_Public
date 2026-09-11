package se.anders.tunerstudio.pidautotune.ui;

import org.junit.Test;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CsvExportWorkerTest {
    @Test
    public void taskRunsOffEdtAndSuccessReturnsToEdt() throws Exception {
        final AtomicBoolean taskRanOnEdt = new AtomicBoolean(true);
        final AtomicBoolean completionRanOnEdt = new AtomicBoolean(false);
        final CountDownLatch completed = new CountDownLatch(1);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                new CsvExportWorker(new CsvExportWorker.Task() {
                    @Override
                    public void run() {
                        taskRanOnEdt.set(SwingUtilities.isEventDispatchThread());
                    }
                }, new CsvExportWorker.Completion() {
                    @Override
                    public void succeeded() {
                        completionRanOnEdt.set(SwingUtilities.isEventDispatchThread());
                        completed.countDown();
                    }

                    @Override
                    public void failed(Throwable failure) {
                        completed.countDown();
                    }
                }).execute();
            }
        });

        assertTrue("worker did not complete", completed.await(5, TimeUnit.SECONDS));
        assertFalse("CSV task must not run on the EDT", taskRanOnEdt.get());
        assertTrue("completion must run on the EDT", completionRanOnEdt.get());
    }

    @Test
    public void failureIsUnwrappedAndReportedOnEdt() throws Exception {
        final AtomicReference<Throwable> reported = new AtomicReference<Throwable>();
        final AtomicBoolean completionRanOnEdt = new AtomicBoolean(false);
        final CountDownLatch completed = new CountDownLatch(1);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                new CsvExportWorker(new CsvExportWorker.Task() {
                    @Override
                    public void run() throws Exception {
                        throw new IOException("disk failed");
                    }
                }, new CsvExportWorker.Completion() {
                    @Override
                    public void succeeded() {
                        completed.countDown();
                    }

                    @Override
                    public void failed(Throwable failure) {
                        reported.set(failure);
                        completionRanOnEdt.set(SwingUtilities.isEventDispatchThread());
                        completed.countDown();
                    }
                }).execute();
            }
        });

        assertTrue("worker did not complete", completed.await(5, TimeUnit.SECONDS));
        assertNotNull(reported.get());
        assertTrue(reported.get() instanceof IOException);
        assertTrue("failure completion must run on the EDT", completionRanOnEdt.get());
    }
}
