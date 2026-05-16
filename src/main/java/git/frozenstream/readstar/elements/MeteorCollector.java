package git.frozenstream.readstar.elements;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * 客户端流星管理器
 * 管理所有正在飞行的流星实例，单例模式
 * 最大同时存在 50 颗流星，超出时丢弃最早加入的
 */
public class MeteorCollector {

    /** 最大同时持有的流星数量 */
    public static final int MAX_METEORS = 50;

    public final ArrayDeque<Meteor> activeMeteors;

    private static final MeteorCollector INSTANCE = new MeteorCollector();

    private MeteorCollector() {
        activeMeteors = new ArrayDeque<>(MAX_METEORS + 1);
    }

    public static MeteorCollector getInstance() {
        return INSTANCE;
    }

    /**
     * 添加一颗新流星
     * 若已达上限，丢弃最早加入的流星
     */
    public void addMeteor(Meteor meteor) {
        if (meteor == null) return;
        if (activeMeteors.size() >= MAX_METEORS) {
            activeMeteors.pollFirst();
        }
        activeMeteors.offerLast(meteor);
    }

    /**
     * 每 tick 调用，移除已到达终点的流星
     *
     * @param currentTick 当前游戏 tick
     */
    public void tick(long currentTick) {
        Iterator<Meteor> it = activeMeteors.iterator();
        while (it.hasNext()) {
            Meteor meteor = it.next();
            long elapsed = currentTick - meteor.startTick();
            float totalTicks = meteor.getDuration();
            if (totalTicks > 0 && elapsed >= totalTicks) {
                it.remove();
            }
        }
    }

    /**
     * 获取当前活跃流星数量
     */
    public int getCount() {
        return activeMeteors.size();
    }

    /**
     * 清空所有流星
     */
    public void clear() {
        activeMeteors.clear();
    }
}
