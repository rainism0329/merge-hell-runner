# 2026-09-21：章节深化与武器表现

本批在上一版成熟度样板上，扩展第二、四、五关遭遇，深化第四、五关机关和第四关 Boss，并补全六种手持武器的表现。第一关的任务导演保持原有预算和压力机制，第三关保留上一批样板。本批不包含角色主动技能、互斥构筑分支或挑战模式。

**完成的玩家体验**

- 第二、四、五关共 9 场战斗、18 波改为分批混合编组。第二关以地面冲锋/重装与中空泄漏、锁定射手配合；第四关以钻地、空中俯冲和熔渣抛射配合；第五关以孢子、跃袭和树脂射手配合。出场位置避开玩家与实体墙面，满额或位置受阻时等待。
- 战场脚本以三名活敌为入场预算；第五关孵化也使用同一预算。巢穴满额时保留成熟请求和剩余次数，同次更新的多个请求逐个占用名额；自由路段保留六名上限。已有路段敌人占用预算，不会被强行删除。
- 第四关货运控制切换后段输送方向。可选排气控制关闭后段喷口、冷却末端热料槽；不操作时可经平台跳过。输送箭头、控制线路、压力表和排气反馈对应真实状态。
- 第五关主节点停止前方孵化，可选神经节点用 120 步收起末端膜障。射击、移动和画面使用同一收缩几何；完全开放后不再把残膜发布为可攻击目标。旧设施 HP、孵化次数与已领取奖励保持不变。
- 第四关 Boss 增加冲锋后的短热轨迹与第三阶段后散热口泄压。热轨迹先预警再生效；泄压有固定方向预警，可蹲避、跳过或打断。破坏驱动装甲会中断冲锋，缩短和减慢后续冲锋并停止新热轨迹；过热清除已有热区并开放核心。不增加血量、不强制锁血。
- 六把武器保留原绘制握点与枪身材质，新增独立前端结构和进化部件。四角色、菜单、临时拾取、弹药耗尽回核心、超频强制散射均显示相应装备。枪口与实际发射位置保持一致；本批未改武器伤害或进化数值。
- 修复同侧靠墙误判：敌人曾因把自身宽度扩展后的接近边界当作墙体边界，错误绕到墙后。现在按玩家与实际墙体的相对位置决定是否绕行；保留上一批移动障碍重规划。

**最终验收**

| 项目 | 结果 |
|---|---|
| 完整 Gradle 测试 | **839 / 839 通过**，无失败、错误或跳过 |
| 插件构建与包校验 | `buildPlugin`、`verifyPlugin` 通过 |
| 生产画面文字检查 | 72 个新增场景 + 68 个既有场景通过，覆盖英文/简体中文及 960×600 / 600×400 |
| 包内容核对 | 397 个生产 class 与 instrumented 构建输出一致，40 个 game 资源与源码资源一致 |
| 存档集成 | E 交互、安全节点保存、菜单继续、状态恢复及不重复奖励通过；用上一预览编译结果中的 5/4/10 个设施资源布局验证后三关兼容性 |
| 机关通行 | 四角色均可到达必要与可选控制台，热槽直接跳跃和冷却路线、膜障直接越过和神经开路均验证 |
| 差异检查 | `git diff --check` 通过 |

完整构建日志：`build/campaign-depth-20260921/gradle.log`。测试结果：`build/test-results/test/`。构建耗时 4 分 6 秒，不是游戏帧率或启动耗时。

可复现构建，需 JDK 17 与已有离线依赖缓存：

```powershell
./gradlew.bat test buildPlugin verifyPlugin --offline --no-daemon --console=plain '-PpluginVersion=2.0.0-campaign-depth-preview'
python scripts/verify-languages.py build/campaign-depth-20260921/final-visual/displayed-text.txt --profile depth
python scripts/verify-languages.py build/campaign-depth-20260921/final-language/displayed-text.txt
```

**完整路线诊断与边界**

以下四次路线诊断使用最终编译生产类、原种子及未经修改的 `ChapterPlaythroughProbe`，统一 12,000 步上限、`bombOnStall=false`。base 为普通初始构筑；developed 预先应用八项合法升级。进入后采用正常输入，不开无敌、不注入生命、不直接清怪或修改位置。

| 场景 | 配置 | 结果 | 步数 | 剩余 HP / 生命 |
|---|---|---|---:|---|
| 第四关路线 | base | 达到步数上限，停于 x2916 的钻地敌人附近 | 12000 | 73 / 3 |
| 第四关路线 | developed | 达到步数上限，停于 x8840 的钻地敌人附近 | 12000 | 14 / 3 |
| 第五关路线 | base | 到达 Boss | 6279 | 22 / 2 |
| 第五关路线 | developed | 到达 Boss | 5329 | 10 / 3 |
| 第四关 Boss | base | 击败 Boss，进入新泄压阶段 | 4196 | 64 / 2 |
| 第四关 Boss | developed | 击败 Boss，进入新泄压阶段 | 3369 | 100 / 2 |

第四关两次路线诊断**没有通关**。原自动玩家在高台上遇到横向接近的低位目标会反复原地跳跃、垂直射击，弹道与敌人错开。另有一次真实导航误判已修复，但这不解决自动玩家的决策局限。

新增独立受控输入测试复现两处相对位置，使用满 203 HP 钻地敌人；初始位置是夹具，之后只用普通移动、C 射击和 V 定点瞄准。走下高台并沿地面转向射击，分别在 451 / 420 步击杀，玩家均剩 76 HP。后段相同输入在导航修复前会把敌人错误引到墙后，修复后可正常处理。这些结果证明局部存在有效解法，不能替代第四关完整真人通关。

另有开发期 `14000 / bombOnStall=true` 的第四关 developed 诊断在 12911 步到达 Boss；参数与上表不同，不作为同条件通过结果。旧失败轨迹保留在 `build/campaign-encounters-20260921/`，扩展诊断在 `build/chapter-mechanisms-20260921/`。

Boss 与路线分别运行，不是一次连续战役。第二关新增遭遇完成了真实敌人工厂、生成几何、延迟结算和流程测试，本批没有第二关完整输入通关记录。

**可查看的生产画面与数据**

以下截图来自真实绘制路径，但使用章节、位置、练习或阶段夹具；用于检查布局和状态，不是原生 IDE 真人游玩截图。

- [第四关热料通道](data/2026-09-21-campaign-depth/zh-CN-foundry-hot-600.png) / [卸压冷却后](data/2026-09-21-campaign-depth/zh-CN-foundry-cooled-600.png)
- [第五关孵化静默](data/2026-09-21-campaign-depth/zh-CN-hatchery-silenced-600.png)
- [膜障收缩过程](data/2026-09-21-campaign-depth/zh-CN-membrane-moving-600.png) / [开放通路](data/2026-09-21-campaign-depth/zh-CN-membrane-open-600.png)
- [四角色的六种进化武器](data/2026-09-21-campaign-depth/evolved-weapons.png) / [菜单中的武器预览](data/2026-09-21-campaign-depth/zh-CN-weapon-refactor_beam-600.png)
- [第四关 Boss 阶段对照](data/2026-09-21-campaign-depth/foundry-boss-phases.png)
- [结构化包验收](data/2026-09-21-campaign-depth/artifacts.json) / [遭遇与导航专项记录](data/2026-09-21-campaign-depth/encounter-navigation-tests.txt)

同目录保存六份路线/Boss 摘要 CSV 及两份完整双语显示文本。完整画面在 `build/campaign-depth-20260921/final-visual/` 与 `final-language/`，武器对照在 `final-weapons/`。

**预览包**

- [merge-hell-runner-2.0.0-campaign-depth-preview.zip](../../build/distributions/merge-hell-runner-2.0.0-campaign-depth-preview.zip)，35,801,478 bytes。
- SHA-256：`092885b3f08f8268acd7c113dbc7034f9c3fe9ea2087b9ae109ca1fb9e14980e`。
- 正式配置仍为 2.0.0，没有提交、推送或对外发布。

本批尚未完成原生 IDE 真人实玩、30 分钟性能曲线及多 IDE 版本验证；`verifyPlugin` 是包校验，不等于完成跨版本 Plugin Verifier 和安装冒烟。第四关完整路线仍应重点人工验收。后续角色主动能力、互斥武器构筑和短局挑战另行推进。
