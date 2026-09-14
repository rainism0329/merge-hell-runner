# 无人机升级说明与接入审查

本记录仅覆盖无人机升级卡和一次只读接入审查。生产控制器、弹丸、演员绘制及 GamePanel 集成由其他并行任务完成；正式 Gradle 全套结果由根任务另行记录。

## 升级卡

`UpgradeCatalog.droneDescriptionLines(nextRank)` 提供三行分阶说明，`UpgradeOverlayRenderer` 从 `DroneController.profile(rank)` 直接读取数量、间隔、弹伤、贯穿和范围。间隔由现有 16 ms 模拟步长换算，显示到小数点后两位；界面读取当前构筑的武器伤害，不把武器初始伤害写死。

| 下一阶 | 行为说明 | 实际变化（初始 Commit Cannon 为例） |
| --- | --- | --- |
| L1 | 部署一机，独立索敌与开火，优先标记目标 | 0 → 1 机；每机每 1.15 秒一发，10 点直接伤害；560 px 索敌范围 |
| L2 | 部署第二机，分配不同目标；只剩一个弱敌时第二机待命 | 1 → 2 机；每机间隔和单发伤害不变 |
| L3 | 保持两机，强化射速、伤害和贯穿 | 每机 1.15 → 0.77 秒；10 → 14 点；贯穿 1 名普通敌人 |

贯穿范围按根任务确认，沿用现有友弹碰撞语义：普通敌人允许一次穿透，Legacy 节点、核心及旧 Boss 命中会消耗弹丸。卡片明确写 `normal enemy`，没有承诺穿透 Boss。描述中的双机分配也明确保留弱单敌待命条件。

验证发现 `ceil(100 * .55)` 因浮点表示可误得 56。控制器任务已修复为整百分比的 long 向上取整，100 点来源伤害的 L3 弹伤现在为 55；卡片直接读取同一算法。

## 接入审查结论

检查时 GamePanel 的 `startGame`、`advanceLevel`、`continueRun`、复活与 LAB 位移入口已重置无人机状态及目标 ID 表。暂停、升级选择、hitstop 和关末等待在无人机更新前返回，保留冻结快照；`dispose` 与模拟回调共用面板锁，排队旧回调由 disposed guard 阻止。此记录是只读审查，生命周期行为由根任务的 GamePanel 集成测试验收。

普通目标排除死亡、非敌对及受生成保护者。Legacy 依赖阶段只提交活节点，核心开放后提交核心；60 tick 阶段过渡只停止 Boss 出招，核心伤害仍有效。旧 Boss 需已激活、HP > 0、死亡演出尚未开始，避免在预警或死亡演出中继续锁定。通过真实碰撞命中 Boss，不用控制器直接扣血。

## 验证与证据

- 独立入口：`build/drone-ui-validation/run.ps1`，JDK 17.0.12 + 真实 IC 232.9921.47 SDK；所有当前生产与测试源码独立 javac 成功，没有写正式 `build/classes`。
- 定向 JUnit：14 / 14 通过，5 个容器，0 跳过、0 中止、0 失败，耗时 693 ms。包含新增卡片/描述 6 项及既有抽卡/耗尽补给 8 项。
- 新测试覆盖三阶区别、预览无构筑副作用、武器伤害升级传播、精确弹伤、合法等级、六武器满构筑行宽、三阶真实抽卡绘制和 Graphics2D 状态隔离。所有测试使用普通 `@Test`，没有 junit-params 依赖。
- 日志：`build/drone-ui-validation/console.log`；XML：`build/drone-ui-validation/reports/`。
- 四份拥有的生产/测试文件 hash 清单：`build/drone-ui-validation/source-sha256.txt`，清单 SHA-256 `1dcc40cbc58ada1b5cceaa2c85bfc9e8391dc922e5ff6ca620d40e4dcd0df547`。
- 对拥有文件执行 `git diff --check` 退出 0。仅使用进程范围 Git 配置，没有改全局配置。

显式抽卡 fixture 由生产 `UpgradeOverlayRenderer` 绘制，三阶各保存 960 × 600 和 600 × 400 预览：`docs/qa/images/2026-09-11-drone-upgrades/`。预览工具 `build/drone-ui-validation/DroneCardPreview.java` 通过真实抽卡找到无人机卡，构筑等级为测试前置条件；这些图片不代表正常游玩升级过程或完整 IDE 窗口。

已目视 L3 的 960 图和 L2 的 600 图，新增描述、效果、等级行无重叠或截断。600 预览沿用当前整幅缩放布局，正文物理字号仍偏小，本记录不将它表述为所有窄窗可读性问题已解决。
