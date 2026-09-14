# 首关本轮实际画面

从最终生产代码的 `GamePanel → FrameMailbox → EDT paint` 导出，没有替换 HUD 或合成角色；完整输出位于 `build/visual-preview-first-mission`，含 15 个场景 × 3 种真实面板尺寸、96 帧连续运动和 6 张路线状态图，共 147 张 PNG。

- `menu-960.png` / `menu-600.png`：新版首页，练习、Boss、继续、设置和独立静音区域。
- `combat-960.png` / `combat-600.png`：通过 G 进入练习并移动，明确布置六种首关敌人以检查素材；包含真实刷怪和开火。
- `final-stage-600.png`：末段 30 杀/56 秒双条件，真实无敌状态。
- `boss-gate-600.png`：门口三敌清场条件，新 Conflict 已接入。
- `boss-warning-600.png`：Legacy 实际齐射预警，中央任务卡让位。
- `mission-complete-600.png`：受控 Boss 阶段经过真实死亡等待进入结算。

其中 6 张来自 `GameVisualPreview`，实际状态记录保留在 `frames.csv`；末段与门口两张路线状态图来自 `build/mission-validation/HudPreview.java` 的显式场景布置。完整主预览源代码为 `src/test/java/com/bigphil/mergehell/GameVisualPreview.java`。

以上均为 headless 受控画面，包含 Lab/直接阶段布置，不能当作正常通关、原生 IDE 焦点或实时性能证据。正常输入三种子记录单独见 [首关节奏报告](../2026-09-11-route-pacing-and-hud.md)。
