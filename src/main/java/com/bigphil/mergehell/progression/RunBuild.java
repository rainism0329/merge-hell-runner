package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.combat.CombatStats;
import com.bigphil.mergehell.combat.WeaponCatalog;
import com.bigphil.mergehell.combat.WeaponId;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class RunBuild {
    public record Checkpoint(WeaponId weapon, Map<UpgradeId, Integer> ranks, int weaponLevel,
                             boolean evolutionCoreInstalled, boolean evolved, CharacterId character, GameDifficulty difficulty) {
        public Checkpoint(WeaponId weapon, Map<UpgradeId,Integer> ranks, int weaponLevel,
                          boolean evolutionCoreInstalled,boolean evolved) {
            this(weapon,ranks,weaponLevel,evolutionCoreInstalled,evolved,CharacterId.REPAIR,GameDifficulty.STANDARD);
        }
        public Checkpoint {
            Objects.requireNonNull(weapon, "weapon");
            Objects.requireNonNull(character,"character"); Objects.requireNonNull(difficulty,"difficulty");
            ranks = Map.copyOf(Objects.requireNonNull(ranks, "ranks"));
            if (weaponLevel < 1 || weaponLevel > 5) throw new IllegalArgumentException("Invalid weapon level");
        }
    }
    private final WeaponId weapon;
    private CharacterId character=CharacterId.REPAIR;
    private GameDifficulty difficulty=GameDifficulty.STANDARD;
    public CharacterId character() { return character; }
    public GameDifficulty difficulty() { return difficulty; }
    public void setIdentity(CharacterId character, GameDifficulty difficulty) {
        this.character=Objects.requireNonNull(character);this.difficulty=Objects.requireNonNull(difficulty);
    }
    private final EnumMap<UpgradeId, Integer> ranks = new EnumMap<>(UpgradeId.class);
    private BuildStats buildStats;
    private int weaponLevel = 1;
    private boolean evolutionCoreInstalled;
    private boolean evolved;

    public RunBuild(WeaponId weapon) {
        this.weapon = Objects.requireNonNull(weapon, "weapon");
        this.buildStats = BuildStats.base(WeaponCatalog.definition(weapon).baseStats());
    }

    public void apply(UpgradeDefinition upgrade) {
        Objects.requireNonNull(upgrade, "upgrade");
        // Supplies are consumed by Player; they do not occupy a permanent rank or weapon level.
        if (upgrade.isSupply()) return;
        if ((upgrade.tag() == UpgradeTag.WEAPON
                || upgrade.tag() == UpgradeTag.EVOLUTION_CORE)
                && !upgrade.isWeaponRelevant(weapon)) {
            throw new IllegalArgumentException(
                    "upgrade " + upgrade.id() + " does not support " + weapon);
        }

        int rank = ranks.getOrDefault(upgrade.id(), 0);
        if (rank >= upgrade.maxRank()) {
            throw new IllegalStateException("upgrade is max rank");
        }

        buildStats = Objects.requireNonNull(upgrade.effect().apply(buildStats),
                "upgrade effect result");
        ranks.put(upgrade.id(), rank + 1);
        if (upgrade.tag() == UpgradeTag.WEAPON) {
            weaponLevel = Math.min(5, weaponLevel + 1);
        }
        if (upgrade.tag() == UpgradeTag.EVOLUTION_CORE) {
            evolutionCoreInstalled = true;
        }
    }

    public boolean tryEvolve() {
        if (!evolutionReady()) {
            return false;
        }

        evolved = true;
        return true;
    }

    private CombatStats evolvedStats(CombatStats current) {
        return switch (weapon) {
            case COMMIT_CANNON -> new CombatStats(current.damage(), current.cooldownFrames(), current.pellets(),
                        current.speed(), current.spreadRadians(), current.pierces(),
                        current.knockback(), Math.min(1, current.criticalChance() + 0.15),
                        current.ricochets() + 2);
            case FORCE_PUSH -> new CombatStats(current.damage(), current.cooldownFrames(),
                        current.pellets() + 4, current.speed(), current.spreadRadians(),
                        current.pierces() + 1, current.knockback() + 12,
                        current.criticalChance(), current.ricochets());
            case RAPID_CI -> current.withPierces(current.pierces() + 1);
            case GARBAGE_COLLECTOR -> current.withPierces(current.pierces() + 2);
            case FIREWALL -> current.withPierces(current.pierces() + 2);
            case REFACTOR_BEAM -> current.withPelletsAndSpread(current.pellets() + 2, 0.10)
                    .withRicochets(current.ricochets() + 2);
        };
    }

    public WeaponId weapon() {
        return weapon;
    }

    public CombatStats effectiveStats() {
        return evolved ? evolvedStats(buildStats.combat()) : buildStats.combat();
    }

    public BuildStats buildStats() {
        return evolved ? buildStats.withCombat(effectiveStats()) : buildStats;
    }

    public int rank(UpgradeId id) {
        return ranks.getOrDefault(Objects.requireNonNull(id, "id"), 0);
    }

    public int weaponLevel() {
        return weaponLevel;
    }

    public boolean evolved() {
        return evolved;
    }

    public boolean evolutionReady() { return !evolved && weaponLevel >= 5 && evolutionCoreInstalled; }

    public Map<UpgradeId, Integer> ranks() { return Map.copyOf(ranks); }

    public Checkpoint checkpoint() {
        return new Checkpoint(weapon, ranks, weaponLevel, evolutionCoreInstalled, evolved, character, difficulty);
    }

    /** Rebuild once from base stats; evolution remains a derived modifier, never a repeated bonus. */
    public static RunBuild restoreCheckpoint(Checkpoint value) {
        Objects.requireNonNull(value, "value");
        RunBuild restored = new RunBuild(value.weapon());
        restored.setIdentity(value.character(),value.difficulty());
        for (UpgradeId id : UpgradeId.values()) {
            Integer rank = value.ranks().get(id);
            if (rank == null) continue;
            UpgradeDefinition definition = UpgradeCatalog.definition(id);
            if (definition.isSupply() || rank <= 0 || rank > definition.maxRank()) {
                throw new IllegalArgumentException("Invalid saved rank for " + id);
            }
            for (int i = 0; i < rank; i++) restored.apply(definition);
        }
        if (restored.weaponLevel != value.weaponLevel()
                || restored.evolutionCoreInstalled != value.evolutionCoreInstalled()
                || value.evolved() && !restored.tryEvolve()) {
            throw new IllegalArgumentException("Saved evolution does not match the build");
        }
        return restored;
    }
}
