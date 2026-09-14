package com.bigphil.mergehell.audio;

/** Original, deterministic ambient loops. Synthesized once on demand, never on the game/EDT thread. */
public final class AmbienceBank {
    public static final int LOOP_FRAMES = SoundBank.SAMPLE_RATE * 8;
    private final short[][] loops = new short[AmbienceScene.values().length][];

    private static final class Cache { static final AmbienceBank INSTANCE = new AmbienceBank(); }
    public static AmbienceBank synthesized() { return Cache.INSTANCE; }

    private AmbienceBank() {
        int[] roots = {0, 45, 41, 50, 43, 38, 33};
        int[][] motifs = {
                {}, {0, 7, 12, 7, 3, 10, 7, 3}, {0, 3, 7, 10, 7, 3, 12, 7},
                {0, 12, 7, 15, 10, 7, 12, 3}, {0, 7, 1, 7, 3, 10, 1, 7},
                {0, 7, 12, 15, 10, 7, 3, 0}, {0, 0, 7, 0, 3, 0, 10, 7}
        };
        for (AmbienceScene scene : AmbienceScene.values()) {
            if (scene == AmbienceScene.NONE) continue;
            short[] loop = new short[LOOP_FRAMES];
            int id = scene.ordinal();
            double root = frequency(roots[id]);
            double[] notes = new double[8];
            for (int i = 0; i < notes.length; i++) notes[i] = frequency(roots[id] + 12 + motifs[id][i]);
            double fifth = frequency(roots[id] + 7);
            boolean boss = scene == AmbienceScene.BOSS;
            for (int frame = 0; frame < loop.length; frame++) {
                double time = frame / (double) SoundBank.SAMPLE_RATE;
                double step = time * (boss ? 2 : 1);
                double position = step - Math.floor(step);
                // Envelopes reach zero with zero slope at note boundaries. All pad oscillators
                // complete whole cycles in eight seconds, including their slow modulation.
                double envelope = Math.pow(Math.sin(Math.PI * position), 2) * Math.exp(-position * 3);
                double note = notes[(int) step % 8];
                double pad = (Math.sin(2 * Math.PI * root * time)
                        + .40 * Math.sin(2 * Math.PI * fifth * time)
                        + .22 * Math.sin(2 * Math.PI * root * 2 * time)) * .11;
                pad *= .80 + .20 * Math.cos(2 * Math.PI * time / 8);
                double chime = (Math.sin(2 * Math.PI * note * time)
                        + .16 * Math.sin(2 * Math.PI * note * 2 * time)) * envelope * .28;
                double machinery = Math.sin(2 * Math.PI * 37 * time
                        + .7 * Math.sin(2 * Math.PI * time / 4)) * .025;
                double pulse = boss ? Math.sin(2 * Math.PI * 55 * time) * envelope * .25 : 0;
                loop[frame] = (short) Math.round((pad + chime + machinery + pulse) * 32_767);
            }
            loops[id] = loop;
        }
    }

    private static double frequency(int midi) {
        return Math.round(440 * Math.pow(2, (midi - 69) / 12.0) * 8) / 8.0;
    }

    public short at(AmbienceScene scene, int frame) {
        return scene == AmbienceScene.NONE ? 0 : loops[scene.ordinal()][frame];
    }
}
