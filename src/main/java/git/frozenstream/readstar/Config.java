package git.frozenstream.readstar;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    // ========== 渲染参数 ==========

    public static final ModConfigSpec.DoubleValue STAR_CORE_SIZE = BUILDER
            .comment("Star core quad size multiplier (base size before brightness scaling). Default: 0.648")
            .defineInRange("starCoreSize", 0.75, 0.01, 10.0);

    public static final ModConfigSpec.DoubleValue STAR_GLOW_SIZE = BUILDER
            .comment("Star glow quad size multiplier for bright stars (Vmag < 2.0). Default: 1.5")
            .defineInRange("starGlowSize", 2, 0.01, 10.0);

    public static final ModConfigSpec.DoubleValue STAR_FOV_COMPENSATION_STRENGTH = BUILDER
            .comment("FOV compensation strength for star billboard size.\n"
                    + "1.0 = full compensation (stars keep same screen size regardless of FOV).\n"
                    + "0.0 = no compensation (stars change size with FOV).\n"
                    + "Values > 1.0 exaggerate the effect. Default: 0.8")
            .defineInRange("starFovCompensationStrength", 0.8, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STAR_FOV_BRIGHTNESS_STRENGTH = BUILDER
            .comment("FOV brightness boost for stars.\n"
                    + "Smaller FOV (zoomed in) → stars appear larger/dimmer → boost brightness.\n"
                    + "0.0 = no FOV brightness effect.\n"
                    + "1.0 = moderate boost. Default: 1.0")
            .defineInRange("starFovBrightnessStrength", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CELESTIAL_APPARENT_SIZE_FACTOR = BUILDER
            .comment("Celestial body apparent size calculation factor. Larger values make all bodies appear larger. Default: 4000.0")
            .defineInRange("celestialApparentSizeFactor", 1000.0, 1.0, 100000.0);

    public static final ModConfigSpec.DoubleValue CELESTIAL_APPARENT_SIZE_MIN = BUILDER
            .comment("Minimum apparent size clamp for celestial bodies. Prevents bodies from becoming too small when far away. Default: 1.024")
            .defineInRange("celestialApparentSizeMin", 1.024, 0.001, 100.0);

    static final ModConfigSpec SPEC = BUILDER.build();

}
