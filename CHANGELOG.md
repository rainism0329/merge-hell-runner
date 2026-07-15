# Changelog

All notable changes to Merge Hell Runner are documented here. The project follows
[Semantic Versioning](https://semver.org/).

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
