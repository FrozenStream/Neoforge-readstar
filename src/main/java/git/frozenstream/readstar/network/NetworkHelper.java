package git.frozenstream.readstar.network;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 网络工具类 - 提供便捷的网络包发送方法
 */
public class NetworkHelper {

    /**
     * 向指定玩家发送消息
     *
     * @param player  目标玩家
     * @param message 要发送的 Component 消息
     */
    public static void sendMessageToPlayer(ServerPlayer player, Component message) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new ServerMessagePayload(message));
        }
    }

    /**
     * 向所有在线玩家发送消息
     *
     * @param message 要发送的 Component 消息
     */
    public static void sendMessageToAllPlayers(Component message) {
        PacketDistributor.sendToAllPlayers(new ServerMessagePayload(message));
    }
}
