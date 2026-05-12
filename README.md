# Read Star — 天体模拟天空盒 Mod

基于 NeoForge 26.1.1 (Minecraft 1.21.x) 的自定义天空盒与天体模拟系统。

## 设计理念

```
gametime → 星系内的所有公转位置
daytime  → 行星的所有自转位置
```

- **`gametime`** (世界总游戏刻) 用于开普勒轨道计算，控制行星公转位置
- **`daytime`** (维度日光时间 0~24000) 用于行星自转，控制观测者天顶方向与昼夜

不设计纬度系统，因为纬度导致昼夜长短不等，与原版 Minecraft 昼夜周期兼容性不佳。

## 功能特性

### 天体物理系统
- 树形层级天体系统（恒星 → 行星 → 卫星），通过 JSON 配置文件定义
- 完整开普勒轨道参数：半长轴、偏心率、轨道倾角、近心点幅角、升交点经度、初始平近点角
- 牛顿迭代法求解 Kepler 方程（`E − e·sin(E) = M`）
- 包含真实物理数据示例：太阳、地球、月球、火星

### 自定义天空渲染
- 完整替换原版天空渲染管线
- 天球坐标系：观测者天顶方向 → Y 轴，自转轴 → Z 轴
- 太阳、月亮根据轨道位置在世界空间中渲染
- 基于天体几何的月相计算（观测者-月球-恒星夹角）
- 三次函数 `1−(1−t)³` 平滑星光亮度过渡
- 自定义渲染管线 `readstar:star_textured`，支持逐星颜色与透明度

### 真实恒星渲染
- 从 `stars.json` 加载真实恒星数据（约 4000+ 颗星）
- 使用视星等 (Vmag) 控制亮度
- 12 色自适应纹理图集，运行时按颜色生成精灵
- 亮星（Vmag < 2.0）三级光晕效果（低/中/高）
- 光晕底模使用预生成的灰度图 + 色调染色

### 网络同步
- 服务端从 `data/readstar/celestial/system.json` 加载天体配置
- 通过自定义数据包广播至所有客户端
- 缓存机制：玩家登录时发送缓存配置
- 服务端 → 客户端消息系统（`/readstar message` 命令）

### 其他
- 创造模式标签页 "Read Star"
- 示例方块、示例食物物品
- 天文手稿物品（带有 GUI）
- NeoForge 配置界面支持

## 构建与运行

### 前置条件
- Java 25+
- Git

### 构建
```bash
./gradlew build
```
输出：`build/libs/readstar-1.0.0.jar`

### 运行客户端
```bash
./gradlew runClient
```

### 运行服务端
```bash
./gradlew runServer
```

### 数据生成
```bash
./gradlew runData
```
输出：`src/generated/resources/`

## 项目结构

```
src/
├── main/
│   ├── java/git/frozenstream/readstar/
│   │   ├── ReadStar.java              # 主模组入口
│   │   ├── ReadStarClient.java        # 客户端初始化
│   │   ├── Config.java                # 配置
│   │   ├── command/
│   │   │   └── TestMessageCommand.java
│   │   ├── elements/
│   │   │   ├── CelestialBody.java     # 天体模型
│   │   │   ├── CelestialBodyManager.java  # 天体管理
│   │   │   ├── Orbit.java             # 开普勒轨道
│   │   │   ├── Star.java              # 恒星数据
│   │   │   └── StarManager.java       # 恒星查询
│   │   ├── network/
│   │   │   ├── NetworkHelper.java
│   │   │   ├── PlanetReloader.java    # JSON 资源加载
│   │   │   ├── PlanetSystemPayload.java
│   │   │   ├── ReadStarNetwork.java   # 数据包注册
│   │   │   └── ServerMessagePayload.java
│   │   ├── skybox/
│   │   │   ├── FogEvent.java
│   │   │   ├── ReadStarCloudsRenderer.java
│   │   │   ├── ReadstarSkyboxRenderer.java  # 天空盒桥梁
│   │   │   └── ReadstarSkyRenderer.java     # 主天空渲染器
│   │   └── sprite/
│   │       ├── CelestialSpriteSourceProvider.java
│   │       └── StarSpriteSource.java  # 运行时恒星纹理生成
│   └── resources/
│       ├── assets/readstar/
│       │   ├── atlases/star.json
│       │   ├── custom/stars/stars.json    # 真实恒星目录
│       │   ├── lang/en_us.json
│       │   ├── lang/zh_cn.json
│       │   └── textures/...
│       └── data/readstar/celestial/system.json  # 天体系统配置
```

## 配置文件格式

### 天体系统 (`data/readstar/celestial/system.json`)

```json
{
  "System": {
    "<天体名>": {
      "mass":       <质量，千克>,
      "radius":     <半径，米>,
      "luminance":  <自发光亮度 0~15>,
      "axis":       [<自转轴 X>, <Y>, <Z>],
      "orbit": {
        "semiMajorAxis":           <半长轴，米>,
        "eccentricity":            <偏心率>,
        "inclination":             <轨道倾角，弧度>,
        "argumentOfPeriapsis":     <近心点幅角，弧度>,
        "longitudeOfAscendingNode":<升交点经度，弧度>,
        "initialMeanAnomaly":      <初始平近点角，弧度>
      },
      "children": {
        ...  // 子天体
      }
    }
  }
}
```

## 数据流

```
[服务端启动]
    ↓
PlanetReloader.onResourceManagerReload()
    ↓
读取 system.json → CelestialBodyManager.initializeFromJson()
    ↓
发送 PlanetSystemPayload 至所有客户端
    ↓
[客户端每 tick]
    ↓
ExtractLevelRenderStateEvent
    ├─ getGameTime()           → updatePositions()     ← 公转
    └─ getDefaultClockTime()   → updateCurrentVec()    ← 自转
    ↓
ReadstarSkyRenderer.renderSunMoonAndStars()
    └─ 天球旋转 → 渲染太阳/月亮/恒星
```

## 许可证

保留所有权利。
