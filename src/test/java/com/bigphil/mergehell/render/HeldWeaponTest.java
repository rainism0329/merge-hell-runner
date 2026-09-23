package com.bigphil.mergehell.render;

import com.bigphil.mergehell.combat.*;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeldWeaponTest {
    @Test void equippedWeaponAndEvolutionAreValueSnapshotsIncludingTemporaryAmmoFallback() {
        Player player=new Player(100,450);
        var build=evolved(WeaponId.COMMIT_CANNON);
        player.setRunBuild(build);
        var rig=new ActorVisuals();rig.update(player,List.of(),.016);
        var original=rig.snapshot().hero();assertTrue(original.evolved());
        player.giveWeapon(WeaponType.FLAME,1);
        rig.update(player,List.of(),.016);
        assertEquals(WeaponId.FIREWALL,rig.snapshot().hero().weapon());
        assertFalse(rig.snapshot().hero().evolved(),"A pickup must not borrow the core weapon's evolution");
        assertEquals(WeaponId.COMMIT_CANNON,original.weapon(),"Published pose cannot change with inventory");
        var shots=new ArrayList<Projectile>();
        player.update(false,false,false,true,480,2000,shots,List.of());
        rig.update(player,List.of(),.016);
        assertFalse(shots.isEmpty());assertEquals(WeaponType.COMMIT,player.getWeapon());
        assertEquals(WeaponId.COMMIT_CANNON,rig.snapshot().hero().weapon());
        assertTrue(rig.snapshot().hero().evolved());
    }

    @Test void everyBarrelModuleUsesTheSimulationMuzzleThroughAimCrouchAndRecoil() {
        for(var role:CharacterId.values())for(var weapon:WeaponId.values())
        for(boolean crouched:new boolean[]{false,true})for(int facing:new int[]{-1,1})
        for(double angle:new double[]{-Math.PI/2,-Math.PI/4,0,Math.PI/4,Math.PI/2})
        for(double recoil:new double[]{0,.5,1}) {
            var pose=new ActorVisuals.Hero(120,200,facing,ActorVisuals.Action.IDLE,0,recoil,0,false,false,1,
                    Math.cos(angle)*facing,Math.sin(angle),crouched,role,weapon,true);
            var expected=HeroAim.local(Math.cos(angle),Math.sin(angle),crouched,role);
            var world=AffineTransform.getTranslateInstance(120,200);world.scale(facing,1);
            world.concatenate(HeldWeaponRenderer.muzzleTransform(pose));
            var actual=world.transform(new Point2D.Double(0,0),null);
            assertEquals(120+facing*expected.x(),actual.getX(),1e-8);
            assertEquals(200+expected.y(),actual.getY(),1e-8);
        }
    }

    @Test void sudoShowsItsActualSpreadWeaponThenReturnsToTheEvolvedCore() {
        var player=new Player(100,450);player.setRunBuild(evolved(WeaponId.REFACTOR_BEAM));
        var rig=new ActorVisuals();rig.update(player,List.of(),.016);
        assertEquals(WeaponId.REFACTOR_BEAM,rig.snapshot().hero().weapon());
        player.setSudoTimer(90);
        player.update(false,false,false,true,480,2000,new ArrayList<>(),List.of());
        rig.update(player,List.of(),.016);
        assertEquals(WeaponId.FORCE_PUSH,player.getLastFiredWeaponId());
        assertEquals(player.getLastFiredWeaponId(),rig.snapshot().hero().weapon());
        assertFalse(rig.snapshot().hero().evolved());
        player.setSudoTimer(0);rig.update(player,List.of(),.016);
        assertEquals(WeaponId.REFACTOR_BEAM,rig.snapshot().hero().weapon());assertTrue(rig.snapshot().hero().evolved());
    }

    @Test void weaponRenderingPreservesCallerStateAndHasNoSimulationSideEffects() {
        var art=IndustrialArt.load();var rig=new ActorVisuals();var player=new Player(100,450);
        var build=evolved(WeaponId.REFACTOR_BEAM);player.setRunBuild(build);rig.update(player,List.of(),.016);
        var pose=rig.snapshot().hero();var checkpoint=build.checkpoint();
        Graphics2D g=new BufferedImage(240,200,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            g.setColor(Color.PINK);g.setStroke(new BasicStroke(3));g.translate(3,5);
            var tx=g.getTransform();var stroke=g.getStroke();
            for(int i=0;i<10;i++)HeldWeaponRenderer.render(g,art,pose.character().art(),pose,ActorVisuals.cannonTransform(art,pose));
            assertEquals(Color.PINK,g.getColor());assertEquals(tx,g.getTransform());assertEquals(stroke,g.getStroke());
            assertEquals(checkpoint,build.checkpoint());assertSame(pose,rig.snapshot().hero());
        }finally {g.dispose();}
    }

    static RunBuild evolved(WeaponId weapon) {
        var build=new RunBuild(weapon);
        for(var id:UpgradeId.values()) {
            var def=UpgradeCatalog.definition(id);
            if(def.isWeaponRelevant(weapon) && (def.tag()==UpgradeTag.WEAPON || def.tag()==UpgradeTag.EVOLUTION_CORE))
                for(int rank=0;rank<def.maxRank();rank++)build.apply(def);
        }
        assertTrue(build.tryEvolve());return build;
    }
}
