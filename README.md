# ReadStar — NeoForge 26.1 天文模组

基于真实天体力学与星表数据的 Minecraft 天象模组。支持行星轨道系统、恒星目录、三级光晕渲染、服务端同步的行星系统配置。

**作者**: FrozenStream

---

## 快速开始

```bash
# 构建
./gradlew build

# 运行客户端
./gradlew runClient
```

模组加载后，通过**数据包** `data/readstar/celestial/system.json` 定义天体系统，通过**资源包** `assets/readstar/custom/stars/stars.json` 定义恒星目录。

修改配置文件后需重新构建，或 F3+T 重载资源包（仅资源包内容有效，数据包需重启或 `/reload`）。

---

## 添加自定义月亮贴图

ReadStar 的核心亮点之一：**为任意天体添加带 8 种月相的自定义纹理，只需放置 PNG 文件，无需修改任何 Java 代码**。

### 一、准备工作目录

在你的资源包（或模组 JAR）中创建以下目录结构：

```
assets/<命名空间>/textures/environment/celestial/moons/<天体名称>/
```

- `<命名空间>`: 你的 mod ID，如 `readstar`、`mymod` 或 `minecraft`。可任选，不会影响渲染。
- `<天体名称>`: **必须与 `system.json` 中定义的天体名称完全一致（小写）**。例如你在数据包中定义了一个 `"Jupiter"`，则目录名应为 `jupiter`。

### 二、准备 8 张月相贴图

每个天体目录下需要放置 **恰好 8 张 PNG**，文件名对应月相枚举 `MoonPhase` 的固定值：

| 文件名 | 月相 | 说明 |
|--------|------|------|
| `full_moon.png` | 满月 | 整个面被照亮 |
| `waning_gibbous.png` | 亏凸月 | 满月后逐渐变暗 |
| `third_quarter.png` | 下弦月 | 左半亮 |
| `waning_crescent.png` | 残月 | 新月前夕 |
| `new_moon.png` | 新月 | 背光面 |
| `waxing_crescent.png` | 蛾眉月 | 新月后初现 |
| `first_quarter.png` | 上弦月 | 右半亮 |
| `waxing_gibbous.png` | 盈凸月 | 逐渐接近满月 |

> 8 张图**缺一不可**，缺失的文件会导致渲染时对应的月相显示为紫黑贴图。月相的名称顺序来自 Minecraft 原版 `MoonPhase` 枚举，不可更改。

### 三、贴图要求

| 规格 | 要求 |
|------|------|
| 格式 | PNG（RGBA 32-bit，不支持 8-bit 灰度） |
| 尺寸 | 建议 16×16 或 32×32（最终在图集中被拼接，过大尺寸无意义） |
| 边缘 | 贴图外的区域应为透明（alpha=0），以便月相圆盘形状正确渲染 |
| 着色 | 贴图本身应包含颜色信息（非纯白底模），渲染时不会额外染色 |

### 四、完整示例

假设你想添加木星（Jupiter）作为可观测天体，需要做两件事：

#### 步骤 1：在 `system.json` 定义天体轨道

```json
{
  "System": {
    "Sun": { "...": "..." },
    "Jupiter": {
      "mass": 1.898e27,
      "radius": 6.991e7,
      "luminance": 0,
      "axis": [0.0, 0.0, 0.0],
      "orbit": { "semiMajorAxis": 7.785e11, "eccentricity": 0.0484, "inclination": 0.0228, "argumentOfPeriapsis": 0.0, "longitudeOfAscendingNode": 1.754, "initialMeanAnomaly": 0.0 },
      "children": {}
    }
  }
}
```

#### 步骤 2：放置 8 张月相贴图

```
assets/readstar/textures/environment/celestial/moons/jupiter/
├── full_moon.png
├── waning_gibbous.png
├── third_quarter.png
├── waning_crescent.png
├── new_moon.png
├── waxing_crescent.png
├── first_quarter.png
└── waxing_gibbous.png
```

#### 原理

1. **图集自动扫描**：`textures/environment/celestial/` 目录下的所有 `.png` 由 Minecraft 的图集系统自动发现并拼入 `celestial` 图集
2. **渲染器自动发现**：`ReadstarSkyRenderer` 的构造函数通过 `ResourceManager.listResources()` 扫描 `moons/` 子目录，按子目录名自动分组构建 GPU 缓冲
3. **运行时匹配**：每帧渲染时，遍历 `CelestialBodyManager` 中所有天体，通过 `body.name` 在缓冲映射表中查找对应纹理组，找到则自动渲染其月相

全程**零配置、零代码**。

### 为其他 Mod 的天体添加纹理

如果你开发的是另一个 Mod，想给 `readstar` 的某个天体（如 Earth）替换纹理，只需在你的资源包中放置同名文件：

```
assets/yourmod/textures/environment/celestial/moons/earth/
├── full_moon.png   ← 替换地球的满月贴图
├── ...
```

Minecraft 资源包的优先级系统会自动决定哪个文件胜出（你的 mod 优先级高于 `readstar` 时，你的文件被使用）。

### 贴图文件未生效？故障排查

| 现象 | 检查点 |
|------|--------|
| 某个月相显示紫黑贴图 | 该月相对应的 `.png` 文件是否缺失；文件名是否拼写正确（严格小写） |
| 所有月相都不显示 | 目录名是否与 `system.json` 中的天体名称小写一致？`moons/` 子目录名是否正确？ |
| 天体未渲染 | 该天体是否有 `hostStar`（`system.json` 中该天体或其祖先的 `luminance > 0`）？ |
| F3+T 后不更新 | 纹理修改属于资源包变更，F3+T 即可刷新；如果不行，重新构建 |
| 贴图显示但方向不对 | ReadStar 的 UV 映射已与原版月相逻辑对齐，无需额外处理 |

---

## 数据包 — 天体系统配置

**路径**: `data/readstar/celestial/system.json`

数据包定义了天体层次结构与轨道参数。可放在 `saves/<世界>/datapacks/<你的数据包>/data/readstar/celestial/system.json` 下替换默认配置，或放在服务端的 `world/datapacks/` 下。

### 结构

```json
{
  "System": {
    "<天体名称>": {
      "mass": <double>,           // 质量（kg），0 则固定于原点
      "radius": <double>,         // 半径（m），影响渲染视大小
      "luminance": <int>,         // 自发光亮度（0~15），>0 被识别为恒星
      "axis": [<x>, <y>, <z>],    // 自转轴方向向量，全零则默认 (0,0,-1)
      "orbit": {
        "semiMajorAxis": <double>,           // 半长轴（m），0 表示不公转
        "eccentricity": <double>,            // 偏心率（0=正圆）
        "inclination": <double>,             // 轨道倾角（弧度）
        "argumentOfPeriapsis": <double>,     // 近心点幅角（弧度）
        "longitudeOfAscendingNode": <double>, // 升交点经度（弧度）
        "initialMeanAnomaly": <double>       // 初始平近点角（弧度）
      },
      "children": {
        "<子天体名称>": { ... }   // 递归同结构，嵌套深度不限
      }
    }
  }
}
```

### 参数说明

| 字段 | 说明 |
|------|------|
| `mass` | 轨道力学计算核心参数。设为 0 的天体固定在原点（父节点位置） |
| `radius` | 渲染时的天体大小通过 `max(1.024, radius / distance × 2000)` 计算，距离越远越小 |
| `luminance` | 自动影响渲染：`> 0` 的天体被识别为恒星，同系统内其他天体自动查找最近的发光祖先作为 `hostStar` |
| `inclination`, `longitudeOfAscendingNode` | 共同决定轨道平面方向。`inclination=0` 时轨道在参考平面内 |
| `eccentricity` | 偏心率 = 0 为纯圆轨道，> 0 为椭圆 |
| `initialMeanAnomaly` | 决定 t=0 时刻天体在轨道上的起始位置 |

名称大小写不敏感（内部统一转为小写）。`children` 为空对象 `{}` 时表示无子天体。

### 完整示例

下例定义了一个三层天体系统：太阳（中心恒星）→ 地球 + 火星 → 月球（地球的卫星）。数值采自真实太阳系数据。

```json
{
  "System": {
    "Sun": {
      "mass": 1.989e30,
      "radius": 6.957e8,
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
          "mass": 5.972e24,
          "radius": 6.371e6,
          "luminance": 0,
          "axis": [0.0, 0.0, 0.0],
          "orbit": {
            "semiMajorAxis": 1.496e11,
            "eccentricity": 0.0167,
            "inclination": 0.0,
            "argumentOfPeriapsis": 1.796,
            "longitudeOfAscendingNode": 0.0,
            "initialMeanAnomaly": 6.240
          },
          "children": {
            "Moon": {
              "mass": 7.342e22,
              "radius": 1.737e6,
              "luminance": 0,
              "axis": [0.0, 0.0, 0.5],
              "orbit": {
                "semiMajorAxis": 3.844e8,
                "eccentricity": 0.0549,
                "inclination": 0.0899,
                "argumentOfPeriapsis": 0.0,
                "longitudeOfAscendingNode": 0.0,
                "initialMeanAnomaly": 0.0
              },
              "children": {}
            }
          }
        },
        "Mars": {
          "mass": 6.417e23,
          "radius": 3.390e6,
          "luminance": 0,
          "axis": [0.0, 0.0, 0.0],
          "orbit": {
            "semiMajorAxis": 2.279e11,
            "eccentricity": 0.0934,
            "inclination": 0.0323,
            "argumentOfPeriapsis": 0.0,
            "longitudeOfAscendingNode": 0.865,
            "initialMeanAnomaly": 0.0
          },
          "children": {}
        }
      }
    }
  }
}
```

### 添加太阳纹理

太阳纹理不同于月相——它只有单张贴图。放在：

```
assets/<命名空间>/textures/environment/celestial/suns/<名称>.png
```

默认是 `readstar:textures/environment/celestial/suns/white_sun.png`。渲染器自动扫描 `suns/` 目录，但不参与月相计算，仅作为恒星的发光贴图渲染。

### 继承规则

- `hostStar` 自动向上递归查找最近的自发光天体
- 无自发光祖先的天体不会渲染对应的日照效果
- 位置计算：`position = parent.position + orbit(parent.mass, t)`
- 根节点 `Root` 固定于 `(0,0,0)`

### 日/月相计算

```
gameTime        → updatePositions(t)    → 轨道公转
daylightTime    → updateCurrentVec(t)   → 自转天顶更新
```

- `gameTime`: 自世界创建以来的总 tick 数。影响天体轨道位置
- `daylightTime`: 维度的日光周期时间（0~24000）。影响行星自转带来的天顶方向变化

月相由观测者-卫星-恒星的几何关系自动计算，完整映射 8 种月相（亏盈由轨道法线方向区分）。

---

## 资源包 — 恒星目录

**路径**: `assets/readstar/custom/stars/stars.json`

资源包定义了渲染到天空中的恒星列表。可通过资源包完全替换。

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

| 字段 | 说明 |
|------|------|
| `name` | 标识符，无运行时作用 |
| `position` | 单位球面上的方向向量 `[x,y,z]`，渲染时归一化并缩放到 100 单位 |
| `type` | 0~11 的光谱分类，图集中精灵的颜色由此索引 |
| `Vmag` | 视星等。`< 0.5` 高级光晕，`< 1.5` 中级光晕，`< 2.0` 低级光晕，`≥ 2.0` 仅核心 |
| `color` | ARGB 颜色值，作为图集精灵生成的 key |

恒星亮度映射到顶点 Alpha 通道：`clamp((14 - Vmag) / 15, 0.4, 1.0) × 255`。

## 资源包 — 精灵与图集

### 星星图集

图集源通过 `RegisterSpriteSourcesEvent` 注册，类型 `readstar:star`，在 `assets/readstar/atlases/star.json` 声明：

```json
{
  "sources": [ { "type": "readstar:star" } ]
}
```

运行时从 `stars.json` 读取所有 `color` 值，读取以下底模纹理逐像素染色生成精灵：

| 底模文件 | 路径 | 作用 |
|----------|------|------|
| `star_base.png` | `textures/environment/star/` | 核心贴图底模（32×32，RGBA） |
| `star_glow_low.png` | 同上 | 低级光晕底模 |
| `star_glow_med.png` | 同上 | 中级光晕底模 |
| `star_glow_high.png` | 同上 | 高级光晕底模 |

每种颜色生成 4 个子图：`color_{color}`、`glow_low_{color}`、`glow_med_{color}`、`glow_high_{color}`。

底模制作要求：
- RGBA 32-bit PNG（不可用 8-bit 灰度，否则 `NativeImage.read()` 解析失败）
- 32×32 像素
- 边缘纯黑 `rgb(0,0,0)` 以融入背景
- 光晕底模的核心亮度通过采样中心 8×8 区域 × 0.35 系数压制，确保远暗于核心

### 天体图集

`assets/readstar/atlases/celestial.json` 使用 Minecraft 原生的 `minecraft:directory` 源类型，自动扫描所有命名空间下的 `textures/environment/celestial/` 目录。**无需额外配置，放文件即可**。

---

## 网络同步

天体系统数据由服务端通过 `data/readstar/celestial/*.json` 加载，通过自定义网络包 `readstar:planet_system` 广播到所有客户端。客户端 `CelestialBodyManager.initializeFromJson()` 在收到数据包后解析并构建天体树。

---

## 构建

```bash
./gradlew build          # 构建 mod JAR
./gradlew runClient      # 运行客户端
./gradlew runServer      # 运行服务端
```

构建产物：`build/libs/readstar-*.jar`

### 故障排查

| 现象 | 检查点 |
|------|--------|
| 星星显示紫黑贴图 | `star_base.png` 是否为 32-bit RGBA |
| 数据包未生效 | `/reload` 或重启服务端 |
| 资源包未更新 | F3+T 重载资源包，或重新构建 |
| 天体未显示 | 该天体在 `system.json` 中是否有 `hostStar`（自身或祖先 luminance>0）？ |
| 月相总是新月 | 检查 8 个 PNG 文件名是否完全匹配，包括下划线 |

---

© 2026 FrozenStream. 基于 NeoForge 构建，遵循 MIT 许可证。
