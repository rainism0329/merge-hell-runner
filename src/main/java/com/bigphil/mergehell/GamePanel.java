package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.DroneController;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.combat.WeaponCatalog;
import com.bigphil.mergehell.audio.AudioService;
import com.bigphil.mergehell.audio.AmbienceScene;
import com.bigphil.mergehell.audio.CombatFeedbackEvent;
import com.bigphil.mergehell.audio.CombatFeedbackEvent.Cue;
import com.bigphil.mergehell.boss.BossAction;
import com.bigphil.mergehell.boss.BossArrivalController;
import com.bigphil.mergehell.render.BossArrivalRenderer;
import com.bigphil.mergehell.world.TraversalEnvironment;
import com.bigphil.mergehell.world.BlueprintCitadelController;
import com.bigphil.mergehell.render.BlueprintHudRenderer;
import com.bigphil.mergehell.world.KernelCoreController;
import com.bigphil.mergehell.render.KernelHudRenderer;
import com.bigphil.mergehell.world.SingularityEdgeController;
import com.bigphil.mergehell.render.SingularityHudRenderer;
import com.bigphil.mergehell.boss.BossPhase;
import com.bigphil.mergehell.boss.BossSnapshot;
import com.bigphil.mergehell.boss.DependencyNode;
import com.bigphil.mergehell.boss.LegacyBossController;
import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.InputFrame;
import com.bigphil.mergehell.engine.InputCommandBuffer;
import com.bigphil.mergehell.engine.ScheduledTickScheduler;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.engine.EntityLimits;
import com.bigphil.mergehell.engine.ProjectileBuffer;
import com.bigphil.mergehell.engine.ProjectileBudget;
import com.bigphil.mergehell.mission.DirectorCommand;
import com.bigphil.mergehell.mission.DirectorInput;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.render.GameViewport;
import com.bigphil.mergehell.render.HudRenderer;
import com.bigphil.mergehell.render.DroneRenderer;
import com.bigphil.mergehell.render.UpgradeOverlayRenderer;
import com.bigphil.mergehell.render.ViewportTransform;
import com.bigphil.mergehell.render.FrameMailbox;
import com.bigphil.mergehell.render.SettingsOverlayRenderer;
import com.bigphil.mergehell.settings.SettingsEditor;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.persistence.CheckpointCodec;
import com.bigphil.mergehell.world.DarkBiome;
import com.bigphil.mergehell.world.ProceduralDarkRoute;
import com.bigphil.mergehell.world.WorldScenery;
import com.bigphil.mergehell.world.HeapDistrictController;
import com.bigphil.mergehell.boss.BossFeedbackController;
import com.bigphil.mergehell.render.BossFeedbackRenderer;
import com.bigphil.mergehell.render.HeapWorldRenderer;
import com.bigphil.mergehell.render.HeapHudRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import java.util.function.LongSupplier;
import com.intellij.openapi.Disposable;

public class GamePanel extends JPanel implements ActionListener, Disposable {

    private final GameLoop gameLoop;
    /** Guarded by this, shared with simulation, loop errors and shutdown. */
    private boolean disposed;
    private final InputCommandBuffer inputCommands = new InputCommandBuffer();
    private final AudioService audio = GraphicsEnvironment.isHeadless()
            ? new AudioService(() -> { throw new IllegalStateException("Headless audio disabled"); }, System::nanoTime)
            : new AudioService();
    private MergeHellState.Settings settings = MergeHellStateService.getInstance().getState().settings;
    private SettingsEditor settingsEditor;
    /** Guarded by audio: a focus event cannot be undone by a finishing simulation tick. */
    private boolean audioFocusSuspended;
    private boolean compactDisplay;
    private volatile boolean requestedCompactDisplay;
    private final SettingsOverlayRenderer settingsRenderer = new SettingsOverlayRenderer();
    private long visualWorldTick;
    private final String storageOwner = UUID.randomUUID().toString();
    private String runId = UUID.randomUUID().toString();
    private boolean ownsCheckpoint;
    private final Player player;
    private final ObstacleManager enemyManager;
    private Boss boss;
    private LegacyBossController legacyBoss;
    private LevelManager levelManager;

    private final ProjectileBuffer projectiles = new ProjectileBuffer(EntityLimits.MAX_PROJECTILES);
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private List<Platform> platforms = new ArrayList<>();
    private List<Platform> combatPlatforms = List.of();
    private TraversalEnvironment environment;
    private final java.util.ArrayDeque<TraversalEnvironment.BreakEvent> environmentEvents = new java.util.ArrayDeque<>();
    private List<LevelManager.Coin> coins = new ArrayList<>();
    private List<WorldScenery> darkScenery = new ArrayList<>();

    private final LinkedList<String> logs = new LinkedList<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT);

    private GameState state = GameState.MENU;
    private GameState prePauseState = GameState.RUNNING;

    private final CollisionSystem.Context ctx = new CollisionSystem.Context();
    private final CollisionSystem collision;
    private final GameRenderer renderer = new GameRenderer();
    private final HudRenderer hudRenderer = new HudRenderer();
    private final DroneController drones = new DroneController();
    private final DroneRenderer droneRenderer = new DroneRenderer();
    private HeapDistrictController heap;
    private BlueprintCitadelController blueprint;
    private boolean blueprintInteract;
    private final ArrayDeque<BlueprintCitadelController.Event> blueprintEvents = new ArrayDeque<>();
    private final BlueprintHudRenderer blueprintHudRenderer = new BlueprintHudRenderer();
    private KernelCoreController kernel;
    private boolean kernelInteract;
    private final ArrayDeque<KernelCoreController.Event> kernelEvents = new ArrayDeque<>();
    private final KernelHudRenderer kernelHudRenderer = new KernelHudRenderer();
    private SingularityEdgeController singularity;
    private boolean singularityInteract;
    private final ArrayDeque<SingularityEdgeController.Event> singularityEvents = new ArrayDeque<>();
    private final SingularityHudRenderer singularityHudRenderer = new SingularityHudRenderer();
    private HeapDistrictController.Choice heapChoice = HeapDistrictController.Choice.NONE;
    private boolean heapMeleeConnected;
    private double heapBossArenaLeft;
    private final HeapWorldRenderer heapRenderer = new HeapWorldRenderer();
    private final HeapHudRenderer heapHudRenderer = new HeapHudRenderer();
    private final BossFeedbackController bossFeedback = new BossFeedbackController();
    private final BossFeedbackRenderer bossFeedbackRenderer = new BossFeedbackRenderer();
    private final IdentityHashMap<ObstacleManager.Enemy, Long> droneTargetIds = new IdentityHashMap<>();
    private long nextDroneTargetId = 16;
    private final com.bigphil.mergehell.render.LegacyBossRenderer legacyBossRenderer = new com.bigphil.mergehell.render.LegacyBossRenderer();
    private final UpgradeOverlayRenderer upgradeRenderer = new UpgradeOverlayRenderer();
    private final FrameMailbox frames = new FrameMailbox();
    private GameSession session;

    private int level = 0;
    private int shakeTimer = 0;
    private int flashTimer = 0;
    private int powerFlashTimer;
    private double difficulty = 1.0;
    private boolean isNewHighScore = false;
    private double cameraX = 0;
    private int bossWarningTimer = 0;
    private final BossArrivalController bossArrival = new BossArrivalController();
    private final BossArrivalRenderer bossArrivalRenderer = new BossArrivalRenderer();
    private double bossArrivalArenaLeft;
    private int bossDeathTimer = 0;
    private int missionCompleteTimer = 0;
    private boolean missionSettled;
    private int missionClearBonus;
    private boolean missionFirstClearReward;
    private int extraLifeScore = 5000;
    private int hitstop = 0;
    private int transitionTimer = 0;
    private double recoveryX = 100;
    private double recoveryY = 480;
    private boolean legacyBossDying;
    private BossPhase lastLegacyPhase;
    private String loopErrorMessage = "";
    private int rejectedProjectiles;
    private long observedPlayerRejections, observedEnemyRejections, observedDroneRejections;
    private WeaponId selectedStartingWeapon = WeaponId.COMMIT_CANNON;
    private boolean labPowerEnabled;
    private boolean runUnranked;
    private int labOffNoticeTicks;
    private ProceduralDarkRoute darkRoute;
    private DarkBiome darkBiome = DarkBiome.REPOSITORY_CITY;
    private int biomeBannerTicks;
    private double legacyBossArenaLeft;
    private boolean legacyMeleeConnected;
    private int legacyLaserFlashTicks;
    private double legacyLaserY;
    private int legacyLaserHeight;
    private int legacyImmuneTextCooldown;

    private boolean keyLeft, keyRight, keyShoot;

    static final int TERMINAL_HEIGHT = 120;
    private static final double CAMERA_LEAD = 0.3;
    private static final int MISSION_COMPLETE_TICKS = 240;
    private static final int MISSION_COMPLETE_FADE_TICKS = 45;

    public GamePanel() {
        this(new ScheduledTickScheduler(), System::nanoTime);
    }

    GamePanel(TickScheduler scheduler, LongSupplier clock) {
        setPreferredSize(new Dimension(960, 600));
        setBackground(GameColors.BG);
        setFocusable(true);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                requestedCompactDisplay = GameViewport.fit(getWidth(), getHeight()).drawWidth() < 768;
            }
        });

        // Tool windows can hand focus back to the editor at any time. Clicking the game
        // always restores controls, and clearing keys prevents a stuck movement key.
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
            @Override public void mouseReleased(MouseEvent e) {
                ViewportTransform transform = GameViewport.fit(getWidth(), getHeight());
                if (!transform.containsPhysical(e.getX(), e.getY())) return;
                int logicalX = transform.logicalX(e.getX());
                int logicalY = transform.logicalY(e.getY());
                inputCommands.submit(() -> handleClick(logicalX, logicalY));
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { queueFocusLoss(); }
            @Override public void focusGained(FocusEvent e) {
                synchronized (audio) { audioFocusSuspended = false; }
            }
        });

        int groundY = 600 - TERMINAL_HEIGHT;
        player = new Player(100, groundY);
        enemyManager = new ObstacleManager();
        session = new GameSession(System.nanoTime());
        player.setRunBuild(session.runBuild());
        collision = new CollisionSystem(this::handleCombatEvent,
                () -> !LevelManager.isLegacyMission(level),
                () -> session.runBuild().buildStats().comboGraceTicks());
        collision.setWorldImpactHandler(this::hitWorldObject);

        setupKeyBindings();
        audio.setPaused(true);
        applySettings();
        renderer.updateVisuals(player, enemyManager.getEnemies(), 0);
        addLog("System initialized. Kernel loaded.");
        // Preserve the measured legacy tempo until every motion/timer is migrated to 60 Hz.
        gameLoop = new GameLoop(scheduler, () -> actionPerformed(null), this::handleLoopError,
                clock, GameLoop.LEGACY_STEP_NANOS);
        frames.publish(this::renderLogicalFrame);
        gameLoop.start();
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
            if (isShowing()) gameLoop.resume();
            else {
                gameLoop.pause();
                queueFocusLoss();
            }
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
        registerKey(im, am, "START", KeyEvent.VK_ENTER, true, this::handleEnter);

        registerKey(im, am, "SHOOT", KeyEvent.VK_C, true, () -> keyShoot = true);
        registerKey(im, am, "SHOOT_R", KeyEvent.VK_C, false, () -> keyShoot = false);

        registerKey(im, am, "MELEE", KeyEvent.VK_X, true, () -> {
            if (isActiveGameplay() && state != GameState.BOSS_WARNING) player.melee();
        });
        registerKey(im, am, "HEAP_PURGE", KeyEvent.VK_E, true,
                () -> queueHeapChoice(HeapDistrictController.Choice.PURGE));
        registerKey(im, am, "HEAP_SALVAGE", KeyEvent.VK_F, true,
                () -> queueHeapChoice(HeapDistrictController.Choice.SALVAGE));

        registerKey(im, am, "BOMB", KeyEvent.VK_B, true, () -> {
            if (isActiveGameplay() && state != GameState.BOSS_WARNING && player.useBomb()) {
                powerFlashTimer = 16; shakeTimer = 25;
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
                if (boss != null && boss.isActive()) {
                    int damage = boss.damage(300);
                    recordBossImpact(CombatEvent.BossPart.BODY, 0, damage, boss.getHp(), boss.getMaxHp(),
                            boss.getBounds().getCenterX(), boss.getBounds().getCenterY(),
                            CombatEvent.DamageKind.BOMB, false, null);
                }
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
        registerKey(im, am, "UPGRADE_REROLL", KeyEvent.VK_R, true, () -> {
            if (state == GameState.MENU) continueRun(); else rerollUpgrades();
        });

        registerKey(im, am, "LAB_TOGGLE", KeyEvent.VK_F12, true, this::toggleLabMode);
        registerKey(im, am, "LAB_TOGGLE_ALT", KeyEvent.VK_T, true, this::toggleLabMode);
        registerKey(im, am, "PRACTICE_START", KeyEvent.VK_G, true, () -> {
            if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY)
                startPractice(false);
        });
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
            if (!isActiveGameplay()) return;
            boolean wasDashing = player.isDashing();
            if (keyRight) player.dash(1);
            else if (keyLeft) player.dash(-1);
            else player.dash();
            if (!wasDashing && player.isDashing()) audio.emit(CombatFeedbackEvent.of(Cue.DASH));
        });

        registerKey(im, am, "PAUSE_P", KeyEvent.VK_P, true, this::togglePause);
        registerKey(im, am, "PAUSE_ESC", KeyEvent.VK_ESCAPE, true, this::togglePause);
        registerKey(im, am, "MENU_UP", KeyEvent.VK_UP, true, () -> { });
        registerKey(im, am, "MENU_DOWN", KeyEvent.VK_DOWN, true, () -> { });
        registerKey(im, am, "SETTINGS", KeyEvent.VK_O, true, this::openSettings);
        registerKey(im, am, "MUTE", KeyEvent.VK_M, true, this::toggleMute);
        registerKey(im, am, "MENU", KeyEvent.VK_Q, true, () -> {
            if (state != GameState.PAUSED && state != GameState.MISSION_COMPLETE && state != GameState.VICTORY) return;
            state = GameState.MENU;
            MergeHellStateService.getInstance().releaseRun(storageOwner);
            ownsCheckpoint = false;
            clearHeldKeys();
            addLog("Returned to menu. Continue restarts from the saved level entrance.");
        });
    }

    private void registerKey(InputMap im, ActionMap am, String name, int keyCode, boolean pressed, Runnable action) {
        im.put(KeyStroke.getKeyStroke(keyCode, 0, !pressed), name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (pressed) inputCommands.press(keyCode, () -> {
                    if (settingsEditor != null) { handleSettingsKey(keyCode); return; }
                    action.run();
                });
                else inputCommands.release(keyCode, action);
            }
        });
        if (pressed) {
            // Every one-shot key needs a release edge, including jump, dash and menus.
            // Explicit held-key release bindings below replace this default binding.
            String releaseName = name + "_RELEASE";
            im.put(KeyStroke.getKeyStroke(keyCode, 0, true), releaseName);
            am.put(releaseName, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    inputCommands.release(keyCode, () -> { });
                }
            });
        }
    }

    private void togglePause() {
        if (state == GameState.PAUSED) {
            state = prePauseState;
            syncAudio();
            gameLoop.resetClock();
            addLog("System resumed.");
        } else if (isActiveGameplay()) {
            prePauseState = state; state = GameState.PAUSED; addLog("System paused by user.");
            audio.setPaused(true);
            clearHeldKeys();
            gameLoop.resetClock();
        }
    }

    private void openSettings() {
        if (state != GameState.MENU && state != GameState.PAUSED) return;
        settingsEditor = new SettingsEditor(settings);
        clearHeldKeys();
    }

    private void handleSettingsKey(int keyCode) {
        SettingsEditor.Command command = switch (keyCode) {
            case KeyEvent.VK_UP -> SettingsEditor.Command.UP;
            case KeyEvent.VK_DOWN -> SettingsEditor.Command.DOWN;
            case KeyEvent.VK_LEFT -> SettingsEditor.Command.LEFT;
            case KeyEvent.VK_RIGHT -> SettingsEditor.Command.RIGHT;
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> SettingsEditor.Command.ACTIVATE;
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_O -> SettingsEditor.Command.BACK;
            default -> null;
        };
        if (command == null) return;
        var result = settingsEditor.handle(command);
        if (result == SettingsEditor.Result.VALUE_CHANGED) {
            settings = settingsEditor.settings();
            applySettings();
        } else if (result == SettingsEditor.Result.CLOSE_REQUESTED) {
            settingsEditor = null;
            clearHeldKeys();
        }
    }

    private void applySettings() {
        MergeHellStateService.getInstance().updateSettings(settings);
        settings = MergeHellStateService.getInstance().getState().settings;
        audio.setVolumePercent(settings.volumePercent);
        audio.setMuted(settings.muted);
        syncAudio();
        renderer.setAudioMuted(settings.muted);
        renderer.configureVisuals(settings.crt, settings.highContrast, settings.particlePercent);
        hudRenderer.setHighContrast(settings.highContrast);
    }

    private void toggleMute() {
        settings.muted = !settings.muted;
        applySettings();
        addLog(settings.muted ? "Audio muted. [M] to restore." : "Audio restored.");
    }

    private void syncAudio() {
        synchronized (audio) {
            boolean active = isActiveGameplay();
            audio.setBackground(active ? AmbienceScene.forMission(level,
                    state == GameState.BOSS_FIGHT || state == GameState.BOSS_WARNING) : AmbienceScene.NONE,
                    settings.backgroundVolumePercent);
            audio.setPaused(audioFocusSuspended || !active);
        }
    }

    private void handleClick(int x, int y) {
        if (settingsEditor != null) return;
        if (state == GameState.MENU) {
            var choice = com.bigphil.mergehell.render.MenuLaunchRenderer.at(x, y, menuLaunchModel());
            if (choice != null) switch (choice) {
                case START -> startGame();
                case PRACTICE -> startPractice(false);
                case BOSS -> startPractice(true);
                case CONTINUE -> continueRun();
                case SETTINGS -> openSettings();
                case MUTE -> toggleMute();
            }
            else if (x >= 64 && x <= 544 && y >= 322 && y <= 376) handleWeaponKey();
        } else selectUpgradeAt(x, y);
    }

    private com.bigphil.mergehell.render.MenuLaunchRenderer.Model menuLaunchModel() {
        var storage = MergeHellStateService.getInstance();
        int savedWorld = storage.readCheckpoint().map(run -> run.mission + 1).orElse(0);
        return new com.bigphil.mergehell.render.MenuLaunchRenderer.Model(labPowerEnabled, savedWorld,
                storage.ownedByAnotherWindow(storageOwner), settings.muted);
    }

    private void startPractice(boolean atBoss) {
        // A separate practice run leaves the existing campaign checkpoint available to Continue.
        labPowerEnabled = true;
        startGame();
        if (atBoss) labAdvanceToBoss();
        addLog(atBoss ? "BOSS PRACTICE // GOD ON. [T] switches damage on/off."
                : "PRACTICE // GOD ON. [L] jumps to Boss. Campaign checkpoint preserved.");
    }

    private boolean isActiveGameplay() {
        return state == GameState.RUNNING || state == GameState.BOSS_WARNING
                || state == GameState.BOSS_FIGHT;
    }

    private void queueFocusLoss() {
        synchronized (audio) {
            audioFocusSuspended = true;
            audio.setPaused(true);
        }
        inputCommands.clearAndSubmit(() -> {
            clearHeldKeys();
            if (isActiveGameplay()) {
                prePauseState = state;
                state = GameState.PAUSED;
                gameLoop.resetClock();
                addLog("System paused: game focus lost. Press P or Esc to resume.");
            }
        });
    }

    private void handleJumpOrStart() {
        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            startGame();
        } else if (state == GameState.RUNNING || state == GameState.BOSS_WARNING || state == GameState.BOSS_FIGHT) {
            player.requestJump();
        }
    }

    private void handleEnter() {
        if (state != GameState.MISSION_COMPLETE) { handleJumpOrStart(); return; }
        clearHeldKeys();
        if (level < 4) {
            level++;
            advanceLevel();
            transitionTimer = 255;
            syncAudio();
            gameLoop.resetClock();
        } else {
            state = GameState.VICTORY; // Final rewards and score were already saved at the boundary.
        }
    }

    private void clearHeldKeys() {
        kernelInteract = false; singularityInteract = false;
        blueprintInteract = false;
        keyLeft = false;
        keyRight = false;
        keyShoot = false;
        heapChoice = HeapDistrictController.Choice.NONE;
    }

    private void handleWeaponKey() {
        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            WeaponId[] weapons = WeaponId.values();
            selectedStartingWeapon = weapons[(selectedStartingWeapon.ordinal() + 1) % weapons.length];
            addLog("Starting weapon: " + selectedStartingWeapon.name().replace('_', ' '));
            repaint();
        } else if (isActiveGameplay()) {
            player.cycleWeapon();
        }
    }

    private void toggleLabMode() {
        labPowerEnabled = !labPowerEnabled;
        player.setDebugMode(labPowerEnabled);
        if (labPowerEnabled) {
            labOffNoticeTicks = 0;
            markRunUnranked();
            addLog("Invincibility enabled · Unlimited ammo/bombs · No ranking");
        } else {
            labOffNoticeTicks = 188;
            addLog("Invincibility disabled. Normal damage restored. N starts a new ranked run.");
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
        if (!runUnranked) {
            var storage = MergeHellStateService.getInstance();
            storage.clearCheckpoint(storageOwner);
            storage.releaseRun(storageOwner);
            ownsCheckpoint = false;
        }
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
        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            startPractice(true);
            return;
        }
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
        if (heap != null) heap.resetTransient();
        if (boss != null) boss.clearVulnerability();
        recoveryY = player.getY();
        resetDrones();
    }

    private void chooseUpgrade(int index) {
        if (state != GameState.UPGRADE_SELECTION) return;
        if (index < 0 || index >= session.upgradeChoices().size()) return;
        var upgradeId = session.upgradeChoices().get(index).id();
        String title = session.upgradeChoices().get(index).title();
        session.chooseUpgrade(index);
        state = session.state();
        player.setRunBuild(session.runBuild());
        player.applySupply(upgradeId);
        syncAudio();
        audio.emit(CombatFeedbackEvent.of(Cue.UPGRADE));
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

    private void selectUpgradeAt(int logicalX, int logicalY) {
        if (state != GameState.UPGRADE_SELECTION) return;
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
        settingsEditor = null;
        logs.clear();
        ctx.score = 0; ctx.combo = 0; ctx.comboTimer = 0;
        ctx.shakeTimer = 0; ctx.flashTimer = 0; ctx.hitstop = 0; ctx.newKills = 0;
        level = 0; shakeTimer = 0; flashTimer = 0; hitstop = 0;
        difficulty = 1.0; cameraX = 0; isNewHighScore = false;
        bossWarningTimer = 0; bossDeathTimer = 0; missionCompleteTimer = 0; transitionTimer = 0;
        missionSettled = false; missionClearBonus = 0; missionFirstClearReward = false;
        extraLifeScore = 5000;
        long runSeed = random.nextLong();
        session = new GameSession(runSeed, selectedStartingWeapon);
        resetWorldSystems();
        player.setCombatSeed(runSeed);
        collision.resetEffects(ctx);
        renderer.resetVisuals();
        resetDrones();
        visualWorldTick = 0;
        runUnranked = labPowerEnabled;
        labOffNoticeTicks = 0;
        runId = UUID.randomUUID().toString();
        var storage = MergeHellStateService.getInstance();
        if (runUnranked) storage.releaseRun(storageOwner);
        ownsCheckpoint = !runUnranked && storage.claimRun(storageOwner, runId);
        recoveryX = 100;
        recoveryY = GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT;
        levelManager = new LevelManager(0, levelSeed(0x4C41594F5554L));
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
        session.beginMission(0);
        state = GameState.RUNNING;
        syncAudio();
        addLog("Starting new session...");
        persistLevelStart();
    }

    private void advanceLevel() {
        ctx.combo = ctx.comboTimer = ctx.shakeTimer = ctx.flashTimer = ctx.hitstop = ctx.newKills = 0;
        shakeTimer = flashTimer = hitstop = 0;
        difficulty = 1.0 + level * 2.0;
        cameraX = 0;
        extraLifeScore = (int) Math.min(Integer.MAX_VALUE, (long) extraLifeScore + 10000);
        levelManager = new LevelManager(level, levelSeed(0x4C41594F5554L));
        resetWorldSystems();
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
        missionSettled = false; missionClearBonus = 0; missionFirstClearReward = false;
        player.beginNextLevel(100, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT);
        recoveryX = 100;
        recoveryY = GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT;
        collision.resetEffects(ctx);
        resetLevelRandom();
        projectiles.clear(); particles.clear(); floatingTexts.clear();
        renderer.resetVisuals();
        resetDrones();
        session.beginMission(level);
        state = GameState.RUNNING;
        addLog("Level " + (level + 1) + " starting...");
        persistLevelStart();
    }

    private void resetGame() {
        player.reset(100, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT);
        player.setRunBuild(session.runBuild());
        player.setDebugMode(labPowerEnabled);
        resetLevelRandom();
        projectiles.clear(); particles.clear(); floatingTexts.clear();
    }

    @Override
    public synchronized void actionPerformed(ActionEvent e) {
        if (disposed) return;
        try {
            advanceSimulation();
        } finally {
            double seconds = session.worldTick() != visualWorldTick ? GameLoop.LEGACY_STEP_NANOS / 1e9 : 0;
            visualWorldTick = session.worldTick();
            renderer.updateVisuals(player, enemyManager.getEnemies(), seconds);
            syncAudio();
            frames.publish(this::renderLogicalFrame);
            repaint();
        }
    }

    private void advanceSimulation() {
        boolean compact = requestedCompactDisplay;
        if (compact != compactDisplay) {
            compactDisplay = compact;
            renderer.setCompact(compact);
            hudRenderer.setCompact(compact);
            settingsRenderer.setCompact(compact);
        }
        inputCommands.drain();
        if (state == GameState.PAUSED) { repaint(); return; }
        if (state == GameState.BOSS_WARNING) { updateBossArrival(); return; }

        if (state == GameState.ERROR) { repaint(); return; }
        if (labOffNoticeTicks > 0) labOffNoticeTicks--;

        // Feedback uses simulation cadence, including hitstop and upgrade screens.
        // Rendering the same state twice must not shorten a flash or screen shake.
        if (shakeTimer > 0) shakeTimer--;
        if (flashTimer > 0) flashTimer--;
        if (powerFlashTimer > 0) powerFlashTimer--;

        if (state == GameState.UPGRADE_SELECTION) {
            clearHeldKeys();
            repaint();
            return;
        }

        if (state == GameState.MISSION_COMPLETE) {
            repaint(); return;
        }

        // Screen wipe-in on new level
        if (transitionTimer > 0) {
            transitionTimer = Math.max(0, transitionTimer - 12);
        }

        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            repaint(); return;
        }

        observeBossFeedback();
        bossFeedback.tick(true);
        consumeBossFeedback();

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
        if (chapterRoute != null) chapterRoute.beforeMove(player, state == GameState.BOSS_FIGHT || state == GameState.BOSS_WARNING);
        refreshTerrain();
        double previousPlayerX = player.getX(), previousFeet = player.getY() + player.getBounds().height;
        boolean wasDashingOnTerrain = player.isDashing();
        long previousShot = player.getShotSequence();
        int previousHp = player.getHp();
        // Resolve the visible movement limit before firing so a locked screen edge does not
        // lend a new projectile movement that the player never actually completed.
        player.update(keyLeft, keyRight, false, keyShoot || settings.autoFire, chapterRoute == null ? groundY : chapterRoute.groundFor(player.getX(), player.getBounds().width), levelWidth,
                projectiles, combatPlatforms, cameraX, cameraX + panelW - player.getBounds().width);
        if (chapterRoute != null) {
            chapterRoute.afterMove(player, previousPlayerX, (int) enemyManager.getEnemies().stream()
                    .filter(en -> !en.isDead() && en.getType().isHostile()).count());
            if (chapterInteract) chapterRoute.interact(player);
            chapterInteract = false;
            if (player.getMeleeBounds() != null) chapterRoute.melee(player.getMeleeBounds());
            consumeChapterEvents();
        }
        if (environment != null) environment.update(new TraversalEnvironment.Step(
                previousPlayerX, previousFeet, player.getX(), player.getY() + player.getBounds().height,
                player.getBounds().width, cameraX, panelW, player.isGrounded(), wasDashingOnTerrain,
                true, state == GameState.BOSS_FIGHT));
        if (player.getShotSequence() != previousShot && boss != null) boss.recordPlayerShot();
        if (player.getShotSequence() != previousShot) audio.emit(CombatFeedbackEvent.of(
                switch (player.getLastFiredWeaponId()) {
                    case COMMIT_CANNON -> Cue.COMMIT_SHOT;
                    case FORCE_PUSH -> Cue.FORCE_SHOT;
                    case RAPID_CI -> Cue.RAPID_SHOT;
                    case GARBAGE_COLLECTOR -> Cue.GC_SHOT;
                    case FIREWALL -> Cue.FIREWALL_SHOT;
                    case REFACTOR_BEAM -> Cue.BEAM_SHOT;
                }));
        resolvePlayerDefeat();
        if (state == GameState.GAME_OVER) return;

        // ── Camera ──────────────────────────────────────
        // Check for battle zone entry
        boolean legacyMission = LevelManager.isLegacyMission(level);
        LevelManager.BattleZone bz = legacyMission ? levelManager.getBattleAt(player.getX()) : null;
        if (legacyMission && bz != null && !levelManager.isInBattle()) {
            levelManager.enterBattle(bz);
            if (level >= 1 && level <= 4) { recoveryX = player.getX(); recoveryY = groundY - player.getBounds().height; }
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
        if (boss != null && state == GameState.BOSS_FIGHT) targetCam = bossArrivalArenaLeft;
        // Smooth camera lerp
        cameraX += (targetCam - cameraX) * 0.15;

        // ── Battle zone waves ───────────────────────────
        int aliveEnemies = (int) enemyManager.getEnemies().stream()
                .filter(en -> !en.isDead() && en.getType().isHostile()).count();

        if (!legacyMission) {
            double pressure = (1.0 - player.getHp() / (double) player.getMaxHp()) * 0.6
                    + Math.min(1.0, aliveEnemies / 30.0) * 0.4;
            applyDirectorCommands(session.directMission(new DirectorInput(
                    session.worldTick(), player.getX(), pressure, aliveEnemies)), panelW, groundY);
            if (state == GameState.BOSS_WARNING) return;
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
            boss = new Boss(levelManager.bossName, levelManager.bossHp,
                    levelManager.bossSymbol, cameraX + panelW, level, levelSeed(0x424F5353L));
            bossFeedback.reset();
            if (level == 1) {
                heapBossArenaLeft = cameraX;
            }
            if (level >= 1 && level <= 4) {
                recoveryX = cameraX + 100;
                recoveryY = groundY - player.getBounds().height;
            }
            beginBossArrival(boss.getName());
            return;
        }

        // Mid-boss spawn
        // Battle zones are authored encounters: letting ambient spawns leak into them
        // made their clear condition feel arbitrary and could starve the next wave.
        boolean freeRoam = legacyMission && level < 2 && state == GameState.RUNNING && !levelManager.isInBattle();
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
                    spawnExplosion((int) boss.getX() + visualRandom.nextInt(boss.getWidth()),
                            (int) boss.getY() + visualRandom.nextInt(boss.getHeight()),
                            30, GameColors.DANGER_RED);
                if (bossDeathTimer == 50)
                    floatingTexts.add(new FloatingText(boss.getX(), boss.getY(),
                            "PROCESS KILLED!", Color.GREEN));
                if (bossDeathTimer <= 0) {
                    spawnExplosion((int) boss.getX() + boss.getWidth() / 2,
                            (int) boss.getY() + boss.getHeight() / 2, 150, Color.RED);
                    completeCurrentMission();
                }
            }
        }

        if (state == GameState.BOSS_FIGHT && legacyBoss != null) {
            updateLegacyBoss(panelW, groundY);
        }

        // A settled boundary must not collect another coin, open a draft, or change survivor resources.
        if (state == GameState.MISSION_COMPLETE) { repaint(); return; }

        // ── Enemy updates + collision ───────────────────
        double difficultySpeed = 0.8 + difficulty * 0.15;
        List<Projectile> enemyBullets = enemyManager.getEnemyBullets();
        for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
            if (!en.isDead()) {
                if (chapterRoute != null && (en.getType() == EntityType.SENTINEL || en.getType() == EntityType.WARDEN)) {
                    var movement = chapterRoute.movementBounds(en.getX(), (int) Math.ceil(en.getWidth()));
                    en.setMovementBounds(movement.minX(), movement.maxX());
                }
                en.update(difficultySpeed, player.getX(), player.getY(), (int) cameraX, (int) cameraX + panelW, groundY);
                en.maybeShoot(player.getY(), enemyBullets);
            }
        }
        updateHeap();
        updateBlueprint();
        updateKernel();
        updateSingularity();
        if (player.getHp() < previousHp) audio.emit(CombatFeedbackEvent.of(Cue.PLAYER_HURT));
        resolvePlayerDefeat();
        if (state == GameState.GAME_OVER) return;
        if (session.state() == GameState.UPGRADE_SELECTION) {
            state = GameState.UPGRADE_SELECTION;
            clearHeldKeys();
            observeBossFeedback();
            consumeBossFeedback();
            return;
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
                panelW, panelH, cameraX, particles, floatingTexts, this::addLog, combatPlatforms);
        processEnvironmentEvents();
        consumeChapterEvents();
        consumeBlueprintEvents();
        processHeapBlockHits();
        if (player.getHp() < previousHp) audio.emit(CombatFeedbackEvent.of(Cue.PLAYER_HURT));
        if (legacyBoss != null && state == GameState.BOSS_FIGHT) processLegacyBossHits();
        resolvePlayerDefeat();
        observeBossFeedback();
        consumeBossFeedback();
        if (state != GameState.GAME_OVER && session.state() == GameState.UPGRADE_SELECTION) {
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
    private final Random visualRandom = new Random();

    private long levelSeed(long stream) {
        return session.seed() ^ stream ^ (0x9E3779B97F4A7C15L * (level + 1));
    }

    private void resetLevelRandom() {
        random.setSeed(levelSeed(0x535041574EL));
        enemyManager.reset(levelSeed(0x454E454D59L));
        collision.resetDropSeed(levelSeed(0x44524F5053L));
        visualRandom.setSeed(levelSeed(0x56495355414CL));
    }

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
                // Recovery heals within this attempt; durable saves remain at the level entrance.
            } else if (command instanceof DirectorCommand.SpawnBoss) {
                boss = null;
                enemyManager.clearHostiles();
                legacyBoss = new LegacyBossController(new Random(levelSeed(0x4C4547414359L)), 2_400);
                bossFeedback.reset();
                legacyBossArenaLeft = cameraX;
                legacyBoss.setOrigin(cameraX + panelWidth - 200, groundY - 290);
                legacyBossDying = false;
                legacyMeleeConnected = false;
                legacyLaserFlashTicks = 0;
                lastLegacyPhase = BossPhase.DEPENDENCIES;
                beginBossArrival("LEGACY CODE MONSTROSITY");
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
                completeCurrentMission();
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
        List<Projectile> bullets = enemyManager.getEnemyBullets();
        ProjectileBudget.emit(bullets, volley.count(), () -> {
            List<Projectile> accepted = new ArrayList<>(volley.count());
            for (int i = 0; i < volley.count(); i++) {
                double angle = volley.angleFrom(startX, startY, i);
                ProjectileType type = (i == volley.count() / 2 && legacyBoss.phase() == BossPhase.ENRAGED)
                        ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
                accepted.add(new Projectile(startX, startY,
                        Math.cos(angle) * volley.speed(), Math.sin(angle) * volley.speed(), type));
            }
            return accepted;
        });
    }

    private void spawnLegacyShockwave(BossAction.Shockwave shockwave, int groundY) {
        Rectangle core = legacyBoss.coreBounds();
        List<Projectile> bullets = enemyManager.getEnemyBullets();
        ProjectileBudget.emit(bullets, shockwave.count(), () -> {
            List<Projectile> accepted = new ArrayList<>(shockwave.count());
            for (int i = 0; i < shockwave.count(); i++) {
                accepted.add(new Projectile(core.x + 12 + i * 58, groundY - 17,
                        -shockwave.speed(), 0, ProjectileType.CRITICAL));
            }
            return accepted;
        });
    }

    private void damageLegacyBossWithBomb() {
        BossSnapshot snapshot = legacyBoss.snapshot();
        if (snapshot.phase() == BossPhase.DEPENDENCIES) {
            for (int id : legacyBoss.damageAllNodes(120)) {
                BossSnapshot.NodeView view = snapshot.nodes().stream()
                        .filter(node -> node.id() == id).findFirst().orElseThrow();
                rewardLegacyNodeDestroyed(view);
            }
            for (BossSnapshot.NodeView previous : snapshot.nodes()) {
                var now = legacyBoss.nodes().stream().filter(n -> n.id() == previous.id()).findFirst().orElseThrow();
                if (previous.hp() > now.hp()) recordBossImpact(CombatEvent.BossPart.NODE, previous.id(),
                        previous.hp() - now.hp(), now.hp(), now.maxHp(), previous.x(), previous.y(),
                        CombatEvent.DamageKind.BOMB, false, null);
            }
        } else {
            int damage = legacyBoss.damageCore(360);
            recordBossImpact(CombatEvent.BossPart.CORE, 0, damage, legacyBoss.snapshot().coreHp(),
                    snapshot.maxCoreHp(), legacyBoss.coreBounds().getCenterX(), legacyBoss.coreBounds().getCenterY(),
                    CombatEvent.DamageKind.BOMB, false, null);
        }
        hitstop = Math.max(hitstop, 7);
        shakeTimer = Math.max(shakeTimer, 40);
        powerFlashTimer = Math.max(powerFlashTimer, 16);
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
                if (current.alive() && projectile.hits(legacyBoss.nodeBounds(node.id()))) {
                    int previous = current.hp();
                    legacyBoss.damageNode(node.id(), projectile.getDamage());
                    recordBossImpact(CombatEvent.BossPart.NODE, node.id(), previous - current.hp(),
                            current.hp(), current.maxHp(), projectile.getX(), projectile.getY(),
                            projectile.getDamageKind(), false, projectile);
                    projectile.setDead(true);
                    hitNode = true;
                    if (!current.alive()) rewardLegacyNodeDestroyed(node);
                    break;
                }
            }
            if (!hitNode && !projectile.isDead()
                    && projectile.hits(legacyBoss.coreBounds())) {
                int accepted = legacyBoss.damageCore(projectile.getDamage());
                recordBossImpact(CombatEvent.BossPart.CORE, 0, accepted, legacyBoss.snapshot().coreHp(),
                        before.maxCoreHp(), projectile.getX(), projectile.getY(),
                        projectile.getDamageKind(), accepted == 0, projectile);
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
            int previous = current.hp();
            legacyBoss.damageNode(view.id(), 70);
            recordBossImpact(CombatEvent.BossPart.NODE, view.id(), previous - current.hp(),
                    current.hp(), current.maxHp(), melee.getCenterX(), melee.getCenterY(),
                    CombatEvent.DamageKind.MELEE, false, null);
            legacyMeleeConnected = true;
            if (!current.alive()) rewardLegacyNodeDestroyed(view);
            shakeTimer = Math.max(shakeTimer, 12);
            return;
        }
        if (melee.intersects(legacyBoss.coreBounds())) {
            int accepted = legacyBoss.damageCore(70);
            recordBossImpact(CombatEvent.BossPart.CORE, 0, accepted, legacyBoss.snapshot().coreHp(),
                    snapshot.maxCoreHp(), melee.getCenterX(), melee.getCenterY(),
                    CombatEvent.DamageKind.MELEE, accepted == 0, null);
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
    }

    private void completeCurrentMission() {
        if (missionSettled) return;
        session.markComplete();
        missionClearBonus = (int) Math.min(Integer.MAX_VALUE,
                3000L + level * 1000L + (long) (difficulty * 500));
        int settledScore = (int) Math.min(Integer.MAX_VALUE, (long) ctx.score + missionClearBonus);
        if (!runUnranked) {
            var next = level < 4 ? CheckpointCodec.captureNextMission(runId, settledScore,
                    (int) Math.min(Integer.MAX_VALUE, (long) extraLifeScore + 10000),
                    session, player.checkpointForNextLevel()) : null;
            if (level == 4) isNewHighScore = ScoreStore.isHighScore(settledScore);
            missionFirstClearReward = MergeHellStateService.getInstance().settleCampaignMission(
                    storageOwner, level, 250, next, settledScore);
            if (level == 4) ownsCheckpoint = false;
        }
        ctx.score = settledScore;
        missionSettled = true;
        levelManager.onBossDefeated();
        clearHeldKeys();
        hitstop = 0; transitionTimer = 0; missionCompleteTimer = 0;
        state = GameState.MISSION_COMPLETE;
        audio.setPaused(true);
        addLog("World " + (level + 1) + " cleared. Bonus +" + missionClearBonus
                + (missionFirstClearReward ? " // First clear +250 refactor points." : "."));
        if (level >= 2) addKernelLog("chapter." + (level + 1) + ".victory");
    }

    private void saveScore() {
        if (runUnranked) {
            isNewHighScore = false;
            addLog("LAB // Unranked score was not saved.");
            return;
        }
        isNewHighScore = ScoreStore.isHighScore(ctx.score);
        ScoreStore.save(ctx.score);
        MergeHellStateService.getInstance().clearCheckpoint(storageOwner);
        MergeHellStateService.getInstance().releaseRun(storageOwner);
        ownsCheckpoint = false;
    }

    private void handleCombatEvent(CombatEvent event) {
        if (event instanceof CombatEvent.BossImpact impact) {
            bossFeedback.accept(impact);
            return;
        }
        if (event instanceof CombatEvent.DamageDealt) audio.emit(CombatFeedbackEvent.of(Cue.HIT));
        if (event instanceof CombatEvent.BossPhaseChanged) audio.emit(CombatFeedbackEvent.of(Cue.BOSS_PHASE));
        boolean wasOverclocked = session.isOverclocked();
        session.accept(event);
        if (!wasOverclocked && session.isOverclocked()) {
            audio.emit(CombatFeedbackEvent.of(Cue.SUDO));
            addLog("SUDO OVERCLOCK ONLINE — 8 seconds of boosted firepower!");
            floatingTexts.add(new FloatingText(player.getX(), player.getY() - 90,
                    "SUDO // OVERCLOCK", GameColors.SUDO_YELLOW));
            flashTimer = Math.max(flashTimer, 10);
        }
    }

    private void persistLevelStart() {
        if (runUnranked) return;
        if (!ownsCheckpoint) {
            addLog("Another game window owns the checkpoint. This run will not replace it.");
            return;
        }
        var checkpoint = CheckpointCodec.capture(runId, ctx.score, extraLifeScore, session, player.checkpoint());
        MergeHellStateService.getInstance().saveCheckpoint(storageOwner, checkpoint);
    }

    private void continueRun() {
        var saved = MergeHellStateService.getInstance().claimCheckpoint(storageOwner);
        if (saved.isEmpty()) { addLog("No available checkpoint, or another window is using it."); return; }
        var run = saved.get();
        session = CheckpointCodec.restoreSession(run);
        player.restoreCheckpoint(CheckpointCodec.playerCheckpoint(run), session.runBuild(), run.checkpointX, run.checkpointY);
        player.setDebugMode(false);
        runId = run.runId; ownsCheckpoint = true; runUnranked = false; labPowerEnabled = false;
        labOffNoticeTicks = 0;
        level = run.mission; difficulty = 1 + level * 2; cameraX = 0;
        resetWorldSystems();
        ctx.score = run.score; ctx.combo = ctx.comboTimer = ctx.shakeTimer = ctx.flashTimer = ctx.hitstop = ctx.newKills = 0;
        extraLifeScore = run.nextLifeThreshold;
        recoveryX = run.checkpointX; recoveryY = run.checkpointY;
        levelManager = new LevelManager(level, levelSeed(0x4C41594F5554L));
        boss = null; legacyBoss = null; legacyBossDying = false; legacyMeleeConnected = false; lastLegacyPhase = null;
        bossWarningTimer = bossDeathTimer = missionCompleteTimer = hitstop = shakeTimer = flashTimer = legacyLaserFlashTicks = 0;
        missionSettled = false; missionClearBonus = 0; missionFirstClearReward = false;
        transitionTimer = 30;
        resetLevelRandom(); projectiles.clear(); particles.clear(); floatingTexts.clear();
        collision.resetEffects(ctx); darkScenery = new ArrayList<>();
        if (level == 0) {
            platforms = new ArrayList<>(); coins = new ArrayList<>();
            darkRoute = new ProceduralDarkRoute(run.seed ^ 0x4D4552474548454CL, 360);
            darkBiome = DarkBiome.REPOSITORY_CITY; biomeBannerTicks = 180;
            darkRoute.extendTo(3_000, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT, darkBiome, platforms, coins, darkScenery);
        } else {
            platforms = levelManager.getPlatforms(); coins = levelManager.getCoins(); darkRoute = null; biomeBannerTicks = 0;
        }
        renderer.resetVisuals(); visualWorldTick = session.worldTick();
        resetDrones();
        state = GameState.RUNNING; gameLoop.resetClock();
        syncAudio();
        logs.clear(); addLog("Checkpoint restored // World " + (level + 1) + " entrance.");
    }

    private void enforceEntityLimits() {
        List<Projectile> enemyBullets = enemyManager.getEnemyBullets();
        collectProjectileRejections();
        while (particles.size() > EntityLimits.MAX_PARTICLES) particles.remove(0);
        while (floatingTexts.size() > EntityLimits.MAX_FLOATING_TEXTS) floatingTexts.remove(0);
        int hostiles = (int) enemyManager.getEnemies().stream()
                .filter(enemy -> !enemy.isDead() && enemy.getType().isHostile()).count();
        session.updateMetrics(hostiles, projectiles.size(), enemyBullets.size(),
                particles.size(), floatingTexts.size(), rejectedProjectiles);
    }

    /** Aggregate source deltas so a companion respawn cannot erase this level's diagnostics. */
    private void collectProjectileRejections() {
        long playerCount = projectiles.rejectedProjectiles(), enemyCount = enemyManager.getRejectedProjectiles();
        long droneCount = drones.rejectedProjectiles();
        for (long delta : new long[]{counterDelta(playerCount, observedPlayerRejections),
                counterDelta(enemyCount, observedEnemyRejections), counterDelta(droneCount, observedDroneRejections)})
            rejectedProjectiles += (int) Math.min(Integer.MAX_VALUE - rejectedProjectiles, delta);
        observedPlayerRejections = playerCount; observedEnemyRejections = enemyCount; observedDroneRejections = droneCount;
    }

    private static long counterDelta(long current, long previous) { return current >= previous ? current - previous : current; }

    private void resetDrones() {
        collectProjectileRejections();
        drones.reset();
        observedDroneRejections = 0;
        droneTargetIds.clear();
        nextDroneTargetId = 16;
    }

    private com.bigphil.mergehell.world.ChapterRouteController chapterRoute;
    private boolean chapterInteract;

    private void resetWorldSystems() {
        projectiles.reset();
        rejectedProjectiles = 0;
        observedPlayerRejections = 0;
        observedEnemyRejections = enemyManager.getRejectedProjectiles();
        observedDroneRejections = drones.rejectedProjectiles();
        environment = level < 2 ? new TraversalEnvironment(level, levelSeed(0x5445525241494EL),
                GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT) : null;
        if (environment != null) environment.prepare(0, GameViewport.LOGICAL_WIDTH, false);
        chapterRoute = level >= 2 ? new com.bigphil.mergehell.world.ChapterRouteController(level, 480) : null;
        chapterInteract = false;
        environmentEvents.clear(); combatPlatforms = List.of();
        bossArrival.reset();
        bossArrivalArenaLeft = 0;
        heap = level == 1 ? HeapDistrictController.standard(levelSeed(0x48454150L)) : null;
        blueprint = null;
        kernel = null;
        singularity = null;
        singularityEvents.clear();
        kernelInteract = false; singularityInteract = false; kernelEvents.clear();
        blueprintInteract = false; blueprintEvents.clear();
        heapChoice = HeapDistrictController.Choice.NONE;
        heapMeleeConnected = false;
        heapBossArenaLeft = 0;
        powerFlashTimer = 0;
        bossFeedback.reset();
    }

    /** Lethal damage is settled before any pending upgrade can hide a zero-HP player. */
    private void resolvePlayerDefeat() {
        if (player.getHp() > 0 || state == GameState.GAME_OVER) return;
        if (player.loseLife()) {
            player.heal(player.getMaxHp());
            player.setX(recoveryX);
            player.setY(Math.min(recoveryY, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT - player.getBounds().height));
            player.setInvincibleTimer(90);
            resetDrones();
            if (chapterRoute != null) { chapterRoute.resetTransient(); enemyManager.getEnemyBullets().clear(); chapterInteract = false; }
            if (heap != null) heap.resetTransient();
            if (blueprint != null) blueprint.resetTransient();
            if (kernel != null) {
                kernel.resetTransient();
                enemyManager.getEnemyBullets().clear();
            }
            kernelEvents.clear();
            if (singularity != null) {
                singularity.resetTransient();
                enemyManager.getEnemyBullets().clear();
            }
            singularityEvents.clear();
            blueprintEvents.clear();
            if (boss != null) boss.clearVulnerability();
            heapMeleeConnected = false;
            enemyManager.clearHostiles();
            shakeTimer = 20; flashTimer = 15;
            addLog("Process restarted. Lives: " + player.getLives());
        } else {
            state = GameState.GAME_OVER; shakeTimer = 30;
            addLog("FATAL ERROR: All processes terminated.");
            saveScore();
        }
    }

    private void queueHeapChoice(HeapDistrictController.Choice choice) {
        if (chapterRoute != null && state == GameState.RUNNING) chapterInteract = true;
        if (heap != null && isActiveGameplay() && state != GameState.BOSS_WARNING) heapChoice = choice;
        if (blueprint != null && choice == HeapDistrictController.Choice.PURGE
                && (state == GameState.RUNNING || state == GameState.BOSS_FIGHT)) blueprintInteract = true;
        if (kernel != null && choice == HeapDistrictController.Choice.PURGE
                && (state == GameState.RUNNING || state == GameState.BOSS_FIGHT)) kernelInteract = true;
        if (singularity != null && choice == HeapDistrictController.Choice.PURGE
                && (state == GameState.RUNNING || state == GameState.BOSS_FIGHT)) singularityInteract = true;
    }

    private static HeapDistrictController.Bounds heapBounds(Rectangle bounds) {
        return new HeapDistrictController.Bounds(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    private void updateHeap() {
        if (heap == null) return;
        boolean fighting = state == GameState.BOSS_FIGHT && boss != null
                && boss.isActive() && boss.getHp() > 0 && bossDeathTimer == 0;
        boolean active = (state == GameState.RUNNING || state == GameState.BOSS_FIGHT)
                && session.state() != GameState.UPGRADE_SELECTION;
        heap.update(new HeapDistrictController.Input(heapBounds(player.getBounds()), cameraX,
                GameViewport.LOGICAL_WIDTH, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT,
                active, fighting, boss == null ? null : heapBounds(boss.getBounds()),
                boss == null ? 1 : boss.getCombatStage()));
        consumeHeapEvents();
        if (active && player.getHp() > 0 && session.state() != GameState.UPGRADE_SELECTION
                && heapChoice != HeapDistrictController.Choice.NONE) heap.interact(heapChoice);
        heapChoice = HeapDistrictController.Choice.NONE;
        consumeHeapEvents();
    }

    private void processHeapBlockHits() {
        if (heap == null || state != GameState.BOSS_FIGHT || bossDeathTimer > 0) return;
        for (Projectile projectile : projectiles) {
            if (projectile.isDead()) continue;
            for (var block : heap.snapshot().blocks()) {
                if (block.warningTicksRemaining() == 0 && projectile.hits(block.bounds().rectangle())
                        && heap.hitBlock(block.id())) {
                    projectile.setDead(true);
                    break;
                }
            }
        }
        Rectangle melee = player.getMeleeBounds();
        if (melee == null) heapMeleeConnected = false;
        else if (!heapMeleeConnected) for (var block : heap.snapshot().blocks()) {
            if (block.warningTicksRemaining() == 0 && melee.intersects(block.bounds().rectangle())
                    && heap.hitBlock(block.id())) { heapMeleeConnected = true; break; }
        }
        consumeHeapEvents();
    }

    private void consumeHeapEvents() {
        if (heap == null) return;
        for (var event : heap.drainEvents()) {
            if (event instanceof HeapDistrictController.DamagePlayer damage) {
                player.takeDamage(damage.amount());
            } else if (event instanceof HeapDistrictController.Cleaned clean) {
                ctx.score += clean.score();
                if (clean.xp() > 0) session.awardBuildXp(clean.xp());
                enemyManager.getEnemyBullets().removeIf(p -> clean.clearedArea().rectangle().contains(p.getX(), p.getY()));
                if (boss != null && boss.getHp() > 0) boss.openVulnerability(clean.bossExposeTicks());
                audio.emit(CombatFeedbackEvent.of(Cue.GC_PURGE));
                addLog(clean.bossExposeTicks() > 0 ? "GC complete. Core exposed; attack now!" : "GC complete. This area is safe.");
            } else if (event instanceof HeapDistrictController.Salvaged salvage) {
                ctx.score += salvage.score();
                if (salvage.xp() > 0) session.awardBuildXp(salvage.xp());
                audio.emit(CombatFeedbackEvent.of(Cue.UPGRADE));
                addLog("Salvaged +" + salvage.score() + " score / +" + salvage.xp() + " XP. Pipe pressure increased.");
            } else if (event instanceof HeapDistrictController.RefluxArrived reflux) {
                if (boss != null && boss.isActive()) {
                    int restored = boss.heal(reflux.healAmount());
                    if (restored > 0) floatingTexts.add(new FloatingText(boss.getX(), boss.getY() - 12,
                            "RETURN +" + restored, new Color(120, 235, 182)));
                }
            } else if (event instanceof HeapDistrictController.RefluxBroken broken) {
                if (boss != null && boss.getHp() > 0) boss.openVulnerability(broken.bossExposeTicks());
                spawnExplosion((int) broken.x(), (int) broken.y(), 12, new Color(120, 235, 210));
                if (!broken.byGc()) audio.emit(CombatFeedbackEvent.of(Cue.BOSS_BREAK));
            } else if (event instanceof HeapDistrictController.Story story) {
                addLog(story.text());
            }
        }
    }

    private void recordBossImpact(CombatEvent.BossPart part, int id, int damage, int remaining, int maximum,
                                  double x, double y, CombatEvent.DamageKind kind, boolean blocked,
                                  Projectile projectile) {
        Rectangle bounds = part == CombatEvent.BossPart.NODE && legacyBoss != null ? legacyBoss.nodeBounds(id)
                : part == CombatEvent.BossPart.CORE && legacyBoss != null ? legacyBoss.coreBounds()
                : boss == null ? null : boss.getBounds();
        if (bounds != null) {
            x = Math.max(bounds.getMinX(), Math.min(bounds.getMaxX(), x));
            y = Math.max(bounds.getMinY(), Math.min(bounds.getMaxY(), y));
        }
        handleCombatEvent(new CombatEvent.BossImpact(part, id, damage, remaining, maximum, x, y,
                projectile == null ? session.runBuild().weapon() : projectile.getWeapon(), kind,
                projectile == null ? 0 : projectile.getRootEventId(),
                projectile != null && projectile.isCritical(), blocked));
    }

    private void observeBossFeedback() {
        if (legacyBoss != null) {
            BossSnapshot current = legacyBoss.snapshot();
            int hp = current.coreHp() + current.nodes().stream().mapToInt(BossSnapshot.NodeView::hp).sum();
            int max = current.maxCoreHp() + current.nodes().stream().mapToInt(BossSnapshot.NodeView::maxHp).sum();
            Rectangle r = legacyBoss.coreBounds();
            bossFeedback.observe(new BossFeedbackController.Status(hp, max, current.phase().ordinal() + 1,
                    "LEGACY / " + current.phase().name().replace('_', ' '),
                    current.phase() == BossPhase.DEPENDENCIES, false,
                    r.x, r.y, r.width, r.height));
        } else if (boss != null && boss.isActive()) {
            Rectangle r = boss.getBounds();
            bossFeedback.observe(new BossFeedbackController.Status(boss.getHp(), boss.getMaxHp(),
                    boss.getCombatStage(), boss.getName() + " / P" + boss.getCombatStage(), false,
                    boss.isVulnerable(), r.x, r.y, r.width, r.height));
        }
    }

    private void consumeBossFeedback() {
        for (var signal : bossFeedback.drainSignals()) {
            hitstop = Math.max(hitstop, signal.hitstopTicks());
            shakeTimer = Math.max(shakeTimer, signal.shakeTicks());
            Cue cue = switch (signal.kind()) {
                case HIT, HEAVY_HIT -> Cue.BOSS_HIT;
                case NODE_BREAK, GUARD_BREAK, DEFEATED -> Cue.BOSS_BREAK;
                case PHASE -> Cue.BOSS_PHASE;
                default -> null;
            };
            if (cue != null) audio.emit(CombatFeedbackEvent.of(cue));
        }
    }

    private void fireDrones() {
        boolean active = isActiveGameplay() && session.state() != GameState.UPGRADE_SELECTION;
        List<DroneController.Target> targets = new ArrayList<>();
        var enemies = enemyManager.getEnemies();
        droneTargetIds.keySet().removeIf(enemy -> enemy.isDead() || !enemies.contains(enemy));
        if (active && session.runBuild().buildStats().droneLevel() > 0) {
            for (ObstacleManager.Enemy enemy : enemies) {
                if (enemy.isDead() || !enemy.getType().isHostile()) continue;
                long id = droneTargetIds.computeIfAbsent(enemy, ignored -> nextDroneTargetId++);
                Rectangle bounds = enemy.getBounds();
                targets.add(new DroneController.Target(id, bounds.getCenterX(), bounds.getCenterY(),
                        enemy.getHp(), collision.isMarked(ctx, enemy), !enemy.isCollisionProtected()));
            }
            if (state == GameState.BOSS_FIGHT && legacyBoss != null && !legacyBossDying) {
                BossSnapshot snapshot = legacyBoss.snapshot();
                if (legacyBoss.phase() == BossPhase.DEPENDENCIES) {
                    for (BossSnapshot.NodeView node : snapshot.nodes()) if (node.alive()) {
                        Rectangle bounds = legacyBoss.nodeBounds(node.id());
                        targets.add(new DroneController.Target(node.id() + 1, bounds.getCenterX(),
                                bounds.getCenterY(), node.hp(), false, true));
                    }
                } else if (legacyBoss.phase() != BossPhase.DEFEATED) {
                    Rectangle bounds = legacyBoss.coreBounds();
                    targets.add(new DroneController.Target(4, bounds.getCenterX(), bounds.getCenterY(),
                            snapshot.coreHp(), false, true));
                }
            } else if (state == GameState.BOSS_FIGHT && boss != null && boss.isActive()
                    && boss.getHp() > 0 && bossDeathTimer == 0) {
                int partId = 5;
                for (var part : boss.getParts()) {
                    if (part.targetable() && !part.destroyed()) {
                        Rectangle bounds = part.bounds().rectangle();
                        targets.add(new DroneController.Target(partId, bounds.getCenterX(), bounds.getCenterY(),
                                part.hp(), part.weak(), true));
                    }
                    partId++;
                }
            }
        }
        List<Projectile> droneShots = drones.update(new DroneController.Input(player.getBounds().getCenterX(),
                player.getBounds().getCenterY(), player.getFacingDir(),
                session.runBuild().buildStats().droneLevel(), session.runBuild().weapon(),
                session.runBuild().effectiveStats().damage(),
                new DroneController.Bounds(cameraX, 0, cameraX + GameViewport.LOGICAL_WIDTH,
                        GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT), targets,
                Math.max(0, EntityLimits.MAX_PROJECTILES - projectiles.size()), active));
        projectiles.addAll(droneShots);
        if (!droneShots.isEmpty()) audio.emit(CombatFeedbackEvent.of(Cue.DRONE_SHOT));
    }

    private void drawEffectZones(Graphics2D original) {
        Graphics2D g = (Graphics2D) original.create();
        try {
            if (heap != null && state != GameState.MENU) heapRenderer.drawWorld(g, heap.snapshot(),
                    cameraX, GameViewport.LOGICAL_WIDTH, compactDisplay, settings.flashes, settings.highContrast);
            for (var zone : collision.effectZones(ctx)) {
                int radius = zone.radius();
                int alpha = Math.min(95, zone.ticksRemaining() * 4);
                g.setColor(new Color(244, 129, 36, alpha));
                g.fillOval((int) zone.x() - radius, (int) zone.y() - radius, radius * 2, radius * 2);
                g.setColor(new Color(255, 192, 61, Math.min(200, alpha * 2)));
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval((int) zone.x() - radius, (int) zone.y() - radius, radius * 2, radius * 2);
            }
            for (var enemy : enemyManager.getEnemies()) {
                if (!enemy.isDead() && collision.isMarked(ctx, enemy)) {
                    int x = (int) (enemy.getX() + enemy.getWidth() / 2), y = (int) enemy.getY() - 18;
                    g.setColor(new Color(255, 213, 94)); g.setStroke(new BasicStroke(1.5f));
                    g.drawOval(x - 6, y - 6, 12, 12); g.drawLine(x, y - 10, x, y - 3);
                }
            }
            if (state != GameState.MENU) droneRenderer.render(g, drones.snapshot());
        } finally { g.dispose(); }
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
        else if (type.isChapterSpecialist()) y = ObstacleManager.specialistSpawnY(type, groundY, random.nextInt(70));
        enemyManager.spawnEnemy(panelWidth + random.nextInt(70) + (int) cameraX, y, type);
    }

    private void spawnExplosion(int x, int y, int count, Color c) {
        for (int i = 0; i < count; i++) {
            double angle = visualRandom.nextDouble() * Math.PI * 2;
            double speed = 2 + visualRandom.nextDouble() * 8;
            particles.add(new Particle(x, y, c,
                    Math.cos(angle) * speed, Math.sin(angle) * speed - 3,
                    0.03f + visualRandom.nextFloat() * 0.03f));
        }
    }

    private void renderLogicalFrame(Graphics2D logical) {
        try (var language = com.bigphil.mergehell.i18n.GameText.use(
                com.bigphil.mergehell.i18n.GameLanguage.fromTag(settings.language))) {
            renderLocalizedFrame(logical);
        }
    }

    private void renderLocalizedFrame(Graphics2D logical) {
        int panelW = GameViewport.LOGICAL_WIDTH;
        int panelH = GameViewport.LOGICAL_HEIGHT;
        int groundY = panelH - TERMINAL_HEIGHT;
        int wave = levelManager != null ? levelManager.getCurrentWave() : 0;
        int total = levelManager != null ? levelManager.getTotalWaves() : 0;
        boolean inBattle = levelManager != null && levelManager.isInBattle();
        double runProgress = levelManager == null ? 0
                : LevelManager.isLegacyMission(level)
                    ? levelManager.getProgress(player.getX()) : session.missionProgress();

        logical.setColor(GameColors.BG);
        logical.fillRect(0, 0, panelW, panelH);
        renderer.setBossHudManaged(true);
        renderer.setFlashesEnabled(settings.flashes);
        renderer.setPowerFlashTicks(settings.flashes ? powerFlashTimer : 0);
        renderer.setChapter(chapterRoute == null ? com.bigphil.mergehell.world.ChapterRouteController.Snapshot.empty() : chapterRoute.snapshot());
        renderer.setTraversal(environment == null ? TraversalEnvironment.Snapshot.empty() : environment.snapshot());
        renderer.setBlueprint(blueprint == null || state == GameState.BOSS_WARNING
                || state == GameState.PAUSED && prePauseState == GameState.BOSS_WARNING
                ? BlueprintCitadelController.Snapshot.empty() : blueprint.snapshot());
        renderer.setKernel(kernel == null || state == GameState.BOSS_WARNING
                || state == GameState.PAUSED && prePauseState == GameState.BOSS_WARNING
                ? KernelCoreController.Snapshot.empty() : kernel.snapshot());
        renderer.setSingularity(singularity == null || state == GameState.BOSS_WARNING
                || state == GameState.MENU || state == GameState.MISSION_COMPLETE
                ? SingularityEdgeController.Snapshot.empty() : singularity.snapshot());
        renderer.render(logical, panelW, panelH, groundY,
                state, player, boss, enemyManager,
                projectiles, enemyManager.getEnemyBullets(),
                particles, floatingTexts, combatPlatforms.isEmpty() ? platforms : combatPlatforms, coins,
                logs, ctx.score, ctx.combo, ctx.comboTimer, shakeTimer * settings.shakePercent / 100,
                settings.flashes ? flashTimer : 0, level, difficulty, isNewHighScore,
                cameraX, inBattle, wave, total, transitionTimer, runProgress,
                darkBiome, darkScenery, biomeBannerTicks, this::drawEffectZones, layer -> {
                    if (legacyBoss != null && (state == GameState.BOSS_FIGHT || state == GameState.BOSS_WARNING
                            || state == GameState.PAUSED && prePauseState == GameState.BOSS_FIGHT
                            || state == GameState.PAUSED && prePauseState == GameState.BOSS_WARNING
                            || state == GameState.UPGRADE_SELECTION)) drawLegacyBoss(layer, panelW);
                    boolean showBoss = (legacyBoss != null || boss != null && boss.isActive())
                            && (state == GameState.BOSS_FIGHT || state == GameState.PAUSED
                            && prePauseState == GameState.BOSS_FIGHT || state == GameState.UPGRADE_SELECTION);
                    if (showBoss) {
                        Graphics2D world = (Graphics2D) layer.create();
                        try {
                            world.translate(-cameraX, 0);
                            bossFeedbackRenderer.renderWorld(world, bossFeedback.snapshot(), settings.flashes);
                        } finally { world.dispose(); }
                        if (boss != null && boss.hasMultipartEncounter())
                            bossFeedbackRenderer.renderOverlay(layer, bossFeedback.snapshot(), panelW, compactDisplay,
                                    GameText.message("chapter.boss.move.exposed"), boss.getEncounterStatus(),
                                    GameText.message(boss.getEncounterHint()));
                        else bossFeedbackRenderer.renderOverlay(layer, bossFeedback.snapshot(), panelW, compactDisplay);
                    }
                });

        if ((state == GameState.RUNNING || state == GameState.BOSS_FIGHT) && levelManager != null) {
            logical.setColor(new Color(255, 255, 255, 25));
            logical.fillRect(0, panelH - 32, panelW, 2);
            logical.setColor(new Color(100, 200, 255, 100));
            logical.fillRect(0, panelH - 32, (int) (panelW * runProgress), 2);
        }

        boolean gameplayHudVisible = state == GameState.RUNNING || state == GameState.BOSS_WARNING
                || state == GameState.BOSS_FIGHT || state == GameState.LEVEL_CLEAR;
        if (gameplayHudVisible) {
            hudRenderer.render(logical, session, player, ctx.score, ctx.combo, ctx.comboTimer,
                    runUnranked, !LevelManager.isLegacyMission(level));
            if (heap != null && state != GameState.BOSS_WARNING) heapHudRenderer.render(logical, heap.snapshot(), compactDisplay,
                    state == GameState.BOSS_FIGHT, labOffNoticeTicks > 0);
            if (blueprint != null && state != GameState.BOSS_WARNING) blueprintHudRenderer.render(logical,
                    blueprint.snapshot(), compactDisplay, state == GameState.BOSS_FIGHT, labOffNoticeTicks > 0);
            if (kernel != null && state != GameState.BOSS_WARNING) kernelHudRenderer.render(logical,
                    kernel.snapshot(), compactDisplay, state == GameState.BOSS_FIGHT, labOffNoticeTicks > 0);
            if (singularity != null && state != GameState.BOSS_WARNING) singularityHudRenderer.render(logical,
                    singularity.snapshot(), compactDisplay, state == GameState.BOSS_FIGHT, labOffNoticeTicks > 0);
        }
        if (state == GameState.BOSS_WARNING)
            bossArrivalRenderer.renderOverlay(logical, bossArrival.snapshot(), panelW, groundY, compactDisplay);
        if ((gameplayHudVisible || state == GameState.PAUSED) && labOffNoticeTicks > 0 && !labPowerEnabled)
            drawLabOffNotice(logical);
        if (state == GameState.UPGRADE_SELECTION) upgradeRenderer.render(logical, session);
        if (state == GameState.MISSION_COMPLETE) drawCompletionRewards(logical, panelW);
        if (state == GameState.MENU || state == GameState.GAME_OVER || state == GameState.VICTORY) {
            drawStartingLoadout(logical);
        }
        if (state == GameState.ERROR) drawLoopError(logical, panelW, panelH);
        if (settingsEditor != null) settingsRenderer.render(logical, settingsEditor.snapshot());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D physical = (Graphics2D) g.create();
        physical.setColor(GameColors.BG);
        physical.fillRect(0, 0, getWidth(), getHeight());
        physical.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        physical.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        ViewportTransform transform = GameViewport.fit(getWidth(), getHeight());
        frames.paint(physical, transform.offsetX(), transform.offsetY(), transform.drawWidth(), transform.drawHeight());
        physical.dispose();

    }

    LevelManager getLevelManager() { return levelManager; }
    void setLevelManager(LevelManager lm) { this.levelManager = lm; }
    GameSession getSession() { return session; }

    @Override
    public void addNotify() {
        super.addNotify();
        synchronized (audio) { audioFocusSuspended = false; }
        if (gameLoop != null) gameLoop.resume();
    }

    @Override
    public void removeNotify() {
        if (gameLoop != null) gameLoop.pause();
        queueFocusLoss();
        super.removeNotify();
    }

    @Override
    public synchronized void dispose() {
        if (disposed) return;
        disposed = true;
        // These close methods only request shutdown; none joins a worker while this lock is held.
        // Any tick already inside this monitor has settled its checkpoint before ownership is released.
        gameLoop.dispose();
        audio.close();
        frames.close();
        resetDrones();
        MergeHellStateService.getInstance().releaseRun(storageOwner);
        ownsCheckpoint = false;
    }

    private synchronized void handleLoopError(Throwable error) {
        if (disposed) return;
        String message = error.getMessage();
        loopErrorMessage = message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message.replaceAll("[\\r\\n]+", " ");
        if (loopErrorMessage.length() > 120) loopErrorMessage = loopErrorMessage.substring(0, 120);
        state = GameState.ERROR;
        audio.setPaused(true);
        frames.publish(g -> drawLoopError(g, GameViewport.LOGICAL_WIDTH, GameViewport.LOGICAL_HEIGHT));
        repaint();
    }

    private void drawLoopError(Graphics2D original, int width, int height) {
        try (var language = GameText.use(com.bigphil.mergehell.i18n.GameLanguage.fromTag(settings.language))) {
            Graphics2D g = (Graphics2D) original.create();
            g.setColor(new Color(8, 10, 15, 245));
            g.fillRect(0, 0, width, height);
            g.setColor(GameColors.DANGER_RED);
            g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 28)));
            GameText.draw(g, "GAME LOOP PAUSED", 70, height / 2 - 30);
            g.setColor(Color.WHITE);
            g.setFont(GameText.font(new Font("JetBrains Mono", Font.PLAIN, 14)));
            GameText.draw(g, "An unexpected error interrupted this run.", 70, height / 2 + 10);
            GameText.draw(g, "Close and reopen the tool window to restart safely.", 70, height / 2 + 45);
            g.dispose();
        }
    }

    private void drawStartingLoadout(Graphics2D original) {
        Graphics2D g = (Graphics2D) original.create();
        g.setColor(new Color(8, 12, 20, 225));
        int left = state == GameState.MENU ? 64 : 230;
        int top = state == GameState.MENU ? 322 : 468;
        g.fillRoundRect(left, top, 480, 95, 7, 7);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compactDisplay ? 18 : 14)));
        g.setColor(GameColors.SHIELD_CYAN);
        GameText.draw(g, "[ Z ] STARTING WEAPON  " + (selectedStartingWeapon.ordinal() + 1) + "/6", left + 16, top + 24);
        g.setColor(GameColors.SUDO_YELLOW);
        var definition = WeaponCatalog.definition(selectedStartingWeapon);
        String detail = definition.displayName().toUpperCase() + " // "
                + (compactDisplay ? definition.tags()[0] : String.join(" + ", definition.tags()));
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compactDisplay ? 17 : 12)));
        GameText.draw(g, detail, left + 16, top + 47);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compactDisplay ? 16 : 11)));
        g.setColor(labPowerEnabled ? GameColors.SUDO_YELLOW : new Color(190, 205, 220));
        String labStatus = labPowerEnabled
                ? "Invincibility on · Practice run   [ T ] Disable"
                : "Normal damage   [ T ] Invincibility   [ N ] New run";
        GameText.draw(g, labStatus, left + 16, top + 75);
        if (state == GameState.MENU) {
            com.bigphil.mergehell.render.MenuLaunchRenderer.render(g, menuLaunchModel(), compactDisplay);
        }
        g.dispose();
    }

    private void drawCompletionRewards(Graphics2D original, int width) {
        Graphics2D g = (Graphics2D) original.create();
        try {
            String reward = "CLEAR BONUS +" + missionClearBonus
                    + (missionFirstClearReward ? "     FIRST CLEAR +250 RP" : "");
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compactDisplay ? 18 : 15)));
            g.setColor(new Color(242, 190, 105));
            GameText.draw(g, reward, (width - g.getFontMetrics().stringWidth(GameText.text(reward))) / 2, 355);
            String progress = runUnranked ? "Practice used · No rankings or permanent rewards"
                    : level == 4 ? "CAMPAIGN RESULT RECORDED"
                    : ownsCheckpoint ? "NEXT WORLD ENTRANCE SAVED"
                    : "ANOTHER WINDOW IS SAVING THE CAMPAIGN";
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compactDisplay ? 17 : 13)));
            g.setColor(new Color(177, 196, 190));
            GameText.draw(g, progress, (width - g.getFontMetrics().stringWidth(GameText.text(progress))) / 2, 473);
        } finally { g.dispose(); }
    }

    private void drawLabOffNotice(Graphics2D original) {
        Graphics2D g = (Graphics2D) original.create();
        try {
            g.setColor(new Color(8, 16, 20, 237));
            g.fillRoundRect(14, 500, compactDisplay ? 550 : 464, 57, 6, 6);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compactDisplay ? 20 : 17)));
            g.setColor(new Color(169, 233, 196));
            GameText.draw(g, "Invincibility off · Normal damage restored", 26, 524);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compactDisplay ? 17 : 14)));
            g.setColor(new Color(211, 220, 216));
            GameText.draw(g, runUnranked ? "Practice run; no ranking. [ N ] New normal run" : "Normal damage is active", 26, 547);
        } finally { g.dispose(); }
    }

    private void drawLegacyBoss(Graphics2D graphics, int panelWidth) {
        Rectangle core = legacyBoss.coreBounds();
        graphics = (Graphics2D) graphics.create();
        try {
            if (bossArrival.active()) {
                double progress = bossArrival.snapshot().progress();
                graphics.translate((1 - progress) * 180, -(1 - progress) * 65);
                graphics.setComposite(AlphaComposite.SrcOver.derive((float) (.12 + progress * .88)));
            }
            legacyBossRenderer.render(graphics, renderer.art(), new com.bigphil.mergehell.render.LegacyBossRenderer.Visual(
                    legacyBoss.snapshot(), core.x, core.y, core.width, core.height,
                    legacyBoss.telegraphTicksRemaining(), legacyLaserY, legacyLaserHeight, legacyLaserFlashTicks,
                    legacyBossDying ? (90 - bossDeathTimer) / 90.0 : 0, renderer.visualSeconds(),
                    cameraX, compactDisplay, settings.flashes), panelWidth, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT,
                    false, !bossArrival.active());
        } finally { graphics.dispose(); }
    }

    private void beginBossArrival(String name) {
        bossArrival.begin(name, BossArrivalController.Direction.RIGHT);
        bossWarningTimer = bossArrival.remainingTicks();
        bossArrivalArenaLeft = cameraX;
        enemyManager.clearHostiles(); enemyManager.getEnemyBullets().clear(); projectiles.clear();
        collision.resetEffects(ctx); resetDrones();
        heapChoice = HeapDistrictController.Choice.NONE;
        hitstop = shakeTimer = flashTimer = powerFlashTimer = 0;
        blueprintInteract = false;
        state = GameState.BOSS_WARNING; session.setGameplayState(state);
        kernelInteract = false; singularityInteract = false;
        refreshTerrain();
        addLog("WARNING: Boss arena detected!");
    }

    private void updateBossArrival() {
        int groundY = GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT;
        cameraX = bossArrivalArenaLeft;
        session.setGameplayState(GameState.BOSS_WARNING);
        session.tick(new InputFrame(keyLeft, keyRight, false, false, player.isDashing(), false));
        player.update(keyLeft, keyRight, false, false, groundY, levelManager.getCameraMaxX(),
                projectiles, combatPlatforms, cameraX + 20, cameraX + GameViewport.LOGICAL_WIDTH - 320);
        projectiles.clear();
        boolean arrived = bossArrival.tick(true);
        bossWarningTimer = bossArrival.remainingTicks();
        if (boss != null) boss.previewArrival(bossArrival.snapshot().progress(), groundY);
        transitionTimer = Math.max(0, transitionTimer - 12);
        if (arrived) {
            if (boss != null) boss.activate();
            state = GameState.BOSS_FIGHT; session.setGameplayState(state); session.markBossSpawned();
            player.setInvincibleTimer(Math.max(45, player.getInvincibleTimer()));
            audio.emit(CombatFeedbackEvent.of(Cue.BOSS_PHASE));
            addLog("ALERT: " + bossArrival.snapshot().bossName() + " engaged!");
        }
    }

    private void refreshTerrain() {
        if (chapterRoute != null) { combatPlatforms = chapterRoute.platforms(); return; }
        if (environment == null) { combatPlatforms = platforms; return; }
        environment.prepare(cameraX, GameViewport.LOGICAL_WIDTH,
                state == GameState.BOSS_WARNING || state == GameState.BOSS_FIGHT);
        var terrain = new ArrayList<>(platforms);
        terrain.addAll(environment.platforms());
        if (blueprint != null && state != GameState.BOSS_WARNING) terrain.addAll(blueprint.platforms());
        if (kernel != null && state != GameState.BOSS_WARNING) terrain.addAll(kernel.platforms());
        if (singularity != null && state != GameState.BOSS_WARNING) terrain.addAll(singularity.platforms());
        terrain.sort(java.util.Comparator.comparingDouble(platform -> platform.x));
        combatPlatforms = List.copyOf(terrain);
    }

    private boolean hitWorldObject(Projectile shot, double before) {
        if (chapterRoute != null) return state == GameState.RUNNING && chapterRoute.consumeShot(shot, before);
        if (state != GameState.RUNNING && state != GameState.BOSS_FIGHT) return false;
        BlueprintCitadelController.SupportView nearest = null;
        double supportHit = Double.POSITIVE_INFINITY;
        if (blueprint != null && session.state() != GameState.UPGRADE_SELECTION) {
            for (var support : blueprint.snapshot().supports()) {
                if (support.state() != BlueprintCitadelController.SupportState.ONLINE
                        && support.state() != BlueprintCitadelController.SupportState.OVERLOADING) continue;
                double fraction = shot.hitFraction(support.bounds().rectangle());
                if (fraction < supportHit) { supportHit = fraction; nearest = support; }
            }
        }
        if (environment != null && state == GameState.RUNNING) {
            var hit = environment.hit(shot, Math.min(before, supportHit));
            if (hit.isPresent()) {
                environmentEvents.addLast(hit.get()); shot.setDead(true); return true;
            }
        }
        if (nearest != null && supportHit <= before && blueprint.hitSupport(nearest.id(), shot.getDamage())) {
            shot.setDead(true);
            spawnExplosion((int) nearest.bounds().centerX(), (int) shot.getY(), 4, new Color(137, 204, 213));
            return true;
        }
        return false;
    }

    private void consumeChapterEvents() {
        if (chapterRoute == null) return;
        for (var event : chapterRoute.drainEvents()) {
            if (event.spawn()) enemyManager.spawnEnemy((int) event.x(), (int) event.y(), EntityType.LURKER);
            else {
                if (event.key().equals("chapter.bridge.rescued")) {
                    cameraX = Math.max(0, Math.min(cameraX, player.getX()-120));
                    clearHeldKeys(); resetDrones();
                }
                addKernelLog(event.key());
                if (!event.key().contains(".story."))
                    spawnExplosion((int) event.x(), (int) event.y(), event.damage() > 0 ? 8 : 18,
                            level == 4 ? new Color(172, 220, 132) : new Color(240, 182, 99));
                if (event.key().endsWith(".broken")) {
                    ctx.score += 200;
                    audio.emit(CombatFeedbackEvent.of(Cue.BOSS_BREAK));
                }
            }
        }
    }

    private static BlueprintCitadelController.Bounds blueprintBounds(Rectangle bounds) {
        return new BlueprintCitadelController.Bounds(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    private void updateBlueprint() {
        if (blueprint == null) return;
        boolean fighting = state == GameState.BOSS_FIGHT && boss != null
                && boss.isActive() && boss.getHp() > 0 && bossDeathTimer == 0;
        boolean active = (state == GameState.RUNNING || state == GameState.BOSS_FIGHT)
                && session.state() != GameState.UPGRADE_SELECTION;
        blueprint.update(new BlueprintCitadelController.Input(blueprintBounds(player.getBounds()), cameraX,
                GameViewport.LOGICAL_WIDTH, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT,
                active, fighting, boss == null ? null : blueprintBounds(boss.getBounds()),
                boss == null ? 1 : boss.getCombatStage()));
        consumeBlueprintEvents();
        if (blueprintInteract && active && player.getHp() > 0 && session.state() != GameState.UPGRADE_SELECTION)
            blueprint.interact();
        blueprintInteract = false;
        refreshTerrain();
    }

    private void consumeBlueprintEvents() {
        if (blueprint == null) return;
        var fresh = blueprint.drainEvents();
        // Settle hurt before a same-step structural reward can open a draft.
        for (var event : fresh) if (event instanceof BlueprintCitadelController.DamagePlayer damage)
            player.takeDamage(damage.amount());
        for (var event : fresh) if (!(event instanceof BlueprintCitadelController.DamagePlayer)) blueprintEvents.addLast(event);
        if (player.getHp() <= 0) { blueprintEvents.clear(); return; }
        while (!blueprintEvents.isEmpty() && session.state() != GameState.UPGRADE_SELECTION) {
            var event = blueprintEvents.removeFirst();
            if (event instanceof BlueprintCitadelController.Story story) addLog(story.text());
            else if (event instanceof BlueprintCitadelController.SupportBroken broken) {
                ctx.score += broken.score();
                if (broken.xp() > 0) session.awardBuildXp(broken.xp());
                enemyManager.getEnemyBullets().removeIf(p -> broken.clearedArea().rectangle().intersects(p.getBounds()));
                if (broken.bossExposeTicks() > 0 && boss != null) boss.interruptBlueprint(broken.bossExposeTicks());
                audio.emit(CombatFeedbackEvent.of(Cue.BOSS_BREAK));
                addLog(broken.bossExposeTicks() > 0 ? "SUPPORTS OFFLINE // CORE EXPOSED"
                        : "Support disconnected. Scanner offline; walkway collapsing.");
            }
        }
    }

    private static KernelCoreController.Bounds kernelBounds(Rectangle bounds) {
        return new KernelCoreController.Bounds(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    private void updateKernel() {
        if (kernel == null) return;
        boolean fighting = state == GameState.BOSS_FIGHT && boss != null
                && boss.isActive() && boss.getHp() > 0 && bossDeathTimer == 0;
        boolean active = (state == GameState.RUNNING || state == GameState.BOSS_FIGHT)
                && session.state() != GameState.UPGRADE_SELECTION;
        kernel.update(new KernelCoreController.Input(kernelBounds(player.getBounds()), cameraX,
                GameViewport.LOGICAL_WIDTH, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT,
                active, fighting, boss == null ? null : kernelBounds(boss.getBounds()),
                boss == null ? 1 : boss.getCombatStage(), fighting && boss.isDashing()));
        consumeKernelEvents();
        if (kernelInteract && active && player.getHp() > 0 && session.state() != GameState.UPGRADE_SELECTION) {
            kernel.interact();
            consumeKernelEvents();
        }
        kernelInteract = false; singularityInteract = false;
        refreshTerrain();
    }

    private void consumeKernelEvents() {
        var fresh = kernel.drainEvents();
        for (var event : fresh) if (event instanceof KernelCoreController.DamagePlayer damage)
            player.takeDamage(damage.amount());
        for (var event : fresh) if (!(event instanceof KernelCoreController.DamagePlayer)) kernelEvents.addLast(event);
        if (player.getHp() <= 0) { kernelEvents.clear(); return; }
        while (!kernelEvents.isEmpty() && session.state() != GameState.UPGRADE_SELECTION) {
            var event = kernelEvents.removeFirst();
            if (event instanceof KernelCoreController.Story story) addKernelLog(story.text());
            else if (event instanceof KernelCoreController.StationDisabled station) {
                ctx.score += station.score();
                if (station.xp() > 0) session.awardBuildXp(station.xp());
                audio.emit(CombatFeedbackEvent.of(Cue.GC_PURGE));
                addKernelLog("kernel.log.powerOff");
            } else if (event instanceof KernelCoreController.Discharge fault) {
                enemyManager.getEnemyBullets().removeIf(p -> fault.clearedArea().rectangle().intersects(p.getBounds()));
                if (boss != null) boss.interruptKernel(fault.bossExposeTicks());
                audio.emit(CombatFeedbackEvent.of(Cue.BOSS_BREAK));
                spawnExplosion((int) boss.getBounds().getCenterX(), (int) boss.getBounds().getCenterY(), 24,
                        new Color(241, 183, 112));
                addKernelLog("kernel.log.exposed");
            }
        }
    }

    private void updateSingularity() {
        if (singularity == null) return;
        boolean fighting = state == GameState.BOSS_FIGHT && boss != null
                && boss.isActive() && boss.getHp() > 0 && bossDeathTimer == 0;
        boolean active = (state == GameState.RUNNING || state == GameState.BOSS_FIGHT)
                && session.state() != GameState.UPGRADE_SELECTION;
        var b = player.getBounds();
        singularity.update(new SingularityEdgeController.Input(
                new SingularityEdgeController.Bounds(b.x, b.y, b.width, b.height), cameraX,
                GameViewport.LOGICAL_WIDTH, GameViewport.LOGICAL_HEIGHT - TERMINAL_HEIGHT,
                active, fighting, boss == null ? 1 : boss.getCombatStage()));
        consumeSingularityEvents();
        if (singularityInteract && active && player.getHp() > 0 && session.state() != GameState.UPGRADE_SELECTION) {
            singularity.interact();
            consumeSingularityEvents();
        }
        singularityInteract = false;
        refreshTerrain();
    }

    private void consumeSingularityEvents() {
        var fresh = singularity.drainEvents();
        for (var event : fresh) if (event instanceof SingularityEdgeController.DamagePlayer damage)
            player.takeDamage(damage.amount());
        for (var event : fresh) if (!(event instanceof SingularityEdgeController.DamagePlayer)) singularityEvents.addLast(event);
        if (player.getHp() <= 0) { singularityEvents.clear(); return; }
        while (!singularityEvents.isEmpty() && session.state() != GameState.UPGRADE_SELECTION) {
            var event = singularityEvents.removeFirst();
            if (event instanceof SingularityEdgeController.Story story) addKernelLog(story.text());
            else if (event instanceof SingularityEdgeController.AnchorStabilized anchor) {
                ctx.score += anchor.score();
                if (anchor.xp() > 0) session.awardBuildXp(anchor.xp());
                audio.emit(CombatFeedbackEvent.of(Cue.GC_PURGE));
                addKernelLog("singularity.log.stabilized");
            } else if (event instanceof SingularityEdgeController.Resonance resonance) {
                enemyManager.getEnemyBullets().removeIf(p -> resonance.clearedArea().rectangle().intersects(p.getBounds()));
                if (boss != null) {
                    boss.interruptSingularity(resonance.bossExposeTicks());
                    spawnExplosion((int) boss.getBounds().getCenterX(), (int) boss.getBounds().getCenterY(), 24,
                            new Color(149, 220, 216));
                }
                audio.emit(CombatFeedbackEvent.of(Cue.BOSS_BREAK));
                addKernelLog("singularity.log.exposed");
            }
        }
    }

    /** Stored logs stay canonical so existing entries can switch display language later. */
    private void addKernelLog(String key) {
        try (var language = GameText.use(com.bigphil.mergehell.i18n.GameLanguage.ENGLISH)) {
            addLog(GameText.message(key));
        }
    }

    private void processEnvironmentEvents() {
        if (player.getHp() <= 0) { environmentEvents.clear(); return; }
        while (!environmentEvents.isEmpty() && session.state() != GameState.UPGRADE_SELECTION) {
            var event = environmentEvents.removeFirst();
            if (event.kind() == TraversalEnvironment.PropKind.CAPACITOR) {
                collision.discharge(ctx, enemyManager, session.runBuild().weapon(),
                        event.x(), event.y(), event.radius(), particles, floatingTexts);
                spawnExplosion((int) event.x(), (int) event.y(), 36, new Color(105, 229, 242));
                audio.emit(CombatFeedbackEvent.of(Cue.GC_PURGE));
                floatingTexts.add(new FloatingText(event.x(), event.y() - 28,
                        "CAPACITOR DISCHARGE", new Color(133, 235, 243)));
            } else {
                player.addBomb(); player.heal(15);
                spawnExplosion((int) event.x(), (int) event.y(), 18, GameColors.SUDO_YELLOW);
                audio.emit(CombatFeedbackEvent.of(Cue.UPGRADE));
                floatingTexts.add(new FloatingText(event.x(), event.y() - 28,
                        "SUPPLIES +1 BOMB / +15 HP", GameColors.SUDO_YELLOW));
            }
        }
    }
}
