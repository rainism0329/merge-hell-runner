package com.bigphil.mergehell.render;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.progression.UpgradeCatalog;
import com.bigphil.mergehell.progression.UpgradeId;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UpgradeOverlayRendererTest {
    @Test void everyTranslatedUpgradeFitsTheTitleDescriptionAndEffectSections() throws Exception {
        Graphics2D g = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
        var impact = UpgradeOverlayRenderer.class.getDeclaredMethod("impactText", GameSession.class,
                com.bigphil.mergehell.progression.UpgradeDefinition.class);
        impact.setAccessible(true);
        try {
            for (var language : com.bigphil.mergehell.i18n.GameLanguage.values())
                try (var scope = com.bigphil.mergehell.i18n.GameText.use(language)) {
                    for (var card : UpgradeCatalog.all()) {
                        g.setFont(com.bigphil.mergehell.i18n.GameText.font(new Font("JetBrains Mono", Font.BOLD, 20)));
                        assertTrue(com.bigphil.mergehell.i18n.GameText.wrap(g.getFontMetrics(), card.title(), 210).size() <= 2,
                                language + " title: " + card.id());
                        g.setFont(com.bigphil.mergehell.i18n.GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, 14)));
                        assertTrue(com.bigphil.mergehell.i18n.GameText.wrap(g.getFontMetrics(), card.description(), 210).size() <= 4,
                                language + " description: " + card.id());
                        if (card.id() != UpgradeId.DRONE_COPILOT) {
                            String text = (String) impact.invoke(null, new GameSession(2), card);
                            assertTrue(com.bigphil.mergehell.i18n.GameText.wrap(g.getFontMetrics(), text, 210).size() <= 3,
                                    language + " effect: " + card.id());
                        }
                    }
                }
        } finally { g.dispose(); }
    }

    @Test void dronePreviewShowsCountAndPerDroneCadenceThenTheFinalRankDifferences() {
        GameSession session = new GameSession(1, WeaponId.COMMIT_CANNON);
        var card = UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT);
        var checkpoint = session.runBuild().checkpoint();
        assertEquals(List.of("DRONES 0 → 1", "10 DMG / 1.15s EACH", "AUTO TARGET · 560px"),
                UpgradeOverlayRenderer.droneImpactLines(session));
        assertEquals(checkpoint, session.runBuild().checkpoint(), "Preview must not install an upgrade");
        session.runBuild().apply(card);
        assertEquals(List.of("DRONES 1 → 2", "10 DMG / 1.15s EACH", "SPLIT TARGETS · 560px"),
                UpgradeOverlayRenderer.droneImpactLines(session));
        session.runBuild().apply(card);
        assertEquals(List.of("EACH SHOT 1.15s → 0.77s", "DAMAGE 10 → 14 / SHOT", "PIERCE 0 → 1 ENEMY"),
                UpgradeOverlayRenderer.droneImpactLines(session));
    }

    @Test void previewUsesTheCurrentWeaponsUpgradedDamageAndRoundsShotsUp() {
        GameSession session = new GameSession(2, WeaponId.GARBAGE_COLLECTOR);
        session.runBuild().apply(UpgradeCatalog.definition(UpgradeId.GC_COMPACTION));
        assertEquals("40 DMG / 1.15s EACH", UpgradeOverlayRenderer.droneImpactLines(session).get(1));
        session.runBuild().apply(UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT));
        session.runBuild().apply(UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT));
        assertEquals("DAMAGE 40 → 55 / SHOT", UpgradeOverlayRenderer.droneImpactLines(session).get(1));
    }

    @Test void everyDroneDescriptionAndEffectLineFitsTheCardsAtMaximumWeaponDamage() {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
            for (WeaponId weapon : WeaponId.values()) {
                GameSession session = new GameSession(3, weapon);
                for (var upgrade : UpgradeCatalog.all()) {
                    if (upgrade.isWeaponRelevant(weapon)) {
                        for (int rank = 0; rank < upgrade.maxRank(); rank++) session.runBuild().apply(upgrade);
                    }
                }
                session.runBuild().tryEvolve();
                for (int rank = 0; rank < 3; rank++) {
                    List<String> lines = new ArrayList<>(UpgradeCatalog.droneDescriptionLines(rank + 1));
                    lines.addAll(UpgradeOverlayRenderer.droneImpactLines(session));
                    for (String line : lines) assertTrue(g.getFontMetrics().stringWidth(line) <= 210,
                            weapon + " rank " + (rank + 1) + " would clip: " + line);
                    session.runBuild().apply(UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT));
                }
            }
        } finally { g.dispose(); }
    }

    @Test void allThreeDroneRankCardsRenderWhilePreservingTheCallersGraphicsState() {
        for (int rank = 0; rank < 3; rank++) {
            GameSession session = sessionWithDroneCard(rank);
            BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.translate(1, 2); g.clipRect(0, 0, 950, 590);
                g.setColor(Color.MAGENTA); g.setFont(new Font(Font.SERIF, Font.BOLD, 17));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, .75f));
                var transform = g.getTransform(); var clip = g.getClipBounds();
                var composite = g.getComposite(); var font = g.getFont(); var color = g.getColor();
                new UpgradeOverlayRenderer().render(g, session);
                assertEquals(transform, g.getTransform()); assertEquals(clip, g.getClipBounds());
                assertEquals(composite, g.getComposite()); assertEquals(font, g.getFont());
                assertEquals(color, g.getColor());
                assertNotEquals(0, image.getRGB(400, 200));
            } finally { g.dispose(); }
        }
    }

    private static GameSession sessionWithDroneCard(int rank) {
        for (int seed = 0; seed < 100; seed++) {
            GameSession session = new GameSession(seed);
            for (int i = 0; i < rank; i++) session.runBuild().apply(UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT));
            session.awardBuildXp(100);
            if (session.upgradeChoices().stream().anyMatch(card -> card.id() == UpgradeId.DRONE_COPILOT)) return session;
        }
        throw new AssertionError("Fixture could not draft a drone card");
    }
}
