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
 */
public record MoonSpriteSource() implements SpriteSource {

    private static final int SRC_SIZE = 32, CROP_SIZE = 8;
    private static final int OFF_X = (SRC_SIZE - CROP_SIZE) / 2;
    private static final int OFF_Y = (SRC_SIZE - CROP_SIZE) / 2;

    public static final MapCodec<MoonSpriteSource> CODEC = MapCodec.unit(new MoonSpriteSource());
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    @Override
    public void run(ResourceManager manager, Output output) {
        int generated = 0;
        for (MoonPhase phase : MoonPhase.values()) {
            ResourceLocation src = ResourceLocation.fromNamespaceAndPath(
                    "minecraft", "textures/environment/celestial/moon/"
                            + phase.getSerializedName() + ".png");
            Optional<Resource> res = manager.getResource(src);
            if (res.isEmpty()) {
                ReadStar.LOGGER.warn("Vanilla moon texture not found: {}", src);
                continue;
            }
            try (InputStream in = res.get().open()) {
                NativeImage source = NativeImage.read(in);
                NativeImage cropped = new NativeImage(CROP_SIZE, CROP_SIZE, false);
                for (int y = 0; y < CROP_SIZE; y++)
                    for (int x = 0; x < CROP_SIZE; x++)
                        cropped.setPixelRGBA(x, y, source.getPixelRGBA(x + OFF_X, y + OFF_Y));
                source.close();

                ResourceLocation sid = ResourceLocation.fromNamespaceAndPath(
                        ReadStar.MODID,
                        "environment/celestial/non-luminous/moon/" + phase.getSerializedName());
                output.add(sid, loader -> new SpriteContents(sid,
                        new FrameSize(CROP_SIZE, CROP_SIZE), cropped, ResourceMetadata.EMPTY));
                generated++;
            } catch (Exception e) {
                ReadStar.LOGGER.error("Failed moon sprite for {}: {}",
                        phase.getSerializedName(), e.getMessage());
            }
        }
        ReadStar.LOGGER.info("Generated {} cropped moon sprites from vanilla textures", generated);
    }

    @Override
    public SpriteSourceType type() { return TYPE; }
}
