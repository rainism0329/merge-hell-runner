package com.bigphil.mergehell.boss;

import java.util.List;

public record BossSnapshot(
        BossPhase phase,
        int coreHp,
        int maxCoreHp,
        List<NodeView> nodes,
        int transitionTicks,
        BossAction telegraph) {
    public BossSnapshot { nodes = List.copyOf(nodes); }

    public record NodeView(int id, double x, double y, int hp, int maxHp, boolean alive) { }
}
