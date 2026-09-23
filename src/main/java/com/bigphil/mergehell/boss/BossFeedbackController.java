package com.bigphil.mergehell.boss;

import com.bigphil.mergehell.combat.CombatEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Bounded presentation state driven only by confirmed damage and simulation ticks. */
public final class BossFeedbackController {
    public enum Kind { HIT, HEAVY_HIT, DRONE_HIT, BLOCKED, NODE_BREAK, GUARD_BREAK, PHASE, DEFEATED }
    public record Status(int hp, int maxHp, int stage, String stageLabel, boolean shielded,
                         boolean vulnerable, double x, double y, double width, double height) {
        public Status {
            Objects.requireNonNull(stageLabel, "stageLabel");
            if (hp < 0 || maxHp <= 0 || hp > maxHp || stage < 0 || !finite(x, y, width, height)
                    || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid boss status");
        }
    }
    public record ImpactView(CombatEvent.BossPart part, int partId, double x, double y, int damage,
                             boolean blocked, boolean critical, CombatEvent.DamageKind source, double progress) { }
    public record Banner(String text, Kind kind, double progress) { }
    public record Snapshot(int hp, int maxHp, double trailingHp, int stage, String stageLabel,
                           boolean shielded, boolean vulnerable, double bodyFlash, double recoil,
                           List<ImpactView> impacts, Banner banner,
                           double x, double y, double width, double height) {
        public Snapshot { impacts = List.copyOf(impacts); }
    }
    public record Signal(Kind kind, int hitstopTicks, int shakeTicks, int flashTicks) { }
    private record HitKey(long root, CombatEvent.BossPart part, int partId) { }
    private static final class Pulse {
        final CombatEvent.BossPart part;
        final int partId;
        final boolean blocked;
        final CombatEvent.DamageKind source;
        final double x, y;
        int damage, age;
        boolean critical;
        Pulse(CombatEvent.BossImpact hit) {
            part = hit.part(); partId = hit.partId(); blocked = hit.blocked(); source = hit.source();
            x = hit.x(); y = hit.y(); damage = hit.actualDamage(); critical = hit.critical();
        }
        ImpactView view() { return new ImpactView(part, partId, x, y, damage, blocked, critical, source, age / 24.0); }
    }

    private final List<Pulse> impacts = new ArrayList<>();
    private final LinkedHashMap<HitKey, Boolean> consumed = new LinkedHashMap<>();
    private final EnumMap<Kind, Signal> pending = new EnumMap<>(Kind.class);
    private Status status;
    private double trailingHp;
    private int trailDelay, flashTicks, recoilTicks, bannerTicks, bannerDuration;
    private Kind bannerKind;
    private String bannerText = "";
    private long clock, nextHitSignal, nextDroneSignal, nextBlockedSignal;

    public void reset() {
        impacts.clear(); consumed.clear(); pending.clear(); status = null; trailingHp = 0;
        trailDelay = flashTicks = recoilTicks = bannerTicks = bannerDuration = 0;
        bannerKind = null; bannerText = ""; clock = nextHitSignal = nextDroneSignal = nextBlockedSignal = 0;
    }

    public void accept(CombatEvent.BossImpact hit) {
        Objects.requireNonNull(hit, "hit");
        if (hit.actualDamage() == 0 && !hit.blocked()) return;
        if (hit.rootEventId() > 0) {
            HitKey key = new HitKey(hit.rootEventId(), hit.part(), hit.partId());
            if (consumed.putIfAbsent(key, true) != null) return;
            if (consumed.size() > 512) consumed.remove(consumed.keySet().iterator().next());
        }
        Pulse same = impacts.stream().filter(pulse -> pulse.age <= 3 && pulse.part == hit.part()
                && pulse.partId == hit.partId() && pulse.source == hit.source() && pulse.blocked == hit.blocked()
                && Math.hypot(pulse.x - hit.x(), pulse.y - hit.y()) <= 32).findFirst().orElse(null);
        if (same == null) {
            if (impacts.size() == 12) impacts.remove(0);
            impacts.add(new Pulse(hit));
        } else {
            same.damage = (int) Math.min(Integer.MAX_VALUE, (long) same.damage + hit.actualDamage());
            same.critical |= hit.critical();
        }
        if (hit.blocked()) {
            if (clock >= nextBlockedSignal) { signal(Kind.BLOCKED, 0, 0); nextBlockedSignal = clock + 18; }
            return;
        }
        boolean drone = hit.source() == CombatEvent.DamageKind.DRONE;
        boolean heavy = hit.critical() || hit.actualDamage() >= 50 || hit.source() == CombatEvent.DamageKind.BOMB
                || hit.source() == CombatEvent.DamageKind.ENVIRONMENT;
        flashTicks = Math.max(flashTicks, drone ? 2 : heavy ? 6 : 4);
        recoilTicks = Math.max(recoilTicks, drone ? 2 : heavy ? 8 : 5);
        if (hit.part() == CombatEvent.BossPart.NODE && hit.destroyed()) {
            signal(Kind.NODE_BREAK, 4, 14);
            banner("LINK " + (hit.partId() + 1) + " BROKEN", Kind.NODE_BREAK, 48);
        } else if (drone) {
            if (clock >= nextDroneSignal) { signal(Kind.DRONE_HIT, 0, 0); nextDroneSignal = clock + 12; }
        } else if (clock >= nextHitSignal) {
            signal(heavy ? Kind.HEAVY_HIT : Kind.HIT, heavy ? 2 : 1, heavy ? 7 : 3);
            nextHitSignal = clock + 10;
        }
    }

    /** First observation establishes a baseline; only later edges may announce a new phase. */
    public void observe(Status next) {
        Objects.requireNonNull(next, "next");
        if (status == null) { status = next; trailingHp = next.hp(); return; }
        if (next.maxHp() != status.maxHp()) {
            reset(); status = next; trailingHp = next.hp(); return;
        }
        if (next.hp() < status.hp() && trailingHp <= status.hp()) trailDelay = 14;
        trailingHp = Math.max(next.hp(), trailingHp);
        if (status.hp() > 0 && next.hp() == 0) {
            signal(Kind.DEFEATED, 7, 24); banner("PROCESS TERMINATED", Kind.DEFEATED, 100);
        } else if (next.hp() > 0) {
            if (status.shielded() && !next.shielded() || !status.vulnerable() && next.vulnerable()) {
                signal(Kind.GUARD_BREAK, 5, 16);
                banner(next.vulnerable() ? "CORE WINDOW // BONUS DAMAGE" : "CORE EXPOSED", Kind.GUARD_BREAK, 75);
            } else if (status.stage() != next.stage()) {
                signal(Kind.PHASE, 3, 12); banner(next.stageLabel(), Kind.PHASE, 75);
            }
        }
        status = next;
    }

    /** Call before a hitstop early return so impact feedback can finish while combat is frozen. */
    public void tick(boolean active) {
        if (!active) return;
        clock++;
        for (Pulse pulse : impacts) pulse.age++;
        impacts.removeIf(pulse -> pulse.age >= 24);
        if (flashTicks > 0) flashTicks--;
        if (recoilTicks > 0) recoilTicks--;
        if (bannerTicks > 0) bannerTicks--;
        if (trailDelay > 0) trailDelay--;
        else if (status != null) trailingHp = Math.max(status.hp(), trailingHp - Math.max(1, status.maxHp() * 0.018));
    }

    public List<Signal> drainSignals() {
        List<Signal> result = List.copyOf(pending.values()); pending.clear(); return result;
    }

    public Snapshot snapshot() {
        Status current = status == null ? new Status(0, 1, 0, "", false, false, 0, 0, 1, 1) : status;
        return new Snapshot(current.hp(), current.maxHp(), trailingHp, current.stage(), current.stageLabel(),
                current.shielded(), current.vulnerable(), flashTicks / 6.0, recoilTicks / 8.0,
                impacts.stream().map(Pulse::view).toList(), bannerTicks == 0 ? null
                : new Banner(bannerText, bannerKind, 1 - bannerTicks / (double) bannerDuration),
                current.x(), current.y(), current.width(), current.height());
    }

    private void signal(Kind kind, int hitstop, int shake) {
        // No Boss hit requests the global red damage overlay; all impact light is local.
        pending.put(kind, new Signal(kind, hitstop, shake, 0));
    }

    private void banner(String text, Kind kind, int duration) {
        if (bannerTicks > 0 && bannerKind.ordinal() > kind.ordinal()) return;
        bannerText = text; bannerKind = kind; bannerTicks = bannerDuration = duration;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
