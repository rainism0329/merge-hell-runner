package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.*;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.persistence.*;
import com.bigphil.mergehell.world.ChapterRouteController;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Captures the actual panel. Fixtures select chapter/position/enemies and damage boss parts. */
public final class ChapterCampaignPreview {
    private final Path out;
    private final List<String> transcript=new ArrayList<>(), evidence=new ArrayList<>();
    private ChapterCampaignPreview(Path out)throws Exception {this.out=out;Files.createDirectories(out);}
    public static void main(String[] args)throws Exception {
        var preview=new ChapterCampaignPreview(Path.of(args.length==0?"build/chapter-overhaul/previews":args[0]));
        for(GameLanguage language:GameLanguage.values())for(int level=2;level<=4;level++)preview.chapter(language,level);
        Files.write(preview.out.resolve("displayed-text.txt"),preview.transcript);
        Files.write(preview.out.resolve("scene-evidence.csv"),preview.evidence);
        System.out.println("Captured "+preview.evidence.size()+" real campaign frames: "+preview.out.toAbsolutePath());
    }
    private void chapter(GameLanguage language,int level)throws Exception {
        var stored=new MergeHellState();stored.settings.language=language.tag();stored.settings.muted=true;
        MergeHellStateService.getInstance().loadState(stored);
        try(var h=new HeapGameHarness()) {
            h.set("level",level);h.invoke("advanceLevel");h.tick();
            capture(h,language,level,"entrance");
            for(int section=0;section<3;section++) {
                int x=(level==2?new int[]{730,3000,6670}:level==3?new int[]{920,3150,5680}:new int[]{730,3500,6680})[section];
                h.place(x,level==2?400:450);quiet(h,3);
                capture(h,language,level,"route-"+(section+1));
            }
            h.place(200,450);h.enemies().clearHostiles();
            EntityType[] types=level==2?new EntityType[]{EntityType.SENTINEL,EntityType.WARDEN,EntityType.RIGGER}:
                    level==3?new EntityType[]{EntityType.INTERRUPT,EntityType.DRILLER,EntityType.SLAG_SPITTER}:
                    new EntityType[]{EntityType.MIRROR,EntityType.SPORE_POD,EntityType.LURKER};
            for(int i=0;i<3;i++)h.enemies().spawnEnemy(440+i*155,ObstacleManager.specialistSpawnY(types[i],480,i*18),types[i]);
            h.ticks(35);capture(h,language,level,"enemy-squad");
            h.ticks(35);capture(h,language,level,"enemy-warning");
            ((LevelManager)h.get("levelManager")).advanceToBossGateForTesting();h.place(7600,450);
            quiet(h,80);capture(h,language,level,"boss-arrival");
            for(int i=0;i<250&&h.state()!=GameState.BOSS_FIGHT;i++)quiet(h,1);
            quiet(h,35);capture(h,language,level,"boss-ready");
            for(int phase=1;phase<=3;phase++) {
                if(phase>1)h.boss().damage(Math.max(1,h.boss().getHp()-(int)(h.boss().getMaxHp()*(phase==2?.60:.25))));
                for(int tick=0;tick<650&&h.boss().getWarningTicks()==0;tick++)quiet(h,1);
                capture(h,language,level,"boss-phase-"+phase);
                quiet(h,Math.max(1,h.boss().getWarningTicks()+2));
                capture(h,language,level,"boss-attack-"+phase);
            }
            for(var part:List.copyOf(h.boss().getParts()))if(!part.id().equals("core")&&part.targetable()&&!part.destroyed())
                h.boss().damageAt(part.bounds().rectangle(),Math.max(1,part.hp()));
            capture(h,language,level,"boss-dismantled");
        }
    }
    private void capture(HeapGameHarness h,GameLanguage language,int level,String scene)throws Exception {
        for(int width:new int[]{960,600}) {
            int height=width==960?600:400;
            SwingUtilities.invokeAndWait(()->h.panel.setSize(width,height));SwingUtilities.invokeAndWait(()->{});
            h.tick();var captured=GamePanelLanguageTest.frame(h);
            String name=language.tag()+"-chapter"+(level+1)+"-"+scene+"-"+width;
            transcript.add("=== "+name+" ===");transcript.addAll(captured.lines());
            BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(()->{var g=image.createGraphics();try{h.panel.paint(g);}finally{g.dispose();}});
            ImageIO.write(image,"png",out.resolve(name+".png").toFile());image.flush();captured.image().flush();
            var route=(ChapterRouteController)h.get("chapterRoute");
            evidence.add(name+","+h.state()+","+h.player().getHp()+","+h.player().isDebugMode()+","+route.snapshot().tick()+","+route.platforms().size()+","+(h.boss()==null?"NONE":h.boss().getEncounterAction()));
        }
    }
    private static void quiet(HeapGameHarness h,int n)throws Exception {
        for(int i=0;i<n;i++){h.enemies().clearHostiles();h.enemies().getEnemyBullets().clear();h.tick();}
    }
}
