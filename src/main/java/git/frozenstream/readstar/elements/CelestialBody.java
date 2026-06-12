package git.frozenstream.readstar.elements;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;

/**
 * 天体类（行星、卫星等）
 * 表示一个具有轨道运动的天体，可以是行星、卫星或其他天体
 */
public class CelestialBody {
    // ==================== 基本物理属性 ====================

    /**
     * 天体名称
     */
    public String name;


    /**
     * 天体质量（千克）
     */
    public double mass;

    /**
     * 天体半径（米）
     */
    public double radius;

    /**
     * 天体自发光亮度（0-15，0 表示不发光，15 表示最亮）
     * 用于渲染时的光源效果，例如恒星会有较高的亮度值
     */
    public int luminance;

    /**
     * 天体自转轴方向向量
     */
    public Vector3f rotationAxis;

    /**
     * 是否为"不稳定的脏雪球"（彗星类天体）
     * 彗星由冰、尘埃和岩石组成，轨道通常高度偏心，靠近恒星时会形成彗发和彗尾
     */
    public boolean unstableDirtySnowball;

    // ==================== 大气属性 ====================

    /**
     * 是否有大气层
     */
    public boolean hasAtmosphere;

    /**
     * 大气 HSV 颜色（打包 int，各分量 0~255）
     * <pre>
     *   bits 16-23 : Hue（色相，0=红 → 255 循环回红）
     *   bits  8-15 : Saturation（饱和度，0=灰白 → 255=纯色）
     *   bits   0-7 : Value（浓度/强度，0=无大气 → 255=最浓）
     * </pre>
     * 示例：地球大气 (H=0.58, S=0.6, V=1.0) → 0x9499FF
     */
    public int atmosphereHSV;

    /**
     * 天体自身颜色（HSV 打包 int，与 atmosphereHSV 相同编码）
     * 发光体：发射光谱色（如太阳 = 黄白）
     * 非发光体：表面反射色（如地球 = 蓝绿，火星 = 红棕）
     */
    public int starHSV;

    /**
     * 天体的轨道参数，定义其围绕父天体的运动轨迹
     */
    public Orbit orbit;

    // ==================== 层级关系属性 ====================

    /**
     * 父天体（被环绕的天体），根节点为 null
     */
    public CelestialBody parent;

    /**
     * 子天体列表（环绕该天体的所有天体）
     */
    public ArrayList<CelestialBody> children;


    /**
     * 该天体所属的恒星系统中的主恒星
     */
    public CelestialBody hostStar;

    // ===================== 动态属性 =====================

    /**
     * 天体在绝对空间中的当前位置坐标
     */
    public Vector3f position;

    /**
     * 当前时刻行星表面观察者所在位置的天顶方向向量（绝对坐标系）
     * 即垂直于当地地平面向上的单位向量，表示观察者的垂直方向
     */
    public Vector3f currentRotationVector;

    /**
     * 正午时刻（太阳位于天顶最高点时）行星表面观察者所在位置的天顶方向向量（绝对坐标系）
     * 用于计算日照角度和昼夜变化
     */
    public Vector3f noonRotationVector;

    /**
     * 局部坐标系 → 世界坐标系的旋转四元数，由 updateCurrentVec() 同步更新。
     * 基于 currentRotationVector (Y轴/天顶) 和 rotationAxis (Z轴/极轴) 构建标准正交基。
     */
    public final Quaternionf localToWorldQuat = new Quaternionf();

    /**
     * 虚拟根节点，用于构建天体层级树的根
     */
    public static CelestialBody Root = new CelestialBody("VOID", 0, 0, 0, null, null, new ArrayList<>(), false, false, 0, 0);

    // ==================== 大气 HSV 编解码 ====================

    /** 打包 HSV → int（各分量 0~255） */
    public static int packHSV(int hue, int saturation, int value) {
        return ((hue & 0xFF) << 16) | ((saturation & 0xFF) << 8) | (value & 0xFF);
    }

    /** 打包 HSV → int（各分量 0.0~1.0） */
    public static int packHSV(float hue, float saturation, float value) {
        return packHSV((int) (hue * 255), (int) (saturation * 255), (int) (value * 255));
    }

    /** 提取 Hue（0~255） */
    public static int getHue(int hsv) { return (hsv >> 16) & 0xFF; }

    /** 提取 Saturation（0~255） */
    public static int getSaturation(int hsv) { return (hsv >> 8) & 0xFF; }

    /** 提取 Value（0~255） */
    public static int getValue(int hsv) { return hsv & 0xFF; }

    /** 提取 Hue（0.0~1.0） */
    public static float getHueFloat(int hsv) { return getHue(hsv) / 255f; }

    /** 提取 Saturation（0.0~1.0） */
    public static float getSaturationFloat(int hsv) { return getSaturation(hsv) / 255f; }

    /** 提取 Value（0.0~1.0） */
    public static float getValueFloat(int hsv) { return getValue(hsv) / 255f; }

    /**
     * 计算发光体在大气中的光晕颜色。
     * 光晕 = 星光光谱 × 大气散射效率。
     * <ul>
     *   <li>色相：星光色相向大气色相偏移（散射着色）</li>
     *   <li>饱和度：星光饱和 + 大气散射增量</li>
     *   <li>明度：星光亮度 + 大气散射增亮</li>
     * </ul>
     *
     * @param starHSV       发光天体自身 HSV
     * @param atmosphereHSV 观测者大气 HSV
     * @return 光晕 HSV（打包 int）
     */
    public static int computeGlowColor(int starHSV, int atmosphereHSV) {
        float starH = getHueFloat(starHSV);
        float starS = getSaturationFloat(starHSV);
        float starV = getValueFloat(starHSV);
        float atmH = getHueFloat(atmosphereHSV);
        float atmS = getSaturationFloat(atmosphereHSV);
        float atmV = getValueFloat(atmosphereHSV);

        // 大气散射着色：色相向大气偏移
        float glowH = starH + (atmH - starH) * atmS * atmV * 0.25f;
        // 饱和度：大气散射增加颜色纯度
        float glowS = Math.min(1f, starS + atmS * atmV * 0.2f);
        // 明度：大气散射增亮（散射光叠加）
        float glowV = Math.min(1f, starV + atmV * atmS * 0.15f);

        return packHSV(glowH, glowS, glowV);
    }

    /**
     * 构造一个完整的天体
     *
     * @param name                   天体名称
     * @param mass                   天体质量（千克）
     * @param radius                 天体半径（米）
     * @param luminance              自发光亮度（0-15）
     * @param rotationAxis           自转轴方向向量（可为 null，默认为 (0, 0, -1)）
     * @param orbit                  轨道参数（定义围绕父天体的运动）
     * @param children               子天体列表
     * @param unstableDirtySnowball  是否为"不稳定的脏雪球"（彗星类天体）
     * @param hasAtmosphere          是否有大气层
     * @param atmosphereHSV          大气 HSV 颜色（打包 int）
     * @param starHSV                天体自身颜色（打包 int，发光体=发射色，非发光体=表面色）
     */
    public CelestialBody(String name, double mass, double radius, int luminance, Vector3f rotationAxis, Orbit orbit,
                         ArrayList<CelestialBody> children, boolean unstableDirtySnowball,
                         boolean hasAtmosphere, int atmosphereHSV, int starHSV) {
        this.name = name;
        this.mass = mass;
        this.radius = radius;
        this.luminance = luminance;
        this.position = new Vector3f();
        this.currentRotationVector = new Vector3f();
        this.noonRotationVector = new Vector3f();
        this.rotationAxis = rotationAxis;
        this.orbit = orbit;
        this.parent = null;
        this.children = children;
        this.unstableDirtySnowball = unstableDirtySnowball;
        this.hasAtmosphere = hasAtmosphere;
        this.atmosphereHSV = atmosphereHSV;
        this.starHSV = starHSV;
    }

    /**
     * 获取天体的自转轴方向向量
     * 如果自转轴未设置或为零向量，则返回默认值 (0, 0, -1)
     * 注意：返回的是归一化后的向量（修改原向量），确保下游 Gram-Schmidt 和 rotateAxis 计算正确
     *
     * @return 归一化的自转轴方向向量
     */
    public Vector3f getRotationAxis() {
        if (rotationAxis == null) rotationAxis = new Vector3f(0, 0, -1);
        if (rotationAxis.lengthSquared() == 0f) rotationAxis.set(0, 0, -1);
        rotationAxis.normalize();
        return rotationAxis;
    }

    /**
     * 递归更新自身及所有子体的位置。
     * 位置 = 父体位置 + 轨道偏移，Root 固定于原点。
     */
    public void updatePosition(long t) {
        if (this == Root) {
            this.position.set(0, 0, 0);
        } else {
            if (this.mass == 0) {
                this.position.set(0, 0, 0);
            } else {
                this.position.set(this.parent.position).add(this.orbit.calPosition(this.parent.mass, t));
            }
            updateNoonVec();
        }
        // ReadStar.LOGGER.debug("[CelestialBody] updatePosition: {} {}", this.name, this.position);
        this.children.forEach(child -> child.updatePosition(t));
    }

    /** 更新正午朝向向量（基于 hostStar 位置） */
    public void updateNoonVec() {
        if (this == Root || this.hostStar == null) return;
        // toStar = hostStar.position - this.position，复用 noonRotationVector 免分配
        this.noonRotationVector.set(this.hostStar.position).sub(this.position);
        Vector3f axis = getRotationAxis();
        float dot = axis.dot(this.noonRotationVector);
        // Gram-Schmidt: 减去沿自转轴的分量 → 保留赤道面内的投影
        this.noonRotationVector.sub(axis.x * dot, axis.y * dot, axis.z * dot);
        // 极地盛夏：恒星接近天顶/天底，水平分量近乎为零 → 任选一个水平方向
        if (this.noonRotationVector.lengthSquared() < 0.0001f) {
            this.noonRotationVector.set(-1, 0, 0);
        } else {
            this.noonRotationVector.normalize();
        }
    }

    /**
     * 根据维度日周期时间更新当前天顶方向向量。
     * currentRotationVector = noonRotationVector 绕 rotationAxis 旋转 -θ
     * 其中 θ = (t - 6000) × π / 12000，t 为 0~24000 的维度日光时间
     * 同时更新局部→世界坐标系的旋转四元数（Y=天顶, Z=极轴, X=Y×Z）。
     */
    public void updateCurrentVec(long daylightTick) {
        float theta = (daylightTick - 6000) * (float) Math.PI / 12000;
        Vector3f axis = getRotationAxis();
        this.currentRotationVector.set(this.noonRotationVector).rotateAxis(-theta, axis.x, axis.y, axis.z);

        // 同步更新局部→世界坐标系的旋转四元数
        Vector3f yAxis = new Vector3f(this.currentRotationVector).normalize();
        Vector3f zAxis = new Vector3f(getRotationAxis()).normalize();
        if (yAxis.lengthSquared() > 0.001f && zAxis.lengthSquared() > 0.001f) {
            Vector3f xAxis = new Vector3f(yAxis).cross(zAxis).normalize();
            Matrix3f basis = new Matrix3f();
            basis.m00(xAxis.x); basis.m10(xAxis.y); basis.m20(xAxis.z);
            basis.m01(yAxis.x); basis.m11(yAxis.y); basis.m21(yAxis.z);
            basis.m02(zAxis.x); basis.m12(zAxis.y); basis.m22(zAxis.z);
            this.localToWorldQuat.setFromNormalized(basis);
        }
    }

    /**
     * 获取局部坐标系到世界坐标系的旋转四元数。
     * 由 updateCurrentVec() 每帧同步更新，可直接用于渲染变换。
     *
     * @return 局部→世界的旋转四元数
     */
    public Quaternionf getLocalToWorldQuaternion() {
        return localToWorldQuat;
    }

    /**
     * 向上查找最近的发光祖先天体（hostStar）。
     * 如果自身发光则返回自己，否则递归查 parent。
     */
    public static CelestialBody findLuminousAncestor(CelestialBody body) {
        if (body == null || body == Root) return null;
        if (body.luminance > 0) return body;
        return findLuminousAncestor(body.parent);
    }

    /**
     * 判断该天体是否为"不稳定的脏雪球"（彗星类天体）
     *
     * @return true 表示该天体为彗星类天体
     */
    public boolean isUnstableDirtySnowball() {
        return unstableDirtySnowball;
    }
}
