package git.frozenstream.readstar.skybox;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;

public class RenderUtils {
    /** POSITION_TEX_COLOR_OFFSET 格式的字段偏移量（固定值） */
    private static final int OFF_POS = 0;      // Position: vec3f = 12B
    private static final int OFF_UV  = 12;     // UV0:      vec2f =  8B
    private static final int OFF_COL = 20;     // Color:    ubyte4 =  4B
    private static final int OFF_OFF = 24;     // Offset:   vec3f = 12B  (总计 36B)

    /**
     * 向 ByteBufferBuilder 写入一颗星的 4 个顶点（QUAD），
     * 使用固定的 POSITION_TEX_COLOR_OFFSET 布局。
     * 所有顶点共享同一个 Position（球面中心），Offset（vec3，经 rotation 旋转后的 3D 偏移）区分四个角落。
     */
    public static void writeStarQuad(ByteBufferBuilder buf, int vtxSize,
            Vector3f center,
            float u0, float v0, float u1, float v1,
            int colorByte, int alpha,
            float size) {
        long ptr = buf.reserve(vtxSize * 4);

        // 计算 billboard 旋转矩阵（同旧代码逻辑）
        Vector3f dirToCenter = new Vector3f(center).negate();
        Matrix3f rotation = new Matrix3f().rotateTowards(dirToCenter, new Vector3f(0.0F, 1.0F, 0.0F));

        // 角落偏移量，经 rotation 旋转后存入 vec3 Offset
        float[][] corners = {
                { size, -size, u0, v0 },
                { size, size, u1, v0 },
                { -size, size, u1, v1 },
                { -size, -size, u0, v1 },
        };

        for (float[] c : corners) {
            Vector3f offset3d = new Vector3f(c[0], c[1], 0.0F).mul(rotation);
            writeVertex(ptr, center, c[2], c[3], colorByte, alpha,
                    offset3d.x, offset3d.y, offset3d.z);
            ptr += vtxSize;
        }
    }

    /** 写入单个顶点到指定内存位置（POSITION_TEX_COLOR_OFFSET 布局） */
    private static void writeVertex(long ptr,
            Vector3f pos, float u, float v, int colorByte, int alpha,
            float ox, float oy, float oz) {
        // Position (vec3 float)
        MemoryUtil.memPutFloat(ptr + OFF_POS, pos.x);
        MemoryUtil.memPutFloat(ptr + OFF_POS + 4, pos.y);
        MemoryUtil.memPutFloat(ptr + OFF_POS + 8, pos.z);
        // UV0 (vec2 float)
        MemoryUtil.memPutFloat(ptr + OFF_UV, u);
        MemoryUtil.memPutFloat(ptr + OFF_UV + 4, v);
        // Color (vec4 ubyte, normalized)
        MemoryUtil.memPutByte(ptr + OFF_COL, (byte) colorByte);
        MemoryUtil.memPutByte(ptr + OFF_COL + 1, (byte) colorByte);
        MemoryUtil.memPutByte(ptr + OFF_COL + 2, (byte) colorByte);
        MemoryUtil.memPutByte(ptr + OFF_COL + 3, (byte) alpha);
        // Offset (vec3 float): 经 rotation 旋转后的 3D billboard 偏移
        MemoryUtil.memPutFloat(ptr + OFF_OFF, ox);
        MemoryUtil.memPutFloat(ptr + OFF_OFF + 4, oy);
        MemoryUtil.memPutFloat(ptr + OFF_OFF + 8, oz);
    }

    // ==================== HSV 工具方法（从 CelestialBody 迁移） ====================

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
     * HSV → RGB 转换。
     * @param hue 色相 (0.0~1.0)
     * @param saturation 饱和度 (0.0~1.0)
     * @param value 明度 (0.0~1.0)
     * @return float[3] {r, g, b}，各分量 0.0~1.0
     */
    public static float[] hsvToRgb(float hue, float saturation, float value) {
        hue = hue % 1.0f;
        if (hue < 0) hue += 1.0f;

        int h = (int) (hue * 6);
        float f = hue * 6 - h;
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);

        return switch (h) {
            case 0 -> new float[] { value, t, p };
            case 1 -> new float[] { q, value, p };
            case 2 -> new float[] { p, value, t };
            case 3 -> new float[] { p, q, value };
            case 4 -> new float[] { t, p, value };
            default -> new float[] { value, p, q };
        };
    }

    /** 银道坐标系 → 赤道坐标系 (J2000) 预旋转。
     *  天球纹理以银心为中心、银道面为赤道，需先旋转到赤道系再叠加 observer 旋转。 */
    public static final Quaternionf GALACTIC_TO_EQUATORIAL = buildGalacticToEquatorial();

    private static Quaternionf buildGalacticToEquatorial() {
        // 北银极在赤道系 (J2000): RA=192.85948°, Dec=+27.12825°
        float ngpRA = (float) Math.toRadians(192.85948);
        float ngpDec = (float) Math.toRadians(27.12825);
        Vector3f ngp = new Vector3f(
                (float)(Math.cos(ngpDec) * Math.cos(ngpRA)),
                (float) Math.sin(ngpDec),
                (float)(Math.cos(ngpDec) * Math.sin(ngpRA)));

        // 银心在赤道系 (J2000): RA=266.4051°, Dec=-28.9362°
        float gcRA = (float) Math.toRadians(266.4051);
        float gcDec = (float) Math.toRadians(-28.9362);
        Vector3f gc = new Vector3f(
                (float)(Math.cos(gcDec) * Math.cos(gcRA)),
                (float) Math.sin(gcDec),
                (float)(Math.cos(gcDec) * Math.sin(gcRA)));

        // 银道坐标系正交基在赤道系中的表示
        // 天穹本地：X(-1,0,0)=银心方向, Y(0,1,0)=北银极, Z(0,0,1)=Y×X
        // R × v_local = v_equatorial → 列向量为基向量在赤道系的坐标
        Vector3f zGal = new Vector3f(ngp).cross(gc).normalize(); // Z = NGP × GC（正交补全）

        // R = [ -gc | ngp | zGal ]  （因为 X_local=(-1,0,0) → R×(-1,0,0) = gc → col0 = -gc）
        Matrix3f rot = new Matrix3f();
        rot.setColumn(0, new Vector3f(-gc.x, -gc.y, -gc.z));
        rot.setColumn(1, new Vector3f(ngp));
        rot.setColumn(2, zGal);

        return rot.getUnnormalizedRotation(new Quaternionf());
    }
}
