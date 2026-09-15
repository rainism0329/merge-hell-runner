package com.bigphil.mergehell.progression;

/** Authored health, incoming damage and recovery pacing; warnings keep their readable minimum. */
public enum GameDifficulty {
    RELAXED(.72,.65,1.25), STANDARD(1,1,1), CHALLENGE(1.28,1.2,.80);
    private final double health,damage,recovery;
    GameDifficulty(double health,double damage,double recovery) { this.health=health;this.damage=damage;this.recovery=recovery; }
    public int health(int base) { return Math.max(1,(int)Math.round(base*health)); }
    public int damage(int base) { return Math.max(1,(int)Math.round(base*damage)); }
    public int recovery(int ticks) { return Math.max(12,(int)Math.round(ticks*recovery)); }
    public String key() { return "difficulty."+name().toLowerCase(java.util.Locale.ROOT); }
}
