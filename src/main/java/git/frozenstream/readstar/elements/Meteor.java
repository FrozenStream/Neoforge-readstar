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
    public static final float SHELL_THICKNESS = 50.0f;

    /** 球壳外径 */
    public static final float SHELL_OUTER_RADIUS = SHELL_INNER_RADIUS + SHELL_THICKNESS;

    /** 默认流星速度范围（m/tick） */
    public static final float DEFAULT_MIN_SPEED = 10f;
    public static final float DEFAULT_MAX_SPEED = 20f;

    /** 默认流星轨迹长度范围（m） */
    public static final float DEFAULT_MIN_DISTANCE = 200f;
    public static final float DEFAULT_MAX_DISTANCE = 400f;

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

    /**
     * 根据 LaunchZone 创建一颗随机流星
     * 起始位置受 azimuth 偏向（水平方向），终点在 direction 方向上
     *
     * @param zone        启动区域参数
     * @param gameTime    当前游戏 tick（生成的流星 startTick = gameTime + 40）
     * @param minSpeed    最小速度
     * @param maxSpeed    最大速度
     * @param minDistance 最小轨迹距离
     * @param maxDistance 最大轨迹距离
     * @return 流星实例
     */
    public static Meteor randomFromZone(LaunchZone zone, long gameTime,
                                         float minSpeed, float maxSpeed,
                                         float minDistance, float maxDistance) {
        long startTick = gameTime + 40; // 2 秒延迟
        float speed = minSpeed + (float) Math.random() * (maxSpeed - minSpeed);

        // 起始位置：偏向 azimuth 方向
        float azimuthRad = (float) Math.toRadians(zone.azimuth());
        Vector3f start = randomPositionWithAzimuthBias(azimuthRad, SHELL_OUTER_RADIUS);

        // 终点约束：距离在范围内 + 方向与 zone.direction 一致
        for (int i = 0; i < MAX_RETRIES; i++) {
            Vector3f end = randomPositionOnShell();
            float dist = start.distance(end);
            if (dist >= minDistance && dist <= maxDistance) {
                Vector3f actualDir = new Vector3f(end).sub(start).normalize();
                if (actualDir.dot(zone.direction()) > 0.3f) {
                    return new Meteor(start, end, speed, startTick);
                }
            }
        }

        // 退化为标准随机
        return random(minSpeed, maxSpeed, minDistance, maxDistance, startTick);
    }

    /**
     * 在球壳外径上生成一个随机位置，水平方向偏向指定 azimuth
     *
     * @param azimuthRad 偏向的方位角（弧度）
     * @param radius     球壳半径
     */
    private static Vector3f randomPositionWithAzimuthBias(float azimuthRad, float radius) {
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

        // 将水平分量（XZ）朝目标方位角混合 50%
        float currentAzimuth = (float) Math.atan2(dir.z, dir.x);
        float mixedAzimuth = currentAzimuth + (azimuthRad - currentAzimuth) * 0.5f;
        float horizontalLen = (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        dir.x = (float) (horizontalLen * (float) Math.cos(mixedAzimuth));
        dir.z = (float) (horizontalLen * (float) Math.sin(mixedAzimuth));
        dir.normalize();
        dir.mul(radius);
        return dir;
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
