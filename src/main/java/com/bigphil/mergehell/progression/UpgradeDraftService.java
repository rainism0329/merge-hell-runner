package com.bigphil.mergehell.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class UpgradeDraftService {
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
        List<UpgradeDefinition> available = eligible(build);
        List<UpgradeDefinition> relevant = available.stream()
                .filter(definition -> definition.isWeaponRelevant(build.weapon()))
                .sorted(Comparator.comparing(UpgradeDefinition::id))
                .toList();
        List<UpgradeDefinition> globals = available.stream()
                .filter(definition -> definition.tag() != UpgradeTag.WEAPON)
                .filter(definition -> definition.tag() != UpgradeTag.EVOLUTION_CORE)
                .sorted(Comparator.comparing(UpgradeDefinition::id))
                .toList();
        List<UpgradeDefinition> result = new ArrayList<>(3);
        if (!relevant.isEmpty()) result.add(chooseRelevant(relevant, previousIds));
        addCandidates(result, globals, previousIds);
        // Exhausting global modules must not hide remaining weapon progression.
        addCandidates(result, relevant, previousIds);
        // A completely developed build keeps receiving an explicit, repeatable supply choice.
        addCandidates(result, UpgradeCatalog.supplies(), previousIds);
        return List.copyOf(result);
    }

    private List<UpgradeDefinition> eligible(RunBuild build) {
        return UpgradeCatalog.all().stream()
                .filter(definition -> !definition.isSupply())
                .filter(definition -> definition.tag() != UpgradeTag.WEAPON
                        && definition.tag() != UpgradeTag.EVOLUTION_CORE
                        || definition.isWeaponRelevant(build.weapon()))
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

    private void addCandidates(List<UpgradeDefinition> result,
            List<UpgradeDefinition> candidates, Set<UpgradeId> previousIds) {
        List<UpgradeDefinition> fresh = new ArrayList<>();
        List<UpgradeDefinition> repeated = new ArrayList<>();
        for (UpgradeDefinition candidate : candidates) {
            if (result.stream().anyMatch(card -> card.id() == candidate.id())) continue;
            (previousIds.contains(candidate.id()) ? repeated : fresh).add(candidate);
        }
        shuffle(fresh);
        shuffle(repeated);
        fresh.addAll(repeated);
        for (UpgradeDefinition candidate : fresh) {
            if (result.size() == 3) break;
            result.add(candidate);
        }
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
