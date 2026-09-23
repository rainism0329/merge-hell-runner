package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.world.ChapterRouteController;
import com.bigphil.mergehell.world.ExplorationRoute;
import java.awt.*;
import java.awt.geom.*;

/** Visible machinery uses the same gate offset and deployed bridge width as collision. */
final class CitadelMechanismRenderer {
    private static final Color STEEL=new Color(69,84,87), AMBER=new Color(226,183,102),
            READY=new Color(148,221,188), INK=new Color(12,23,29);
    static void gateFrame(Graphics2D original,ExplorationRoute.Snapshot route,double camera,int width) {
        if(route.chapter()!=2 || route.arena() || camera+width<9200 || camera>9470)return;
        Graphics2D g=(Graphics2D)original.create();
        try {
            for(int x:new int[]{9238,9420}) {
                g.setColor(INK);g.fillRect(x-6,126,14,354);
                g.setColor(STEEL);g.fillRect(x-2,130,4,350);
                for(int y=144;y<475;y+=24){g.setColor(AMBER.darker());g.fillRect(x-4,y,8,3);}
            }
            g.setColor(new Color(24,39,46));g.fillRoundRect(9226,108,214,32,5,5);
            g.setColor(STEEL);g.drawRoundRect(9226,108,214,32,5,5);
            g.setColor(route.visited().contains(1)?READY:AMBER);g.fillRect(9270,121,97,3);
            for(int x:new int[]{9243,9420}) {
                g.setColor(INK);g.fillOval(x-11,113,22,22);
                g.setColor(AMBER);g.drawOval(x-9,115,18,18);
                double angle=route.gateLift()*.13;
                g.setStroke(new BasicStroke(2));
                g.draw(new Line2D.Double(x-7*Math.cos(angle),124-7*Math.sin(angle),x+7*Math.cos(angle),124+7*Math.sin(angle)));
            }
        }finally {g.dispose();}
    }
    static void gateStatus(Graphics2D original,ExplorationRoute.Snapshot route,double camera,int width) {
        if(route.chapter()!=2 || route.arena() || camera+width<9210 || camera>9460)return;
        Graphics2D g=(Graphics2D)original.create();
        try {
            int bottom=480-route.gateLift();
            g.setColor(route.gateLift()==180?READY:AMBER);g.fillRect(9250,bottom-7,170,5);
            g.setStroke(new BasicStroke(3));
            for(int x=9250;x<9420;x+=20) {
                g.setColor(INK);g.drawLine(x,bottom-7,x+7,bottom-3);
            }
            if(route.visited().contains(1))label(g,route.gateLift()==180?"explore.winch.ready":"explore.winch.motion",9335,98);
        }finally {g.dispose();}
    }
    static void bridge(Graphics2D original,ChapterRouteController.Snapshot scene,double camera,int width,int floor) {
        if(scene.level()!=2 || scene.arena() || camera+width<9510 || camera>9900)return;
        Graphics2D g=(Graphics2D)original.create();
        try {
            int y=floor-90, deployed=scene.safeBridge().deployedWidth();
            // The drive cabinet sits below the console and is visibly bolted to the abutment.
            g.setColor(new Color(33,47,51));g.fillRect(9539,y+31,12,floor-y-31);
            g.setColor(STEEL);g.fillRect(9529,floor-6,32,6);
            g.setStroke(new BasicStroke(3));g.drawLine(9566,340,9544,340);g.drawLine(9544,340,9544,y-20);
            g.setColor(INK);g.fillRoundRect(9526,y-20,37,55,6,6);
            g.setColor(STEEL);g.drawRoundRect(9526,y-20,37,55,6,6);
            g.setColor(scene.safeBridge().requested()?READY:AMBER);
            g.fillRect(9535,y-10,17,4);
            g.setStroke(new BasicStroke(2));g.drawLine(9544,y+5,9544,y+28);
            g.setColor(STEEL);g.fillRect(9856,y+12,8,floor-y-12);
            g.setColor(AMBER);g.fillRect(9854,y+2,12,8);
            if(deployed>0) {
                int end=9560+deployed;
                g.setColor(deployed==300?READY:AMBER);g.fillRect(9560,y-2,deployed,2);
                g.setStroke(new BasicStroke(2));
                for(int x=9565;x<end-8;x+=38) {
                    g.setColor(STEEL);g.drawLine(x,y-17,x,y-2);
                    g.setColor(new Color(114,132,130));g.drawLine(x,y-17,Math.min(end-3,x+38),y-17);
                }
                // Moving leading edge marks precisely where support ends while deploying.
                g.setColor(AMBER);g.fillRect(end-4,y-5,4,11);
                if(deployed<300)g.fillOval(end-3,y-13,5,5);
            }
            if(scene.safeBridge().requested())
                label(g,deployed==300?"explore.skybridge.ready":"explore.skybridge.motion",9705,y-35);
        }finally {g.dispose();}
    }
    private static void label(Graphics2D g,String key,int center,int y) {
        String text=GameText.message(key);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF,Font.BOLD,12)));
        int w=g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(7,17,24,220));g.fillRoundRect(center-w/2-7,y-14,w+14,20,5,5);
        g.setColor(READY);GameText.draw(g,text,center-w/2,y);
    }
    private CitadelMechanismRenderer() { }
}
