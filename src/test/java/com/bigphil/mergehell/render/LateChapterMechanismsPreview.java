package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.*;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.progression.CharacterId;
import com.bigphil.mergehell.world.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Set;

/** Fixed-camera, production-rendered before/after views of actual exploration state changes. */
public final class LateChapterMechanismsPreview {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length==0?"build/chapter-mechanisms-20260921/visual":args[0]);Files.createDirectories(out);
        IndustrialArt art=IndustrialArt.load();ChapterArt.preload();
        try(var language=GameText.use(GameLanguage.ENGLISH)) {
            for(int chapter=3;chapter<=4;chapter++)for(int control=1;control<=2;control++)for(int phase=0;phase<3;phase++) {
                var exploration=new ExplorationRoute(chapter);var route=new ChapterRouteController(chapter,480);
                if(control==2)exploration.restore(Set.of(1));
                route.syncExploration(exploration.visited());
                int controlId=control;
                var point=exploration.snapshot().landmarks().stream().filter(p->p.id()==controlId).findFirst().orElseThrow();
                Player player=new Player(point.bounds().x,point.bounds().y);
                route.beforeMove(player,false);
                if(phase>0)exploration.update(player,true);
                int ticks=phase==0?70:phase==1?60:150;
                for(int i=0;i<ticks;i++) {
                    route.syncExploration(exploration.visited());route.beforeMove(player,false);
                    route.afterMove(player,player.getX(),0);
                }
                int camera=control==1?8100:9200;
                BufferedImage image=new BufferedImage(960,540,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                ChapterArt.load().backdrop(g,960,480,camera,chapter);
                new ChapterWorldRenderer().atmosphere(g,chapter,960,480,camera,ticks/60.0);
                Graphics2D world=(Graphics2D)g.create();world.translate(-camera,0);
                new ChapterWorldRenderer().world(world,route.snapshot(),camera,960,480,false);
                ExplorationRenderer.world(world,art,exploration.snapshot(),camera,960);
                ActorVisuals.hero(world,art,new ActorVisuals.Hero(point.bounds().x,point.bounds().y+30,1,
                        ActorVisuals.Action.IDLE,0,0,0,false,false,1,1,0,false,CharacterId.REPAIR));
                world.dispose();g.setColor(new Color(8,16,23));g.fillRect(0,490,960,50);
                g.setColor(new Color(223,231,216));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));
                String title="CHAPTER "+(chapter+1)+" / "+(control==1?"PRIMARY CONTROL":"OPTIONAL SAFETY ROUTE")
                        +" / "+(phase==0?"BEFORE":phase==1?"RESPONSE":"SETTLED");
                g.drawString(title,18,520);g.dispose();
                ImageIO.write(image,"png",out.resolve("chapter"+(chapter+1)+"-control"+control+"-"+phase+".png").toFile());
            }
        }
        System.out.println(out.toAbsolutePath());
    }
}
