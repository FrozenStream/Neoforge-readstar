package git.frozenstream.readstar.sprite;

/**
 * RGB 色彩离散化工具。
 * 每个通道使用感知均匀（gamma 2.2）的 10 个离散值，总计 10³ = 1000 种颜色。
 * 暗部采样更密集，匹配人眼对暗部变化更敏感的特性。
 */
public final class ColorDiscretizer {

    private ColorDiscretizer() {}

    /** 每通道离散值数量，总计 N³ ≤ 1000 */
    public static final int VALUES_PER_CHANNEL = 10;

    /**
     * 感知均匀分布的离散值（gamma ≈ 2.2）。
     * value[i] = round(255 * (i / (N-1)) ^ 2.2)
     */
    public static final int[] DISCRETE_VALUES = {
        0,   2,   9,   22,  39,
        61,  89,  122, 160, 255
    };

    /**
     * 将任意 0-255 通道值映射到最近的离散值。
     */
    public static int discretizeChannel(int value) {
        if (value <= DISCRETE_VALUES[0]) return DISCRETE_VALUES[0];
        if (value >= DISCRETE_VALUES[VALUES_PER_CHANNEL - 1]) return DISCRETE_VALUES[VALUES_PER_CHANNEL - 1];

        int lo = 0, hi = VALUES_PER_CHANNEL - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (DISCRETE_VALUES[mid] <= value) lo = mid;
            else hi = mid;
        }
        return (value - DISCRETE_VALUES[lo] <= DISCRETE_VALUES[hi] - value)
            ? DISCRETE_VALUES[lo]
            : DISCRETE_VALUES[hi];
    }

    /**
     * 将 RGB 颜色（0xAARRGGBB 或 0xRRGGBB）离散化到最近的感知调色板颜色。
     * 保留 alpha 位不变。
     */
    public static int discretize(int color) {
        int a = (color >> 24) & 0xFF;
        int r = discretizeChannel((color >> 16) & 0xFF);
        int g = discretizeChannel((color >> 8) & 0xFF);
        int b = discretizeChannel(color & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
