# 🔥 Merge Hell Runner

> **"The only IDE plugin where `rm -rf /` is a valid survival strategy."**

[![Version](https://img.shields.io/jetbrains/plugin/v/29132-merge-hell-runner)](https://plugins.jetbrains.com/plugin/29132-merge-hell-runner)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/29132-merge-hell-runner)](https://plugins.jetbrains.com/plugin/29132-merge-hell-runner)

**Merge Hell Runner** is a hardcore side-scrolling survival game running directly inside your IntelliJ IDEA.

Tired of fixing merge conflicts? Exhausted by memory leaks? **Don't fix them. Jump over them.** Turn your coding stress into high-octane parkour gameplay.

## Version 2.0.0 · Illustrated worlds, deeper combat

The 2.0.0 release brings rebuilt later chapters, four playable operatives, eight-way aiming,
three difficulty settings, longer exploration routes and quieter, clearer combat presentation.
Read the [release notes](docs/releases/2.0.0.md) for the highlights.

Build your run with six starting weapons, upgrades, evolutions and six combat combinations.
The game includes distinct synthesized weapon sounds, checkpoint Continue, persistent settings,
and a compact HUD for small tool windows. Press **O** from the menu or pause screen for settings;
use arrows to select/adjust, Enter to toggle, and Esc to return. Audio is **muted by default**;
**M** enables or mutes it and saves your choice. Five world ambience loops and a Boss theme
fade between scenes. Set **Background Audio** to 0 to keep only combat effects.
Pausing, choosing upgrades, switching to the editor, hiding or closing the game silences audio.
Returning focus keeps the game paused until you resume it. Muted startup opens no audio device.
Pause then press **Q** to return to the menu; **R** continues from the saved safe segment or level entrance.
Closing mid-fight does not preserve that exact combat frame.

The industrial city, repair robot, first-world enemies and Legacy Boss now use the illustrated art
pipeline, with real transparency, joint animation and cached texture reduction for small windows.
After each world, **Enter** continues and **Q** returns to the menu. The next entrance is saved
when the world is cleared; all five worlds award their first-clear points consistently.
All five Bosses now announce their arrival with a visible three-second preparation window,
and a small **[ B ] BOMBS** indicator inside the existing HUD keeps the remaining charges visible. Shared industrial
scenery adds shallow water, stepped decks and shootable props; details are below.
The later chapters are now rebuilt around three separate worlds: **Aerial Citadel**, **Geothermal
Foundry**, and **Alien Hive**. Each has an exclusive three-enemy ecosystem, an authored physical
route, an independent painted panorama and a multipart Boss with its own combat rules.
Chapter 3 uses suspended bridges, counterweights and moving lifts; Chapter 4 uses conveyors,
molten trenches and coolant; Chapter 5 uses acid, root ledges, living membranes and hatching nests.
The original first two chapters retain their industrial city and leaking pipeworks identity.
The [chapter design and acceptance matrix](docs/design/evolution/chapter-quality-overhaul.md)
defines the overhaul, and [art provenance](docs/design/evolution/chapter-premium-art.md)
records the production assets. Headless captures and automated combat checks are recorded separately
from native IDE performance and human playthroughs.

---

## Combat, characters and exploration

Choose **Y** for your operative, **D** for Relaxed / Standard / Challenge, and **Z** for
an independent starting weapon. The default is Standard, with tougher enemies,
faster recovery between attacks, paired later Boss patterns and finite bomb damage. Boss
telegraphs retain their preparation time. Rankings are separated by difficulty.

Hold **↑** to aim up, combine it with **← / →** for diagonals, and hold **↓** in the air
to fire down. On the ground **↓** crouches with a lower muzzle and smaller hitbox. Hold **V**
to aim without walking. All six weapons and all four illustrated operatives use the new aiming
poses. **F1** opens the bilingual controls guide and pauses combat.

All five routes now include solid obstacles, upper routes, two required facility interactions
and an optional secret room. Boss gates in chapters 2–5 move from 7,600 to 9,900 world units; the first
mission gains about 27% more authored time. Press **E** at the marked facilities. The first
chapter's abandoned office unlocks the tired, bespectacled **Night-shift Engineer** in a normal
campaign. Practice can preview the character. Quiet ground segments save resources, route
changes and discovery state; **R** continues that checkpoint. Existing entrance saves remain
compatible and default to Repair / Standard. Audio remains muted by default.

本轮新增八向射击、地面蹲射、三档难度、独立角色选择，以及五关的跳跃路线、设施事件和秘密。
主菜单 **Y 选角色 / D 选难度 / Z 选武器**，游戏内 **F1 看操作、E 交互、V 定点瞄准**。
正常战役探索第一关旧办公室可解锁戴厚眼镜、挂工牌的夜班工程师；安全段落会自动保存。

See the [implementation and validation record](docs/design/evolution/combat-depth-validation.md)
for balance values, source artwork and the limits of automated playthrough evidence.

Character proportions, shoulder and neck connections, two-handed weapon grips and crouch poses
have been refined across all four operatives. Ground enemies can climb around solid route walls;
fliers route above or below them, and burrowers wait for clear ground before emerging. Ordinary
shots now use local weapon or organ charge cues, while piercing shots, rushes, leaps and Boss
attacks keep the warning detail needed to react. Enemy health bars appear after damage.
See the [character, navigation and warning validation](docs/design/evolution/character-terrain-polish-validation.md)
for the latest evidence.

---

## 🎮 World Preview

<img src="docs/qa/data/2026-09-14-chapter-overhaul/en-chapter3-boss-ready-960.png" alt="Aerial Citadel: articulated gantry boss and suspended steel platforms" width="720">
<img src="docs/qa/data/2026-09-14-chapter-overhaul/en-chapter4-route-1-960.png" alt="Geothermal Foundry: conveyors, coolant tank and molten trench" width="720">
<img src="docs/qa/data/2026-09-14-chapter-overhaul/en-chapter5-boss-ready-960.png" alt="Alien Hive: organic terrain and the Rootheart's two brood organs" width="720">

These are actual Swing game frames. The [release QA report](docs/qa/2026-09-14-chapter-overhaul.md)
records the explicit capture fixtures, combat probes, bilingual checks and local package verification.

---

## 🚀 Campaign features

Play a **five-chapter campaign** with distinct worlds, arena survival, exploration and a boss at the end of every route.

* **🗺️ 5-Level Campaign**: Run through **Repository City → Heap District → Aerial Citadel → Geothermal Foundry → Alien Hive**. The opening mission evolves across five biomes; every later mission has its own architecture, route layout, and atmosphere.
* **👹 5 Unique Bosses**: Break the dependency graph of the **Legacy Code Monstrosity** (⚠️), survive **Memory Leak Daemon** (💀), outmaneuver **Aerial Gantry Architect**, dismantle the **Geothermal Siege Engine**, and destroy the **Alien Rootheart**. Each encounter has three stages and its own attacks, summons, silhouette, and HUD identity.
* **🧬 Level-Specific Enemies**: Heap **Leaks** give way to rail sentinels, shield wardens and cable wasps in the sky; welding drones, thermal drillers and slag crawlers in the foundry; resin spitters, spore drifters and pouncers in the hive. Each later chapter uses only its own three families.
* **🌊 Arena Battle Zones**: Clear authored squads before the camera unlocks. Later chapters combine their own specialist enemies with bridges, foundry platforms or living defenses.
* **🔫 6-Weapon Arsenal**: Choose **Commit Cannon**, **Force Push**, **Rapid CI**, **Garbage Collector**, **Firewall**, or **Refactor Beam**, with dedicated upgrade and evolution paths.
* **⚡ Sudo Overclock**: Kills and projectile reflections build overclock charge. Filling the meter grants a brief boosted spread-shot barrage.
* **🛡️ Defense Matrix**: Shield pickups provide temporary damage immunity; the Shield Reboot upgrade can prevent a fatal hit. Grab ❤️ health drops to recover HP.
* **🪙 Coins & Leaderboard**: Collect coins mid-run and chase your **Top 5 high scores**, saved locally in your IDE.
* **🎹 Combo System**: Chain kills to rack up multipliers and inflate your score.
* **📺 Retro Visuals**: Immersive CRT scanline filter, parallax code rain, and floating combat text that reads like a hacker's terminal.

---

## 🕹️ Controls

| Key | Action | Description |
| :--- | :--- | :--- |
| **Space** | `Jump` | Press once to jump. **Press again in mid-air for Double Jump.** |
| **Enter** | `Start / Jump / Continue` | Start a run, jump during combat, or continue after clearing a world. |
| **C** | `Commit / Shoot` | Fire code projectiles to debug enemies. |
| **← / →** | `Move` | Dodge left and right. |
| **↑ / ↓** | `Aim / Crouch` | Aim up, crouch on the ground, or aim down in the air. Combine with left/right for diagonals. |
| **V** | `Aim lock` | Hold to select a firing direction without walking. |
| **Y / D** | `Character / Difficulty` | Choose the operative and difficulty on the title screen. |
| **F1** | `Controls guide` | Open the localized controls guide; combat pauses while reading. |
| **Shift** | `Dash` | Dash through danger; briefly grants invulnerability. |
| **X** | `Melee` | Strike nearby enemies. |
| **Z** | `Weapon` | Cycle weapons; on the title screen, choose a starting weapon. |
| **B** | `Bomb` | Deal emergency area damage and clear hostile shots, with a one-second cooldown; the small [ B ] indicator shows remaining charges. |
| **E** | `World interaction` | Activate a nearby marked facility, collect a secret, purge Heap or open a Foundry coolant valve. Shoot sky counterweights, hive membranes and nests. |
| **F** | `Risk salvage` | At an unused Heap route station, take a larger one-time reward and increase leak pressure. |
| **1** / **2** / **3** | `Upgrade` | Choose an upgrade when a draft appears. |
| **R** | `Continue / Reroll` | Continue from the saved safe segment or level entrance in the menu; reroll an upgrade draft when available. |
| **P** / **Esc** | `Pause` | Pause the game (and pretend you're working). |
| **Q** | `Return to menu` | Available while paused or at world completion; Continue restores the saved checkpoint. |
| **O** | `Settings` | Open from menu/pause; arrows adjust, Enter toggles, Esc returns. |
| **M** | `Mute` | Toggle saved mute without changing volume. |
| **G** | `Invincible practice` | Start from the menu with invincibility; preserves the campaign checkpoint. |
| **L** | `Boss practice` | From the menu, start invincible practice at Legacy; during Lab, advance to the current Boss. |
| **T** / **F12** | `Invincibility` | Toggle damage immunity; a Lab run remains unranked even after switching it off. |

### Language / 界面语言

Press **O** from the menu or pause screen, select **LANGUAGE**, and use left/right or Enter
to choose **English** or **简体中文**. The whole game switches immediately and saves the
choice, including current dialogue and existing event messages. Older saves initially
follow the OS language (Chinese or English). Keycap names and code syntax remain literal.

主菜单或暂停时按 **O**，选择 **界面语言**，用左右键或 Enter 切换中文/英文。
菜单、战斗提示、升级说明、剧情与结算统一切换，选择自动保存。

### Lab Mode

The menu offers **G / 无敌练习** and **L / Boss 练习** as clickable practice entries.
Both start with invincibility and unlimited ammunition/bombs, without replacing the saved
campaign checkpoint. While enabled, the HUD shows **无敌开启 · [ T ] 关闭**. Switching it off
confirms normal damage for about three seconds, then removes the status badge. The result
screen explains that a run which used practice does not award ranking or permanent rewards.
**R** resumes the saved campaign with normal damage; **N** starts a new normal campaign.

During a run, press **F12** or **T** to toggle invincibility; enabling it marks that run
permanently **unranked** and discards its own campaign checkpoint. Use **H** for supplies, **U** for an upgrade draft,
**J** for the next encounter, **K** to clear the current wave, and **L** to arm the boss
gate. Press **N** to start a fresh ranked run with Lab Mode disabled.

Repository City's route now shows stage number, current-stage time and the actual Boss
gate conditions. Its seven timed stages take approximately 5:30 of active game time; the last requires
30 kills in that stage, followed by clearing remaining enemies. Pauses and upgrade choices
do not advance the route. Moving shots inherit the player's completed horizontal movement
once at launch, preserving each weapon's relative speed during a run.

### Drone Copilot

Drone Copilot now deploys visible mechanical companions that follow, aim and fire independently
of the player's trigger. Rank 1 deploys one drone; rank 2 adds a second with staggered shots and
separate targets when available. Rank 3 retains two drones, increases damage from 40% to 55% of
the current weapon's damage and shortens each drone's firing interval from 1.15 to 0.77 seconds.
Its rounds can penetrate one ordinary enemy. Drones prioritize marked targets and can attack
Legacy's exposed nodes/core and later Bosses. Upgrade cards show the actual next-rank effects.

### Boss arrivals, terrain and props

Every Boss, including Legacy, now approaches during an approximately **three-second safe
preparation window**. The display names the Boss, shows its approach direction and counts down.
You can move and jump into position while attacks and damage are suspended; pausing also freezes
the countdown. A small **[ B ] BOMBS** indicator shares the existing lives/resource row and
shows the remaining count, empty status or **∞** while invincibility grants unlimited charges.
It no longer extends a separate panel over the playfield in either window layout.

The first two chapters' industrial scenery adds parallax gantries, hanging cables, work lights and drifting haze.
Thin, irregular puddles sit on the metal deck, without raised rims or disconnected dripping pipes.
They reflect the repair robot and respond to footsteps, landings and dashes with ripples and splashes.
This is shallow-water traversal; swimming is not implemented. Terraced
routes use 24/48-pixel walk-up steps and an 80-pixel deck reached by jumping.

Shoot a **capacitor** to discharge 80 damage in a 150-pixel radius against at most 16 hostile
enemies. Shoot a **supply cache** for one bomb and 15 HP, subject to the existing resource caps.
Each prop can be claimed only once in the current level attempt. These common interactions
belong to the industrial city and pipeworks routes. Saved English/Chinese selection and default-muted audio continue to apply.

### Aerial Citadel: cross the skyworks

Four broken sky bridges can be crossed with double jumps and their moving lifts. Shoot a hanging
counterweight to secure a full-width crossing. Ground sentinels and shield wardens stay on their
side of the shaft; cable wasps can fly across. Wardens resist frontal shots while guarding, then
expose themselves after a clearly marked ram. The two combat arenas use deliberately small squads.

The Gantry Architect has two independently destructible claws. Losing a claw removes its associated
attacks; destroying both opens a four-second core window before a visible rebuild. Use the arena's
platforms to reach the command unit. Sweeps and falling girders have locked, visible warnings.

### Geothermal Foundry: use the cooling infrastructure

Conveyors carry grounded actors, and raised steel or basalt ledges cross molten trenches.
Shoot a coolant tank or press **E** beside its valve to temporarily cool nearby hazards. Steam
vents warn before they fire. Welding drones commit to a dive, drillers telegraph their emergence,
and slag crawlers fire real gravity-driven shells.

The Siege Engine has a front armor plate and a rear vent. Break the plate or wait for a cooling
cycle, then shoot the vent to force an overheat and attack the exposed engine. Its drill charge
announces the ground corridor; mortar arcs show where shells will land. The route's **E** interaction
operates coolant valves; the Boss's weak points open through plate damage, cooling and vent hits.

### Alien Hive: destroy the living defenses

Acid pools, root ledges, shootable membranes and finite hatching nests replace the foundry's machinery.
Resin spitters, spore drifters and six-legged pouncers create different attack heights and timing.
Destroy nests early to prevent their remaining broods, or use the upper roots to bypass danger.

Destroy the Rootheart's two brood organs to expose its heart. Tendril strikes, spore fans and hatching
have independent warnings; the final phase sheds the outer defenses and brings the heart into a
mobile attack. All six weapons, melee and drone shots resolve hits against the visible body parts;
bombs damage the Boss and its surviving appendages.

Projectile limits admit complete volleys before spawning. Existing shots stay in play; when full,
player weapons preserve ammunition, cooldown and charged attacks until space becomes available.
Map motion, warning timers and Boss mechanisms freeze during pause and upgrade selection.

### Heap District: reclaim the pipeworks

The second world now has leaking pipeworks, three route GC stations and a short chapter that
unfolds as the player reaches the archive, reflux main and pump room. **E** cleans the visible
area after a short activation, reducing pressure; **F** salvages more score/XP immediately but
raises pressure. A route station can be used only once, and all three may be bypassed.
Leaks warn before becoming harmful, remain bounded, and leave open ground between pools.

Memory Leak sends visible blocks back toward its core. Shoot or melee a block to interrupt it;
an arrival restores a small, capped amount of health. Two reusable GC stations clear the visible
hazards and bullets, then open a brief **+50% damage** window. The damaged core, delayed health
bar, local impacts and distinct sounds expose the result of each hit. Pauses and upgrade choices
freeze the mechanisms; revival clears temporary danger while retaining spent stations.

---

## 👾 The Bestiary (Enemies)

The first two chapters introduce the original industrial enemies: charging **Bugs** and **Crashes**,
advancing **Merge Conflicts**, floating **Deadlocks**, tall **Firewalls**, heavy **TechDebt**, and drifting
heap **Leaks**. Shoot obstacles or jump around their visible bodies; contact and ranged attacks have
different threats. Chapters 3–5 each introduce three exclusive species with distinct attack commitments
and recovery windows.

### Chapter 3 · Aerial Citadel

| Enemy | Behavior | Counterplay |
| :--- | :--- | :--- |
| **Survey Sentry** (`SENTINEL`) | A planted survey turret commits to one fast rail shot, with a bright muzzle and short direction marks. Its firing position stays fixed through the warning. | Change height or move after it takes aim, then attack during recovery. Ground sentinels stay on their side of a bridge gap. |
| **Bulwark Warden** (`WARDEN`) | A construction guard advances behind frontal armor, then marks and commits to a short ground ram. | Frontal guarding reduces damage without granting immunity. Attack from behind, jump over the committed ram, or punish the open recovery. |
| **Cable Wasp** (`RIGGER`) | An airborne rig patrols across shafts, charges its weapon and fires two parallel shots above and below its aiming line. | Dodge the pair or use the space between them. Jump to its height or let drones cover the air while it recovers. |

### Chapter 4 · Geothermal Foundry

| Enemy | Behavior | Counterplay |
| :--- | :--- | :--- |
| **Arc Welder** (`INTERRUPT`) | A hovering welder marks a destination, dives along the committed path and slowly climbs back to patrol height. | Move after the destination locks; punish the low end of the dive before it returns to patrol height. |
| **Thermal Driller** (`DRILLER`) | A tracked burrower approaches beneath the deck, pauses with an emergence warning, then bursts upward before settling into recovery. | Its exposed crest remains shootable while buried. The warning has no contact damage; clear the marked eruption and attack the fully exposed body afterward. |
| **Slag Bellows** (`SLAG_SPITTER`) | A heavy furnace crawler plants itself, charges its furnace and lobs a shell toward the position it targeted. The shell follows a gravity-driven arc. | Keep moving after it charges and watch the falling shell. Close the distance between volleys, using ledges and coolant to manage the surrounding floor hazards. |

### Chapter 5 · Alien Hive

| Enemy | Behavior | Counterplay |
| :--- | :--- | :--- |
| **Resin Mimic** (`MIRROR`) | A chitinous ground creature keeps its distance, charges its firing organ, releases a two-shot resin fan and retreats during recovery. | Dodge the separated shots, then close in or change height while it retreats. |
| **Spore Drifter** (`SPORE_POD`) | A floating incubator releases three arcing seeds at different horizontal speeds after a long warning. | Track the descending arcs and reposition between their landings. Reach it from root ledges or use drones to cover its elevated body. |
| **Chitin Pouncer** (`LURKER`) | A low, fast six-legged creature stalks the player, crouches to mark a landing and commits to a long arcing leap. Route nests and the Rootheart can hatch more of these creatures. | Change position after the landing locks, then attack during the recovery. Destroy route nests early to prevent their remaining broods. |

---

## 🛠️ Installation & Development

### Prerequisite
* IntelliJ IDEA (2023.2 or later recommended)
* JDK 17

### Run from Source
1.  Clone this repository.
2.  Open the project in IntelliJ IDEA.
3.  Run the Gradle task:
    ```bash
    ./gradlew runIde
    ```

### Install Plugin (Manual)
1.  Build the plugin: `./gradlew buildPlugin`
2.  Go to IDEA `Settings` -> `Plugins` -> `⚙️` -> `Install Plugin from Disk...`
3.  Select `build/distributions/merge-hell-runner-2.0.0.zip` without extracting it.

For release engineering instructions, see [RELEASING.md](RELEASING.md). For the full
change history, see [CHANGELOG.md](CHANGELOG.md).

---

## 📸 Screenshots

| Boss Fight | Start Screen |
| :---: | :---: |
| ![docs/boss_preview.png](https://github.com/rainism0329/springclouddemo/blob/master/merge%20hell2.png) | ![docs/sudo_preview.png](https://github.com/rainism0329/springclouddemo/blob/master/merge%20hell.png) |

---

## 👨‍💻 Author

Crafted with ❤️, ☕, and a lot of `git merge --abort` by **Phil Zhang**.

* 🌍 **Portfolio:** [HomePage](https://phil-the-guy.netlify.app/)
* 📧 **Contact:** bigphil.zhang@qq.com

---

## 🤝 Contributing

Pull Requests are welcome! If you find a bug (in the game, not the enemies), please open an issue.

1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/NewBoss`)
3.  Commit your Changes (`git commit -m 'Add new Boss: The Microservice'`)
4.  Push to the Branch (`git push origin feature/NewBoss`)
5.  Open a Pull Request

---

*Enjoy the game, and may your build always succeed!*
