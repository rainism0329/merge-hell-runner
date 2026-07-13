package com.bigphil.mergehell.model;

import com.bigphil.mergehell.persistence.MergeHellStateService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScoreStore {

    private static final int MAX_SCORES = 5;

    public static List<Integer> load() {
        return new ArrayList<>(MergeHellStateService.getInstance().getState().topScores);
    }

    public static void save(int newScore) {
        MergeHellStateService.getInstance().recordScore(newScore);
    }

    public static boolean isHighScore(int score) {
        List<Integer> scores = load();
        if (scores.size() < MAX_SCORES) return true;
        return score > scores.get(scores.size() - 1);
    }
}
