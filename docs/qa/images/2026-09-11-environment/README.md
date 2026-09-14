# 环境与首领入场实际画面

2026-09-11，从本批最终构建的生产类导出。原始预览包含两种语言、960×600 和 600×400，共 40 张新环境画面；另有 68 张菜单、战斗、升级、设置及结算回归画面。本目录保留其中十张新画面，未做图片编辑。

捕获运行实际 GamePanel 输入、模拟、帧发布和 EDT 绘制。测试夹具选择关卡、起始位置、生命与炸弹数，并清理敌人以突出场景；没有注入水花、射击命中、奖励或 UI 文本。环境步行、冲刺、跳跃落地、开箱及首领倒计时均由实际更新产生。两个窗口尺寸的捕获各前进一步以应用视口，逐图数据记录该差异。

- [逐图状态与按键](state-evidence.csv)
- [108 张画面最终显示文字](displayed-text.txt)
- [十张原始 PNG 的 SHA-256 清单](capture-manifest.json)
- [完整实施与验证记录](../../2026-09-11-environment-and-boss-arrival.md)

这些是 headless 游戏面板证据，没有替代原生 IDE 安装、实际音频设备或真人战役试玩验收。

- [浅水步行、工业背景与独立炸弹栏](zh-CN-water-walk-960.png)
- [紧凑窗口中的冲刺水花](zh-CN-water-dash-600.png)
- [真实跳跃后落水的反馈](zh-CN-water-landing-600.png)
- [经过低台阶跳上高架平台](zh-CN-heap-upper-route-960.png)
- [可射击的电容罐及提示](zh-CN-capacitor-before-600.png)
- [电容罐放电反馈](zh-CN-capacitor-discharge-600.png)
- [射击补给箱前的炸弹与生命](en-supplies-before-600.png)
- [射击后实际补给奖励](en-supplies-after-600.png)
- [中文紧凑窗口的安全入场倒计时](zh-CN-boss-arrival-600.png)
- [英文安全入场倒计时与炸弹数量](en-boss-arrival-960.png)
