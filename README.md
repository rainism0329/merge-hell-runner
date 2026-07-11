# 🔥 Merge Hell Runner

> **"The only IDE plugin where `rm -rf /` is a valid survival strategy."**

[![Version](https://img.shields.io/jetbrains/plugin/v/29132-merge-hell-runner)](https://plugins.jetbrains.com/plugin/29132-merge-hell-runner)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/29132-merge-hell-runner)](https://plugins.jetbrains.com/plugin/29132-merge-hell-runner)

**Merge Hell Runner** is a hardcore side-scrolling survival game running directly inside your IntelliJ IDEA.

Tired of fixing merge conflicts? Exhausted by memory leaks? **Don't fix them. Jump over them.** Turn your coding stress into high-octane parkour gameplay.

---

## 🎮 Gameplay Preview

![Gameplay Preview](docs/gameplay_placeholder.gif)

> *Featuring parallax code rain background, CRT scanline filters, and satisfying floating combat text.*

---

## 🚀 Features v1.0.0: The Campaign Release

Version 1.0.0 turns the endless runner into a **full 5-level campaign** — themed worlds, arena survival, and a boss waiting at the end of every route.

* **🗺️ 5-Level Campaign**: Fight through five hand-themed worlds — **Dark → Monokai → Solarized → Nord → Dracula** — each with more enemies, tighter platforms, and higher stakes.
* **👹 5 Unique Bosses**: Survive the **Legacy Code Monstrosity** (⚠️), **Memory Leak Daemon** (💀), **The Architect** (👑), **Kernel Panic Overlord** (💀), and the final **Singularity Engine** (☠️). They dash, summon firewalls, and don't follow clean code principles.
* **🌊 Arena Battle Zones**: Get locked into an arena and clear relentless enemy waves — **Rush**, **Sniper**, and **Mini-Boss** formations — before the gates reopen.
* **🔫 6-Weapon Arsenal**: Swap between `git push`, spread-fire `git push -f`, rapid `git commit -a`, the devastating `rm -rf /`, flaming `git blaze`, and the screen-clearing `sudo rm -rf /` laser.
* **⚡ Sudo Mode**: Pick up the Golden Thunderbolt to gain **ROOT ACCESS** and blast everything on screen with a spread-shot barrage.
* **🛡️ Defense Matrix**: Equip the Shield (🛡️) to survive one fatal `NullPointerException`, and grab ❤️ health drops to stay alive.
* **🪙 Coins & Leaderboard**: Collect coins mid-run and chase your **Top 5 high scores**, saved locally in your IDE.
* **🎹 Combo System**: Chain kills to rack up multipliers and inflate your score.
* **📺 Retro Visuals**: Immersive CRT scanline filter, parallax code rain, and floating combat text that reads like a hacker's terminal.

---

## 🕹️ Controls

| Key | Action | Description |
| :--- | :--- | :--- |
| **Space** | `Jump` | Press once to jump. **Press again in mid-air for Double Jump.** |
| **Enter** | `Start / Jump` | Start a run from the briefing, or jump during a run. |
| **C** | `Commit / Shoot` | Fire code projectiles to debug enemies. |
| **← / →** | `Move` | Dodge left and right. |
| **Shift** | `Dash` | Dash through danger; briefly grants invulnerability. |
| **X** / **Z** / **B** | `Melee / Weapon / Bomb` | Slash, switch weapons, or trigger the emergency clear. |
| **P** / **Esc** | `Pause` | Pause the game (and pretend you're working). |

---

## 👾 The Bestiary (Enemies)

Know your enemy to survive the sprint:

* 🐛 **Bug**: The classic pest. Small, annoying, everywhere.
* 🔥 **Firewall**: Tall barrier. You can't jump over it; you must shoot it down.
* 🔒 **Deadlock**: Floating locks that try to freeze your progress.
* 💥 **Crash**: Fast-moving explosive runtime errors.
* TODO **TechDebt**: Massive blocks of code that hurt you if you touch them.

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
3.  Select the generated ZIP file in `build/distributions/`.

---

## 📸 Screenshots

| Boss Fight | Start Screen |
| :---: | :---: |
| ![docs/boss_preview.png](https://github.com/rainism0329/springclouddemo/blob/master/merge%20hell2.png) | ![docs/sudo_preview.png](https://github.com/rainism0329/springclouddemo/blob/master/merge%20hell.png) |

---

## 👨‍💻 Author

Crafted with ❤️, ☕, and a lot of `git merge --abort` by **Phil Zhang**.

* 🌍 **Portfolio:** [HomePage](https://phil-the-guy.zeabur.app/)
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
