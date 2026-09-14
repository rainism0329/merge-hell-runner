# 内核核心性能对照原始数据

三个版本均使用同一生产路径探针、Java 17 headless、256–512 MiB 堆；每场景预热 750ms、采样 1750ms。before 为上一批 ZIP，initial 为本批未缓存场景，after 为本批最终缓存版本。原始摘要里的 `sample_window_ms=2000` 是说明文字错误，实际常量一直为 1750ms。保留原始摘要不改写；随附探针仅修正此说明文字。

CSV 数量为真实更新之后的在场数量。普通场景每步注入 6 敌人、32 玩家弹、12 敌弹、120 粒子，生产射击/刷怪使敌弹偶尔为 13、第四关敌人偶尔为 7。所有满预算帧均精确为 40 / 320 / 180 / 1200，所有帧成功发布。`performance-comparison.json` 包含每种实际数量的出现次数。

每个版本 12 个场景，采样数依次为 1546 / 1286 / 1352。它们是短时、非限帧、合成负载下的真实更新/发布与 EDT 绘制调用耗时，不能作为原生 IDE 帧率或长期稳定性的证明。不同版本在相同时间窗口内推进的模拟步数不同，不是完全逐帧相同的场景序列。

`source-before.json` / `source-after.json` 记录最终完整构建前后的 268 个源文件一致性；随后仅修改探针采样时长说明文字。生产安装包及其资源校验记录见 `package-check.json`。

- [完整性能表](performance-table.md)
- [实现与验证说明](../../2026-09-14-kernel-core-and-projectile-budget.md)

随附 `performance_report.py` 为原始报告脚本，输入目录为项目下 `build/kernel-verification/performance-{before,initial,after}`；如需复算，将对应 CSV 放回该布局即可。
