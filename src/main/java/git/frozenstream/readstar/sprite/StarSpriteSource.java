package git.frozenstream.readstar.sprite;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import git.frozenstream.readstar.ReadStar;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 运行时为每种颜色生成核心 + 三级光晕子图。
 * 光晕亮度由核心中心 8×8 区域采样 × 0.35 系数控制，确保远暗于核心。
 */
public record StarSpriteSource() implements SpriteSource {

    static final int WIDTH = 32;
    static final int HEIGHT = 32;

    public static final MapCodec<StarSpriteSource> CODEC = MapCodec.unit(new StarSpriteSource());

    private static final String TEX_DIR = "textures/environment/star/";

    private static float[][] loadPattern(Identifier path, ResourceManager manager) {
        Optional<net.minecraft.server.packs.resources.Resource> res = manager.getResource(path);
        if (res.isEmpty()) throw new RuntimeException("Missing texture: " + path);
        try (InputStream in = res.get().open()) {
            NativeImage source = NativeImage.read(in);
            try {
                float[][] pattern = new float[WIDTH][HEIGHT];
                int w = Math.min(source.getWidth(), WIDTH);
                int h = Math.min(source.getHeight(), HEIGHT);
                for (int x = 0; x < w; x++)
                    for (int y = 0; y < h; y++)
                        pattern[x][y] = ((source.getPixel(x, y) >> 16) & 0xFF) / 255.0f;
                return pattern;
            } finally { source.close(); }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load texture: " + path, e);
        }
    }

    /** 采样核心中心 8×8 区域平均亮度，用于确定光晕亮度上限 */
    private static float sampleCoreBrightness(float[][] corePattern) {
        int c = WIDTH / 2; // 16
        float sum = 0;
        int count = 0;
        for (int x = c - 4; x < c + 4; x++)
            for (int y = c - 4; y < c + 4; y++) {
                sum += corePattern[x][y];
                count++;
            }
        return sum / count;
    }

    private static NativeImage createTinted(float[][] pattern, int color, float brightnessMul) {
        NativeImage img = new NativeImage(WIDTH, HEIGHT, false);
        int cR = (color >> 16) & 0xFF, cG = (color >> 8) & 0xFF, cB = color & 0xFF;
        for (int x = 0; x < WIDTH; x++)
            for (int y = 0; y < HEIGHT; y++) {
                float i = pattern[x][y] * brightnessMul;
                img.setPixel(x, y, (0xFF << 24) | ((int)(cR*i) << 16) | ((int)(cG*i) << 8) | (int)(cB*i));
            }
        return img;
    }

    private static final String[][] GLOW_LAYERS = {
        {"star_glow_low.png",  "glow_low"},
        {"star_glow_med.png",  "glow_med"},
        {"star_glow_high.png", "glow_high"},
    };

    @Override
    public void run(ResourceManager manager, Output output) {
        float[][] corePattern = loadPattern(
                Identifier.fromNamespaceAndPath(ReadStar.MODID, TEX_DIR + "star_base.png"), manager);

        // 采样核心中心亮度，光晕亮度 = 核心亮度 × 0.35（显著暗于核心）
        float coreBrightness = sampleCoreBrightness(corePattern);
        float glowMul = coreBrightness * 0.35f;
        ReadStar.LOGGER.info("Core center brightness: {}", coreBrightness);

        float[][][] glowPatterns = new float[GLOW_LAYERS.length][][];
        for (int i = 0; i < GLOW_LAYERS.length; i++) {
            Identifier path = Identifier.fromNamespaceAndPath(ReadStar.MODID, TEX_DIR + GLOW_LAYERS[i][0]);
            glowPatterns[i] = loadPattern(path, manager);
        }

        // 扫描 stars/ 目录下所有 .json 文件
        Map<Identifier, net.minecraft.server.packs.resources.Resource> starResources =
                manager.listResources("stars", id -> id.getPath().endsWith(".json"));
        if (starResources.isEmpty()) {
            ReadStar.LOGGER.warn("No star data files found in stars/");
            return;
        }

        Set<Integer> colors = new HashSet<>();
        for (Map.Entry<Identifier, net.minecraft.server.packs.resources.Resource> entry : starResources.entrySet()) {
            Identifier resPath = entry.getKey();
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                JsonArray arr = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("Stars");
                if (arr == null) {
                    ReadStar.LOGGER.warn("No 'Stars' array in: {}", resPath);
                    continue;
                }
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    if (o.has("color")) colors.add(o.get("color").getAsInt());
                }
            } catch (Exception e) {
                ReadStar.LOGGER.error("Failed to read star data from {}", resPath, e);
            }
        }

        try {
            int total = colors.size() * (1 + GLOW_LAYERS.length);
            ReadStar.LOGGER.info("Generating {} sprites ({} colors × 1 core + {} glow layers)", total, colors.size(), GLOW_LAYERS.length);

            for (int color : colors) {
                // 核心（满亮度）
                Identifier coreId = Identifier.fromNamespaceAndPath(ReadStar.MODID, "environment/stars/color_" + color);
                NativeImage coreImg = createTinted(corePattern, color, 1.0f);
                try { output.add(coreId, (SpriteSource.DiscardableLoader) l -> new SpriteContents(coreId, new FrameSize(WIDTH, HEIGHT), coreImg)); }
                catch (Throwable t) { coreImg.close(); ReadStar.LOGGER.error("Failed core sprite for color {}", color, t); }

                // 三级光晕（低亮度）
                for (int g = 0; g < GLOW_LAYERS.length; g++) {
                    String layerPrefix = GLOW_LAYERS[g][1];
                    Identifier glowId = Identifier.fromNamespaceAndPath(ReadStar.MODID, "environment/stars/" + layerPrefix + "_" + color);
                    NativeImage glowImg = createTinted(glowPatterns[g], color, glowMul);
                    try { output.add(glowId, (SpriteSource.DiscardableLoader) l -> new SpriteContents(glowId, new FrameSize(WIDTH, HEIGHT), glowImg)); }
                    catch (Throwable t) { glowImg.close(); ReadStar.LOGGER.error("Failed {} sprite for color {}", layerPrefix, color, t); }
                }
            }
        } catch (Exception e) {
            ReadStar.LOGGER.error("Failed to read star data", e);
        }
    }

    @Override
    public MapCodec<? extends SpriteSource> codec() { return CODEC; }
}
