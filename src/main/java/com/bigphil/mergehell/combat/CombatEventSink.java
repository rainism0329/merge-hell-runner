package com.bigphil.mergehell.combat;

@FunctionalInterface
public interface CombatEventSink {
    void accept(CombatEvent event);
}
