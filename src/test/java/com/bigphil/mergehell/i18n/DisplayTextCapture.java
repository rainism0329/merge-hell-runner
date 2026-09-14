package com.bigphil.mergehell.i18n;

import java.util.ArrayList;
import java.util.List;

/** Observes final production drawing calls; never substitutes a test renderer. */
public final class DisplayTextCapture implements AutoCloseable {
    private final List<String> lines = new ArrayList<>();
    private final java.util.function.Consumer<String> previous = GameText.DISPLAY_OBSERVER.get();
    public DisplayTextCapture() { GameText.DISPLAY_OBSERVER.set(lines::add); }
    public List<String> lines() { return List.copyOf(lines); }
    @Override public void close() {
        if (previous == null) GameText.DISPLAY_OBSERVER.remove(); else GameText.DISPLAY_OBSERVER.set(previous);
    }
}
