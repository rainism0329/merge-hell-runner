# 正式 Gradle 构建恢复记录

环境恢复日期：2026-09-10；最终正式复验：2026-09-11 00:00–00:01。**当前稳定源码已通过 `test build buildPlugin verifyPlugin`：51 个测试类、295 项测试全部通过，0 失败、0 错误、0 跳过。** 构建前后 160 个源码 / 资源文件哈希一致。原始 160 项和中间 250 项结果保留为历史；正式构建结果不代替完整 IDE 内的 GUI 试玩或视觉验收。

## 恢复结果

| 检查 | 实际结果 |
| --- | --- |
| Gradle 项目配置与依赖解析 | 成功，保留 Gradle 8.13 / IntelliJ Gradle Plugin 1.17.4 |
| `setupDependencies` | 成功，真实 SDK 为 IntelliJ IDEA Community 2023.2.2 / IC-232.9921.47 |
| 原始基线 `compileJava` / 插桩 | 成功，Oracle JDK 17.0.12 编译 |
| 原始基线 JUnit | **160 / 160 通过**，0 失败、0 错误、0 跳过 |
| 测试运行时 | SDK 指定的 JetBrains Runtime 17.0.8，b1000.22 |
| 原始基线 `build` / `buildPlugin` / `verifyPlugin` | **全部成功** |
| 原始基线完整 Gradle 执行 | 4 分 17 秒，16 个任务执行 |
| 新验证脚本离线 `help` | 成功；运行后四个临时环境变量均恢复原值 |
| 中间阶段首次完整集成 | 250 / 250 测试通过，四任务成功；55 秒，历史结果 |
| 最终正式集成 | **295 / 295 测试通过**，`test` / `build` / `buildPlugin` / `verifyPlugin` 全部成功；54 秒 |
| 最终源码 / 资源一致性 | 160 个文件，构建前后 0 差异 |
| 最终差异检查 | `git diff --check` 退出码 0；只使用进程内 Git 配置，无全局修改 |

`verifyPlugin` 是插件结构验证。本次未执行 `runPluginVerifier` 的跨版本 API 检查，也未进行开发 IDE 内的实际安装和 GUI 试玩。

## 最终正式复验与交付包

实际入口为 `./scripts/verify.ps1`，Oracle JDK 17.0.12，保持真实 IntelliJ IC 2023.2.2 / IC-232.9921.47 SDK 与对应 JBR 17 测试环境。日志：`.gradle/verification-logs/20260911-000035-228.log`。

| 检查 / 产物 | 结果 |
| --- | --- |
| `test` | 51 suites，295 tests，0 failures / errors / skipped；XML 汇总执行时间 4.488 秒 |
| `build` | 成功 |
| `buildPlugin` | 成功，重新生成插件 ZIP |
| `verifyPlugin` | 成功 |
| Gradle 总耗时 | 54 秒；16 个任务中 14 个执行、2 个已是最新 |
| 输入清单 | `build/gradle-recovery/release-fixed-source-before.txt` 与 `release-fixed-source-after.txt` 完全一致 |
| 输入清单 SHA-256 | `59fdb8942b69a69cdf2b13995ca2d1d27eb18189c2bbf9b958093dce6c0b4bba` |
| 插件 ZIP | `build/distributions/merge-hell-runner-1.0.0.zip`，**2,799,911 字节** |
| ZIP SHA-256 | **`c45c26c81d0f8887f993054088e53c5ad6c136f6f5b769ea76ae60545c2ec6a2`** |
| 包内实现 JAR | `merge-hell-runner/lib/instrumented-merge-hell-runner-1.0.0.jar` |
| 插件描述符 | `com.bigphil.mergehell`，版本 `1.0.0`，`since-build=232`；未声明 `until-build`，不据此声称所有后续 IDE 版本已经验证 |
| 字节码抽查 | 包内 `GamePanel.class` major version 61，即 Java 17 |
| 资源核对 | 13 个源资源全部在包内；其中 12 个原样资源的 SHA-256 与源码一致，`plugin.xml` 为 Gradle 正常修补后的描述符 |

包内游戏美术资源为 `game/art/city.png`（2,260,572 字节）及 `city.properties`，其余为现有图标 / 图片与插件描述符；本检查确认实际打包内容，没有把未接入的人物精灵声明为已交付。

证据：`build/test-results/test/TEST-*.xml`、`build/reports/tests/test/index.html`、`build/gradle-recovery/release-fixed-test-totals.json`、`release-artifact.json`、`release-fixed-diff-check.log`。资源核对通过读取 ZIP 内插桩 JAR 完成，没有解包修改源资源。Git 检查仅有现有 LF / CRLF 提示，没有空白错误。

前一轮 294 项中的复活失败已修复：生命值扣减下限为 0，治疗使用不会溢出的运算且忽略负治疗，复活恢复满血；原复活位置断言保留，并增加极端伤害 / 治疗边界测试。295 项成功结果对应修复后的稳定输入。

## 所做的构建改动

没有升级框架或改变兼容性目标，`build.gradle.kts`、`settings.gradle.kts`、`gradle.properties` 保持原配置。

1. 重新生成与项目已锁定版本一致的 **Gradle 8.13 wrapper**。旧 wrapper JAR 未提供 `networkTimeout` 能力，容易在下载阶段长时间等待；新 wrapper 使用 30 秒网络超时，并设置发行版的官方 SHA-256。
2. 增加 [scripts/verify.ps1](../../scripts/verify.ps1)，统一 JDK 检查、缓存、代理传递与验证日志。
3. 所需 Gradle 插件、IDE SDK、JBR、插桩工具和 JUnit 依赖落在项目 `.gradle/evolution-user-home/`，无需写入全局 Gradle 或 IDE 配置。

新脚本默认执行 `test build buildPlugin verifyPlugin`，要求 JDK 17。它读取当前 `HTTP_PROXY` / `HTTPS_PROXY` 中的 HTTP 代理主机和端口，分别传给 wrapper JVM 和 Gradle；含凭据的代理 URL 不复制到命令行。`JAVA_HOME`、`GRADLE_USER_HOME`、`JAVA_OPTS` 与 `CI` 均只在调用期间设置，并在 `finally` 中恢复。

验证期间设置 `CI=true`，使用 IntelliJ Gradle Plugin 已有行为跳过可选 IDE 源码归档；真实编译所用 SDK 二进制保持完整，普通 `gradlew` 开发调用的默认行为不变。

## 可复用验证入口

在仓库根目录的 PowerShell 执行：

```powershell
# JAVA_HOME 已指向 JDK 17 时
./scripts/verify.ps1

# 显式选择当前机器已安装的 JDK 17
./scripts/verify.ps1 -JavaHome 'E:\Java\jdk-17.0.12'

# 本项目缓存完整后，离线执行同一验证流程
./scripts/verify.ps1 -Offline

# 发布候选的干净构建；按 Gradle 既有规则清理项目 build 目录
./scripts/verify.ps1 -Clean -Offline

# 仅准备 SDK；详细依赖日志可使用 PowerShell 的 Verbose 开关
./scripts/verify.ps1 -Tasks setupDependencies -Verbose
```

`-NoProxy` 可禁用脚本对 HTTP 代理环境变量的转换。已有 JVM 选项保留原样。

缓存目录是 `.gradle/evolution-user-home/`；日志写入 `.gradle/verification-logs/<时间>.log`。二者都已被仓库现有 `.gitignore` 排除，并且不在 `build/` 内，因此正常 `clean` 不会删掉下载缓存或本轮日志。

## 下载与校验

本机最初只有其他 Gradle / IntelliJ 版本的部分缓存；旧构建插件、232 SDK 与对应测试 JBR 均需补齐。官方 Gradle GitHub 大文件下载速度较慢，改从镜像取得 **相同 8.13 发行版**，执行前与官方校验值核对一致。

SDK 通过 [JetBrains 官方 IntelliJ 仓库](https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2023.2.2/ideaIC-2023.2.2.zip) 的 CDN 下载。为控制大文件下载时间，保留已下载前缀，对剩余内容使用六个 HTTP Range 请求；组装后检查总字节数与官方 SHA-1，再放入项目 Gradle 缓存。Gradle 随后输出 `Found locally available resource with matching checksum` 并正常解包。已核对完整缓存后，清理了本次生成的范围片段和重复组装归档，释放约 1.58 GB，保留正式缓存归档及 SDK。

| 文件 | 校验 |
| --- | --- |
| Gradle 8.13 bin ZIP | 官方 SHA-256：`20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` |
| Gradle 8.13 wrapper JAR | 与官方 SHA-256 一致：`81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f` |
| IntelliJ IC 2023.2.2 SDK ZIP | 788,651,098 字节；官方 SHA-1：`9501eca11251de344f61d01388781a8fabcd81df` |
| 同一 SDK ZIP | 本地计算 SHA-256：`00a6842151a66257dd66bbc9b42053a6ea3a0166689fd2c56e0a2468a769be2b` |

两次为更换下载方式而主动停止的本次 Gradle / curl 进程留下了下载中止日志，属于恢复过程，不是游戏源码失败。后续的正式 Gradle 构建已完整成功。

## 正式基线证据

验证输入仍是 [M0 记录](2026-09-10-baseline.md) 中提前冻结的 `build/baseline-20260910/input/`，没有混入并行游戏改动。调用项目当前 wrapper，使用 `--project-dir` 指向该副本：

```powershell
# 环境由项目内验证缓存与现有代理设置提供；以下为实际目标任务
./gradlew.bat --project-dir build/baseline-20260910/input `
    test build buildPlugin verifyPlugin --no-daemon --console=plain
```

本次实际调用还设置了 `CI=true`、项目 Gradle 用户目录及当前代理的 JVM 参数；完整日志保存在以下路径：

- `.gradle/verification-logs/baseline-formal-20260910.log`
- `build/baseline-20260910/input/build/test-results/test/TEST-*.xml`
- `build/baseline-20260910/input/build/reports/tests/test/index.html`

生成的 **原始基线包**：`build/baseline-20260910/input/build/distributions/merge-hell-runner-1.0.0.zip`，410,707 字节，SHA-256 为 `4bdcfae07f9d1129d6fe105541b448d3fc231bf22e76d07597ff2d99aa6f753c`。它仅证明原始源码可以正式构建，不作为正在提升的新版本交付包。

构建仍报告 Gradle 9 相关弃用提示，当前锁定的 8.13 构建成功；未为消除提示而升级构建框架。项目未声明 Settings / SearchableOptions 页面，既有 `buildSearchableOptions` 和其空输出打包步骤按原配置跳过。

## 当前工作区集成检查

首次检查执行 `./scripts/verify.ps1`，输入哈希清单保存于 `build/gradle-recovery/integration-source-before.txt`。日志为 `.gradle/verification-logs/20260910-231845-239.log`。

- `compileJava` 暴露 `UpgradeOverlayRenderer.java:75` 的升级卡片 `switch` 未覆盖新加入的 15 个 `UpgradeId`；已交给该代码负责人修复。测试和打包未执行，不能视为新版验收通过。
- Gradle 写入既存的 `build/reports/problems/problems-report.html` 时遇到 `AccessDeniedException`。该文件生成于 2026-07-15，其权限不含当前沙箱账户。完整保留到 `build/gradle-recovery/prior-problems-report-20260715.html`，让下一轮构建创建新的生成报告；未删除旧报告、调整全局权限或修改游戏代码。
- 首轮用时 32 秒，退出码 1；这次失败证明正式环境已能检查实际集成源码，尚需修复后完整复验。

修复遗漏的卡片分支后，再次执行 `./scripts/verify.ps1`，完整流程成功：

| 项目 | 结果 |
| --- | --- |
| 日志 | `.gradle/verification-logs/20260910-232245-664.log` |
| 输入 | 149 个源码 / 资源文件；构建前后清单一致，没有构建期间改动 |
| 输入清单 | `build/gradle-recovery/integration-source-after-switch-fix.txt` |
| 清单 SHA-256 | `6004d3914d0cdf4e9d575695b83ceb9dcc0c0742380d796a026c0277f3ad6890` |
| 生产 / 测试编译及插桩 | 全部成功，沿用 Java 17 / IntelliJ 232 |
| JUnit XML 汇总 | 43 个测试类，**250 项通过**，0 失败、0 错误、0 跳过 |
| `build` / `buildPlugin` / `verifyPlugin` | 全部成功 |
| 耗时 | 55 秒，15 个任务中 14 个执行、1 个已是最新 |
| 新生成包 | `build/distributions/merge-hell-runner-1.0.0.zip`，2,774,464 字节 |
| 包 SHA-256 | `9f9acd934cc1b7f5b4af5cd015a398d04689265e5203541bf769e99cf74f182d` |

本轮 XML 保存在 `build/test-results/test/`，HTML 在 `build/reports/tests/test/index.html`，汇总在 `build/gradle-recovery/integration-test-totals.json`。旧报告权限错误没有复现，新的 Problems Report 正常生成。后续主流程集成测试和视觉素材仍在准备；上述数据对应此轮稳定输入，尚未宣称完整 GUI 或视觉验收通过。

新增 4 项 `GamePanelEvolutionTest` 和 3 项 `ActorVisualsTest` 后，日志 `.gradle/verification-logs/20260910-232716-319.log` 记录了 257 项测试、1 项失败。唯一失败是暂停姿态测试在角色首次落地时要求 `RUN`，实际为合法的 `LAND`；断言失败发生在暂停比较之前，已交由测试负责人调整前置状态。其余 256 项通过，耗时 45 秒；由于 `test` 失败，本次尚未继续打包。失败 XML 另存于 `build/gradle-recovery/actor-visuals-failed-20260910-232716.xml`，以便保留修复前证据。

最终接线后的首次正式复验（`.gradle/verification-logs/20260910-235644-330.log`）完成 294 项测试，其中 293 项通过、1 项失败，源码 / 资源前后哈希完全一致。失败为 `GamePanelEvolutionTest.losingALifeAtTheInitialRecoveryPointNeverPublishesThePlayerBelowTheFloor`：角色承受 200 点伤害后 HP 为 −100，复活使用固定 `heal(100)` 仅恢复到 0，测试期望满血 100。已交给主任务修复满血恢复语义；该失败发生在地面位置断言之前。编译及插桩成功，测试失败后未继续打包，耗时 55 秒。失败 XML 保存在 `build/gradle-recovery/release-gamepanel-failed-20260910-235644.xml`。同轮 `git diff --check` 退出码 0，仅提示现有 LF / CRLF 转换，没有空白错误。
