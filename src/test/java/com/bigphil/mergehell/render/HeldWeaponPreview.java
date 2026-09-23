package com.bigphil.mergehell.render;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.progression.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Real inventory snapshots at 1x and 2.2x, plus a barrel detail. No gameplay or balance claim. */
public final class HeldWeaponPreview {
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args.length>0?args[0]:"build/held-weapons-20260921/visual");Files.createDirectories(output);
        var art=IndustrialArt.load();
        for(boolean evolved:new boolean[]{false,true}) {
            var sheet=new BufferedImage(1200,960,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
            g.setColor(new Color(20,28,35));g.fillRect(0,0,1200,960);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            for(var weapon:WeaponId.values())for(var role:CharacterId.values()) {
                var build=evolved?HeldWeaponTest.evolved(weapon):new RunBuild(weapon);
                build.setIdentity(role,GameDifficulty.STANDARD);var player=new Player(0,450);player.setRunBuild(build);
                var rig=new ActorVisuals();rig.update(player,List.of(),0);var pose=rig.snapshot().hero();
                Graphics2D cell=(Graphics2D)g.create(role.ordinal()*300,weapon.ordinal()*160,300,160);
                cell.setColor(new Color(60,75,84));cell.drawRect(0,0,299,159);
                cell.setColor(new Color(218,222,212));cell.setFont(new Font(Font.MONOSPACED,Font.PLAIN,11));
                cell.drawString(role+" / "+weapon,7,16);
                var body=(Graphics2D)cell.create();body.translate(68,140);body.scale(2.2,2.2);body.translate(-pose.x(),-pose.footY());
                ActorVisuals.hero(body,art,pose);body.dispose();
                body=(Graphics2D)cell.create();body.translate(150,140);body.translate(-pose.x(),-pose.footY());
                ActorVisuals.hero(body,art,pose);body.dispose();
                body=(Graphics2D)cell.create();body.translate(273,71);body.scale(4,4);body.translate(-18,30);
                HeldWeaponRenderer.render(body,art,role.art(),pose,ActorVisuals.cannonTransform(art,pose));body.dispose();
                cell.setColor(new Color(130,151,165));cell.drawString(evolved?"EVOLVED":"BASE",192,119);cell.dispose();
            }
            g.dispose();ImageIO.write(sheet,"png",output.resolve(evolved?"evolved.png":"base.png").toFile());
        }
        System.out.println("Weapon inventory preview: "+output.toAbsolutePath());
    }
}
