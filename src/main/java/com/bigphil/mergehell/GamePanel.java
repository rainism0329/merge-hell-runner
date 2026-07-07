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

    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final List<CodeRain> bgLayer1 = new ArrayList<>();
    private final List<CodeRain> bgLayer2 = new ArrayList<>();

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

    private boolean keyLeft, keyRight, keyJump, keyShoot;

    static final int TERMINAL_HEIGHT = 120;

    public GamePanel() {
        setPreferredSize(new Dimension(960, 600));
        setBackground(GameColors.BG);
        setFocusable(true);

        int groundY = 600 - TERMINAL_HEIGHT;
        player = new Player(100, groundY);
        enemyManager = new ObstacleManager();

        for (int i = 0; i < 30; i++) {
            bgLayer1.add(new CodeRain(960, groundY));
            bgLayer2.add(new CodeRain(960, groundY));
        }

        setupLevel(0);
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
            @Override
            public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private void togglePause() {
        if (state == GameState.PAUSED) {
            state = prePauseState;
            addLog("System resumed.");
        } else if (state == GameState.RUNNING || state == GameState.BOSS_WARNING || state == GameState.BOSS_FIGHT) {
            prePauseState = state;
            state = GameState.PAUSED;
            addLog("System paused by user.");
        }
    }

    public void addLog(String msg) {
        logs.addFirst("[" + timeFmt.format(new Date()) + "] " + msg);
        if (logs.size() > 7) logs.removeLast();
    }

    private void setupLevel(int lvl) {
        this.level = lvl;
        String bossName = lvl == 0 ? "LEGACY CODE MONSTROSITY"
                : (lvl == 1 ? "MEMORY LEAK DAEMON" : "THE ARCHITECT");
        String bossSymbol = lvl == 0 ? "⚠️" : (lvl == 1 ? "💀" : "👑");
        int hpPool = 2000 + lvl * 1500;
        boss = new Boss(bossName, hpPool, bossSymbol, getPreferredSize().width);
    }

    private void startGame() {
        ctx.score = 0;
        ctx.combo = 0;
        ctx.comboTimer = 0;
        ctx.shakeTimer = 0;
        level = 0;
        shakeTimer = 0;
        flashTimer = 0;
        difficulty = 1.0;
        isNewHighScore = false;
        setupLevel(0);
        resetGame();
        state = GameState.RUNNING;
        addLog("Starting new session...");
    }

    private void resetGame() {
        int groundY = getHeight() - TERMINAL_HEIGHT;
        player.reset(100, groundY);
        enemyManager.reset();
        projectiles.clear();
        particles.clear();
        floatingTexts.clear();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == GameState.PAUSED) { repaint(); return; }

        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            int h = getHeight() - TERMINAL_HEIGHT;
            for (CodeRain cr : bgLayer1) cr.update(getWidth(), h, 2.0);
            for (CodeRain cr : bgLayer2) cr.update(getWidth(), h, 1.0);
            repaint();
            return;
        }

        int groundY = getHeight() - TERMINAL_HEIGHT;

        double paraSpeed = 1.0 + difficulty * 0.3;
        for (CodeRain cr : bgLayer1) cr.update(getWidth(), groundY, paraSpeed + 1);
        for (CodeRain cr : bgLayer2) cr.update(getWidth(), groundY, paraSpeed * 0.5 + 0.5);

        if (ctx.comboTimer > 0) {
            ctx.comboTimer--;
            if (ctx.comboTimer == 0) ctx.combo = 0;
        }

        player.update(keyLeft, keyRight, keyJump, keyShoot, groundY, getWidth(), projectiles);
        if (player.getHp() <= 0) {
            state = GameState.GAME_OVER;
            shakeTimer = 30;
            addLog("FATAL ERROR: Process terminated unexpectedly.");
            isNewHighScore = ScoreStore.isHighScore(ctx.score);
            ScoreStore.save(ctx.score);
        }

        if (state == GameState.RUNNING) {
            difficulty += 0.0008;
            ctx.score++;
            enemyManager.spawnRandom(getWidth(), groundY, difficulty);

            if (ctx.score > 1000 + (level * 1000)) {
                state = GameState.BOSS_WARNING;
                addLog("WARNING: CPU usage at 100%!");
                Timer t = new Timer(2000, evt -> {
                    if (state != GameState.PAUSED && state != GameState.GAME_OVER) {
                        state = GameState.BOSS_FIGHT;
                        boss.activate();
                        addLog("ALERT: " + boss.getName() + " process started!");
                    } else if (state == GameState.PAUSED) {
                        prePauseState = GameState.BOSS_FIGHT;
                        boss.activate();
                    }
                    ((Timer) evt.getSource()).stop();
                });
                t.setRepeats(false);
                t.start();
            }
        } else if (state == GameState.BOSS_FIGHT) {
            difficulty += 0.0005;
            ctx.score++;
            enemyManager.spawnRandom(getWidth(), groundY, difficulty);
            boss.update(enemyManager, groundY, player.getX(), player.getY(),
                        enemyManager.getEnemyBullets());

            if (boss.getHp() <= 0) {
                spawnExplosion((int) boss.getX() + boss.getWidth() / 2,
                               (int) boss.getY() + boss.getHeight() / 2, 100, GameColors.DANGER_RED);
                level++;
                addLog("Boss process killed. Memory freed.");
                floatingTexts.add(new FloatingText(boss.getX(), boss.getY(), "PROCESS KILLED!", Color.GREEN));

                if (level >= 3) {
                    state = GameState.VICTORY;
                    isNewHighScore = ScoreStore.isHighScore(ctx.score);
                    ScoreStore.save(ctx.score);
                } else {
                    state = GameState.LEVEL_CLEAR;
                    setupLevel(level);
                    Timer t = new Timer(3000, evt -> {
                        state = GameState.RUNNING;
                        resetGame();
                        addLog("Deploying next version...");
                        ((Timer) evt.getSource()).stop();
                    });
                    t.setRepeats(false);
                    t.start();
                }
            }
        }

        double difficultySpeed = 0.8 + difficulty * 0.15;
        List<Projectile> enemyBullets = enemyManager.getEnemyBullets();
        for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
            if (!en.isDead()) {
                en.update(difficultySpeed);
                Projectile bullet = en.maybeShoot(player.getY());
                if (bullet != null) enemyBullets.add(bullet);
            }
        }
        enemyManager.update(getWidth());

        collision.process(ctx, projectiles, enemyManager, boss, player, state,
                          getWidth(), getHeight(), particles, floatingTexts, this::addLog);
        if (ctx.shakeTimer > shakeTimer) shakeTimer = ctx.shakeTimer;
        if (ctx.flashTimer > flashTimer) flashTimer = ctx.flashTimer;
        ctx.shakeTimer = 0;
        ctx.flashTimer = 0;

        particles.removeIf(p -> p.getLife() <= 0);
        floatingTexts.removeIf(t -> !t.update());
        for (Particle p : particles) p.update();

        repaint();
    }

    private void spawnExplosion(int x, int y, int count, Color c) {
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 2 + Math.random() * 8;
            particles.add(new Particle(x, y, c,
                    Math.cos(angle) * speed,
                    Math.sin(angle) * speed - 3,
                    0.03f + (float) Math.random() * 0.03f));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int groundY = getHeight() - TERMINAL_HEIGHT;
        renderer.render((Graphics2D) g, getWidth(), getHeight(), groundY,
                        state, player, boss, enemyManager,
                        projectiles, enemyManager.getEnemyBullets(),
                        particles, floatingTexts,
                        bgLayer1, bgLayer2,
                        logs, ctx.score, ctx.combo, shakeTimer,
                        flashTimer, level, difficulty, isNewHighScore);
        if (shakeTimer > 0) shakeTimer--;
        if (flashTimer > 0) flashTimer--;
    }
}
