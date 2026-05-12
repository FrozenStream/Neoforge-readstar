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

# 数据生成（图集配置）
./gradlew runData
```

模组加载后，通过**数据包** `data/readstar/celestial/system.json` 定义天体系统，通过**资源包** `assets/readstar/custom/stars/stars.json` 定义恒星目录。

修改配置文件后需重新构建，或 F3+T 重载资源包（仅资源包内容有效，数据包需重启或 `/reload`）。

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

由数据生成器（`runData`）生成，位于 `src/generated/resources/assets/readstar/atlases/celestial.json`。添加新天体纹理需修改 `CelestialSpriteSourceProvider` 后重新生成。

---

## 网络同步

天体系统数据由服务端通过 `data/readstar/celestial/*.json` 加载，通过自定义网络包 `readstar:planet_system` 广播到所有客户端。客户端 `CelestialBodyManager.initializeFromJson()` 在收到数据包后解析并构建天体树。

---

## 构建

```bash
./gradlew build          # 构建 mod JAR
./gradlew runData        # 数据生成（图集配置）
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

---

© 2026 FrozenStream DeepSeek-V4-Flash. 基于 NeoForge 构建，遵循 MIT 许可证。
