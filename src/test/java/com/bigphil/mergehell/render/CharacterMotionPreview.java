package com.bigphil.mergehell.render;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.progression.CharacterId;
import com.bigphil.mergehell.progression.GameDifficulty;
import com.bigphil.mergehell.progression.RunBuild;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;

/** Bounded visual QA driven by real Player inputs and ActorVisuals updates, not authored Hero poses. */
public final class CharacterMotionPreview {
    private static final int TICKS = 288, GROUND = 300, CELL_W = 330, FRAME_H = 560;
    private static final int[] SAMPLES = {16,43,64,72,78,84,90,96,102,108,114,137,160,179,197,208,218,234,250,272};
    private static final Color BG = new Color(18,25,32), GRID = new Color(55,77,86);
    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private record Input(boolean left, boolean right, boolean up, boolean down, boolean locked,
                         boolean shoot, String label) { }

    private static final class Subject {
        final CharacterId role;
        final Player player = new Player(600, GROUND - 30);
        final ActorVisuals rig = new ActorVisuals();
        final List<Projectile> shots = new ArrayList<>();
        final Set<String> directions = new LinkedHashSet<>();
        final EnumSet<ActorVisuals.Action> actions = EnumSet.noneOf(ActorVisuals.Action.class);
        final BufferedImage sheet = image(5 * 220, 4 * 235);
        boolean crouchMoved, airborneDown;
        Subject(CharacterId role) {
            this.role = role;
            var build = new RunBuild(WeaponId.COMMIT_CANNON);
            build.setIdentity(role, GameDifficulty.STANDARD);
            player.setRunBuild(build);
            player.setCombatSeed(20260915L + role.ordinal());
            player.update(false,false,false,false,GROUND,12_000,shots,List.of());
            rig.update(player,List.of(),0);
        }
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "build/polish/motion" : args[0]);
        Files.createDirectories(output.resolve("frames"));
        var art = IndustrialArt.load();
        List<Subject> subjects = new ArrayList<>();
        for (var role : CharacterId.values()) subjects.add(new Subject(role));
        List<String> csv = new ArrayList<>(List.of("tick,role,input,action,x,y,grounded,crouching,facing,aimX,aimY,phase,shotSequence,hp,muzzleX,muzzleY"));
        for (int tick = 0; tick < TICKS; tick++) {
            Input input = input(tick);
            BufferedImage frame = image(CELL_W * subjects.size(), FRAME_H);
            Graphics2D frameGraphics = frame.createGraphics();
            configure(frameGraphics);
            for (int column = 0; column < subjects.size(); column++) {
                Subject subject = subjects.get(column);
                Player player = subject.player;
                double previousX = player.getX();
                if (tick == 70 || tick == 94 || tick == 256) player.requestJump();
                if (tick == 208) player.takeDamage(10);
                if (tick == 216) player.melee();
                if (tick == 232) player.dash(1);
                player.setAimInput(input.up,input.down,input.locked,input.left,input.right);
                player.update(input.left,input.right,false,input.shoot,GROUND,12_000,subject.shots,List.of());
                subject.shots.removeIf(Projectile::isDead);
                subject.shots.forEach(Projectile::update);
                subject.rig.update(player,List.of(),.016);
                ActorVisuals.Hero hero = subject.rig.snapshot().hero();
                subject.actions.add(hero.action());
                subject.directions.add(sign(hero.aimX()) + ":" + sign(hero.aimY()));
                subject.crouchMoved |= player.isCrouching() && Math.abs(player.getX()-previousX) > .1;
                subject.airborneDown |= !player.isGrounded() && hero.aimY() > .1;
                if (!Double.isFinite(hero.phase()) || !Double.isFinite(player.muzzleX()) || !Double.isFinite(player.muzzleY()))
                    throw new AssertionError("Non-finite real pose at " + tick + "/" + subject.role);
                Graphics2D cell = (Graphics2D) frameGraphics.create(column*CELL_W,0,CELL_W,FRAME_H);
                drawFrame(cell,art,subject,input,tick); cell.dispose();
                for (int sample=0; sample<SAMPLES.length; sample++) if (SAMPLES[sample]==tick) {
                    Graphics2D thumbnail = subject.sheet.createGraphics();
                    thumbnail.translate((sample%5)*220,(sample/5)*235);
                    thumbnail.clipRect(0,0,220,235); configure(thumbnail);
                    thumbnail.setColor(GRID); thumbnail.drawRect(0,0,219,234);
                    thumbnail.setColor(Color.WHITE); thumbnail.drawString("T"+tick+" / "+hero.action(),8,17);
                    thumbnail.drawString(input.label,8,33);
                    thumbnail.setColor(new Color(160,192,203));
                    thumbnail.drawString("aim "+sign(hero.aimX())+","+sign(hero.aimY()),8,49);
                    Graphics2D actor=(Graphics2D)thumbnail.create();
                    actor.translate(104,220);actor.scale(2.8,2.8);actor.translate(-hero.x(),-hero.footY());
                    ActorVisuals.hero(actor,art,hero);actor.dispose();thumbnail.dispose();
                }
                csv.add(String.format(Locale.ROOT,"%d,%s,%s,%s,%.3f,%.3f,%s,%s,%d,%.5f,%.5f,%.5f,%d,%d,%.3f,%.3f",
                        tick,subject.role,input.label,hero.action(),player.getX(),player.getY(),player.isGrounded(),
                        player.isCrouching(),hero.facing(),hero.aimX(),hero.aimY(),hero.phase(),player.getShotSequence(),
                        player.getHp(),player.muzzleX(),player.muzzleY()));
            }
            frameGraphics.dispose();
            ImageIO.write(frame,"png",output.resolve("frames/frame-%03d.png".formatted(tick)).toFile());
        }
        List<String> summary = new ArrayList<>();
        summary.add("Production Player: "+Player.class.getProtectionDomain().getCodeSource().getLocation());
        summary.add("Production ActorVisuals: "+ActorVisuals.class.getProtectionDomain().getCodeSource().getLocation());
        summary.add("288 real simulation ticks per role; 1152 player updates; 16 ms per tick; no fabricated Hero snapshots.");
        for (Subject subject : subjects) {
            if (subject.directions.size()!=8 || !subject.crouchMoved || !subject.airborneDown
                    || !subject.actions.containsAll(EnumSet.of(ActorVisuals.Action.RUN,ActorVisuals.Action.RISE,
                    ActorVisuals.Action.FALL,ActorVisuals.Action.MELEE,ActorVisuals.Action.HURT))
                    || subject.player.getShotSequence()==0)
                throw new AssertionError("Input script missed coverage: "+subject.role+" "+subject.directions+" "+subject.actions);
            ImageIO.write(subject.sheet,"png",output.resolve(subject.role.name().toLowerCase(Locale.ROOT)+"-sequence.png").toFile());
            summary.add(subject.role+": directions="+subject.directions.size()+", actions="+subject.actions
                    +", crouchWalk="+subject.crouchMoved+", airborneDown="+subject.airborneDown
                    +", volleys="+subject.player.getShotSequence()+", hp="+subject.player.getHp());
        }
        Files.write(output.resolve("motion.csv"),csv,StandardCharsets.UTF_8);
        Files.write(output.resolve("summary.txt"),summary,StandardCharsets.UTF_8);
        Files.writeString(output.resolve("index.html"),html(),StandardCharsets.UTF_8);
        summary.forEach(System.out::println);
    }

    private static Input input(int tick) {
        if(tick<40) return new Input(false,true,false,false,false,true,"RUN_RIGHT");
        if(tick<60) return new Input(true,false,false,false,false,true,"RUN_LEFT");
        if(tick<70) return new Input(false,false,true,false,true,true,"AIM_UP");
        if(tick<118) {
            int[][] direction={{1,0},{1,-1},{0,-1},{-1,-1},{-1,0},{-1,1},{0,1},{1,1}};
            int[] aim=direction[(tick-70)/6];
            return new Input(aim[0]<0,aim[0]>0,aim[1]<0,aim[1]>0,true,true,"AIR_AIM_"+((tick-70)/6));
        }
        if(tick<156) return new Input(false,false,false,true,true,true,"JUMP_DOWN_LAND");
        if(tick<174) return new Input(false,true,false,true,false,true,"CROUCH_RIGHT");
        if(tick<192) return new Input(true,false,false,true,false,true,"CROUCH_LEFT");
        if(tick<208) return new Input(false,false,true,false,true,true,"STAND_UP_SHOT");
        if(tick<216) return new Input(false,false,true,false,true,false,"DAMAGE_AIM_UP");
        if(tick<224) return new Input(false,true,false,false,true,false,"MELEE");
        if(tick<232) return new Input(false,true,false,false,false,true,"RUN_RIGHT");
        if(tick<240) return new Input(false,true,false,false,false,true,"DASH");
        if(tick<256) return new Input(true,false,false,false,false,true,"RUN_LEFT");
        return new Input(tick<270,tick>=270,false,true,true,true,"JUMP_DOWN_TURN");
    }

    private static void drawFrame(Graphics2D g,IndustrialArt art,Subject subject,Input input,int tick) {
        var hero=subject.rig.snapshot().hero();
        g.setColor(GRID);g.drawRect(0,0,CELL_W-1,FRAME_H-1);
        g.setColor(Color.WHITE);g.drawString(subject.role+" / T"+tick+" / "+hero.action(),12,22);
        g.drawString(input.label+" / aim "+sign(hero.aimX())+","+sign(hero.aimY()),12,42);
        g.drawString("HP "+subject.player.getHp()+" / shots "+subject.player.getShotSequence(),12,62);
        g.setColor(new Color(140,168,180));g.drawString("3x camera follows feet",12,86);
        Graphics2D close=(Graphics2D)g.create();
        close.translate(150,290);close.scale(3,3);close.translate(-hero.x(),-hero.footY());
        ActorVisuals.hero(close,art,hero);close.dispose();
        g.setColor(GRID);g.drawLine(8,320,CELL_W-8,320);
        g.setColor(new Color(140,168,180));g.drawString("1x world: jump height preserved",12,342);
        Graphics2D world=(Graphics2D)g.create();
        world.clipRect(0,350,CELL_W,FRAME_H-350);
        world.translate(150-hero.x(),530-GROUND);
        world.setColor(GRID);world.drawLine((int)hero.x()-145,GROUND,(int)hero.x()+170,GROUND);
        for(int mark=-4;mark<=4;mark++) {
            int x=((int)hero.x()/40+mark)*40;world.drawLine(x,GROUND,x,GROUND+5);
        }
        subject.shots.forEach(shot->shot.draw(world));
        ActorVisuals.hero(world,art,hero);world.dispose();
    }

    private static int sign(double value) { return value < -.1 ? -1 : value > .1 ? 1 : 0; }
    private static BufferedImage image(int width,int height) {
        BufferedImage result=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=result.createGraphics();g.setColor(BG);g.fillRect(0,0,width,height);g.dispose();return result;
    }
    private static void configure(Graphics2D g) {
        g.setFont(FONT);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
    private static String html() {
        return """
                <!doctype html><html lang="en"><meta charset="utf-8"><title>Continuous production character motion</title>
                <style>body{background:#121920;color:#eff5f8;font:15px system-ui;max-width:1350px;margin:24px auto}img{max-width:100%}input{width:65%}button,select{padding:7px;margin:8px}</style>
                <h1>Continuous production character motion</h1>
                <p>288 actual Player + ActorVisuals ticks per role. The upper view follows the feet at 3x; the lower view preserves jump height at native scale. Inspect weapon/hand contact through aiming, running, landing, crouch, damage and melee transitions.</p>
                <img id="frame" src="frames/frame-000.png" alt="Four real player states">
                <div><button id="play">Play / pause</button><select id="speed"><option value="16">1x speed</option><option value="48">1/3 speed</option><option value="96">1/6 speed</option></select><input id="seek" type="range" min="0" max="287" value="0"><span id="tick">0</span></div>
                <script>let n=0,playing=false,last=0;const frame=document.querySelector('#frame'),seek=document.querySelector('#seek'),tick=document.querySelector('#tick'),speed=document.querySelector('#speed');
                function show(){frame.src='frames/frame-'+String(n).padStart(3,'0')+'.png';seek.value=n;tick.textContent=n}
                document.querySelector('#play').onclick=()=>playing=!playing;seek.oninput=()=>{playing=false;n=+seek.value;show()};
                function loop(t){if(playing&&t-last>=+speed.value){n=(n+1)%288;show();last=t}requestAnimationFrame(loop)}requestAnimationFrame(loop);</script>
                <h2>Actual sampled sequences</h2><img src="repair-sequence.png"><img src="scout-sequence.png"><img src="warden-sequence.png"><img src="engineer-sequence.png">
                <p><a href="motion.csv">All 1152 state records</a> · <a href="summary.txt">Coverage and production class provenance</a></p></html>
                """;
    }
}
