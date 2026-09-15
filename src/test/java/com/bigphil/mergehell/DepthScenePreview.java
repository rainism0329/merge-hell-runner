package com.bigphil.mergehell;
import com.bigphil.mergehell.persistence.*;
import com.bigphil.mergehell.progression.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import javax.swing.SwingUtilities;
public final class DepthScenePreview {
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args.length==0?"build/depth/scenes":args[0]);Files.createDirectories(out);
        var storage=MergeHellStateService.getInstance();var previous=storage.getState();
        try {
            storage.loadState(new MergeHellState());
            try(var h=new HeapGameHarness()) {
                for(String language:new String[]{"en","zh-CN"}) {
                    var settings=(MergeHellState.Settings)h.get("settings");settings.language=language;h.invoke("applySettings");
                    h.set("state",GameState.MENU);
                    for(var role:CharacterId.values()) {
                        h.set("selectedCharacter",role);h.tick();capture(h,out.resolve(language+"-menu-"+role.name()+".png"));
                    }
                    h.key("HELP");h.tick();capture(h,out.resolve(language+"-controls.png"));h.key("HELP");h.tick();
                }
                for(int chapter=0;chapter<5;chapter++) {
                    h.set("level",chapter);h.invoke("advanceLevel");h.tick();
                    h.player().getRunBuild().setIdentity(CharacterId.values()[chapter%4],GameDifficulty.STANDARD);
                    h.place(chapter==0?1580:7820,450);h.enemies().clearHostiles();h.tick();
                    capture(h,out.resolve("world-"+(chapter+1)+".png"));
                    if(chapter==2) {
                        h.set("state",GameState.MENU);
                        for(String language:new String[]{"en","zh-CN"}) {
                            var settings=(MergeHellState.Settings)h.get("settings");settings.language=language;h.invoke("applySettings");
                            capture(h,out.resolve(language+"-safe-continue.png"));
                        }
                        h.set("state",GameState.RUNNING);
                    }
                }
            }
        }finally{storage.loadState(previous);}
    }
    private static void capture(HeapGameHarness h,Path file)throws Exception {
        for(int width:new int[]{960,600}) {
            int height=width==960?600:400;
            SwingUtilities.invokeAndWait(()->h.panel.setSize(width,height));h.tick();
            BufferedImage frame=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
            SwingUtilities.invokeAndWait(()->{var g=frame.createGraphics();h.panel.paint(g);g.dispose();});
            Path output=width==960?file:file.resolveSibling(file.getFileName().toString().replace(".png","-600.png"));
            ImageIO.write(frame,"png",output.toFile());
        }
        SwingUtilities.invokeAndWait(()->h.panel.setSize(960,600));h.tick();
    }
}
