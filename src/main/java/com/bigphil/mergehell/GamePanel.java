package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.Timer;

public class GamePanel extends JPanel implements ActionListener {

    private final Timer timer;
    private final Player player;
    private final ObstacleManager enemyManager;
    private Boss boss;
    private LevelManager levelManager;

    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final List<CodeRain> bgLayer1 = new ArrayList<>();
    private final List<CodeRain> bgLayer2 = new ArrayList<>();
    private List<Platform> platforms = new ArrayList<>();
    private List<LevelManager.Coin> coins = new ArrayList<>();

    private final LinkedList<String> logs = new LinkedList<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    private GameState state = GameState.MENU;
    private GameState prePauseState = GameState.RUNNING;

    private final CollisionSystem.Context ctx = new CollisionSystem.Context();
    private final CollisionSystem collision = new CollisionSystem();
    private final GameRenderer renderer = new GameRenderer();

    private int level = 0;
    private int shakeTimer = 0;
    private int flashTimer = 0;
    private double difficulty = 1.0;
    private boolean isNewHighScore = false;
    private double cameraX = 0;
    private int bossDeathTimer = 0;
    private int missionCompleteTimer = 0;
    private int extraLifeScore = 5000;

    private boolean keyLeft, keyRight, keyJump, keyShoot;

    static final int TERMINAL_HEIGHT = 120;
    private static final double CAMERA_LEAD = 0.3;

    public GamePanel() {
        setPreferredSize(new Dimension(960, 600));
        setBackground(GameColors.BG);
        setFocusable(true);

        int groundY = 600 - TERMINAL_HEIGHT;
        player = new Player(100, groundY);
        enemyManager = new ObstacleManager();

        for (int i = 0; i < 30; i++) {
            bgLayer1.add(new CodeRain(getWidth(), groundY));
            bgLayer2.add(new CodeRain(getWidth(), groundY));
        }

        setupKeyBindings();
        addLog("System initialized. Kernel loaded.");
        timer = new Timer(16, this);
        timer.start();
    }

    private void setupKeyBindings() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        registerKey(im, am, "LEFT", KeyEvent.VK_LEFT, true, () -> keyLeft = true);
        registerKey(im, am, "LEFT_R", KeyEvent.VK_LEFT, false, () -> keyLeft = false);
        registerKey(im, am, "RIGHT", KeyEvent.VK_RIGHT, true, () -> keyRight = true);
        registerKey(im, am, "RIGHT_R", KeyEvent.VK_RIGHT, false, () -> keyRight = false);

        registerKey(im, am, "JUMP", KeyEvent.VK_SPACE, true, () -> {
            keyJump = true;
            if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) startGame();
        });
        registerKey(im, am, "JUMP_R", KeyEvent.VK_SPACE, false, () -> keyJump = false);

        registerKey(im, am, "SHOOT", KeyEvent.VK_C, true, () -> keyShoot = true);
        registerKey(im, am, "SHOOT_R", KeyEvent.VK_C, false, () -> keyShoot = false);

        registerKey(im, am, "MELEE", KeyEvent.VK_X, true, () -> player.melee());

        registerKey(im, am, "BOMB", KeyEvent.VK_B, true, () -> {
            if (player.useBomb()) {
                flashTimer = 20; shakeTimer = 25;
                for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
                    if (!en.isDead() && en.getType().isHostile()) {
                        en.setDead(true);
                        ctx.score += en.getType().pointValue;
                    }
                }
                enemyManager.getEnemyBullets().clear();
                addLog("EMERGENCY PROTOCOL: Screen cleared!");
            }
        });

        registerKey(im, am, "DASH", KeyEvent.VK_SHIFT, true, () -> {
            if (keyRight) player.dash(1);
            else if (keyLeft) player.dash(-1);
            else player.dash();
        });

        registerKey(im, am, "PAUSE_P", KeyEvent.VK_P, true, this::togglePause);
        registerKey(im, am, "PAUSE_ESC", KeyEvent.VK_ESCAPE, true, this::togglePause);
    }

    private void registerKey(InputMap im, ActionMap am, String name, int keyCode, boolean pressed, Runnable action) {
        im.put(KeyStroke.getKeyStroke(keyCode, 0, !pressed), name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private void togglePause() {
        if (state == GameState.PAUSED) { state = prePauseState; addLog("System resumed."); }
        else if (state == GameState.RUNNING || state == GameState.BOSS_WARNING || state == GameState.BOSS_FIGHT) {
            prePauseState = state; state = GameState.PAUSED; addLog("System paused by user.");
        }
    }

    public void addLog(String msg) {
        logs.addFirst("[" + timeFmt.format(new Date()) + "] " + msg);
        if (logs.size() > 7) logs.removeLast();
    }

    private void startGame() {
        ctx.score = 0; ctx.combo = 0; ctx.comboTimer = 0;
        ctx.shakeTimer = 0; ctx.flashTimer = 0; ctx.newKills = 0;
        level = 0; shakeTimer = 0; flashTimer = 0;
        difficulty = 1.0; cameraX = 0; isNewHighScore = false;
        bossDeathTimer = 0; missionCompleteTimer = 0;
        extraLifeScore = 5000;
        levelManager = new LevelManager(0);
        platforms = levelManager.getPlatforms();
        coins = levelManager.getCoins();
        boss = null;
        resetGame();
        state = GameState.RUNNING;
        addLog("Starting new session...");
    }

    private void advanceLevel() {
        // Level clear bonus
        int timeBonus = (int) (difficulty * 500);
        int clearBonus = 2000 + level * 1000;
        ctx.score += timeBonus + clearBonus;
        floatingTexts.add(new FloatingText(getWidth() / 2, getHeight() / 2,
                "BONUS +" + (timeBonus + clearBonus), Color.YELLOW));

        difficulty = 1.0 + level * 2.0;
        cameraX = 0;
        extraLifeScore += 10000;
        levelManager = new LevelManager(level);
        platforms = levelManager.getPlatforms();
        coins = levelManager.getCoins();
        boss = null;
        bossDeathTimer = 0; missionCompleteTimer = 0;
        resetGame();
        state = GameState.RUNNING;
        addLog("Level " + (level + 1) + " starting...");
    }

    private void resetGame() {
        player.reset(100, 600 - TERMINAL_HEIGHT);
        enemyManager.reset();
        projectiles.clear(); particles.clear(); floatingTexts.clear();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == GameState.PAUSED) { repaint(); return; }

        if (state == GameState.MISSION_COMPLETE) {
            missionCompleteTimer--;
            if (missionCompleteTimer <= 0) {
                if (level < 2) advanceLevel();
                else { state = GameState.VICTORY; saveScore(); }
            }
            repaint(); return;
        }

        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            int h = getHeight() - TERMINAL_HEIGHT;
            for (CodeRain cr : bgLayer1) cr.update(960, h, 2.0, 0);
            for (CodeRain cr : bgLayer2) cr.update(960, h, 1.0, 0);
            repaint(); return;
        }

        int panelW = getWidth(), panelH = getHeight();
        int groundY = panelH - TERMINAL_HEIGHT;
        double levelWidth = levelManager.getCameraMaxX();

        // ── Player update ───────────────────────────────
        player.update(keyLeft, keyRight, keyJump, keyShoot, groundY, levelWidth, projectiles, platforms);
        if (player.getHp() <= 0) {
            if (player.loseLife()) {
                player.heal(100);
                player.setShieldTimer(120);
                shakeTimer = 20; flashTimer = 15;
                addLog("Process restarted. Lives: " + player.getLives());
            } else {
                state = GameState.GAME_OVER; shakeTimer = 30;
                addLog("FATAL ERROR: All processes terminated.");
                saveScore();
            }
        }

        // ── Camera ──────────────────────────────────────
        // Check for battle zone entry
        LevelManager.BattleZone bz = levelManager.getBattleAt(player.getX());
        if (bz != null && !levelManager.isInBattle()) {
            levelManager.enterBattle(bz);
            addLog("Enemy ambush! Clear the area.");
        }

        // Camera: follow player, but lock during battle
        double targetCam = player.getX() - panelW * CAMERA_LEAD;
        cameraX = Math.max(0, Math.min(targetCam, levelWidth - panelW));
        if (levelManager.isInBattle()) {
            double lockX = levelManager.getCameraLockX();
            if (cameraX > lockX) cameraX = lockX;
        }

        // ── Battle zone waves ───────────────────────────
        int aliveEnemies = (int) enemyManager.getEnemies().stream()
                .filter(en -> !en.isDead() && en.getType().isHostile()).count();

        if (levelManager.needsWaveSpawn(aliveEnemies)) {
            LevelManager.WaveDef wave = levelManager.popWave();
            for (int i = 0; i < wave.count; i++) {
                if (wave.fromDir == 0 || wave.fromDir == 2)
                    enemyManager.spawnEnemy(panelW + random.nextInt(200) + (int) cameraX,
                            groundY - 30 - random.nextInt(120), wave.type);
                if (wave.fromDir == 1 || wave.fromDir == 2)
                    enemyManager.spawnFromLeft(groundY, (int) cameraX);
            }
            levelManager.startNextWaveTimer();
        }

        // ── Triggers + random spawns ────────────────────
        List<LevelManager.SpawnTrigger> triggers = levelManager.getPendingTriggers(player.getX());
        for (LevelManager.SpawnTrigger t : triggers) {
            for (int i = 0; i < t.count; i++) {
                if (t.fromLeft == 0 || t.fromLeft == 2)
                    enemyManager.spawnEnemy(panelW + random.nextInt(200) + (int) cameraX,
                            groundY - 30 - random.nextInt(120), t.type);
                if (t.fromLeft == 1 || t.fromLeft == 2)
                    enemyManager.spawnFromLeft(groundY, (int) cameraX);
            }
        }

        // Extra life check
        if (ctx.score >= extraLifeScore) {
            player.addBomb();
            floatingTexts.add(new FloatingText(player.getX(), player.getY() - 50,
                    "⭐ MILESTONE +2 BOMBS!", Color.ORANGE));
            addLog("Score milestone reached!");
            extraLifeScore += 10000;
        }

        // Mid-boss spawn
        if (!levelManager.isInBattle() && random.nextInt(1500) < 2 + difficulty)
            enemyManager.spawnEnemy(panelW + (int) cameraX + 100, groundY - 60, EntityType.TECHDEBT);

        difficulty += 0.0005;
        if (random.nextInt(100) < 1.5 + difficulty * 0.4)
            enemyManager.spawnRandom(panelW, groundY, difficulty, (int) cameraX);

        // ── Boss ────────────────────────────────────────
        if (state == GameState.RUNNING && levelManager.shouldSpawnBoss(player.getX())) {
            state = GameState.BOSS_WARNING;
            addLog("WARNING: Boss arena detected!");
            boss = new Boss(levelManager.bossName, levelManager.bossHp,
                    levelManager.bossSymbol, cameraX + panelW);
            Timer t = new Timer(2000, evt -> {
                state = GameState.BOSS_FIGHT; boss.activate();
                addLog("ALERT: " + boss.getName() + " engaged!");
                ((Timer) evt.getSource()).stop();
            });
            t.setRepeats(false); t.start();
        }

        if (state == GameState.BOSS_FIGHT && boss != null) {
            if (bossDeathTimer == 0)
                boss.update(enemyManager, groundY, player.getX(), player.getY(),
                        enemyManager.getEnemyBullets());

            if (boss.getHp() <= 0 && bossDeathTimer == 0) {
                bossDeathTimer = 90; shakeTimer = 40;
                addLog("Boss process terminated.");
            }

            if (bossDeathTimer > 0) {
                bossDeathTimer--;
                if (bossDeathTimer % 15 == 0)
                    spawnExplosion((int) boss.getX() + random.nextInt(boss.getWidth()),
                            (int) boss.getY() + random.nextInt(boss.getHeight()),
                            30, GameColors.DANGER_RED);
                if (bossDeathTimer == 50)
                    floatingTexts.add(new FloatingText(boss.getX(), boss.getY(),
                            "PROCESS KILLED!", Color.GREEN));
                if (bossDeathTimer <= 0) {
                    spawnExplosion((int) boss.getX() + boss.getWidth() / 2,
                            (int) boss.getY() + boss.getHeight() / 2, 150, Color.RED);
                    levelManager.onBossDefeated(); level++;
                    missionCompleteTimer = 180; state = GameState.MISSION_COMPLETE;
                }
            }
        }

        // ── Background ──────────────────────────────────
        double paraSpeed = 1.0 + difficulty * 0.3;
        for (CodeRain cr : bgLayer1) cr.update(panelW, groundY, paraSpeed + 1, cameraX);
        for (CodeRain cr : bgLayer2) cr.update(panelW, groundY, paraSpeed * 0.5 + 0.5, cameraX);

        // ── Enemy updates + collision ───────────────────
        double difficultySpeed = 0.8 + difficulty * 0.15;
        List<Projectile> enemyBullets = enemyManager.getEnemyBullets();
        for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
            if (!en.isDead()) {
                en.update(difficultySpeed, player.getX());
                Projectile bullet = en.maybeShoot(player.getY());
                if (bullet != null) enemyBullets.add(bullet);
            }
        }
        // Collect coins
        for (LevelManager.Coin c : coins) {
            if (!c.collected && player.getBounds().intersects(
                    new Rectangle((int) c.x - 10, (int) c.y - 10, 20, 20))) {
                c.collected = true;
                ctx.score += 500;
                floatingTexts.add(new FloatingText(c.x, c.y, "+500", Color.YELLOW));
            }
        }

        enemyManager.update((int) cameraX + panelW + 200);

        collision.process(ctx, projectiles, enemyManager, boss, player, state,
                panelW, panelH, cameraX, particles, floatingTexts, this::addLog);
        ctx.newKills = 0;

        if (ctx.shakeTimer > shakeTimer) shakeTimer = ctx.shakeTimer;
        if (ctx.flashTimer > flashTimer) flashTimer = ctx.flashTimer;
        ctx.shakeTimer = 0; ctx.flashTimer = 0;

        particles.removeIf(p -> p.getLife() <= 0);
        floatingTexts.removeIf(t -> !t.update());
        for (Particle p : particles) p.update();

        repaint();
    }

    private final Random random = new Random();

    private void saveScore() {
        isNewHighScore = ScoreStore.isHighScore(ctx.score);
        ScoreStore.save(ctx.score);
    }

    private void spawnExplosion(int x, int y, int count, Color c) {
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 2 + Math.random() * 8;
            particles.add(new Particle(x, y, c,
                    Math.cos(angle) * speed, Math.sin(angle) * speed - 3,
                    0.03f + (float) Math.random() * 0.03f));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int panelW = getWidth(), panelH = getHeight();
        int groundY = panelH - TERMINAL_HEIGHT;
        int wave = levelManager != null ? levelManager.getCurrentWave() : 0;
        int total = levelManager != null ? levelManager.getTotalWaves() : 0;
        boolean inBattle = levelManager != null && levelManager.isInBattle();

        renderer.render((Graphics2D) g, panelW, panelH, groundY,
                state, player, boss, enemyManager,
                projectiles, enemyManager.getEnemyBullets(),
                particles, floatingTexts, bgLayer1, bgLayer2, platforms, coins,
                logs, ctx.score, ctx.combo, shakeTimer,
                flashTimer, level, difficulty, isNewHighScore,
                cameraX, inBattle, wave, total);

        // Progress bar
        if ((state == GameState.RUNNING || state == GameState.BOSS_FIGHT) && levelManager != null) {
            Graphics2D g2 = (Graphics2D) g;
            double maxX = levelManager.getCameraMaxX();
            double ratio = Math.min(1.0, cameraX / Math.max(1, maxX - panelW));
            g2.setColor(new Color(255, 255, 255, 25));
            g2.fillRect(0, groundY - 3, panelW, 3);
            g2.setColor(new Color(100, 200, 255, 100));
            g2.fillRect(0, groundY - 3, (int) (panelW * ratio), 3);
        }

        if (shakeTimer > 0) shakeTimer--;
        if (flashTimer > 0) flashTimer--;
    }

    LevelManager getLevelManager() { return levelManager; }
    void setLevelManager(LevelManager lm) { this.levelManager = lm; }
}
