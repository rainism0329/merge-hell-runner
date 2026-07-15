package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.ProjectileSpec;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.boss.BossAction;
import com.bigphil.mergehell.boss.BossPhase;
import com.bigphil.mergehell.boss.BossSnapshot;
import com.bigphil.mergehell.boss.DependencyNode;
import com.bigphil.mergehell.boss.LegacyBossController;
import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.InputFrame;
import com.bigphil.mergehell.engine.ScheduledTickScheduler;
import com.bigphil.mergehell.engine.EntityLimits;
import com.bigphil.mergehell.mission.DirectorCommand;
import com.bigphil.mergehell.mission.DirectorInput;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.render.GameViewport;
import com.bigphil.mergehell.render.HudRenderer;
import com.bigphil.mergehell.render.UpgradeOverlayRenderer;
import com.bigphil.mergehell.render.ViewportTransform;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.world.DarkBiome;
import com.bigphil.mergehell.world.ProceduralDarkRoute;
import com.bigphil.mergehell.world.WorldScenery;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import com.intellij.openapi.Disposable;

public class GamePanel extends JPanel implements ActionListener, Disposable {

    private final GameLoop gameLoop;
    private final Player player;
    private final ObstacleManager enemyManager;
    private Boss boss;
    private LegacyBossController legacyBoss;
    private LevelManager levelManager;

    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private List<Platform> platforms = new ArrayList<>();
    private List<LevelManager.Coin> coins = new ArrayList<>();
    private List<WorldScenery> darkScenery = new ArrayList<>();

    private final LinkedList<String> logs = new LinkedList<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    private GameState state = GameState.MENU;
    private GameState prePauseState = GameState.RUNNING;

    private final CollisionSystem.Context ctx = new CollisionSystem.Context();
    private final CollisionSystem collision;
    private final GameRenderer renderer = new GameRenderer();
    private final HudRenderer hudRenderer = new HudRenderer();
    private final UpgradeOverlayRenderer upgradeRenderer = new UpgradeOverlayRenderer();
    private final BufferedImage logicalBuffer = new BufferedImage(
            GameViewport.LOGICAL_WIDTH, GameViewport.LOGICAL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    private GameSession session;

    private int level = 0;
    private int shakeTimer = 0;
    private int flashTimer = 0;
    private double difficulty = 1.0;
    private boolean isNewHighScore = false;
    private double cameraX = 0;
    private int bossWarningTimer = 0;
    private int bossDeathTimer = 0;
    private int missionCompleteTimer = 0;
    private int extraLifeScore = 5000;
    private int hitstop = 0;
    private int transitionTimer = 0;
    private double recoveryX = 100;
    private double recoveryY = 480;
    private boolean legacyBossDying;
    private BossPhase lastLegacyPhase;
    private String loopErrorMessage = "";
    private int rejectedProjectiles;
    private WeaponId selectedStartingWeapon = WeaponId.COMMIT_CANNON;
    private boolean labPowerEnabled;
    private boolean runUnranked;
    private ProceduralDarkRoute darkRoute;
    private DarkBiome darkBiome = DarkBiome.REPOSITORY_CITY;
    private int biomeBannerTicks;
    private double legacyBossArenaLeft;
    private boolean legacyMeleeConnected;
    private int legacyLaserFlashTicks;
    private double legacyLaserY;
    private int legacyLaserHeight;
    private int legacyImmuneTextCooldown;

    private boolean keyLeft, keyRight, keyShoot, pauseKeyHeld;

    static final int TERMINAL_HEIGHT = 120;
    private static final double CAMERA_LEAD = 0.3;
    private static final int MISSION_COMPLETE_TICKS = 240;
    private static final int MISSION_COMPLETE_FADE_TICKS = 45;

    public GamePanel() {
        setPreferredSize(new Dimension(960, 600));
        setBackground(GameColors.BG);
        setFocusable(true);

        // Tool windows can hand focus back to the editor at any time. Clicking the game
        // always restores controls, and clearing keys prevents a stuck movement key.
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
            @Override public void mouseReleased(MouseEvent e) {
                synchronized (GamePanel.this) { selectUpgradeAt(e.getX(), e.getY()); }
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { clearHeldKeys(); }
        });

        int groundY = 600 - TERMINAL_HEIGHT;
        player = new Player(100, groundY);
        enemyManager = new ObstacleManager();
        session = new GameSession(System.nanoTime());
        player.setRunBuild(session.runBuild());
        collision = new CollisionSystem(this::handleCombatEvent,
                () -> !LevelManager.isLegacyMission(level),
                () -> session.runBuild().buildStats().comboGraceTicks());

        setupKeyBindings();
        addLog("System initialized. Kernel loaded.");
        gameLoop = new GameLoop(new ScheduledTickScheduler(),
                () -> actionPerformed(null), this::handleLoopError);
        gameLoop.start();
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
            if (isShowing()) gameLoop.resume();
            else gameLoop.pause();
        });
    }

    private void setupKeyBindings() {
        InputMap im = getInputMap(WHEN_FOCUSED);
        ActionMap am = getActionMap();

        registerKey(im, am, "LEFT", KeyEvent.VK_LEFT, true, () -> keyLeft = true);
        registerKey(im, am, "LEFT_R", KeyEvent.VK_LEFT, false, () -> keyLeft = false);
        registerKey(im, am, "RIGHT", KeyEvent.VK_RIGHT, true, () -> keyRight = true);
        registerKey(im, am, "RIGHT_R", KeyEvent.VK_RIGHT, false, () -> keyRight = false);

        registerKey(im, am, "JUMP", KeyEvent.VK_SPACE, true, this::handleJumpOrStart);
        registerKey(im, am, "START", KeyEvent.VK_ENTER, true, this::handleJumpOrStart);

        registerKey(im, am, "SHOOT", KeyEvent.VK_C, true, () -> keyShoot = true);
        registerKey(im, am, "SHOOT_R", KeyEvent.VK_C, false, () -> keyShoot = false);

        registerKey(im, am, "MELEE", KeyEvent.VK_X, true, () -> player.melee());

        registerKey(im, am, "BOMB", KeyEvent.VK_B, true, () -> {
            if (player.useBomb()) {
                flashTimer = 20; shakeTimer = 25;
                for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
                    if (!en.isDead() && en.getType().isHostile()) {
                        en.setDead(true);
                        int points = en.getType().pointValue;
                        ctx.score += points;
                        handleCombatEvent(new CombatEvent.EnemyKilled(
                                en.getType(), points, en.getX(), en.getY()));
                    }
                }
                enemyManager.getEnemyBullets().clear();
                if (boss != null && boss.isActive())
                    boss.takeDamage(300);
                if (legacyBoss != null && state == GameState.BOSS_FIGHT)
                    damageLegacyBossWithBomb();
                addLog("EMERGENCY PROTOCOL: Screen cleared!");
                if (session.state() == GameState.UPGRADE_SELECTION) {
                    state = GameState.UPGRADE_SELECTION;
                    clearHeldKeys();
                }
            }
        });

        registerKey(im, am, "WEAPON", KeyEvent.VK_Z, true, this::handleWeaponKey);

        registerKey(im, am, "UPGRADE_1", KeyEvent.VK_1, true, () -> chooseUpgrade(0));
        registerKey(im, am, "UPGRADE_2", KeyEvent.VK_2, true, () -> chooseUpgrade(1));
        registerKey(im, am, "UPGRADE_3", KeyEvent.VK_3, true, () -> chooseUpgrade(2));
        registerKey(im, am, "UPGRADE_REROLL", KeyEvent.VK_R, true, this::rerollUpgrades);

        registerKey(im, am, "LAB_TOGGLE", KeyEvent.VK_F12, true, this::toggleLabMode);
        registerKey(im, am, "LAB_TOGGLE_ALT", KeyEvent.VK_T, true, this::toggleLabMode);
        registerKey(im, am, "LAB_SUPPLY", KeyEvent.VK_F7, true, this::labSupplyDrop);
        registerKey(im, am, "LAB_SUPPLY_ALT", KeyEvent.VK_H, true, this::labSupplyDrop);
        registerKey(im, am, "LAB_UPGRADE", KeyEvent.VK_F8, true, this::labGrantUpgrade);
        registerKey(im, am, "LAB_UPGRADE_ALT", KeyEvent.VK_U, true, this::labGrantUpgrade);
        registerKey(im, am, "LAB_NEXT", KeyEvent.VK_F9, true, this::labAdvanceSegment);
        registerKey(im, am, "LAB_NEXT_ALT", KeyEvent.VK_J, true, this::labAdvanceSegment);
        registerKey(im, am, "LAB_CLEAR", KeyEvent.VK_F10, true, this::labClearWave);
        registerKey(im, am, "LAB_CLEAR_ALT", KeyEvent.VK_K, true, this::labClearWave);
        registerKey(im, am, "LAB_BOSS", KeyEvent.VK_F11, true, this::labAdvanceToBoss);
        registerKey(im, am, "LAB_BOSS_ALT", KeyEvent.VK_L, true, this::labAdvanceToBoss);
        registerKey(im, am, "NEW_RANKED_RUN", KeyEvent.VK_N, true, this::restartRankedRun);

        registerKey(im, am, "DASH", KeyEvent.VK_SHIFT, true, () -> {
            if (keyRight) player.dash(1);
            else if (keyLeft) player.dash(-1);
            else player.dash();
        });

        registerKey(im, am, "PAUSE_P", KeyEvent.VK_P, true, this::togglePauseOnce);
        registerKey(im, am, "PAUSE_P_R", KeyEvent.VK_P, false, () -> pauseKeyHeld = false);
        registerKey(im, am, "PAUSE_ESC", KeyEvent.VK_ESCAPE, true, this::togglePauseOnce);
        registerKey(im, am, "PAUSE_ESC_R", KeyEvent.VK_ESCAPE, false, () -> pauseKeyHeld = false);
    }

    private void registerKey(InputMap im, ActionMap am, String name, int keyCode, boolean pressed, Runnable action) {
        im.put(KeyStroke.getKeyStroke(keyCode, 0, !pressed), name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                synchronized (GamePanel.this) { action.run(); }
            }
        });
    }

    private void togglePause() {
        if (state == GameState.PAUSED) { state = prePauseState; addLog("System resumed."); }
        else if (state == GameState.RUNNING || state == GameState.BOSS_WARNING || state == GameState.BOSS_FIGHT) {
            prePauseState = state; state = GameState.PAUSED; addLog("System paused by user.");
        }
    }

    private void togglePauseOnce() {
        if (pauseKeyHeld) return;
        pauseKeyHeld = true;
        togglePause();
    }

    private void handleJumpOrStart() {
        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            startGame();
        } else if (state == GameState.RUNNING || state == GameState.BOSS_WARNING || state == GameState.BOSS_FIGHT) {
            player.requestJump();
        }
    }

    private void clearHeldKeys() {
        keyLeft = false;
        keyRight = false;
        keyShoot = false;
        pauseKeyHeld = false;
    }

    private void handleWeaponKey() {
        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            selectedStartingWeapon = selectedStartingWeapon == WeaponId.COMMIT_CANNON
                    ? WeaponId.FORCE_PUSH : WeaponId.COMMIT_CANNON;
            addLog("Starting weapon: " + selectedStartingWeapon.name().replace('_', ' '));
            repaint();
        } else {
            player.cycleWeapon();
        }
    }

    private void toggleLabMode() {
        labPowerEnabled = !labPowerEnabled;
        player.setDebugMode(labPowerEnabled);
        if (labPowerEnabled) {
            markRunUnranked();
            addLog("LAB MODE ON // GOD + INFINITE AMMO/BOMBS");
        } else {
            addLog("LAB powers disabled. This run remains unranked.");
        }
        repaint();
    }

    private void restartRankedRun() {
        labPowerEnabled = false;
        runUnranked = false;
        player.setDebugMode(false);
        clearHeldKeys();
        startGame();
        addLog("NEW RANKED RUN // LAB disabled.");
    }

    private void markRunUnranked() {
        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) return;
        if (!runUnranked) MergeHellStateService.getInstance().clearActiveRun();
        runUnranked = true;
    }

    private boolean labActionAvailable() {
        return labPowerEnabled && state != GameState.UPGRADE_SELECTION
                && (state == GameState.RUNNING || state == GameState.BOSS_WARNING
                || state == GameState.BOSS_FIGHT);
    }

    private void labSupplyDrop() {
        if (!labActionAvailable()) return;
        markRunUnranked();
        player.heal(999);
        player.setShieldTimer(600);
        for (int i = 0; i < 5; i++) player.addBomb();
        addLog("LAB // HP, shield and bombs restored.");
    }

    private void labGrantUpgrade() {
        if (!labActionAvailable() || session.progressionComplete()) return;
        markRunUnranked();
        int needed = Math.max(1, session.buildProgress().nextThreshold()
                - session.buildProgress().currentXp());
        session.awardBuildXp(needed);
        if (session.state() == GameState.UPGRADE_SELECTION) {
            state = GameState.UPGRADE_SELECTION;
            clearHeldKeys();
        }
        addLog("LAB // Upgrade draft granted.");
    }

    private void labAdvanceSegment() {
        if (!labActionAvailable()) return;
        if (legacyBoss != null || boss != null) {
            addLog("LAB // Boss encounter already active.");
            return;
        }
        markRunUnranked();
        enemyManager.clearHostiles();
        enemyManager.getEnemyBullets().clear();
        projectiles.clear();
        if (LevelManager.isLegacyMission(level)) {
            double targetX = levelManager.advanceToNextEncounterForTesting(player.getX());
            relocatePlayerForLab(targetX);
            state = GameState.RUNNING;
            session.setGameplayState(GameState.RUNNING);
            addLog("LAB // Advanced to next route encounter.");
        } else {
            session.advanceMissionSegmentForTesting();
            updateDarkBiome();
            addLog("LAB // Advanced to next mission segment.");
        }
    }

    private void labClearWave() {
        if (!labActionAvailable()) return;
        markRunUnranked();
        int cleared = 0;
        for (ObstacleManager.Enemy enemy : enemyManager.getEnemies()) {
            if (enemy.isDead() || !enemy.getType().isHostile()) continue;
            enemy.setDead(true);
            int points = enemy.getType().pointValue;
            ctx.score += points;
            handleCombatEvent(new CombatEvent.EnemyKilled(
                    enemy.getType(), points, enemy.getX(), enemy.getY()));
            cleared++;
        }
        enemyManager.getEnemyBullets().clear();
        if (session.state() == GameState.UPGRADE_SELECTION) {
            state = GameState.UPGRADE_SELECTION;
            clearHeldKeys();
        }
        addLog("LAB // Cleared " + cleared + " hostiles.");
    }

    private void labAdvanceToBoss() {
        if (!labActionAvailable()) return;
        if (legacyBoss != null || boss != null) {
            addLog("LAB // Boss encounter already active.");
            return;
        }
        markRunUnranked();
        enemyManager.clearHostiles();
        enemyManager.getEnemyBullets().clear();
        projectiles.clear();
        boss = null;
        state = GameState.RUNNING;
        session.setGameplayState(GameState.RUNNING);
        if (LevelManager.isLegacyMission(level)) {
            levelManager.advanceToBossGateForTesting();
            relocatePlayerForLab(levelManager.getBossGateX());
        } else {
            session.advanceToBossGateForTesting();
            updateDarkBiome();
        }
        addLog("LAB // Boss Gate armed. Stand by...");
    }

    private void relocatePlayerForLab(double worldX) {
        double levelWidth = levelManager.getCameraMaxX();
        player.setX(worldX);
        cameraX = Math.max(0, Math.min(worldX - GameViewport.LOGICAL_WIDTH * CAMERA_LEAD,
                levelWidth - GameViewport.LOGICAL_WIDTH));
        recoveryX = worldX;
        recoveryY = player.getY();
    }

    private void chooseUpgrade(int index) {
        if (state != GameState.UPGRADE_SELECTION) return;
        String title = session.upgradeChoices().get(index).title();
        session.chooseUpgrade(index);
        state = session.state();
        player.setRunBuild(session.runBuild());
        addLog("Installed upgrade: " + title);
        floatingTexts.add(new FloatingText(player.getX(), player.getY() - 65,
                title + " INSTALLED", GameColors.SUDO_YELLOW));
    }

    private void rerollUpgrades() {
        if (state != GameState.UPGRADE_SELECTION) return;
        try {
            session.rerollUpgrades();
            addLog("Upgrade candidates rerolled.");
        } catch (IllegalStateException ignored) {
            addLog("Reroll already consumed.");
        }
    }

    private void selectUpgradeAt(int physicalX, int physicalY) {
        if (state != GameState.UPGRADE_SELECTION) return;
        ViewportTransform transform = GameViewport.fit(getWidth(), getHeight());
        if (!transform.containsPhysical(physicalX, physicalY)) return;
        int logicalX = transform.logicalX(physicalX);
        int logicalY = transform.logicalY(physicalY);
        for (int i = 0; i < 3; i++) {
            if (upgradeRenderer.cardBounds(i).contains(logicalX, logicalY)) {
                chooseUpgrade(i);
                return;
            }
        }
    }

    public void addLog(String msg) {
        logs.addFirst("[" + timeFmt.format(new Date()) + "] " + msg);
        // The terminal has room for five complete, readable event lines. Keeping more
        // only rendered the oldest messages below the panel boundary.
        if (logs.size() > 5) logs.removeLast();
    }

    private void startGame() {
        logs.clear();
        ctx.score = 0; ctx.combo = 0; ctx.comboTimer = 0;
        ctx.shakeTimer = 0; ctx.flashTimer = 0; ctx.newKills = 0;
        level = 0; shakeTimer = 0; flashTimer = 0;
        difficulty = 1.0; cameraX = 0; isNewHighScore = false;
        bossWarningTimer = 0; bossDeathTimer = 0; missionCompleteTimer = 0; transitionTimer = 0;
        extraLifeScore = 5000;
        long runSeed = random.nextLong();
        session = new GameSession(runSeed, selectedStartingWeapon);
        runUnranked = labPowerEnabled;
        MergeHellStateService.getInstance().clearActiveRun();
        recoveryX = 100;
        recoveryY = GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT;
        levelManager = new LevelManager(0);
        platforms = new ArrayList<>();
        coins = new ArrayList<>();
        darkScenery = new ArrayList<>();
        darkRoute = new ProceduralDarkRoute(runSeed ^ 0x4D4552474548454CL, 360);
        darkBiome = DarkBiome.REPOSITORY_CITY;
        biomeBannerTicks = 180;
        darkRoute.extendTo(3_000, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT,
                darkBiome, platforms, coins, darkScenery);
        boss = null;
        legacyBoss = null;
        legacyBossDying = false;
        legacyMeleeConnected = false;
        legacyLaserFlashTicks = 0;
        lastLegacyPhase = null;
        resetGame();
        state = GameState.RUNNING;
        addLog("Starting new session...");
    }

    private void advanceLevel() {
        // Level clear bonus
        int timeBonus = (int) (difficulty * 500);
        int clearBonus = 2000 + level * 1000;
        ctx.score += timeBonus + clearBonus;
        floatingTexts.add(new FloatingText(GameViewport.LOGICAL_WIDTH / 2.0,
                GameViewport.LOGICAL_HEIGHT / 2.0,
                "BONUS +" + (timeBonus + clearBonus), Color.YELLOW));

        difficulty = 1.0 + level * 2.0;
        cameraX = 0;
        extraLifeScore += 10000;
        levelManager = new LevelManager(level);
        platforms = levelManager.getPlatforms();
        coins = levelManager.getCoins();
        darkScenery = new ArrayList<>();
        darkRoute = null;
        biomeBannerTicks = 0;
        boss = null;
        legacyBoss = null;
        legacyBossDying = false;
        legacyMeleeConnected = false;
        legacyLaserFlashTicks = 0;
        lastLegacyPhase = null;
        bossWarningTimer = 0; bossDeathTimer = 0; missionCompleteTimer = 0;
        resetGame();
        state = GameState.RUNNING;
        addLog("Level " + (level + 1) + " starting...");
    }

    private void resetGame() {
        player.reset(100, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT);
        player.setRunBuild(session.runBuild());
        player.setDebugMode(labPowerEnabled);
        enemyManager.reset();
        projectiles.clear(); particles.clear(); floatingTexts.clear();
    }

    @Override
    public synchronized void actionPerformed(ActionEvent e) {
        if (state == GameState.PAUSED) { repaint(); return; }

        if (state == GameState.ERROR) { repaint(); return; }

        if (state == GameState.UPGRADE_SELECTION) {
            clearHeldKeys();
            repaint();
            return;
        }

        if (state == GameState.MISSION_COMPLETE) {
            missionCompleteTimer--;
            transitionTimer = missionCompleteTransitionAlpha(missionCompleteTimer);
            if (missionCompleteTimer <= 0) {
                if (level < 4) { level++; advanceLevel(); transitionTimer = 255; }
                else { state = GameState.VICTORY; saveScore(); }
            }
            repaint(); return;
        }

        // Screen wipe-in on new level
        if (transitionTimer > 0) {
            transitionTimer = Math.max(0, transitionTimer - 12);
        }

        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            repaint(); return;
        }

        if (state == GameState.BOSS_WARNING && boss != null && bossWarningTimer > 0
                && --bossWarningTimer == 0) {
            state = GameState.BOSS_FIGHT;
            boss.activate();
            shakeTimer = 30;
            flashTimer = 20;
            hitstop = 10;
            addLog("ALERT: " + boss.getName() + " engaged!");
        }

        // Hitstop freeze
        if (hitstop > 0) { hitstop--; repaint(); return; }

        session.setGameplayState(state);
        session.tick(new InputFrame(keyLeft, keyRight, false, keyShoot,
                player.isDashing(), player.isMeleeActive()));
        player.setSudoTimer(session.overclockActiveTicks());

        int panelW = GameViewport.LOGICAL_WIDTH, panelH = GameViewport.LOGICAL_HEIGHT;
        int groundY = panelH - TERMINAL_HEIGHT;
        double levelWidth = levelManager.getCameraMaxX();
        if (!LevelManager.isLegacyMission(level)) {
            updateDarkBiome();
            ensureDarkRoute(player.getX(), groundY);
            if (biomeBannerTicks > 0) biomeBannerTicks--;
        }

        // ── Player update ───────────────────────────────
        player.update(keyLeft, keyRight, false, keyShoot, groundY, levelWidth, projectiles, platforms);
        if (player.getHp() <= 0) {
            if (player.loseLife()) {
                player.heal(100);
                player.setX(recoveryX);
                player.setY(recoveryY);
                player.setInvincibleTimer(90);
                enemyManager.clearHostiles();
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
        boolean legacyMission = LevelManager.isLegacyMission(level);
        LevelManager.BattleZone bz = legacyMission ? levelManager.getBattleAt(player.getX()) : null;
        if (legacyMission && bz != null && !levelManager.isInBattle()) {
            levelManager.enterBattle(bz);
            addLog("Enemy ambush! Clear the area.");
        }

        // Camera: follow player, lock during battle, smooth unlock
        double targetCam = player.getX() - panelW * CAMERA_LEAD;
        targetCam = Math.max(0, Math.min(targetCam, levelWidth - panelW));
        if (levelManager.isInBattle()) {
            double lockX = levelManager.getCameraLockX();
            targetCam = Math.min(targetCam, lockX);
        }
        if (legacyBoss != null && state == GameState.BOSS_FIGHT) targetCam = legacyBossArenaLeft;
        // Smooth camera lerp
        cameraX += (targetCam - cameraX) * 0.15;

        // Player bounds: stay within visible area
        if (player.getX() > cameraX + panelW - player.getBounds().width) {
            player.setX(cameraX + panelW - player.getBounds().width);
        }
        if (player.getX() < cameraX) {
            player.setX(cameraX);
        }

        // ── Battle zone waves ───────────────────────────
        int aliveEnemies = (int) enemyManager.getEnemies().stream()
                .filter(en -> !en.isDead() && en.getType().isHostile()).count();

        if (!legacyMission) {
            double pressure = (1.0 - player.getHp() / (double) player.getMaxHp()) * 0.6
                    + Math.min(1.0, aliveEnemies / 30.0) * 0.4;
            applyDirectorCommands(session.directMission(new DirectorInput(
                    session.worldTick(), player.getX(), pressure, aliveEnemies)), panelW, groundY);
        }

        if (legacyMission && levelManager.needsWaveSpawn(aliveEnemies)) {
            LevelManager.WaveDef wave = levelManager.popWave();
            for (int i = 0; i < wave.count; i++) {
                if (wave.fromDir == 0 || wave.fromDir == 2)
                    spawnFromRight(panelW, groundY, wave.type);
                if (wave.fromDir == 1 || wave.fromDir == 2)
                    enemyManager.spawnFromLeft(groundY, (int) cameraX, wave.type);
            }
            levelManager.startNextWaveTimer();
        }

        // ── Triggers + random spawns ────────────────────
        if (legacyMission && !levelManager.isInBattle()) {
            List<LevelManager.SpawnTrigger> triggers = levelManager.getPendingTriggers(player.getX());
            for (LevelManager.SpawnTrigger t : triggers) {
                for (int i = 0; i < t.count; i++) {
                    if (t.fromLeft == 0 || t.fromLeft == 2)
                        spawnFromRight(panelW, groundY, t.type);
                    if (t.fromLeft == 1 || t.fromLeft == 2)
                        enemyManager.spawnFromLeft(groundY, (int) cameraX, t.type);
                }
            }
        }

        // Extra life check
        if (ctx.score >= extraLifeScore) {
            player.addBomb();
            floatingTexts.add(new FloatingText(player.getX(), player.getY() - 50,
                    "MILESTONE +1 BOMB!", Color.ORANGE));
            addLog("Score milestone reached!");
            extraLifeScore += 10000;
        }

        // Enter the boss warning before ambient spawn checks so a LAB jump (or a fast
        // player) cannot bring a random enemy into the boss arena on the same tick.
        if (legacyMission && state == GameState.RUNNING && levelManager.shouldSpawnBoss(player.getX())) {
            state = GameState.BOSS_WARNING;
            addLog("WARNING: Boss arena detected!");
            boss = new Boss(levelManager.bossName, levelManager.bossHp,
                    levelManager.bossSymbol, cameraX + panelW, level);
            bossWarningTimer = 120;
        }

        // Mid-boss spawn
        // Battle zones are authored encounters: letting ambient spawns leak into them
        // made their clear condition feel arbitrary and could starve the next wave.
        boolean freeRoam = legacyMission && state == GameState.RUNNING && !levelManager.isInBattle();
        if (freeRoam && random.nextInt(1500) < 2 + difficulty)
            enemyManager.spawnEnemy(panelW + (int) cameraX + 100, groundY - 60, EntityType.TECHDEBT);

        difficulty += 0.0005;
        if (freeRoam && random.nextInt(100) < 1.5 + difficulty * 0.4)
            enemyManager.spawnRandom(panelW, groundY, difficulty, (int) cameraX, level);

        // ── Boss ────────────────────────────────────────
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
                    levelManager.onBossDefeated();
                    missionCompleteTimer = MISSION_COMPLETE_TICKS;
                    state = GameState.MISSION_COMPLETE;
                }
            }
        }

        if (state == GameState.BOSS_FIGHT && legacyBoss != null) {
            updateLegacyBoss(panelW, groundY);
        }

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
        fireDrones();
        // Collect coins
        for (LevelManager.Coin c : coins) {
            if (!c.collected && player.getBounds().intersects(
                    new Rectangle((int) c.x - 10, (int) c.y - 10, 20, 20))) {
                c.collected = true;
                ctx.score += 500;
                session.awardBuildXp(3);
                floatingTexts.add(new FloatingText(c.x, c.y, "CACHE +500 // +3 XP", Color.YELLOW));
            }
        }

        enemyManager.update((int) cameraX, (int) cameraX + panelW);

        collision.process(ctx, projectiles, enemyManager, boss, player, state,
                panelW, panelH, cameraX, particles, floatingTexts, this::addLog);
        if (legacyBoss != null && state == GameState.BOSS_FIGHT) processLegacyBossHits();
        if (session.state() == GameState.UPGRADE_SELECTION) {
            if (state != GameState.UPGRADE_SELECTION) addLog("BUILD XP FULL — select one upgrade.");
            state = GameState.UPGRADE_SELECTION;
            clearHeldKeys();
        }
        ctx.newKills = 0;

        if (ctx.shakeTimer > shakeTimer) shakeTimer = ctx.shakeTimer;
        if (ctx.flashTimer > flashTimer) flashTimer = ctx.flashTimer;
        if (ctx.hitstop > hitstop) hitstop = ctx.hitstop;
        if (ctx.killLog != null) { addLog(ctx.killLog); ctx.killLog = null; }
        ctx.shakeTimer = 0; ctx.flashTimer = 0; ctx.hitstop = 0;

        if (ctx.comboTimer > 0) {
            ctx.comboTimer--;
        } else if (ctx.combo > 0) {
            ctx.combo = 0;
            addLog("Combo window expired.");
        }

        particles.removeIf(p -> p.getLife() <= 0);
        floatingTexts.removeIf(t -> !t.update());
        for (Particle p : particles) p.update();

        enforceEntityLimits();

        repaint();
    }

    private final Random random = new Random();

    static int missionCompleteTransitionAlpha(int ticksRemaining) {
        if (ticksRemaining > MISSION_COMPLETE_FADE_TICKS) return 0;
        int fadeElapsed = MISSION_COMPLETE_FADE_TICKS - Math.max(0, ticksRemaining);
        return Math.min(255, fadeElapsed * 255 / MISSION_COMPLETE_FADE_TICKS);
    }

    private void applyDirectorCommands(List<DirectorCommand> commands, int panelWidth, int groundY) {
        for (DirectorCommand command : commands) {
            if (command instanceof DirectorCommand.Spawn spawn) {
                if (spawn.side() > 0) spawnFromRight(panelWidth, groundY, spawn.type());
                else enemyManager.spawnFromLeft(groundY, (int) cameraX, spawn.type());
            } else if (command instanceof DirectorCommand.BeginArena arena) {
                addLog("ARENA: " + arena.objective());
                floatingTexts.add(new FloatingText(cameraX + panelWidth / 2.0, 110,
                        arena.objective(), GameColors.SUDO_YELLOW));
            } else if (command instanceof DirectorCommand.BeginRecovery recovery) {
                recoveryX = player.getX();
                recoveryY = player.getY();
                enemyManager.clearHostiles();
                player.heal(35);
                if (session.runBuild().tryEvolve()) addLog("EVOLUTION UNLOCKED!");
                addLog("Recovery checkpoint secured (" + recovery.durationTicks() / 60 + "s).");
                saveActiveRun();
            } else if (command instanceof DirectorCommand.SpawnBoss) {
                boss = null;
                enemyManager.clearHostiles();
                legacyBoss = new LegacyBossController(random, 2_400);
                legacyBossArenaLeft = cameraX;
                legacyBoss.setOrigin(cameraX + panelWidth - 200, groundY - 290);
                legacyBossDying = false;
                legacyMeleeConnected = false;
                legacyLaserFlashTicks = 0;
                lastLegacyPhase = BossPhase.DEPENDENCIES;
                state = GameState.BOSS_FIGHT;
                session.setGameplayState(GameState.BOSS_FIGHT);
                session.markBossSpawned();
                shakeTimer = 30;
                flashTimer = 20;
                addLog("ALERT: dependency graph materialized.");
                floatingTexts.add(new FloatingText(cameraX + panelWidth / 2.0, 190,
                        "LEGACY DEPENDENCY // BREAK 3 LINKS", GameColors.DANGER_RED));
            }
        }
    }

    private void updateLegacyBoss(int panelWidth, int groundY) {
        if (legacyLaserFlashTicks > 0) legacyLaserFlashTicks--;
        if (legacyImmuneTextCooldown > 0) legacyImmuneTextCooldown--;
        if (legacyBoss.phase() == BossPhase.DEFEATED) {
            if (!legacyBossDying) {
                legacyBossDying = true;
                bossDeathTimer = 90;
                shakeTimer = 40;
                addLog("Legacy dependency graph fully detached.");
            }
            if (--bossDeathTimer <= 0) {
                levelManager.onBossDefeated();
                session.markComplete();
                if (!runUnranked) MergeHellStateService.getInstance().completeMission(0, 250);
                MergeHellStateService.getInstance().clearActiveRun();
                missionCompleteTimer = MISSION_COMPLETE_TICKS;
                state = GameState.MISSION_COMPLETE;
            }
            return;
        }

        Rectangle core = legacyBoss.coreBounds();
        if (player.getBounds().intersects(core)) {
            player.takeDamage(24);
            player.setX(Math.max(cameraX, core.x - player.getBounds().width - 12));
            shakeTimer = Math.max(shakeTimer, 14);
        }

        for (BossAction action : legacyBoss.tick(player.getX(), player.getY())) {
            if (action instanceof BossAction.Spawn spawn) {
                for (int i = 0; i < spawn.count(); i++) {
                    if ((i & 1) == 0) spawnFromRight(panelWidth, groundY, spawn.type());
                    else enemyManager.spawnFromLeft(groundY, (int) cameraX, spawn.type());
                }
            } else if (action instanceof BossAction.Laser laser) {
                legacyLaserY = laser.laneY();
                legacyLaserHeight = laser.height();
                legacyLaserFlashTicks = 10;
                double playerCenter = player.getBounds().getCenterY();
                if (Math.abs(playerCenter - laser.laneY()) <= laser.height() / 2.0) {
                    player.takeDamage(laser.damage());
                }
                flashTimer = Math.max(flashTimer, 8);
                shakeTimer = Math.max(shakeTimer, 22);
            } else if (action instanceof BossAction.Volley volley) {
                spawnLegacyVolley(volley);
                shakeTimer = Math.max(shakeTimer, 10);
            } else if (action instanceof BossAction.Shockwave shockwave) {
                spawnLegacyShockwave(shockwave, groundY);
                shakeTimer = Math.max(shakeTimer, 16);
            }
        }
    }

    private void spawnLegacyVolley(BossAction.Volley volley) {
        Rectangle core = legacyBoss.coreBounds();
        double startX = core.x + 10;
        double startY = core.y + 92;
        double dx = volley.targetX() - startX;
        double dy = volley.targetY() + 15 - startY;
        double base = Math.atan2(dy, dx);
        List<Projectile> bullets = enemyManager.getEnemyBullets();
        for (int i = 0; i < volley.count(); i++) {
            double spread = (i - (volley.count() - 1) / 2.0) * 0.105;
            double angle = base + spread;
            ProjectileType type = (i == volley.count() / 2 && legacyBoss.phase() == BossPhase.ENRAGED)
                    ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
            bullets.add(new Projectile(startX, startY,
                    Math.cos(angle) * volley.speed(), Math.sin(angle) * volley.speed(), type));
        }
    }

    private void spawnLegacyShockwave(BossAction.Shockwave shockwave, int groundY) {
        Rectangle core = legacyBoss.coreBounds();
        List<Projectile> bullets = enemyManager.getEnemyBullets();
        for (int i = 0; i < shockwave.count(); i++) {
            bullets.add(new Projectile(core.x + 12 + i * 58, groundY - 17,
                    -shockwave.speed(), 0, ProjectileType.CRITICAL));
        }
    }

    private void damageLegacyBossWithBomb() {
        BossSnapshot snapshot = legacyBoss.snapshot();
        if (snapshot.phase() == BossPhase.DEPENDENCIES) {
            for (int id : legacyBoss.damageAllNodes(120)) {
                BossSnapshot.NodeView view = snapshot.nodes().stream()
                        .filter(node -> node.id() == id).findFirst().orElseThrow();
                rewardLegacyNodeDestroyed(view);
            }
            floatingTexts.add(new FloatingText(legacyBoss.coreBounds().getCenterX(),
                    legacyBoss.coreBounds().getCenterY(), "BOMB // LINKS -120", Color.ORANGE));
        } else {
            int damage = legacyBoss.damageCore(360);
            floatingTexts.add(new FloatingText(legacyBoss.coreBounds().getCenterX(),
                    legacyBoss.coreBounds().getCenterY(), "BOMB // CORE -" + damage, Color.ORANGE));
        }
        hitstop = Math.max(hitstop, 7);
        shakeTimer = Math.max(shakeTimer, 40);
        flashTimer = Math.max(flashTimer, 20);
        addLog("BOMB confirmed on Legacy Dependency.");
    }

    private void processLegacyBossHits() {
        BossSnapshot before = legacyBoss.snapshot();
        for (Projectile projectile : projectiles) {
            if (projectile.isDead()) continue;
            boolean hitNode = false;
            for (BossSnapshot.NodeView node : before.nodes()) {
                DependencyNode current = legacyBoss.nodes().stream()
                        .filter(candidate -> candidate.id() == node.id()).findFirst().orElseThrow();
                if (current.alive() && projectile.getBounds().intersects(legacyBoss.nodeBounds(node.id()))) {
                    legacyBoss.damageNode(node.id(), projectile.getDamage());
                    projectile.setDead(true);
                    hitNode = true;
                    if (!current.alive()) rewardLegacyNodeDestroyed(node);
                    break;
                }
            }
            if (!hitNode && !projectile.isDead()
                    && projectile.getBounds().intersects(legacyBoss.coreBounds())) {
                int accepted = legacyBoss.damageCore(projectile.getDamage());
                projectile.setDead(true);
                if (accepted == 0 && legacyImmuneTextCooldown == 0) {
                    legacyImmuneTextCooldown = 30;
                    floatingTexts.add(new FloatingText(projectile.getX(), projectile.getY(),
                            "LOCKED BY DEPENDENCIES", GameColors.DANGER_RED));
                }
            }
        }
        processLegacyMelee(before);
        if (legacyBoss.phase() != lastLegacyPhase) {
            lastLegacyPhase = legacyBoss.phase();
            handleCombatEvent(new CombatEvent.BossPhaseChanged(lastLegacyPhase));
            addLog("Boss phase: " + lastLegacyPhase);
            flashTimer = 15;
            shakeTimer = 20;
        }
    }

    private void processLegacyMelee(BossSnapshot snapshot) {
        Rectangle melee = player.getMeleeBounds();
        if (melee == null) {
            legacyMeleeConnected = false;
            return;
        }
        if (legacyMeleeConnected) return;
        for (BossSnapshot.NodeView view : snapshot.nodes()) {
            DependencyNode current = legacyBoss.nodes().stream()
                    .filter(node -> node.id() == view.id()).findFirst().orElseThrow();
            if (!current.alive() || !melee.intersects(legacyBoss.nodeBounds(view.id()))) continue;
            legacyBoss.damageNode(view.id(), 70);
            legacyMeleeConnected = true;
            if (!current.alive()) rewardLegacyNodeDestroyed(view);
            shakeTimer = Math.max(shakeTimer, 12);
            return;
        }
        if (melee.intersects(legacyBoss.coreBounds())) {
            int accepted = legacyBoss.damageCore(70);
            legacyMeleeConnected = true;
            if (accepted == 0 && legacyImmuneTextCooldown == 0) {
                legacyImmuneTextCooldown = 30;
                floatingTexts.add(new FloatingText(melee.getCenterX(), melee.getCenterY(),
                        "CORE SHIELDED", GameColors.DANGER_RED));
            }
            shakeTimer = Math.max(shakeTimer, 12);
        }
    }

    private void rewardLegacyNodeDestroyed(BossSnapshot.NodeView node) {
        ctx.score += 600;
        handleCombatEvent(new CombatEvent.BossNodeDestroyed(
                node.id(), 120, node.x(), node.y()));
        floatingTexts.add(new FloatingText(node.x(), node.y(),
                "LINK SEVERED +600 // +120 XP", GameColors.SUDO_YELLOW));
        hitstop = Math.max(hitstop, 6);
        shakeTimer = Math.max(shakeTimer, 24);
        flashTimer = Math.max(flashTimer, 12);
    }

    private void saveScore() {
        if (runUnranked) {
            isNewHighScore = false;
            addLog("LAB // Unranked score was not saved.");
            return;
        }
        isNewHighScore = ScoreStore.isHighScore(ctx.score);
        ScoreStore.save(ctx.score);
    }

    private void handleCombatEvent(CombatEvent event) {
        boolean wasOverclocked = session.isOverclocked();
        session.accept(event);
        if (!wasOverclocked && session.isOverclocked()) {
            addLog("SUDO OVERCLOCK ONLINE — 8 seconds of boosted firepower!");
            floatingTexts.add(new FloatingText(player.getX(), player.getY() - 90,
                    "SUDO // OVERCLOCK", GameColors.SUDO_YELLOW));
            flashTimer = Math.max(flashTimer, 10);
        }
    }

    private void saveActiveRun() {
        if (runUnranked) return;
        MergeHellState.ActiveRun run = new MergeHellState.ActiveRun();
        run.mission = level;
        run.checkpointX = recoveryX;
        run.checkpointY = recoveryY;
        run.lives = player.getLives();
        run.weapon = session.runBuild().weapon();
        run.upgradeRanks.putAll(session.runBuild().ranks());
        run.worldTick = session.worldTick();
        MergeHellStateService.getInstance().saveActiveRun(run);
    }

    private void enforceEntityLimits() {
        while (projectiles.size() > EntityLimits.MAX_PROJECTILES) {
            projectiles.remove(projectiles.size() - 1);
            rejectedProjectiles++;
        }
        List<Projectile> enemyBullets = enemyManager.getEnemyBullets();
        while (enemyBullets.size() > EntityLimits.MAX_ENEMY_PROJECTILES) {
            enemyBullets.remove(enemyBullets.size() - 1);
        }
        while (particles.size() > EntityLimits.MAX_PARTICLES) particles.remove(0);
        while (floatingTexts.size() > EntityLimits.MAX_FLOATING_TEXTS) floatingTexts.remove(0);
        int hostiles = (int) enemyManager.getEnemies().stream()
                .filter(enemy -> !enemy.isDead() && enemy.getType().isHostile()).count();
        session.updateMetrics(hostiles, projectiles.size(), enemyBullets.size(),
                particles.size(), floatingTexts.size(), rejectedProjectiles);
    }

    private void fireDrones() {
        int droneLevel = session.runBuild().buildStats().droneLevel();
        if (droneLevel <= 0 || session.worldTick() % 90 != 0
                || projectiles.size() >= EntityLimits.MAX_PROJECTILES) return;
        ObstacleManager.Enemy target = enemyManager.getEnemies().stream()
                .filter(enemy -> !enemy.isDead() && enemy.getType().isHostile())
                .min(Comparator.comparingDouble(enemy -> {
                    double dx = enemy.getX() - player.getX();
                    double dy = enemy.getY() - player.getY();
                    return dx * dx + dy * dy;
                })).orElse(null);
        if (target == null) return;
        double originX = player.getX() + 15;
        double originY = player.getY() - 12;
        double dx = target.getX() + target.getWidth() / 2.0 - originX;
        double dy = target.getY() + 20 - originY;
        double length = Math.max(1, Math.hypot(dx, dy));
        int damage = Math.max(1, (int) Math.ceil(session.runBuild().effectiveStats().damage() * 0.40));
        for (int i = 0; i < droneLevel && projectiles.size() < EntityLimits.MAX_PROJECTILES; i++) {
            double angleOffset = (i - (droneLevel - 1) / 2.0) * 0.035;
            double cos = Math.cos(angleOffset), sin = Math.sin(angleOffset);
            double vx = dx / length * 12;
            double vy = dy / length * 12;
            projectiles.add(new Projectile(originX, originY, new ProjectileSpec(
                    WeaponId.COMMIT_CANNON, damage,
                    vx * cos - vy * sin, vx * sin + vy * cos,
                    false, 0, 1, 0)));
        }
        floatingTexts.add(new FloatingText(originX, originY, "AI PAIR // FIRE", GameColors.SHIELD_CYAN));
    }

    private void ensureDarkRoute(double playerX, int groundY) {
        if (darkRoute != null) darkRoute.extendTo(playerX + 2_600, groundY,
                darkBiome, platforms, coins, darkScenery);
    }

    private void updateDarkBiome() {
        DarkBiome next = DarkBiome.forProgress(session.missionProgress());
        if (next == darkBiome) return;
        darkBiome = next;
        biomeBannerTicks = 180;
        addLog("ENTERING // " + darkBiome.displayName);
        floatingTexts.add(new FloatingText(player.getX(), player.getY() - 68,
                darkBiome.displayName, darkBiome.accent));
    }

    private void spawnFromRight(int panelWidth, int groundY, EntityType type) {
        int y = groundY - 30 - random.nextInt(120);
        if (type == EntityType.TECHDEBT) y = groundY - 80;
        else if (type == EntityType.FIREWALL) y = 0;
        else if (type == EntityType.SENTINEL) y = groundY - 170 - random.nextInt(130);
        else if (type == EntityType.MIRROR) y = groundY - 90 - random.nextInt(180);
        enemyManager.spawnEnemy(panelWidth + random.nextInt(200) + (int) cameraX, y, type);
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
    protected synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);
        int panelW = GameViewport.LOGICAL_WIDTH;
        int panelH = GameViewport.LOGICAL_HEIGHT;
        int groundY = panelH - TERMINAL_HEIGHT;
        int wave = levelManager != null ? levelManager.getCurrentWave() : 0;
        int total = levelManager != null ? levelManager.getTotalWaves() : 0;
        boolean inBattle = levelManager != null && levelManager.isInBattle();
        double runProgress = levelManager == null ? 0
                : LevelManager.isLegacyMission(level)
                    ? levelManager.getProgress(player.getX()) : session.missionProgress();

        Graphics2D logical = logicalBuffer.createGraphics();
        logical.setColor(GameColors.BG);
        logical.fillRect(0, 0, panelW, panelH);
        renderer.render(logical, panelW, panelH, groundY,
                state, player, boss, enemyManager,
                projectiles, enemyManager.getEnemyBullets(),
                particles, floatingTexts, platforms, coins,
                logs, ctx.score, ctx.combo, ctx.comboTimer, shakeTimer,
                flashTimer, level, difficulty, isNewHighScore,
                cameraX, inBattle, wave, total, transitionTimer, runProgress,
                darkBiome, darkScenery, biomeBannerTicks);
        if (legacyBoss != null && state == GameState.BOSS_FIGHT) {
            drawLegacyBoss(logical, panelW);
        }

        if ((state == GameState.RUNNING || state == GameState.BOSS_FIGHT) && levelManager != null) {
            logical.setColor(new Color(255, 255, 255, 25));
            logical.fillRect(0, groundY - 3, panelW, 3);
            logical.setColor(new Color(100, 200, 255, 100));
            logical.fillRect(0, groundY - 3, (int) (panelW * runProgress), 3);
        }

        boolean gameplayHudVisible = state == GameState.RUNNING || state == GameState.BOSS_WARNING
                || state == GameState.BOSS_FIGHT || state == GameState.LEVEL_CLEAR;
        if (gameplayHudVisible) {
            hudRenderer.render(logical, session, player, ctx.score, ctx.combo, ctx.comboTimer,
                    runUnranked, !LevelManager.isLegacyMission(level));
        }
        if (state == GameState.UPGRADE_SELECTION) upgradeRenderer.render(logical, session);
        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            drawStartingLoadout(logical);
        }
        if (state == GameState.ERROR) drawLoopError(logical, panelW, panelH);
        logical.dispose();

        Graphics2D physical = (Graphics2D) g.create();
        physical.setColor(GameColors.BG);
        physical.fillRect(0, 0, getWidth(), getHeight());
        physical.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        physical.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        ViewportTransform transform = GameViewport.fit(getWidth(), getHeight());
        physical.drawImage(logicalBuffer, transform.offsetX(), transform.offsetY(),
                transform.drawWidth(), transform.drawHeight(), null);
        physical.dispose();

        if (shakeTimer > 0) shakeTimer--;
        if (flashTimer > 0) flashTimer--;
    }

    LevelManager getLevelManager() { return levelManager; }
    void setLevelManager(LevelManager lm) { this.levelManager = lm; }
    GameSession getSession() { return session; }

    @Override
    public void addNotify() {
        super.addNotify();
        if (gameLoop != null) gameLoop.resume();
    }

    @Override
    public void removeNotify() {
        if (gameLoop != null) gameLoop.pause();
        clearHeldKeys();
        super.removeNotify();
    }

    @Override
    public void dispose() { gameLoop.dispose(); }

    private synchronized void handleLoopError(Throwable error) {
        String message = error.getMessage();
        loopErrorMessage = message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message.replaceAll("[\\r\\n]+", " ");
        if (loopErrorMessage.length() > 120) loopErrorMessage = loopErrorMessage.substring(0, 120);
        state = GameState.ERROR;
        repaint();
    }

    private void drawLoopError(Graphics2D original, int width, int height) {
        Graphics2D g = (Graphics2D) original.create();
        g.setColor(new Color(8, 10, 15, 245));
        g.fillRect(0, 0, width, height);
        g.setColor(GameColors.DANGER_RED);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 28));
        g.drawString("GAME LOOP PAUSED", 70, height / 2 - 30);
        g.setColor(Color.WHITE);
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        g.drawString(loopErrorMessage, 70, height / 2 + 10);
        g.drawString("Close and reopen the tool window to restart safely.", 70, height / 2 + 45);
        g.dispose();
    }

    private void drawStartingLoadout(Graphics2D original) {
        Graphics2D g = (Graphics2D) original.create();
        g.setColor(new Color(8, 12, 20, 225));
        g.fillRoundRect(230, 382, 500, 86, 16, 16);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 14));
        g.setColor(GameColors.SHIELD_CYAN);
        g.drawString("[ Z ] STARTING WEAPON", 254, 406);
        g.setColor(GameColors.SUDO_YELLOW);
        String detail = selectedStartingWeapon == WeaponId.COMMIT_CANNON
                ? "COMMIT CANNON // PRECISION + RICOCHET"
                : "FORCE PUSH // 5 PELLETS + KNOCKBACK";
        g.drawString(detail, 254, 431);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
        g.setColor(labPowerEnabled ? GameColors.SUDO_YELLOW : new Color(190, 205, 220));
        String labStatus = labPowerEnabled
                ? "LAB ARMED // NEXT RUN UNRANKED     [ T / F12 ] DISABLE"
                : "[ T / F12 ] LAB TEST MODE     [ N ] NEW RANKED RUN";
        g.drawString(labStatus, 254, 454);
        g.dispose();
    }

    private void drawLegacyBoss(Graphics2D original, int panelWidth) {
        Graphics2D g = (Graphics2D) original.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        BossSnapshot snapshot = legacyBoss.snapshot();
        Rectangle core = legacyBoss.coreBounds();
        int coreX = (int) (core.x - cameraX);
        int coreY = core.y;
        int pulse = 85 + (int) (Math.sin(System.currentTimeMillis() / 110.0) * 35);

        if (legacyLaserFlashTicks > 0) {
            int y = (int) legacyLaserY - legacyLaserHeight / 2;
            int alpha = Math.min(235, 80 + legacyLaserFlashTicks * 16);
            g.setColor(new Color(255, 32, 48, alpha));
            g.fillRect(0, y, panelWidth, legacyLaserHeight);
            g.setColor(new Color(255, 240, 220, alpha));
            g.fillRect(0, y + legacyLaserHeight / 2 - 3, panelWidth, 6);
        }

        if (snapshot.telegraph() != null) {
            int warningAlpha = Math.min(190, 65 + (legacyBoss.telegraphTicksRemaining() % 8) * 18);
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 14));
            if (snapshot.telegraph() instanceof BossAction.Laser laser) {
                int y = (int) laser.laneY() - laser.height() / 2;
                g.setColor(new Color(255, 55, 65, warningAlpha / 2));
                g.fillRect(0, y, panelWidth, laser.height());
                g.setColor(new Color(255, 90, 90, warningAlpha));
                g.drawLine(0, y, panelWidth, y);
                g.drawLine(0, y + laser.height(), panelWidth, y + laser.height());
                g.drawString("LASER LOCK // CHANGE LANE!", 32, Math.max(184, y - 9));
            } else if (snapshot.telegraph() instanceof BossAction.Volley volley) {
                int tx = (int) (volley.targetX() - cameraX) + 15;
                int ty = (int) volley.targetY() + 15;
                g.setColor(new Color(255, 90, 90, warningAlpha));
                g.setStroke(new BasicStroke(2f));
                for (int i = -2; i <= 2; i++) g.drawLine(coreX + 18, coreY + 92, tx, ty + i * 8);
                g.drawOval(tx - 22, ty - 22, 44, 44);
                g.drawString("PACKET BARRAGE // MOVE!", Math.max(24, tx - 100), Math.max(184, ty - 34));
            } else if (snapshot.telegraph() instanceof BossAction.Shockwave) {
                int ground = GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT;
                g.setColor(new Color(255, 110, 45, warningAlpha / 2));
                g.fillRect(0, ground - 42, panelWidth, 42);
                g.setColor(new Color(255, 155, 55, warningAlpha));
                for (int x = 0; x < panelWidth; x += 38) g.drawLine(x, ground, x + 19, ground - 24);
                g.drawString("GROUND PANIC // JUMP!", 36, ground - 52);
            }
        }

        // Energy tethers make the shield relationship immediately readable.
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (BossSnapshot.NodeView node : snapshot.nodes()) {
            if (!node.alive()) continue;
            int nx = (int) (node.x() - cameraX) + 27;
            int ny = (int) node.y() + 27;
            g.setColor(new Color(255, 205, 45, pulse));
            g.drawLine(nx, ny, coreX + 5, coreY + core.height / 2);
        }

        if (snapshot.phase() == BossPhase.ENRAGED) {
            g.setColor(new Color(255, 25, 45, 42));
            for (int i = 1; i <= 4; i++)
                g.drawRoundRect(coreX - i * 8, coreY - i * 6,
                        core.width + i * 16, core.height + i * 12, 32, 32);
        }

        g.setColor(new Color(4, 7, 12, 150));
        g.fillRoundRect(coreX - 12, coreY + 10, core.width + 20, core.height + 12, 32, 32);
        Color armor = snapshot.phase() == BossPhase.DEPENDENCIES
                ? new Color(48, 65, 82) : snapshot.phase() == BossPhase.ENRAGED
                    ? new Color(128, 27, 39) : new Color(92, 39, 58);
        g.setPaint(new GradientPaint(coreX, coreY, armor.brighter(),
                coreX + core.width, coreY + core.height, armor.darker()));
        g.fillRoundRect(coreX, coreY, core.width, core.height, 24, 24);

        g.setColor(new Color(8, 12, 18, 190));
        for (int y = coreY + 18; y < coreY + core.height - 20; y += 48)
            g.fillRoundRect(coreX + 14, y, core.width - 28, 31, 8, 8);
        g.setColor(snapshot.phase() == BossPhase.ENRAGED
                ? new Color(255, 35, 45) : new Color(255, 86, 105));
        g.fillRoundRect(coreX + 24, coreY + 72, core.width - 48, 18, 9, 9);
        g.setColor(Color.WHITE);
        g.fillOval(coreX + core.width / 2 - 5, coreY + 76, 10, 10);

        g.setColor(Color.WHITE);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 16));
        g.drawString("LEGACY", coreX + 43, coreY + 132);
        g.drawString("DEPENDENCY", coreX + 23, coreY + 154);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
        g.setColor(new Color(225, 235, 245));
        g.drawString(snapshot.phase() == BossPhase.DEPENDENCIES
                ? "CORE IMMUNE" : snapshot.phase() == BossPhase.ENRAGED
                    ? "PANIC MODE" : "CORE EXPOSED", coreX + 34, coreY + 184);

        if (snapshot.phase() == BossPhase.DEPENDENCIES) {
            g.setColor(new Color(80, 205, 255, 70));
            g.setStroke(new BasicStroke(4f));
            g.drawRoundRect(coreX - 8, coreY - 7, core.width + 16, core.height + 14, 32, 32);
            g.drawRoundRect(coreX - 15, coreY, core.width + 30, core.height, 36, 36);
        }

        for (BossSnapshot.NodeView node : snapshot.nodes()) {
            int x = (int) (node.x() - cameraX);
            int y = (int) node.y();
            if (node.alive()) {
                g.setColor(new Color(255, 205, 45, 45 + pulse / 2));
                g.fillOval(x - 10, y - 10, 74, 74);
            }
            g.setColor(node.alive() ? GameColors.SUDO_YELLOW : new Color(48, 52, 60));
            g.fillRoundRect(x, y, 54, 54, 14, 14);
            g.setColor(node.alive() ? new Color(35, 25, 4) : new Color(110, 115, 122));
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
            g.drawString(node.alive() ? "LINK " + (node.id() + 1) : "BROKEN", x + 6, y + 31);
            if (node.alive()) {
                g.setColor(new Color(20, 25, 32));
                g.fillRoundRect(x - 3, y - 10, 60, 6, 4, 4);
                g.setColor(GameColors.SUDO_YELLOW);
                g.fillRoundRect(x - 3, y - 10,
                        (int) (60 * node.hp() / (double) node.maxHp()), 6, 4, 4);
                g.setColor(Color.WHITE);
                g.drawString("SHOOT", x - 48, y + 31);
                g.drawLine(x - 12, y + 27, x - 2, y + 27);
            }
        }

        int barW = Math.min(540, panelWidth - 80);
        int barX = (panelWidth - barW) / 2;
        g.setColor(new Color(10, 12, 18, 220));
        g.fillRoundRect(barX, 130, barW, 34, 12, 12);
        g.setColor(snapshot.phase() == BossPhase.DEPENDENCIES
                ? new Color(70, 105, 132) : snapshot.phase() == BossPhase.ENRAGED
                    ? Color.RED : GameColors.DANGER_RED);
        g.fillRoundRect(barX + 4, 134,
                (int) ((barW - 8) * snapshot.coreHp() / (double) snapshot.maxCoreHp()), 26, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
        long aliveNodes = snapshot.nodes().stream().filter(BossSnapshot.NodeView::alive).count();
        String objective = snapshot.phase() == BossPhase.DEPENDENCIES
                ? "BREAK LINKS " + (3 - aliveNodes) + "/3  //  CORE IMMUNE"
                : snapshot.phase() == BossPhase.ENRAGED ? "CORE PANIC // FINISH IT"
                    : "CORE EXPOSED // FULL DAMAGE";
        g.drawString("LEGACY DEPENDENCY  //  " + objective, barX + 12, 153);
        g.dispose();
    }
}
