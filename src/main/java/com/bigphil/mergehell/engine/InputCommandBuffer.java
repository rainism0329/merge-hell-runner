package com.bigphil.mergehell.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Boundary between host events and simulation commands. Callbacks execute only when
 * the simulation drains the buffer, never while holding the event-submission lock.
 */
public final class InputCommandBuffer {
    private final Set<Integer> pressedKeys = new HashSet<>();
    private final List<Runnable> pending = new ArrayList<>();
    private long generation;
    private Runnable pendingBarrier;

    public synchronized void press(int keyCode, Runnable command) {
        Objects.requireNonNull(command, "command");
        if (pressedKeys.add(keyCode)) pending.add(command);
    }

    public synchronized void release(int keyCode, Runnable command) {
        Objects.requireNonNull(command, "command");
        if (pressedKeys.remove(keyCode)) pending.add(command);
    }

    public synchronized void submit(Runnable command) {
        pending.add(Objects.requireNonNull(command, "command"));
    }

    /**
     * A lost host focus cancels queued intent and permits fresh physical key edges.
     * A callback already executing or admitted to execution may finish; the remaining
     * old batch is cancelled and this barrier is applied before that drain returns.
     */
    public synchronized void clearAndSubmit(Runnable command) {
        Objects.requireNonNull(command, "command");
        pressedKeys.clear();
        pending.clear();
        generation++;
        pendingBarrier = command;
    }

    public void drain() {
        List<Runnable> commands;
        long batchGeneration;
        synchronized (this) {
            commands = List.copyOf(pending);
            pending.clear();
            batchGeneration = generation;
        }
        try {
            applyPendingBarriers();
            for (Runnable command : commands) {
                synchronized (this) {
                    if (generation != batchGeneration) break;
                    // Admit this callback while locked, then execute without blocking host events.
                }
                command.run();
            }
        } finally {
            // A focus event received during a callback must stop gameplay in this same tick.
            applyPendingBarriers();
        }
    }

    private void applyPendingBarriers() {
        while (true) {
            Runnable barrier;
            synchronized (this) {
                barrier = pendingBarrier;
                pendingBarrier = null;
                if (barrier == null) return;
            }
            // Only cancellation barriers are followed here. New ordinary commands stay
            // queued for the next drain, so producers cannot extend the current batch.
            barrier.run();
        }
    }
}
