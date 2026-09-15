package com.bigphil.mergehell.progression;

/** Stable saved identities; every character can use every weapon and complete every route. */
public enum CharacterId {
    REPAIR("repair", 1, 1), SCOUT("scout", 1.13, 1.10), WARDEN("warden", .87, .85), ENGINEER("engineer", .96, 1);
    private final String art;
    private final double speed, incoming;
    CharacterId(String art,double speed,double incoming) { this.art=art;this.speed=speed;this.incoming=incoming; }
    public String art() { return art; }
    public double speed() { return speed; }
    public double incoming() { return incoming; }
    public String titleKey() { return "character."+art+".name"; }
    public String detailKey() { return "character."+art+".detail"; }
}
