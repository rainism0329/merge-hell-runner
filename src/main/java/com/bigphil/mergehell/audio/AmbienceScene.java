package com.bigphil.mergehell.audio;

/** Music follows the mission; NONE also permits effects-only playback. */
public enum AmbienceScene {
    NONE, FOUNDRY, HEAP, BLUEPRINT, KERNEL, SINGULARITY, BOSS;

    public static AmbienceScene forMission(int mission, boolean boss) {
        if (boss) return BOSS;
        return switch (mission) {
            case 0 -> FOUNDRY;
            case 1 -> HEAP;
            case 2 -> BLUEPRINT;
            case 3 -> KERNEL;
            case 4 -> SINGULARITY;
            default -> NONE;
        };
    }
}
