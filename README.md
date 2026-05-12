# ReadStar（占星术）— NeoForge 1.21 天文模组

一个基于真实天体力学与星表数据的 Minecraft 天象模组。支持完整的行星轨道体系、真实恒星目录（含约 200 颗恒星）、三级光晕渲染，以及服务端权威同步的行星系统配置。

---

## 目录

- [快速开始](#快速开始)
- [配置总览](#配置总览)
- [天体系统配置 (`system.json`)](#天体系统配置)
- [恒星目录配置 (`stars.json`)](#恒星目录配置)
- [精灵图集与运行时纹理生成](#精灵图集与运行时纹理生成)
- [数据生成（Gradle）](#数据生成gradle)
- [网络同步机制](#网络同步机制)
- [本地化](#本地化)
- [构建与运行](#构建与运行)
- [常见问题](#常见问题)

---

## 快速开始

```bash
# 构建
./gradlew build

# 运行客户端
./gradlew runClient

# 生成数据（纹理图集配置等）
./gradlew runData
```

模组加载后，行星系统定义在 `data/readstar/celestial/system.json`，恒星目录在 `assets/readstar/custom/stars/stars.json`。所有修改后需要重新构建或使用 F3+T 重载资源包。

---

## 配置总览

| 文件 | 位置 | 用途 |
|------|------|------|
| `system.json` | `data/readstar/celestial/` | 天体层次结构（恒星→行星→卫星）与轨道参数 |
| `stars.json` | `assets/readstar/custom/stars/` | 真实恒星目录（名称、位置、光谱、视星等、颜色） |
| `star.json` | `assets/readstar/atlases/` | 星星图集的精灵源类型声明 |
| `celestial.json` | 由 `runData` 生成 | 天体纹理图集（earth, mars, mercury, venus） |
| PNG 纹理 | `assets/readstar/textures/environment/star/` | 核心底模及三级光晕底模 |

---

## 天体系统配置

行星系统定义为一个嵌套的 JSON 树，根节点为 `System`。

**文件**：`data/readstar/celestial/system.json`

### 完整结构示例

```json
{
  "System": {
    "Sun": {
      "mass": 2.0e30,
      "radius": 6.955e8,
      "luminance": 15,
      "axis": [0.0, 0.0, 0.0],
      "orbit": {
        "semiMajorAxis": 0.0,
        "eccentricity": 0.0,
        "inclination": 0.0,
        "argumentOfPeriapsis": 0.0,
        "longitudeOfAscendingNode": 0.0,
        "initialMeanAnomaly": 0.0
      },
      "children": {
        "Earth": {
          "mass": 6.0e24,
          "radius": 6.371e6,
          "luminance": 0,
          "axis": [0.0, 0.0, 0.0],
          "orbit": { ... },
          "children": {
            "Moon": {
              "mass": 7.0e22,
              "radius": 1.737e6,
              "luminance": 0,
              "axis": [0.0, 0.0, 0.5],
              "orbit": { ... },
              "children": {}
            }
          }
        },
        "Mars": { ... }
      }
    }
  }
}
```

### 参数说明

#### 天体公共字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `mass` | double | 质量（千克）。值为 0 的天体固定于原点 |
| `radius` | double | 半径（米）。影响渲染时的视大小 |
| `luminance` | int (0~15) | 自发光亮度。0=不发光，>0 会被系统识别为恒星 |
| `axis` | double[3] | 自转轴方向向量 `[x, y, z]`。若全零则使用默认值 `(0, 0, -1)` |
| `orbit` | object | 轨道参数（见下文） |
| `children` | object | 子天体映射。键为天体名称，值为相同结构 |

**重要**：天体名称不区分大小写（`parseAndAddCelestialBody` 内部统一转为小写）。

#### 轨道参数

| 字段 | 类型 | 说明 |
|------|------|------|
| `semiMajorAxis` | double | 轨道半长轴（米）。0 表示不公转（中心天体） |
| `eccentricity` | double | 轨道偏心率（0=正圆，0~1=椭圆） |
| `inclination` | double | 轨道倾角（弧度） |
| `argumentOfPeriapsis` | double | 近心点幅角（弧度） |
| `longitudeOfAscendingNode` | double | 升交点黄经（弧度） |
| `initialMeanAnomaly` | double | 初始平近点角（弧度），决定 t=0 时的起始位置 |

### 坐标系

- X: 右（东）
- Y: 上（天顶）
- Z: 南

所有位置使用 **右手系**，单位为米。渲染时按 100 单位缩放（可通过代码调整）。

### 继承规则

- `hostStar` 自动向上递归查找最近的自发光天体（`luminance > 0`）
- 如果没有自发光祖先，`hostStar` 为 null，该天体不会渲染太阳光晕
- 天体的 `position` 由其父天体轨道计算得到：`position = parent.position + orbit(parent.mass, t)`
- 根节点 `Root`（VOID）固定于原点

### 运行时计算

```text
getGameTime()     → updatePositions()    → 轨道公转（所有天体层次递归）
getDefaultClockTime() → updateCurrentVec() → 行星自转（天顶方向绕自转轴旋转）
```

- `gameTime` 影响 **公转位置**（轨道运行）
- `daylightTime` 影响 **自转朝向**（每日旋转）
- 两个时间维度独立，分别对应 `getGameTime()` 和 `getDefaultClockTime()`

---

## 恒星目录配置

**文件**：`assets/readstar/custom/stars/stars.json`

这是一个包含约 200 颗真实恒星的目录，每条记录格式如下：

```json
{
  "Stars": [
    {
      "name": "Sirius",
      "position": [-0.188181, -0.169608, 0.967338],
      "type": 1,
      "Vmag": -1.46,
      "color": 4291815679
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 恒星名称（仅用于标识） |
| `position` | double[3] | 单位球面上的方向向量 `[x, y, z]`。渲染时被归一化并缩放到 100 单位 |
| `type` | int (0~11) | 光谱/颜色分类。影响精灵图集的色彩映射 |
| `Vmag` | double | 视星等。决定亮度等级和光晕级别 |
| `color` | int | ARGB 颜色值。作为图集中生成子图的 key |

### 颜色类型

| type 范围 | 色调 |
|-----------|------|
| 0~2 | 蓝色/蓝白色 |
| 3~5 | 白色/黄白色 |
| 6~8 | 黄色/橙色调 |
| 9~11 | 橙红色/红色调 |

### 亮度与渲染

- `Vmag < 2.0`：会额外绘制光晕（按亮度分三级）
  - `Vmag < 0.5` → `glow_high`
  - `0.5 ≤ Vmag < 1.5` → `glow_med`
  - `1.5 ≤ Vmag < 2.0` → `glow_low`
- `2.0 ≤ Vmag < 14.0`：仅绘制核心，无光晕
- 亮度映射到顶点 Alpha 通道：`clamp((14 - Vmag) / 15, 0.4, 1.0) × 255`

### 添加/修改恒星

只需在 `Stars` 数组中添加或修改对应条目即可。**无需修改其他文件**，`StarSpriteSource` 会：
1. 读取所有恒星的 `color` 字段
2. 自动去重并生成对应颜色的精灵（每种颜色 1 核心 + 3 光晕）
3. 按颜色索引放入星星图集

---

## 精灵图集与运行时纹理生成

### 星星图集

**声明文件**：`assets/readstar/atlases/star.json`

```json
{
  "sources": [
    { "type": "readstar:star" }
  ]
}
```

- `type: "readstar:star"` 通过 `RegisterSpriteSourcesEvent` 注册
- 运行时从 `stars.json` 读取所有 `color` 值
- 读取 `star_base.png` 作为核心底模，`star_glow_low/med/high.png` 作为光晕底模
- 每张底模 32×32 灰度 PNG，每个像素的亮度值用于染色

每种颜色生成 **4 个子图**：
| 路径 | 内容 | 亮度系数 |
|------|------|---------|
| `environment/stars/color_{color}` | 核心 | 1.0× 颜色 |
| `environment/stars/glow_low_{color}` | 低级光晕 | coreBrightness × 0.35 |
| `environment/stars/glow_med_{color}` | 中级光晕 | coreBrightness × 0.35 |
| `environment/stars/glow_high_{color}` | 高级光晕 | coreBrightness × 0.35 |

### 底模纹理

位于 `assets/readstar/textures/environment/star/`：

| 文件 | 说明 |
|------|------|
| `star_base.png` | 核心底模（32×32 灰度），像素值为亮度权重 |
| `star_glow_low.png` | 低级光晕底模，边缘柔和扩散 |
| `star_glow_med.png` | 中级光晕底模 |
| `star_glow_high.png` | 高级光晕底模，建议在圆形柔光基础上加箭形外射效果 |

**制作要求**：
- 格式：RGBA 32-bit PNG（使用 Pillow 等工具生成，避免 8-bit 灰度）
- 尺寸：32×32（2 的幂次方）
- 背景：纯黑（`rgb(0,0,0)`），确保边缘融入背景
- 亮度范围：0.0（纯黑）~ 1.0（纯白）
- 光晕的核心中心亮度通过 `sampleCoreBrightness` 自动采样（中心 8×8 区域）

### 天体图集

由数据生成器（`runData`）生成，位于 `src/generated/resources/assets/readstar/atlases/celestial.json`：

```json
{
  "sources": [
    { "type": "minecraft:single", "resource": "readstar:environment/celestialbody/earth" },
    { "type": "minecraft:single", "resource": "readstar:environment/celestialbody/mars" },
    { "type": "minecraft:single", "resource": "readstar:environment/celestialbody/mercury" },
    { "type": "minecraft:single", "resource": "readstar:environment/celestialbody/venus" }
  ]
}
```

如需添加新的天体纹理：
1. 在 `assets/readstar/textures/environment/celestialbody/` 下添加 PNG
2. 在 `CelestialSpriteSourceProvider.gather()` 中添加 `addSource(new SingleFile(...))`
3. 运行 `./gradlew runData` 重新生成

---

## 数据生成（Gradle）

```bash
./gradlew runData
```

这会运行 `GatherDataEvent.Client` 中注册的 `CelestialSpriteSourceProvider`：

- 输出到 `src/generated/resources/`
- 生成 `celestial.json` 图集配置文件
- 当前的 `star.json` 精灵源已在 `ReadStarClient.onRegisterSpriteSources` 中通过事件直接注册，无需 `runData` 生成

如需添加新的数据生成器，修改 `ReadStarClient.gatherData()`：

```java
@SubscribeEvent
public static void gatherData(GatherDataEvent.Client event) {
    event.createProvider(CelestialSpriteSourceProvider::new);
    // 添加更多 provider: event.createProvider(MyProvider::new);
}
```

---

## 网络同步机制

天体系统数据通过服务端→客户端的网络包同步。

### 数据流

```
[服务端启动 / 资源重载]
        │
        ▼
  PlanetReloader.apply()
        │ 监听 data/readstar/celestial/*.json
        │ 读出 system.json → 缓存为 JSON 字符串
        │
        ├── 通过 PacketDistributor.sendToAllPlayers() 广播
        │
        ▼
  PlanetSystemPayload (custom packet)
        │  ID: "readstar:planet_system"
        │  编码: StreamCodec + UTF-8 字符串
        │
        ▼
  CelestialBodyManager.getInstance().initializeFromJson(jsonObject)
        │  递归解析天体树
        │  设置 hostStar、parent、children
        │
        ▼
  ExtractLevelRenderStateEvent
        │  updatePositions(gameTime)
        │  updateCurrentVec(daylightTime)
        │  → Observer = earth 的 CelestialBody 引用
```

### 自定义网络包

如需修改 Payload 格式，编辑 `PlanetSystemPayload.java`：

```java
public record PlanetSystemPayload(String jsonData) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlanetSystemPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("readstar", "planet_system"));

    public static final StreamCodec<ByteBuf, PlanetSystemPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlanetSystemPayload::jsonData,
            PlanetSystemPayload::new
        );
    // ...
}
```

---

## 本地化

语言文件位于 `assets/readstar/lang/`，支持中英双语：

| Key | 值 |
|-----|-----|
| `itemGroup.readstar` | 物品栏标签名称 |
| `item.readstar.astronomical_manuscript` | 物品名称 |
| `planet.readstar.sun` | 天体显示名称 |
| `planetdesc.readstar.sun` | 天体描述 |

添加新条目：

```json
{
  "planet.readstar.newbody": "New Body",
  "planetdesc.readstar.newbody": "Description of the new celestial body"
}
```

---

## 构建与运行

```bash
# 构建（含资源处理）
./gradlew build

# 运行客户端
./gradlew runClient

# 运行服务端
./gradlew runServer

# 数据生成（图集配置等）
./gradlew runData

# 清理
./gradlew clean
```

构建产物位于 `build/libs/readstar-*.jar`。

### 运行时调试

- F3+T：重载资源包。适用于修改了 JSON 配置或纹理文件后快速验证
- F3+A：重载区块。适用于天体位置更新后刷新
- 游戏日志位于 `run/logs/latest.log`

---

## 常见问题

### 重启后星星看不见 / 紫黑贴图

1. 检查 `star_base.png` 是否为 **32-bit RGBA**（非 8-bit 灰度）。Pillow 默认保存 8-bit 会导致 `NativeImage.read()` 解析失败
2. 确认 `custom/stars/stars.json` 包含有效的 `Stars` 数组
3. 按 F3+T 重载资源，查看日志中是否有 `Failed to load texture` 或 `Failed to read star data`

### Sun/Moon 贴图不显示

- 检查 `data/readstar/celestial/system.json` 中 earth 是否有 `hostStar`（自动查找 luminance > 0 的父天体）
- 检查 `ReadstarSkyRenderer.Observer` 是否在 `ExtractLevelRenderStateEvent` 中被正确设置
- 如果 face culling 导致不可见，确认 `renderSun()` 和 `renderMoon()` 中使用 `scale(..., -1.0F, ...)` 反转绕向

### 天体出现在错误方向

- 确认 `currentRotationVector` 在 `updateCurrentVec` 中正确计算（绕 `rotationAxis` 旋转 `noonRotationVector`）
- 检查 `renderSunMoonAndStars` 中的 `frameQuat` 是否正确构建（Y = currentRotationVector, Z = rotationAxis）

### 添加新天体后不显示

1. 在 `system.json` 中添加天体条目
2. 确认 JSON 语法正确（推荐用 IDE 验证）
3. 重新构建并启动客户端（服务端必须同步新 JSON）
4. 在 `findMoonBody()` 或渲染逻辑中引用该天体

### 修改纹理后 F3+T 不生效

`StarSpriteSource` 在资源包重载时重新运行，会重新读取 `stars.json` 和所有 PNG 底模。如果仍显示旧纹理：
1. 检查构建输出目录 `build/resources/` 是否已更新
2. 尝试 `./gradlew clean build` 后重新运行
