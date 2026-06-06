package git.frozenstream.readstar.network;

import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.elements.LaunchZone;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端流星发射器
 * 在 ServerTickEvent.Post 中按维度分别创建"启动区域"（LaunchZone），
 * 通过 MeteorZonePayload 只发送给对应维度的玩家，
 * 客户端 MeteorCollector 收到区域后自行生成流星实例
 */
public class MeteorLauncher {

    /** 启动区域创建间隔（tick） */
    private static final int ZONE_INTERVAL = 100;

    /** 启动区域持续时长（tick） */
    private static final int ZONE_DURATION = 200;

    /** 每一 tick 生成的流星平均数量 */
    private static final float ZONE_DENSITY = 0.1f;

    /** 按维度存储活跃启动区域 */
    private final Map<Identifier, List<LaunchZone>> activeZonesByDimension = new HashMap<>();

    private static MeteorLauncher INSTANCE;

    public MeteorLauncher() {
        INSTANCE = this;
    }

    public static MeteorLauncher getInstance() {
        return INSTANCE;
    }

    /**
     * 获取指定维度的所有活跃启动区域副本（用于新玩家同步）
     */
    public List<LaunchZone> getActiveZonesForDimension(Identifier dimensionId) {
        List<LaunchZone> zones = activeZonesByDimension.get(dimensionId);
        if (zones == null)
            return List.of();
        return new ArrayList<>(zones);
    }

    @SubscribeEvent
    public void onServerTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide())
            return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;

        long gameTime = serverLevel.getGameTime();
        // 通过资源注册表获取维度 ID
        Identifier dimensionId = serverLevel.dimension().identifier();

        // 按维度获取或初始化区域列表
        List<LaunchZone> zones = activeZonesByDimension.computeIfAbsent(dimensionId, k -> new ArrayList<>());

        // 移除已过期的区域
        zones.removeIf(z -> gameTime >= z.endTime());

        // // 按间隔生成新的启动区域
        // if (gameTime % ZONE_INTERVAL == 0) {
        //     float azimuth = (float) (Math.random() * 360);
        //     Vector3f direction = new Vector3f(
        //             (float) (Math.random() * 2 - 1),
        //             (float) (Math.random() * 2 - 1),
        //             (float) (Math.random() * 2 - 1)).normalize();
        //     float density = ZONE_DENSITY;
        //     long endTime = gameTime + ZONE_DURATION;

        //     LaunchZone zone = new LaunchZone(azimuth, direction, density, endTime, dimensionId);
        //     zones.add(zone);

        //     // 将新区域广播给该维度的所有玩家
        //     MeteorZonePayload payload = new MeteorZonePayload(
        //             zone.azimuth(), zone.direction(), zone.density(),
        //             zone.endTime(), zone.dimensionId());
        //     PacketDistributor.sendToPlayersInDimension(serverLevel, payload);
        // }
    }

    /**
     * 当玩家首次登录游戏时，向其发送当前维度的所有活跃启动区域
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer))
            return;

        Identifier dimId = serverPlayer.level().dimension().identifier();
        syncZonesToPlayer(serverPlayer, dimId);
    }

    /**
     * 当玩家切换维度时，向其发送新维度的所有活跃启动区域
     */
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer))
            return;

        syncZonesToPlayer(serverPlayer, event.getTo().identifier());
    }

    /**
     * 向指定玩家同步指定维度的所有活跃启动区域
     */
    private void syncZonesToPlayer(ServerPlayer player, Identifier dimensionId) {
        ReadStar.LOGGER.debug("Send Zones to player");
        List<LaunchZone> zones = getActiveZonesForDimension(dimensionId);
        if (zones.isEmpty())
            return;

        for (LaunchZone zone : zones) {
            PacketDistributor.sendToPlayer(
                    player,
                    new MeteorZonePayload(zone.azimuth(), zone.direction(), zone.density(), zone.endTime(),
                            zone.dimensionId()));
        }
    }
}
