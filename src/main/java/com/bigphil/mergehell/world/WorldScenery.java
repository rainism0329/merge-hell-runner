package com.bigphil.mergehell.world;

/** A lightweight, deterministic piece of world dressing. */
public record WorldScenery(
        double x, int y, int width, int height, Kind kind, Layer layer, int variant) {

    public enum Layer { BACK, MID, FRONT }

    public enum Kind {
        CITY_TOWER, NEON_SIGN, WATER_TANK,
        CRANE, FURNACE, SMOKE_STACK, PIPE_CLUSTER,
        SERVER_RACK, COOLING_FAN, CABLE_BRIDGE,
        BROKEN_PACKAGE, SCRAP_HEAP, DEAD_TREE,
        WARNING_PYLON, BLAST_DOOR, SEARCHLIGHT
    }
}
