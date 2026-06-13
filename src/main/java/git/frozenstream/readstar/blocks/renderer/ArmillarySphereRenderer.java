package git.frozenstream.readstar.blocks.renderer;

import com.mojang.blaze3d.vertex.*;
import git.frozenstream.readstar.blocks.entity.ArmillarySphereBlockEntity;
import git.frozenstream.readstar.elements.CelestialBody;
import git.frozenstream.readstar.elements.CelestialBodyManager;
import git.frozenstream.readstar.elements.Orbit;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 浑天仪 BER —— 在 3×3×1 区域内绘制整个恒星系
 */
public class ArmillarySphereRenderer
        implements BlockEntityRenderer<ArmillarySphereBlockEntity, ArmillarySphereRenderState> {
    private static final float HW = 1.5f, HH = 0.5f;
    private static final double MAX_R = 5.0e11;
    private static final float MIN_R = 0.008f;
    private static final int ORB_SEG = 128, RING_SEG = 64;

    public ArmillarySphereRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public ArmillarySphereRenderState createRenderState() {
        return new ArmillarySphereRenderState();
    }

    @Override
    public void submit(ArmillarySphereRenderState s, PoseStack ps,
            SubmitNodeCollector col, CameraRenderState cam) {
        // 从 level 获取 BE 数据（extractRenderState 不在 BER 接口中，需手动获取）
        var level = Minecraft.getInstance().level;
        float zoom = 1f;
        if (level != null && level.getBlockEntity(s.blockPos) instanceof ArmillarySphereBlockEntity be) {
            zoom = be.getZoomLevel();
        }
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        drawSystem(ps, col, zoom);
        ps.popPose();
    }

    // ========== 底座 ==========
    private void drawBase(PoseStack ps, SubmitNodeCollector col) {
        col.submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, vc) -> {
            float rr = 1.4f, y0 = -HH + 0.02f, th = 0.04f;
            float r = 0.55f, g = 0.47f, b = 0.14f, a = 0.85f;
            for (int i = 0; i <= RING_SEG; i++) {
                float ang = i / (float) RING_SEG * 2f * (float) Math.PI;
                float c = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                vc.addVertex(pose, c * (rr + th), y0, sn * (rr + th)).setColor(r, g, b, a);
                vc.addVertex(pose, c * (rr - th), y0 + 0.04f, sn * (rr - th)).setColor(r, g * .8f, b * .8f, a * .7f);
            }
        });
    }

    // ========== 系统 ==========
    private void drawSystem(PoseStack ps, SubmitNodeCollector col, float z) {
        CelestialBody root = CelestialBodyManager.getInstance().Root;
        if (root == null || root.children.isEmpty())
            return;
        // 递归渲染，子天体相对于父天体做局部映射
        drawWithParent(ps, col, root, new Vector3f(0, 0, 0), z);
    }

    /** 递归：渲染 p 的所有子天体，renderPos 为父天体已映射的渲染位置 */
    private void drawWithParent(PoseStack ps, SubmitNodeCollector col, CelestialBody p, Vector3f parentRenderPos,
            float z) {
        if (p.children == null)
            return;
        for (CelestialBody c : p.children) {
            Vector3f rp;
            if (p == CelestialBody.Root || p.parent == CelestialBody.Root) {
                // Root 的子天体（恒星）和恒星的行星：用全局映射
                rp = mapGlobal(c.position, z);
            } else {
                // 行星的卫星等：相对于父天体的偏移，用局部线性缩放
                Vector3f offset = new Vector3f(c.position).sub(p.position);
                rp = mapLocal(offset, parentRenderPos);
            }
            drawOrbit(ps, col, c, parentRenderPos, z);
            drawBody(ps, col, c, rp);
            drawWithParent(ps, col, c, rp, z);
        }
    }

    /** 全局映射：太阳系尺度 → 渲染空间（平方根压缩） */
    private Vector3f mapGlobal(Vector3f p, float z) {
        double d = Math.sqrt(p.x() * p.x() + p.y() * p.y() + p.z() * p.z());
        if (d < 1e-11)
            return new Vector3f();
        double sd = Math.sqrt(d / MAX_R) * HW * 0.9 * z;
        double sc = sd / d;
        return new Vector3f((float) (p.x() * sc), (float) (p.z() * sc), (float) (p.y() * sc));
    }

    /** 局部映射：卫星轨道 → 父天体周围的偏移 */
    private Vector3f mapLocal(Vector3f offset, Vector3f parentPos) {
        double d = Math.sqrt(offset.x() * offset.x() + offset.y() * offset.y() + offset.z() * offset.z());
        if (d < 1e-11)
            return new Vector3f(parentPos);
        // 局部缩放：最大卫星轨道 ~4e8 m → 0.08 方块
        double localScale = 0.08 / 4e8;
        return new Vector3f(
                parentPos.x() + (float) (offset.x() * localScale),
                parentPos.y() + (float) (offset.z() * localScale),
                parentPos.z() + (float) (offset.y() * localScale));
    }

    /** 用细四边形模拟轨道线（采样完整3D轨道，含倾角） */
    private void drawOrbit(PoseStack ps, SubmitNodeCollector col, CelestialBody child, Vector3f parentRenderPos, float z) {
        if (child.orbit == null || child.orbit.semiMajorAxis() == 0) return;
        Orbit o = child.orbit;
        // 轨道周期 T = 2π / n，n = √(GM/a³)
        double n = Math.sqrt(6.67430e-11 * child.parent.mass / Math.pow(o.semiMajorAxis(), 3));
        double period = 2 * Math.PI / n;
        boolean isLocal = !(child.parent == CelestialBody.Root || child.parent.parent == CelestialBody.Root);
        col.submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, vc) -> {
            float lineW = 0.001f;
            Vector3f prev = null;
            for (int i = 0; i <= ORB_SEG; i++) {
                double t = period * i / ORB_SEG;
                Vector3fc phys = o.calPosition(child.parent.mass, t);
                Vector3f curr;
                if (isLocal) {
                    curr = mapLocal(new Vector3f(phys), parentRenderPos);
                } else {
                    curr = mapGlobal(new Vector3f(phys), z);
                }
                if (prev != null) {
                    float dx = curr.x() - prev.x(), dy = curr.y() - prev.y();
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 1e-6f) {
                        float px = -dy / len * lineW, py = dx / len * lineW;
                        // 第一遍：平行地面（XY 平面内垂直方向）
                        vc.addVertex(pose, prev.x()-px, prev.y()-py, prev.z()).setColor(0.3f,0.4f,0.5f,0.5f);
                        vc.addVertex(pose, prev.x()+px, prev.y()+py, prev.z()).setColor(0.3f,0.4f,0.5f,0.5f);
                        vc.addVertex(pose, curr.x()+px, curr.y()+py, curr.z()).setColor(0.3f,0.4f,0.5f,0.5f);
                        vc.addVertex(pose, curr.x()-px, curr.y()-py, curr.z()).setColor(0.3f,0.4f,0.5f,0.5f);
                        // 第二遍：垂直地面（Z 方向，立体交叉）
                        vc.addVertex(pose, prev.x(), prev.y(), prev.z()-lineW).setColor(0.25f,0.35f,0.45f,0.5f);
                        vc.addVertex(pose, prev.x(), prev.y(), prev.z()+lineW).setColor(0.25f,0.35f,0.45f,0.5f);
                        vc.addVertex(pose, curr.x(), curr.y(), curr.z()+lineW).setColor(0.25f,0.35f,0.45f,0.5f);
                        vc.addVertex(pose, curr.x(), curr.y(), curr.z()-lineW).setColor(0.25f,0.35f,0.45f,0.5f);
                    }
                }
                prev = curr;
            }
        });
    }

    private void drawBody(PoseStack ps, SubmitNodeCollector col, CelestialBody bd, Vector3f rp) {
        col.submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, vc) -> {
            float rr = radius(bd.radius), x = rp.x(), y = rp.y(), z = rp.z();
            float[] rgb = hsv(CelestialBody.getHueFloat(bd.starHSV),
                    CelestialBody.getSaturationFloat(bd.starHSV),
                    Math.min(1f, CelestialBody.getValueFloat(bd.starHSV)));
            float r = rgb[0], g = rgb[1], bl = rgb[2];
            float al = bd.luminance > 0 ? 0.9f : 0.85f;

            // 小立方体 —— 6面不同亮度，呈现3D立体感
            float h = rr;
            quad(pose, vc, x - h, y + h, z - h, x + h, y + h, z - h, x + h, y + h, z + h, x - h, y + h, z + h, r, g, bl,
                    al, 1.0f);
            quad(pose, vc, x - h, y - h, z + h, x + h, y - h, z + h, x + h, y - h, z - h, x - h, y - h, z - h, r, g, bl,
                    al, 0.45f);
            quad(pose, vc, x - h, y - h, z + h, x + h, y - h, z + h, x + h, y + h, z + h, x - h, y + h, z + h, r, g, bl,
                    al, 0.8f);
            quad(pose, vc, x + h, y - h, z - h, x - h, y - h, z - h, x - h, y + h, z - h, x + h, y + h, z - h, r, g, bl,
                    al, 0.55f);
            quad(pose, vc, x + h, y - h, z + h, x + h, y - h, z - h, x + h, y + h, z - h, x + h, y + h, z + h, r, g, bl,
                    al, 0.9f);
            quad(pose, vc, x - h, y - h, z - h, x - h, y - h, z + h, x - h, y + h, z + h, x - h, y + h, z - h, r, g, bl,
                    al, 0.65f);
        });
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float r, float g, float b, float a, float bright) {
        vc.addVertex(pose, x1, y1, z1).setColor(r * bright, g * bright, b * bright, a);
        vc.addVertex(pose, x2, y2, z2).setColor(r * bright, g * bright, b * bright, a);
        vc.addVertex(pose, x3, y3, z3).setColor(r * bright, g * bright, b * bright, a);
        vc.addVertex(pose, x4, y4, z4).setColor(r * bright, g * bright, b * bright, a);
    }

    /** 立方根映射：体积 → 线尺寸，比对数保留更多真实比例 */
    private float radius(double pr) {
        double cr = Math.cbrt(pr); // 立方根 → 线尺寸代理
        double t = cr / 10000;
        return Math.max(MIN_R, (float) t);
    }

    static float[] hsv(float h, float s, float v) {
        h -= (float) Math.floor(h);
        int hi = (int) (h * 6f);
        float f = h * 6f - hi, p = v * (1f - s), q = v * (1f - f * s), tt = v * (1f - (1f - f) * s);
        return switch (hi % 6) {
            case 0 -> new float[] { v, tt, p };
            case 1 -> new float[] { q, v, p };
            case 2 -> new float[] { p, v, tt };
            case 3 -> new float[] { p, q, v };
            case 4 -> new float[] { tt, p, v };
            default -> new float[] { v, p, q };
        };
    }

    static float rng(long s, int i) {
        long h = s ^ ((long) i * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return ((h ^ (h >>> 31)) & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }
}
