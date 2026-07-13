package com.bigphil.mergehell.boss;

public final class DependencyNode {
    private final int id;
    private final int maxHp;
    private int hp;

    public DependencyNode(int id, int maxHp) {
        if (id < 0 || maxHp <= 0) throw new IllegalArgumentException();
        this.id = id;
        this.maxHp = maxHp;
        hp = maxHp;
    }

    public void damage(int amount) { hp = Math.max(0, hp - Math.max(0, amount)); }
    public int id() { return id; }
    public int hp() { return hp; }
    public int maxHp() { return maxHp; }
    public boolean alive() { return hp > 0; }
}
