# 跑射弹道与相对射程修复

2026-09-11。用户反馈“往前跑时子弹发射距离显得很短”。本次先冻结发射/碰撞相关 class，再用同一个真实 Player 更新探针生成修改前后的数据。

## 原因和量化

生产仍保留 16ms 一步。玩家跑速 5px/步；旧发射器直接把武器弹速当作世界弹速，前进时玩家会追上自己的慢弹。以下是中轴弹丸；散射外侧的横向分量使用原来的 cosine，逐颗也按相同规则验证。

| 武器 | 站射弹速 | 旧跑射相对速度 | 新跑射世界速度 | 新跑射相对速度 | 寿命/步 | 旧→新名义跑射相对射程 |
|---|---:|---:|---:|---:|---:|---:|
| Commit Cannon | 11 | 6 | 16 | 11 | 180 | 1080→1980px |
| Force Push | 9 | 4 | 14 | 9 | 65 | 260→585px |
| Rapid CI | 14 | 9 | 19 | 14 | 240 | 2160→3360px |
| Garbage Collector | 7 | 2 | 12 | 7 | 150 | 300→1050px |
| Firewall | 7 | 2 | 12 | 7 | 28 | 56→196px |
| Refactor Beam | 22 | 17 | 27 | 22 | 240 | 4080→5280px |

向左的世界速度符号取反，相对速度大小一致。现有唯一 MOVEMENT 卡 Dash Cache 只缩短冲刺冷却，并不增加跑速；升满三阶也执行了探针。原有冲刺为 8 步、10px/步，期间不发射。结束后普通武器最早在第 9 步发射，Beam 还需 10 步蓄能、在第 18 步发射；新子弹只继承实际发射那一步的跑速。

名义射程是相对速度乘寿命，并非承诺跨屏伤害距离。CollisionSystem 仍在镜头左右各 100px 之外回收弹丸，寿命末步仍按旧顺序移除；没有延长寿命或扩大命中框。原镜头把角色放在屏幕宽度约 30% 处，向左前方约 288px、向右约 672px，左射较早离开画面属于取景差异。

## 实现

- FireRequest 增加发射瞬间的 shooterVelocityX；两个旧构造入口默认 0。WeaponFireController 将它只加到横向世界速度，原散射纵向分量、伤害、暴击随机流、寿命均保留。Projectile 不持有 Player 引用，之后停下、转向或跳跃不会改变旧弹丸速度。
- Player 在水平移动和边界裁剪后计算实际位移。新 update 重载接受允许的玩家左上角 minX/maxX，与世界 0..levelWidth-30 相交；入口/复活式坐标纠正在计速前完成，不能成为发射动量。冲刺也使用该范围。原 update 入口继续兼容。
- 根任务已将 GamePanel 可见区边界传入新重载，删除发射后再推回玩家的逻辑。锁屏顶边持续跑射时继承量为 0。
- 新增 Projectile.hits(Rectangle) 固定步扫掠与 hitFraction(Rectangle) 接触顺序。真实进化 Beam 外侧弹在跑射时约为 26.97/1.10px 每步，已复现跨过 36×48 IRQ 角落、末点不重叠的漏判。普通敌人按最近接触顺序处理，既有穿透/奖励上限保留；折射后不复用入射路径。旧 Boss 与根任务持有的 Legacy 节点/核心都接入同一个 hits 接口。背景和平台未作为弹道障碍。
- 敌弹的生成速度、运动与玩家命中逻辑未修改。Player.Checkpoint/Schema 2 数据结构未改变。

## 可复查证据

源探针：`src/test/java/com/bigphil/mergehell/combat/RunFirePreview.java`。

- `build/runfire-validation/before/measurements.csv` 与 `after/measurements.csv`：每份 48 条六武器、左右站射/跑射、移动卡与冲刺后开火的实际更新记录；同时记录世界/相对速度、寿命、出画面与回收步数。
- `build/runfire-validation/{before,after}/age-{01,08,16,24}.png`：真实 Player/Projectile 更新后的 18 情景对照。图片采用跟随人物的相对构图，以便直接比较弹丸离枪口的距离；CSV 的裁剪数据则使用真实 CollisionSystem 和 960px、0.15 平滑系数的镜头计算。这些是 headless 探针，不冒充原生 UI 录屏。
- `build/runfire-validation/index.html`：修改前后预览入口。
- `targeted-tests.log`：29 项新增专项通过；`regression-tests.log`：包含专项在内的 166 项战斗、Player、Projectile、构筑组合、武器效果、掉落回放测试全部通过，无跳过或失败。

使用独立 before-classes/after-classes 和 JDK 17/JUnit Console 编译验证，未运行正式 Gradle，未修改正式 build/classes。原生 IDE 手感与完整正式构建由根任务另行验收。
