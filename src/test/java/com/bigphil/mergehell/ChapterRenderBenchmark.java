package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.persistence.*;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/** Headless Java2D production-frame timings, not native IDE FPS or input latency. */
public final class ChapterRenderBenchmark {
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args.length==0?"build/chapter-overhaul/render-performance.csv":args[0]);
        List<String> rows=new ArrayList<>(List.of("chapter,width,load,frames,p50_ms,p95_ms,p99_ms"));
        Method render=GamePanel.class.getDeclaredMethod("renderLogicalFrame",java.awt.Graphics2D.class);render.setAccessible(true);
        for(int level=2;level<=4;level++)for(int width:new int[]{960,600})for(int load:new int[]{0,6,24}) {
            MergeHellStateService.getInstance().loadState(new MergeHellState());
            try(var h=new HeapGameHarness()) {
                h.set("level",level);h.invoke("advanceLevel");h.tick();h.place(2700,450);h.enemies().clearHostiles();
                EntityType[] roster=level==2?new EntityType[]{EntityType.SENTINEL,EntityType.WARDEN,EntityType.RIGGER}:
                        level==3?new EntityType[]{EntityType.INTERRUPT,EntityType.DRILLER,EntityType.SLAG_SPITTER}:
                        new EntityType[]{EntityType.MIRROR,EntityType.SPORE_POD,EntityType.LURKER};
                double camera=(double)h.get("cameraX");
                for(int i=0;i<load;i++)h.enemies().spawnEnemy((int)camera+360+(i*67)%560,ObstacleManager.specialistSpawnY(roster[i%3],480,i%3*16),roster[i%3]);
                if(load>0)for(int i=0;i<load*4;i++) {
                    h.shots().add(new Projectile(camera+40+(i*29)%850,170+i%9*27,0,0,ProjectileType.COMMIT));
                    h.enemies().getEnemyBullets().add(new Projectile(camera+50+(i*43)%830,200+i%8*29,0,0,ProjectileType.ENEMY));
                }
                SwingUtilities.invokeAndWait(()->h.panel.setSize(width,width==960?600:400));h.tick();
                BufferedImage image=new BufferedImage(960,600,BufferedImage.TYPE_INT_RGB);
                long[] times=new long[160];
                for(int frame=-40;frame<times.length;frame++) {
                    var g=image.createGraphics();long start=System.nanoTime();
                    try{render.invoke(h.panel,g);}finally{g.dispose();}
                    if(frame>=0)times[frame]=System.nanoTime()-start;
                }
                Arrays.sort(times);String row=String.format(Locale.ROOT,"%d,%d,%d,%d,%.3f,%.3f,%.3f",level+1,width,load,times.length,
                        times[80]/1e6,times[152]/1e6,times[158]/1e6);rows.add(row);System.out.println(row);image.flush();
            }
        }
        Files.write(out,rows);
    }
}
