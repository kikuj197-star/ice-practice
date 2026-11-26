# Ice Practice

**史上最强skyblock炸冰模组** — 用站点、瞄点、清除方块描边和 Pickobulus 瞄准区域，帮助你在 Glacite Tunnels 中快速定位炸冰位置。

> 当前为 **0.8.3 测试版**。炸冰数量和区域由客户端模型估算；服务器机制、其他玩家采矿以及 Mining Spread 等会影响实际结果。本项目与 Hypixel、Skyblocker 无官方关联。

## 安装

1. 准备 Minecraft **26.1.2 或 26.2**、Fabric Loader **0.19.3 或更新的兼容版本**、对应版本的 Fabric API 和 Java 25。
2. 从 [Releases](../../releases) 下载与你的游戏版本对应的 `ice-practice-0.8.3+26.1.2.jar` 或 `ice-practice-0.8.3+26.2.jar`，放进对应游戏实例的 `mods` 文件夹。两个文件不可互换。
3. 重启游戏。**不需要安装 Skyblocker**：坐标描边、文字和瞄准区域由模组独立渲染。

如果原来的 Skyblocker ICE 航点仍在显示，建议在 Skyblocker 里关闭这些航点，避免原有光柱与本模组描边重叠。模组不会自动修改 Skyblocker 的航点文件。

## 推荐炸冰流程

1. 在 Hypixel 游戏内输入 **`/widgets`**，找到并开启 **Pickaxe Ability Widget**（镐子技能小部件）。然后到矮人矿井的 **Glacite Tunnels**，按 TAB 确认列表中出现 `Pickobulus:` 和技能冷却状态。多人模式下的瞄准区域及成功使用检测依赖这一状态；未显示时请先检查该 Widget 是否开启。
2. 找到同编号的**红色站点**、**绿色瞄点**和**橙色清除方块**。先挖开必要的清除方块。
3. **建议使用 Etherwarp（Ewarp）传送到红色站点所标记的方块上**。Ewarp 会把玩家放到方块顶部中心，这样每次站位更一致，有利于再次命中缓存。目标方块上方应留出可站立空间。
4. 面向绿色瞄点，用准星指向视野内 **5 格以内**的浮冰。首次到这个站位和方块时，模组需要数秒采样计算可投掷的最优预测区域；计算完毕后，区域会贴在对应方块面上。
5. 将准星对准红色高亮区域，使用 Pickobulus。HUD 的 `Current forecast` 是根据当前可见的冰预测的数量；实际结果可能不同。
6. 当模组观察到高亮内右键后技能进入冷却、附近浮冰发生变化时，会把该区域保存下来。之后从同一站位对准同一方块，可跳过耗时计算。站位需精确到约 **0.04 格**，站立与下蹲分别匹配；保存上限为 128 条。

周围的冰因 Mining Spread 或采矿发生变化时，已缓存的定位区域仍会显示，当前预测数量会重新计算。缓存代表之前保存的区域；矿脉变化较大时，如需重新计算当前区域，可使用 `/iceangle refresh`。

安装 Hypixel Mod API 并确认所在地点为 Hypixel SkyBlock 矮人矿井时，不同连接入口共用同一矿井缓存；其他服务器和单人世界仍分别保存。旧版同一连接入口的缓存命中后会自动迁移，其他旧入口的记录不会未经确认自动合并。

## 坐标与描边

输入 **`/iceway`** 或 `/iceconfig` 打开管理界面：

- **新增坐标**：填名称、分组和整数 `X Y Z`，选择类型并保存。
- **修改或删除**：点击列表中的坐标；可修改名称、分组、坐标、类型和显示开关，或直接删除。
- **筛选与翻页**：按全部、站点、瞄点、清除方块筛选，使用上一页和下一页浏览。
- **简易 / 详细标签**：简易模式显示编号、数量和类型；详细模式显示完整名称。清除方块只有描边，没有文字标签。
- **颜色/字号**：分别设置站点、瞄点、清除方块的六位十六进制颜色；拖动标签大小滑块后点保存。标签按屏幕大小显示，远处不会因距离缩小。

默认描边分别为**红色站点**、**绿色瞄点**、**橙色清除方块**。站点标记的是脚下的方块，而不是玩家脚所在的空气格。站点和瞄点持续描边；清除方块在玩家五格内且已不是浮冰时隐藏，离开范围后重新显示。

模组自带一组 Glacite 默认点位，以及默认关闭的 A–Q 原始标记。`START` 入口点不导入。已有的 Skyblocker 航点不会被自动读取或修改。

坐标仅在 Hypixel SkyBlock 矮人矿井和单人世界中显示，切换到其他服务器或岛屿后自动隐藏。安装 Hypixel Mod API 时优先根据地点事件判断；未安装时尝试从侧栏和 TAB 信息保守识别，信息不足时不会显示。

## 瞄准区域设置与指令

`/iceangle` 打开设置界面，可调整瞄准区域颜色、透明度，以及拖动 HUD 位置和调整大小；这些设置与坐标描边颜色、标签字号分别保存。

| 指令 | 作用 |
| --- | --- |
| `/iceangle on` / `/iceangle off` | 打开或关闭瞄准区域预览 |
| `/iceangle status` | 查看 TAB 技能状态、缓存数量和当前计算状态 |
| `/iceangle refresh` | 忽略当前结果并重新计算 |
| `/iceangle color FF0000` | 设置区域颜色；替换为六位十六进制颜色 |
| `/iceangle opacity 40` | 设置区域不透明度，范围为 5–100 |
| `/iceway` / `/iceconfig` | 打开坐标管理界面 |

只有准星实际命中的浮冰方块会触发预览。若多个可达面可供选择，计算时选择预测数量最高的面；高亮范围是采样得到的估计，不是服务器返回的精确碰撞区域。

## 单人练习

在**单人世界**中：

1. 输入 `/icepractice on`，手持镐子右键，模拟炸开目标周围的浮冰。聊天栏的数量是模型估计。
2. 输入 `/icepractice off` 关闭练习挖掘。该模式不会在专用多人服务器上执行。
3. 输入 `/icepractice wand` 获得练习用传送工具。右键为短距离传送；下蹲右键模拟 Ewarp，最远 60 格，目标方块上方需要两格空气，传送到方块顶部中心。该练习工具无消耗。

## 进阶：采集和缓存

- `/icecache status` 查看本地浮冰缓存及收集状态；`/icecache save` 尝试写入待保存的数据。自动收集需要识别到矮人矿井和 TAB 中的 Pickobulus 信息；缓存仅增加见到的浮冰，不会因它暂时被挖掉而删除。
- `/iceprobe start` 开启手动观测，然后右键触发一次约 6 秒的记录；`/iceprobe stop` 停止，`/iceprobe status` 查看状态。记录是客户端观察结果，不能单独证明每个变化都由你的技能造成。
- 用户数据存放在游戏实例的 `config/icepractice/`。其中可能包含个人坐标、服务器信息和观测记录；分享配置或日志前请自行检查。Release 不包含这些用户数据。

## 从源码构建

需要 JDK 25：

```powershell
.\gradlew.bat build -x test testIceModel testIceUnion testIceAngleMath testIceAngleCache testIceWaypoints testIceLocation testIceWorldIdentity
```

macOS / Linux 使用 `./gradlew`。主分支构建 26.1.2 版本；构建 26.2 版本请先切换到 [mc-26.2 分支](../../tree/mc-26.2)。构建产物位于 `build/libs/`。首次构建需要下载 Gradle 和依赖。

## 许可与致谢

本项目以 [LGPL-3.0](LICENSE) 发布。炸冰数量模型参考并改写自 [Skyblocker](https://github.com/SkyblockerMod/Skyblocker)；描边渲染参考 [Safari King](https://github.com/kikuj197-star/safari-king)。详细归属和许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
