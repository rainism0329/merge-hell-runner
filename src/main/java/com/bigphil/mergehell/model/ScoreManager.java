package com.bigphil.mergehell.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.ide.util.PropertiesComponent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScoreManager {

    private int currentScore = 0;
    private final List<Integer> topScores = new ArrayList<>();
    private static final String KEY = "com.bigphil.mergehell.topScores";
    private static final int MAX_TOP = 5;

    public ScoreManager() {
        loadTopScores();
    }

    public void reset() {
        currentScore = 0;
    }

    public void increase() {
        currentScore++;
    }

    public int getCurrentScore() {
        return currentScore;
    }

    public List<Integer> getTopScores() {
        return Collections.unmodifiableList(topScores);
    }

    public void finalizeScore() {
        topScores.add(currentScore);
        topScores.sort(Collections.reverseOrder());
        while (topScores.size() > MAX_TOP) {
            topScores.remove(topScores.size() - 1);
        }
        saveTopScores();
    }

    private void saveTopScores() {
        String json = new Gson().toJson(topScores);
        PropertiesComponent.getInstance().setValue(KEY, json);
    }

    private void loadTopScores() {
        String json = PropertiesComponent.getInstance().getValue(KEY);
        if (json != null) {
            try {
                Type listType = new TypeToken<List<Integer>>() {}.getType();
                List<Integer> saved = new Gson().fromJson(json, listType);
                if (saved != null) {
                    topScores.clear();
                    topScores.addAll(saved);
                }
            } catch (Exception ignored) {}
        }
    }
}
