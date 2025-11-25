package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.Particle;

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

    private enum GameState {
        MENU, RUNNING, BOSS_WARNING, BOSS_FIGHT, LEVEL_CLEAR, GAME_OVER, VICTORY, PAUSED
    }

    private class CodeRain {
        String text;
        double x, y, speed;
        float alpha;

        CodeRain(int startX, int maxY) {
            reset(startX, maxY);
        }

        void reset(int startX, int maxY) {
            String[] snippets = {
                    "public void fix() {", "return null;", "throw new Exception();",
                    "// TODO: Remove this", "git merge master", "Segmentation fault",
                    "System.exit(0);", "if (bug) panic();", "while(true) {", ">> HEAD"
            };
            this.text = snippets[(int)(Math.random() * snippets.length)];
            this.x = startX + Math.random() * 300;
            this.y = 50 + Math.random() * (maxY - 100);
            this.speed = 2 + Math.random() * 3;
            this.alpha = 0.1f + (float)Math.random() * 0.2f;
        }

        void update(int width, int maxY) {
            x -= speed;
            if (x < -200) {
                reset(width, maxY);
            }
        }

        void draw(Graphics2D g) {
            g.setColor(new Color(1f, 1f, 1f, alpha));
            g.drawString(text, (int)x, (int)y);
        }
    }

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

    private int score = 0;
    private int level = 0;
    private int shakeTimer = 0;
    private int combo = 0;
    private int comboTimer = 0;
    private long frameCount = 0;

    private boolean keyLeft, keyRight, keyJump, keyShoot;

    private final int TERMINAL_HEIGHT = 120;
    private final Color COLOR_BG = Color.decode("#1e1f22");
    private final Color COLOR_BOSS_RED = Color.decode("#e75c4c");

    public GamePanel() {
        setPreferredSize(new Dimension(960, 600));
        setBackground(COLOR_BG);
        setFocusable(true);

        int groundY = 600 - TERMINAL_HEIGHT;
        player = new Player(100, groundY);
        enemyManager = new ObstacleManager();

        for(int i=0; i<30; i++) {
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
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
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
        score = 0;
        level = 0;
        combo = 0;
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
        if (state == GameState.PAUSED) {
            repaint();
            return;
        }

        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            for (CodeRain cr : backgroundCodes) cr.update(getWidth(), getHeight() - TERMINAL_HEIGHT);
            repaint();
            return;
        }

        frameCount++;
        int groundY = getHeight() - TERMINAL_HEIGHT;

        for (CodeRain cr : backgroundCodes) cr.update(getWidth(), groundY);

        if (comboTimer > 0) {
            comboTimer--;
            if (comboTimer == 0) combo = 0;
        }

        player.update(keyLeft, keyRight, keyJump, keyShoot, groundY, getWidth(), projectiles);
        if (player.hp <= 0) {
            state = GameState.GAME_OVER;
            shakeTimer = 30;
            addLog("FATAL ERROR: Process terminated unexpectedly.");
        }

        if (state == GameState.RUNNING) {
            score++;
            enemyManager.spawnRandom(getWidth(), groundY);

            if (score > 1000 + (level * 1000)) {
                state = GameState.BOSS_WARNING;
                addLog("WARNING: CPU usage at 100%!");
                Timer t = new Timer(2000, evt -> {
                    if (state != GameState.PAUSED && state != GameState.GAME_OVER) {
                        state = GameState.BOSS_FIGHT;
                        boss.activate();
                        addLog("ALERT: " + boss.name + " process started!");
                    } else if (state == GameState.PAUSED) {
                        prePauseState = GameState.BOSS_FIGHT;
                        boss.activate();
                    }
                    ((Timer)evt.getSource()).stop();
                });
                t.setRepeats(false);
                t.start();
            }
        } else if (state == GameState.BOSS_FIGHT) {
            boss.update(enemyManager, groundY, player.y);

            if (boss.hp <= 0) {
                spawnExplosion((int)boss.x + boss.width/2, (int)boss.y + boss.height/2, 100, COLOR_BOSS_RED);
                level++;
                addLog("Boss process killed. Memory freed.");
                floatingTexts.add(new FloatingText(boss.x, boss.y, "PROCESS KILLED!", Color.GREEN));

                if (level >= 3) {
                    state = GameState.VICTORY;
                } else {
                    state = GameState.LEVEL_CLEAR;
                    setupLevel(level);
                    Timer t = new Timer(3000, evt -> {
                        state = GameState.RUNNING;
                        resetGame();
                        addLog("Deploying next version...");
                        ((Timer)evt.getSource()).stop();
                    });
                    t.setRepeats(false);
                    t.start();
                }
            }
        }

        enemyManager.update();

        Iterator<Projectile> pIt = projectiles.iterator();
        while (pIt.hasNext()) {
            Projectile p = pIt.next();
            p.update();
            if (p.x > getWidth() || p.x < 0 || p.y > getHeight() || p.y < 0 || p.dead) {
                pIt.remove();
                continue;
            }

            for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
                if (!en.dead && !en.type.startsWith("powerup") && p.getBounds().intersects(en.getBounds())) {
                    en.dead = true;
                    if (!p.type.equals("sudo")) p.dead = true;
                    spawnExplosion((int)en.x, (int)en.y, 10, en.color);

                    combo++;
                    comboTimer = 100;
                    int bonus = 50 + (combo * 10);
                    score += bonus;

                    String text = combo > 1 ? "Combo " + combo + "!" : "+" + bonus;
                    floatingTexts.add(new FloatingText(en.x, en.y, text, Color.WHITE));
                }
            }

            if (state == GameState.BOSS_FIGHT && boss.active && p.getBounds().intersects(boss.getBounds())) {
                boss.takeDamage(p.damage);
                p.dead = true;
                spawnExplosion((int)p.x, (int)p.y, 3, Color.WHITE);
                floatingTexts.add(new FloatingText(p.x, p.y, "-" + p.damage, Color.LIGHT_GRAY));
            }
        }

        for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
            if (!en.dead && player.getBounds().intersects(en.getBounds())) {
                en.dead = true;
                if (en.type.equals("powerup_sudo")) {
                    player.sudoTimer = 600;
                    spawnExplosion((int)player.x, (int)player.y, 20, Color.decode("#f2c55c"));
                    addLog("ROOT ACCESS GRANTED: Spread shot enabled!");
                    floatingTexts.add(new FloatingText(player.x, player.y - 30, "SUDO MODE!", Color.YELLOW));
                } else if (en.type.equals("powerup_shield")) {
                    player.shieldTimer = 400;
                    spawnExplosion((int)player.x, (int)player.y, 20, Color.decode("#40c4ff"));
                    addLog("Firewall rules updated (Shield Up).");
                    floatingTexts.add(new FloatingText(player.x, player.y - 30, "SHIELD UP!", Color.CYAN));
                } else {
                    player.takeDamage(en.damage);
                    shakeTimer = 15;
                    combo = 0;
                    spawnExplosion((int)player.x, (int)player.y, 15, Color.RED);
                    floatingTexts.add(new FloatingText(player.x, player.y, "ERROR!", Color.RED));
                }
            }
        }

        // --- Boss 碰撞修复：增加伤害和反馈 ---
        if (state == GameState.BOSS_FIGHT && boss.active && player.getBounds().intersects(boss.getBounds())) {
            // 普通接触改为 5 点伤害，冲刺接触 35 点
            int damage = boss.isDashing() ? 35 : 5;

            if (player.shieldTimer <= 0 && player.invincibleTimer <= 0) {
                player.takeDamage(damage);

                // 无论哪种伤害，都增加反馈
                if (boss.isDashing()) {
                    // 冲刺重击
                    shakeTimer = 30;
                    floatingTexts.add(new FloatingText(player.x, player.y, "CRITICAL ERROR!", Color.RED));
                    addLog("CRITICAL: Hit by core dump!");
                } else {
                    // 普通接触，轻微震动
                    shakeTimer = 5;
                    floatingTexts.add(new FloatingText(player.x, player.y, "CONTACT -" + damage, Color.ORANGE));
                }
            }
        }

        particles.removeIf(p -> p.life <= 0);
        floatingTexts.removeIf(t -> !t.update());
        for(Particle p : particles) p.update();

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
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (shakeTimer > 0) {
            int dx = (int)(Math.random() * 10 - 5);
            int dy = (int)(Math.random() * 10 - 5);
            g2.translate(dx, dy);
            shakeTimer--;
        }

        int groundY = getHeight() - TERMINAL_HEIGHT;

        g2.setColor(COLOR_BG);
        g2.fillRect(0, 0, getWidth(), groundY);

        g2.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        for (CodeRain cr : backgroundCodes) cr.draw(g2);

        g2.setColor(Color.decode("#323232"));
        g2.fillRect(0, groundY, getWidth(), 10);
        g2.setColor(Color.GRAY);
        g2.drawLine(0, groundY, getWidth(), groundY);

        player.draw(g2);
        enemyManager.draw(g2);
        if (state == GameState.BOSS_FIGHT || state == GameState.BOSS_WARNING) boss.draw(g2);
        for (Projectile p : projectiles) p.draw(g2);
        for (Particle p : particles) p.draw(g2);
        for (FloatingText t : floatingTexts) t.draw(g2);

        drawScanlines(g2, groundY);
        drawHUD(g2);

        if (state == GameState.MENU) {
            drawOverlay(g2, "MERGE HELL 2.0", "PRESS SPACE TO DEPLOY", Color.decode("#3574f0"));
            drawControls(g2, (getHeight() - TERMINAL_HEIGHT) / 2);
        } else if (state == GameState.PAUSED) {
            drawOverlay(g2, "SYSTEM PAUSED", "PRESS 'P' TO RESUME", Color.YELLOW);
            drawControls(g2, (getHeight() - TERMINAL_HEIGHT) / 2);
        } else if (state == GameState.GAME_OVER) {
            drawOverlay(g2, "BUILD FAILED", "See terminal for logs", COLOR_BOSS_RED);
        } else if (state == GameState.BOSS_WARNING) {
            drawCenteredString(g2, "WARNING: HIGH LOAD", groundY/2, 40, Color.RED);
        } else if (state == GameState.VICTORY) {
            drawOverlay(g2, "PRODUCTION READY", "All systems operational.", Color.GREEN);
        }

        if (shakeTimer > 0) g2.translate(0, 0);
        drawTerminal(g2, groundY);
    }

    private void drawControls(Graphics2D g, int centerY) {
        g.setColor(new Color(200, 200, 200));
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 16));

        int startY = centerY + 80;
        int lineHeight = 25;

        String[] lines = {
                "[ ← / → ]   MOVE",
                "[ SPACE ]   JUMP / DOUBLE JUMP",
                "[ C ]       COMMIT (SHOOT)",
                "[ P / ESC]  PAUSE"
        };

        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int x = (getWidth() - fm.stringWidth(line)) / 2;
            g.drawString(line, x, startY + i * lineHeight);
        }
    }

    private void drawScanlines(Graphics2D g, int height) {
        g.setColor(new Color(0, 0, 0, 30));
        for(int i=0; i<height; i+=4) {
            g.fillRect(0, i, getWidth(), 2);
        }
    }

    private void drawTerminal(Graphics2D g, int yStart) {
        g.setColor(new Color(30, 30, 30));
        g.fillRect(0, yStart, getWidth(), TERMINAL_HEIGHT);
        g.setColor(new Color(50, 50, 50));
        g.drawLine(0, yStart, getWidth(), yStart);

        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Terminal: Local", 10, yStart + 16);

        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        int logY = yStart + 35;
        for (String log : logs) {
            if (log.contains("ERROR") || log.contains("WARNING") || log.contains("ALERT") || log.contains("CRITICAL")) g.setColor(COLOR_BOSS_RED);
            else if (log.contains("Sudo") || log.contains("Victory") || log.contains("GRANTED")) g.setColor(Color.YELLOW);
            else g.setColor(Color.GRAY);

            g.drawString(log, 10, logY);
            logY += 16;
        }
    }

    private void drawHUD(Graphics2D g) {
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 18));
        g.setColor(Color.decode("#3574f0"));
        g.drawString("Lines: " + score, 20, 30);

        if (combo > 1) {
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 24));
            g.setColor(Color.YELLOW);
            g.drawString(combo + "x COMBO!", 20, 60);
        }

        int barWidth = 200;
        int barX = getWidth() - barWidth - 20;
        g.setColor(Color.DARK_GRAY);
        g.fillRect(barX, 20, barWidth, 10);
        g.setColor(player.hp < 30 ? Color.RED : Color.decode("#6aab73"));
        int hpWidth = (int)((player.hp / (double)player.maxHp) * barWidth);
        g.fillRect(barX, 20, hpWidth, 10);

        int buffY = 50;

        g.setFont(new Font("SansSerif", Font.BOLD, 12));

        if (player.sudoTimer > 0) {
            g.setColor(Color.decode("#f2c55c"));
            g.drawString("⚡ SUDO MODE " + (player.sudoTimer/60) + "s", barX, buffY);
            buffY += 15;
        }
        if (player.shieldTimer > 0) {
            g.setColor(Color.decode("#40c4ff"));
            g.drawString("🛡️ SHIELD ACTIVE " + (player.shieldTimer/60) + "s", barX, buffY);
        }

        if (state == GameState.BOSS_FIGHT && boss.active) {
            int bw = 600;
            int bx = (getWidth() - bw) / 2;
            g.setColor(Color.DARK_GRAY);
            g.fillRect(bx, 60, bw, 15);
            g.setColor(COLOR_BOSS_RED);
            int bossHpWidth = (int)((boss.hp / (double)boss.maxHp) * bw);
            g.fillRect(bx, 60, bossHpWidth, 15);
            g.setColor(COLOR_BOSS_RED);
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 16));
            g.drawString(boss.name, bx, 55);
        }
    }

    private void drawOverlay(Graphics2D g, String title, String sub, Color c) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, getWidth(), getHeight() - TERMINAL_HEIGHT);
        drawCenteredString(g, title, (getHeight() - TERMINAL_HEIGHT) / 2 - 20, 40, c);
        drawCenteredString(g, sub, (getHeight() - TERMINAL_HEIGHT) / 2 + 30, 20, Color.WHITE);
    }

    private void drawCenteredString(Graphics2D g, String text, int y, int size, Color c) {
        g.setColor(c);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }
}