package com.bigphil.mergehell.render;

import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.i18n.DisplayTextCapture;
import com.bigphil.mergehell.i18n.GameLanguage;
import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.mission.*;
import com.bigphil.mergehell.model.Player;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HudRendererTest {
    @Test void inlineBombInstrumentTracksSpendingEmptyAndUnlimitedWithoutHidingItsShortcut() {
        for (GameLanguage language : GameLanguage.values()) for (boolean compact : List.of(false, true)) {
            try (var scope = GameText.use(language)) {
                Player player = new Player(100, 480);
                assertBombInstrument(player, compact, "3", "hud.bombs.label");
                assertTrue(player.useBomb());
                assertBombInstrument(player, compact, "2", "hud.bombs.label");
                coolBomb(player); assertTrue(player.useBomb()); coolBomb(player); assertTrue(player.useBomb());
                assertFalse(player.useBomb());
                assertBombInstrument(player, compact, "0", "hud.bombs.empty");
                player.setDebugMode(true);
                assertTrue(player.useBomb());
                assertBombInstrument(player, compact, "∞", "hud.bombs.unlimited");
                player.setDebugMode(false);
                assertBombInstrument(player, compact, "0", "hud.bombs.empty");
                player.addBomb();
                assertBombInstrument(player, compact, "1", "hud.bombs.label");
            }
        }
    }

    private static void coolBomb(Player p) {
        for(int i=0;i<60;i++)p.update(false,false,false,false,480,960,new java.util.ArrayList<>(),List.of());
    }
    private static void assertBombInstrument(Player player, boolean compact, String count, String statusKey) {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try (var capture = new DisplayTextCapture()) {
            HudRenderer renderer = new HudRenderer(); renderer.setCompact(compact);
            // No mission card is required for bombs to remain visible, including during bosses.
            renderer.render(g, new GameSession(12), player, 0, 0, 0, true, false);
            assertTrue(capture.lines().contains(GameText.message(statusKey)));
            assertTrue(capture.lines().contains("[B]"), "B triggers bombs; X is melee");
            assertTrue(capture.lines().contains(count), "Supply remains a separately drawn number");
            assertNotEquals(0, image.getRGB(compact ? 141 : 733, compact ? 87 : 50),
                    "The small bomb icon belongs inside the existing resource panel");
            for (int y = 118; y < 173; y++) for (int x = 14; x < 341; x++)
                assertEquals(0, image.getRGB(x, y), "No extra bomb card may cover scenery or status overlays");
        } finally { g.dispose(); }
    }

    @Test void everyBombStateFitsItsExistingHudRowInBothLanguagesAndSizes() {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            for (GameLanguage language : GameLanguage.values()) for (boolean compact : List.of(false, true))
                try (var scope = GameText.use(language)) {
                g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 20 : 14)));
                int quantityWidth = Math.max(g.getFontMetrics().stringWidth("5"), g.getFontMetrics().stringWidth("∞"));
                g.setFont(GameText.font(new Font(Font.SANS_SERIF, compact ? Font.PLAIN : Font.BOLD, compact ? 18 : 12)));
                int leadingWidth = (compact ? 11 + 6 + 8 : 8 + 4 + 6) + g.getFontMetrics().stringWidth("[B]");
                for (String key : List.of("hud.bombs.label", "hud.bombs.empty", "hud.bombs.unlimited")) {
                    assertTrue(leadingWidth + g.getFontMetrics().stringWidth(GameText.message(key)) + 6 + quantityWidth
                                    <= (compact ? 190 : 134),
                            "The complete inline instrument must leave adjacent lives and scores clear: " + key);
                }
                if (compact) assertTrue(g.getFont().getSize2D() * .625 >= 11,
                        "600px-wide IDE presentation keeps bomb text above 11 physical pixels");
            }
        } finally { g.dispose(); }
    }

    @Test void missionTextIdentifiesStageTimeInsteadOfClaimingABossCountdown() {
        GameSession session = new GameSession(1);
        var card = HudRenderer.missionCard(session.routeProgress());
        assertEquals("ROUTE 1/8 · 7 LEFT", card.route());
        assertEquals("THIS STAGE 00:42", card.condition());
        assertEquals("BOSS: ROUTE + CLEAR AREA", card.next());
        assertEquals(0, card.progress());
    }

    @Test void finalKillsAndGateClearanceReplaceAmbiguousZeroTime() {
        EncounterDirector director = new EncounterDirector(new DarkMissionDefinition(List.of(
                new MissionSegment(MissionSegment.Kind.COMBAT, 2, 1, "CLEAR 30 HOSTILES"),
                new MissionSegment(MissionSegment.Kind.BOSS_GATE, 2, 0, "BOSS")), 100), new Random(1));
        director.tick(new DirectorInput(1, 0, .5, 2, 29));
        director.tick(new DirectorInput(2, 0, .5, 2, 29));
        var waiting = HudRenderer.missionCard(director.routeProgress(29));
        assertEquals("KILLS 29/30 · STAGE 00:00", waiting.condition());
        assertEquals("TIME DONE · FINISH KILLS", waiting.next());
        director.tick(new DirectorInput(3, 0, .5, 2, 30));
        director.tick(new DirectorInput(4, 0, .5, 2, 30));
        var gate = HudRenderer.missionCard(director.routeProgress(30));
        assertEquals("CLEAR AREA: 2 HOSTILES", gate.condition());
        assertEquals("BOSS AFTER AREA IS CLEAR", gate.next());
        assertFalse(gate.condition().contains("00:00"));
    }

    @Test void compactMissionLinesFitTheirPanelWithoutLosingProgressConditions() {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            EncounterDirector director = new EncounterDirector(DarkMissionDefinition.standard(), new Random(2));
            for (int stage = 0; stage < 8; stage++) {
                var card = HudRenderer.missionCard(director.routeProgress(29));
                for (String line : List.of(card.route(), card.objective(), card.condition(), card.next())) {
                    assertTrue(g.getFontMetrics().stringWidth(line) <= 298, "Compact line would be clipped: " + line);
                }
                director.advanceSegmentForTesting(0);
            }
        } finally { g.dispose(); }
    }

    @Test void bossWarningsKeepTheUpperCenterRegionInBothSizes() {
        GameSession session = new GameSession(3);
        session.advanceToBossGateForTesting();
        session.directMission(new DirectorInput(0, 0, .5, 0));
        assertTrue(session.routeProgress().bossSpawned());
        for (boolean compact : List.of(false, true)) {
            BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                HudRenderer renderer = new HudRenderer(); renderer.setCompact(compact);
                renderer.render(g, session, new Player(100, 480), 0, 0, 0, false, true);
            } finally { g.dispose(); }
            for (int y = 12; y < 134; y++) for (int x = 354; x < 680; x++) {
                assertEquals(0, image.getRGB(x, y), "HUD must leave the boss warning region clear");
            }
        }
    }

    @Test void endingInvincibilityRemovesTheCombatBadgeEvenIfTheRunUsedPractice() {
        assertEquals("Invincible · [ T ] Disable", HudRenderer.labStatus(true));
        assertEquals("Invincibility disabled", HudRenderer.labStatus(false));
        GameSession session = new GameSession(4);
        Player player = new Player(100, 480);
        HudRenderer renderer = new HudRenderer(); renderer.setCompact(true);
        BufferedImage off = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = off.createGraphics();
        try { renderer.render(g, session, player, 0, 0, 0, true, true); } finally { g.dispose(); }
        for (int y = 500; y < 559; y++) for (int x = 14; x < 312; x++)
            assertEquals(0, off.getRGB(x, y), "Past practice must not leave a permanent combat badge");
        player.setDebugMode(true);
        Graphics2D on = off.createGraphics();
        try { renderer.render(on, session, player, 0, 0, 0, true, true); } finally { on.dispose(); }
        assertNotEquals(0, off.getRGB(20, 530), "Active invincibility remains explicitly visible");
    }

    @Test void hudRenderingPreservesItsCallersGraphicsState() {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.translate(3, 4); g.clipRect(5, 6, 850, 550);
            g.setColor(Color.MAGENTA); g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, .5f));
            var transform = g.getTransform(); var clip = g.getClipBounds(); var composite = g.getComposite();
            var color = g.getColor(); var font = g.getFont();
            HudRenderer renderer = new HudRenderer(); renderer.setCompact(true);
            renderer.render(g, new GameSession(5), new Player(100, 480), 0, 0, 0, true, true);
            assertEquals(transform, g.getTransform()); assertEquals(clip, g.getClipBounds());
            assertEquals(composite, g.getComposite()); assertEquals(color, g.getColor()); assertEquals(font, g.getFont());
        } finally { g.dispose(); }
    }
}
