package com.bigphil.mergehell.render;

import com.bigphil.mergehell.combat.HeroAim;
import com.bigphil.mergehell.combat.WeaponId;
import java.awt.*;
import java.awt.geom.*;

/** Weapon modules share the authored receiver and hand sockets. All barrels end at the real muzzle. */
public final class HeldWeaponRenderer {
    private HeldWeaponRenderer() { }

    public static Color accent(WeaponId weapon) {
        return switch (weapon) {
            case COMMIT_CANNON -> new Color(255,183,80);
            case FORCE_PUSH -> new Color(255,211,100);
            case RAPID_CI -> new Color(100,219,245);
            case GARBAGE_COLLECTOR -> new Color(153,222,137);
            case FIREWALL -> new Color(255,121,63);
            case REFACTOR_BEAM -> new Color(185,156,255);
        };
    }

    static AffineTransform muzzleTransform(ActorVisuals.Hero pose) {
        double ax=pose.aimX()*pose.facing();
        var muzzle=HeroAim.local(ax,pose.aimY(),pose.crouching(),pose.character());
        var result=AffineTransform.getTranslateInstance(muzzle.x(),muzzle.y());
        result.rotate(Math.atan2(pose.aimY(),ax)-pose.shot()*.04);
        return result;
    }

    static void render(Graphics2D target,IndustrialArt art,String id,ActorVisuals.Hero pose,AffineTransform cannon) {
        var frame=art.frame(id,"cannon").orElse(null);
        if(frame==null)return;
        var muzzleTransform=muzzleTransform(pose);
        Graphics2D stock=(Graphics2D)target.create();
        try {
            // Preserve the painted stock, trigger, and both grips; only the forward module changes.
            if(pose.weapon()!=WeaponId.COMMIT_CANNON)
                stock.clip(muzzleTransform.createTransformedShape(new Rectangle2D.Double(-28,-16,19,36)));
            stock.transform(cannon);
            art.part(stock,id,"cannon",0,0,frame.width(),frame.height());
        } finally {stock.dispose();}
        Graphics2D g=(Graphics2D)target.create();
        try {
            g.transform(muzzleTransform);
            g.setStroke(new BasicStroke(.65f));
            Color light=accent(pose.weapon());
            var skin=new ModuleSkin(g,art,id);
            switch(pose.weapon()) {
                case COMMIT_CANNON -> {
                    if(pose.evolved()) {
                        skin.metal(-12,-4.7,1.4,3.4);skin.metal(-8,-4.7,1.4,3.4);
                        skin.metal(-13,-6.7,7,2.6); // Optic and a raised ricochet rail.
                        g.setColor(light);g.fill(new Rectangle2D.Double(-7,-6.2,1.5,1.5));
                        skin.metal(-7,-3.7,6,1.4);
                    }
                }
                case FORCE_PUSH -> {
                    skin.metal(-13,-4,7,8);
                    skin.metal(-7,-3.5,7,2.8);skin.metal(-7,.7,7,2.8);
                    g.setColor(new Color(15,22,26));g.fill(new Rectangle2D.Double(-1.2,-3,1.2,6));
                    if(pose.evolved()) {skin.metal(-5,-5.2,4,1.5);skin.metal(-5,3.7,4,1.5);}
                    strip(g,light,-12,-3,4,1.2);
                }
                case RAPID_CI -> {
                    skin.metal(-13,-3.5,6,7);skin.metal(-8,-2,8,4);
                    skin.metal(-13,3.5,4,3.5); // Box magazine, separate from the support grip.
                    for(int i=0;i<3;i++)strip(g,new Color(16,24,28),-6+i*2,-1.3,1,2.6);
                    if(pose.evolved()) {
                        skin.metal(-8.5,-4.3,1.5,8.6);
                        skin.metal(-9,-4.6,8,1.5);skin.metal(-9,3.1,8,1.5);
                    }
                    strip(g,light,-12,-2.6,3,1.3);
                }
                case GARBAGE_COLLECTOR -> {
                    skin.metal(-14,-5.3,11,10.6);
                    skin.metal(-4,-4,4,8);
                    g.setColor(new Color(21,31,27));g.fill(new Ellipse2D.Double(-11,-3.4,5.8,6.8));
                    g.setColor(light);g.setStroke(new BasicStroke(pose.evolved()?1.1f:.65f));
                    g.draw(new Ellipse2D.Double(-10.5,-2.7,4.5,5.4));
                    skin.metal(-10.2,-1.3,3.8,2.6);
                    if(pose.evolved()) {skin.metal(-13,-6.7,7,1.8);skin.metal(-13,4.9,7,1.8);}
                }
                case FIREWALL -> {
                    skin.metal(-13,-3.4,7,6.8);skin.metal(-6,-1.7,6,3.4);
                    // Fuel cylinder and a vented heat shroud are recognizable at game scale.
                    skin.metal(-15,3.8,6,3.8);
                    for(int i=0;i<3;i++)skin.metal(-6+i*1.8,-3.2,1,6.4);
                    strip(g,light,-1.5,-.9,1.5,1.8);
                    if(pose.evolved()) {
                        g.setColor(light);g.setStroke(new BasicStroke(.8f));
                        g.draw(new Arc2D.Double(-9,-5,7,10,75,210,Arc2D.OPEN));
                    }
                }
                case REFACTOR_BEAM -> {
                    skin.metal(-14,-3.3,6,6.6);
                    skin.metal(-9,-3.7,9,1.6);skin.metal(-9,2.1,9,1.6);
                    strip(g,light,-10,-.65,10,1.3);
                    if(pose.evolved()) {
                        skin.metal(-11.5,-5.5,1.5,11);
                        skin.metal(-11,-6,7,1.5);skin.metal(-11,4.5,7,1.5);
                        g.setColor(light);g.draw(new Line2D.Double(-3,-4.5,0,0));
                        g.draw(new Line2D.Double(-3,4.5,0,0));
                    }
                }
            }
        } finally {g.dispose();}
    }

    private record ModuleSkin(Graphics2D g,IndustrialArt art,String id) {
        void metal(double x,double y,double w,double h) {
            Color alloy=switch(id) {
                case "repair" -> new Color(188,139,61);
                case "scout" -> new Color(162,167,146);
                case "warden" -> new Color(123,123,121);
                default -> new Color(132,119,91);
            };
            double bevel=Math.min(.9,Math.min(w,h)/3);
            var body=new Path2D.Double();body.moveTo(x+bevel,y);body.lineTo(x+w-bevel,y);
            body.lineTo(x+w,y+bevel);body.lineTo(x+w,y+h-bevel);body.lineTo(x+w-bevel,y+h);
            body.lineTo(x+bevel,y+h);body.lineTo(x,y+h-bevel);body.lineTo(x,y+bevel);body.closePath();
            g.setPaint(new GradientPaint((float)x,(float)y,alloy,
                    (float)x,(float)(y+h),new Color(32,40,42)));
            g.fill(body);
            // Reuse the character's painted metal, then cut readable seams and fasteners into it.
            if(w>3&&h>3) {
                Graphics2D wear=(Graphics2D)g.create();
                try {
                    wear.clip(body);
                    if(wear.getComposite() instanceof AlphaComposite alpha)
                        wear.setComposite(alpha.derive(alpha.getAlpha()*.45f));
                    art.part(wear,id,"cannon",x-2,y-2,w+5,h+5);
                }finally {wear.dispose();}
                g.setStroke(new BasicStroke(.45f));g.setColor(new Color(13,22,25,210));
                g.draw(new Line2D.Double(x+w-1.8,y+1,x+w-1.8,y+h-1));
                g.fill(new Ellipse2D.Double(x+1,y+h-2.1,.9,.9));
                g.setColor(new Color(214,209,179,170));
                g.draw(new Line2D.Double(x+1.5,y+1,x+w*.65,y+1));
            }
            g.setStroke(new BasicStroke(.6f));g.setColor(new Color(17,25,30));g.draw(body);
            g.setColor(new Color(217,215,181,150));
            g.draw(new Line2D.Double(x+bevel,y+.4,x+w-bevel,y+.4));
        }
    }
    private static void strip(Graphics2D g,Color color,double x,double y,double w,double h) {
        g.setColor(color);g.fill(new Rectangle2D.Double(x,y,w,h));
    }
}
