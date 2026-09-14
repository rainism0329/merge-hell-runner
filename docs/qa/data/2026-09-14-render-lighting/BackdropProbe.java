package com.bigphil.mergehell.render;
import com.bigphil.mergehell.assets.*;
import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
public final class BackdropProbe {
    public static void main(String[] args) throws Exception {
        var store = AssetStore.preload(new AssetCatalog(Map.of("city", "game/art/city.properties")), BackdropProbe.class.getClassLoader());
        var original = new IndustrialArtReference(store);
        var changed = new IndustrialArt(store);
        Path output = Path.of(args[0]); Files.createDirectories(output);
        int cases=0; long differing=0, maxDifference=0;
        for(int light : new int[]{0, 1, 2, 3, 4, 0, -1, 9}) for(int width : new int[]{600,960,1280})
        for(double camera : new double[]{0, 0.2, 80, 7999, 8571.4, 8571.5, 17200, -13.2}) {
            BufferedImage a = frame(original,null,width,600,camera,light,0);
            BufferedImage b = frame(null,changed,width,600,camera,light,0);
            for(int y=0;y<a.getHeight();y++) for(int x=0;x<width;x++) if(a.getRGB(x,y)!=b.getRGB(x,y)){
                differing++; for(int s:new int[]{0,8,16,24}) maxDifference=Math.max(maxDifference,Math.abs((a.getRGB(x,y)>>s&255)-(b.getRGB(x,y)>>s&255)));
            }
            cases++;
        }
        System.out.println("main cases="+cases+" differing_pixels="+differing+" max_channel_difference="+maxDifference);
        for(int option=1;option<=4;option++) for(int height : new int[]{400,600}) {
            var a=frame(original,null,960,height,512.4,3,option);var b=frame(null,changed,960,height,512.4,3,option);
            for(int y=0;y<height;y++)for(int x=0;x<960;x++)if(a.getRGB(x,y)!=b.getRGB(x,y)) throw new AssertionError("fallback mismatch "+option+" height "+height+" x="+x+" y="+y+" a="+Integer.toHexString(a.getRGB(x,y))+" b="+Integer.toHexString(b.getRGB(x,y)));
            cases++;
        }
        System.out.println("cases="+cases+" differing_pixels="+differing+" max_channel_difference="+maxDifference);
        ImageIO.write(frame(original,null,960,600,8571.4,3,0),"png",output.resolve("before.png").toFile());
        ImageIO.write(frame(null,changed,960,600,8571.4,3,0),"png",output.resolve("after.png").toFile());
        bench(original,null,"before");bench(null,changed,"after");
    }
    static BufferedImage frame(IndustrialArtReference a,IndustrialArt b,int w,int h,double camera,int level,int option){
        BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB_PRE);Graphics2D g=img.createGraphics();
        g.setColor(new Color(22,33,55,113));g.fillRect(0,0,w,h);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        if(option==1)g.setComposite(AlphaComposite.SrcOver.derive(.5f));
        if(option==2)g.scale(.8,.8);
        if(option==3)g.translate(.4,.8);
        if(option==4)g.setClip(50,50,w-100,h-100);
        if(a!=null)a.backdrop(g,w,h,camera,level);else b.backdrop(g,w,h,camera,level);g.dispose();return img;
    }
    static void bench(IndustrialArtReference a,IndustrialArt b,String label){
        BufferedImage img=new BufferedImage(960,600,BufferedImage.TYPE_INT_ARGB_PRE);Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);double[] samples=new double[400];
        for(int i=0;i<550;i++){long start=System.nanoTime();if(a!=null)a.backdrop(g,960,600,8000+i*4.5,0);else b.backdrop(g,960,600,8000+i*4.5,0);if(i>=150)samples[i-150]=(System.nanoTime()-start)/1e6;}
        g.dispose();Arrays.sort(samples);System.out.printf(Locale.ROOT,"%s p50=%.6f p95=%.6f p99=%.6f ms%n",label,samples[199],samples[379],samples[395]);
    }
}
