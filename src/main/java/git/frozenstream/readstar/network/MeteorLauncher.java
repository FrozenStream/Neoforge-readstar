package git.frozenstream.readstar.network;

import git.frozenstream.readstar.elements.Meteor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * 服务端流星发射器
 * 在 ServerTickEvent.Post 中周期性生成流星数据，通过 MeteorPayload 发送给所有在线玩家
 * 生成的流星 startTick = 当前游戏 tick + 40 (2 秒延迟)
 */
public class MeteorLauncher {

    /** 流星生成间隔（tick） */
    private static final int SPAWN_INTERVAL = 10;

    /** 起始 tick 相对于当前 tick 的延迟（40 tick = 2 秒） */
    private static final int DELAY_TICKS = 40;

    /** 流星速度范围（m/tick） */
    private static final float MIN_SPEED = 50f;
    private static final float MAX_SPEED = 100f;

    /** 流星轨迹长度范围（m） */
    private static final float MIN_DISTANCE = 200f;
    private static final float MAX_DISTANCE = 500f;

    private long lastSpawnTick = 0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server == null)
            return;

        long gameTime = server.overworld().getGameTime();

        // 检查是否达到生成间隔
        if (gameTime - lastSpawnTick < SPAWN_INTERVAL)
            return;
        lastSpawnTick = gameTime;

        // startTick 延后 2 秒（40 tick）
        long startTick = gameTime + DELAY_TICKS;

        // 生成随机流星
        Meteor meteor = Meteor.random(MIN_SPEED, MAX_SPEED, MIN_DISTANCE, MAX_DISTANCE, startTick);

        // 发送给所有在线玩家
        Vector3f start = meteor.startPosition();
        Vector3f end = meteor.endPosition();
        PacketDistributor.sendToAllPlayers(new MeteorPayload(start, end, meteor.speed(), meteor.startTick()));
    }
}
