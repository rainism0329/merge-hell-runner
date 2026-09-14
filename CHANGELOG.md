# Changelog

All notable changes to Merge Hell Runner are documented here. The project follows
[Semantic Versioning](https://semver.org/).

## [Unreleased]

- Rebuild chapters 3–5 as the Aerial Citadel, Geothermal Foundry and Alien Hive, each with an
  independent illustrated panorama, exclusive three-enemy roster, physical terrain and multipart Boss.
- Replace generic support-switch encounters with moving lifts and counterweight bridges, conveyors
  and coolant, living membranes and bounded hatching nests. Add chapter-specific radio narrative.
- Gantry claws, siege armor/heat vent and alien brood organs now have real collision geometry;
  destroyed components change attacks, and drones target actual parts. Empty body gaps pass shots.
- Fix directional-armor damage accounting, melee repeat hits within one swing, safe bridge rider
  transfer, multipart respawn recovery and right-edge enemy spawn pruning. Preserve bilingual UI,
  default mute, first-two-chapter behavior and level-entrance checkpoint compatibility.

The entries below describe earlier development steps; the chapter overhaul supersedes the old
Blueprint, Kernel and Singularity switch mechanics in the current campaign.


- Add Singularity Edge's three bounded echo fields, grounded E stabilization and a paired-anchor
  resonance window that clears bullets, stops the final Boss and exposes its core. Give Mirror and
  the final Boss mechanical bodies and the final sector its own cached containment hall.
- Select the final Boss's announced attack order from bounded recent movement and successful-shot
  history; use each echo once per four attacks, lock warning geometry before firing, and provide safe
  phase changes, grounded charge preparation and recovery. Keep both languages and default-muted audio.
- Bake the industrial panorama's static lighting into one reusable normal/mirrored raster pair,
  preserving the original composition path for transformed or clipped callers.

- Add Kernel Core's timed power rails, nearby E switches, permanent refuge platforms and
  one-time route rewards. Armed fault nodes interrupt the actual grounded Boss charge, clear
  arena bullets and expose the core. Add complete charge/lane warnings and safe phase reboots.
- Replace Interrupt and Kernel Boss bodies with cached industrial machinery and add a turbine
  workshop, connected power infrastructure and interaction-driven bilingual chapter dialogue.
- Admit player/enemy volleys before generating them, retaining existing projectiles at capacity.
  Rejected player fire preserves ammunition, charge, heat and cooldown; enemy shooters wait
  visibly, Boss volleys remain atomic, and companion/reflection admission is observable.
  Evidence: `docs/qa/2026-09-14-kernel-core-and-projectile-budget.md`.

- Shrink bombs into the existing HUD resource row, releasing the separate card's playfield space.
  Replace rectangular water troughs and detached dripping outlets with thin irregular puddles,
  soft wet edges and restrained floor reflections; move route progress below the deck face.
- Add Blueprint Citadel's shootable/overloadable supports, linked walkways with delayed collapse,
  telegraphed scanners, one-time route rewards and progress-driven dialogue. Connect two reusable
  Architect supports to real projectile clearing, attack interruption and a +50% core damage window.
  Add mechanical Sentinel/Architect bodies and layered drafting-hall scenery; retain bilingual UI,
  saved settings and muted startup. Current evidence: `docs/qa/2026-09-11-blueprint-and-polish.md`.

- Give all five Bosses a visible approximately three-second arrival sequence with a localized
  name, approach direction and countdown. Allow safe movement/jumping while attacks and damage
  are suspended; freeze preparation on pause and reset it for a new run.
- Add a `[ B ]` bomb indicator with remaining charges and empty/infinite status
  in normal and compact HUDs.
- Add shared industrial parallax gantries, cables, work lights and haze. Shallow pools reflect
  the player and react to actual footsteps, landings and dashes with bounded ripples and splashes;
  water remains shallow traversal rather than a swimming system.
- Add 24/48-pixel walk-up steps and an 80-pixel jump deck. Shootable capacitors deal 80 damage
  within a 150-pixel radius to at most 16 hostiles; supply caches grant one bomb and 15 HP up to
  existing caps. Each prop can be claimed once per level attempt.
- Keep the new HUD, preparation and environment text consistent in English and Simplified
  Chinese, and preserve default-muted audio and its saved controls. See the
  [current implementation record](docs/qa/2026-09-11-environment-and-boss-arrival.md).

- Default to muted audio, including old XML with no explicit mute preference; retain deliberate unmute choices.
  Add original quiet ambience for all five worlds and Boss music, smooth transitions and a separate
  background volume. Defer audio-device creation until enabled during gameplay. Silence upgrade
  selection, focus loss, hidden panels and loop errors; discard stale effects across pause races.
  Keep the audio controls and live mute hint consistent in English and Simplified Chinese.

- Add saved English / Simplified Chinese selection across the game, including retained dialogue,
  logs, combat text and error recovery. Add matching UTF-8 catalogs, per-frame language isolation,
  CJK font fallback and measured bilingual menu/settings/upgrade layouts.

- Add six-weapon build/evolution paths, bounded combat combinations and distinct cached weapon sounds.
- Add owned Schema 2 level-entrance checkpoints, independent seeded encounters/drops, and old-save backups.
- Add persistent audio/visual/control settings and readable compact HUD/settings layouts for small windows.
- Add an industrial city sample, asset/animation metadata, simulation-owned actor poses and complete-frame publication.
- Fix input repeat/focus cancellation, cross-level recovery, lethal overkill/revival, paused Boss layering and blocked node hints.
- Integrate true-alpha repair/Bug/TechDebt rigs, stable joints and foot contacts, mirrored visor text,
  reactor stages, accurate volley warnings, and preloaded texture reductions for small sprites.
- Settle all five world rewards consistently and let players continue or safely exit at completion.
- Add visible invincible/Boss practice entries that preserve the campaign checkpoint. Show active
  invincibility, briefly confirm normal damage when disabled, and explain practice eligibility at settlement.
- Fix running shots losing relative range; inherit completed horizontal movement once at launch,
  clamp before firing at screen boundaries, and sweep friendly shots through targets between ticks.
- Retune Repository City's timed lead-up to 4:20, fix the last-stage timer jump, and show stage,
  final-stage kill and Boss-gate clearance conditions.
- Complete Conflict, Crash, Lock and Firewall industrial sprites for the first-world enemy roster.
- Replace head-mounted drone volleys with visible independent companions, rotating turrets, thrusters
  and a quiet firing cue. Add staggered dual-drone targeting, Boss support, reinforced rank-3 hulls,
  faster armor-piercing fire and upgrade cards showing real per-rank values.
- Keep drone formations separated through turns and screen edges; restore them safely after
  level transitions, Continue and revival, and freeze them during pauses and upgrade choices.
- Remove the persistent UNRANKED HUD badge after T is disabled; retain the current run's practice
  eligibility and make both restored normal damage and the fresh normal-run shortcut explicit.
- Add Heap District's GC purge/risk-salvage stations, telegraphed leak pools, bounded pressure,
  shootable reflux blocks, reusable Boss breakers and progress/choice-driven chapter dialogue.
- Give Boss hits, blocked shots, destroyed nodes, vulnerability and phase changes distinct local
  reactions, bounded hitstop, delayed damage bars and cached sounds; GC windows apply real +50% damage.
- Settle lethal damage before an upgrade can open, freeze further collisions when a GC reward opens
  a draft, and clear both the actual Boss vulnerability and its chapter hint on revival.
- Keep implementation and validation records in `docs/qa/`; the prior Heap/Boss-feedback batch
  remains documented in `docs/qa/2026-09-11-heap-chapter-and-boss-feedback.md`.
  Full world 3–5 expansion, physical audio checks, human IDE playthrough/performance acceptance
  and the full timing migration remain in development.

## [1.0.0] - 2026-07-15

### Added

- A complete five-mission campaign with escalating routes, arenas, platform layouts,
  mission objectives, boss gates, and a final victory sequence.
- Five distinct worlds: Repository City and its changing Dark biomes, Heap District,
  Blueprint Citadel, Kernel Core, and Singularity Edge.
- Five handcrafted bosses:
  - Legacy Code Monstrosity, a dependency-graph encounter.
  - Memory Leak Daemon, with Heap Spray and GC Storm patterns.
  - The Architect, with Blueprint Grid and Runtime Wall patterns.
  - Kernel Panic Overlord, with Core Dump dashes and Panic Lanes.
  - Singularity Engine, which learns patterns from the preceding bosses and closes
    with Event Horizon attacks.
- Three-stage boss identities, phase-specific HUD status, telegraphed attacks, unique
  silhouettes, and level-specific summons.
- Level-specific enemy ecosystems, including Leak, Sentinel, Interrupt, and Mirror,
  alongside the original Bug, Conflict, Crash, Lock, Firewall, and TechDebt roster.
- Arena Battle Zones with Rush, Mixed, Sniper, and Mini-Boss wave formations.
- Six weapons, melee, bombs, dash invulnerability, double jump, shields, health,
  coins, combo scoring, upgrades, and a persistent local Top-5 leaderboard.
- Lab Mode for rapid QA: toggle with `F12` or `T`, then use supply, upgrade, route,
  clear-wave, and boss shortcuts. Lab runs are explicitly unranked.

### Fixed

- Cleared Battle Zones can no longer reactivate and trap the player in repeating waves.
- The Lab boss shortcut (`F11` or `L`) now advances correctly in all five missions,
  including after a boss has already been cleared in a previous mission.
- Mission-complete presentation now remains fully visible before fading into the next
  mission instead of flashing for a single frame.
- Boss warnings and transitions now advance on the game loop rather than an unrelated
  Swing timer, keeping pause and transition behavior deterministic.
- Mission numbering and mission cards no longer show stale data after level changes.

### Quality

- Added regression coverage for campaign transitions, Battle Zone completion, Lab
  routing, boss move identity, level-specific enemy rosters, and world rendering.
- Added bounded hostile spawning and clearer spawn protection/attack telegraphs.

[1.0.0]: https://github.com/rainism0329/merge-hell-runner/releases/tag/v1.0.0
