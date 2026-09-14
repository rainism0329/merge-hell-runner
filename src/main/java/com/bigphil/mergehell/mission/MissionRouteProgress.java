package com.bigphil.mergehell.mission;

/** Read-only route status. Timers describe authored stages, never an ETA to the boss. */
public record MissionRouteProgress(int stageNumber, int stageCount, int stagesRemaining,
                                   MissionSegment.Kind kind, String objective, int stageTicksRemaining,
                                   int routeTicksRemaining, int kills, int killTarget, int activeHostiles,
                                   boolean bossSpawned, double stageFraction) {
    public boolean atBossGate() { return kind == MissionSegment.Kind.BOSS_GATE; }
    public boolean waitingForKills() { return killTarget > kills && stageTicksRemaining == 0; }
    public double routeFraction() {
        if (bossSpawned || kind == null) return 1;
        return Math.max(0, Math.min(1, (stageNumber - 1 + stageFraction) / stageCount));
    }
}
