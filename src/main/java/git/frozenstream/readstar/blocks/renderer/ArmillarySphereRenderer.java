package git.frozenstream.readstar.blocks.renderer;

import com.mojang.blaze3d.vertex.*;
import git.frozenstream.readstar.blocks.entity.ArmillarySphereBlockEntity;
import git.frozenstream.readstar.elements.CelestialBody;
import git.frozenstream.readstar.elements.CelestialBodyManager;
import git.frozenstream.readstar.elements.Orbit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 浑天仪 BER — 在 3×3×1 区域内绘制整个恒星系。
 * <p>
 * 1.21.1 版本：使用经典 BlockEntityRenderer<T> 接口 + MultiBufferSource。
 */
public class ArmillarySphereRenderer implements BlockEntityRenderer<ArmillarySphereBlockEntity> {
    private static final float HW = 1.5f, HH = 0.5f;
    private static final double MAX_R = 5.0e11;
    private static final float MIN_R = 0.008f;
    private static final int ORB_SEG = 128, RING_SEG = 64;

    public ArmillarySphereRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(ArmillarySphereBlockEntity be, float partialTick, PoseStack ps,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float zoom = be.getZoomLevel();
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        drawSystem(ps, bufferSource, zoom);
        ps.popPose();
    }

    // ========== 系统 ==========
    private void drawSystem(PoseStack ps, MultiBufferSource bufferSource, float z) {
        CelestialBody root = CelestialBodyManager.getInstance().Root;
        if (root == null || root.children.isEmpty())
            return;
        drawWithParent(ps, bufferSource, root, new Vector3f(0, 0, 0), z);
    }

    /** 递归：渲染 p 的所有子天体，renderPos 为父天体已映射的渲染位置 */
    private void drawWithParent(PoseStack ps, MultiBufferSource bufferSource, CelestialBody p,
            Vector3f parentRenderPos, float z) {
        if (p.children == null)
            return;
        for (CelestialBody c : p.children) {
            Vector3f rp;
            if (p == CelestialBody.Root || p.parent == CelestialBody.Root) {
                rp = mapGlobal(c.position, z);
            } else {
                Vector3f offset = new Vector3f(c.position).sub(p.position);
                rp = mapLocal(offset, parentRenderPos);
            }
            drawOrbit(ps, bufferSource, c, parentRenderPos, z);
            drawBody(ps, bufferSource, c, rp);
            drawWithParent(ps, bufferSource, c, rp, z);
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
        double localScale = 0.08 / 4e8;
        return new Vector3f(
                parentPos.x() + (float) (offset.x() * localScale),
                parentPos.y() + (float) (offset.z() * localScale),
                parentPos.z() + (float) (offset.y() * localScale));
    }

    /** 用细四边形模拟轨道线（等偏近点角采样） */
    private void drawOrbit(PoseStack ps, MultiBufferSource bufferSource, CelestialBody child,
            Vector3f parentRenderPos, float z) {
        if (child.orbit == null || child.orbit.semiMajorAxis() == 0) return;
        Orbit o = child.orbit;
        boolean isLocal = !(child.parent == CelestialBody.Root || child.parent.parent == CelestialBody.Root);

        VertexConsumer vc = bufferSource.getBuffer(RenderType.debugQuads());
        float lineW = 0.001f;
        Vector3f prev = null;
        for (int i = 0; i <= ORB_SEG; i++) {
            double E = 2.0 * Math.PI * i / ORB_SEG;
            Vector3fc phys = o.calPositionFromE(E);
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
                    // 第一遍：平行地面
                    addVertex(vc, ps, prev.x() - px, prev.y() - py, prev.z(), 0.3f, 0.4f, 0.5f, 0.5f);
                    addVertex(vc, ps, prev.x() + px, prev.y() + py, prev.z(), 0.3f, 0.4f, 0.5f, 0.5f);
                    addVertex(vc, ps, curr.x() + px, curr.y() + py, curr.z(), 0.3f, 0.4f, 0.5f, 0.5f);
                    addVertex(vc, ps, curr.x() - px, curr.y() - py, curr.z(), 0.3f, 0.4f, 0.5f, 0.5f);
                    // 第二遍：垂直地面
                    addVertex(vc, ps, prev.x(), prev.y(), prev.z() - lineW, 0.25f, 0.35f, 0.45f, 0.5f);
                    addVertex(vc, ps, prev.x(), prev.y(), prev.z() + lineW, 0.25f, 0.35f, 0.45f, 0.5f);
                    addVertex(vc, ps, curr.x(), curr.y(), curr.z() + lineW, 0.25f, 0.35f, 0.45f, 0.5f);
                    addVertex(vc, ps, curr.x(), curr.y(), curr.z() - lineW, 0.25f, 0.35f, 0.45f, 0.5f);
                }
            }
            prev = curr;
        }
    }

    private void drawBody(PoseStack ps, MultiBufferSource bufferSource, CelestialBody bd, Vector3f rp) {
        VertexConsumer vc = bufferSource.getBuffer(RenderType.debugQuads());
        float rr = radius(bd.radius), x = rp.x(), y = rp.y(), z = rp.z();
        float[] rgb = hsv(CelestialBody.getHueFloat(bd.starHSV),
                CelestialBody.getSaturationFloat(bd.starHSV),
                Math.min(1f, CelestialBody.getValueFloat(bd.starHSV)));
        float r = rgb[0], g = rgb[1], bl = rgb[2];
        float al = bd.luminance > 0 ? 0.9f : 0.85f;

        float h = rr;
        quad(ps, vc, x - h, y + h, z - h, x + h, y + h, z - h, x + h, y + h, z + h, x - h, y + h, z + h, r, g, bl, al, 1.0f);
        quad(ps, vc, x - h, y - h, z + h, x + h, y - h, z + h, x + h, y - h, z - h, x - h, y - h, z - h, r, g, bl, al, 0.45f);
        quad(ps, vc, x - h, y - h, z + h, x + h, y - h, z + h, x + h, y + h, z + h, x - h, y + h, z + h, r, g, bl, al, 0.8f);
        quad(ps, vc, x + h, y - h, z - h, x - h, y - h, z - h, x - h, y + h, z - h, x + h, y + h, z - h, r, g, bl, al, 0.55f);
        quad(ps, vc, x + h, y - h, z + h, x + h, y - h, z - h, x + h, y + h, z - h, x + h, y + h, z + h, r, g, bl, al, 0.9f);
        quad(ps, vc, x - h, y - h, z - h, x - h, y - h, z + h, x - h, y + h, z + h, x - h, y + h, z - h, r, g, bl, al, 0.65f);
    }

    private static void quad(PoseStack ps, VertexConsumer vc,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float r, float g, float b, float a, float bright) {
        PoseStack.Pose pose = ps.last();
        addVertex(vc, pose, x1, y1, z1, r * bright, g * bright, b * bright, a);
        addVertex(vc, pose, x2, y2, z2, r * bright, g * bright, b * bright, a);
        addVertex(vc, pose, x3, y3, z3, r * bright, g * bright, b * bright, a);
        addVertex(vc, pose, x4, y4, z4, r * bright, g * bright, b * bright, a);
    }

    /** 1.21.1 VertexConsumer 使用 int 颜色 */
    private static void addVertex(VertexConsumer vc, PoseStack.Pose pose,
            float x, float y, float z, float r, float g, float b, float a) {
        vc.addVertex(pose, x, y, z)
          .setColor((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));
    }

    private static void addVertex(VertexConsumer vc, PoseStack pose,
            float x, float y, float z, float r, float g, float b, float a) {
        vc.addVertex(pose.last(), x, y, z)
          .setColor((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));
    }

    /** 立方根映射：体积 → 线尺寸 */
    private float radius(double pr) {
        double cr = Math.cbrt(pr);
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
}
