# AGENTS.md

# 无论如何，Agent应该使用中文和用户对话

## 项目概述

本项目是一个基于 NeoForge 26.1.1 的 Minecraft Mod，开发代号为 "readstar"。

## 目录结构

```
Neoforge-readstar-26.1.1/
├── build.gradle          # Gradle 构建配置
├── gradle.properties     # Gradle 属性配置
├── settings.gradle       # Gradle 设置
├── src/
│   └── main/
│       ├── java/         # Java 源代码
│       └── resources/    # 资源文件（纹理、模型、语言文件等）
├── run/                  # 游戏运行目录
│   ├── config/           # 配置文件
│   ├── logs/             # 日志文件
│   ├── mods/             # 模组文件
│   └── screenshots/      # 截图
└── build/                # 构建输出
    ├── libs/             # 生成的 JAR 文件
    └── classes/          # 编译后的类文件
```

## 开发环境

- **Minecraft 版本**: 1.21.x (NeoForge 26.1.1)
- **构建工具**: Gradle
- **开发语言**: Java

## 常见问题

### F3+T 重新加载资源包后出现错误贴图

如果在游戏中按 F3+T 重新加载资源包后出现 `minecraft:missingno` 贴图，可能的原因包括：

1. **纹理文件缺失或路径错误**
   - 确保纹理文件位于 `src/main/resources/assets/<modid>/textures/` 目录下
   - 检查纹理文件名是否与代码/JSON 中引用的一致（注意大小写）

2. **模型 JSON 文件错误**
   - 检查 `src/main/resources/assets/<modid>/models/` 下的 JSON 文件
   - 确保 `parent` 字段引用的父模型存在
   - 确保 `textures` 字段中的路径正确

3. **语言文件缺失**
   - 确保 `src/main/resources/assets/<modid>/lang/` 下有对应语言的 `.json` 文件

4. **Mod ID 不一致**
   - 确保代码中注册的 Mod ID 与资源文件中的路径一致

### 调试步骤

1. 检查 `run/screenshots/debug/` 目录下的纹理图集文件
2. 查看游戏日志 (`run/logs/`) 中的错误信息
3. 确认资源文件已正确复制到构建输出中

## 构建与运行

```bash
# 构建项目
./gradlew build

# 运行客户端
./gradlew runClient

# 运行服务端
./gradlew runServer
```

## 注意事项

- 修改资源文件后需要重新构建或按 F3+T 刷新
- 确保纹理尺寸为 2 的幂次方（16x16, 32x32, 64x64 等）
- JSON 文件使用 UTF-8 编码
