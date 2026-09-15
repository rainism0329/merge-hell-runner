package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.world.ExplorationRoute;
import java.awt.*;


/** Authored room landmarks and solid scenery, using the same geometry as the route. */
public final class ExplorationRenderer {
    public static void world(Graphics2D original, IndustrialArt art, ExplorationRoute.Snapshot route,
                             double camera, int width) {
        if (route == null || route.arena()) return;
        Graphics2D g = (Graphics2D) original.create();
        try {
            Color accent = switch (route.chapter()) {
                case 0 -> new Color(237,180,97); case 1 -> new Color(96,203,213);
                case 2 -> new Color(166,220,229); case 3 -> new Color(246,136,76);
                default -> new Color(192,209,130);
            };
            for (var block : route.blocks()) {
                Rectangle b = block.bounds();
                if (b.getMaxX() < camera - 60 || b.x > camera + width + 60) continue;
                String part = switch (block.material()) {
                    case BRICK -> "city-wall"; case DRAIN -> "drain-wall";
                    case GIRDER -> "gantry-wall"; case FURNACE -> "foundry-wall"; case TISSUE -> "hive-wall";
                };
                if (block.canopy()) {
                    // Suspension reaches the scene roof; the visible beam has physical clearance below.
                    for (int dx : new int[]{17, b.width-17}) {
                        g.setStroke(new BasicStroke(route.chapter() == 4 ? 7 : 5));
                        g.setColor(route.chapter() == 4 ? new Color(70,58,68) : new Color(42,45,44));
                        g.drawLine(b.x+dx, 0, b.x+dx, b.y+8);
                        g.setStroke(new BasicStroke(1)); g.setColor(new Color(157,146,119,145));
                        g.drawLine(b.x+dx-1, 0, b.x+dx-1, b.y+8);
                    }
                }
                if (!art.part(g, "route-props", part, b.x, b.y, b.width, b.height)) {
                    g.setColor(new Color(75,71,63)); g.fill(b);
                }
                // Keep the exact walkable edge legible against detailed surfaces.
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 155));
                g.fillRect(b.x+3, b.y, b.width-6, 2);
                if (block.canopy()) {
                    g.setColor(accent); g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, 11)));
                    GameText.draw(g, "↓ " + GameText.message("explore.crouch"), b.x+7, b.y+b.height-7);
                }
            }
            for (var p : route.platforms()) {
                if (p.x+p.width < camera-60 || p.x > camera+width+60) continue;
                if (route.chapter() >= 2) ChapterWorldRenderer.deck(g, route.chapter(), p.x, p.y, p.width, true);
                else art.part(g, "route-props", "walkway", p.x, p.y, p.width, Math.max(25, p.width*.19));
            }
            for (var point : route.landmarks()) {
                Rectangle b = point.bounds();
                if (b.x < camera-140 || b.x > camera+width+80) continue;
                boolean done = route.visited().contains(point.id());
                boolean nearby = route.hint().equals(point.key()) && !done;
                String part = route.chapter() == 4 ? "nerve-console" : "console";
                boolean office = point.secret() && route.chapter() == 0;
                art.part(g, "route-props", part, b.x+(office?4:-2), b.y+(office?-4:-17), office?29:40, office?34:47);
                g.setColor(done ? new Color(108,221,153) : accent);
                g.fillOval(b.x+15, b.y-5, 4, 4);
                g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, 12)));
                String title = GameText.message(point.key()+".name");
                int labelWidth = g.getFontMetrics().stringWidth(title);
                g.setColor(new Color(7,17,24,205));
                g.fillRoundRect(b.x+17-labelWidth/2-6, b.y-40, labelWidth+12, 19, 6, 6);
                g.setColor(done ? new Color(146,183,155) : accent);
                GameText.draw(g, title, b.x+17-labelWidth/2, b.y-26);
                if (nearby) GameText.draw(g, "[ E ]", b.x+3, b.y+44);
                if (point.secret() && route.chapter() == 0) {
                    // An abandoned workstation hints at the engineer's hidden office.
                    g.setColor(new Color(119,103,83)); g.fillRect(b.x-47,b.y+15,38,5);
                    g.setColor(new Color(52,53,49));
                    g.fillRect(b.x-44,b.y+20,3,10); g.fillRect(b.x-15,b.y+20,3,10);
                    g.setColor(new Color(194,192,164)); g.fillRoundRect(b.x-38,b.y+7,7,8,2,2);
                    g.drawOval(b.x-33,b.y+8,5,4);
                }
            }
        } finally { g.dispose(); }
    }
    public static void hud(Graphics2D original, ExplorationRoute.Snapshot route, double x) {
        if(route==null || route.arena())return;
        Graphics2D g=(Graphics2D)original.create();
        try {
            String text;
            if(!route.hint().isEmpty())text="[ E ] "+GameText.message(route.hint()+".name");
            else {
                var next=route.landmarks().stream().filter(l->!l.secret()&&!route.visited().contains(l.id())).findFirst();
                if(next.isEmpty())return;
                var point=next.get();
                text=(point.bounds().x>=x?"→ ":"← ")+GameText.message(point.key()+".name")+"  "+point.id()+"/2";
            }
            g.setFont(GameText.font(new Font(Font.SANS_SERIF,Font.BOLD,13)));
            int w=g.getFontMetrics().stringWidth(text)+28;
            g.setColor(new Color(7,17,24,220));g.fillRoundRect(18,401,w,28,6,6);
            g.setColor(new Color(199,226,203));GameText.draw(g,text,32,420);
        }finally {g.dispose();}
    }
    private ExplorationRenderer() { }
}
