package com.bigphil.mergehell.model;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.combat.CombatStats;
import com.bigphil.mergehell.combat.FireRequest;
import com.bigphil.mergehell.combat.WeaponFireController;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.combat.WeaponCatalog;
import com.bigphil.mergehell.combat.CombatRandom;
import com.bigphil.mergehell.progression.RunBuild;
import com.bigphil.mergehell.progression.UpgradeId;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Player {
    private double x, y, dy;
    private final int width = 30, height = 30;
    private boolean grounded = false;
    private int jumpsRemaining = 2;
    private int jumpBufferTimer = 0;
    private int coyoteTimer = 0;

    private int hp = 100;
    private final int maxHp = 100;
    private int sudoTimer = 0;
    private int shieldTimer = 0;
    private int invincibleTimer = 0;
    private int cooldown = 0;

    private int dashTimer = 0;
    private int dashCooldown = 0;
    private double dashVx = 0;
    private int facingDir = 1;

    private int meleeTimer = 0;
    private int meleeCooldown = 0;
    private int bombs = 3;
    private int lives = 3;

    private WeaponType currentWeapon = WeaponType.COMMIT;
    private int weaponAmmo = 0;
    private boolean debugMode = false;
    private boolean temporaryWeapon;
    private RunBuild runBuild;
    private int shieldRebootsUsed;
    private final WeaponFireController fireController;
    private final CombatRandom combatRandom = new CombatRandom(System.nanoTime());
    private int rapidHeat;
    private int beamCharge;
    private long shotSequence;
    private WeaponId lastFiredWeaponId;
    private int dashCacheTicks;
    private int meleeReflectionsRemaining;

    private static final double GRAVITY = 0.6;
    private static final double JUMP_FORCE = -13;
    private static final double SPEED = 5;
    private static final int DASH_FRAMES = 8;
    private static final int DASH_COOLDOWN_MAX = 45;
    private static final double DASH_SPEED = 10;
    private static final int MELEE_FRAMES = 8;
    private static final int MELEE_COOLDOWN = 30;
    private static final int MAX_JUMPS = 2;
    private static final int JUMP_BUFFER_FRAMES = 8;
    private static final int COYOTE_FRAMES = 6;
    private static final double MAX_WALK_STEP = 26;
    private static final double SURFACE_EPSILON = 0.001;

    public Player(int startX, int startY) {
        this.x = startX;
        this.y = startY;
        this.runBuild = new RunBuild(WeaponId.COMMIT_CANNON);
        this.fireController = new WeaponFireController();
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getSudoTimer() { return sudoTimer; }
    public int getShieldTimer() { return shieldTimer; }
    public int getInvincibleTimer() { return invincibleTimer; }
    public int getDashCooldown() { return dashCooldown; }
    public boolean isDashing() { return dashTimer > 0; }
    public boolean isMeleeActive() { return meleeTimer > 0; }
    public int getFacingDir() { return facingDir; }
    public WeaponType getWeapon() { return currentWeapon; }
    public int getWeaponAmmo() { return weaponAmmo; }
    public boolean isDebugMode() { return debugMode; }
    public boolean isUsingTemporaryWeapon() { return temporaryWeapon; }
    public int getJumpsRemaining() { return jumpsRemaining; }
    public RunBuild getRunBuild() { return runBuild; }
    public boolean isGrounded() { return grounded; }
    public double getVerticalVelocity() { return dy; }
    public int getMeleeTicksRemaining() { return meleeTimer; }
    /** Progress through the real melee window, from the trigger to its final tick. */
    public double getMeleeProgress() {
        return 1.0 - Math.max(0, Math.min(MELEE_FRAMES, meleeTimer)) / (double) MELEE_FRAMES;
    }
    public long getShotSequence() { return shotSequence; }
    /** Null until the first volley; records the emitted weapon even after temporary ammo runs out. */
    public WeaponId getLastFiredWeaponId() { return lastFiredWeaponId; }
    public int getDashCacheTicks() { return dashCacheTicks; }
    public boolean canReflectProjectile() { return meleeTimer > 0 && meleeReflectionsRemaining > 0; }
    public boolean tryConsumeMeleeReflection() {
        if (!canReflectProjectile()) return false;
        meleeReflectionsRemaining--;
        return true;
    }
    public int getRapidHeat() { return rapidHeat; }
    public int getBeamChargeTicks() { return beamCharge; }
    public void setCombatSeed(long seed) { combatRandom.setSeed(seed); }

    public record Checkpoint(int hp, int lives, int bombs, int sudoTicks, int shieldTicks,
                             int invincibleTicks, int cooldown, int dashCooldown, int meleeCooldown,
                             boolean temporaryWeapon, WeaponType weapon, int ammo,
                             Map<WeaponType, Integer> ammoReserve, int shieldRebootsUsed,
                             int rapidHeat, long combatRandomState) {
        public Checkpoint {
            Objects.requireNonNull(weapon, "weapon");
            ammoReserve = Map.copyOf(Objects.requireNonNull(ammoReserve, "ammoReserve"));
            if (hp <= 0 || hp > 100 || lives <= 0 || lives > 99 || bombs < 0 || bombs > 5
                    || ammo < 0 || ammo > 1_000_000 || shieldRebootsUsed < 0
                    || rapidHeat < 0 || rapidHeat > 100 || !CombatRandom.isValidState(combatRandomState)) {
                throw new IllegalArgumentException("Invalid player checkpoint resources");
            }
            for (int timer : new int[]{sudoTicks, shieldTicks, invincibleTicks, cooldown, dashCooldown, meleeCooldown}) {
                if (timer < 0 || timer > 36_000) throw new IllegalArgumentException("Invalid checkpoint timer");
            }
            for (int rounds : ammoReserve.values()) {
                if (rounds < 0 || rounds > 1_000_000) throw new IllegalArgumentException("Invalid ammo reserve");
            }
            if (!temporaryWeapon && ammo != 0) throw new IllegalArgumentException("Core weapon has no temporary ammo");
            if (temporaryWeapon && ammoReserve.getOrDefault(weapon, -1) != ammo) {
                throw new IllegalArgumentException("Equipped ammo and its reserve disagree");
            }
        }
    }

    /** Capture only after beginNextLevel has normalized the safe entry pose. */
    public Checkpoint checkpoint() {
        if (debugMode || dashTimer != 0 || meleeTimer != 0 || beamCharge != 0 || dashCacheTicks != 0 || dy != 0) {
            throw new IllegalStateException("Player is not at a safe checkpoint boundary");
        }
        return checkpointForNextLevel();
    }

    /** Capture survivor resources without moving the actor on its completed-level screen.
     * The restored entrance clears motion exactly as beginNextLevel does. */
    public Checkpoint checkpointForNextLevel() {
        if (debugMode) throw new IllegalStateException("Lab resources cannot be checkpointed");
        return new Checkpoint(hp, lives, bombs, sudoTimer, shieldTimer, invincibleTimer,
                cooldown, dashCooldown, meleeCooldown, temporaryWeapon, currentWeapon, weaponAmmo,
                ammoReserve, shieldRebootsUsed, rapidHeat, combatRandom.checkpointState());
    }

    /** Validates the complete checkpoint before mutating this player. Never restores Lab powers. */
    public void restoreCheckpoint(Checkpoint value, RunBuild build, double startX, double startY) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(build, "build");
        if (!Double.isFinite(startX) || !Double.isFinite(startY) || startX < 0 || startY < 0
                || startX > 10_000_000 || startY > 10_000_000
                || value.shieldRebootsUsed() > build.buildStats().shieldReboots()
                || !value.temporaryWeapon() && value.weapon() != WeaponCatalog.toLegacy(build.weapon())) {
            throw new IllegalArgumentException("Checkpoint does not match the build or safe position");
        }
        runBuild = build;
        x = startX; y = startY; dy = 0; grounded = false;
        jumpsRemaining = MAX_JUMPS; jumpBufferTimer = 0; coyoteTimer = 0;
        dashTimer = 0; dashVx = 0; meleeTimer = 0; beamCharge = 0; facingDir = 1;
        hp = value.hp(); lives = value.lives(); bombs = value.bombs();
        sudoTimer = value.sudoTicks(); shieldTimer = value.shieldTicks(); invincibleTimer = value.invincibleTicks();
        cooldown = value.cooldown(); dashCooldown = value.dashCooldown(); meleeCooldown = value.meleeCooldown();
        temporaryWeapon = value.temporaryWeapon(); currentWeapon = value.weapon(); weaponAmmo = value.ammo();
        ammoReserve.clear(); ammoReserve.putAll(value.ammoReserve());
        shieldRebootsUsed = value.shieldRebootsUsed(); rapidHeat = value.rapidHeat();
        combatRandom.restoreState(value.combatRandomState());
        shotSequence = 0; lastFiredWeaponId = null; debugMode = false;
        dashCacheTicks = meleeReflectionsRemaining = 0;
    }

    public void toggleDebugMode() {
        setDebugMode(!debugMode);
    }
    public void setDebugMode(boolean enabled) {
        debugMode = enabled;
        if (debugMode && temporaryWeapon) weaponAmmo = Math.max(weaponAmmo, 999);
    }
    public int getBombs() { return bombs; }
    public int getLives() { return lives; }

    public boolean loseLife() {
        if (lives <= 0) return false;
        lives--;
        return lives > 0;
    }

    public boolean useBomb() {
        if (debugMode) return true;
        if (bombs <= 0) return false;
        bombs--;
        return true;
    }
    public void addBomb() { if (bombs < 5) bombs++; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; this.dy = 0; }
    public void setSudoTimer(int t) { this.sudoTimer = t; }
    public void setShieldTimer(int t) { this.shieldTimer = t; }
    public void setInvincibleTimer(int t) { this.invincibleTimer = Math.max(0, t); }
    public void setRunBuild(RunBuild value) {
        RunBuild previous = this.runBuild;
        this.runBuild = Objects.requireNonNull(value, "value");
        if (previous != value) { shieldRebootsUsed = 0; beamCharge = 0; }
        if (!temporaryWeapon) currentWeapon = coreWeaponType();
    }

    public void bindRunBuild(RunBuild value) {
        setRunBuild(value);
    }

    public void giveWeapon(WeaponType weapon, int ammo) {
        this.currentWeapon = Objects.requireNonNull(weapon, "weapon");
        this.weaponAmmo = Math.max(0, ammo);
        this.beamCharge = 0;
        this.temporaryWeapon = true;
        ammoReserve.put(weapon, weaponAmmo);
    }

    /**
     * Queues a jump briefly. Keeping input intent separate from the simulation makes the
     * controls forgiving without turning a held key into an automatic bunny-hop.
     */
    public void requestJump() {
        jumpBufferTimer = JUMP_BUFFER_FRAMES;
    }

    private final Map<WeaponType, Integer> ammoReserve = new HashMap<>();

    public void cycleWeapon() {
        beamCharge = 0;
        if (temporaryWeapon && weaponAmmo > 0) ammoReserve.put(currentWeapon, weaponAmmo);
        WeaponType[] all = WeaponType.values();
        int idx = 0;
        for (int i = 0; i < all.length; i++)
            if (all[i] == currentWeapon) { idx = i; break; }
        WeaponType core = coreWeaponType();
        for (int i = 0; i < all.length; i++) {
            int next = (idx + 1 + i) % all.length;
            WeaponType candidate = all[next];
            if (candidate == core) {
                restoreCoreWeapon();
                return;
            }
            int reserve = ammoReserve.getOrDefault(candidate, 0);
            if (reserve > 0) {
                currentWeapon = candidate;
                weaponAmmo = reserve;
                temporaryWeapon = true;
                return;
            }
        }
        restoreCoreWeapon();
    }

    public boolean isBuffExpiring(int timer) {
        return timer > 0 && timer < 180; // last 3 seconds
    }

    public void reset(int startX, int startY) {
        this.x = startX; this.y = startY;
        this.hp = 100; this.dy = 0;
        this.sudoTimer = 0; this.shieldTimer = 0; this.invincibleTimer = 0;
        this.cooldown = 0; this.dashTimer = 0; this.dashCooldown = 0;
        this.meleeTimer = 0; this.meleeCooldown = 0;
        this.bombs = 3; this.facingDir = 1;
        this.lives = 3;
        this.grounded = false;
        this.jumpsRemaining = MAX_JUMPS;
        this.jumpBufferTimer = 0;
        this.coyoteTimer = 0;
        this.temporaryWeapon = false;
        if (runBuild == null) this.runBuild = new RunBuild(WeaponId.COMMIT_CANNON);
        this.currentWeapon = coreWeaponType();
        this.weaponAmmo = 0;
        this.debugMode = false;
        this.shieldRebootsUsed = 0;
        this.ammoReserve.clear();
        this.rapidHeat = 0;
        this.beamCharge = 0;
        this.shotSequence = 0;
        this.lastFiredWeaponId = null;
        this.dashCacheTicks = this.meleeReflectionsRemaining = 0;
    }

    /** Moves a campaign survivor to a new level without restoring spent resources or build charges. */
    public void beginNextLevel(int startX, int startY) {
        x = startX;
        y = startY;
        dy = 0;
        grounded = false;
        jumpsRemaining = MAX_JUMPS;
        jumpBufferTimer = 0;
        coyoteTimer = 0;
        dashTimer = 0;
        dashVx = 0;
        meleeTimer = 0;
        beamCharge = 0;
        dashCacheTicks = meleeReflectionsRemaining = 0;
        facingDir = 1;
        shotSequence = 0;
        lastFiredWeaponId = null;
        // Cooldowns, buffs, ammo, lives, HP, bombs and spent shield reboots remain part of the run.
    }

    /** Returns false for permanent upgrades; supplies never alter permanent build ranks. */
    public boolean applySupply(UpgradeId id) {
        return switch (Objects.requireNonNull(id, "id")) {
            case SUPPLY_REPAIR -> { heal(25); yield true; }
            case SUPPLY_BOMB -> { addBomb(); yield true; }
            case SUPPLY_SHIELD -> { shieldTimer = Math.max(shieldTimer, 180); yield true; }
            default -> false;
        };
    }

    public void dash() { dash(facingDir); }

    public void dash(int direction) {
        if (dashCooldown > 0 || dashTimer > 0) return;
        beamCharge = 0;
        int dir = direction >= 0 ? 1 : -1;
        dashTimer = DASH_FRAMES;
        dashVx = dir * DASH_SPEED;
        facingDir = dir;
        invincibleTimer = Math.max(invincibleTimer, DASH_FRAMES + 8);
    }

    public void melee() {
        if (meleeCooldown > 0 || meleeTimer > 0) return;
        beamCharge = 0;
        meleeTimer = MELEE_FRAMES;
        meleeCooldown = MELEE_COOLDOWN;
        meleeReflectionsRemaining = 3;
    }

    public Rectangle getMeleeBounds() {
        if (meleeTimer <= 0) return null;
        int mx = facingDir > 0 ? (int) x + width : (int) x - 50;
        return new Rectangle(mx, (int) y - 10, 50, height + 20);
    }

    public void heal(int amount) {
        hp = (int) Math.min((long) Math.max(0, hp) + Math.max(0, amount), maxHp);
    }

    public void update(boolean left, boolean right, boolean jump, boolean shoot,
                        int groundY, double levelWidth, List<Projectile> projectiles,
                        List<Platform> platforms) {
        update(left, right, jump, shoot, groundY, levelWidth, projectiles, platforms,
                0, Math.max(0, levelWidth - width));
    }

    /** Horizontal limits describe allowed player origins, intersected with the world bounds.
     * Entry/respawn corrections happen before measuring movement used by a new projectile. */
    public void update(boolean left, boolean right, boolean jump, boolean shoot,
                       int groundY, double levelWidth, List<Projectile> projectiles,
                       List<Platform> platforms, double minX, double maxX) {
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(levelWidth)
                || minX > maxX || levelWidth < 0) throw new IllegalArgumentException("Invalid horizontal movement bounds");
        double worldRight = Math.max(0, levelWidth - width);
        double leftLimit = Math.max(0, Math.min(worldRight, minX));
        double rightLimit = Math.max(leftLimit, Math.min(worldRight, maxX));
        x = Math.max(leftLimit, Math.min(rightLimit, x));
        // Kept for callers that use the original update contract. The game panel uses
        // requestJump() on the key press so a held Space key does not keep jumping.
        if (jump) requestJump();
        if (dashCacheTicks > 0) dashCacheTicks--;

        if (dashTimer > 0) {
            x += dashVx;
            dashTimer--;
            if (dashTimer == 0) {
                dashCooldown = Math.max(12, (int) Math.round(DASH_COOLDOWN_MAX
                        * runBuild.buildStats().dashCooldownMultiplier()));
                if (runBuild.ranks().getOrDefault(UpgradeId.DASH_CACHE, 0) > 0) dashCacheTicks = 90;
            }
            x = Math.max(leftLimit, Math.min(rightLimit, x));
            dy = 0;
            return;
        }

        double previousX = x;
        double previousBottom = y + height;
        boolean canWalkStep = grounded && dy >= 0 && jumpBufferTimer == 0
                && hasSupportAt(previousX, previousBottom, groundY, platforms);
        if (left) { x -= SPEED; facingDir = -1; }
        if (right) { x += SPEED; facingDir = 1; }

        x = Math.max(leftLimit, Math.min(rightLimit, x));

        dy += GRAVITY;
        y += dy;

        // Compare all downward crossings before changing velocity or snapping position. A thick
        // platform must not catch someone beneath it, and a fast fall must not skip a thin deck.
        double landingTop = Double.POSITIVE_INFINITY;
        if (dy >= 0) {
            double currentBottom = y + height;
            if (currentBottom >= groundY) landingTop = groundY;
            for (Platform p : platforms) {
                if (x + width <= p.x || x >= p.x + p.width) continue;
                boolean crossedTop = previousBottom <= p.y + SURFACE_EPSILON && currentBottom >= p.y;
                double stepHeight = previousBottom - p.y;
                boolean enteredFromSide = x > previousX && previousX + width <= p.x + SURFACE_EPSILON
                        || x < previousX && previousX >= p.x + p.width - SURFACE_EPSILON;
                boolean walkedUpStep = canWalkStep && enteredFromSide
                        && stepHeight > SURFACE_EPSILON && stepHeight <= MAX_WALK_STEP;
                if (crossedTop || walkedUpStep) landingTop = Math.min(landingTop, p.y);
            }
        }
        boolean landed = Double.isFinite(landingTop);
        if (landed) { y = landingTop - height; dy = 0; }
        if (landed) {
            grounded = true;
            jumpsRemaining = MAX_JUMPS;
            coyoteTimer = COYOTE_FRAMES;
        } else {
            if (grounded) coyoteTimer = COYOTE_FRAMES;
            grounded = false;
            if (coyoteTimer > 0) coyoteTimer--;
        }

        if (jumpBufferTimer > 0) {
            boolean canGroundJump = grounded || coyoteTimer > 0;
            if (canGroundJump || jumpsRemaining > 0) {
                dy = JUMP_FORCE;
                grounded = false;
                jumpsRemaining = Math.max(0, jumpsRemaining - 1);
                coyoteTimer = 0;
                jumpBufferTimer = 0;
            } else {
                jumpBufferTimer--;
            }
        }

        if (cooldown > 0) cooldown--;
        if (sudoTimer > 0) sudoTimer--;
        if (shieldTimer > 0) shieldTimer--;
        if (invincibleTimer > 0) invincibleTimer--;
        if (dashCooldown > 0) dashCooldown--;
        if (meleeTimer > 0) meleeTimer--;
        if (meleeCooldown > 0) meleeCooldown--;

        if (!shoot || currentWeapon != WeaponType.RAPID) rapidHeat = Math.max(0, rapidHeat - 2);
        if (!shoot || currentWeapon != WeaponType.LASER || meleeTimer > 0 || sudoTimer > 0) beamCharge = 0;
        if (shoot && cooldown <= 0 && meleeTimer <= 0) {
            WeaponType w = (sudoTimer > 0) ? WeaponType.SPREAD : currentWeapon;

            // Check ammo BEFORE firing
            if (sudoTimer <= 0 && !debugMode && temporaryWeapon && weaponAmmo <= 0) {
                restoreCoreWeapon();
                w = currentWeapon;
            }

            double bulletX = (facingDir > 0) ? x + width : x;
            WeaponId weaponId = WeaponCatalog.fromLegacy(w);
            boolean usesCore = runBuild.weapon() == weaponId;
            boolean evolved = usesCore && runBuild.evolved();
            CombatStats stats = usesCore ? runBuild.effectiveStats() : WeaponCatalog.definition(weaponId).baseStats();
            if (weaponId == WeaponId.REFACTOR_BEAM) {
                int requiredCharge = evolved ? 6 : 10;
                beamCharge = Math.min(requiredCharge, beamCharge + 1);
                if (beamCharge < requiredCharge) return;
            }
            boolean heatBurst = weaponId == WeaponId.RAPID_CI && rapidHeat + 14 >= 100;
            if (heatBurst && evolved) {
                stats = stats.withPelletsAndSpread(3, 0.18).withRicochets(stats.ricochets() + 2);
            }
            if (dashCacheTicks > 0) stats = stats.withCriticalChance(1).withPierces(stats.pierces() + 1);
            FireRequest request = new FireRequest(weaponId, bulletX, y + height / 2.0,
                    facingDir, stats, sudoTimer > 0, combatRandom, evolved, x - previousX);
            if (!fireController.fireInto(request, projectiles)) return;
            if (weaponId == WeaponId.REFACTOR_BEAM) beamCharge = 0;
            dashCacheTicks = 0;
            lastFiredWeaponId = weaponId;
            shotSequence++;
            cooldown = stats.cooldownFrames();
            if (weaponId == WeaponId.REFACTOR_BEAM) cooldown++;
            if (weaponId == WeaponId.RAPID_CI) {
                rapidHeat += 14;
                if (heatBurst) {
                    rapidHeat = evolved ? 28 : 0;
                    cooldown = Math.max(cooldown, evolved ? 14 : 32);
                }
            }

            if (sudoTimer <= 0 && !debugMode && temporaryWeapon && weaponAmmo > 0) {
                weaponAmmo--;
                if (weaponAmmo <= 0) {
                    ammoReserve.remove(currentWeapon);
                    restoreCoreWeapon();
                } else ammoReserve.put(currentWeapon, weaponAmmo);
            }
        }
    }

    private boolean hasSupportAt(double left, double bottom, int groundY, List<Platform> platforms) {
        if (Math.abs(bottom - groundY) <= SURFACE_EPSILON) return true;
        for (Platform p : platforms) {
            if (left + width > p.x && left < p.x + p.width
                    && Math.abs(bottom - p.y) <= SURFACE_EPSILON) return true;
        }
        return false;
    }

    public void takeDamage(int amount) {
        if (debugMode || shieldTimer > 0 || invincibleTimer > 0) return;
        int damage = Math.max(0, amount);
        int availableReboots = runBuild.buildStats().shieldReboots();
        if (damage >= hp && shieldRebootsUsed < availableReboots) {
            shieldRebootsUsed++;
            hp = 25;
            shieldTimer = 120;
            invincibleTimer = 90;
            return;
        }
        hp = Math.max(0, hp - damage);
        invincibleTimer = 60;
    }

    public int getShieldRebootsRemaining() {
        return Math.max(0, runBuild.buildStats().shieldReboots() - shieldRebootsUsed);
    }

    private WeaponType coreWeaponType() {
        return runBuild == null ? WeaponType.COMMIT : WeaponCatalog.toLegacy(runBuild.weapon());
    }

    private void restoreCoreWeapon() {
        temporaryWeapon = false;
        currentWeapon = coreWeaponType();
        weaponAmmo = 0;
    }

    public void draw(Graphics2D g) {
        if (invincibleTimer > 0 && !isDashing() && !isMeleeActive() && (invincibleTimer / 4) % 2 == 0) return;

        Composite originalComposite = g.getComposite();

        if (isDashing() && dashTimer < DASH_FRAMES - 1) {
            for (int i = 1; i <= 3; i++) {
                float alpha = 0.15f * (4 - i);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setColor(sudoTimer > 0 ? GameColors.SUDO_YELLOW : GameColors.PLAYER);
                int trailX = (int) (x - dashVx * i * 0.6);
                g.fillRoundRect(trailX, (int) y, width, height, 8, 8);
            }
            g.setComposite(originalComposite);
        }

        g.setColor(sudoTimer > 0 ? GameColors.SUDO_YELLOW : GameColors.PLAYER);
        g.fillRoundRect((int) x, (int) y, width, height, 8, 8);

        g.setColor(sudoTimer <= 0 ? Color.WHITE : Color.BLACK);
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 20)));
        String dirSymbol = facingDir > 0 ? "J>" : "<J";
        GameText.draw(g, dirSymbol, (int) x + 3, (int) y + 22);

        if (shieldTimer > 0) {
            g.setColor(GameColors.SHIELD_CYAN);
            g.setStroke(new BasicStroke(2));
            g.drawOval((int) x - 8, (int) y - 8, width + 16, height + 16);
        }

        // Melee slash effect
        if (meleeTimer > 0) {
            g.setComposite(originalComposite);
            float alpha = meleeTimer / (float) MELEE_FRAMES;
            g.setColor(new Color(1f, 1f, 1f, alpha * 0.7f));
            int sx = facingDir > 0 ? (int) x + width : (int) x - 50;
            g.setStroke(new BasicStroke(3));
            g.drawArc(sx, (int) y - 20, 50, height + 40, facingDir > 0 ? -60 : 120, 120);
        }
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }
}
