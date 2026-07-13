package com.bigphil.mergehell.engine;

import com.intellij.openapi.Disposable;

public interface TickScheduler extends Disposable {
    void scheduleAtFixedRate(Runnable task, long periodMillis);
}
