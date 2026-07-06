package git.frozenstream.readstar.sprite;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import git.frozenstream.readstar.ReadStar;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 运行时为每种颜色生成核心 + 三级光晕子图。1.21.1 版本。
 */
public record StarSpriteSource() implements SpriteSource {

    static final int WIDTH = 32, HEIGHT = 32;

    public static final MapCodec<StarSpriteSource> CODEC = MapCodec.unit(new StarSpriteSource());
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    private static final String TEX_DIR = "textures/environment/star/";

    private static float[][] loadPattern(ResourceLocation path, ResourceManager manager) {
        Optional<Resource> res = manager.getResource(path);
        if (res.isEmpty()) throw new RuntimeException("Missing texture: " + path);
        try (InputStream in = res.get().open()) {
            NativeImage source = NativeImage.read(in);
            try {
                float[][] pattern = new float[WIDTH][HEIGHT];
                int w = Math.min(source.getWidth(), WIDTH);
                int h = Math.min(source.getHeight(), HEIGHT);
                for (int x = 0; x < w; x++)
                    for (int y = 0; y < h; y++)
                        pattern[x][y] = ((source.getPixelRGBA(x, y) >> 16) & 0xFF) / 255.0f;
                return pattern;
            } finally { source.close(); }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load texture: " + path, e);
        }
    }

    private static float sampleCoreBrightness(float[][] p) {
        int c = WIDTH / 2;
        float sum = 0; int cnt = 0;
        for (int x = c - 4; x < c + 4; x++)
            for (int y = c - 4; y < c + 4; y++) { sum += p[x][y]; cnt++; }
        return sum / cnt;
    }

    private static NativeImage createTinted(float[][] p, int color, float mul) {
        NativeImage img = new NativeImage(WIDTH, HEIGHT, false);
        int cR = (color >> 16) & 0xFF, cG = (color >> 8) & 0xFF, cB = color & 0xFF;
        for (int x = 0; x < WIDTH; x++)
            for (int y = 0; y < HEIGHT; y++) {
                float i = p[x][y] * mul;
                img.setPixelRGBA(x, y,
                        (0xFF << 24) | ((int)(cR*i) << 16) | ((int)(cG*i) << 8) | (int)(cB*i));
            }
        return img;
    }

    private static final String[][] GLOW = {
        {"star_glow_low.png",  "glow_low"},
        {"star_glow_med.png",  "glow_med"},
        {"star_glow_high.png", "glow_high"},
    };

    @Override
    public void run(ResourceManager manager, Output output) {
        float[][] core = loadPattern(
                ResourceLocation.fromNamespaceAndPath(ReadStar.MODID, TEX_DIR + "star_base.png"), manager);
        float coreBright = sampleCoreBrightness(core);
        float glowMul = coreBright * 0.35f;
        ReadStar.LOGGER.info("Core center brightness: {}", coreBright);

        float[][][] glows = new float[GLOW.length][][];
        for (int i = 0; i < GLOW.length; i++) {
            ResourceLocation p = ResourceLocation.fromNamespaceAndPath(ReadStar.MODID, TEX_DIR + GLOW[i][0]);
            glows[i] = loadPattern(p, manager);
        }

        Map<ResourceLocation, Resource> starResources =
                manager.listResources("stars", id -> id.getPath().endsWith(".csv"));
        if (starResources.isEmpty()) { ReadStar.LOGGER.warn("No star data files found in stars/"); return; }

        Set<Integer> colors = new HashSet<>();
        for (Map.Entry<ResourceLocation, Resource> e : starResources.entrySet()) {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(e.getValue().open(), StandardCharsets.UTF_8))) {
                String hdr = r.readLine();
                if (hdr == null || !hdr.startsWith("name,")) continue;
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split(",");
                    if (parts.length >= 6) colors.add(Integer.parseUnsignedInt(parts[5]));
                }
            } catch (Exception ex) {
                ReadStar.LOGGER.error("Failed to read star data from {}", e.getKey(), ex);
            }
        }

        int total = colors.size() * (1 + GLOW.length);
        ReadStar.LOGGER.info("Generating {} sprites ({} colors × 1 core + {} glow)",
                total, colors.size(), GLOW.length);

        for (int color : colors) {
            ResourceLocation cid = ResourceLocation.fromNamespaceAndPath(
                    ReadStar.MODID, "environment/stars/color_" + color);
            NativeImage cImg = createTinted(core, color, 1.0f);
            try { output.add(cid, loader -> new SpriteContents(cid,
                    new FrameSize(WIDTH, HEIGHT), cImg, ResourceMetadata.EMPTY)); }
            catch (Throwable t) { cImg.close(); ReadStar.LOGGER.error("Failed core sprite for color {}", color, t); }

            for (int g = 0; g < GLOW.length; g++) {
                ResourceLocation gid = ResourceLocation.fromNamespaceAndPath(
                        ReadStar.MODID, "environment/stars/" + GLOW[g][1] + "_" + color);
                NativeImage gImg = createTinted(glows[g], color, glowMul);
                try { output.add(gid, loader -> new SpriteContents(gid,
                        new FrameSize(WIDTH, HEIGHT), gImg, ResourceMetadata.EMPTY)); }
                catch (Throwable t) { gImg.close(); ReadStar.LOGGER.error("Failed {} sprite for color {}",
                        GLOW[g][1], color, t); }
            }
        }
    }

    @Override
    public SpriteSourceType type() { return TYPE; }
}
