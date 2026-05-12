package git.frozenstream.readstar;

import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's semiMajorAxis good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    static final ModConfigSpec SPEC = BUILDER.build();

}
