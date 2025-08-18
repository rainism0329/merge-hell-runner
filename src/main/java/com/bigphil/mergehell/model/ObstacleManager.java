package com.bigphil.mergehell.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ObstacleManager {

    private final List<Rectangle> obstacles = new ArrayList<>();

    private final int width = 40;
    private final int height = 60;
    private final int initialCount = 5;
    private final int initialStartX = 500;

    public void reset(int panelHeight) {
        obstacles.clear();
        for (int i = 0; i < initialCount; i++) {
            int x = initialStartX + i * 250;
            int y = panelHeight - height;
            obstacles.add(new Rectangle(x, y, width, height));
        }
    }

    public void update(int panelWidth, int panelHeight) {
        Iterator<Rectangle> iterator = obstacles.iterator();
        while (iterator.hasNext()) {
            Rectangle obs = iterator.next();
            obs.x -= 5;
        }

        // 添加新障碍物
        if (!obstacles.isEmpty()) {
            Rectangle last = obstacles.get(obstacles.size() - 1);
            if (last.x < panelWidth - 300) {
                int newX = last.x + 200 + (int) (Math.random() * 150);
                int y = panelHeight - height;
                obstacles.add(new Rectangle(newX, y, width, height));
            }
        }

        // 移除屏幕外障碍物
        obstacles.removeIf(obs -> obs.x + width < 0);
    }

    public boolean checkCollision(Rectangle playerBounds) {
        for (Rectangle obs : obstacles) {
            if (obs.intersects(playerBounds)) {
                return true;
            }
        }
        return false;
    }

    public void draw(Graphics g) {
        g.setColor(Color.RED);
        for (Rectangle obs : obstacles) {
            g.fillRect(obs.x, obs.y, obs.width, obs.height);
            g.setColor(Color.WHITE);
            g.drawString("MERGE", obs.x + 2, obs.y + 30);
            g.setColor(Color.RED);
        }
    }
}
