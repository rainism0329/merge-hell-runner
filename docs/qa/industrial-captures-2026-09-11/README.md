# 2026-09-11 真实工业美术截图

此处图片直接复制自 `build/visual-preview-art/`，由 `GameVisualPreview` 驱动生产
`GameLoop → GamePanel → GameRenderer → FrameMailbox → EDT paint` 输出，未合成概念界面或替换 HUD。
`-960` 为真实 960×600 面板，`-600` 为真实 600×400 面板（游戏画面 600×375，保留黑边）。

它们是固定 seed 20260911 的受控 LAB 视觉检查。敌人位置和 Boss 阶段被明确布置，
核心暴露/狂暴/完成画面使用控制器 damage 调用进入，所以**不属于正常通关证据**。
正常输入的首关证据单独保存在 `../images/2026-09-11-normal-input/`。

- combat：主角、Bug、TechDebt、尚未重制的 Conflict，工业地面与浮台。
- legacy-boss/core-exposed/enraged：节点与核心阶段的真实渲染。
- legacy-volley-warning-600：预警使用真实齐射弹道角度，关闭闪光仍然可见。
- mission-complete：真实关末界面，显示实际 bonus 和 Enter/Q；此处 LAB 不会保存或领取首通点。
- frames.csv：完整 15 场景 × 3 窗口和 96 连续动作帧的实际状态记录；本目录仅保留其中 8 张代表图。

完整输出共 141 张 PNG。重新编译后运行 `src/test/java/com/bigphil/mergehell/GameVisualPreview.java`
可重复捕获；骨架接触点、六武器弹道与 180 连续模拟步预览入口是 `render/RigVisualPreview.java`。
这些 headless 预览不验证原生 IDE 显示、设备声音、实际 FPS 或完整战役体验。
