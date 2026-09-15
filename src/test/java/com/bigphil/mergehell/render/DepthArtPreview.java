package com.bigphil.mergehell.render;
import com.bigphil.mergehell.progression.CharacterId;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
public final class DepthArtPreview {
    public static void main(String[] args)throws Exception {
        var art=IndustrialArt.load();
        BufferedImage image=new BufferedImage(1440,1120,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(18,25,32));g.fillRect(0,0,1440,1120);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(var character:CharacterId.values())for(int pose=0;pose<4;pose++) {
            int x=180+pose*360,y=character.ordinal()*280+240;
            g.setColor(new Color(79,104,111));g.drawLine(x-145,y,x+145,y);
            g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));g.drawString(character.name()+" / "+pose,x-150,y-232);
            Graphics2D actor=(Graphics2D)g.create();actor.translate(x,y);actor.scale(4,4);
            ActorVisuals.hero(actor,art,new ActorVisuals.Hero(0,0,1,pose==2?ActorVisuals.Action.FALL:ActorVisuals.Action.IDLE,
                    0,0,0,false,false,1,pose==0||pose==3?1:0,pose==1?-1:pose==2?1:0,pose==3,character));actor.dispose();
        }
        g.dispose();ImageIO.write(image,"png",new File(args.length==0?"build/depth/characters-preview.png":args[0]));
    }
}
