package git.frozenstream.readstar.elements;

import org.joml.Vector3f;

/**
 * 流星数据结构（record）
 * 表示一颗在球壳空间中飞行的流星，包含起始位置、结束位置、速度和起始 tick
 * 位置限定在指定内径和厚度的球壳上（内径 500，厚度 500，即距离原点 500~1000）
 *
 * @param startPosition 起始位置（球壳上的点）
 * @param endPosition   结束位置（球壳上的点）
 * @param speed         飞行速度（m/Tick）
 * @param startTick     流星开始飞行的游戏 tick
 */
public record Meteor(Vector3f startPosition, Vector3f endPosition, float speed, long startTick) {

    /** 球壳内径 */
    public static final float SHELL_INNER_RADIUS = 500.0f;

    /** 球壳厚度 */
    public static final float SHELL_THICKNESS = 100.0f;

    /** 球壳外径 */
    public static final float SHELL_OUTER_RADIUS = SHELL_INNER_RADIUS + SHELL_THICKNESS;

    /**
     * 紧凑构造器，对 Vector3f 做防御性复制
     */
    public Meteor {
        startPosition = new Vector3f(startPosition);
        endPosition = new Vector3f(endPosition);
    }

    // ==================== 球壳位置生成 ====================

    /**
     * 在球壳上生成一个随机位置（半径 500~1000）
     */
    public static Vector3f randomPositionOnShell() {
        return randomPositionWithRadius(SHELL_INNER_RADIUS, SHELL_THICKNESS);
    }

    /**
     * 在半径为 [baseRadius, baseRadius + thickness] 的球壳上生成随机位置
     */
    private static Vector3f randomPositionWithRadius(float baseRadius, float thickness) {
        Vector3f dir = new Vector3f(
                (float) (Math.random() * 2 - 1),
                (float) (Math.random() * 2 - 1),
                (float) (Math.random() * 2 - 1)
        );
        while (dir.lengthSquared() < 1e-8f) {
            dir.set(
                    (float) (Math.random() * 2 - 1),
                    (float) (Math.random() * 2 - 1),
                    (float) (Math.random() * 2 - 1)
            );
        }
        dir.normalize();
        float radius = baseRadius + (float) Math.random() * thickness;
        dir.mul(radius);
        return dir;
    }

    /** 随机生成流星时的默认最大重试次数 */
    private static final int MAX_RETRIES = 200;

    // ==================== 随机流星工厂 ====================

    /**
     * 创建一个随机流星，起始位置在外球壳上，限制起始和结束位置之间的距离在指定范围内
     *
     * @param minSpeed     最小速度（m/Tick）
     * @param maxSpeed     最大速度（m/Tick）
     * @param minDistance  起始到结束的最小距离
     * @param maxDistance  起始到结束的最大距离
     * @param startTick    流星开始飞行的游戏 tick
     * @return 随机流星实例
     */
    public static Meteor random(float minSpeed, float maxSpeed, float minDistance, float maxDistance, long startTick) {
        Vector3f start = randomPositionWithRadius(SHELL_OUTER_RADIUS, 0f);
        float speed = minSpeed + (float) Math.random() * (maxSpeed - minSpeed);

        for (int i = 0; i < MAX_RETRIES; i++) {
            Vector3f end = randomPositionOnShell();
            float dist = start.distance(end);
            if (dist >= minDistance && dist <= maxDistance) {
                return new Meteor(start, end, speed, startTick);
            }
        }

        // 超出重试次数 —— 选择最接近约束中心的终点
        Vector3f bestEnd = randomPositionOnShell();
        float bestDist = start.distance(bestEnd);
        float targetDist = (minDistance + maxDistance) / 2f;
        for (int i = 0; i < MAX_RETRIES; i++) {
            Vector3f end = randomPositionOnShell();
            float dist = start.distance(end);
            if (dist >= minDistance && dist <= maxDistance) {
                return new Meteor(start, end, speed, startTick);
            }
            if (Math.abs(dist - targetDist) < Math.abs(bestDist - targetDist)) {
                bestEnd = end;
                bestDist = dist;
            }
        }
        return new Meteor(start, bestEnd, speed, startTick);
    }

    // ==================== 流星运动计算 ====================

    /**
     * 根据经过的 tick 数插值计算流星当前位置
     *
     * @param currentTick 从 startTick 开始经过的 tick 数
     * @return 当前位置
     */
    public Vector3f getPositionAtTime(long currentTick) {
        return new Vector3f(startPosition).lerp(endPosition, getCurrentProgress(currentTick));
    }

    /**
     * 获取从起始到结束所需的总 tick 数
     */
    public float getDuration() {
        float totalDistance = startPosition.distance(endPosition);
        if (speed <= 0f || totalDistance <= 0f) return 0f;
        return totalDistance / speed;
    }

    public float getCurrentProgress(long currentTick) {
        float duration = getDuration();
        if (duration <= 0f) return 0f;
        return Math.min((currentTick - startTick) / duration, 1.0f);
    }
}
