package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.render.DarkWorldRenderer;
import com.bigphil.mergehell.render.LegacyWorldRenderer;
import com.bigphil.mergehell.render.IndustrialArt;
import com.bigphil.mergehell.render.ActorVisuals;
import com.bigphil.mergehell.render.HeapWorldRenderer;
import com.bigphil.mergehell.render.HeapActorRenderer;
import com.bigphil.mergehell.render.BlueprintActorRenderer;
import com.bigphil.mergehell.render.KernelActorRenderer;
import com.bigphil.mergehell.render.KernelWorldRenderer;
import com.bigphil.mergehell.world.KernelCoreController;
import com.bigphil.mergehell.world.SingularityEdgeController;
import com.bigphil.mergehell.render.SingularityWorldRenderer;
import com.bigphil.mergehell.render.SingularityActorRenderer;
import com.bigphil.mergehell.render.BlueprintWorldRenderer;
import com.bigphil.mergehell.world.BlueprintCitadelController;
import com.bigphil.mergehell.world.DarkBiome;
import com.bigphil.mergehell.world.WorldScenery;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class GameRenderer {

    private static final int TERMINAL_HEIGHT = 120;
    private static final Font PLATFORM_FONT = new Font("JetBrains Mono", Font.PLAIN, 10);
    private final DarkWorldRenderer darkWorldRenderer = new DarkWorldRenderer();
    private final LegacyWorldRenderer legacyWorldRenderer = new LegacyWorldRenderer();
    private final IndustrialArt industrialArt = IndustrialArt.load();
    private final com.bigphil.mergehell.render.ChapterArt chapterArt = com.bigphil.mergehell.render.ChapterArt.load();
    private final com.bigphil.mergehell.render.ChapterWorldRenderer chapterWorld = new com.bigphil.mergehell.render.ChapterWorldRenderer();
    private com.bigphil.mergehell.world.ChapterRouteController.Snapshot chapter = com.bigphil.mergehell.world.ChapterRouteController.Snapshot.empty();
    public void setChapter(com.bigphil.mergehell.world.ChapterRouteController.Snapshot scene) { chapter = scene; }
    private final ActorVisuals actors = new ActorVisuals();
    private final com.bigphil.mergehell.render.IndustrialDeck deck = new com.bigphil.mergehell.render.IndustrialDeck();
    private double visualSeconds;
    private boolean crtEnabled = true;
    private boolean highContrast;
    private int particlePercent = 100;
    private boolean compact;
    private boolean audioMuted = true;
    public void setAudioMuted(boolean muted) { audioMuted = muted; }
    private boolean bossHudManaged;
    private boolean flashesEnabled = true;
    private int powerFlashTicks;
    private final HeapWorldRenderer heapWorldRenderer = new HeapWorldRenderer();
    private BlueprintCitadelController.Snapshot blueprint = BlueprintCitadelController.Snapshot.empty();
    public void setBlueprint(BlueprintCitadelController.Snapshot scene) { blueprint = scene; }
    private KernelCoreController.Snapshot kernel = KernelCoreController.Snapshot.empty();
    public void setKernel(KernelCoreController.Snapshot scene) { kernel = scene; }
    private SingularityEdgeController.Snapshot singularity = SingularityEdgeController.Snapshot.empty();
    public void setSingularity(SingularityEdgeController.Snapshot scene) { singularity = scene; }
    public GameRenderer() { com.bigphil.mergehell.render.ChapterActorRenderer.preload(); }
    private final com.bigphil.mergehell.render.TraversalRenderer traversalRenderer = new com.bigphil.mergehell.render.TraversalRenderer();
    private com.bigphil.mergehell.world.TraversalEnvironment.Snapshot traversal = com.bigphil.mergehell.world.TraversalEnvironment.Snapshot.empty();
    public void setTraversal(com.bigphil.mergehell.world.TraversalEnvironment.Snapshot scene) { traversal = scene; }
    public void setCompact(boolean enabled) { compact = enabled; }
    public void setBossHudManaged(boolean enabled) { bossHudManaged = enabled; }
    public void setFlashesEnabled(boolean enabled) { flashesEnabled = enabled; }
    public void setPowerFlashTicks(int ticks) { powerFlashTicks = Math.max(0, Math.min(16, ticks)); }

    public void configureVisuals(boolean crt, boolean contrast, int particles) {
        crtEnabled = crt; highContrast = contrast;
        particlePercent = Math.max(0, Math.min(100, particles));
    }

    public void updateVisuals(Player player, List<ObstacleManager.Enemy> enemies, double seconds) {
        visualSeconds += Math.max(0, seconds);
        if (seconds > 0 || actors.snapshot().hero() == null) actors.update(player, enemies, seconds);
    }
    public void resetVisuals() { actors.reset(); visualSeconds = 0; }
    public IndustrialArt art() { return industrialArt; }
    public ActorVisuals.Snapshot actorSnapshot() { return actors.snapshot(); }
    public double visualSeconds() { return visualSeconds; }

    public void render(Graphics2D g, int width, int height, int groundY,
                       GameState state, Player player, Boss boss,
                       ObstacleManager enemyManager,
                       List<Projectile> projectiles, List<Projectile> enemyBullets,
                       List<Particle> particles, List<FloatingText> floatingTexts,
                       List<Platform> platforms, List<LevelManager.Coin> coins,
                       LinkedList<String> logs, int score, int combo, int comboTimer,
                       int shakeTimer, int flashTimer, int level, double difficulty,
                       boolean isNewHighScore, double cameraX,
                       boolean inBattle, int currentWave, int totalWaves,
                       int transitionTimer, double runProgress,
                       DarkBiome darkBiome, List<WorldScenery> scenery,
                       int biomeBannerTicks, Consumer<Graphics2D> worldEffects,
                       Consumer<Graphics2D> bossLayer) {

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean darkMission = level == 0;
        boolean danger = state == GameState.BOSS_FIGHT || state == GameState.BOSS_WARNING;
        LevelTheme theme = LevelTheme.forLevel(level);
        DarkBiome safeBiome = darkBiome == null ? DarkBiome.REPOSITORY_CITY : darkBiome;
        boolean illustrated;
        if (level == 1) {
            heapWorldRenderer.drawBackground(g, width, height, groundY, cameraX, visualSeconds, danger, highContrast);
            illustrated = true;
        } else if (level >= 2) {
            illustrated = chapterArt.backdrop(g, width, height, cameraX, level);
            chapterWorld.atmosphere(g, level, width, groundY, cameraX, visualSeconds);
        } else illustrated = industrialArt.backdrop(g, width, height, cameraX, level);
        if (!illustrated && darkMission) {
            darkWorldRenderer.drawBackground(g, width, groundY, cameraX, safeBiome, danger);
        } else if (!illustrated) {
            legacyWorldRenderer.drawBackground(g, level, width, groundY, cameraX, danger);
        }
        if (highContrast) {
            g.setColor(new Color(2, 7, 12, 112));
            g.fillRect(0, 0, width, height);
        }
        if (level < 2) traversalRenderer.backdrop(g, width, groundY, cameraX, visualSeconds, level, highContrast);

        int shakeX = shakeTimer > 0 ? (int) (Math.sin(visualSeconds * 97) * Math.min(5, shakeTimer * 0.4)) : 0;
        int shakeY = shakeTimer > 0 ? (int) (Math.cos(visualSeconds * 83) * Math.min(4, shakeTimer * 0.3)) : 0;
        Graphics2D world = (Graphics2D) g.create();
        world.translate(shakeX - cameraX, shakeY);
        if (darkMission && !illustrated) {
            darkWorldRenderer.drawScenery(world, scenery, WorldScenery.Layer.BACK,
                    cameraX, width, safeBiome);
            darkWorldRenderer.drawScenery(world, scenery, WorldScenery.Layer.MID,
                    cameraX, width, safeBiome);
            darkWorldRenderer.drawGround(world, width, groundY, cameraX, safeBiome);
        } else if (!illustrated) {
            legacyWorldRenderer.drawGround(world, level, width, groundY, cameraX, theme);
        }
        if (illustrated && level < 2) deck.draw(world, width, groundY, cameraX);
        if (level < 2) traversalRenderer.ground(world, traversal, cameraX, width, groundY, level, visualSeconds);

        if (level >= 2) chapterWorld.world(world, chapter, cameraX, width, groundY, compact);
        else drawPlatforms(world, platforms, darkMission ? safeBiome.accent : theme.accent,
                cameraX, width, darkMission);
        drawCoins(world, coins, cameraX, width);
        worldEffects.accept(world);
        if (industrialArt.has("repair") && actors.snapshot().hero() != null)
            ActorVisuals.hero(world, industrialArt, actors.snapshot().hero());
        else player.draw(world);
        if (actors.snapshot().hero() != null) {
            for (var enemy : actors.snapshot().enemies()) {
                if (enemy.x() + enemy.width() < cameraX - 80 || enemy.x() > cameraX + width + 80) continue;
                if (!com.bigphil.mergehell.render.ChapterActorRenderer.enemy(world, enemy, visualSeconds, flashesEnabled)
                        && !HeapActorRenderer.renderLeak(world, enemy))
                    ActorVisuals.enemy(world, industrialArt, enemy);
            }
        } else enemyManager.draw(world);
        if (boss != null && (state == GameState.BOSS_FIGHT || state == GameState.BOSS_WARNING
                || state == GameState.PAUSED || state == GameState.UPGRADE_SELECTION)) {
            if (boss.getBossLevel() == 1) HeapActorRenderer.renderBoss(world, new HeapActorRenderer.BossVisual(
                    boss.getX(), boss.getY(), boss.getWidth(), boss.getHeight(), boss.getHp(), boss.getMaxHp(),
                    boss.getCombatStage(), boss.getHitFlashTicks(), boss.getVulnerabilityTicks(),
                    visualSeconds, flashesEnabled, highContrast));
            else if (boss.hasMultipartEncounter()) {
                com.bigphil.mergehell.render.ChapterActorRenderer.boss(world, boss, visualSeconds, flashesEnabled, highContrast);
            } else boss.draw(world, flashesEnabled);
        }
        for (Projectile p : projectiles) p.draw(world);
        for (Projectile b : enemyBullets) b.draw(world);
        for (int i = 0; i < particles.size(); i++) {
            if ((i * 37) % 100 < particlePercent) particles.get(i).draw(world);
        }
        for (FloatingText t : floatingTexts) t.draw(world);
        if (level < 2) traversalRenderer.foreground(world, traversal, industrialArt, actors.snapshot().hero(), cameraX, width, compact, particlePercent);
        if (darkMission && !illustrated) darkWorldRenderer.drawScenery(world, scenery, WorldScenery.Layer.FRONT,
                cameraX, width, safeBiome);
        world.dispose();

        Graphics2D bossGraphics = (Graphics2D) g.create();
        try {
            bossGraphics.translate(shakeX, shakeY);
            bossLayer.accept(bossGraphics);
        } finally { bossGraphics.dispose(); }

        if (crtEnabled) drawScanlines(g, width, groundY);

        if (powerFlashTicks > 0) {
            g.setColor(new Color(255, 207, 126, powerFlashTicks * 3));
            g.fillRect(0, 0, width, groundY);
        }

        // Damage flash overlay (screen space)
        if (flashTimer > 0) {
            float alpha = (flashTimer / 15f) * 0.4f;
            g.setColor(new Color(1f, 0f, 0f, Math.min(alpha, 0.4f)));
            g.fillRect(0, 0, width, groundY);
        }

        // Low HP warning vignette
        if (player.getHp() > 0 && player.getHp() < 30) {
            float alpha = (30 - player.getHp()) / 50f * 0.35f;
            g.setColor(new Color(1f, 0f, 0f, alpha));
            g.fillRect(0, 0, width, groundY);
        }

        if (darkMission && state == GameState.RUNNING)
            darkWorldRenderer.drawZoneBanner(g, width, safeBiome, biomeBannerTicks);

        // Kill streak banner
        if (combo == 10 || combo == 20 || combo == 30 || combo == 50) {
            g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 40)));
            g.setColor(new Color(255, 200, 50, 200));
            String text = combo >= 50 ? "GODLIKE!" : combo >= 30 ? "UNSTOPPABLE!"
                    : combo >= 20 ? "RAMPAGE!" : "KILLING SPREE!";
            int tx = (width - g.getFontMetrics().stringWidth(GameText.text(text))) / 2;
            GameText.draw(g, text, tx, groundY / 2);
        }

        chapterWorld.hud(g, chapter, width, danger);
        drawEncounterStatus(g, width, boss, state, inBattle, currentWave, totalWaves);
        drawUI(g, width, height, groundY, state, score, isNewHighScore, level);

        boolean isGameplay = state == GameState.RUNNING || state == GameState.BOSS_WARNING
                || state == GameState.BOSS_FIGHT || state == GameState.LEVEL_CLEAR;
        if (isGameplay) {
            drawEventRibbon(g, width, height, logs);
        }

        // Screen transition overlay
        if (transitionTimer > 0) {
            g.setColor(new Color(0, 0, 0, Math.min(transitionTimer, 255)));
            g.fillRect(0, 0, width, height);
        }
    }

    private void drawUI(Graphics2D g, int width, int height, int groundY,
                         GameState state, int score, boolean isNewHighScore, int level) {
        boolean fullscreen = state == GameState.MENU || state == GameState.PAUSED
                || state == GameState.GAME_OVER || state == GameState.VICTORY
                || state == GameState.MISSION_COMPLETE;
        int areaH = fullscreen ? height : groundY;
        int centerY = areaH / 2;

        if (state == GameState.MENU) {
            drawMenu(g, width, areaH);
        } else if (state == GameState.PAUSED) {
            drawPause(g, width, areaH);
        } else if (state == GameState.GAME_OVER) {
            String sub = "SCORE: " + score + (isNewHighScore ? "  // NEW HIGH SCORE!" : "");
            drawOverlay(g, width, areaH, "BUILD FAILED", sub, GameColors.DANGER_RED);
            drawCenteredString(g, width, "[ SPACE / ENTER ]  NEW RUN", centerY + 72, 12, Color.WHITE);
            drawTopScores(g, width, centerY);
        } else if (state == GameState.MISSION_COMPLETE) {
            String sub = "SCORE: " + score + "  |  Level " + (level + 1) + " cleared";
            drawOverlay(g, width, areaH, "MISSION COMPLETE", sub, new Color(243, 191, 108));
            String next = level < 4 ? "[ ENTER ]  CONTINUE TO WORLD " + (level + 2) : "[ ENTER ]  RESULTS";
            drawCenteredString(g, width, next, centerY + 90, 19, new Color(233, 219, 190));
            drawCenteredString(g, width, "[ Q ]  BACK TO MENU", centerY + 130, 17, new Color(179, 200, 203));
        } else if (state == GameState.VICTORY) {
            String sub = "SCORE: " + score + (isNewHighScore ? "  // NEW HIGH SCORE!" : "");
            drawOverlay(g, width, areaH, "PRODUCTION READY", sub, Color.GREEN);
            drawCenteredString(g, width, "[ SPACE / ENTER ]  NEW RUN", centerY + 72, 12, Color.WHITE);
            drawTopScores(g, width, centerY);
        }
    }

    private com.bigphil.mergehell.progression.CharacterId menuCharacter = com.bigphil.mergehell.progression.CharacterId.REPAIR;
    private com.bigphil.mergehell.progression.GameDifficulty scoreDifficulty = com.bigphil.mergehell.progression.GameDifficulty.STANDARD;
    public void setRunIdentity(com.bigphil.mergehell.progression.CharacterId character,
                               com.bigphil.mergehell.progression.GameDifficulty difficulty) {
        menuCharacter = character;
        scoreDifficulty = difficulty;
    }

    private void drawMenu(Graphics2D g, int width, int height) {
        g.setPaint(new GradientPaint(0, 0, new Color(7, 11, 16, 242), width, 0, new Color(7, 11, 16, 40)));
        g.fillRect(0, 0, width, height);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, 12)));
        g.setColor(new Color(235, 174, 87)); GameText.draw(g, "INDUSTRIAL BUILD  /  REPOSITORY CITY", 64, 72);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, 66)));
        g.setColor(new Color(240, 233, 218)); GameText.draw(g, "MERGE", 60, 164);
        g.setColor(new Color(246, 179, 77)); GameText.draw(g, "HELL", 60, 228);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 17 : 13)));
        g.setColor(new Color(177, 188, 190)); GameText.draw(g, "RUN THE BUILD. OUTRUN THE FAILURE.", 65, 261);
        g.setColor(new Color(224, 215, 197)); GameText.draw(g, GameText.message("menu.roster.tagline"), 65, 292);
        if (industrialArt.has("repair")) {
            Graphics2D character = (Graphics2D) g.create();
            character.translate(727, 428); character.scale(4.3, 4.3);
            ActorVisuals.hero(character, industrialArt, new ActorVisuals.Hero(0, 0, -1,
                    ActorVisuals.Action.IDLE, visualSeconds * 15, 0, 0, false, false, 1, -1, 0, false, menuCharacter));
            character.dispose();
            g.setFont(GameText.font(new Font(Font.MONOSPACED, Font.PLAIN, 10))); g.setColor(new Color(215, 167, 90));
        }
        drawControlStrip(g, width, height - 39);
    }

    private void drawPause(Graphics2D g, int width, int height) {
        g.setColor(new Color(8, 11, 16, 205));
        g.fillRect(0, 0, width, height);
        drawCenteredString(g, width, "RUN PAUSED", height / 2 - 35, 34, Color.YELLOW);
        drawCenteredString(g, width, "[ Q ] MENU / CONTINUE FROM LEVEL ENTRANCE", height / 2 - 7, 12, new Color(190, 198, 207));
        int cardW = Math.min(350, width - 40);
        int cardX = (width - cardW) / 2;
        int cardY = height / 2 + 20;
        drawPanel(g, cardX, cardY, cardW, 52, Color.YELLOW);
        drawCenteredString(g, width, "[ P / ESC ]  RESUME RUN", cardY + 32, 13, Color.WHITE);
        drawCenteredString(g, width, "[ O ] SETTINGS    [ M ] MUTE", cardY + 78, 12, new Color(210, 188, 149));
        drawControlStrip(g, width, Math.min(height - 35, cardY + 124));
    }

    private void drawTopScores(Graphics2D g, int width, int centerY) {
        java.util.List<Integer> scores = ScoreStore.load(scoreDifficulty);
        if (scores.isEmpty()) return;

        g.setFont(GameText.font(new Font("JetBrains Mono", Font.PLAIN, 14)));
        int y = centerY + 120;
        g.setColor(Color.LIGHT_GRAY);
        String header = GameText.message(scoreDifficulty.key()) + " / " + GameText.text("-- TOP SCORES --");
        int hx = (width - g.getFontMetrics().stringWidth(GameText.text(header))) / 2;
        GameText.draw(g, header, hx, y);
        y += 24;
        for (int i = 0; i < Math.min(scores.size(), 5); i++) {
            g.setColor(i == 0 ? Color.YELLOW : Color.LIGHT_GRAY);
            String entry = (i + 1) + ". " + scores.get(i);
            int x = (width - g.getFontMetrics().stringWidth(GameText.text(entry))) / 2;
            GameText.draw(g, entry, x, y);
            y += 20;
        }
    }

    private void drawPlatforms(Graphics2D g, List<Platform> platforms, Color accent,
                               double cameraX, int width, boolean darkMission) {
        for (Platform p : platforms) {
            if (p.x + p.width < cameraX - 120) continue;
            if (p.x > cameraX + width + 120) {
                if (darkMission) break; // generated Dark platforms are x-sorted
                continue; // authored legacy platform lists are not guaranteed to be sorted
            }
            Color body = switch (p.style) {
                case ROOFTOP -> new Color(34, 43, 53);
                case CATWALK -> new Color(42, 43, 43);
                case PIPE -> new Color(59, 44, 38);
                case SERVER_BANK, CABLE -> new Color(25, 48, 52);
                case RUBBLE -> new Color(54, 46, 57);
                case FORTIFICATION -> new Color(58, 35, 37);
                default -> new Color(36, 40, 46);
            };
            g.setPaint(new GradientPaint((float) p.x, (float) p.y, body.brighter(),
                    (float) p.x, (float) (p.y + p.height), body.darker()));
            g.fillRect((int) p.x, (int) p.y, p.width, p.height);
            g.setColor(industrialArt.has("city") ? new Color(244, 177, 87) : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180));
            g.fillRect((int) p.x, (int) p.y, p.width, 2);
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
            g.fillRect((int) p.x, (int) p.y + 2, p.width, 6);
            drawPlatformDetail(g, p, accent, darkMission);
            if (industrialArt.has("city")) {
                int x = (int) p.x, y = (int) p.y;
                g.setColor(new Color(6, 11, 14, 190));
                g.fillRect(x + 3, y + p.height - 3, p.width - 6, 4);
                // End brackets and panel seams give the foothold depth without moving its top.
                for (int end : new int[]{x, x + p.width - 7}) {
                    g.setColor(new Color(73, 69, 56)); g.fillRect(end, y + 2, 7, p.height - 2);
                    g.setColor(new Color(149, 129, 91)); g.drawLine(end + 1, y + 3, end + 1, y + p.height - 3);
                    g.setColor(new Color(14, 18, 20)); g.fillOval(end + 3, y + 6, 3, 3);
                }
                for (int seam = x + 64; seam < x + p.width - 12; seam += 64) {
                    g.setColor(new Color(6, 13, 16, 150)); g.drawLine(seam, y + 5, seam, y + p.height - 4);
                    g.setColor(new Color(145, 112, 65, 120)); g.drawLine(seam + 1, y + 5, seam + 1, y + p.height - 4);
                }
                if (p.style == Platform.Style.ROOFTOP || p.style == Platform.Style.FORTIFICATION) {
                    for (int bracket = x + 15; bracket < x + p.width - 14; bracket += 84) {
                        g.setColor(new Color(11, 17, 21));
                        g.fillPolygon(new int[]{bracket, bracket + 24, bracket + 9},
                                new int[]{y + p.height, y + p.height, y + p.height + 13}, 3);
                        g.setColor(new Color(92, 78, 56));
                        g.drawLine(bracket + 22, y + p.height + 1, bracket + 9, y + p.height + 11);
                        g.setColor(new Color(158, 127, 82)); g.fillOval(bracket + 7, y + p.height + 3, 2, 2);
                    }
                }
            }
        }
    }

    private void drawPlatformDetail(Graphics2D g, Platform p, Color accent, boolean darkMission) {
        int x = (int) p.x, y = (int) p.y;
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 110));
        g.setFont(GameText.font(PLATFORM_FONT));
        switch (p.style) {
            case ROOFTOP -> {
                for (int px = x + 10; px < x + p.width - 8; px += 28) g.fillRect(px, y + 10, 14, 2);
            }
            case CATWALK -> {
                for (int px = x; px < x + p.width; px += 24) {
                    g.drawLine(px, y + p.height, px + 12, y + 3);
                    g.drawLine(px + 12, y + 3, px + 24, y + p.height);
                }
            }
            case PIPE -> {
                g.drawLine(x + 5, y + p.height / 2, x + p.width - 5, y + p.height / 2);
                for (int px = x + 18; px < x + p.width; px += 44) g.drawOval(px, y + 5, 8, 8);
            }
            case SERVER_BANK -> {
                for (int px = x + 8; px < x + p.width - 7; px += 24) g.fillRect(px, y + 8, 13, 4);
            }
            case CABLE -> {
                g.drawArc(x + 8, y + 5, p.width - 16, p.height, 0, 180);
            }
            case RUBBLE -> {
                for (int px = x + 7; px < x + p.width - 8; px += 29) g.drawLine(px, y + 4, px + 13, y + p.height - 3);
            }
            case FORTIFICATION -> {
                for (int px = x + 6; px < x + p.width; px += 36) g.fillRect(px, y + 7, 23, 5);
            }
            default -> {
                GameText.draw(g, "[", x + 2, y + 14);
                GameText.draw(g, "]", x + p.width - 10, y + 14);
            }
        }
    }

    private void drawCoins(Graphics2D g, List<LevelManager.Coin> coins, double cameraX, int width) {
        for (LevelManager.Coin c : coins) {
            if (c.collected) continue;
            if (c.x < cameraX - 40 || c.x > cameraX + width + 40) continue;
            g.setColor(new Color(255, 220, 50));
            g.setFont(GameText.font(new Font("SansSerif", Font.BOLD, 18)));
            int pulse = (int) (Math.sin(visualSeconds * 5) * 3);
            GameText.draw(g, "$", (int) c.x - 5, (int) c.y + 6 + pulse);
        }
    }

    private void drawEventRibbon(Graphics2D g, int width, int height, LinkedList<String> logs) {
        g.setColor(new Color(8, 12, 17, 215)); g.fillRect(0, height - 28, width, 28);
        g.setColor(new Color(169, 123, 62, 130)); g.drawLine(0, height - 28, width, height - 28);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 16 : 11)));
        g.setColor(new Color(193, 200, 192));
        String audioHint = GameText.text(audioMuted ? "M UNMUTE / P PAUSE" : "M MUTE / P PAUSE");
        int hintX = width - 16 - g.getFontMetrics().stringWidth(audioHint);
        if (!logs.isEmpty()) GameText.draw(g, fitText(g, logs.getFirst(), hintX - 32), 16, height - 10);
        g.setColor(new Color(229, 170, 82));
        GameText.draw(g, audioHint, hintX, height - 10);
    }

    private void drawScanlines(Graphics2D g, int width, int height) {
        g.setColor(new Color(0, 0, 0, 15));
        for (int i = 0; i < height; i += 4) {
            g.fillRect(0, i, width + 100, 1);
        }
    }

    private void drawTerminal(Graphics2D g, int width, int yStart, LinkedList<String> logs) {
        g.setColor(new Color(17, 22, 29));
        g.fillRect(0, yStart, width, TERMINAL_HEIGHT);
        g.setColor(new Color(86, 107, 128));
        g.drawLine(0, yStart, width, yStart);

        g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 10)));
        g.setColor(GameColors.HP_BAR);
        g.fillOval(12, yStart + 12, 7, 7);
        g.setColor(new Color(202, 213, 224));
        GameText.draw(g, "LIVE EVENT STREAM", 26, yStart + 19);
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.PLAIN, 9)));
        String status = "LOCAL / CONNECTED";
        g.setColor(new Color(130, 152, 173));
        GameText.draw(g, status, width - g.getFontMetrics().stringWidth(GameText.text(status)) - 12, yStart + 19);
        g.setColor(new Color(255, 255, 255, 24));
        g.drawLine(10, yStart + 28, width - 10, yStart + 28);

        g.setFont(GameText.font(new Font("JetBrains Mono", Font.PLAIN, 11)));
        int logY = yStart + 46;
        for (String log : logs) {
            if (log.contains("ERROR") || log.contains("WARNING") || log.contains("ALERT") || log.contains("CRITICAL")) {
                g.setColor(GameColors.DANGER_RED);
            } else if (log.contains("GRANTED") || log.contains("collected") || log.contains("killed")) {
                g.setColor(Color.YELLOW);
            } else {
                g.setColor(Color.GRAY);
            }
            GameText.draw(g, log, 10, logY);
            logY += 16;
        }
    }

    private void drawEncounterStatus(Graphics2D g, int width, Boss boss, GameState state,
                                     boolean inBattle, int currentWave, int totalWaves) {
        if (inBattle && totalWaves > 0) {
            int displayWave = Math.max(1, Math.min(currentWave, totalWaves));
            g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 12)));
            g.setColor(Color.ORANGE);
            String waveText = "ARENA LOCKED  //  WAVE " + displayWave + " / " + totalWaves;
            int lx = (width - g.getFontMetrics().stringWidth(GameText.text(waveText))) / 2;
            drawPanel(g, lx - 12, 130, g.getFontMetrics().stringWidth(GameText.text(waveText)) + 24, 28, Color.ORANGE);
            GameText.draw(g, waveText, lx, 149);
        }

        if (!bossHudManaged && state == GameState.BOSS_FIGHT && boss != null && boss.isActive()) {
            int bw = Math.min(500, width - 80);
            int bx = (width - bw) / 2;
            drawPanel(g, bx - 10, 124, bw + 20, 58, GameColors.DANGER_RED);
            g.setColor(Color.WHITE);
            g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 10)));
            GameText.draw(g, fitText(g, boss.getName() + "  //  " + boss.getPersonalityName(), bw), bx, 141);
            g.setColor(GameColors.SUDO_YELLOW);
            g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 9)));
            GameText.draw(g, fitText(g, boss.getEncounterStatus(), bw), bx, 157);
            drawMeter(g, bx, 165, bw, 7, boss.getHp() / (double) boss.getMaxHp(), GameColors.DANGER_RED);
        }
    }

    private String fitText(Graphics2D g, String text, int maxWidth) {
        text = GameText.text(text);
        if (g.getFontMetrics().stringWidth(GameText.text(text)) <= maxWidth) return text;
        String suffix = "...";
        int length = text.length();
        while (length > 0
                && g.getFontMetrics().stringWidth(GameText.text(text.substring(0, length) + suffix)) > maxWidth) {
            length--;
        }
        return text.substring(0, length) + suffix;
    }

    private void drawPanel(Graphics2D g, int x, int y, int width, int height, Color accent) {
        Composite original = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f));
        g.setColor(new Color(13, 18, 25));
        g.fillRoundRect(x, y, width, height, 9, 9);
        g.setComposite(original);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 165));
        g.drawRoundRect(x, y, width, height, 9, 9);
    }

    private void drawMeter(Graphics2D g, int x, int y, int width, int height,
                           double ratio, Color fill) {
        double safeRatio = Math.max(0, Math.min(1, ratio));
        g.setColor(new Color(255, 255, 255, 32));
        g.fillRoundRect(x, y, width, height, height, height);
        g.setColor(fill);
        g.fillRoundRect(x, y, (int) (width * safeRatio), height, height, height);
    }

    private void drawControlStrip(Graphics2D g, int width, int y) {
        String controls = compact || width < 620
                ? "[←/→] MOVE   [SPACE] JUMP   [C] FIRE   [P] PAUSE"
                : "[←/→] MOVE   [SPACE] DOUBLE JUMP   [C] FIRE   [X] MELEE   [SHIFT] DASH   [B] BOMB";
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 16 : 10)));
        g.setColor(new Color(167, 181, 196));
        int x = (width - g.getFontMetrics().stringWidth(GameText.text(controls))) / 2;
        GameText.draw(g, controls, Math.max(12, x), y);
    }

    private void drawOverlay(Graphics2D g, int width, int areaH, String title, String sub, Color c) {
        g.setColor(GameColors.OVERLAY);
        g.fillRect(0, 0, width, areaH);
        drawCenteredString(g, width, title, areaH / 2 - 20, 40, c);
        drawCenteredString(g, width, sub, areaH / 2 + 30, 20, Color.WHITE);
    }

    private void drawCenteredString(Graphics2D g, int width, String text, int y, int size, Color c) {
        g.setColor(c);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? Math.max(16, size) : size)));
        FontMetrics fm = g.getFontMetrics();
        int x = (width - fm.stringWidth(GameText.text(text))) / 2;
        GameText.draw(g, text, x, y);
    }

    private void drawControls(Graphics2D g, int width, int centerY) {
        g.setColor(GameColors.CONTROLS_TEXT);
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.PLAIN, 16)));

        int startY = centerY + 50;
        int lineHeight = 25;

        String[] lines = {
                "[ ← / → ]   MOVE",
                "[ SHIFT ]   DASH",
                "[ SPACE ]   JUMP / DOUBLE JUMP",
                "[ C ]       SHOOT",
                "[ X ]       MELEE  [ Z ] WEAPON",
                "[ B ]       BOMB   [ SHIFT ] DASH",
                "[ P / ESC]  PAUSE"
        };

        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int x = (width - fm.stringWidth(GameText.text(line))) / 2;
            GameText.draw(g, line, x, startY + i * lineHeight);
        }
    }
}
