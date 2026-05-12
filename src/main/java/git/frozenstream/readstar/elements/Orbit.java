package git.frozenstream.readstar.elements;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 天体轨道参数记录类
 * 用于描述天体的椭圆轨道及其在空间中的位置和方向
 *
 * @param semiMajorAxis             轨道半长轴（米），定义轨道的大小
 * @param eccentricity              轨道偏心率（0-1之间），定义轨道的形状（0为圆形，接近1为扁椭圆）
 * @param inclination               轨道倾角（弧度），轨道平面相对于参考平面的倾斜角度
 * @param argumentOfPeriapsis       近心点幅角（弧度），从升交点到近心点的角度
 * @param longitudeOfAscendingNode  升交点经度（弧度），从参考方向到升交点的角度
 * @param initialMeanAnomaly        初始平近点角（弧度），t=0时刻的平近点角，定义天体在轨道上的初始位置
 */
public record Orbit(
        double semiMajorAxis,
        double eccentricity,
        double inclination,
        double argumentOfPeriapsis,
        double longitudeOfAscendingNode,
        double initialMeanAnomaly
) {
    private static final double G = 6.67430e-11;

    /**
     * 获取平近点角的角速度（平均运动）
     * 基于开普勒第三定律：n = √(GM/a³)
     *
     * @param centralMass   中心天体的质量（千克），例如恒星质量（当计算行星轨道时）或行星质量（当计算卫星轨道时）
     * @param a             轨道半长轴（米）
     * @return              平均运动角速度（弧度/秒）
     */
    private static double mean_anomaly_angular_velocity(double centralMass, double a) {
        return Math.sqrt(G * centralMass / Math.pow(a, 3));
    }

    /**
     * 使用牛顿迭代法解 Kepler 方程：M = E - eccentricity * sin(E)
     *
     * @param M 平近点角
     * @param e 轨道偏心率
     */
    public static double solveKepler(double M, double e) {
        double E;
        if (e < 0.8) E = M; // 初始猜测
        else E = Math.PI; // 高偏心率时使用 π

        for (int i = 0; i < 100; i++) {
            double f = E - e * Math.sin(E) - M;
            double df = 1 - e * Math.cos(E);
            double delta = f / df;
            E -= delta;
            if (Math.abs(delta) < 1e-5) break;
        }
        return E;
    }

    /**
     * 计算星体在轨道上的位置
     * 通过解开普勒方程并使用轨道根数转换为笛卡尔坐标
     *
     * @param centralMass   中心天体的质量（千克），被环绕的天体质量
     * @param t             时间（秒），从初始时刻开始经过的时间
     * @return              星体相对于中心天体的 XYZ 坐标（米）
     */
    public Vector3fc calPosition(double centralMass, double t) {
        if (semiMajorAxis == 0) return new Vector3f(0, 0, 0);

        // 步骤1：计算当前时刻的平近点角 M
        double M = initialMeanAnomaly + mean_anomaly_angular_velocity(centralMass, semiMajorAxis) * t;
        M = M % (2 * Math.PI); // 归一化到 [0, 2π)

        // 步骤3：解 Kepler 方程，求偏近点角 E
        double E = solveKepler(M, eccentricity);

        // 步骤4：计算轨道平面坐标 (xp, yp)
        double xp = semiMajorAxis * (Math.cos(E) - eccentricity);
        double yp = semiMajorAxis * Math.sqrt(1 - eccentricity * eccentricity) * Math.sin(E);

        // 步骤5：构造旋转矩阵并计算 XYZ
        // inclination (inclination) 是轨道倾角，argumentOfPeriapsis (argumentOfPeriapsis) 是近心点幅角， longitudeOfAscendingNode (longitudeOfAscendingNode) 是升交点经度
        double cos_Omega = Math.cos(longitudeOfAscendingNode);
        double sin_Omega = Math.sin(longitudeOfAscendingNode);
        double cos_i = Math.cos(inclination);
        double sin_i = Math.sin(inclination);
        double cos_w = Math.cos(argumentOfPeriapsis);
        double sin_w = Math.sin(argumentOfPeriapsis);

        double X = (cos_Omega * cos_w - sin_Omega * sin_w * cos_i) * xp
                + (-cos_Omega * sin_w - sin_Omega * cos_w * cos_i) * yp;

        double Y = (sin_Omega * cos_w + cos_Omega * sin_w * cos_i) * xp
                + (-sin_Omega * sin_w + cos_Omega * cos_w * cos_i) * yp;

        double Z = (sin_w * sin_i) * xp + (cos_w * sin_i) * yp;

        return new Vector3f((float) X, (float) Y, (float) Z);
    }
}