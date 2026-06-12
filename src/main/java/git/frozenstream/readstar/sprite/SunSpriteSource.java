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

import java.io.InputStream;
import java.util.Optional;

/**
 * 运行时从原版 minecraft:textures/environment/celestial/sun.png 读取太阳贴图，
 * 作为精灵注入 CELESTIAL_ATLAS_INFO 图集。
 * 精灵 ID：environment/celestial/luminous/sun
 */
public record SunSpriteSource() implements SpriteSource {

    public static final MapCodec<SunSpriteSource> CODEC = MapCodec.unit(new SunSpriteSource());

    /** 原版 32×32 太阳贴图居中裁剪 —— 太阳位于图像中央 */
    private static final int SRC_SIZE = 32;
    private static final int CROP_SIZE = 8;
    private static final int SRC_OFFSET_X = (SRC_SIZE - CROP_SIZE) / 2; // 12
    private static final int SRC_OFFSET_Y = (SRC_SIZE - CROP_SIZE) / 2; // 12

    @Override
    public void run(ResourceManager manager, Output output) {
        Identifier sourceId = Identifier.fromNamespaceAndPath(
                "minecraft", "textures/environment/celestial/sun.png");

        Optional<Resource> res = manager.getResource(sourceId);
        if (res.isEmpty()) {
            ReadStar.LOGGER.warn("Vanilla sun texture not found: {}", sourceId);
            return;
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
                    ReadStar.MODID, "environment/celestial/luminous/sun");

            output.add(spriteId, (DiscardableLoader) l -> new SpriteContents(spriteId,
                    new FrameSize(CROP_SIZE, CROP_SIZE), cropped));

            ReadStar.LOGGER.info("Registered cropped sun sprite in atlas");
        } catch (Exception e) {
            ReadStar.LOGGER.error("Failed to register sun sprite: {}", e.getMessage());
        }
    }

    @Override
    public MapCodec<? extends SpriteSource> codec() {
        return CODEC;
    }
}
