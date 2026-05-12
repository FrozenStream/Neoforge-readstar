package git.frozenstream.readstar.elements;

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
     * 虚拟根节点，用于构建天体层级树的根
     */
    public static CelestialBody Root = new CelestialBody("VOID", 0, 0, 0, null, null,  new ArrayList<>());

    /**
     * 构造一个完整的天体
     *
     * @param name         天体名称
     * @param mass         天体质量（千克）
     * @param radius       天体半径（米）
     * @param luminance    自发光亮度（0-15）
     * @param rotationAxis 自转轴方向向量（可为 null，默认为 (0, 0, -1)）
     * @param orbit        轨道参数（定义围绕父天体的运动）
     */
    public CelestialBody(String name, double mass, double radius, int luminance, Vector3f rotationAxis, Orbit orbit, ArrayList<CelestialBody>  children) {
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
     */
    public void updateCurrentVec(long daylightTick) {
        float theta = (daylightTick - 6000) * (float) Math.PI / 12000;
        Vector3f axis = getRotationAxis();
        this.currentRotationVector.set(this.noonRotationVector).rotateAxis(-theta, axis.x, axis.y, axis.z);
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
}
