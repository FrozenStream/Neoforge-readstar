package git.frozenstream.readstar.sprite;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import git.frozenstream.readstar.ReadStar;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.MoonPhase;

import java.io.InputStream;
import java.util.Optional;

/**
 * 运行时从原版 minecraft:textures/environment/celestial/moon/ 读取 32×32 月相贴图，
 * 裁剪中央 8×8 核心区域，作为精灵注入 CELESTIAL_ATLAS_INFO 图集。
 * 精灵 ID 格式：environment/celestial/non-luminous/moon/{phase_name}
 */
public record MoonSpriteSource() implements SpriteSource {

    public static final MapCodec<MoonSpriteSource> CODEC = MapCodec.unit(new MoonSpriteSource());

    /** 原版 32×32 月亮贴图居中裁剪 —— 月亮位于图像中央 */
    private static final int SRC_SIZE = 32;
    private static final int CROP_SIZE = 8;
    private static final int SRC_OFFSET_X = (SRC_SIZE - CROP_SIZE) / 2; // 12
    private static final int SRC_OFFSET_Y = (SRC_SIZE - CROP_SIZE) / 2; // 12

    @Override
    public void run(ResourceManager manager, Output output) {
        MoonPhase[] phases = MoonPhase.values();
        int generated = 0;

        for (MoonPhase phase : phases) {
            Identifier sourceId = Identifier.fromNamespaceAndPath(
                    "minecraft",
                    "textures/environment/celestial/moon/" + phase.getSerializedName() + ".png");

            Optional<Resource> res = manager.getResource(sourceId);
            if (res.isEmpty()) {
                ReadStar.LOGGER.warn("Vanilla moon texture not found: {}", sourceId);
                continue;
            }

            try (InputStream in = res.get().open()) {
                NativeImage source = NativeImage.read(in);

                // 裁剪中央 8×8 区域
                NativeImage cropped = new NativeImage(CROP_SIZE, CROP_SIZE, false);
                for (int y = 0; y < CROP_SIZE; y++) {
                    for (int x = 0; x < CROP_SIZE; x++) {
                        cropped.setPixel(x, y, source.getPixel(x + SRC_OFFSET_X, y + SRC_OFFSET_Y));
                    }
                }
                source.close();

                Identifier spriteId = Identifier.fromNamespaceAndPath(
                        ReadStar.MODID,
                        "environment/celestial/non-luminous/moon/" + phase.getSerializedName());

                output.add(spriteId, (DiscardableLoader) l -> new SpriteContents(spriteId,
                        new FrameSize(CROP_SIZE, CROP_SIZE), cropped));

                generated++;
            } catch (Exception e) {
                ReadStar.LOGGER.error("Failed to generate moon sprite for {}: {}",
                        phase.getSerializedName(), e.getMessage());
            }
        }

        ReadStar.LOGGER.info("Generated {} cropped moon sprites from vanilla textures", generated);
    }

    @Override
    public MapCodec<? extends SpriteSource> codec() {
        return CODEC;
    }
}
