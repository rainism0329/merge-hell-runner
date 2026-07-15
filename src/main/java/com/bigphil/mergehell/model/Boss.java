package com.bigphil.mergehell.model;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Bosses for the four legacy campaign levels. The Dark mission keeps its bespoke
 * dependency-graph controller; these encounters deliberately use different movement,
 * projectile grammar, summons, phase names, and silhouettes instead of shared stat scaling.
 */
public class Boss {
    private enum Phase { ENTER, IDLE, DASH_WARN, DASH, RECOVER }

    private enum Archetype {
        GENERIC("SYSTEM ANOMALY", new Color(231, 92, 76), new Color(242, 197, 92),
                new String[]{"OBSERVE", "ESCALATE", "TERMINATE"}, "SCANNING"),
        MEMORY_LEAK("HEAP PREDATOR", new Color(166, 226, 46), new Color(249, 38, 114),
                new String[]{"ALLOCATING", "LEAKING", "OUT OF MEMORY"}, "HEAP GROWING"),
        ARCHITECT("BLUEPRINT TYRANT", new Color(64, 196, 255), new Color(181, 137, 0),
                new String[]{"DRAFT", "ENFORCE", "PERFECT FORM"}, "CALCULATING"),
        KERNEL_PANIC("RING-0 EXECUTIONER", new Color(255, 85, 85), new Color(180, 142, 173),
                new String[]{"BOOT", "PANIC", "SYSTEM HALT"}, "WATCHDOG ACTIVE"),
        SINGULARITY("PATTERN DEVOURER", new Color(255, 121, 198), new Color(139, 233, 253),
                new String[]{"OBSERVE", "ASSIMILATE", "COLLAPSE"}, "LEARNING BUILD");

        final String personality;
        final Color primary;
        final Color accent;
        final String[] stages;
        final String idleMove;

        Archetype(String personality, Color primary, Color accent,
                  String[] stages, String idleMove) {
            this.personality = personality;
            this.primary = primary;
            this.accent = accent;
            this.stages = stages;
            this.idleMove = idleMove;
        }

        static Archetype forLevel(int level) {
            return switch (level) {
                case 1 -> MEMORY_LEAK;
                case 2 -> ARCHITECT;
                case 3 -> KERNEL_PANIC;
                case 4 -> SINGULARITY;
                default -> GENERIC;
            };
        }
    }

    private double x;
    private double y;
    private final int width = 120;
    private final int height = 150;
    private final int maxHp;
    private int hp;
    private final String name;
    private final String symbol;
    private final int bossLevel;
    private final Archetype archetype;
    private final Random random;
    private final Set<String> movesSeen = new LinkedHashSet<>();

    private boolean active;
    private Phase phase = Phase.ENTER;
    private double targetX;
    private double playerX;
    private double playerY;
    private double dashDir = -1;
    private int combatTick;
    private int actionCooldown = 90;
    private int minorCooldown = 45;
    private int patternIndex;
    private int warningTicks;
    private int dashTicks;
    private int moveLabelTicks;
    private int lastStage = 1;
    private int lastGroundY = 480;
    private String moveName = "MATERIALIZING";
    private String dashMoveName = "CORE DUMP";
    private boolean flashing;

    public Boss(String name, int hpPool, String symbol, double spawnWorldX, int bossLevel) {
        this(name, hpPool, symbol, spawnWorldX, bossLevel, new Random());
    }

    Boss(String name, int hpPool, String symbol, double spawnWorldX,
         int bossLevel, Random random) {
        this.name = Objects.requireNonNull(name, "name");
        this.maxHp = Math.max(1, hpPool / 2);
        this.hp = this.maxHp;
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        this.bossLevel = bossLevel;
        this.archetype = Archetype.forLevel(bossLevel);
        this.random = Objects.requireNonNull(random, "random");
        this.x = spawnWorldX + 200;
        this.targetX = spawnWorldX - 150;
        this.y = 100;
        this.actionCooldown = 70 + this.random.nextInt(25);
        this.minorCooldown = 38 + this.random.nextInt(15);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public boolean isDashing() { return phase == Phase.DASH; }
    public String getPersonalityName() { return archetype.personality; }
    public String getMoveName() { return moveName; }
    public int getCombatStage() {
        double ratio = hp / (double) maxHp;
        if (ratio <= 0.30) return 3;
        if (ratio <= 0.65) return 2;
        return 1;
    }
    public String getStageName() { return archetype.stages[getCombatStage() - 1]; }
    public String getEncounterStatus() {
        return "P" + getCombatStage() + " " + getStageName() + "  //  " + moveName;
    }

    Set<String> movesSeenForTesting() { return Set.copyOf(movesSeen); }

    public void activate() {
        active = true;
        setMove("MATERIALIZING", 120);
    }

    public void update(ObstacleManager obstacleManager, int groundY, double px, double py,
                       List<Projectile> enemyBullets) {
        if (!active || hp <= 0) return;
        playerX = px;
        playerY = py;
        lastGroundY = groundY;

        if (phase == Phase.ENTER) {
            x = Math.max(targetX, x - (5 + bossLevel * 0.35));
            y = clampY(y + (playerY - y - height / 2.0) * 0.025, groundY);
            if (x <= targetX) {
                phase = Phase.IDLE;
                actionCooldown = 55;
                setMove("LOCK ACQUIRED", 55);
            }
            return;
        }

        combatTick++;
        if (moveLabelTicks > 0) {
            moveLabelTicks--;
        } else {
            moveName = archetype.idleMove;
        }

        int stage = getCombatStage();
        if (stage != lastStage) {
            lastStage = stage;
            x = targetX;
            phase = Phase.IDLE;
            flashing = false;
            actionCooldown = 24;
            radialBurst(enemyBullets, 8 + stage * 3, 3.2 + stage * 0.45, stage == 3);
            markMove("PHASE_SHIFT_" + stage, "PHASE SHIFT // " + getStageName(), 90);
        }

        switch (archetype) {
            case MEMORY_LEAK -> updateMemoryLeak(obstacleManager, groundY, enemyBullets, stage);
            case ARCHITECT -> updateArchitect(obstacleManager, groundY, enemyBullets, stage);
            case KERNEL_PANIC -> updateKernelPanic(obstacleManager, groundY, enemyBullets, stage);
            case SINGULARITY -> updateSingularity(obstacleManager, groundY, enemyBullets, stage);
            case GENERIC -> updateGeneric(obstacleManager, groundY, enemyBullets, stage);
        }
    }

    private void updateMemoryLeak(ObstacleManager om, int groundY,
                                  List<Projectile> bullets, int stage) {
        double hover = playerY - height / 2.0 + Math.sin(combatTick * 0.045) * (35 + stage * 12);
        y = clampY(y + (hover - y) * 0.045, groundY);

        if (--minorCooldown <= 0) {
            aimedVolley(bullets, 2 + stage, 0.32 + stage * 0.08,
                    4.6 + stage * 0.45, false);
            if (moveLabelTicks == 0) setMove("REFERENCE DRIP", 28);
            minorCooldown = 72 - stage * 10;
        }

        if (--actionCooldown > 0) return;
        patternIndex++;
        if ((patternIndex & 1) == 1) {
            aimedVolley(bullets, 5 + stage * 2, 1.05 + stage * 0.12,
                    4.8 + stage * 0.4, stage == 3);
            om.spawnEnemy((int) x + width + 30, groundY - 55, EntityType.LEAK);
            if (stage >= 2) om.spawnEnemy((int) x + width + 80, groundY - 85, EntityType.TECHDEBT);
            markMove("HEAP_SPRAY", "HEAP SPRAY // RETAINED OBJECTS", 72);
        } else {
            radialBurst(bullets, 10 + stage * 4, 3.2 + stage * 0.55, stage == 3);
            if (stage == 3) aimedVolley(bullets, 7, 0.9, 6.2, true);
            markMove("GC_STORM", "GC STORM // NO SAFE ROOT", 78);
        }
        actionCooldown = 175 - stage * 24;
    }

    private void updateArchitect(ObstacleManager om, int groundY,
                                 List<Projectile> bullets, int stage) {
        int node = (combatTick / 110) % 3;
        double nodeY = 45 + node * Math.max(45, (groundY - height - 70) / 2.0);
        y = clampY(y + (nodeY - y) * 0.04, groundY);

        if (--minorCooldown <= 0) {
            aimedVolley(bullets, 1 + stage, 0.26, 5.8 + stage * 0.45, stage == 3);
            minorCooldown = 78 - stage * 9;
        }

        if (--actionCooldown > 0) return;
        patternIndex++;
        if ((patternIndex & 1) == 1) {
            blueprintGrid(bullets, groundY, stage);
            if (stage == 3) om.spawnEnemy((int) x + width + 35, groundY - 105, EntityType.SENTINEL);
            markMove("BLUEPRINT_GRID", "BLUEPRINT GRID // FIND THE GAP", 82);
        } else {
            int walls = stage == 3 ? 2 : 1;
            for (int i = 0; i < walls; i++) {
                om.spawnEnemy((int) x + width + 45 + i * 115, 0, EntityType.FIREWALL);
            }
            if (stage >= 2) om.spawnEnemy((int) x + width + 10, groundY - 125, EntityType.SENTINEL);
            markMove("RUNTIME_WALL", "RUNTIME WALL // DESIGN IS LAW", 86);
        }
        actionCooldown = 170 - stage * 19;
    }

    private void updateKernelPanic(ObstacleManager om, int groundY,
                                   List<Projectile> bullets, int stage) {
        if (updateDashState(groundY, stage)) return;

        double jitter = Math.sin(combatTick * 0.23) * (45 + stage * 12);
        y = clampY(y + (playerY - height / 2.0 + jitter - y) * 0.075, groundY);

        if (--minorCooldown <= 0) {
            aimedVolley(bullets, stage >= 3 ? 2 : 1, 0.16, 7.2 + stage * 0.45, true);
            minorCooldown = 68 - stage * 10;
        }

        if (--actionCooldown > 0) return;
        patternIndex++;
        if ((patternIndex & 1) == 1) {
            if (stage == 3) panicLanes(bullets, groundY, stage);
            startDash("CORE DUMP", "CORE_DUMP", stage);
        } else {
            panicLanes(bullets, groundY, stage);
            om.spawnEnemy((int) x + width + 25, groundY - 60, EntityType.INTERRUPT);
            if (stage >= 2) {
                om.spawnFromLeft(groundY, (int) targetX - 800, EntityType.INTERRUPT);
            }
            markMove("PANIC_LANES", "PANIC LANES // IRQ STORM", 76);
        }
        actionCooldown = 138 - stage * 16;
    }

    private void updateSingularity(ObstacleManager om, int groundY,
                                   List<Projectile> bullets, int stage) {
        if (updateDashState(groundY, stage)) return;

        x = targetX + Math.sin(combatTick * 0.027) * (18 + stage * 5);
        double orbitY = (groundY - height) / 2.0 + Math.sin(combatTick * 0.041) * (65 + stage * 18);
        y = clampY(y + (orbitY - y) * 0.06, groundY);

        if (--minorCooldown <= 0) {
            spiralShot(bullets, 4.4 + stage * 0.5, stage == 3 && patternIndex % 2 == 0);
            minorCooldown = 45 - stage * 6;
        }

        if (--actionCooldown > 0) return;
        patternIndex++;
        switch (patternIndex % 4) {
            case 1 -> {
                radialBurst(bullets, 11 + stage * 3, 3.5 + stage * 0.45, stage == 3);
                markMove("MEMORY_ECHO", "MEMORY ECHO // ABSORB", 70);
            }
            case 2 -> {
                blueprintGrid(bullets, groundY, Math.min(3, stage + 1));
                markMove("BLUEPRINT_ECHO", "BLUEPRINT ECHO // REWRITE", 74);
            }
            case 3 -> {
                if (stage == 3) aimedVolley(bullets, 5, 0.7, 6.4, true);
                startDash("KERNEL ECHO", "KERNEL_ECHO", stage);
            }
            default -> {
                aimedVolley(bullets, 7 + stage * 2, 1.25, 5.5 + stage * 0.5, stage >= 2);
                radialBurst(bullets, 8 + stage * 2, 2.8 + stage * 0.5, false);
                if (stage >= 2) om.spawnEnemy((int) x + width + 30, groundY - 100, EntityType.MIRROR);
                markMove("EVENT_HORIZON", "EVENT HORIZON // NO RETURN", 86);
            }
        }
        actionCooldown = 145 - stage * 17;
    }

    private void updateGeneric(ObstacleManager om, int groundY,
                               List<Projectile> bullets, int stage) {
        y = clampY(y + (playerY - y - height / 2.0) * 0.04, groundY);
        if (--minorCooldown <= 0) {
            aimedVolley(bullets, 2 + stage, 0.4, 5.0 + stage, false);
            minorCooldown = 65;
        }
        if (--actionCooldown <= 0) {
            radialBurst(bullets, 10 + stage * 2, 4.0 + stage * 0.5, stage == 3);
            om.spawnEnemy((int) x + width, groundY - 60, EntityType.BUG);
            markMove("SYSTEM_BURST", "SYSTEM BURST", 65);
            actionCooldown = 150;
        }
    }

    private void startDash(String displayName, String moveId, int stage) {
        dashMoveName = displayName;
        dashDir = playerX > x ? 1 : -1;
        warningTicks = Math.max(16, 34 - stage * 5);
        phase = Phase.DASH_WARN;
        markMove(moveId, displayName + " // TELEGRAPH", warningTicks + 20);
    }

    private boolean updateDashState(int groundY, int stage) {
        if (phase == Phase.DASH_WARN) {
            flashing = (warningTicks / 4) % 2 == 0;
            y = clampY(y + (playerY - y - height / 2.0) * 0.10, groundY);
            if (--warningTicks <= 0) {
                phase = Phase.DASH;
                dashTicks = 15 + stage * 2;
                setMove(dashMoveName + " // EXECUTE", dashTicks + 18);
            }
            return true;
        }
        if (phase == Phase.DASH) {
            x += dashDir * (20 + stage * 3);
            if (--dashTicks <= 0 || x < targetX - 760 || x > targetX + 140) {
                phase = Phase.RECOVER;
            }
            return true;
        }
        if (phase == Phase.RECOVER) {
            double distance = targetX - x;
            x += Math.copySign(Math.min(Math.abs(distance), 12 + stage * 2), distance);
            if (Math.abs(targetX - x) < 1) {
                x = targetX;
                phase = Phase.IDLE;
                flashing = false;
                actionCooldown = 48;
                setMove("REBOOTING", 32);
            }
            return true;
        }
        return false;
    }

    private void aimedVolley(List<Projectile> bullets, int count, double spread,
                             double speed, boolean critical) {
        double cx = x + width / 2.0;
        double cy = y + height / 2.0;
        double base = Math.atan2(playerY + 15 - cy, playerX - cx);
        for (int i = 0; i < count; i++) {
            double offset = count == 1 ? 0 : (i / (double) (count - 1) - 0.5) * spread;
            double angle = base + offset;
            ProjectileType type = critical && (i % 2 == 0) ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
            bullets.add(new Projectile(cx, cy,
                    Math.cos(angle) * speed, Math.sin(angle) * speed, type));
        }
    }

    private void radialBurst(List<Projectile> bullets, int count, double speed, boolean critical) {
        double cx = x + width / 2.0;
        double cy = y + height / 2.0;
        double rotation = combatTick * 0.035;
        for (int i = 0; i < count; i++) {
            double angle = rotation + Math.PI * 2 * i / count;
            ProjectileType type = critical && (i % 3 == 0) ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
            bullets.add(new Projectile(cx, cy,
                    Math.cos(angle) * speed, Math.sin(angle) * speed, type));
        }
    }

    private void blueprintGrid(List<Projectile> bullets, int groundY, int stage) {
        int lanes = 3 + stage;
        double speed = 6.0 + stage * 0.65;
        for (int i = 0; i < lanes; i++) {
            double laneY = 34 + (groundY - 68) * (i + 1) / (double) (lanes + 1);
            ProjectileType type = stage == 3 && (i & 1) == 0
                    ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
            bullets.add(new Projectile(x + 10 + (i % 2) * 28, laneY, -speed, 0, type));
        }
    }

    private void panicLanes(List<Projectile> bullets, int groundY, int stage) {
        int lanes = 4 + stage;
        for (int i = 0; i < lanes; i++) {
            double laneY = 28 + (groundY - 56) * i / (double) Math.max(1, lanes - 1);
            double vy = ((i & 1) == 0 ? 0.45 : -0.45) * stage;
            ProjectileType type = (i + patternIndex) % 3 == 0
                    ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
            bullets.add(new Projectile(x + width / 2.0, laneY,
                    -(7.0 + stage * 0.8), vy, type));
        }
    }

    private void spiralShot(List<Projectile> bullets, double speed, boolean critical) {
        double angle = combatTick * 0.23 + patternIndex * 0.8;
        bullets.add(new Projectile(x + width / 2.0, y + height / 2.0,
                Math.cos(angle) * speed, Math.sin(angle) * speed,
                critical ? ProjectileType.CRITICAL : ProjectileType.ENEMY));
    }

    private double clampY(double value, int groundY) {
        return Math.max(20, Math.min(value, groundY - height));
    }

    private void markMove(String id, String displayName, int ticks) {
        movesSeen.add(id);
        setMove(displayName, ticks);
    }

    private void setMove(String displayName, int ticks) {
        moveName = displayName;
        moveLabelTicks = Math.max(1, ticks);
    }

    public void takeDamage(int amount) {
        if (!active || amount <= 0) return;
        hp = Math.max(0, hp - amount);
    }

    public void draw(Graphics2D g) {
        if (!active) return;
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();
        Composite oldComposite = g.getComposite();
        Font oldFont = g.getFont();

        g.setColor(new Color(0, 0, 0, 95));
        g.fillRoundRect((int) x + 7, (int) y + 9, width, height, 24, 24);
        switch (archetype) {
            case MEMORY_LEAK -> drawMemoryLeak(g);
            case ARCHITECT -> drawArchitect(g);
            case KERNEL_PANIC -> drawKernelPanic(g);
            case SINGULARITY -> drawSingularity(g);
            case GENERIC -> drawGeneric(g);
        }

        drawIdentity(g);
        drawTelegraph(g);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
        g.setComposite(oldComposite);
        g.setFont(oldFont);
    }

    private void drawMemoryLeak(Graphics2D g) {
        Color body = stageColor(archetype.primary, archetype.accent);
        g.setPaint(new GradientPaint((float) x, (float) y, body.brighter(),
                (float) x, (float) y + height, new Color(45, 65, 40)));
        g.fillRoundRect((int) x, (int) y, width, height, 42, 42);
        g.setColor(archetype.accent);
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect((int) x, (int) y, width, height, 42, 42);

        int bubbles = 3 + getCombatStage();
        for (int i = 0; i < bubbles; i++) {
            double angle = combatTick * 0.035 + Math.PI * 2 * i / bubbles;
            int radius = 70 + i * 5;
            int bx = (int) (x + width / 2.0 + Math.cos(angle) * radius);
            int by = (int) (y + height / 2.0 + Math.sin(angle) * radius * 0.55);
            g.setColor(new Color(archetype.accent.getRed(), archetype.accent.getGreen(),
                    archetype.accent.getBlue(), 125));
            g.fillOval(bx - 7, by - 7, 14, 14);
        }
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 23));
        g.setColor(Color.BLACK);
        g.drawString("HEAP", (int) x + 28, (int) y + 77);
        g.setColor(Color.WHITE);
        g.drawString("HEAP", (int) x + 26, (int) y + 75);
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
        g.setColor(archetype.accent);
        g.drawString("retained=" + (100 - hp * 100 / maxHp) + "%", (int) x + 16, (int) y + 106);
        for (int i = 0; i < getCombatStage(); i++) {
            int leakY = (int) y + height + (combatTick * 2 + i * 17) % 35;
            g.fillRect((int) x + 24 + i * 31, leakY, 7, 7);
        }
    }

    private void drawArchitect(Graphics2D g) {
        Color body = stageColor(new Color(10, 46, 58), new Color(62, 82, 92));
        g.setColor(body);
        g.fillRect((int) x, (int) y, width, height);
        g.setColor(archetype.primary);
        g.setStroke(new BasicStroke(2));
        for (int gx = 15; gx < width; gx += 20) g.drawLine((int) x + gx, (int) y, (int) x + gx, (int) y + height);
        for (int gy = 15; gy < height; gy += 20) g.drawLine((int) x, (int) y + gy, (int) x + width, (int) y + gy);
        g.setColor(archetype.accent);
        g.setStroke(new BasicStroke(4));
        g.drawRect((int) x, (int) y, width, height);
        g.drawLine((int) x + 15, (int) y + 116, (int) x + 60, (int) y + 35);
        g.drawLine((int) x + 60, (int) y + 35, (int) x + 106, (int) y + 116);
        g.drawLine((int) x + 15, (int) y + 116, (int) x + 106, (int) y + 116);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 17));
        g.setColor(Color.WHITE);
        g.drawString("ARCH", (int) x + 37, (int) y + 88);
        for (int i = 0; i < 4; i++) {
            g.fillOval((int) x + 12 + i * 31, (int) y + 124, 7, 7);
        }
    }

    private void drawKernelPanic(Graphics2D g) {
        int glitch = (int) Math.round(Math.sin(combatTick * 0.71) * (2 + getCombatStage() * 2));
        g.setColor(new Color(22, 18, 29));
        g.fillRect((int) x, (int) y, width, height);
        g.setColor(archetype.accent);
        g.fillRect((int) x + glitch, (int) y + 18, width - 12, 22);
        g.setColor(archetype.primary);
        g.fillRect((int) x - glitch, (int) y + 57, width + 8, 16);
        g.fillRect((int) x + glitch * 2, (int) y + 112, width - 18, 25);
        g.setColor(Color.WHITE);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 20));
        g.drawString("PANIC", (int) x + 28 + glitch, (int) y + 101);
        g.setStroke(new BasicStroke(3));
        g.setColor(flashing ? Color.WHITE : archetype.primary);
        g.drawRect((int) x, (int) y, width, height);
    }

    private void drawSingularity(Graphics2D g) {
        int cx = (int) x + width / 2;
        int cy = (int) y + height / 2;
        g.setColor(new Color(15, 12, 26));
        g.fillOval((int) x, (int) y + 14, width, width);
        for (int i = 0; i < 4 + getCombatStage(); i++) {
            int radius = 27 + i * 10;
            int start = (combatTick * (2 + i) + i * 47) % 360;
            g.setColor(i % 2 == 0 ? archetype.primary : archetype.accent);
            g.setStroke(new BasicStroke(i == 0 ? 4 : 2));
            g.drawArc(cx - radius, cy - radius, radius * 2, radius * 2, start, 170);
        }
        g.setPaint(new RadialGradientPaint(cx, cy, 27,
                new float[]{0f, 0.55f, 1f},
                new Color[]{Color.BLACK, new Color(70, 20, 95), archetype.primary}));
        g.fillOval(cx - 27, cy - 27, 54, 54);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 12));
        g.setColor(Color.WHITE);
        g.drawString("∞ MERGE", cx - 25, cy + 5);
    }

    private void drawGeneric(Graphics2D g) {
        Color body = stageColor(archetype.primary, archetype.accent);
        g.setPaint(new GradientPaint((float) x, (float) y, body.brighter(),
                (float) x, (float) y + height, body.darker()));
        g.fillRect((int) x, (int) y, width, height);
        g.setColor(archetype.accent);
        g.setStroke(new BasicStroke(3));
        g.drawRect((int) x, (int) y, width, height);
        g.setFont(new Font("SansSerif", Font.BOLD, 52));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(symbol, (int) x + (width - fm.stringWidth(symbol)) / 2, (int) y + 92);
    }

    private void drawIdentity(Graphics2D g) {
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
        g.setColor(Color.BLACK);
        g.drawString("P" + getCombatStage() + " // " + getStageName(), (int) x + 1, (int) y - 9);
        g.setColor(archetype.accent);
        g.drawString("P" + getCombatStage() + " // " + getStageName(), (int) x, (int) y - 10);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 9));
        g.setColor(new Color(255, 255, 255, 210));
        String shortMove = moveName.length() > 23 ? moveName.substring(0, 23) : moveName;
        g.drawString(shortMove, (int) x + 5, (int) y + height - 8);
    }

    private void drawTelegraph(Graphics2D g) {
        if (phase == Phase.DASH_WARN) {
            g.setColor(new Color(255, 40, 40, flashing ? 210 : 95));
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{10, 7}, 0));
            int startX = (int) (x + width / 2.0);
            int startY = (int) (y + height / 2.0);
            g.drawLine(startX, startY, (int) (startX + dashDir * 760), startY);
        }
        if (moveName.contains("GRID") || moveName.contains("LANES")) {
            g.setColor(new Color(archetype.primary.getRed(), archetype.primary.getGreen(),
                    archetype.primary.getBlue(), 55));
            g.setStroke(new BasicStroke(1));
            for (int i = 1; i <= 4; i++) {
                int lineY = i * lastGroundY / 5;
                g.drawLine((int) targetX - 800, lineY, (int) targetX + width, lineY);
            }
        }
        if (moveName.contains("STORM") || moveName.contains("HORIZON")) {
            g.setColor(new Color(archetype.accent.getRed(), archetype.accent.getGreen(),
                    archetype.accent.getBlue(), 75));
            int pulse = 35 + combatTick % 45;
            g.drawOval((int) x + width / 2 - pulse, (int) y + height / 2 - pulse,
                    pulse * 2, pulse * 2);
        }
    }

    private Color stageColor(Color calm, Color danger) {
        return switch (getCombatStage()) {
            case 1 -> calm;
            case 2 -> blend(calm, danger, 0.45);
            default -> danger;
        };
    }

    private static Color blend(Color a, Color b, double ratio) {
        double keep = 1.0 - ratio;
        return new Color(
                (int) (a.getRed() * keep + b.getRed() * ratio),
                (int) (a.getGreen() * keep + b.getGreen() * ratio),
                (int) (a.getBlue() * keep + b.getBlue() * ratio));
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }
}
