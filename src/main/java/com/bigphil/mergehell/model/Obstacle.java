package com.bigphil.mergehell.model;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class Obstacle {

    public enum Type {
        CONFLICT_START, // <<<<<<<
        SEPARATOR,      // =======
        CONFLICT_END,   // >>>>>>>
        BUG             // 红色 Bug
    }

    protected int x, y, width, height;
    protected Type type;
    private BufferedImage image;

    public Obstacle(int x, int y, int width, int height, Type type) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;

        // 尝试根据类型加载图片
        String path = switch (type) {
            case BUG -> "/images/bug.png";
            default -> null; // 其他用代码绘制
        };

        if (path != null) {
            try {
                var url = getClass().getResource(path);
                if (url != null) image = ImageIO.read(url);
            } catch (IOException ignored) {}
        }
    }

    public void update(int speed) {
        x -= speed;
    }

    public boolean isOffScreen() {
        return x + width < 0;
    }

    public boolean checkCollision(Rectangle playerBounds) {
        // 稍微宽容一点的碰撞检测
        Rectangle r = new Rectangle(x + 4, y + 4, width - 8, height - 8);
        return r.intersects(playerBounds);
    }

    public void draw(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
            return;
        }

        // 默认绘制逻辑：模拟代码冲突块
        switch (type) {
            case CONFLICT_START -> {
                g2d.setColor(new Color(60, 120, 200)); // 蓝色背景
                g2d.fillRect(x, y, width, height);
                g2d.setColor(Color.WHITE);
                g2d.drawString("<<<<<<<", x + 2, y + 20);
                g2d.drawString("HEAD", x + 5, y + 40);
            }
            case SEPARATOR -> {
                g2d.setColor(new Color(80, 80, 80)); // 灰色背景
                g2d.fillRect(x, y, width, height);
                g2d.setColor(Color.WHITE);
                g2d.drawString("=======", x + 2, y + 35);
            }
            case CONFLICT_END -> {
                g2d.setColor(new Color(60, 180, 100)); // 绿色背景
                g2d.fillRect(x, y, width, height);
                g2d.setColor(Color.WHITE);
                g2d.drawString(">>>>>>>", x + 2, y + 20);
                g2d.drawString("master", x + 2, y + 40);
            }
            case BUG -> {
                g2d.setColor(new Color(200, 50, 50)); // 红色
                g2d.fillOval(x, y, width, height);
                g2d.setColor(Color.BLACK);
                g2d.drawString("NPE", x + 5, y + 35); // NullPointerException
            }
        }
    }

    public int getRightEdge() {
        return x + width;
    }
}