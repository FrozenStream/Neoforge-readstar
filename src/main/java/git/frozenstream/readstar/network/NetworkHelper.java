package git.frozenstream.readstar.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 网络工具类 - 提供便捷的网络包发送方法
 */
public class NetworkHelper {
    
    /**
     * 向指定玩家发送消息
     * 
     * @param player 目标玩家
     * @param message 要发送的消息内容
     */
    public static void sendMessageToPlayer(ServerPlayer player, String message) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new ServerMessagePayload(message));
        }
    }

    /**
     * 向所有在线玩家发送消息
     * 
     * @param message 要发送的消息内容
     */
    public static void sendMessageToAllPlayers(String message) {
        PacketDistributor.sendToAllPlayers(new ServerMessagePayload(message));
    }

    /**
     * 向指定玩家发送消息（支持格式化）
     * 
     * @param player 目标玩家
     * @param format 消息格式
     * @param args 格式化参数
     */
    public static void sendMessageToPlayer(ServerPlayer player, String format, Object... args) {
        sendMessageToPlayer(player, String.format(format, args));
    }

    /**
     * 向所有在线玩家发送消息（支持格式化）
     * 
     * @param format 消息格式
     * @param args 格式化参数
     */
    public static void sendMessageToAllPlayers(String format, Object... args) {
        sendMessageToAllPlayers(String.format(format, args));
    }
}
