package com.bigphil.mergehell.engine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScheduledTickScheduler implements TickScheduler {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "MergeHell-GameLoop");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean disposed = new AtomicBoolean();

    @Override
    public void scheduleAtFixedRate(Runnable task, long periodMillis) {
        if (disposed.get()) throw new IllegalStateException("scheduler disposed");
        executor.scheduleAtFixedRate(task, 0, periodMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) executor.shutdownNow();
    }
}
