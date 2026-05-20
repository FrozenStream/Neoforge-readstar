package git.frozenstream.readstar.elements;

import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 客户端流星管理器
 * 管理所有正在飞行的流星实例以及启动区域，单例模式
 * 区域由服务端发送，Collector 根据区域参数自行创建流星
 * 最大同时存在 50 颗流星，按维度区分
 */
public class MeteorCollector {

    /** 最大同时持有的流星数量 */
    public static final int MAX_METEORS = 50;

    public final ArrayDeque<Meteor> activeMeteors;

    /** 当前活跃的启动区域（由服务端下发） */
    private final List<LaunchZone> activeZones = new ArrayList<>();

    /** 客户端当前所在维度，用于过滤区域 */
    private Identifier currentDimension = Identifier.parse("minecraft:overworld");

    private static final MeteorCollector INSTANCE = new MeteorCollector();

    private MeteorCollector() {
        activeMeteors = new ArrayDeque<>(MAX_METEORS + 1);
    }

    public static MeteorCollector getInstance() {
        return INSTANCE;
    }

    /**
     * 添加一颗新流星（由本 Collector 根据 LaunchZone 自行创建时调用）
     * 若已达上限，丢弃
     */
    public void addMeteor(Meteor meteor) {
        if (meteor == null)
            return;
        if (activeMeteors.size() >= MAX_METEORS)
            return;
        activeMeteors.add(meteor);
    }

    public boolean isFull() {
        return activeMeteors.size() >= MAX_METEORS;
    }

    // ==================== 维度管理 ====================

    /**
     * 设置客户端当前维度
     * 如果维度发生变化，清空旧维度的所有流星球区域
     */
    public void setCurrentDimension(Identifier dimensionId) {
        if (!this.currentDimension.equals(dimensionId)) {
            this.currentDimension = dimensionId;
            activeMeteors.clear();
            activeZones.clear();
        }
    }

    // ==================== 启动区域管理 ====================

    /**
     * 添加一个启动区域，Collector 会定期从中生成流星
     * 仅当区域维度与当前客户端维度匹配时才接受
     */
    public void addZone(LaunchZone zone) {
        if (zone == null)
            return;
        if (!zone.dimensionId().equals(currentDimension))
            return;
        activeZones.add(zone);
    }

    /**
     * 清空所有启动区域
     */
    public void clearZones() {
        activeZones.clear();
    }

    // ==================== 每 tick 更新 ====================

    /**
     * 每 tick 调用：
     * 1. 移除已到达终点的流星
     * 2. 从活跃启动区域生成新流星
     * 3. 移除已过期的启动区域
     *
     * @param currentTick 当前游戏 tick
     */
    public void tick(long currentTick) {
        // 1. 移除已到达终点的流星
        Iterator<Meteor> it = activeMeteors.iterator();
        while (it.hasNext()) {
            Meteor meteor = it.next();
            long elapsed = currentTick - meteor.startTick();
            float totalTicks = meteor.getDuration();
            if (totalTicks > 0 && elapsed >= totalTicks) {
                it.remove();
            }
        }

        // 2. 从活跃启动区域生成新流星
        for (LaunchZone zone : activeZones) {
            if (currentTick >= zone.endTime())
                continue;

            // 根据 density 计算本 tick 应生成的流星数量（支持小数）
            int spawnCount = (int) Math.floor(zone.density());
            if (Math.random() < zone.density() - spawnCount) {
                spawnCount++;
            }

            for (int i = 0; i < spawnCount; i++) {
                if (isFull())
                    break;
                Meteor meteor = Meteor.randomFromZone(
                        zone, currentTick,
                        Meteor.DEFAULT_MIN_SPEED, Meteor.DEFAULT_MAX_SPEED,
                        Meteor.DEFAULT_MIN_DISTANCE, Meteor.DEFAULT_MAX_DISTANCE
                );
                activeMeteors.add(meteor);
            }
        }

        // 3. 移除已过期的启动区域
        activeZones.removeIf(z -> currentTick >= z.endTime());
    }

    // ==================== 查询 ====================

    /**
     * 获取当前活跃流星数量
     */
    public int getCount() {
        return activeMeteors.size();
    }

    /**
     * 获取当前活跃启动区域数量
     */
    public int getZoneCount() {
        return activeZones.size();
    }

    /**
     * 清空所有流星和启动区域
     */
    public void clear() {
        activeMeteors.clear();
        activeZones.clear();
    }
}
