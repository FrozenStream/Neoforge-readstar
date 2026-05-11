package git.frozenstream.readstar.datagen;

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
import java.util.Optional;
import java.util.Set;

/**
 * 运行时从 stars.json 收集所有不重复的颜色值，为每种颜色生成一张染色光晕子图。
 * 子图 ID 格式：environment/stars/color_<colorInt>。
 */
public record StarSpriteSource() implements SpriteSource {

    static final int WIDTH = 32;
    static final int HEIGHT = 32;

    public static final MapCodec<StarSpriteSource> CODEC = MapCodec.unit(new StarSpriteSource());

    /** 预生成的灰度 RGBA 基准图（32×32，R=G=B=亮度，保留 alpha） */
    private static final Identifier BASE_TEXTURE_PATH =
            Identifier.fromNamespaceAndPath(ReadStar.MODID, "textures/environment/star/star_base.png");

    private static float[][] loadBasePattern(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resOpt = resourceManager.getResource(BASE_TEXTURE_PATH);
        if (resOpt.isEmpty()) {
            throw new RuntimeException("Missing required texture: " + BASE_TEXTURE_PATH);
        }
        try (InputStream in = resOpt.get().open()) {
            NativeImage source = NativeImage.read(in);
            try {
                float[][] pattern = new float[WIDTH][HEIGHT];
                int w = Math.min(source.getWidth(), WIDTH);
                int h = Math.min(source.getHeight(), HEIGHT);
                for (int x = 0; x < w; x++) {
                    for (int y = 0; y < h; y++) {
                        int pixel = source.getPixel(x, y);
                        pattern[x][y] = ((pixel >> 16) & 0xFF) / 255.0f;
                    }
                }
                return pattern;
            } finally {
                source.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load base star texture: " + BASE_TEXTURE_PATH, e);
        }
    }

    private static NativeImage createTintedImage(float[][] pattern, int starColor) {
        NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
        int colorR = (starColor >> 16) & 0xFF;
        int colorG = (starColor >> 8) & 0xFF;
        int colorB = starColor & 0xFF;
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                float intensity = pattern[x][y];
                int r = (int) (colorR * intensity);
                int g = (int) (colorG * intensity);
                int b = (int) (colorB * intensity);
                image.setPixel(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        float[][] basePattern = loadBasePattern(resourceManager);

        Identifier starsDataPath = Identifier.fromNamespaceAndPath(ReadStar.MODID, "custom/stars/stars.json");
        Optional<net.minecraft.server.packs.resources.Resource> resourceOpt = resourceManager.getResource(starsDataPath);
        if (resourceOpt.isEmpty()) {
            ReadStar.LOGGER.warn("Star data file not found: {}", starsDataPath);
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonArray starsArray = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("Stars");

            // 收集不重复的颜色值
            Set<Integer> uniqueColors = new HashSet<>();
            for (int i = 0; i < starsArray.size(); i++) {
                JsonObject starObj = starsArray.get(i).getAsJsonObject();
                if (starObj.has("color")) {
                    uniqueColors.add(starObj.get("color").getAsInt());
                }
            }

            ReadStar.LOGGER.info("Generating {} tinted glow sprites ({} unique colors)", uniqueColors.size(), uniqueColors.size());

            for (int color : uniqueColors) {
                Identifier spriteId = Identifier.fromNamespaceAndPath(
                        ReadStar.MODID, "environment/stars/color_" + color);
                NativeImage image = createTintedImage(basePattern, color);
                try {
                    FrameSize size = new FrameSize(WIDTH, HEIGHT);
                    SpriteContents contents = new SpriteContents(spriteId, size, image);
                    output.add(spriteId, (SpriteSource.DiscardableLoader) loader -> contents);
                } catch (Throwable t) {
                    image.close();
                    ReadStar.LOGGER.error("Failed to create sprite for color {}: {}", color, t.getMessage());
                }
            }
        } catch (Exception e) {
            ReadStar.LOGGER.error("Failed to read star data from {}: {}", starsDataPath, e.getMessage());
        }
    }

    @Override
    public MapCodec<? extends SpriteSource> codec() {
        return CODEC;
    }
}
