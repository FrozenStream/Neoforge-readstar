package git.frozenstream.readstar.network;

import git.frozenstream.readstar.ReadStar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端向客户端发送消息的网络包
 */
public record ServerMessagePayload(String message) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ReadStar.MODID, "server_message");
    public static final Type<ServerMessagePayload> TYPE = new Type<>(ID);
    
    /**
     * StreamCodec 用于序列化和反序列化数据包
     * 使用 composite 方法自动处理 String 的编解码
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessagePayload> STREAM_CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,  // String 的编解码器
            ServerMessagePayload::message,  // 获取 message 字段
            ServerMessagePayload::new       // 构造函数引用
        );

    /**
     * 处理接收到的数据包（在客户端执行）
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 在客户端线程中显示消息
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player != null) {
                // 使用聊天组件显示消息
                Component messageComponent = Component.literal("§6[服务器] §f" + message);
                minecraft.player.sendSystemMessage(messageComponent);
            }
        }).exceptionally(e -> {
            ReadStar.LOGGER.error("处理服务器消息包时出错", e);
            return null;
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
