# FlameChunk

FlameChunk 是面向 Minecraft 1.7.10 Forge 的服务端性能采样、区块热力图和实体负载治理模组。采样和管理由服务端执行；客户端可选安装，用于查看报告、实时数据和地图覆盖层。

## 安装与兼容性

- 服务端必须安装 FlameChunk。单人游戏由同一份模组同时提供集成服务器和客户端功能。
- 客户端安装 FlameChunk 后可打开诊断界面和地图集成。JourneyMap 5、JourneyMap 6、Xaero's Minimap、Xaero's World Map 均为可选依赖。
- Navigator 为可选依赖。安装后优先通过 Navigator 的地图图层 API 绘制；未安装时使用 FlameChunk 自带的地图适配。
- ServerUtilities 为可选依赖。安装后 FlameChunk 注册扫描、弱区块清理和加载器管理权限，并可显示领地信息。未安装时玩家权限由服务端 OP 设置控制。
- JourneyMap 5 和 6 使用不同的 Forge mod id 识别。请勿同时安装两个 JourneyMap 版本。

当前移植不包含 FTB/C2ME 与 Carpet 集成。放置者归属和 loader 区域显示依赖 1.7.10 不具备的版本能力，因此不提供这些显示。1.7.10 原生 Forge ticket 来源、加载器冻结和可识别的 ServerUtilities 领地信息仍可使用。

## 性能分析

服务端按需采样并生成有界报告，覆盖随机刻、计划刻、方块更新、方块事件、方块实体、实体、自然刷怪、事件处理器、服务端任务和垃圾回收。区块热力使用每 tick 毫秒数（MSPT）；合计不重复计算方块更新耗时。

分析可以从游戏内诊断界面、支持的地图界面或命令启动。扫描结果可在诊断窗口、聊天栏或两处显示；没有兼容客户端时，可从服务端控制台查看报告。实时订阅单独受服务端权限和客户端配置控制。单人游戏中暂停世界的地图界面会暂停服务端 tick；启动扫描后关闭地图，或改用不暂停游戏的诊断界面。

诊断界面还提供独立性能观测、热点明细、对象热点及栈详情。JFR 栈采样不可用时会显示降级状态，不影响主要 MSPT 观测。

## 地图与世界覆盖层

JourneyMap 5、JourneyMap 6、Xaero's World Map 和 Xaero's Minimap 可显示性能热力、弱加载区块风险、工具提示及扫描控制。地图右键菜单支持选择弱区块实体类型进行二次确认清理，以及对区块加载器执行二次确认的冻结操作。安装 Navigator 时，热力图通过其图层 API 接入。

世界内覆盖层可显示附近实体、掉落物和方块实体热点，以及热点区块光柱；目标按距离和视锥筛选，附近掉落物会聚类。界面暂停时数据更新也会暂停。

## 命令

所有时长以秒为单位；扫描时长范围为 `1..86400`，省略时使用配置值。

| 命令 | 说明 |
| --- | --- |
| `/flamechunk scan [seconds]` | 开始性能扫描。控制台、OP 或获准的 ServerUtilities 用户可执行。 |
| `/flamechunk stop` | 停止当前扫描并发布部分报告。 |
| `/flamechunk report` | 查看最近的性能报告。 |
| `/flamechunk tickets` | 查看 Forge ticket 来源和已保存的冻结区块。 |
| `/flamechunk loader freeze <chunkX> <chunkZ> confirm` | 冻结指定区块的活动加载器 ticket。 |
| `/flamechunk loader unfreeze <chunkX> <chunkZ> confirm` | 恢复指定区块的加载器 ticket。 |
| `/flamechunk loader clear <chunkX> <chunkZ> confirm` | 清除指定区块的活动加载器 ticket。 |
| `/flamechunk loader frozen list` | 查看已保存的冻结策略。 |
| `/flamechunk loader frozen clear-orphans` | 清理当前维度中不再对应活动 ticket 的策略。 |
| `/flamechunk weakclear <chunkX> <chunkZ> <entityType> confirm` | 分批清理弱加载区块中指定类型的实体。仅限游戏内玩家。 |

实体清理会永久移除符合条件的实体，无法撤销。目标在确认后会重新校验，并按 tick 分批处理。

默认要求 OP 执行扫描、加载器控制和弱区块清理。启用 ServerUtilities 后，还会检查相应的 `flamechunk.scan`、`flamechunk.loadercontrol` 和 `flamechunk.weakclear` 权限节点。扫描存储量和数据包大小受配置上限约束。

## 诊断与治理

- 弱区块诊断会分批检查实体密集且没有普通区块加载的区块，报告主要实体类型，并可从地图或命令提交有界清理任务。
- 实体加载保护可在区块载入时按阈值、实体类型数和保留数量删除超量实体；该保护默认关闭，可单独配置阈值、检查和删除上限及广播行为。
- 实体负载诊断会周期性记录高实体数区块及可用的 ServerUtilities 领地信息。
- Watchdog 崩溃报告可附加有界的 Forge ticket 来源、区块、实体和方块实体摘要；该诊断可配置关闭。
- 加载器冻结策略保存在世界数据中，并会作用于后续匹配的 ticket；可列出策略或清理失效策略。

服务端与客户端配置由 GTNHLib `@Config` 注册，位于 FlameChunk 配置目录。客户端可设置热力阈值和透明度、弱加载显示、ticket 标记、工具提示字段和分类、实时订阅、世界覆盖层及报告输出方式。客户端还可用键位打开诊断界面。

## 开发

项目使用 JDK 25 和 RFG 构建。Windows PowerShell 中可运行以下命令构建且不运行单元测试：

```powershell
.\gradlew.bat build -x test
```

运行 Java 25 服务端：

```powershell
.\gradlew.bat runServer25
```

每种地图客户端配置使用独立运行目录，并继承 `runClient25` 的启动参数：

| Gradle 任务 | 附加地图依赖 |
| --- | --- |
| `runClient25Jm5` | JourneyMap 5 |
| `runClient25Jm6` | JourneyMap 6 及其 API |
| `runClient25Xaero` | Xaero's Minimap 和 Xaero's World Map |

当前 Xaero 开发启动配置受已安装的 `lwjgl3ify` 兼容问题影响：其 `XaerosMinimapScrolling` Mixin 目标 `onGuiClosed()V` 不存在于当前使用的 Xaero JAR 中，因此可能在 FlameChunk 集成初始化前中止客户端启动。
