package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.model.Platform;
import com.bigphil.mergehell.world.ChapterRouteController;
import com.bigphil.mergehell.world.ChapterRouteController.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Foreground construction follows collision geometry; decoration never invents a platform. */
public final class ChapterWorldRenderer {
    private static final Color INK=new Color(13,18,24), CREAM=new Color(238,224,192);
    private static final BufferedImage[] MATERIALS={material(2),material(3),material(4)};
    private static final BufferedImage[] MOLTEN_FLOW={moltenFlow(false),moltenFlow(true)};
    private static final Font LABEL=new Font(Font.SANS_SERIF,Font.BOLD,10);
    private boolean compactLabels;

    public void atmosphere(Graphics2D original,int level,int width,int groundY,double camera,double seconds) {
        Graphics2D g=(Graphics2D)original.create();
        try {
            g.clipRect(0,0,width,groundY);
            // The action lane has restrained local contrast; the illustration remains the distant world.
            Color shadow=level==2?new Color(13,25,38,135):level==3?new Color(8,13,18,120):new Color(13,8,29,120);
            g.setPaint(new GradientPaint(0,groundY-175,new Color(0,0,0,0),0,groundY,shadow));
            g.fillRect(0,groundY-175,width,175);
            for(int i=0;i<16;i++) {
                double x=Math.floorMod((int)(i*127-camera*.24+seconds*(level==2?18:4)),width+70)-35;
                double y=80+Math.floorMod((int)(i*67-seconds*(level==3?18:5)),350);
                if(level==2) {
                    g.setColor(new Color(231,220,187,35)); g.draw(new Line2D.Double(x,y,x+17,y-2));
                } else if(level==3) {
                    g.setColor(new Color(255,173,73,70)); g.fill(new Ellipse2D.Double(x,y,2,3));
                } else {
                    g.setColor(new Color(171,222,147,45)); g.fill(new Ellipse2D.Double(x,y,4,4));
                    g.setColor(new Color(215,244,171,100)); g.fill(new Ellipse2D.Double(x+1,y+1,1.4,1.4));
                }
            }
        }finally{g.dispose();}
    }

    public void world(Graphics2D original,Snapshot scene,double camera,int width,int groundY,boolean compact) {
        if(scene.level()<2)return;
        compactLabels=compact;
        Graphics2D g=(Graphics2D)original.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int left=(int)camera-80,right=(int)camera+width+80;
            // The sky chapter has genuine cut-outs: do not draw a continuous floor beneath them.
            double cursor=left;
            for(Surface s:scene.surfaces()) if(s.kind()==SurfaceKind.GAP && !scene.arena()) {
                if(s.x()+s.width()<left || s.x()>right)continue;
                if(s.x()>cursor) deck(g,scene.level(),cursor,groundY,(int)(s.x()-cursor),false);
                cursor=Math.max(cursor,s.x()+s.width());
                edge(g,s.x(),groundY,-1); edge(g,s.x()+s.width(),groundY,1);
            }
            if(cursor<right)deck(g,scene.level(),cursor,groundY,(int)(right-cursor),false);
            for(Surface s:scene.surfaces()) if(s.x()+s.width()>=left && s.x()<=right && !scene.arena()) surface(g,s,scene,groundY);
            for(Platform p:scene.platforms()) if(p.x+p.width>=left && p.x<=right) {
                boolean lift=scene.lifts().stream().anyMatch(l->l.platform()==p);
                deck(g,scene.level(),p.x,p.y,p.width,true);
                if(scene.level()==2) {
                    g.setColor(new Color(35,38,39,200));g.setStroke(new BasicStroke(3));
                    if(lift) {
                        Lift moving=scene.lifts().stream().filter(l->l.platform()==p).findFirst().orElseThrow();
                        g.draw(new Line2D.Double(p.x+9,moving.anchorY(),p.x+9,p.y));
                        g.draw(new Line2D.Double(p.x+p.width-9,moving.anchorY(),p.x+p.width-9,p.y));
                        g.setColor(new Color(230,188,98));g.fill(new Rectangle2D.Double(p.x+6,p.y+2,p.width-12,3));
                    } else if(p.x!=9560) support(g,scene.level(),p,groundY);
                } else if(scene.level()==3) {
                    support(g,scene.level(),p,groundY);
                } else support(g,scene.level(),p,groundY);
            }
            if(!scene.arena()) for(Prop prop:scene.props()) if(prop.bounds().getMaxX()>=left && prop.bounds().x<=right) prop(g,prop,scene,groundY);
            CitadelMechanismRenderer.bridge(g,scene,camera,width,groundY);
            LateChapterMechanismRenderer.world(g,scene,camera,width,groundY);
        }finally{g.dispose();}
    }
    static void deck(Graphics2D g,int level,double x,double y,int width,boolean floating) {
        if(width<=0)return;
        BufferedImage material=MATERIALS[level-2];
        g.setPaint(new TexturePaint(material,new Rectangle(0,(int)y-6,material.getWidth(),material.getHeight())));
        Shape body=level==4?organicDeck(x,y,width):new Rectangle2D.Double(x,y-5,width,floating?26:100);
        g.fill(body);
        ChapterArt art=ChapterArt.load();
        if(art.terrainFrame(level,floating?"platform":"deck").isPresent()) {
            if(floating)art.terrain(g,level,"platform",x,y-3,width,Math.max(24,Math.min(46,width*.24)));
            else {
                Graphics2D tiles=(Graphics2D)g.create();
                try {
                    tiles.clip(new Rectangle2D.Double(x,y-3,width,102));
                    for(double tx=Math.floor(x/256)*256;tx<x+width;tx+=256)
                        art.terrain(tiles,level,"deck",tx,y-3,257,96);
                }finally{tiles.dispose();}
            }
            g.setColor(level==4?new Color(197,201,149,180):new Color(222,216,185,180));
            g.setStroke(new BasicStroke(1));g.draw(new Line2D.Double(x,y,x+width,y));
            return;
        }
        g.setColor(level==2?new Color(209,200,162):level==3?new Color(172,150,112):new Color(154,164,112));
        g.setStroke(new BasicStroke(2));g.draw(new Line2D.Double(x,y,x+width,y));
        if(level!=4) {
            g.setColor(new Color(10,17,24,175));g.fill(new Rectangle2D.Double(x,y+6,width,5));
            for(int i=0;i<width;i+=48) {
                g.setColor(new Color(186,176,140,110));g.fill(new Ellipse2D.Double(x+i+8,y+15,3,3));
                if(level==2) {
                    g.setColor(new Color(36,41,42));g.setStroke(new BasicStroke(4));
                    g.draw(new Line2D.Double(x+i+3,y+25,x+i+42,y+50));
                    g.draw(new Line2D.Double(x+i+3,y+50,x+i+42,y+25));
                }
            }
        } else {
            g.setColor(new Color(200,225,131,85));g.setStroke(new BasicStroke(1.1f));
            for(int i=10;i<width;i+=38)g.draw(new CubicCurve2D.Double(x+i,y+20,x+i+4,y+6,x+i+22,y+28,x+i+30,y+8));
        }
    }
    private static void support(Graphics2D g,int level,Platform p,int floor) {
        double height=floor-p.y-12;
        if(height<24)return;
        int count=p.width>180?2:1;
        for(int i=0;i<count;i++) {
            double x=p.x+(count==1?p.width*.5-15:i==0?18:p.width-48);
            ChapterArt.load().terrain(g,level,"support",x,p.y+18,30,height);
        }
    }
    private static Shape organicDeck(double x,double y,int w) {
        Path2D p=new Path2D.Double();p.moveTo(x,y);p.lineTo(x+w,y);
        p.curveTo(x+w+4,y+25,x+w*.75,y+18,x+w*.5,y+30);
        p.curveTo(x+w*.25,y+42,x-8,y+22,x,y);return p;
    }
    private static void edge(Graphics2D g,double x,int y,int dir) {
        g.setColor(new Color(204,175,112));g.fill(new Rectangle2D.Double(x+(dir<0?-13:0),y-13,13,17));
        g.setColor(new Color(32,33,32));g.setStroke(new BasicStroke(3));
        for(int i=0;i<3;i++)g.draw(new Line2D.Double(x+dir*3,y-10+i*6,x+dir*11,y-14+i*6));
    }
    private void surface(Graphics2D g,Surface s,Snapshot scene,int floor) {
        if(s.kind()==SurfaceKind.GAP)return;
        double x=s.x();int w=s.width();
        if(s.kind()==SurfaceKind.CONVEYOR) {
            g.setColor(new Color(12,18,23));g.fill(new RoundRectangle2D.Double(x,floor-8,w,21,14,14));
            ChapterArt.load().terrain(g,3,"trim",x,floor-6,w,27);
            g.setColor(new Color(139,146,138));g.setStroke(new BasicStroke(2));
            for(int i=-1;i<w/18+2;i++) {
                double stripe=x+i*18+Math.floorMod(scene.tick()*s.phase(),18);
                if(stripe>=x&&stripe<x+w)g.draw(new Line2D.Double(stripe,floor-6,stripe+5,floor+1));
            }
            g.setColor(new Color(226,185,95));
            for(int i=20;i<w;i+=85) {
                int dir=s.phase();Path2D a=new Path2D.Double();a.moveTo(x+i-dir*4,floor-17);a.lineTo(x+i+dir*3,floor-14);a.lineTo(x+i-dir*4,floor-11);g.draw(a);
            }
            return;
        }
        if(s.kind()==SurfaceKind.VENT) {
            g.setColor(new Color(24,30,34));g.fill(new RoundRectangle2D.Double(x,floor-7,w,20,9,9));
            g.setColor(new Color(111,113,101));for(int i=8;i<w;i+=12)g.fill(new Rectangle2D.Double(x+i,floor-4,5,7));
            ChapterArt.load().terrain(g,3,"prop-b",x,floor-6,w,24);
            boolean warning=s.phase()>=0 && s.phase()<90;
            if(warning||s.active()) {
                g.setColor(s.active()?new Color(241,181,107,115):new Color(229,174,91,35));
                for(int i=0;i<5;i++) {
                    double rise=(scene.tick()*2.4+i*23)%120;
                    double swell=12+rise*.32;
                    g.fill(new Ellipse2D.Double(x+w*.5-swell*.5+Math.sin(i+rise*.05)*13,floor-rise-14,swell,26));
                }
                g.setColor(new Color(247,196,112));g.setStroke(new BasicStroke(1.4f));
                g.draw(new Line2D.Double(x,floor-3,x+w*(warning?s.phase()/90.0:1),floor-3));
            }
            return;
        }
        boolean acid=s.kind()==SurfaceKind.ACID;
        if(!acid) { moltenChannel(g,s,scene.tick(),floor); return; }
        Color dark=acid?new Color(44,62,43):new Color(103,37,23);
        Color light=acid?new Color(184,225,109):s.active()?new Color(255,186,66):new Color(129,170,178);
        g.setPaint(new GradientPaint((float)x,floor-3,light,(float)x,floor+22,dark));
        g.fill(new RoundRectangle2D.Double(x,floor-3,w,29,25,12));
        if(acid)ChapterArt.load().terrain(g,4,"trim",x,floor-20,w,67);
        g.setColor(new Color(light.getRed(),light.getGreen(),light.getBlue(),70));
        for(int i=0;i<6;i++) {
            double bx=x+Math.floorMod((int)(i*53+scene.tick()*.13),w-12);
            double by=floor-3+Math.sin(scene.tick()*.04+i)*2;
            g.draw(new Ellipse2D.Double(bx,by,10+i%3*4,3));
        }
        g.setColor(new Color(12,20,25));g.setStroke(new BasicStroke(4));
        g.draw(new Line2D.Double(x,floor-4,x+12,floor+5));g.draw(new Line2D.Double(x+w-12,floor+5,x+w,floor-4));
    }
    private static void moltenChannel(Graphics2D target,Surface surface,long tick,int floor) {
        Graphics2D g=(Graphics2D)target.create();
        try {
            double x=surface.x();int w=surface.width();boolean hot=surface.active();
            // The aperture is inset into the same bolted material as the surrounding floor.
            // Its far lip stays on the authoritative walking surface, never an extra ledge.
            ChapterArt.load().terrain(g,3,"platform",x-4,floor-5,w+8,43);
            Path2D opening=new Path2D.Double();opening.moveTo(x+5,floor-2);
            opening.lineTo(x+w-5,floor-2);opening.lineTo(x+w-12,floor+16);
            opening.lineTo(x+w*.72,floor+18);opening.lineTo(x+w*.48,floor+15);
            opening.lineTo(x+w*.23,floor+18);opening.lineTo(x+12,floor+15);opening.closePath();
            g.setColor(new Color(8,13,17));g.setStroke(new BasicStroke(5));g.draw(opening);
            Graphics2D liquid=(Graphics2D)g.create();
            try {
                liquid.clip(opening);
                BufferedImage flow=MOLTEN_FLOW[hot?1:0];
                double drift=(tick*(hot?.18:.025))%flow.getWidth();
                liquid.setPaint(new TexturePaint(flow,new Rectangle2D.Double(x-drift,floor-5,flow.getWidth(),32)));
                liquid.fill(opening);
                for(int row=0;row<4;row++) {
                    Path2D stream=new Path2D.Double();
                    for(int i=0;i<=w;i+=6) {
                        double y=floor+row*5+Math.sin(i*.081+row*2.4+tick*.019)*1.4
                                +Math.sin(i*.17-row+tick*.012)*.65;
                        if(i==0)stream.moveTo(x+i,y);else stream.lineTo(x+i,y);
                    }
                    liquid.setStroke(new BasicStroke(row%2==0?1.4f:.7f));
                    liquid.setColor(hot?new Color(255,222,117,95):new Color(113,163,169,85));
                    liquid.draw(stream);
                }
                // Small dark cooling rafts break the glow into flowing veins instead of a flat bar.
                for(int i=0;i<9;i++) {
                    double px=x+Math.floorMod((int)(i*47+tick*(hot?.13:.018)),Math.max(1,w));
                    double py=floor+2+(i*7)%12;
                    Path2D crust=new Path2D.Double();crust.moveTo(px-8,py);crust.lineTo(px+1,py-1.5);
                    crust.lineTo(px+10,py+.5);crust.lineTo(px+4,py+2.8);crust.lineTo(px-6,py+2);crust.closePath();
                    liquid.setColor(hot?new Color(63,33,26,170):new Color(27,38,42,190));liquid.fill(crust);
                }
            }finally{liquid.dispose();}
            g.setColor(hot?new Color(242,148,57,130):new Color(125,165,169,105));
            g.setStroke(new BasicStroke(1));g.draw(new Line2D.Double(x+5,floor-3,x+w-5,floor-3));
            // The nearest steel lip remains painted and catches a restrained reflection.
            g.setPaint(new GradientPaint((float)x,floor+18,
                    hot?new Color(255,108,31,75):new Color(125,175,178,40),
                    (float)x,floor+29,new Color(0,0,0,0)));
            g.fill(new Rectangle2D.Double(x+8,floor+18,w-16,11));
        }finally{g.dispose();}
    }
    private void prop(Graphics2D g,Prop p,Snapshot s,int groundY) {
        Rectangle b=p.bounds();
        if(paintedProp(g,p,s,groundY))return;
        if(p.kind()==Kind.COUNTERWEIGHT) {
            double anchor=b.y-170;
            g.setColor(new Color(49,51,48));g.setStroke(new BasicStroke(4));g.draw(new Line2D.Double(b.getCenterX(),anchor,b.getCenterX(),p.hp()>0?b.y:groundY-45));
            g.setColor(new Color(183,153,91));g.draw(new Ellipse2D.Double(b.getCenterX()-8,anchor-8,16,16));
            if(p.hp()>0) {
                g.setPaint(new GradientPaint(b.x,b.y,new Color(236,186,88),b.x+b.width,b.y,new Color(84,75,51)));
                g.fillRoundRect(b.x,b.y,b.width,b.height,4,4);g.setColor(INK);g.setStroke(new BasicStroke(4));
                for(int i=9;i<b.height;i+=14)g.drawLine(b.x+5,b.y+i,b.x+b.width-5,b.y+i-5);
                label(g,"chapter.prop.counterweight",b.x-35,b.y-11,100);
            }
        } else if(p.kind()==Kind.COOLANT) {
            g.setStroke(new BasicStroke(11));g.setColor(new Color(32,50,56));
            g.draw(new CubicCurve2D.Double(b.x+24,groundY+15,b.x+24,b.y-35,b.x-50,b.y-25,b.x-95,b.y-25));
            g.setStroke(new BasicStroke(3));g.setColor(new Color(133,173,172));
            g.draw(new CubicCurve2D.Double(b.x+20,groundY+15,b.x+20,b.y-32,b.x-50,b.y-28,b.x-95,b.y-28));
            g.setPaint(new GradientPaint(b.x,b.y,new Color(115,153,158),b.x+b.width,b.y,new Color(23,44,53)));
            g.fillRoundRect(b.x,b.y,b.width,b.height,20,15);g.setColor(INK);g.fillRoundRect(b.x+10,b.y+12,27,49,5,5);
            g.setColor(p.effectTicks()>0?new Color(135,236,239):new Color(238,188,93));g.fillRoundRect(b.x+16,b.y+18,15,37,4,4);
            if(p.effectTicks()>0)for(int i=0;i<5;i++) {
                double t=(s.tick()+i*17)%80;g.setColor(new Color(175,217,218,(int)(70-t*.65)));
                g.fill(new Ellipse2D.Double(b.x-24-t*.25,b.y-t*.45,35+t*.3,16+t*.15));
            }
            label(g,p.effectTicks()>0?"chapter.coolant.cooling":"chapter.prop.coolant",b.x-60,b.y-42,168);
        } else if(p.kind()==Kind.MEMBRANE) {
            if(p.hp()==0) {root(g,b.getCenterX(),groundY-30,groundY,s.tick(),true);return;}
            double pulse=1+Math.sin(s.tick()*.045+p.id())*.025;
            root(g,b.getCenterX(),b.y-30,groundY,s.tick(),true);
            g.setPaint(new GradientPaint(b.x,b.y,new Color(151,96,156,195),b.x+b.width,b.y,new Color(53,37,79,240)));
            g.fill(new Ellipse2D.Double(b.x,b.y,b.width*pulse,b.height));
            g.setColor(new Color(220,153,217,170));g.setStroke(new BasicStroke(1));
            for(int i=1;i<6;i++)g.draw(new CubicCurve2D.Double(b.x+5,b.y+i*22,b.x+30,b.y+i*25,b.x+15,b.y+i*14,b.x+b.width-4,b.y+i*20));
            label(g,"chapter.prop.membrane",b.x-40,b.y-20,124);
        } else if(p.kind()==Kind.NEST) {
            root(g,b.getCenterX(),b.y+15,groundY,s.tick(),true);
            if(p.hp()==0)return;
            double pulse=Math.sin(s.tick()*.04+p.id())*2;
            for(int i=0;i<4;i++) {
                double x=b.x+7+i*14,y=b.y+18+Math.sin(i*1.9)*11;
                g.setPaint(new RadialGradientPaint(new Point2D.Double(x+13,y+20),(float)(32+pulse),new float[]{0,.5f,1},
                        new Color[]{new Color(192,222,119),new Color(92,104,79),new Color(48,29,64)}));
                g.fill(new Ellipse2D.Double(x,y,29+pulse,53));
                g.setColor(new Color(232,225,170,100));g.draw(new Arc2D.Double(x+5,y+8,17,33,70,110,Arc2D.OPEN));
            }
            label(g,p.warningTicks()>0?"chapter.nest.warning":"chapter.prop.nest",b.x-28,b.y-16,140);
        }
        if(p.hp()>0 && p.hp()<p.maxHp()) {
            g.setColor(INK);g.fillRoundRect(b.x,b.y-5,b.width,3,2,2);
            g.setColor(new Color(238,199,126));g.fillRoundRect(b.x,b.y-5,b.width*p.hp()/p.maxHp(),3,2,2);
        }
    }
    private boolean paintedProp(Graphics2D g,Prop p,Snapshot scene,int floor) {
        ChapterArt art=ChapterArt.load();int level=scene.level();Rectangle b=p.bounds();
        if(art.terrainFrame(level,"prop-a").isEmpty())return false;
        String caption;
        if(p.kind()==Kind.COUNTERWEIGHT) {
            double anchor=b.y-166;
            g.setStroke(new BasicStroke(3));g.setColor(new Color(40,43,43));
            g.draw(new Line2D.Double(b.getCenterX(),anchor+35,b.getCenterX(),p.hp()>0?b.y:floor-45));
            art.terrain(g,level,"prop-b",b.getCenterX()-24,anchor,48,57);
            if(p.hp()==0)return true;
            art.terrain(g,level,"prop-a",b.x,b.y,b.width,b.height);
            caption="chapter.prop.counterweight";
        } else if(p.kind()==Kind.COOLANT) {
            // The illustration includes hoses connected to its footplate; no floating pipe stub.
            art.terrain(g,level,"prop-a",b.x,b.y,b.width,b.height);
            if(p.effectTicks()>0)for(int i=0;i<5;i++) {
                double t=(scene.tick()+i*17)%80;g.setColor(new Color(175,217,218,(int)(70-t*.65)));
                g.fill(new Ellipse2D.Double(b.x+10-t*.25,b.y-t*.45,25+t*.3,16+t*.15));
            }
            caption=p.effectTicks()>0?"chapter.coolant.cooling":"chapter.prop.coolant";
        } else if(p.kind()==Kind.MEMBRANE) {
            if(p.hp()==0){root(g,b.getCenterX(),floor-18,floor,scene.tick(),true);return true;}
            boolean relaxing=level==4&&b.x==9700&&scene.mechanisms().nerveLinked();
            if(relaxing)art.terrain(g,level,"prop-a",b.x,b.y,b.width,b.height);
            else art.fitTerrain(g,level,"prop-a",b.x,b.y,b.width,b.height);
            caption=relaxing?(scene.mechanisms().membraneRetraction()==120?"explore.nerve.active":"explore.nerve.motion")
                    :"chapter.prop.membrane";
        } else {
            if(p.hp()==0){root(g,b.getCenterX(),floor-12,floor,scene.tick(),false);return true;}
            boolean quiet=level==4&&b.x>=8000&&scene.mechanisms().broodQuiet();
            double pulse=quiet?0:Math.sin(scene.tick()*.05+p.id())*1.2;
            art.terrain(g,level,"prop-b",b.x-pulse,b.y-pulse,b.width+pulse*2,b.height+pulse);
            if(p.warningTicks()>0) {
                g.setColor(new Color(191,236,109,175));g.setStroke(new BasicStroke(2));
                g.draw(new Arc2D.Double(b.getCenterX()-10,b.y-28,20,20,90,-360*(1-p.warningTicks()/120.0),Arc2D.OPEN));
            }
            caption=quiet?"explore.uppernest.active":p.warningTicks()>0?"chapter.nest.warning":"chapter.prop.nest";
        }
        int labelWidth=compactLabels?180:140;
        label(g,caption,b.getCenterX()-labelWidth*.5,b.y-(p.kind()==Kind.COOLANT?34:12),labelWidth);
        if(p.hp()>0&&p.hp()<p.maxHp()) {
            g.setColor(INK);g.fillRoundRect(b.x,b.y-5,b.width,3,2,2);
            g.setColor(new Color(238,199,126));g.fillRoundRect(b.x,b.y-5,b.width*p.hp()/p.maxHp(),3,2,2);
        }
        return true;
    }
    private static void root(Graphics2D g,double x,double top,double floor,long ticks,boolean thick) {
        for(int i=-1;i<=1;i++) {
            double bend=Math.sin(i*2.8+x)*26;
            CubicCurve2D c=new CubicCurve2D.Double(x+i*10,top,x+bend,top+40,x-bend,floor-24,x+i*35,floor+10);
            g.setColor(new Color(35,27,49));g.setStroke(new BasicStroke(thick?12:8,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(c);
            g.setColor(new Color(112,90,122));g.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(c);
            g.setColor(new Color(171,185,121,90));g.setStroke(new BasicStroke(1));g.draw(c);
        }
    }
    private void label(Graphics2D g,String key,double x,double y,int width) {
        String text=GameText.message(key);g.setFont(GameText.font(LABEL.deriveFont(compactLabels?16f:11f)));
        GameText.fitFont(g,text,width-10,compactLabels?13:9);int actual=g.getFontMetrics().stringWidth(text)+12;
        g.setColor(new Color(11,17,25,220));g.fillRoundRect((int)x,(int)y-g.getFontMetrics().getAscent()-3,actual,g.getFontMetrics().getHeight()+6,5,5);
        g.setColor(CREAM);g.drawString(text,(float)x+6,(float)y);
    }
    public void hud(Graphics2D g,Snapshot scene,int width,boolean boss) {
        if(scene.level()<2 || boss)return;
        String title=GameText.message(scene.sectionKey());
        g.setFont(GameText.font(new Font(Font.SANS_SERIF,Font.BOLD,compactLabels?16:12)));
        GameText.fitFont(g,title,Math.min(430,width-50),compactLabels?13:10);
        int textWidth=g.getFontMetrics().stringWidth(title);
        g.setColor(new Color(8,15,23,205));g.fillRoundRect(20,112,textWidth+22,25,7,7);
        g.setColor(CREAM);g.drawString(title,31,129);
    }
    private static BufferedImage material(int level) {
        BufferedImage image=new BufferedImage(192,96,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        Color top=level==2?new Color(96,95,79):level==3?new Color(58,62,58):new Color(68,52,76);
        Color bottom=level==2?new Color(30,36,39):level==3?new Color(19,25,29):new Color(27,25,40);
        g.setPaint(new GradientPaint(0,0,top,0,65,bottom));g.fillRect(0,0,192,96);
        Random random=new Random(7200+level);
        for(int i=0;i<1100;i++) {
            int value=random.nextInt(36)+40;g.setColor(new Color(value,value-4,value-9,random.nextInt(90)+15));
            int x=random.nextInt(192),y=random.nextInt(96);g.fillRect(x,y,random.nextInt(9)+1,1);
        }
        g.setColor(new Color(8,14,21,150));g.setStroke(new BasicStroke(2));
        for(int i=0;i<4;i++){int x=i*48+9;g.drawLine(x,12,x+8,35);g.drawLine(x+8,35,x-7,54);}
        g.dispose();return image;
    }
    private static BufferedImage moltenFlow(boolean hot) {
        BufferedImage image=new BufferedImage(256,32,BufferedImage.TYPE_INT_RGB);
        Random grain=new Random(41727);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            double ribbon=Math.sin(x*Math.PI/32+Math.sin(y*.37)*2.1)
                    +Math.sin(x*Math.PI/16-y*.52)*.46+Math.sin(x*Math.PI/8+y*.82)*.2;
            double value=Math.max(0,Math.min(1,.43+ribbon*.2+grain.nextDouble()*.14-y*.005));
            int r=hot?(int)(113+value*141):(int)(30+value*51);
            int g=hot?(int)(43+value*154):(int)(43+value*72);
            int b=hot?(int)(21+value*57):(int)(49+value*77);
            image.setRGB(x,y,(r<<16)|(g<<8)|b);
        }
        return image;
    }
}
