package com.bigphil.mergehell.persistence;

@FunctionalInterface
public interface LegacyScoreSource {
    String read();
}
