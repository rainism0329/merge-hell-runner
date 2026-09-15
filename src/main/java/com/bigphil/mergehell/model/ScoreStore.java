package com.bigphil.mergehell.model;

import com.bigphil.mergehell.persistence.MergeHellStateService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScoreStore {

    private static final int MAX_SCORES = 5;

    public static List<Integer> load(com.bigphil.mergehell.progression.GameDifficulty difficulty) {
        return new ArrayList<>(MergeHellStateService.getInstance().getState().rankedScores.getOrDefault(difficulty,List.of()));
    }
    public static void save(int score,com.bigphil.mergehell.progression.GameDifficulty difficulty) {
        MergeHellStateService.getInstance().recordScore(score,difficulty);
    }
    public static boolean isHighScore(int score,com.bigphil.mergehell.progression.GameDifficulty difficulty) {
        var values=load(difficulty);return values.size()<MAX_SCORES || score>values.get(values.size()-1);
    }
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
