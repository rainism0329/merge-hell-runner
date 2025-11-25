package com.bigphil.mergehell.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ObstacleManager {

    public class Enemy {
        public double x;
        public double y;
        double vx;
        int width, height;
        public String type;
        public Color color;
        String symbol; // 核心：使用 Emoji
        public boolean dead = false;
        public int damage = 20;

        public Enemy(double x, double y, String type) {
            this.x = x; this.y = y; this.type = type;
            this.width = 40; this.height = 40;
            this.vx = 4;

            switch (type) {
                // 回归经典 Emoji 设计
                case "conflict" -> { color = Color.decode("#cf8e6d"); symbol = "<<HEAD"; }
                case "bug" -> { color = Color.decode("#e75c4c"); symbol = "🐛"; } // 虫子
                case "techdebt" -> { width=60; height=80; vx=3; color=Color.decode("#5c6370"); symbol = "TODO"; damage=30; }
                case "crash" -> { width=50; height=50; vx=6; color=Color.decode("#ff9800"); symbol = "💥"; damage=40; } // 爆炸
                case "lock" -> { color=Color.decode("#4caf50"); symbol = "🔒"; } // 锁
                case "firewall" -> { width=60; height=120; vx=2; color=Color.decode("#1e88e5"); symbol = "🔥"; damage=50; } // 火
                case "powerup_sudo" -> { width=30; height=30; color=Color.decode("#f2c55c"); symbol = "⚡"; } // 闪电
                case "powerup_shield" -> { width=30; height=30; color=Color.decode("#40c4ff"); symbol = "🛡️"; } // 盾牌
            }
        }

        public void update() {
            x -= vx;
            // 锁头怪会有上下浮动的动画
            if (type.equals("lock")) y += Math.sin(System.currentTimeMillis() / 100.0) * 2;
        }

        public void draw(Graphics2D g) {
            // 1. 绘制背景色块（稍微透明一点，增加层次感）
            if (type.equals("firewall") || type.equals("techdebt")) {
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
                g.fillRect((int)x, (int)y, width, height);
            }

            // 2. 绘制 Emoji / 文字
            g.setColor(Color.WHITE); // 大部分 Emoji 还是用白色基底绘制比较稳

            // 关键：强制使用支持 Emoji 的字体，防止方块乱码
            // "Segoe UI Emoji" 是 Win10/11 自带的，"Apple Color Emoji" 是 Mac 的
            // "SansSerif" 是安全回退
            g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));

            // 针对不同类型微调文字位置
            if (type.equals("conflict") || type.equals("techdebt")) {
                g.setFont(new Font("JetBrains Mono", Font.BOLD, 14));
                g.drawString(symbol, (int)x + 2, (int)y + 25);
            } else if (type.equals("firewall")) {
                g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
                g.drawString(symbol, (int)x + 10, (int)y + 70);
            } else {
                g.drawString(symbol, (int)x, (int)y + 35);
            }
        }

        public Rectangle getBounds() {
            return new Rectangle((int)x, (int)y, width, height);
        }
    }

    private final List<Enemy> enemies = new ArrayList<>();
    private final Random random = new Random();

    public void reset() {
        enemies.clear();
    }

    public void spawnEnemy(int x, int y, String type) {
        enemies.add(new Enemy(x, y, type));
    }

    public void spawnRandom(int panelWidth, int groundY) {
        if (random.nextInt(100) < 2) {
            int r = random.nextInt(100);
            String type = "bug";
            int y = groundY - 40;

            if (r < 5) { type = "powerup_sudo"; y = groundY - 150; }
            else if (r < 10) { type = "powerup_shield"; y = groundY - 150; }
            else if (r < 30) { type = "conflict"; }
            else if (r < 50) { type = "techdebt"; y = groundY - 80; }
            else if (r < 70) { type = "lock"; y = groundY - 100; }
            else if (r < 80) { type = "firewall"; y = 0; }
            else if (r < 90) { type = "crash"; }

            enemies.add(new Enemy(panelWidth, y, type));
        }
    }

    public void update() {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            e.update();
            if (e.x + e.width < -100 || e.dead) it.remove();
        }
    }

    public void draw(Graphics2D g) {
        for (Enemy e : enemies) e.draw(g);
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }
}