package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.GameLanguage;
import com.bigphil.mergehell.persistence.*;
import com.bigphil.mergehell.world.ExplorationRoute;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Current production-panel captures; chapter/position fixtures are explicit, never playthrough evidence. */
public final class MaturityPreview {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length==0?"build/maturity-20260921/visual":args[0]); Files.createDirectories(out);
        var storage=MergeHellStateService.getInstance(); var saved=storage.getState();
        List<String> text=new ArrayList<>();
        try {
            for(GameLanguage language:GameLanguage.values()) {
                var settings=new MergeHellState();settings.settings.language=language.tag();settings.settings.muted=true;
                storage.loadState(settings);
                try(var h=new HeapGameHarness()) {
                    h.key("PAUSE_P");h.tick();capture(h,out,language,"pause",text);
                    h.key("MENU");h.tick();capture(h,out,language,"continue-menu",text);
                    h.key("NEW_RANKED_RUN");h.tick();capture(h,out,language,"new-confirmation",text);
                    h.key("PAUSE_ESC");h.tick();h.key("START");h.tick();
                    h.key("PAUSE_P");h.tick();h.key("SETTINGS");h.tick();capture(h,out,language,"settings",text);
                    h.key("PAUSE_ESC");h.tick();h.key("PAUSE_P");h.tick();
                    // A real exhausted-life transition provides the failed-run actions.
                    h.player().setDebugMode(false);
                    for(int i=0;i<3;i++) {
                        h.player().setInvincibleTimer(0);h.player().setShieldTimer(0);
                        h.player().takeDamage(Integer.MAX_VALUE);h.invoke("resolvePlayerDefeat");
                    }
                    if(h.state()!=GameState.GAME_OVER)throw new IllegalStateException("Death fixture did not end run");
                    h.tick();capture(h,out,language,"failure",text);
                }
                try(var h=new HeapGameHarness()) {
                    h.set("level",2);h.invoke("advanceLevel");h.tick();
                    h.key("LAB_TOGGLE_ALT");h.tick();h.place(1300,450);h.ticks(165);
                    capture(h,out,language,"mixed-encounter",text);
                }
                try(var h=new HeapGameHarness()) {
                    h.set("level",2);h.invoke("advanceLevel");h.tick();
                    h.key("LAB_TOGGLE_ALT");h.tick();
                    var route=(ExplorationRoute)h.get("exploration");
                    h.place(9140,450);capture(h,out,language,"winch-closed",text);
                    h.place(8860,230);h.key("HEAP_PURGE");h.tick();
                    h.place(9140,450);h.quietTicks(70);capture(h,out,language,"winch-moving",text);
                    h.quietTicks(120);h.place(9480,450);capture(h,out,language,"bridge-choice",text);
                    h.place(9570,280);h.key("HEAP_PURGE");h.tick();h.quietTicks(165);
                    if(!route.visited().containsAll(Set.of(1,2)))throw new IllegalStateException("Console fixture failed");
                    capture(h,out,language,"bridge-open",text);
                }
            }
        } finally { storage.loadState(saved); }
        Files.write(out.resolve("displayed-text.txt"),text);
        System.out.println("Production panel captures: "+out.toAbsolutePath());
    }
    private static void capture(HeapGameHarness h,Path out,GameLanguage language,String name,List<String> text) throws Exception {
        for(int width:new int[]{960,600}) {
            int height=width==960?600:400;
            SwingUtilities.invokeAndWait(()->h.panel.setSize(width,height));SwingUtilities.invokeAndWait(()->{});h.tick();
            var frame=GamePanelLanguageTest.frame(h);text.add("=== "+language.tag()+"-"+name+"-"+width+" ===");text.addAll(frame.lines());
            BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(()->{
                Graphics2D g=image.createGraphics();try{h.panel.paint(g);}finally{g.dispose();}
            });
            ImageIO.write(image,"png",out.resolve(language.tag()+"-"+name+"-"+width+".png").toFile());
            image.flush();frame.image().flush();
        }
    }
}
