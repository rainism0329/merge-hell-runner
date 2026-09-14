# 全面提升首批实施记录

> 本文保留第一批 197 项测试时的历史状态。当前增量、正式 JDK 17/IDE 232 构建恢复、六武器、音频、续玩与设置进展见 [当前集成记录](2026-09-10-integrated-upgrade.md)。下方“Gradle 受阻”“没有新安装包”“同步绘制”等描述只适用于该历史批次。

日期：2026-09-10  
范围：M0 视觉目标与基线，M1 的运行和素材基础。完整提升尚未交付；本记录不代表 M1 或可玩美术样板验收完成。

## 已完成改动

- 用户最新确认全游戏以方案 1 为主：旧工业机械、磨损金属、黄橙装甲与暖色主光。方案 3 仅供个别关卡借鉴雨夜氛围，首批素材与可玩样板以方案 1 验收。四张概念图与统一性检查保存在 `docs/design/evolution/`；它们不是已实现截图。第 4 张为此前组合方向的探索记录。
- `GameLoop` 接入单调时钟与固定步长累积器，最多追赶五次，暂停和恢复重置累计时间。引擎默认 60Hz；`GamePanel` 显式保留旧 16ms 步长，在整体数值迁移前保持原运动与冷却参数对应的节奏。
- Swing 键盘和鼠标事件改为提交命令，由模拟边界消费。按键边沿去重，避免系统自动重复触发二段跳或暂停；失焦取消待处理输入并暂停，暂停状态不消耗炸弹或启动战斗动作。
- 震屏和闪光的计时移入更新，重复绘制不再缩短效果。
- 新增 `AssetCatalog`、`AssetStore`、`SpriteDefinition`、`AnimationClip`、`Animator`、`VisualPose`、`SpriteRenderer`。支持 UTF-8 properties、PNG 图集预加载与复用、资源错误诊断、帧边界校验、脚底锚点和枪口挂点、循环和单次动画、镜像与透明度。
- 图集使用预乘透明通道，不对外暴露可变像素；绘制只引用缓存与源矩形。外观姿态不包含实体引用，外观边界不替换碰撞框。缺失素材返回回退信号，仍需游戏接入者实现回退策略。

新素材组件目前独立存在，尚未接入正式实体渲染。游戏内还没有本轮概念角色和动画。

## 验证

冻结原始代码的 160 项测试全部通过；环境、命令、时间基线和真实截图见 `2026-09-10-baseline.md`。

本轮运行基础初次定向验证 20 项通过，素材基础初次定向验证 17 项通过。首次合并后，对当前全部 80 个生产 Java 文件和 32 个测试文件重新编译，全套 193 项通过，无失败、跳过或中止。

交叉审查后的两项修复：

- 精灵透明度改用 `AlphaComposite.derive`，保留父层的合成规则；回归测试逐像素验证 `SRC` 和 `DST_IN`。精灵渲染测试 5 项通过。
- 输入缓冲增加取消代次和独立失焦命令，取消消费批次中剩余的旧输入，并在本次 `drain()` 返回前应用清键和暂停。已经获执行资格或正在执行的回调允许完成；普通新输入留到下个模拟边界。包含 latch 跨线程验证的输入缓冲测试 7 项通过。

上述修复后再次完整编译 80 个生产源码和 32 个测试源码，最终 **197 / 197 项测试通过**，0 失败、0 跳过、0 中止，耗时 9.502 秒。报告时间为 `2026-09-10T22:30:35`。编译前后全部 `src` 文件哈希一致，源码清单 SHA-256 为 `9d7d6921725202944f3a7e8e5374022b6e31b87d61dac8c0c2aa6eab669725b6`。最终 `git diff --check` 无空白错误。

集成验证使用本地 IDEA 2025.1.3 的 JBR 21、`javac --release 17`、真实 IDE 库和 JUnit Console Standalone 1.10.0。禁用宿主自动发现的 IDE 测试扩展，避免无关的 `ThreadLeakTracker` 初始化。该路径验证了重新编译的项目代码与测试，但不能代替目标 IDE 232 的兼容性验证。

最终集成证据目录：`build/evolution-validation/`，包含 `javac-main.log`、`javac-test.log`、`junit.log` 与 `reports/TEST-junit-jupiter.xml`。辅助文件均位于忽略的 `build/` 中。

复现命令在仓库根目录执行，参数文件由全量源码列表生成：

```powershell
$validationJbr = 'E:\JetBrains\IntelliJ IDEA 2025.1.3\jbr\bin'
$validationIdeLib = 'E:\JetBrains\IntelliJ IDEA 2025.1.3\lib\*'
$validationJunit = 'build/baseline-20260910/junit-platform-console-standalone-1.10.0.jar'
& "$validationJbr\javac.exe" --release 17 -encoding UTF-8 -cp $validationIdeLib -d build/evolution-validation/main '@build/evolution-validation/main-sources.args'
& "$validationJbr\javac.exe" --release 17 -encoding UTF-8 -cp "build/evolution-validation/main;$validationJunit;$validationIdeLib" -d build/evolution-validation/test '@build/evolution-validation/test-sources.args'
& "$validationJbr\java.exe" '-Djava.awt.headless=true' -cp "$validationJunit;build/evolution-validation/main;build/evolution-validation/test;src/main/resources;$validationIdeLib" org.junit.platform.console.ConsoleLauncher execute --scan-class-path=build/evolution-validation/test --exclude-engine=junit-vintage --config=junit.jupiter.extensions.autodetection.enabled=false --reports-dir=build/evolution-validation/reports --details=summary --disable-ansi-colors
```

## 限制与接续顺序

- Gradle 在配置阶段因 `gradle-intellij-plugin:1.17.4` 等旧依赖无缓存而失败，`test`、`buildPlugin`、`verifyPlugin` 三个 Gradle 任务未执行。没有本轮新安装包，历史 ZIP 不算本次交付。
- 用户按 Esc 停止 Computer Use 后，本轮停止桌面操作。已保留原始首页、普通战斗和游戏结束三张真实预览；正式 IDE 集成试玩及改动后的桌面验收尚未完成。
- `GamePanel` 仍有共享状态与同步绘制，尚未实现深不可变 `RenderSnapshot` 或完整会话边界；输入缓冲不能视作整体线程架构已迁移完毕。
- 真正 60Hz 的全量物理、冷却、预警和任务计时迁移仍待完成，不能仅替换步长就验收。原始 62.5Hz 的运动与六武器间隔已记录供比较。
- 后续按计划补齐 M1 的绘制快照、时间域和存档契约，再接入主角、Bug、TechDebt、Repository City、Legacy Boss 和两把枪的完整可玩样板。样板通过后扩展全部五世界、六武器构筑、续玩与菜单，不以样板替代完整范围。

任务清单以 `docs/plans/2026-09-10-game-evolution-plan.md` 为准。复合任务中的未完成部分仍保留未勾选状态。
