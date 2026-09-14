# 无人机与 T 关闭状态：实际面板截图

这些图片由正式构建通过后的生产 GamePanel 导出，走实际输入队列、16 ms 模拟、FrameMailbox 和 EDT paint。无人机等级与敌人位置是明确设置的验收前置条件，练习、Boss、暂停、T 和移动使用真实 ActionMap。没有拼接或替换 HUD，也不代表原生 IDE 或正常流程通关。

完整导出为 `build/drone-game-preview/` 的 44 PNG：七个场景各 960×600、600×400 两尺寸，加 30 个连续移动帧。本目录保存八张代表图及完整 `frames.csv`；CSV 的 unranked 列记录内部资格，用于核对 T 关闭不会继续无敌。

| 图片 | 检查内容 |
| --- | --- |
| [一级](rank-1-960.png) | 单机独立跟随与出弹 |
| [二级窄窗](rank-2-600.png) | 两架可辨识的机械机体 |
| [三级](rank-3-960.png)、[三级窄窗](rank-3-600.png) | 肩甲、青色亮环与炮口 |
| [暂停](paused-600.png) | 遮罩完整覆盖，快照 tick 不变 |
| [关闭 T](god-off-confirmation-600.png) | 明确恢复正常伤害及本局练习说明 |
| [提示消失](god-off-after-notice-600.png) | 三秒后无常驻 UNRANKED/无敌状态标签 |
| [Boss 支援](boss-support-960.png) | 双机与实际射向 Legacy 节点的弹丸 |

已目视正式版本的三级 960 图、关闭 T 的 600 图、提示到期 600 图和 Boss 支援 960 图；机体分离、提示完整、到期标签移除。测试详情和当前安装包哈希见 [验证记录](../../2026-09-11-drone-companions-and-lab-status.md)。
