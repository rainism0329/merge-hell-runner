# 五关结算与关末安全续玩修复

2026-09-11。只读审查发现：后四关 Boss 完成分支没有调用首通奖励与 `session.markComplete()`；首关使用的旧 `clearActiveRun()` 在窗口持有存档所有权时不生效；五关均依赖 240 步自动过场，关末关窗会留下上一关入口，第五关还可能来不及记榜。

## 已实现

- 两种 Boss 的完成分支统一调用 `GamePanel.completeCurrentMission()`。每关首次完成发放 250 refactor points；五关合计 1,250，重复通关不重复发首通点。当前关内结算有幂等 guard，重复触发不会重复增加关末 bonus 或最终榜单。
- 原有过关分数奖励移到关末出现时计算，最终关采用同一规则。结算不等待下一关加载；加分及下个里程碑阈值的累加做整型上限保护。
- 关末保留当前关卡、角色和 Boss 的画面，世界不再自动推进。Enter 进入下一关；Q 安全回菜单。第五关当场写最终分数并按所有者身份清档、释放所有权，Enter 仅进入总胜利界面，不重复记榜。
- 结算时立即构造下一关入口的独立快照，保存分数、HP、命数、炸弹、弹药、构筑、成长、冷却、buff 和随机流。直接关窗或 Q 后，菜单 R 恢复下一关入口。按 Enter 与通过 R 续玩得到相同的入口资源与 session 快照。
- `MergeHellStateService.settleCampaignMission()` 在同一同步操作中发布奖励和下一入口，最终关则发布奖励、最终分数及所有权清理。第二个窗口可以本地结算并继续游戏，但不能覆盖、删除或释放第一个窗口的存档。
- 不改 Schema 2 的数据格式与入口契约；`GameSession.checkpointForNextMission()`、`CheckpointCodec.captureNextMission()`、`Player.checkpointForNextLevel()` 提供完成边界的只读捕获。正在展示的完成场景不会被快照构造移动或重置。
- 进入下一关统一清掉瞬时反馈、连击、动作、朝向和射击视觉计数；资源与已消耗的构筑次数保留。当前关末同 tick 停止继续收币/碰撞，避免完成画面被最后一帧升级选择覆盖。
- 修正 LAB 激活时的旧无所有者清档调用，改用该窗口自己的 owner；LAB 不领取首通奖励、不留可续玩的受污染快照。关末音频暂停也加入实际帧回调的最终状态判断。

## 验证

独立 `build/settlement-validation/run.ps1` 使用 Oracle JDK 17.0.12 和真实 IC 2023.2.2 / 232.9921.47 SDK，将当前全部生产及测试源码编译到自己的 `main/`、`test/` 目录。没有写正式 `build/classes`，没有启动 GUI，也没有在视觉仍迭代时启动正式 Gradle。

JUnit Console 1.10.0：**73 tests / 10 containers，73 successful，0 failed / skipped / aborted**，测试耗时 16.245 秒。包含：

- 新增 `GamePanelSettlementTest`：五关分别进入真实 Boss 死亡完成分支；完整连续五关、重复跑完五关；关末等待 600 步；重复完成幂等；Enter 与关闭/R 恢复入口等价；Q 回菜单续玩；第五关关闭前即时记榜；非 owner 窗口中途与最终结算保护；LAB 清档/无奖励；最后一帧收币不能覆盖关末。
- `MergeHellStateServiceTest` 新增无效下一入口不能部分发布奖励或覆盖原存档的用例。
- 既有 `GamePanelEvolutionTest`、`GameSessionTest`、`PlayerTest` 及全部 persistence 测试，包括真实 IntelliJ XML 序列化兼容与迁移。

执行：`./build/settlement-validation/run.ps1`。日志 `build/settlement-validation/console.log`，JUnit XML `build/settlement-validation/reports/TEST-junit-jupiter.xml`。这些是定向验证；当前视觉源码由其他并行任务继续收敛，不把此结果当作最终源码冻结或正式插件验收。

## 后续边界

关末提示由视觉负责人接入 Enter / Q 与 bonus / first-clear 字段。关末逻辑已替代自动过场，之前的 240 tick 行为不再作为玩家退出机会。旧入口 Schema 2 继续兼容，没有承诺战斗中任意帧保存。真实 IDE 重开后的磁盘持久化、真人操作和五关完整体验仍需单独验收；本次是可重复的集成 fixture 测试，与只走正常输入的首关 bot 证据分开。
