package com.bigphil.mergehell.model;

import java.awt.*;
import java.util.List;

public class ScoreBoardRenderer {

    private final ScoreManager scoreManager;

    public ScoreBoardRenderer(ScoreManager scoreManager) {
        this.scoreManager = scoreManager;
    }

    public void drawScore(Graphics g, int panelWidth) {
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 18));
        String scoreText = "Score: " + scoreManager.getCurrentScore();
        g.drawString(scoreText, 10, 20);
    }

    public void drawTopScores(Graphics g, int panelWidth, int panelHeight) {
        List<Integer> topScores = scoreManager.getTopScores();
        if (topScores.isEmpty()) return;

        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("Arial", Font.PLAIN, 14));

        int startX = panelWidth - 150;
        int startY = 60;

        g.drawString("Top Scores:", startX, startY);

        for (int i = 0; i < topScores.size(); i++) {
            String entry = (i + 1) + ". " + topScores.get(i);
            g.drawString(entry, startX, startY + (i + 1) * 18);
        }
    }

}
