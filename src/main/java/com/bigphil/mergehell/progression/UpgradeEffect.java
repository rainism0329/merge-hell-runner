package com.bigphil.mergehell.progression;

@FunctionalInterface
public interface UpgradeEffect {
    BuildStats apply(BuildStats current);
}
