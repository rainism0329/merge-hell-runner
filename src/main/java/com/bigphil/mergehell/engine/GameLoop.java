package com.bigphil.mergehell.engine;

import com.intellij.openapi.Disposable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class GameLoop implements Disposable {
    private static final long FRAME_MILLIS = 16;

    private final TickScheduler scheduler;
    private final Runnable tick;
    private final Consumer<Throwable> errorHandler;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicBoolean errorReported = new AtomicBoolean();

    public GameLoop(TickScheduler scheduler, Runnable tick, Consumer<Throwable> errorHandler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.tick = Objects.requireNonNull(tick, "tick");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    public void start() {
        if (disposed.get() || !started.compareAndSet(false, true)) return;
        scheduler.scheduleAtFixedRate(this::runFrame, FRAME_MILLIS);
    }

    private void runFrame() {
        if (disposed.get() || paused.get()) return;
        try {
            tick.run();
        } catch (Throwable error) {
            paused.set(true);
            if (errorReported.compareAndSet(false, true)) errorHandler.accept(error);
        }
    }

    public void pause() { paused.set(true); }
    public void resume() { if (!disposed.get()) paused.set(false); }
    public boolean isPaused() { return paused.get(); }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) scheduler.dispose();
    }
}
