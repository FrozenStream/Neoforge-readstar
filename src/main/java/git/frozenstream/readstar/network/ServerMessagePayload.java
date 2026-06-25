package git.frozenstream.readstar.network;

import git.frozenstream.readstar.ReadStar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端向客户端发送消息的网络包
 */
public record ServerMessagePayload(Component message) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ReadStar.MODID, "server_message");
    public static final Type<ServerMessagePayload> TYPE = new Type<>(ID);

    /**
     * StreamCodec：将 Component 序列化为 JSON 字符串传输
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessagePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.STREAM_CODEC,
                    ServerMessagePayload::message,
                    ServerMessagePayload::new);

    /**
     * 处理接收到的数据包（在客户端执行）
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(message);
            }
        }).exceptionally(e -> {
            ReadStar.LOGGER.error("Error handling server message packet", e);
            return null;
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
