package com.bigphil.mergehell.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ObstacleManager {

    private final List<Obstacle> obstacles = new ArrayList<>();
    private final int initialCount = 5;
    private final int initialStartX = 500;

    private final Random random = new Random();

    public void reset(int panelHeight) {
        obstacles.clear();
        for (int i = 0; i < initialCount; i++) {
            int x = initialStartX + i * 250;
            obstacles.add(generateRandomObstacle(x, panelHeight));
        }
    }

    public void update(int panelWidth, int panelHeight) {
        for (Obstacle obs : obstacles) {
            obs.update();
        }

        // 添加新障碍物
        if (!obstacles.isEmpty()) {
            Obstacle last = obstacles.get(obstacles.size() - 1);
            if (last.getRightEdge() < panelWidth - 300) {
                int newX = last.getRightEdge() + 200 + random.nextInt(150);
                obstacles.add(generateRandomObstacle(newX, panelHeight));
            }
        }

        // 移除屏幕外的
        obstacles.removeIf(Obstacle::isOffScreen);
    }

    public boolean checkCollision(Rectangle playerBounds) {
        for (Obstacle obs : obstacles) {
            if (obs.checkCollision(playerBounds)) {
                return true;
            }
        }
        return false;
    }

    public void draw(Graphics g) {
        for (Obstacle obs : obstacles) {
            obs.draw(g);
        }
    }

    private Obstacle generateRandomObstacle(int x, int panelHeight) {
        int type = random.nextInt(3);
        int y = panelHeight - 60;

        return switch (type) {
            case 0 -> new Obstacle(x, y, 40, 60);              // 标准
            case 1 -> new Obstacle(x, y + 20, 30, 40);          // 矮小障碍
            case 2 -> new Obstacle(x, y - 20, 50, 80);          // 高大障碍
            default -> new Obstacle(x, y, 40, 60);
        };
    }
}
