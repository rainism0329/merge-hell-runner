package com.bigphil.mergehell.boss;

import com.bigphil.mergehell.model.EntityType;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class LegacyBossController {
    private final RandomGenerator random;
    private final int maxCoreHp;
    private final List<DependencyNode> nodes;
    private int coreHp;
    private BossPhase phase = BossPhase.DEPENDENCIES;
    private int transitionTicks;
    private int attackCooldown = 60;
    private int telegraphRemaining;
    private int attackSequence;
    private BossAction pendingAction;
    private double originX = 680;
    private double originY = 180;

    public LegacyBossController(RandomGenerator random, int coreHp) {
        this.random = Objects.requireNonNull(random, "random");
        if (coreHp <= 0) throw new IllegalArgumentException("coreHp must be positive");
        this.maxCoreHp = coreHp;
        this.coreHp = coreHp;
        int nodeHp = Math.max(30, coreHp / 10);
        nodes = List.of(new DependencyNode(0, nodeHp), new DependencyNode(1, nodeHp),
                new DependencyNode(2, nodeHp));
    }

    public List<BossAction> tick(double playerX, double playerY) {
        if (phase == BossPhase.DEFEATED) return List.of();
        if (transitionTicks > 0) {
            transitionTicks--;
            return List.of();
        }
        if (pendingAction != null) {
            if (--telegraphRemaining <= 0) {
                BossAction action = pendingAction;
                pendingAction = null;
                attackCooldown = phase == BossPhase.ENRAGED ? 55 : 85;
                return List.of(action);
            }
            return List.of();
        }
        if (--attackCooldown > 0) return List.of();

        attackSequence++;
        if (attackSequence % 5 == 0) {
            attackCooldown = phase == BossPhase.ENRAGED ? 28 : 48;
            return List.of(new BossAction.Spawn(
                    phase == BossPhase.ENRAGED ? EntityType.LOCK : EntityType.CONFLICT,
                    phase == BossPhase.ENRAGED ? 3 : 2));
        }
        int telegraph = phase == BossPhase.ENRAGED
                ? 24 + random.nextInt(9) : 34 + random.nextInt(13);
        pendingAction = switch (attackSequence % 4) {
            case 0 -> new BossAction.Shockwave(telegraph,
                    phase == BossPhase.ENRAGED ? 4 : 3,
                    phase == BossPhase.ENRAGED ? 10.5 : 8.5);
            case 1, 3 -> new BossAction.Laser(telegraph, nearestLane(playerY),
                    phase == BossPhase.ENRAGED ? 54 : 46,
                    phase == BossPhase.ENRAGED ? 34 : 24);
            default -> new BossAction.Volley(telegraph, playerX, playerY,
                    phase == BossPhase.ENRAGED ? 7 : 5,
                    phase == BossPhase.ENRAGED ? 9.0 : 7.0);
        };
        telegraphRemaining = telegraph;
        return List.of();
    }

    public void damageNode(int id, int amount) {
        DependencyNode node = nodes.stream().filter(n -> n.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown node: " + id));
        node.damage(amount);
        if (phase == BossPhase.DEPENDENCIES && nodes.stream().noneMatch(DependencyNode::alive)) {
            phase = BossPhase.CORE_EXPOSED;
            transitionTicks = 60;
            cancelTelegraph();
        }
    }

    public int damageCore(int amount) {
        if (amount <= 0 || phase == BossPhase.DEPENDENCIES || phase == BossPhase.DEFEATED) return 0;
        int accepted = Math.min(coreHp, amount);
        coreHp -= accepted;
        if (coreHp == 0) {
            phase = BossPhase.DEFEATED;
            cancelTelegraph();
        } else if (phase == BossPhase.CORE_EXPOSED && coreHp <= maxCoreHp * 0.4) {
            phase = BossPhase.ENRAGED;
            transitionTicks = 60;
            cancelTelegraph();
        }
        return accepted;
    }

    private void cancelTelegraph() {
        pendingAction = null;
        telegraphRemaining = 0;
        attackCooldown = 45;
    }

    public void setOrigin(double x, double y) { originX = x; originY = y; }

    public BossSnapshot snapshot() {
        List<BossSnapshot.NodeView> views = new ArrayList<>();
        for (DependencyNode node : nodes) {
            double x = switch (node.id()) {
                case 0 -> originX - 82;
                case 1 -> originX - 146;
                default -> originX - 84;
            };
            double y = switch (node.id()) {
                case 0 -> originY + 18;
                case 1 -> originY + 112;
                default -> originY + 224;
            };
            views.add(new BossSnapshot.NodeView(node.id(), x, y, node.hp(), node.maxHp(), node.alive()));
        }
        return new BossSnapshot(phase, coreHp, maxCoreHp, views, transitionTicks, pendingAction);
    }

    public Rectangle nodeBounds(int id) {
        BossSnapshot.NodeView node = snapshot().nodes().stream().filter(n -> n.id() == id).findFirst()
                .orElseThrow();
        return new Rectangle((int) node.x(), (int) node.y(), 54, 54);
    }

    public Rectangle coreBounds() { return new Rectangle((int) originX, (int) originY, 160, 280); }
    public List<DependencyNode> nodes() { return List.copyOf(nodes); }
    public BossPhase phase() { return phase; }
    public int coreHp() { return coreHp; }
    public int maxCoreHp() { return maxCoreHp; }
    public int telegraphTicksRemaining() { return telegraphRemaining; }

    public List<Integer> damageAllNodes(int amount) {
        List<Integer> destroyed = new ArrayList<>();
        for (DependencyNode node : nodes) {
            if (!node.alive()) continue;
            node.damage(amount);
            if (!node.alive()) destroyed.add(node.id());
        }
        if (phase == BossPhase.DEPENDENCIES && nodes.stream().noneMatch(DependencyNode::alive)) {
            phase = BossPhase.CORE_EXPOSED;
            transitionTicks = 60;
            cancelTelegraph();
        }
        return List.copyOf(destroyed);
    }

    private double nearestLane(double playerY) {
        double[] lanes = {originY + 48, originY + 148, originY + 248};
        double playerCenter = playerY + 15;
        double nearest = lanes[0];
        for (double lane : lanes) {
            if (Math.abs(lane - playerCenter) < Math.abs(nearest - playerCenter)) nearest = lane;
        }
        return nearest;
    }
}
