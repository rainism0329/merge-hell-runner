package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.world.ChapterRouteController;
import java.awt.*;
import java.awt.geom.*;

/** Conduits show which nearby machine responds to each exploration control. */
final class LateChapterMechanismRenderer {
    private static final Color COOL=new Color(130,220,220),HOT=new Color(221,148,79),
            NERVE=new Color(174,208,134),DORMANT=new Color(92,115,109);
    static void world(Graphics2D original,ChapterRouteController.Snapshot scene,double camera,int width,int floor) {
        if(scene.arena() || scene.level()<3 || scene.level()>4)return;
        Graphics2D g=(Graphics2D)original.create();
        try {
            if(scene.level()==3) {
                if(visible(camera,width,8250,8950))freight(g,scene,floor);
                if(visible(camera,width,9270,9770))exhaust(g,scene,floor);
            } else {
                if(visible(camera,width,8300,9010))brood(g,scene,floor);
                if(visible(camera,width,9390,9830))nerve(g,scene,floor);
            }
        }finally {g.dispose();}
    }
    private static void freight(Graphics2D g,ChapterRouteController.Snapshot scene,int floor) {
        boolean forward=scene.mechanisms().freightForward();Color color=forward?COOL:HOT;
        Path2D cable=new Path2D.Double();cable.moveTo(8331,290);cable.lineTo(8438,315);
        cable.lineTo(8438,440);cable.lineTo(8658,440);cable.lineTo(8658,floor-9);
        conduit(g,cable,color,false);
        g.setColor(new Color(26,38,40));g.fillRoundRect(8597,421,53,37,7,7);
        g.fillRect(8603,458,6,floor-458);g.fillRect(8638,458,6,floor-458);
        g.setColor(color);g.drawRoundRect(8597,421,53,37,7,7);
        int direction=forward?1:-1;
        double phase=scene.tick()*.11*direction;
        g.setStroke(new BasicStroke(2));g.drawOval(8615,430,17,17);
        g.draw(new Line2D.Double(8623,439,8623+7*Math.cos(phase),439+7*Math.sin(phase)));
        for(int x=8602;x<8650;x+=42) {
            Path2D arrow=new Path2D.Double();arrow.moveTo(x+20-direction*4,424);
            arrow.lineTo(x+20+direction*4,420);arrow.lineTo(x+20-direction*4,416);g.draw(arrow);
        }
        if(forward)label(g,"explore.freight.active",8350,392,COOL);
    }
    private static void exhaust(Graphics2D g,ChapterRouteController.Snapshot scene,int floor) {
        boolean open=scene.mechanisms().exhaustOpen();Color color=open?COOL:HOT;
        Path2D pipe=new Path2D.Double();pipe.moveTo(9368,270);pipe.lineTo(9439,289);
        pipe.lineTo(9439,floor-20);pipe.lineTo(9739,floor-20);pipe.lineTo(9739,floor+6);
        conduit(g,pipe,color,false);
        // The pressure gauge and exhaust stack are attached to the control platform.
        g.setColor(new Color(22,34,39));g.fillRoundRect(9386,190,18,78,5,5);
        g.setColor(new Color(104,125,126));g.drawRoundRect(9386,190,18,78,5,5);
        g.setColor(color);g.setStroke(new BasicStroke(2));g.drawOval(9377,223,18,18);
        g.drawLine(9386,232,open?9380:9391,open?236:225);
        if(open) {
            for(int i=0;i<4;i++) {
                double age=(scene.tick()+i*27)%110;
                g.setColor(new Color(177,217,219,(int)(55*(1-age/110))));
                g.fill(new Ellipse2D.Double(9392-age*.08,185-age*.46,10+age*.23,7+age*.14));
            }
            label(g,"explore.exhaust.active",9590,350,COOL);
        }
    }
    private static void brood(Graphics2D g,ChapterRouteController.Snapshot scene,int floor) {
        boolean quiet=scene.mechanisms().broodQuiet();
        Path2D nerve=new Path2D.Double();nerve.moveTo(8363,259);
        nerve.curveTo(8490,298,8530,422,8650,439);nerve.curveTo(8730,454,8820,436,8945,floor-37);
        conduit(g,nerve,quiet?DORMANT:NERVE,true);
        if(!quiet) {
            double travel=(scene.tick()%190)/190.0;
            double x=8390+travel*540,y=300+Math.sin(travel*Math.PI*.5)*136;
            g.setColor(new Color(190,224,152,130));g.fill(new Ellipse2D.Double(x-3,y-3,6,6));
        }
    }
    private static void nerve(Graphics2D g,ChapterRouteController.Snapshot scene,int floor) {
        boolean linked=scene.mechanisms().nerveLinked();
        Path2D root=new Path2D.Double();root.moveTo(9491,285);
        root.curveTo(9530,370,9620,458,9748,floor-12);
        conduit(g,root,linked?NERVE:new Color(113,91,128),true);
        if(linked && scene.mechanisms().membraneRetraction()==120) {
            ChapterArt.load().terrain(g,4,"prop-a",9700,floor-16,96,16);
            label(g,"explore.nerve.active",9748,floor-31,NERVE);
        }
        if(linked && scene.mechanisms().membraneRetraction()<120) {
            double t=scene.mechanisms().membraneRetraction()/120.0;
            g.setColor(new Color(216,238,171,170));
            g.fill(new Ellipse2D.Double(9491+257*t-4,285+179*t-4,8,8));
        }
    }
    private static void conduit(Graphics2D g,Shape path,Color light,boolean organic) {
        g.setColor(organic?new Color(40,31,46):new Color(23,34,39));
        g.setStroke(new BasicStroke(organic?8:6,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(path);
        g.setColor(new Color(light.getRed(),light.getGreen(),light.getBlue(),organic?125:135));
        g.setStroke(new BasicStroke(organic?2:1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(path);
    }
    private static boolean visible(double camera,int width,int left,int right){return camera+width>=left&&camera<=right;}
    private static void label(Graphics2D g,String key,int center,int y,Color color) {
        String text=GameText.message(key);g.setFont(GameText.font(new Font(Font.SANS_SERIF,Font.BOLD,12)));
        int width=g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(9,18,22,215));g.fillRoundRect(center-width/2-6,y-14,width+12,20,5,5);
        g.setColor(color);GameText.draw(g,text,center-width/2,y);
    }
    private LateChapterMechanismRenderer() { }
}
