# ReadStar 网络通信功能说明

## 概述

本项目已成功添加了服务端向客户端发送信息的网络通信功能，符合 NeoForge 26.1.1 版本的 API 规范。

## 新增文件

### 1. 网络包定义
**文件**: `src/main/java/git/frozenstream/readstar/network/ServerMessagePayload.java`

定义了服务端到客户端的消息数据包，包含：
- 消息内容的序列化和反序列化
- 客户端接收消息后的处理逻辑
- 在聊天栏显示来自服务器的消息

### 2. 网络包注册
**文件**: `src/main/java/git/frozenstream/readstar/network/ReadStarNetwork.java`

负责注册所有的网络包处理器：
- 使用 `RegisterPayloadHandlersEvent` 事件注册
- 定义频道 ID: `readstar:main`
- 协议版本控制，确保兼容性
- 标记为可选，避免与未安装模组的客户端冲突

### 3. 网络工具类
**文件**: `src/main/java/git/frozenstream/readstar/network/NetworkHelper.java`

提供便捷的网络包发送方法：
- `sendMessageToPlayer(ServerPlayer, String)` - 向指定玩家发送消息
- `sendMessageToAllPlayers(String)` - 向所有在线玩家发送消息
- 支持格式化字符串参数

### 4. 测试命令
**文件**: `src/main/java/git/frozenstream/readstar/command/TestMessageCommand.java`

提供游戏内测试命令：
- `/readstar message all <消息>` - 向所有玩家发送消息
- `/readstar message player <玩家> <消息>` - 向指定玩家发送消息

## 使用示例

### 1. 服务器启动时发送消息

已在 `ReadStar.java` 的 `onServerStarting` 事件中添加了示例：

```java
@SubscribeEvent
public void onServerStarting(ServerStartingEvent event) {
    // 向所有在线玩家发送欢迎消息
    NetworkHelper.sendMessageToAllPlayers("欢迎来到 Read Star 模组！");
}
```

### 2. 玩家登录时发送个人消息

已在 `ReadStar.java` 的 `onPlayerLogin` 事件中添加了示例：

```java
@SubscribeEvent
public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    var player = event.getEntity();
    if (player instanceof ServerPlayer serverPlayer) {
        NetworkHelper.sendMessageToPlayer(
            serverPlayer, 
            "你好 %s！感谢使用 Read Star 模组。", 
            serverPlayer.getName().getString()
        );
    }
}
```

### 3. 在其他地方发送消息

在你的代码中任意位置使用：

```java
// 向特定玩家发送消息
NetworkHelper.sendMessageToPlayer(player, "这是一条测试消息");

// 向所有玩家发送消息
NetworkHelper.sendMessageToAllPlayers("服务器即将重启！");

// 使用格式化字符串
NetworkHelper.sendMessageToPlayer(player, "玩家 %s 加入了游戏", playerName);
```

## 技术细节

### NeoForge 26.1.1 API 特性

1. **Identifier 替代 ResourceLocation**
   - 使用 `net.minecraft.core.Identifier` 代替旧的 `ResourceLocation`
   - 创建方式：`Identifier.fromNamespaceAndPath("modid", "path")`

2. **新的网络包注册系统**
   - 使用 `RegisterPayloadHandlersEvent` 事件
   - 通过 `PayloadRegistrar` 注册包处理器
   - 支持版本控制和可选标记

3. **CustomPacketPayload 接口**
   - 使用 record 类型定义数据包
   - 实现 `write()` 和 `read()` 方法进行序列化
   - 实现 `handle()` 方法处理接收到的数据

4. **PacketDistributor 发送消息**
   - `sendToPlayer()` - 发送给单个玩家
   - `sendToAllPlayers()` - 发送给所有玩家

## 测试方法

1. 启动 Minecraft 客户端（使用 gradlew runClient）
2. 进入游戏后，按 T 打开聊天框
3. 输入测试命令：
   ```
   /readstar message all 这是一条测试消息
   ```
4. 你应该在聊天栏看到带有 `[服务器]` 前缀的消息

## 注意事项

1. **线程安全**：网络包的 `handle()` 方法中使用 `context.enqueueWork()` 确保在主线程执行
2. **空指针检查**：发送消息前检查玩家对象是否为 null
3. **客户端检测**：消息显示只在客户端执行，服务端不会显示
4. **协议版本**：如果未来修改了包结构，记得更新 `PROTOCOL_VERSION`

## 扩展建议

你可以基于此框架添加更多类型的网络包：

1. **数据传输包**：传输自定义数据结构
2. **同步包**：同步服务端状态到客户端
3. **响应包**：客户端请求后服务端返回数据
4. **广播包**：向特定维度的所有玩家发送消息

每个新包需要：
- 创建新的 Payload record 类
- 在 `ReadStarNetwork` 中注册
- 在 `NetworkHelper` 中添加便捷的发送方法

## 兼容性

- ✅ NeoForge 26.1.1
- ✅ Minecraft 26.1.1 (1.21.1)
- ✅ Java 25
- ✅ 客户端和服务端都需要安装此模组
