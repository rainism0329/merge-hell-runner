package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.GameLanguage;
import com.bigphil.mergehell.persistence.*;
import com.bigphil.mergehell.combat.WeaponId;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Production panel captures with explicit chapter/position/LAB fixtures, not playthrough evidence. */
public final class CampaignDepthPreview {
    public static void main(String[] args)throws Exception {
        var out=Path.of(args.length>0?args[0]:"build/campaign-depth-20260921/visual");Files.createDirectories(out);
        var storage=MergeHellStateService.getInstance();var saved=storage.getState();var text=new ArrayList<String>();
        try {
            for(var language:GameLanguage.values()) {
                var state=new MergeHellState();state.settings.language=language.tag();state.settings.muted=true;storage.loadState(state);
                try(var h=new HeapGameHarness()) {
                    h.key("PAUSE_P");h.tick();h.key("MENU");h.tick();
                    for(var weapon:WeaponId.values()) {
                        capture(h,out,language,"weapon-"+weapon.name().toLowerCase(Locale.ROOT),text);
                        h.key("WEAPON");h.tick();
                    }
                }
                for(int level:new int[]{1,3,4})try(var h=world(level)) {
                    h.place(level==1?2360:level==3?1760:1900,450);h.ticks(165);
                    capture(h,out,language,"mixed-world-"+(level+1),text);
                }
                try(var h=world(3)) {
                    h.place(8660,450);capture(h,out,language,"freight-reverse",text);
                    interact(h,8300,260);h.place(8660,450);capture(h,out,language,"freight-forward",text);
                    h.place(9470,450);capture(h,out,language,"foundry-hot",text);
                    interact(h,9340,240);h.place(9470,450);capture(h,out,language,"foundry-cooled",text);
                }
                try(var h=world(4)) {
                    h.place(8800,450);h.quietTicks(20);capture(h,out,language,"hatchery-active",text);
                    interact(h,8340,230);h.place(8800,450);capture(h,out,language,"hatchery-silenced",text);
                    h.place(9560,450);capture(h,out,language,"membrane-closed",text);
                    interact(h,9460,255);h.place(9560,450);h.quietTicks(38);capture(h,out,language,"membrane-moving",text);
                    h.quietTicks(90);capture(h,out,language,"membrane-open",text);
                }
            }
        }finally {storage.loadState(saved);}
        Files.write(out.resolve("displayed-text.txt"),text);
        System.out.println("Production campaign captures: "+out.toAbsolutePath());
    }
    private static HeapGameHarness world(int level)throws Exception {
        var h=new HeapGameHarness();
        if(level!=1){h.set("level",level);h.invoke("advanceLevel");h.tick();}
        h.key("LAB_TOGGLE_ALT");h.tick();return h;
    }
    private static void interact(HeapGameHarness h,double x,double y)throws Exception {
        h.place(x,y);h.key("HEAP_PURGE");h.tick();h.quietTicks(2);
    }
    private static void capture(HeapGameHarness h,Path out,GameLanguage language,String name,List<String> text)throws Exception {
        for(int width:new int[]{960,600}) {
            int height=width==960?600:400;
            SwingUtilities.invokeAndWait(()->h.panel.setSize(width,height));SwingUtilities.invokeAndWait(()->{});h.tick();
            var frame=GamePanelLanguageTest.frame(h);String id=language.tag()+"-"+name+"-"+width;
            text.add("=== "+id+" ===");text.addAll(frame.lines());
            var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(()->{var g=image.createGraphics();try{h.panel.paint(g);}finally{g.dispose();}});
            ImageIO.write(image,"png",out.resolve(id+".png").toFile());image.flush();frame.image().flush();
        }
    }
}
