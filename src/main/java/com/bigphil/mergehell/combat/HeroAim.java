package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.progression.CharacterId;

/** Shared anatomical gun position. Simulation and articulated drawing use the same muzzle. */
public final class HeroAim {
    public record Muzzle(double x, double y) { }

    public static Muzzle local(double aimX, double aimY, boolean crouching, CharacterId character) {
        // A vertical rifle is carried in front of the face, never rotated through the waist.
        double side = aimY < 0 ? (character == CharacterId.REPAIR ? 11 : 8) : 12;
        double x = Math.abs(aimX) > .1 ? 18 : side;
        double y = -(crouching ? 18 : 30) + aimY * (aimY < 0 ? 19 : 20);
        return new Muzzle(x, y);
    }

    private HeroAim() { }
}
