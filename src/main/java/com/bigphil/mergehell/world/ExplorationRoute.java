package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.*;
import java.awt.Rectangle;
import java.util.*;

/** Small authored rooms layered onto each chapter. These bounds are also the collision geometry. */
public final class ExplorationRoute {
    public enum Material { BRICK, DRAIN, GIRDER, FURNACE, TISSUE }
    public record Block(Rectangle bounds, Material material, boolean canopy) {
        public Block { bounds=new Rectangle(bounds); }
        @Override public Rectangle bounds() {return new Rectangle(bounds);}
    }
    public record Landmark(int id, Rectangle bounds, String key, boolean secret) {
        public Landmark {bounds=new Rectangle(bounds);}
        @Override public Rectangle bounds() {return new Rectangle(bounds);}
    }
    public record Event(String key, int reward, boolean secret, boolean encounter) { }
    public record Snapshot(int chapter, List<Block> blocks, List<Platform> platforms,
                           List<Landmark> landmarks, Set<Integer> visited, String hint, boolean arena,
                           int gateLift) {
        public Snapshot(int chapter, List<Block> blocks, List<Platform> platforms,
                        List<Landmark> landmarks, Set<Integer> visited, String hint, boolean arena) {
            this(chapter,blocks,platforms,landmarks,visited,hint,arena,0);
        }
    }
    private static final int GATE_X=9250, GATE_TRAVEL=180;
    private final int chapter;
    private final Material material;
    private final List<Block> blocks=new ArrayList<>();
    private final List<Platform> platforms=new ArrayList<>();
    private final List<Landmark> landmarks=new ArrayList<>();
    private final Set<Integer> visited=new HashSet<>();
    private final List<Event> events=new ArrayList<>();
    private boolean arena;
    private int gateLift;
    private String hint="";

    public ExplorationRoute(int chapter) {
        if(chapter<0 || chapter>4) throw new IllegalArgumentException("Unknown chapter");
        this.chapter=chapter;material=Material.values()[chapter];
        switch(chapter) {
            case 0 -> {
                block(940,72,110);ledge(1240,125,155);ledge(1480,200,190);
                // Roof hides a 38-pixel maintenance passage; the upper route remains available.
                canopy(1660,170,130,38);ledge(1660,190,170);
                landmark(0,1692,450,"office",true);
                block(2550,110,130);ledge(2440,55,100);
                block(4200,160,120);ledge(4050,85,110);ledge(4380,145,150);
                landmark(1,4420,305,"rooftop",false);
                block(6120,95,150);ledge(6380,170,230);
                landmark(2,6450,280,"antenna",false);
            }
            case 1 -> {
                block(1610,90,95);ledge(1870,175,210);landmark(0,1940,275,"pump",true);
                block(3150,95,130);ledge(3060,50,90);
                block(8050,150,140);ledge(7890,75,120);ledge(8300,200,220);
                landmark(1,8360,250,"valve",false);
                canopy(8680,200,160,38);ledge(8650,180,140);ledge(9010,130,200);
                landmark(2,9060,320,"spillway",false);
            }
            case 2 -> {
                ledge(3060,130,125);ledge(3210,215,230);landmark(0,3270,235,"control",true);
                block(8070,185,115);ledge(7880,80,115);ledge(8240,155,160);
                block(8600,105,130);ledge(8800,220,220);landmark(1,8860,230,"winch",false);
                block(9250,160,170);ledge(9090,80,110);ledge(9510,170,200);
                landmark(2,9570,280,"skybridge",false);
            }
            case 3 -> {
                ledge(2710,85,120);ledge(2850,190,220);landmark(0,2895,260,"workshop",true);
                block(8000,120,155);ledge(7840,60,125);ledge(8230,190,210);
                landmark(1,8300,260,"freight",false);
                canopy(8570,220,145,38);ledge(8530,190,155);
                block(8980,165,155);ledge(8810,80,135);ledge(9270,210,240);
                landmark(2,9340,240,"exhaust",false);
            }
            case 4 -> {
                ledge(1160,115,125);ledge(1310,210,195);landmark(0,1360,240,"incubator",true);
                block(8040,145,140);ledge(7880,70,130);ledge(8270,220,230);
                landmark(1,8340,230,"uppernest",false);
                canopy(8640,175,190,38);ledge(8600,200,155);
                block(9080,175,135);ledge(8930,85,115);ledge(9390,195,230);
                landmark(2,9460,255,"nerve",false);
            }
        }
        platforms.sort(Comparator.comparingDouble(p->p.x));
    }
    private void block(int x,int rise,int width) {blocks.add(new Block(new Rectangle(x,480-rise,width,rise),material,false));}
    private void canopy(int x,int width,int height,int clearance) {blocks.add(new Block(new Rectangle(x,480-clearance-height,width,height),material,true));}
    private void ledge(int x,int rise,int width) {
        platforms.add(new Platform(x,480-rise,width,18,switch(chapter) {
            case 0 -> Platform.Style.ROOFTOP;case 1 -> Platform.Style.PIPE;case 2 -> Platform.Style.CATWALK;
            case 3 -> Platform.Style.FORTIFICATION;default -> Platform.Style.RUBBLE;
        }));
    }
    private void landmark(int id,int x,int y,String name,boolean secret) {
        landmarks.add(new Landmark(id,new Rectangle(x,y,34,30),"explore."+name,secret));
    }
    public void prepare(boolean bossArena) {arena=bossArena;}
    /** Opening only: riders are carried, and an airborne body above the panel pauses the motor. */
    public void beforeMove(Player player) {
        beforeMove(player,List.of());
    }
    public void beforeMove(Player player,List<Rectangle> otherBodies) {
        if(arena || chapter!=2 || !visited.contains(1) || gateLift>=GATE_TRAVEL)return;
        Rectangle old=gateBounds(gateLift), next=gateBounds(gateLift+1);
        double feet=player.getY()+player.getBounds().height;
        boolean overlaps=player.getX()+player.getBounds().width>old.x && player.getX()<old.getMaxX();
        boolean rider=player.isGrounded() && overlaps && Math.abs(feet-old.y)<2;
        Rectangle body=new Rectangle((int)Math.floor(player.getX()),(int)Math.floor(feet-player.movementHeight()),
                player.getBounds().width,player.movementHeight());
        if(!rider && next.intersects(body) && !old.intersects(body))return;
        // Enemy damage rectangles use integer coordinates; keep one pixel for subpixel movement.
        if(otherBodies.stream().anyMatch(b->{var occupied=new Rectangle(b);occupied.grow(1,1);
            return next.intersects(occupied)&&!old.intersects(occupied);}))return;
        gateLift++;
        if(rider)player.setY(player.getY()-1);
    }
    private static Rectangle gateBounds(int lift) {return new Rectangle(GATE_X,320-lift,170,160);}
    private List<Block> currentBlocks() {
        if(chapter!=2)return List.copyOf(blocks);
        return blocks.stream().map(b->b.bounds.x==GATE_X
                ?new Block(gateBounds(gateLift),b.material,b.canopy):b).toList();
    }
    public List<Rectangle> solids() {return arena?List.of():currentBlocks().stream().map(Block::bounds).toList();}
    public List<Platform> platforms() {return arena?List.of():List.copyOf(platforms);}
    public boolean complete() {return visited.contains(1) && (chapter>=2 || visited.contains(2));}
    public Set<Integer> visited() {return Set.copyOf(visited);}
    public void restore(Set<Integer> flags) {
        if(flags==null || flags.stream().anyMatch(v->v==null||v<0||v>2)) throw new IllegalArgumentException("Invalid exploration flags");
        visited.clear();visited.addAll(flags);events.clear();hint="";
        gateLift=chapter==2&&visited.contains(1)?GATE_TRAVEL:0;
    }
    public void update(Player player, boolean interact) {
        if(arena)return;
        hint="";
        for(Landmark point:landmarks) {
            double distance=Math.hypot(player.getBounds().getCenterX()-point.bounds.getCenterX(),
                    player.getBounds().getCenterY()-point.bounds.getCenterY());
            if(distance>78 || visited.contains(point.id))continue;
            hint=point.key;
            if(interact && distance<64) {
                visited.add(point.id);
                events.add(new Event(point.key,point.secret?350:180,point.secret,point.id==1));
                // Field maintenance is the engineer's small, finite character advantage.
                if(player.getRunBuild().character()==com.bigphil.mergehell.progression.CharacterId.ENGINEER)player.heal(8);
                break;
            }
        }
    }
    public boolean consumeShot(Projectile shot, double before) {
        if(arena || shot.isDead())return false;
        double contact=firstSolidContact(shot);
        if(Double.isFinite(contact) && contact<=before) {shot.setDead(true);return true;}
        return false;
    }
    public double firstSolidContact(Projectile shot) {
        if(arena)return Double.POSITIVE_INFINITY;
        double nearest=Double.POSITIVE_INFINITY;
        for(Block block:currentBlocks())nearest=Math.min(nearest,shot.hitFraction(block.bounds));
        return nearest;
    }
    public List<Event> drainEvents() {var result=List.copyOf(events);events.clear();return result;}
    public Snapshot snapshot() {return new Snapshot(chapter,currentBlocks(),platforms(),List.copyOf(landmarks),visited(),hint,arena,gateLift);}
}

