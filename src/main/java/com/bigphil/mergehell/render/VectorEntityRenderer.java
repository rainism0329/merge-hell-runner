package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.EntityType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;

public final class VectorEntityRenderer {
    private static final Font LABEL = new Font("JetBrains Mono", Font.BOLD, 10);

    public static void render(Graphics2D g, EntityType type, int x, int y, int w, int h,
                              int hp, int maxHp, int telegraphTicks) {
        Color color = type.color;
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 205));
        switch (type) {
            case BUG -> drawBug(g, x, y, w, h);
            case CONFLICT -> drawConflict(g, x, y, w, h);
            case CRASH -> drawCrash(g, x, y, w, h);
            case LOCK -> drawLock(g, x, y, w, h);
            case TECHDEBT -> drawDebt(g, x, y, w, h);
            case FIREWALL -> drawFirewall(g, x, y, w, h);
            default -> { return; }
        }
        g.setFont(LABEL);
        g.setColor(Color.WHITE);
        String label = type == EntityType.TECHDEBT ? "DEBT" : type.name();
        g.drawString(label, x + 5, y + Math.min(h - 8, 28));

        if (maxHp > 1) {
            g.setColor(new Color(10, 12, 18, 200));
            g.fillRoundRect(x, y - 9, w, 5, 5, 5);
            g.setColor(color);
            g.fillRoundRect(x, y - 9, (int) (w * Math.max(0, hp) / (double) maxHp), 5, 5, 5);
        }
        if (telegraphTicks > 0) {
            int alpha = 90 + (telegraphTicks % 8) * 18;
            g.setColor(new Color(255, 70, 70, Math.min(220, alpha)));
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(x - 7, y - 7, w + 14, h + 14, 12, 12);
            g.drawLine(x, y + h / 2, x - 90, y + h / 2);
        }
    }

    private static void drawBug(Graphics2D g, int x, int y, int w, int h) {
        Polygon p = new Polygon(new int[]{x+w/2,x+w-4,x+w-4,x+w/2,x+4,x+4},
                new int[]{y+2,y+h/4,y+h*3/4,y+h-2,y+h*3/4,y+h/4},6);
        g.fillPolygon(p); g.drawLine(x+10,y+8,x+2,y); g.drawLine(x+w-10,y+8,x+w-2,y);
    }
    private static void drawConflict(Graphics2D g, int x, int y, int w, int h) {
        g.setStroke(new BasicStroke(5f));
        g.drawLine(x+4,y+7,x+w/2-3,y+h/2); g.drawLine(x+4,y+h-7,x+w/2-3,y+h/2);
        g.drawLine(x+w-4,y+7,x+w/2+3,y+h/2); g.drawLine(x+w-4,y+h-7,x+w/2+3,y+h/2);
    }
    private static void drawCrash(Graphics2D g, int x, int y, int w, int h) {
        Polygon p = new Polygon(new int[]{x+w/2,x+w-3,x+w/2,x+3},
                new int[]{y+2,y+h/2,y+h-2,y+h/2},4);
        g.fillPolygon(p); g.drawOval(x-5,y-5,w+10,h+10);
    }
    private static void drawLock(Graphics2D g, int x, int y, int w, int h) {
        g.drawArc(x+w/4,y+2,w/2,h/2,0,180); g.fillRoundRect(x+5,y+h/3,w-10,h*2/3-3,8,8);
        g.setColor(new Color(10,20,16)); g.fillOval(x+w/2-3,y+h/2,6,12);
    }
    private static void drawDebt(Graphics2D g, int x, int y, int w, int h) {
        for(int i=0;i<4;i++){ g.fillRoundRect(x+i*5,y+i*12,w-i*10,15,5,5); }
    }
    private static void drawFirewall(Graphics2D g, int x, int y, int w, int h) {
        for(int yy=y;yy<y+h;yy+=18) for(int xx=x;xx<x+w;xx+=18) {
            if (yy > y+h/2-12 && yy < y+h/2+18 && xx > x+w/3) continue;
            g.drawRect(xx,yy,14,14);
        }
    }

    private VectorEntityRenderer() { }
}
