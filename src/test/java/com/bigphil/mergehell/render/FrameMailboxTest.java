package com.bigphil.mergehell.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class FrameMailboxTest {
    private static final int SIZE = 8;

    @Test
    void emptyMailboxAndInvalidPaintDimensionsLeaveTheDestinationUntouched() {
        assertThrows(IllegalArgumentException.class, () -> new FrameMailbox(0, 8));
        try (FrameMailbox mailbox = new FrameMailbox(SIZE, SIZE)) {
            assertEquals(3, mailbox.retainedBufferCount());
            BufferedImage target = image();
            Graphics2D graphics = target.createGraphics();
            try {
                assertFalse(mailbox.paint(graphics, 0, 0, SIZE, SIZE));
                assertTrue(publish(mailbox, Color.RED));
                assertFalse(mailbox.paint(graphics, 0, 0, 0, SIZE));
                assertEquals(0, target.getRGB(0, 0));
            } finally { graphics.dispose(); }
        }
    }

    @Test
    void reusedBackBufferStartsClearAndPaintPreservesTheCallerGraphicsState() {
        try (FrameMailbox mailbox = new FrameMailbox(SIZE, SIZE)) {
            publish(mailbox, Color.RED);
            publish(mailbox, Color.GREEN);
            mailbox.publish(graphics -> {
                graphics.setColor(Color.BLUE);
                graphics.fillRect(0, 0, 1, 1);
            });
            BufferedImage current = capture(mailbox);
            assertEquals(Color.BLUE.getRGB(), current.getRGB(0, 0));
            assertEquals(0, current.getRGB(1, 1), "A reused buffer cannot leak pixels from an older frame");

            Graphics2D graphics = image().createGraphics();
            try {
                graphics.translate(1, 2);
                graphics.setComposite(AlphaComposite.SrcOver.derive(0.5f));
                graphics.setColor(Color.ORANGE);
                AffineTransform transform = graphics.getTransform();
                Composite composite = graphics.getComposite();
                assertTrue(mailbox.paint(graphics, 0, 0, SIZE, SIZE));
                assertEquals(transform, graphics.getTransform());
                assertEquals(composite, graphics.getComposite());
                assertEquals(Color.ORANGE, graphics.getColor());
            } finally { graphics.dispose(); }
        }
    }

    @Test
    void slowReadersPinTheirFramesAndSaturationDropsPublicationWithoutCallingItsPainter() throws Exception {
        try (FrameMailbox mailbox = new FrameMailbox(SIZE, SIZE)) {
            publish(mailbox, Color.RED);
            SlowReader redReader = new SlowReader(mailbox);
            SlowReader greenReader = null;
            try {
                redReader.awaitStarted();
                assertTrue(publish(mailbox, Color.GREEN));
                greenReader = new SlowReader(mailbox);
                greenReader.awaitStarted();
                assertTrue(publish(mailbox, Color.BLUE));
                AtomicInteger droppedPainterCalls = new AtomicInteger();
                assertFalse(mailbox.publish(graphics -> droppedPainterCalls.incrementAndGet()));
                assertEquals(0, droppedPainterCalls.get());
                assertSolid(capture(mailbox), Color.BLUE);

                redReader.finish();
                assertSolid(redReader.target, Color.RED);
                assertTrue(publish(mailbox, Color.YELLOW), "Released old reader slot becomes writable again");
                greenReader.finish();
                assertSolid(greenReader.target, Color.GREEN);
                assertSolid(capture(mailbox), Color.YELLOW);
            } finally {
                redReader.finish();
                if (greenReader != null) greenReader.finish();
            }
        }
    }

    @Test
    void inProgressAndFailedWritesNeverReplaceTheLastCompletedFrame() throws Exception {
        try (FrameMailbox mailbox = new FrameMailbox(SIZE, SIZE)) {
            publish(mailbox, Color.GREEN);
            CountDownLatch halfDrawn = new CountDownLatch(1);
            CountDownLatch finishDrawing = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread writer = thread(() -> {
                try {
                    mailbox.publish(graphics -> {
                        graphics.setColor(Color.RED);
                        graphics.fillRect(0, 0, SIZE / 2, SIZE);
                        halfDrawn.countDown();
                        await(finishDrawing);
                        throw new IllegalStateException("painter failed midway");
                    });
                } catch (Throwable exception) { failure.set(exception); }
            }, "FrameMailboxTest-writer");
            try {
                assertTrue(halfDrawn.await(2, TimeUnit.SECONDS));
                assertSolid(capture(mailbox), Color.GREEN);
            } finally {
                finishDrawing.countDown();
                join(writer);
            }
            assertInstanceOf(IllegalStateException.class, failure.get());
            assertSolid(capture(mailbox), Color.GREEN);
            assertTrue(publish(mailbox, Color.BLUE), "Failure must release the reserved write slot");
            assertSolid(capture(mailbox), Color.BLUE);
        }
    }

    @Test
    void closeReleasesIdleImagesImmediatelyAndPinnedImagesAfterTheirOperationsFinish() throws Exception {
        FrameMailbox mailbox = new FrameMailbox(SIZE, SIZE);
        publish(mailbox, Color.RED);
        SlowReader reader = new SlowReader(mailbox);
        CountDownLatch drawing = new CountDownLatch(1);
        CountDownLatch finishDrawing = new CountDownLatch(1);
        AtomicBoolean writerPublished = new AtomicBoolean(true);
        AtomicReference<Throwable> writerFailure = new AtomicReference<>();
        Thread writer = null;
        try {
            reader.awaitStarted();
            writer = thread(() -> {
                try {
                    writerPublished.set(mailbox.publish(graphics -> {
                        drawing.countDown();
                        await(finishDrawing);
                        graphics.setColor(Color.GREEN);
                        graphics.fillRect(0, 0, SIZE, SIZE);
                    }));
                } catch (Throwable exception) { writerFailure.set(exception); }
            }, "FrameMailboxTest-closing-writer");
            assertTrue(drawing.await(2, TimeUnit.SECONDS));
            mailbox.close();
            mailbox.close();
            assertTrue(mailbox.isClosed());
            assertEquals(2, mailbox.retainedBufferCount());
            assertFalse(mailbox.publish(graphics -> fail("Closed mailbox must not call the painter")));
            Graphics2D destination = image().createGraphics();
            try { assertFalse(mailbox.paint(destination, 0, 0, SIZE, SIZE)); }
            finally { destination.dispose(); }

            reader.finish();
            assertSolid(reader.target, Color.RED);
            assertEquals(1, mailbox.retainedBufferCount());
            finishDrawing.countDown();
            join(writer);
            assertNull(writerFailure.get());
            assertFalse(writerPublished.get(), "Close during drawing prevents publication");
            assertEquals(0, mailbox.retainedBufferCount());
        } finally {
            finishDrawing.countDown();
            reader.finish();
            if (writer != null) join(writer);
            mailbox.close();
        }
    }

    @Test
    void readerExceptionCannotPinABufferAfterClose() {
        FrameMailbox mailbox = new FrameMailbox(SIZE, SIZE);
        publish(mailbox, Color.RED);
        Graphics2D destination = image().createGraphics();
        destination.setComposite((source, target, hints) -> new CompositeContext() {
            @Override public void dispose() { }
            @Override public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
                throw new IllegalStateException("reader failed");
            }
        });
        try {
            assertThrows(IllegalStateException.class, () -> mailbox.paint(destination, 0, 0, SIZE, SIZE));
        } finally {
            destination.dispose();
            mailbox.close();
        }
        assertEquals(0, mailbox.retainedBufferCount());
    }

    private static boolean publish(FrameMailbox mailbox, Color color) {
        return mailbox.publish(graphics -> {
            graphics.setColor(color);
            graphics.fillRect(0, 0, SIZE, SIZE);
        });
    }
    private static BufferedImage image() { return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB); }
    private static BufferedImage capture(FrameMailbox mailbox) {
        BufferedImage result = image();
        Graphics2D graphics = result.createGraphics();
        try { assertTrue(mailbox.paint(graphics, 0, 0, SIZE, SIZE)); }
        finally { graphics.dispose(); }
        return result;
    }
    private static void assertSolid(BufferedImage image, Color color) {
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) assertEquals(color.getRGB(), image.getRGB(x, y));
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
    private static Thread thread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
    private static void join(Thread thread) throws InterruptedException {
        thread.join(2_000);
        assertFalse(thread.isAlive(), "Mailbox operation must finish after releasing its test latch");
    }

    /** A Java2D consumer that blocks inside source composition, without exposing the mailbox image. */
    private static final class SlowReader {
        final BufferedImage target = image();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread thread;

        SlowReader(FrameMailbox mailbox) {
            thread = thread(() -> {
                Graphics2D graphics = target.createGraphics();
                graphics.setComposite(new Composite() {
                    @Override public CompositeContext createContext(ColorModel source, ColorModel target, RenderingHints hints) {
                        CompositeContext delegate = AlphaComposite.SrcOver.createContext(source, target, hints);
                        return new CompositeContext() {
                            @Override public void dispose() { delegate.dispose(); }
                            @Override public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
                                started.countDown();
                                await(release);
                                delegate.compose(src, dstIn, dstOut);
                            }
                        };
                    }
                });
                try { assertTrue(mailbox.paint(graphics, 0, 0, SIZE, SIZE)); }
                catch (Throwable exception) { failure.set(exception); }
                finally { graphics.dispose(); }
            }, "FrameMailboxTest-reader");
        }
        void awaitStarted() throws InterruptedException { assertTrue(started.await(2, TimeUnit.SECONDS)); }
        void finish() throws InterruptedException {
            release.countDown();
            join(thread);
            assertNull(failure.get());
        }
    }
}
