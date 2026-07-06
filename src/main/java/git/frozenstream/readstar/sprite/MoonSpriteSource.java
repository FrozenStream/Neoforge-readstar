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
import git.frozenstream.readstar.skybox.MoonPhase;

import java.io.InputStream;
import java.util.Optional;

/**
 * 从原版读取月相贴图，裁剪中央 8×8 注入图集。1.21.1 版本。
 * 原版 moon_phase.png 是 4×2 的小图集（每个子图 32×32），而不是分散的独立文件。
 */
public record MoonSpriteSource() implements SpriteSource {

    /** 图集列数 */
    private static final int ATLAS_COLS = 4;
    /** 图集行数 */
    private static final int ATLAS_ROWS = 2;
    /** 每个子图的尺寸 */
    private static final int CELL_SIZE = 32;
    /** 裁剪后的尺寸 */
    private static final int CROP_SIZE = 8;
    /** 从子图左上角到裁剪区域的偏移 */
    private static final int OFF_X = (CELL_SIZE - CROP_SIZE) / 2;
    private static final int OFF_Y = (CELL_SIZE - CROP_SIZE) / 2;

    public static final MapCodec<MoonSpriteSource> CODEC = MapCodec.unit(new MoonSpriteSource());
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    @Override
    public void run(ResourceManager manager, Output output) {
        ResourceLocation atlasLoc = ResourceLocation.fromNamespaceAndPath(
                "minecraft", "textures/environment/moon_phases.png");
        Optional<Resource> res = manager.getResource(atlasLoc);
        if (res.isEmpty()) {
            ReadStar.LOGGER.error("Vanilla moon phase atlas not found: {}", atlasLoc);
            return;
        }

        int generated = 0;
        try (InputStream in = res.get().open()) {
            NativeImage atlas = NativeImage.read(in);

            for (MoonPhase phase : MoonPhase.values()) {
                int idx = phase.index();
                int col = idx % ATLAS_COLS;
                int row = idx / ATLAS_COLS;
                int baseX = col * CELL_SIZE;
                int baseY = row * CELL_SIZE;

                NativeImage cropped = new NativeImage(CROP_SIZE, CROP_SIZE, false);
                for (int y = 0; y < CROP_SIZE; y++)
                    for (int x = 0; x < CROP_SIZE; x++)
                        cropped.setPixelRGBA(x, y,
                                atlas.getPixelRGBA(baseX + OFF_X + x, baseY + OFF_Y + y));

                ResourceLocation sid = ResourceLocation.fromNamespaceAndPath(
                        ReadStar.MODID,
                        "environment/celestial/non-luminous/moon/" + phase.getSerializedName());
                output.add(sid, loader -> new SpriteContents(sid,
                        new FrameSize(CROP_SIZE, CROP_SIZE), cropped, ResourceMetadata.EMPTY));
                generated++;
            }
            atlas.close();
        } catch (Exception e) {
            ReadStar.LOGGER.error("Failed to generate moon sprites: {}", e.getMessage());
        }
        ReadStar.LOGGER.info("Generated {} cropped moon sprites from vanilla atlas", generated);
    }

    @Override
    public SpriteSourceType type() { return TYPE; }
}
