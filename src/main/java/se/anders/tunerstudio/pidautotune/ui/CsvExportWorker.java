package se.anders.tunerstudio.pidautotune.ui;

import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;

/** Runs CSV serialization and file I/O away from the Swing EDT, then reports completion on the EDT. */
final class CsvExportWorker extends SwingWorker<Void, Void> {
    interface Task {
        void run() throws Exception;
    }

    interface Completion {
        void succeeded();
        void failed(Throwable failure);
    }

    private final Task task;
    private final Completion completion;

    CsvExportWorker(Task task, Completion completion) {
        if (task == null) throw new IllegalArgumentException("task must not be null");
        if (completion == null) throw new IllegalArgumentException("completion must not be null");
        this.task = task;
        this.completion = completion;
    }

    @Override
    protected Void doInBackground() throws Exception {
        task.run();
        return null;
    }

    @Override
    protected void done() {
        try {
            get();
            completion.succeeded();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            completion.failed(ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            completion.failed(cause == null ? ex : cause);
        }
    }
}
