# 首关正常输入样板检查（有限 headless 样本）

2026-09-11，三个固定输入种子、三个菜单武器各尝试一次。三次均走到首关 Boss；Force Push 一次击破三个依赖节点与核心，正常进入 `MISSION_COMPLETE`，Commit Cannon / Rapid CI 两次耗尽生命。未观察到异常状态、空升级选项或世界停止推进。此结果证明至少一条正常输入通关路径可达，不是武器平衡、真人可玩性或实际 IDE 性能验收。

## 运行边界与复现

- 使用此前正式 **51 suites / 295 tests 全通过**的冻结二进制 `build/art-performance/before-art/{main,resources}`。没有编译或改动正在迭代的生产源码、测试源码、正式 `build/classes`；本报告和截图不代表后续 RGBA 演员、Boss 和地面的最终视觉版本。
- Oracle JDK 17.0.12、真实 IC 2023.2.2 / 232.9921.47 SDK；独立 headless VM，`ApplicationManager.getApplication() == null`，使用内存 fallback state，静音，不写真实 IDE 持久状态。
- 开局前仅设定 GamePanel 的随机数种子，随后通过真实 EDT `ActionMap` 切换菜单武器并 `START`。所有移动、跳跃、射击、近战、冲刺、炸弹和升级选择均为真实按键 action / release。禁止 LAB、测试入口、直接伤害、奖励、改 HP、传送、跳关。
- 每次反射调用生产 `advanceSimulation()` 推进一个 16 ms 步骤，运行真实 session / world / 输入队列 / 碰撞 / Boss / 存档流程；另按 world tick 是否推进调用真实视觉更新。未按固定帧率等待，也未每 tick 绘制，运行耗时不能推断 FPS。
- 关键帧调用生产 `renderLogicalFrame` → `FrameMailbox.publish` → EDT `GamePanel.paint`，输出 960×600 PNG。每次最多 45,000 步；另设 60 秒 wall 上限和 1,000 步世界不推进检测，均未触发。
- 工具：`build/playability/PlayabilityProbe.java`、`build/playability/run.ps1`。运行：`./build/playability/run.ps1 -JavaHome 'E:\Java\jdk-17.0.12' -OutputName 'results-new'`。输出目录应使用新名字以保留已有证据。

冻结目录 199 个 class/resource 文件测后 SHA256 核对：**0 个变化**。冻结清单 `build/art-performance/before-art-input-sha256.txt` 的 SHA256 为 `8860198150f049b3b24389ce6567f973acebe32ea8b6023539c534a19cb9ec74`。

## 样本结果

| 输入种子 / 实际 run seed | 菜单武器 | 最终状态 | world tick / 模拟秒 | HP / 剩余命 | 击杀 / 选卡次数 |
| --- | --- | --- | --- | --- | --- |
| 42 / -5025562857975149833 | Commit Cannon | GAME_OVER | 32,804 / 524.864 | 0 / 0 | 244 / 8 |
| 20260911 / -8174209364415240366 | Force Push | MISSION_COMPLETE | 28,717 / 459.472 | 76 / 1 | 233 / 9 |
| 6174 / 5200007783696993394 | Rapid CI | GAME_OVER | 33,002 / 528.032 | 0 / 0 | 245 / 8 |

三次结束仍为 `ranked=true`、`debug=false`。实际调用步数分别为 32,858 / 28,764 / 33,018；菜单、升级选择和 hitstop 等不推进世界的步骤解释了与 world tick 的差值。统计中的模拟秒仅为 `worldTick × 0.016`。

三次均依次进入八段：`SURVIVE THE FIRST PUSH` → `RECOVER // REBUILD` → `PURGE THE ERROR SWARM` → `KEEP THE BUILD ALIVE` → 第二处 `RECOVER // REBUILD` → `SURVIVE THE CI LOCKDOWN` → `CLEAR 50 HOSTILES` → `LEGACY DEPENDENCY`。Boss 门段倒计时结束后 `currentMissionSegment == null`，Boss 战仍继续推进，未构成软锁。

| 样本 | Boss 进入 world tick | 最终上 / 中 / 下节点 HP（各最大 240） | 核心 HP（最大 2,400）/ 阶段 |
| --- | --- | --- | --- |
| Commit | 29,201 | 108 / 0 / 129 | 2,400 / DEPENDENCIES |
| Force | 25,804 | 0 / 0 / 0 | 0 / DEFEATED |
| Rapid | 29,360 | 152 / 0 / 150 | 2,400 / DEPENDENCIES |

Force 样本正常经过核心暴露、狂暴、击败与过关结算；没有调用 Boss 测试捷径。其散射范围能够在此按键策略下击破上下节点。

## 构筑与输入策略限制

机器人每 4 步决策，普通段持续向右、持续射击，近敌跳跃/近战/冲刺、必要时炸弹；升级优先无人机、防护和关联核心。Boss 战优先瞄准最下方尚存节点，站在其左侧约 165 像素，主要水平射击。**它不主动走下高台，不根据激光预警跳跃躲避，也不优化各枪的垂直射击策略。**

| 样本 | 最终构筑（rank） | 武器等级 / 进化 |
| --- | --- | --- |
| Commit | Drone Copilot 3、Shield Reboot 3、Commit Ricochet 1、Commit Critical 1 | 3 / 否 |
| Force | Drone Copilot 3、Shield Reboot 3、Force Extra Pellets 1、Dependency Core Force 1、Force Knockback 1 | 3 / 否 |
| Rapid | Drone Copilot 3、Shield Reboot 3、Rapid Pipeline 1、Dependency Core Rapid 1 | 2 / 否 |

Commit / Rapid 在 Boss 时多数采样停在高台 `player.y=342`，仍优先最低节点 `y=414`；直线弹主要清掉中节点，上下节点部分受损后留存。Force 的散射覆盖上下高度而成功。**这是当前 bot 策略的明显局限，不能据此判定 Commit / Rapid 失衡、上下节点不可达，或将 1/3 当作玩家胜率。** 三个样本武器和种子同时变化，也不是控制变量的武器比较。

## 死亡时点证据

首次记录保留了 HP 变化和附近敌人/普通敌弹，但未对伤害源埋点，最初只能确认所有八次失命均发生在 Boss 阶段。随后已启动的一次只读诊断重放没有改变按键策略，补记玩家 bounds、到期 Boss 预警、核心相交和激光显示计时；三个种子的完整 `trace.csv` 均与首次记录逐字节相同，结束状态、HP、节点、选卡与按键数也一致（Map 显示顺序、wall 时间、日志时刻除外）。没有进一步训练或修改 AI。

诊断的八个 HP 归零时点均满足：前一步 `telegraph_remaining=1`，待执行动作为 `Laser(laneY=338, height=46, damage=24)`，本步激光 flash 变为 10，玩家 bounds 为 `y=342,height=30`，中心 y=357 在激光带 315–361 内，且没有核心相交。它们对应正常激光命中后的 HP 归零，下一世界步正常复活或结束。

| 样本 | 激光使 HP 归零的 world tick | 下一步剩余命 |
| --- | --- | --- |
| Commit | 30,207 / 31,468 / 32,803 | 2 / 1 / 0 |
| Force | 26,835 / 28,103 | 2 / 1 |
| Rapid | 30,380 / 31,657 / 33,001 | 2 / 1 / 0 |

这解释了样本中的真实失命路径：bot 留在激光高度，未利用预警移动/跳跃。没有证据表明这些失命来自异常扣血、复活失败或无法逃出的状态。此只读诊断不是通用伤害来源追踪器；普通段的其它 HP 损失仍仅保留观察，不做过度归因。

## 证据与未验收项

- 首次三次：`build/playability/results/seed-{42,20260911,6174}/{summary.txt,events.txt,trace.csv,*.png}`；额外诊断：`build/playability/diagnostic-results/`。完整选卡候选和每种 action 次数在对应 summary 中。
- 正常 Force 成功路径的版本化画面：[首次正常升级](images/2026-09-11-normal-input/01-force-upgrade.png)、[Boss 进入](images/2026-09-11-normal-input/02-force-boss-entry.png)、[首关正常完成](images/2026-09-11-normal-input/03-force-mission-complete.png)。已打开核对最后一张，画面显示 `MISSION COMPLETE / Level 1 cleared`。
- 全部工具、输出与留存截图清单：`build/playability/evidence-sha256.txt`，SHA256 `26b36a7dbda2504b7dcfd0c04ed3ebbf0758ab7303043d9e1bcbe32b49a2a669`。成功截图 SHA256 `2749123bcd5cdc0fa537e00fee186bf12c25e77a755816f04d10d649781c5b84`。

未验收：实际 IDE 输入焦点/帧率/声音/真人操作感；另外三把武器；其它构筑、进化或组合的正常输入可达性；完整多关长局；新 RGBA 素材后的绘制效果和性能；一切广义平衡结论。此 probe 与此前 LAB 性能 probe 分开，不能相互替代。后续源码稳定后再做正式 Gradle 与 after-art 性能复验。
