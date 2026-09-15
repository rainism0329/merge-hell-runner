package com.bigphil.mergehell.render;

import com.bigphil.mergehell.progression.CharacterId;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Production poses at 3x and native size; no replacement illustration or altered sprite art. */
public final class CharacterPolishPreview {
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args.length==0?"build/polish/characters":args[0]);Files.createDirectories(output);
        IndustrialArt art=IndustrialArt.load();
        String[] labels={"FORWARD","DIAGONAL UP","UP","DIAGONAL DOWN","DOWN",
                "LEFT","CROUCH / LEFT","RUN / CONTACT","RUN / RECOVERY","MELEE"};
        for(CharacterId role:CharacterId.values()) {
            BufferedImage image=new BufferedImage(1400,600,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=image.createGraphics();g.setColor(new Color(18,25,32));g.fillRect(0,0,1400,600);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            for(int i=0;i<10;i++) {
                int x=i%5*280,y=i/5*300;
                double angle=switch(i){case 1->-Math.PI/4;case 2->-Math.PI/2;case 3->Math.PI/4;case 4->Math.PI/2;default->0;};
                int facing=i==5||i==6?-1:1;
                var action=i>=7&&i<=8?ActorVisuals.Action.RUN:i==9?ActorVisuals.Action.MELEE:
                        i==3||i==4?ActorVisuals.Action.FALL:ActorVisuals.Action.IDLE;
                double phase=i==8?Math.PI/2:i==9?.4:0;
                var pose=new ActorVisuals.Hero(0,0,facing,action,phase,0,0,false,false,1,
                        Math.cos(angle)*facing,Math.sin(angle),i==6,role);
                g.setColor(new Color(55,72,82));g.drawRect(x,y,279,299);
                g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));g.setColor(Color.WHITE);
                g.drawString(role.name()+" / "+labels[i],x+12,y+24);
                g.setColor(new Color(79,104,111));g.drawLine(x+20,y+242,x+260,y+242);
                Graphics2D large=(Graphics2D)g.create();large.translate(x+130,y+242);large.scale(3,3);
                ActorVisuals.hero(large,art,pose);large.dispose();
                Graphics2D nativeSize=(Graphics2D)g.create();nativeSize.translate(x+238,y+240);
                ActorVisuals.hero(nativeSize,art,pose);nativeSize.dispose();
            }
            g.dispose();ImageIO.write(image,"png",output.resolve(role.name().toLowerCase()+".png").toFile());
        }
    }
}
