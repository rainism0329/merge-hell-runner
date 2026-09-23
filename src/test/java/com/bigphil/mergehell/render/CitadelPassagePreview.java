package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.*;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.progression.CharacterId;
import com.bigphil.mergehell.world.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Production-rendered mechanism keyframes; labels identify fixture state, not a gameplay overlay. */
public final class CitadelPassagePreview {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length==0?"build/citadel-passage/visual":args[0]);Files.createDirectories(out);
        IndustrialArt art=IndustrialArt.load();ChapterArt.preload();
        try(var language=GameText.use(GameLanguage.ENGLISH)) {
            for(int phase=0;phase<4;phase++) {
                ExplorationRoute exploration=new ExplorationRoute(2);
                ChapterRouteController route=new ChapterRouteController(2,480);
                Player remote=new Player(9100,450);
                route.beforeMove(remote,false);
                if(phase>0)activate(exploration,1);
                if(phase>=2)activate(exploration,2);
                int ticks=phase==0?0:phase==1?90:phase==2?75:180;
                for(int i=0;i<ticks;i++) {
                    exploration.beforeMove(remote);route.syncExploration(exploration.visited());route.beforeMove(remote,false);
                }
                BufferedImage image=new BufferedImage(960,540,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                ChapterArt.load().backdrop(g,960,480,9100,2);
                new ChapterWorldRenderer().atmosphere(g,2,960,480,9100,ticks/60.0);
                Graphics2D world=(Graphics2D)g.create();world.translate(-9100,0);
                new ChapterWorldRenderer().world(world,route.snapshot(),9100,960,480,false);
                ExplorationRenderer.world(world,art,exploration.snapshot(),9100,960);
                ActorVisuals.hero(world,art,new ActorVisuals.Hero(9570,310,1,ActorVisuals.Action.IDLE,
                        0,0,0,false,false,1,1,0,false,CharacterId.REPAIR));
                world.dispose();
                g.setColor(new Color(8,16,23));g.fillRect(0,488,960,52);
                g.setColor(new Color(221,230,216));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));
                String title=switch(phase) {
                    case 0->"BEFORE / closed gate, lower jump route, upper bridge retracted";
                    case 1->"WINCH / real gate rises continuously; exact solid bounds follow it";
                    case 2->"BRIDGE LOCK / optional safe crossing extends from the upper console";
                    default->"READY / open underpass and connected upper crossing before the boss gate";
                };
                g.drawString(title,18,518);g.dispose();
                ImageIO.write(image,"png",out.resolve("passage-"+phase+".png").toFile());
            }
        }
        System.out.println(out.toAbsolutePath());
    }
    private static void activate(ExplorationRoute route,int id) {
        var point=route.snapshot().landmarks().stream().filter(p->p.id()==id).findFirst().orElseThrow();
        route.update(new Player(point.bounds().x,point.bounds().y),true);route.drainEvents();
    }
}
