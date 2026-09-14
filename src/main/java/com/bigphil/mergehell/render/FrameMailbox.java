package com.bigphil.mergehell.render;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Three preallocated raster frames passed from the simulation renderer to Swing.
 * This is a completed-frame boundary, not a semantic game-state snapshot.
 * A slow reader pins its frame; publishing drops a frame instead of waiting for it.
 * No drawing, supplied callback or image release runs under the mailbox lock.
 */
public final class FrameMailbox implements AutoCloseable {
    private static final int BUFFER_COUNT = 3;
    private static final class Slot {
        BufferedImage image;
        int readers;
        boolean writing;
        Slot(int width, int height) { image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE); }
    }

    private final Object lock = new Object();
    private final Slot[] slots = new Slot[BUFFER_COUNT];
    private final int width;
    private final int height;
    private Slot latest;
    private boolean closed;

    public FrameMailbox() { this(960, 600); }

    public FrameMailbox(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Frame dimensions must be positive");
        this.width = width;
        this.height = height;
        for (int i = 0; i < slots.length; i++) slots[i] = new Slot(width, height);
    }

    /**
     * Render one complete frame. False means closed or no free slot, and the callback
     * is not invoked in those cases. Callback failures propagate without replacing
     * the previous complete frame. A close during rendering also prevents publication.
     * The supplied Graphics2D is valid only during the callback and must not be retained.
     */
    public boolean publish(Consumer<Graphics2D> draw) {
        Objects.requireNonNull(draw, "draw");
        Slot destination = null;
        BufferedImage image;
        synchronized (lock) {
            if (closed) return false;
            for (Slot slot : slots) {
                if (slot != latest && !slot.writing && slot.readers == 0) {
                    destination = slot;
                    break;
                }
            }
            if (destination == null) return false;
            destination.writing = true;
            image = destination.image;
        }

        boolean complete = false;
        boolean published = false;
        try {
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Clear);
                graphics.fillRect(0, 0, width, height);
                graphics.setComposite(AlphaComposite.SrcOver);
                draw.accept(graphics);
                complete = true;
            } finally {
                graphics.dispose();
            }
        } finally {
            BufferedImage release;
            synchronized (lock) {
                destination.writing = false;
                if (complete && !closed) {
                    latest = destination;
                    published = true;
                }
                release = detachIfUnusedAndClosed(destination);
            }
            if (release != null) release.flush();
        }
        return published;
    }

    /** Paint the latest complete frame. A false return leaves the destination unchanged. */
    public boolean paint(Graphics2D graphics, int x, int y, int drawWidth, int drawHeight) {
        Objects.requireNonNull(graphics, "graphics");
        if (drawWidth <= 0 || drawHeight <= 0) return false;
        Slot source;
        BufferedImage image;
        synchronized (lock) {
            if (closed || latest == null) return false;
            source = latest;
            source.readers++;
            image = source.image;
        }
        try {
            graphics.drawImage(image, x, y, drawWidth, drawHeight, null);
            return true;
        } finally {
            BufferedImage release;
            synchronized (lock) {
                source.readers--;
                release = detachIfUnusedAndClosed(source);
            }
            if (release != null) release.flush();
        }
    }

    /** Must be called while locked. The detached image is flushed only after releasing the lock. */
    private BufferedImage detachIfUnusedAndClosed(Slot slot) {
        if (!closed || slot.readers != 0 || slot.writing) return null;
        BufferedImage image = slot.image;
        slot.image = null;
        return image;
    }

    /** Discard unpublished/current frames; an in-flight reader or writer keeps its slot until it exits. */
    @Override
    public void close() {
        BufferedImage[] release = new BufferedImage[BUFFER_COUNT];
        synchronized (lock) {
            if (closed) return;
            closed = true;
            latest = null;
            for (int i = 0; i < slots.length; i++) release[i] = detachIfUnusedAndClosed(slots[i]);
        }
        for (BufferedImage image : release) if (image != null) image.flush();
    }

    public boolean isClosed() { synchronized (lock) { return closed; } }

    /** Package-private lifecycle diagnostic; never exposes a writable frame to readers. */
    int retainedBufferCount() {
        synchronized (lock) {
            int count = 0;
            for (Slot slot : slots) if (slot.image != null) count++;
            return count;
        }
    }
}
