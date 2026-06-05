package git.frozenstream.readstar.skybox;

import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;

public class StarBufferUtils {
    /**
     * 向 ByteBufferBuilder 写入一颗星的 4 个顶点（QUAD）。
     * 所有顶点共享同一个 Position（球面中心），Offset（vec3，经 rotation 旋转后的 3D 偏移）区分四个角落。
     */
    public static void writeStarQuad(ByteBufferBuilder buf, int vtxSize,
            int posOff, int uvOff, int colorOff, int offsetOff,
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
            writeVertex(ptr, posOff, uvOff, colorOff, offsetOff,
                    center, c[2], c[3], colorByte, alpha,
                    offset3d.x, offset3d.y, offset3d.z);
            ptr += vtxSize;
        }
    }

    /** 写入单个顶点到指定内存位置 */
    private static void writeVertex(long ptr, int posOff, int uvOff, int colorOff, int offsetOff,
            Vector3f pos, float u, float v, int colorByte, int alpha,
            float ox, float oy, float oz) {
        // Position (vec3 float)
        MemoryUtil.memPutFloat(ptr + posOff, pos.x);
        MemoryUtil.memPutFloat(ptr + posOff + 4, pos.y);
        MemoryUtil.memPutFloat(ptr + posOff + 8, pos.z);
        // UV0 (vec2 float)
        MemoryUtil.memPutFloat(ptr + uvOff, u);
        MemoryUtil.memPutFloat(ptr + uvOff + 4, v);
        // Color (vec4 ubyte, normalized)
        MemoryUtil.memPutByte(ptr + colorOff, (byte) colorByte);
        MemoryUtil.memPutByte(ptr + colorOff + 1, (byte) colorByte);
        MemoryUtil.memPutByte(ptr + colorOff + 2, (byte) colorByte);
        MemoryUtil.memPutByte(ptr + colorOff + 3, (byte) alpha);
        // Offset (vec3 float): 经 rotation 旋转后的 3D billboard 偏移
        MemoryUtil.memPutFloat(ptr + offsetOff, ox);
        MemoryUtil.memPutFloat(ptr + offsetOff + 4, oy);
        MemoryUtil.memPutFloat(ptr + offsetOff + 8, oz);
    }
}
