# 2026-09-21：进度保护与第三关制作样板

本批落实成熟度评估中的第一批工作：进出游戏流程、第三关混合遭遇与真实机关、第三/第五关 Boss 阶段深化。正式版本配置仍为 2.0.0；提供独立的 `2.0.0-maturity-preview` 安装包，没有对外发布。

**玩家能直接感受到的变化**

1. 有可用存档时，主菜单默认继续战役。N 新开或替换已有进度先确认，Enter 确认、Esc 取消；取消不会推进战斗或清掉存档。
2. T 开启练习保留原正式检查点，关闭无敌仍不恢复该练习局的排名资格。菜单继续可回到正式存档。未解锁工程师不能借失败的新局切换获得正式奖励。
3. 失败页有原配置重试、返回菜单、本关 Boss 练习。重试从新战役开始，不返还已耗尽生命；这次没有新增“关卡无限重试”模式。
4. 暂停页显示真正的保存节点，按钮可以点击；设置支持鼠标滑条、开关、语言和返回。暂停静止时不再持续发布完整画面，输入与缩放仍能刷新，恢复后没有补跑积压时间。
5. 第三关两场锁屏战斗使用分批混合编组：重盾、狙击兵、缆索单位分担职责，编排同时最多三名活敌。位置被占、超预算或生成被拒绝时等待，不吞掉增援，也不会提前结算。
6. 第三关绞盘真正升起闸门；上层桥锁可以展开安全支路，下层直接双跳过沟。桥锁标注可选，绞盘仍是必要节点。几何、射击遮挡、敌人导航及存档恢复一致；闸门遇玩家或敌人会暂停，避免挤压。
7. 天空构架师增加横扫和吊架封锁；断臂立即取消对应侧攻击。异星终局改为爬行、心核脉冲、地根突刺，分别要求换位、蹲避或跳跃。阶段重构期暂缓弱点增伤并显示核心闭合，继续接受原规则下的普通伤害；不增加血量，也不强迫高输出玩家看完全部招式。

同时修正了核心增伤固定显示“+50%”的错误、2.2 倍伤害浮点取整误差，以及跨窗口存档归属、无存档练习和错误恢复说明。

**最终验证**

| 检查 | 结果 |
|---|---|
| Gradle 完整测试 | **788 / 788 通过**，0 失败、0 错误、0 跳过 |
| buildPlugin / verifyPlugin | 均通过 |
| 新流程交叉边界 | 真实 ActionMap / MouseEvent 覆盖确认、取消、恢复、死亡、练习、跨窗口、缩放与暂停缓存 |
| 四角色通行 | 真实 Player 移动和跳跃到达控制台，并能走未开桥主路或上层安全桥 |
| Boss 安全性 | 预警期间无伤且不追踪；最慢角色能到封锁安全区；实际蹲姿、单跳可避对应攻击；断臂取消及整组弹幕预算通过 |
| 双语画面文字 | 68 个既有流程场景 + 40 个新场景，全部通过，均覆盖 960×600 / 600×400 |
| 包内容一致性 | 包内 392 个生产 class 与构建输出一致；40 个 game 资源与当前源码资源一致 |
| 差异格式 | git diff --check 通过 |

最终构建日志在 `build/maturity-20260921/gradle.log`；JUnit XML 在 `build/test-results/test/`。本轮初次整合运行暴露的旧测试假设已按新菜单/练习规则调整，并增强多窗口和奖励隔离断言；上表来自修正后的完整 Gradle 运行。

可复现构建（JDK 17、本地依赖缓存就绪）：

```powershell
./gradlew.bat test buildPlugin verifyPlugin --offline --no-daemon --console=plain -PpluginVersion=2.0.0-maturity-preview
```

文字检查：

```powershell
python scripts/verify-languages.py build/maturity-20260921/final-language/displayed-text.txt
python scripts/verify-languages.py build/maturity-20260921/final-visual/displayed-text.txt --profile maturity
```

**生产输入诊断**

| 内容 | 配置 | 结果 | 模拟步数 | 剩余 HP / 生命 |
|---|---|---|---:|---|
| 第三关路线 | base | 到达 Boss | 5821 | 86 / 2 |
| 第三关路线 | developed | 到达 Boss | 4233 | 25 / 3 |
| 第三关 Boss | base | 击败 Boss | 4621 | 10 / 2 |
| 第三关 Boss | developed | 击败 Boss | 3502 | 7 / 3 |
| 第五关 Boss | base | 击败 Boss | 5001 | 28 / 2 |
| 第五关 Boss | developed | 击败 Boss | 4572 | 7 / 3 |

这些是现有 `ChapterPlaythroughProbe` / `ChapterBossPlaythroughProbe` 的确定性诊断。章节入口、种子和初始构筑是夹具；进入之后使用真实输入与模拟，不开无敌、不注入生命、不直接清怪、不改探针打法。developed 预先应用八项合法升级。路线与 Boss 是分别运行的场景，不能解释为一次连续战役。模拟步数也不是实际 IDE 帧率或真人通关时间。

第五关两种配置均实际经历爬行、脉冲与根刺；第三关 base 经历吊架封锁。第三关 developed 在已获得的断双臂暴露窗口击杀终阶段，这一奖励保留。未用强制锁血要求玩家看完招式。

六份摘要已保存在 [验收数据目录](data/2026-09-21-maturity/)。完整逐步轨迹仍在 `build/citadel-passage/route-probe/final-*` 和 `build/boss-depth-20260921/playthrough-transition/`。

**当前生产画面**

以下为 `MaturityPreview` 通过实际 GamePanel 绘制的受控场景。部分使用练习、章节和位置夹具，用于检查布局与视觉状态，不能代替手动游玩录像。

- [有存档时的菜单](data/2026-09-21-maturity/zh-CN-continue-menu-600.png)
- [新战役确认](data/2026-09-21-maturity/zh-CN-new-confirmation-600.png)
- [真实死亡处理后的失败页](data/2026-09-21-maturity/zh-CN-failure-600.png)
- [绞盘提升中的实体闸门](data/2026-09-21-maturity/zh-CN-winch-moving-600.png)
- [上层桥锁与下层双跳路线](data/2026-09-21-maturity/zh-CN-bridge-choice-600.png)
- [展开后的安全天桥](data/2026-09-21-maturity/zh-CN-bridge-open-600.png)

完整新场景与显示文本在 `build/maturity-20260921/final-visual/`；既有流程在 `final-language/`。两份显示文本已同步保存在验收数据目录。

**预览包与边界**

- 包：[merge-hell-runner-2.0.0-maturity-preview.zip](../../build/distributions/merge-hell-runner-2.0.0-maturity-preview.zip)，35,784,561 bytes。
- SHA-256：`37fe36d006a7fb85504d2bdead5b4f878114e96e1d99088c45beb5086b9d403c`。
- 结构化证据：[artifacts.json](data/2026-09-21-maturity/artifacts.json)。

本轮没有完成原生 IDE 真人试玩、30 分钟资源曲线或多 IDE 版本验证；包校验和离屏画面不能替代这些项目。未增加可改键、角色主动特性、互斥构筑、RP 消费用途、快速挑战模式，也没有把第三关的编排直接复制到其他关卡。

下一阶段应先根据这个样板的实际手感调整节奏，再按每关的主题扩展敌人配合、地图因果和高潮段落。角色/武器分支及长期目标依照成熟度评估继续推进。
