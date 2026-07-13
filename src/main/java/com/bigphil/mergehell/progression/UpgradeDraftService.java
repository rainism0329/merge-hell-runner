package com.bigphil.mergehell.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class UpgradeDraftService {
    private static final int GLOBAL_CARD_COUNT = 2;

    private final RandomGenerator random;
    private List<UpgradeDefinition> lastDraft = List.of();
    private boolean rerollUsed;

    public UpgradeDraftService(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public List<UpgradeDefinition> draft(RunBuild build) {
        Objects.requireNonNull(build, "build");
        rerollUsed = false;
        lastDraft = createDraft(build, Set.of());
        return lastDraft;
    }

    public List<UpgradeDefinition> reroll(RunBuild build) {
        Objects.requireNonNull(build, "build");
        if (lastDraft.isEmpty()) {
            throw new IllegalStateException("draft is required before reroll");
        }
        if (rerollUsed) {
            throw new IllegalStateException("reroll has already been used");
        }

        rerollUsed = true;
        Set<UpgradeId> previousIds = new HashSet<>();
        for (UpgradeDefinition definition : lastDraft) {
            previousIds.add(definition.id());
        }
        lastDraft = createDraft(build, previousIds);
        return lastDraft;
    }

    private List<UpgradeDefinition> createDraft(
            RunBuild build, Set<UpgradeId> previousIds) {
        List<UpgradeDefinition> relevant = eligible(build).stream()
                .filter(definition -> definition.isWeaponRelevant(build.weapon()))
                .sorted(Comparator.comparing(UpgradeDefinition::id))
                .toList();
        List<UpgradeDefinition> globals = eligible(build).stream()
                .filter(definition -> definition.tag() != UpgradeTag.WEAPON)
                .filter(definition -> definition.tag() != UpgradeTag.EVOLUTION_CORE)
                .sorted(Comparator.comparing(UpgradeDefinition::id))
                .toList();
        int requiredGlobals = relevant.isEmpty() ? 3 : GLOBAL_CARD_COUNT;
        if (globals.size() < requiredGlobals) {
            throw new IllegalStateException("not enough global upgrades are available");
        }

        UpgradeDefinition weaponCard = relevant.isEmpty()
                ? chooseRelevant(globals, previousIds) : chooseRelevant(relevant, previousIds);
        List<UpgradeDefinition> globalCards = chooseGlobals(globals.stream()
                .filter(card -> card.id() != weaponCard.id()).toList(), previousIds);
        return List.of(weaponCard, globalCards.get(0), globalCards.get(1));
    }

    private List<UpgradeDefinition> eligible(RunBuild build) {
        return UpgradeCatalog.all().stream()
                .filter(definition -> build.rank(definition.id()) < definition.maxRank())
                .toList();
    }

    private UpgradeDefinition chooseRelevant(
            List<UpgradeDefinition> candidates, Set<UpgradeId> previousIds) {
        List<UpgradeDefinition> alternatives = candidates.stream()
                .filter(definition -> !previousIds.contains(definition.id()))
                .toList();
        List<UpgradeDefinition> pool = alternatives.isEmpty() ? candidates : alternatives;
        return pool.get(random.nextInt(pool.size()));
    }

    private List<UpgradeDefinition> chooseGlobals(
            List<UpgradeDefinition> candidates, Set<UpgradeId> previousIds) {
        List<UpgradeDefinition> fresh = new ArrayList<>();
        List<UpgradeDefinition> repeated = new ArrayList<>();
        for (UpgradeDefinition candidate : candidates) {
            (previousIds.contains(candidate.id()) ? repeated : fresh).add(candidate);
        }
        shuffle(fresh);
        shuffle(repeated);
        fresh.addAll(repeated);
        return List.copyOf(fresh.subList(0, GLOBAL_CARD_COUNT));
    }

    private void shuffle(List<UpgradeDefinition> values) {
        for (int i = values.size() - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            UpgradeDefinition value = values.get(i);
            values.set(i, values.get(swapIndex));
            values.set(swapIndex, value);
        }
    }
}
