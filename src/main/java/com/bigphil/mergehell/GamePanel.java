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
    private final List<CodeRain> backgroundCodes = new ArrayList<>();

    private final LinkedList<String> logs = new LinkedList<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    private GameState state = GameState.MENU;
    private GameState prePauseState = GameState.RUNNING;

    private final CollisionSystem.Context ctx = new CollisionSystem.Context();
    private final CollisionSystem collision = new CollisionSystem();
    private final GameRenderer renderer = new GameRenderer();

    private int level = 0;
    private int shakeTimer = 0;

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
            backgroundCodes.add(new CodeRain(960, groundY));
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
        String bossName = lvl == 0 ? "LEGACY CODE MONSTROSITY" : (lvl == 1 ? "MEMORY LEAK DAEMON" : "THE ARCHITECT");
        String bossSymbol = lvl == 0 ? "⚠️" : (lvl == 1 ? "💀" : "👑");
        int hp = 2000 + lvl * 1500;
        boss = new Boss(bossName, hp, bossSymbol, getPreferredSize().width);
    }

    private void startGame() {
        ctx.score = 0;
        ctx.combo = 0;
        ctx.comboTimer = 0;
        ctx.shakeTimer = 0;
        level = 0;
        shakeTimer = 0;
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
            for (CodeRain cr : backgroundCodes) cr.update(getWidth(), getHeight() - TERMINAL_HEIGHT);
            repaint();
            return;
        }

        int groundY = getHeight() - TERMINAL_HEIGHT;

        for (CodeRain cr : backgroundCodes) cr.update(getWidth(), groundY);

        if (ctx.comboTimer > 0) {
            ctx.comboTimer--;
            if (ctx.comboTimer == 0) ctx.combo = 0;
        }

        player.update(keyLeft, keyRight, keyJump, keyShoot, groundY, getWidth(), projectiles);
        if (player.getHp() <= 0) {
            state = GameState.GAME_OVER;
            shakeTimer = 30;
            addLog("FATAL ERROR: Process terminated unexpectedly.");
        }

        if (state == GameState.RUNNING) {
            ctx.score++;
            enemyManager.spawnRandom(getWidth(), groundY);

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
            boss.update(enemyManager, groundY, player.getY());

            if (boss.getHp() <= 0) {
                spawnExplosion((int) boss.getX() + boss.getWidth() / 2,
                               (int) boss.getY() + boss.getHeight() / 2, 100, GameColors.DANGER_RED);
                level++;
                addLog("Boss process killed. Memory freed.");
                floatingTexts.add(new FloatingText(boss.getX(), boss.getY(), "PROCESS KILLED!", Color.GREEN));

                if (level >= 3) {
                    state = GameState.VICTORY;
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

        enemyManager.update();

        collision.process(ctx, projectiles, enemyManager, boss, player, state,
                          getWidth(), getHeight(), particles, floatingTexts, this::addLog);
        if (ctx.shakeTimer > shakeTimer) shakeTimer = ctx.shakeTimer;
        ctx.shakeTimer = 0;

        particles.removeIf(p -> p.getLife() <= 0);
        floatingTexts.removeIf(t -> !t.update());
        for (Particle p : particles) p.update();

        repaint();
    }

    private void spawnExplosion(int x, int y, int count, Color c) {
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(x, y, c, (Math.random() - 0.5) * 12, (Math.random() - 0.5) * 12, 0.04f));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int groundY = getHeight() - TERMINAL_HEIGHT;
        renderer.render((Graphics2D) g, getWidth(), getHeight(), groundY,
                        state, player, boss, enemyManager,
                        projectiles, particles, floatingTexts, backgroundCodes,
                        logs, ctx.score, ctx.combo, shakeTimer);
        if (shakeTimer > 0) shakeTimer--;
    }
}
