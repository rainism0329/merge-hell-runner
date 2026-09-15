package com.bigphil.mergehell.combat;

/** A normalized aim axis shared by weapons, muzzle placement and the articulated actor. */
public record Aim(double x, double y) {
    public Aim {
        double length = Math.hypot(x, y);
        if (!Double.isFinite(length) || length < 1e-9) throw new IllegalArgumentException("Invalid aim");
        x /= length; y /= length;
    }
    public static Aim horizontal(int facing) { return new Aim(facing < 0 ? -1 : 1, 0); }
    public static Aim from(boolean left, boolean right, boolean up, boolean down, boolean grounded, int facing) {
        int vertical = up == down ? 0 : up ? -1 : grounded ? 0 : 1;
        int horizontal = left == right ? 0 : left ? -1 : 1;
        if (vertical == 0 && horizontal == 0) horizontal = facing;
        return new Aim(horizontal, vertical);
    }
    public double angle() { return Math.atan2(y, x); }
}
