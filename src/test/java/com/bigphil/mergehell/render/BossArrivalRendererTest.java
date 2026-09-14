package com.bigphil.mergehell.render;

import com.bigphil.mergehell.boss.BossArrivalController;
import com.bigphil.mergehell.i18n.DisplayTextCapture;
import com.bigphil.mergehell.i18n.GameLanguage;
import com.bigphil.mergehell.i18n.GameText;
import org.junit.jupiter.api.Test;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class BossArrivalRendererTest {
    private final BossArrivalRenderer renderer = new BossArrivalRenderer();

    @Test void countdownIsVisibleInBothLanguagesAndEveryBossNameIsTranslated() {
        String[] names = {"Legacy Code Monstrosity", "Memory Leak Daemon", "The Architect",
                "Kernel Panic Overlord", "Singularity Engine"};
        for (GameLanguage language : GameLanguage.values()) {
            try (var scope = GameText.use(language)) {
                for (String name : names) {
                    var arrival = arrival(name);
                    try (var capture = new DisplayTextCapture()) {
                        render(arrival.snapshot(), true);
                        assertTrue(capture.lines().contains(GameText.message("boss.arrival.title")));
                        assertTrue(capture.lines().contains(GameText.text(name)), name);
                        assertTrue(capture.lines().contains("3"));
                        assertTrue(capture.lines().contains(GameText.message("boss.arrival.safe")));
                        if (language == GameLanguage.SIMPLIFIED_CHINESE) {
                            assertTrue(capture.lines().stream().noneMatch(line -> line.matches(".*[A-Za-z].*")));
                        } else {
                            assertTrue(capture.lines().stream().noneMatch(line -> line.codePoints()
                                    .anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN)));
                        }
                    }
                }
            }
        }
    }

    @Test void warningKeepsResourceHudAndGroundVisibleAndRepeatedPaintDoesNotAdvanceTime() {
        var arrival = arrival("Legacy Code Monstrosity");
        var before = arrival.snapshot();
        BufferedImage first = render(before, true);
        for (int y = 0; y < 185; y++) for (int x = 0; x < 960; x++) assertEquals(0, first.getRGB(x, y));
        for (int y = 372; y < 600; y++) for (int x = 0; x < 960; x++) assertEquals(0, first.getRGB(x, y));
        assertNotEquals(0, first.getRGB(180, 215), "The warning must remain visible on every frame");
        for (int tick = 0; tick < 50; tick++) arrival.tick(false);
        assertEquals(before, arrival.snapshot());
        assertArrayEquals(first.getRGB(0, 0, 960, 600, null, 0, 960),
                render(before, true).getRGB(0, 0, 960, 600, null, 0, 960));
        arrival.reset();
        BufferedImage empty = render(arrival.snapshot(), false);
        for (int pixel : empty.getRGB(0, 0, 960, 600, null, 0, 960)) assertEquals(0, pixel);
    }

    @Test void longBossNamesDoNotCoverTheCountdownAndRenderingPreservesTheParentState() {
        var longName = arrival("The Architect // BLUEPRINT TYRANT // LONG ENCOUNTER IDENTITY").snapshot();
        var shortName = arrival("The Architect").snapshot();
        BufferedImage longFrame = render(longName, true), shortFrame = render(shortName, true);
        for (int y = 205; y < 335; y++) for (int x = 688; x < 804; x++)
            assertEquals(shortFrame.getRGB(x, y), longFrame.getRGB(x, y), "The countdown column must stay clear");
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.translate(5, 7); g.setClip(0, 0, 900, 550); g.setColor(Color.MAGENTA);
        g.setStroke(new BasicStroke(7)); g.setComposite(AlphaComposite.SrcOver.derive(.6f));
        g.setFont(new Font(Font.MONOSPACED, Font.ITALIC, 19));
        var transform = g.getTransform(); var clip = g.getClipBounds(); var paint = g.getPaint();
        var stroke = g.getStroke(); var font = g.getFont(); var composite = g.getComposite();
        renderer.renderOverlay(g, longName, 960, 480, true);
        assertEquals(transform, g.getTransform()); assertEquals(clip, g.getClipBounds());
        assertEquals(paint, g.getPaint()); assertEquals(stroke, g.getStroke());
        assertEquals(font, g.getFont()); assertEquals(composite, g.getComposite());
        g.dispose();
    }

    private BossArrivalController arrival(String name) {
        BossArrivalController arrival = new BossArrivalController();
        arrival.begin(name, BossArrivalController.Direction.RIGHT);
        return arrival;
    }

    private BufferedImage render(BossArrivalController.Snapshot snapshot, boolean compact) {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        renderer.renderOverlay(g, snapshot, 960, 480, compact);
        g.dispose();
        return image;
    }
}
