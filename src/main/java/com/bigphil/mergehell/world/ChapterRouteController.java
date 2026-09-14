package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.Platform;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.model.Projectile;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** Authored physical routes. Geometry, warnings and rendering share these snapshots. */
public final class ChapterRouteController {
    public enum Kind { COUNTERWEIGHT, COOLANT, MEMBRANE, NEST }
    public enum SurfaceKind { GAP, CONVEYOR, MOLTEN, ACID, VENT }
    public record Surface(double x, int width, SurfaceKind kind, int phase, boolean active) { }
    public record Prop(int id, Kind kind, Rectangle bounds, int hp, int maxHp, int effectTicks, int warningTicks) {
        public Prop { bounds = new Rectangle(bounds); }
        @Override public Rectangle bounds() { return new Rectangle(bounds); }
    }
    public record Lift(int id, Platform platform, double anchorY) { }
    public record MovementBounds(double minX, double maxX) { }
    public record Event(String key, double x, double y, int damage, boolean spawn) { }
    public record Snapshot(int level, long tick, List<Surface> surfaces, List<Prop> props,
                           List<Lift> lifts, List<Platform> platforms, String sectionKey, boolean arena) {
        public static Snapshot empty() { return new Snapshot(-1, 0, List.of(), List.of(), List.of(), List.of(), "", false); }
    }
    private static final class Structure {
        final int id; final Kind kind; final Rectangle bounds; final int maxHp;
        int hp, effectTicks, hatch = -1, hatchCount;
        Structure(int id, Kind kind, int x, int y, int w, int h, int hp) {
            this.id=id; this.kind=kind; this.bounds=new Rectangle(x,y,w,h); this.hp=this.maxHp=hp;
        }
    }
    private final int level, groundY;
    private final List<Structure> structures = new ArrayList<>();
    private final List<Platform> ledges = new ArrayList<>();
    private final List<Surface> terrain = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private List<Lift> lifts = List.of();
    private List<Lift> pendingSupportTransfer;
    private List<Platform> platforms = List.of();
    private long tick;
    private boolean arena;
    private int hazardCooldown, rescueCooldown, meleeCooldown;
    private final boolean[] storySeen=new boolean[4];
    private double safeX=180, playerX;

    public ChapterRouteController(int level, int groundY) {
        if (level < 2 || level > 4) throw new IllegalArgumentException("chapter route: " + level);
        this.level=level; this.groundY=groundY;
        if (level==2) buildCitadel(); else if (level==3) buildFoundry(); else buildHive();
        rebuildPlatforms();
    }
    private void ledge(int x,int rise,int w) {
        ledges.add(new Platform(x,groundY-rise,w,22,level==2?Platform.Style.CATWALK:level==3?Platform.Style.PIPE:Platform.Style.RUBBLE));
    }
    private void surface(int x,int w,SurfaceKind kind,int phase) { terrain.add(new Surface(x,w,kind,phase,false)); }
    private void prop(Kind kind,int x,int rise,int w,int h,int hp) {
        structures.add(new Structure(structures.size(),kind,x,groundY-rise-h,w,h,hp));
    }
    private void buildCitadel() {
        // Stair-like bridge abutments lead into open shafts; every gap is also double-jumpable.
        for (int x : new int[]{850,2790,4490,6600}) {
            ledge(x-180,24,90); ledge(x-90,48,100); surface(x+10,165,SurfaceKind.GAP,0);
            ledge(x+185,48,100); ledge(x+285,24,90);
            prop(Kind.COUNTERWEIGHT,x-40,60,35,50,60);
        }
        ledge(1430,90,220); ledge(1760,155,170); ledge(2150,90,160);
        ledge(3240,100,180); ledge(3660,165,190); ledge(4050,90,180);
        ledge(5230,95,240); ledge(5620,160,160); ledge(5980,95,230);
        ledge(7040,90,200); ledge(7350,155,180);
        ledge(7820,90,180); ledge(8110,160,150);
    }
    private void buildFoundry() {
        for (int x : new int[]{680,2980,5480}) {
            surface(x,410,SurfaceKind.CONVEYOR,(x==2980?-1:1));
            surface(x+440,190,SurfaceKind.MOLTEN,0);
            ledge(x+390,48,100); ledge(x+490,105,150); ledge(x+645,48,90);
            prop(Kind.COOLANT,x+295,0,48,86,45);
        }
        surface(2060,90,SurfaceKind.VENT,0); surface(3980,90,SurfaceKind.VENT,95);
        surface(6700,90,SurfaceKind.VENT,190);
        ledge(1650,60,220); ledge(2350,115,220); ledge(3690,60,150);
        ledge(4450,110,240); ledge(4750,55,170); ledge(6410,65,200);
        ledge(7050,110,220); ledge(7870,90,150); ledge(8160,160,130);
    }
    private void buildHive() {
        for (int x : new int[]{880,2840,4830,6500}) {
            surface(x,250,SurfaceKind.ACID,0);
            ledge(x-60,35,105); ledge(x+65,95,130); ledge(x+220,45,110);
        }
        for (int x : new int[]{1580,3610,5720,7150}) prop(Kind.MEMBRANE,x,0,96,150,160);
        for (int x : new int[]{550,2310,4150,6100}) prop(Kind.NEST,x,0,82,90,110);
        ledge(1830,85,200); ledge(2550,145,160); ledge(3860,100,220);
        ledge(4450,160,175); ledge(5320,85,180); ledge(6880,145,180);
        ledge(7810,90,170); ledge(8130,160,130);
    }
    /** Runs before Player.update, so moving support transports a standing actor exactly once. */
    public void beforeMove(Player player, boolean bossArena) {
        arena=bossArena; playerX=player.getX(); tick++;
        hazardCooldown=Math.max(0,hazardCooldown-1); rescueCooldown=Math.max(0,rescueCooldown-1);
        meleeCooldown=Math.max(0,meleeCooldown-1);
        List<Lift> previous=pendingSupportTransfer == null ? lifts : pendingSupportTransfer;
        pendingSupportTransfer=null;
        for (Structure s:structures) if(s.effectTicks>0)s.effectTicks--;
        rebuildPlatforms();
        if (!arena && player.isGrounded()) {
            double feet=player.getY()+player.getBounds().height;
            for(Lift previousLift:previous) {
                Lift next=lifts.stream().filter(l->l.id()==previousLift.id()).findFirst().orElse(null);
                if(next==null)continue;
                Platform old=previousLift.platform(), now=next.platform();
                if(Math.abs(feet-old.y)<2 && player.getX()+player.getBounds().width>old.x && player.getX()<old.x+old.width) {
                    player.setX(player.getX()+now.x+now.width*.5-old.x-old.width*.5);
                    player.setY(player.getY()+now.y-old.y); break;
                }
            }
            if(Math.abs(feet-groundY)<2) for(Surface s:terrain) {
                if(s.kind==SurfaceKind.CONVEYOR && overlaps(player.getX(),player.getBounds().width,s.x,s.width))
                    player.setX(player.getX()+s.phase*1.05);
            }
        }
    }
    private void rebuildPlatforms() {
        List<Platform> result=new ArrayList<>(ledges);
        List<Lift> moving=new ArrayList<>();
        if(!arena && level==2) {
            int i=0;
            for(Surface gap:terrain) if(gap.kind==SurfaceKind.GAP) {
                Structure counter=structures.get(i);
                if(counter.hp==0) {
                    Platform bridge=new Platform(gap.x,groundY-48,gap.width,22,Platform.Style.CATWALK);
                    result.add(bridge); moving.add(new Lift(i,bridge,groundY-260));
                }
                else {
                    double y=groundY-70-62*Math.sin((tick+i*90)*Math.PI/210);
                    Platform p=new Platform(gap.x+22,y,gap.width-44,20,Platform.Style.CATWALK);
                    moving.add(new Lift(i,p,groundY-260)); result.add(p);
                }
                i++;
            }
        }
        lifts=List.copyOf(moving); platforms=List.copyOf(result);
    }
    public int groundFor(double x,int width) {
        if(!arena && level==2) for(Surface s:terrain) if(s.kind==SurfaceKind.GAP && x+width*.65>s.x && x+width*.35<s.x+s.width)
            return groundY+260;
        return groundY;
    }
    public MovementBounds movementBounds(double x,int width) {
        double min=0,max=8600;
        if(!arena && level==2) for(Surface gap:terrain) if(gap.kind==SurfaceKind.GAP) {
            if(gap.x+gap.width<=x)min=Math.max(min,gap.x+gap.width);
            else if(gap.x>=x+width)max=Math.min(max,gap.x);
            else if(x+width*.5<gap.x+gap.width*.5)max=Math.min(max,gap.x);
            else min=Math.max(min,gap.x+gap.width);
        }
        return new MovementBounds(min,max);
    }
    /** Resolve solid membranes and bounded hazards after physics. Respawn never restores rewards. */
    public void afterMove(Player player,double previousX,int aliveHostiles) {
        playerX=player.getX();
        int section=sectionIndex();
        if(!storySeen[section]) {
            storySeen[section]=true;
            events.add(new Event("chapter."+(level+1)+".story."+(section+1),playerX,player.getY()-35,0,false));
        }
        if(arena)return;
        for(Structure s:structures) if(s.kind==Kind.MEMBRANE && s.hp>0 && player.getBounds().intersects(s.bounds)) {
            player.setX(previousX+player.getBounds().width<=s.bounds.x+8 ? s.bounds.x-player.getBounds().width : s.bounds.getMaxX());
        }
        if(player.getY()+player.getBounds().height>groundY+130 && rescueCooldown==0) {
            player.setX(safeX); player.setY(groundY-player.getBounds().height); player.takeDamage(15); player.setInvincibleTimer(90);
            rescueCooldown=90; events.add(new Event("chapter.bridge.rescued",safeX,groundY-30,15,false));
        }
        boolean hazardous=false;
        for(Surface s:surfaces()) {
            if(!s.active || !overlaps(player.getX(),player.getBounds().width,s.x,s.width))continue;
            double threshold=s.kind==SurfaceKind.VENT?groundY-130:groundY-8;
            if(player.getY()+player.getBounds().height>threshold) hazardous=true;
        }
        if(hazardous && hazardCooldown==0) {
            int before=player.getHp(); player.takeDamage(level==4?8:12);
            if(player.getHp()<before)events.add(new Event("chapter.terrain.hit",player.getX(),player.getY(),before-player.getHp(),false));
            hazardCooldown=45;
        }
        if(!hazardous && player.isGrounded() && Math.abs(player.getY()+player.getBounds().height-groundY)<2
                && groundFor(player.getX()-55,player.getBounds().width+110)==groundY) safeX=player.getX();
        for(Structure s:structures) if(s.kind==Kind.NEST && s.hp>0 && s.hatchCount<3) {
            boolean visible=Math.abs(s.bounds.getCenterX()-player.getX())<540;
            if(!visible){s.hatch=-1;continue;}
            if(s.hatch<0)s.hatch=120;
            if(s.hatch>0)s.hatch--;
            if(s.hatch==0 && aliveHostiles<6) {
                events.add(new Event("chapter.nest.hatch",s.bounds.getCenterX(),groundY-38,0,true));
                s.hatchCount++; s.hatch=330;
            }
        }
        playerX=player.getX();
    }
    public void resetTransient() { hazardCooldown=90; rescueCooldown=90; meleeCooldown=0; structures.forEach(s->s.hatch=-1); }
    public void interact(Player player) {
        if(arena)return;
        for(Structure s:structures) if(s.kind==Kind.COOLANT && s.hp>0 && Math.abs(s.bounds.getCenterX()-player.getX())<95) {
            activate(s); break;
        }
    }
    public boolean consumeShot(Projectile p,double beforeFraction) {
        if(arena || p.isDead())return false;
        Structure nearest=null; double closest=beforeFraction;
        for(Structure s:structures) if(s.hp>0 && p.hits(s.bounds)) {
            double fraction=p.hitFraction(s.bounds);
            if(fraction<=closest){nearest=s;closest=fraction;}
        }
        if(nearest==null)return false;
        hit(nearest,p.getDamage()); p.setDead(true); return true;
    }
    public void melee(Rectangle bounds) {
        if(arena || meleeCooldown>0)return;
        for(Structure s:structures) if(s.hp>0 && bounds.intersects(s.bounds)) {hit(s,50);meleeCooldown=18;}
    }
    private void hit(Structure s,int damage) {
        int previous=s.hp; s.hp=Math.max(0,s.hp-Math.max(1,damage));
        if(s.kind==Kind.COOLANT && s.effectTicks==0)activate(s);
        if(previous>0 && s.hp==0) {
            if(s.kind==Kind.COUNTERWEIGHT && pendingSupportTransfer==null) pendingSupportTransfer=lifts;
            if(s.kind==Kind.COOLANT)s.effectTicks=600;
            events.add(new Event("chapter.prop."+s.kind.name().toLowerCase(java.util.Locale.ROOT)+".broken",s.bounds.getCenterX(),s.bounds.y,0,false));
            rebuildPlatforms();
        }
    }
    private void activate(Structure s) {
        if(s.effectTicks>0)return;
        s.effectTicks=420;
        events.add(new Event("chapter.coolant.active",s.bounds.getCenterX(),s.bounds.y,0,false));
    }
    private List<Surface> surfaces() {
        List<Surface> result=new ArrayList<>();
        for(Surface s:terrain) {
            int phase=s.kind==SurfaceKind.VENT?(int)((tick+s.phase)%300):s.phase;
            boolean active=!arena && (s.kind==SurfaceKind.MOLTEN || s.kind==SurfaceKind.ACID || (s.kind==SurfaceKind.VENT && phase>=90&&phase<160));
            if(active && level==3) for(Structure p:structures) if(p.kind==Kind.COOLANT && p.effectTicks>0 && Math.abs(s.x-p.bounds.x)<850)active=false;
            result.add(new Surface(s.x,s.width,s.kind,phase,active));
        }
        return List.copyOf(result);
    }
    public List<Platform> platforms() { return platforms; }
    public List<Event> drainEvents() { List<Event> copy=List.copyOf(events);events.clear();return copy; }
    public Snapshot snapshot() {
        return new Snapshot(level,tick,surfaces(),structures.stream().map(s->new Prop(s.id,s.kind,s.bounds,s.hp,s.maxHp,s.effectTicks,s.hatch>0&&s.hatch<=120?s.hatch:0)).toList(),lifts,platforms,sectionKey(),arena);
    }
    private int sectionIndex() { return playerX<2400?0:playerX<5050?1:playerX<7600?2:3; }
    private String sectionKey() { return "chapter."+(level+1)+".section."+(sectionIndex()+1); }
    private static boolean overlaps(double x,int w,double x2,int w2){return x+w>x2 && x<x2+w2;}
}
