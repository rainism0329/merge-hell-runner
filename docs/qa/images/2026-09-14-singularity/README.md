# 奇点边缘实际游戏画面

2026-09-14，最终构建通过真实 GamePanel 输入/更新、EDT 绘制生成。保留 13 张未编辑原图。全部回归共 232 张：基础界面 68、环境 40、蓝图城塞 36、内核核心 36、奇点边缘 52；涵盖简体中文/英文与 600×400 / 960×600。

第五关夹具仅选择章节、起始位置、清理无关敌人，路线额外生成真实 Mirror 展示造型。E 操作、危险预警、双锚点共振与冷却均由游戏推进；阶段/结局使用公开伤害接口加速，阶段、计时器、结算结果不直接注入。全部第五关画面 HP100，未开启 Lab；英文使用关闭闪光设置。正常步行连锁另由集成测试验证。它们不等同于真人完整战役试玩。

- [实现与验证记录](../../2026-09-14-singularity-and-render-performance.md)
- [232 张画面的双语文字](displayed-text.txt)
- [52 张第五关画面的真实状态](state-evidence.csv)
- [安装包校验](package-check.json)
- [保留文件哈希](manifest.json)

- [工业约束大厅与机械镜像侦察机](zh-CN-singularity-route-960.png)
- [关闭闪光后的内存回响预警](en-singularity-memorywarning-600.png)
- [地面回响与永久检修台](zh-CN-singularity-memoryactive-960.png)
- [结构扫描的真实危险范围](en-singularity-blueprintactive-600.png)
- [内核回响的通电地段](zh-CN-singularity-kernelactive-600.png)
- [E 稳定后的实体设施](zh-CN-singularity-stabilized-960.png)
- [终局首领安全入场](en-singularity-arrival-600.png)
- [第一个同步锚点](zh-CN-singularity-armed-960.png)
- [锁定后的实际弹丸路径](en-singularity-volleywarning-600.png)
- [双锚共振与核心暴露](zh-CN-singularity-exposed-960.png)
- [锚点冷却时的等待提示](en-singularity-cooldown-600.png)
- [第二阶段安全重配置](zh-CN-singularity-phase2-600.png)
- [真实终局结算](en-singularity-victory-600.png)
