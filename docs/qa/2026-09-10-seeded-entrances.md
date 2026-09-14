# 关卡起点的确定性重建

日期：2026-09-10。本轮修复 `ObstacleManager`、`LevelManager`、`Boss` 的关卡入口随机性，目标是相同入口 seed 与相同输入能重现相同序列；不提供任意帧存档，也未调整 16 ms 模拟步长或运动 / 冷却数值。

## 接入 API

```java
ObstacleManager enemies = new ObstacleManager(enemyLevelSeed);
enemies.reset(enemyLevelSeed); // 换关或从入口恢复：清空实体 / 弹幕并重置随机流
LevelManager level = new LevelManager(levelIndex, pickupLevelSeed);
Boss boss = new Boss(name, hpPool, symbol, spawnWorldX, levelIndex, bossLevelSeed);
```

旧构造器继续可用；无 seed 时仍会生成新的随机入口。原 `ObstacleManager.reset()` 保留清空实体和弹幕的行为，不重置随机流；要重建相同入口必须使用 `reset(long seed)`。后者同时重置拒绝生成计数。

关卡切换、新局、续玩需要由调用方从持久化 run seed 与 level index 稳定派生 seed，使用相同派生规则。建议敌人、拾取、Boss 各用独立域；本轮未修改 `GamePanel`，其统一接入由主任务完成。

直接构造敌人也支持 `Enemy(x,y,type,long seed)` 和 `Enemy(x,y,type,int moveDir,long seed)`。

## 随机流与时间

`ObstacleManager` 有独立的生成 Random 和敌人 seed Random；后者由入口 seed 与固定域值派生。每个 Enemy 再创建自己的 Random，射击 / 冲锋消耗仅影响自身。独立敌人和不同窗口不再共享 static Random。

飞行相位由敌人 seed 的独立域派生，避免相位初始化消耗射击随机流。运动年龄仅在 `Enemy.update` 增加一个 tick，以 `ageTicks × 0.016` 驱动原频率 / 幅度公式；绘制读取该时间，不修改它。已移除 Enemy 内全部 `System.currentTimeMillis()`。因此等待、暂停、绘制频率和恢复发生的墙钟时刻不再改变飞行位置。

这修复了原先由绝对墙钟决定相位的行为；不声称新轨迹能复现旧版某个没有 seed 的随机现场。

## 验证

用 Oracle JDK 17.0.12 / `javac --release 17` 独立重新编译 3 个修改的生产文件和 4 个测试类，使用项目已缓存的 IntelliJ 232 API / JUnit Platform Console 1.10.0。运行目录为 `build/seed-validation/`，未启动 Gradle 或 GUI。

**25 项通过，0 失败、0 跳过，798 ms**：

| 测试 | 覆盖 |
| --- | --- |
| `SeededEncounterTest`，6 项新增 | 全 EntityType 的同 seed 400 步运动 / 射击；插入其他实例活动仍一致；墙钟等待和绘制不推进运动；管理器 320 步生成序列；入口 reset 的 200 步精确回放；不同 seed 区分；四类 Boss 各 900 步，含阶段变化、召唤及弹幕 |
| `LevelSeedTest`，2 项新增 | 五关入口拾取重建、已收集状态不污染新实例；不同 seed 改变拾取而保持地形 |
| `BossTest`，10 项既有 | Boss 生命、状态、四类机制 |
| `LevelManagerTest`，7 项既有 | 关卡流程、遭遇、Boss 门与生态 |

证据：`build/seed-validation/junit.log`、`build/seed-validation/reports/TEST-junit-jupiter.xml`、`build/seed-validation/source-sha256.txt`。

定向运行明确只启用 Jupiter，关闭 IntelliJ 扩展自动发现；SDK 携带的旧 JUnit 3 不应被 Vintage 当作本项目测试。当前其他游戏源码仍并行编辑，完整编译、正式 JUnit 与插件打包需要主任务接入并稳定后重新执行 `scripts/verify.ps1`。
