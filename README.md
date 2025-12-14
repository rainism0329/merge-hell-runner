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

## 🚀 Features v2.0: The "Sudo" Update

We've completely overhauled the engine in version 2.0. The geometry shapes are gone; the **Emoji-based chaos** is back!

* **⚔️ Epic Boss Battles**: Face off against the **Legacy Code Monstrosity** (⚠️) and the **Memory Leak Daemon** (💀). They dash, they summon firewalls, and they don't follow clean code principles.
* **⚡ Sudo Mode**: Pick up the Golden Thunderbolt to gain **ROOT ACCESS**. Your standard `git push` projectile transforms into a spread-shot `rm -rf` blast that wipes out everything on screen.
* **🛡️ Defense Matrix**: Equip the Shield (🛡️) to survive one fatal `NullPointerException`.
* **🎹 Combo System**: Chain kills to rack up multipliers and high scores.
* **📺 Retro Visuals**: Immersive CRT scanline filter and dynamic parallax background that reads like a hacker's terminal.

---

## 🕹️ Controls

| Key | Action | Description |
| :--- | :--- | :--- |
| **Space** | `Jump` | Press once to jump. **Press again in mid-air for Double Jump.** |
| **C** / **Enter** | `Commit / Shoot` | Fire code projectiles to debug enemies. |
| **← / →** | `Move` | Dodge left and right. |
| **Esc** | `Pause` | Pause the game (and pretend you're working). |

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
