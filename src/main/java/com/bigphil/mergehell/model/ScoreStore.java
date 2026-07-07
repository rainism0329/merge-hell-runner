package com.bigphil.mergehell.model;

import com.intellij.ide.util.PropertiesComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScoreStore {

    private static final String KEY = "com.bigphil.mergehell.topScores";
    private static final int MAX_SCORES = 5;

    public static List<Integer> load() {
        String raw = PropertiesComponent.getInstance().getValue(KEY);
        if (raw == null || raw.isBlank()) return new ArrayList<>();

        List<Integer> scores = new ArrayList<>();
        for (String part : raw.split(",")) {
            try {
                scores.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        scores.sort(Collections.reverseOrder());
        return scores;
    }

    public static void save(int newScore) {
        List<Integer> scores = load();
        scores.add(newScore);
        scores.sort(Collections.reverseOrder());
        if (scores.size() > MAX_SCORES) {
            scores = scores.subList(0, MAX_SCORES);
        }
        String raw = String.join(",", scores.stream().map(String::valueOf).toList());
        PropertiesComponent.getInstance().setValue(KEY, raw);
    }

    public static boolean isHighScore(int score) {
        List<Integer> scores = load();
        if (scores.size() < MAX_SCORES) return true;
        return score > scores.get(scores.size() - 1);
    }
}
