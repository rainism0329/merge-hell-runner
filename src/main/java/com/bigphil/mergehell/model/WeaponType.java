package com.bigphil.mergehell.model;

public enum WeaponType {
    COMMIT("git push", 20, 25, 1, false),
    SPREAD("git push -f", 15, 20, 3, true),
    RAPID("git commit -a", 8, 15, 1, false),
    HEAVY("rm -rf /", 25, 80, 1, true);

    public final String label;
    public final int cooldown;
    public final int damage;
    public final int bulletCount;
    public final boolean piercing;

    WeaponType(String label, int cooldown, int damage, int bulletCount, boolean piercing) {
        this.label = label;
        this.cooldown = cooldown;
        this.damage = damage;
        this.bulletCount = bulletCount;
        this.piercing = piercing;
    }
}
