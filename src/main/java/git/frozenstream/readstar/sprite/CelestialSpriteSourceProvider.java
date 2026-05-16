package git.frozenstream.readstar.sprite;

import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.ReadStarClient;
import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.data.SpriteSourceProvider;

import java.util.concurrent.CompletableFuture;

public class CelestialSpriteSourceProvider extends SpriteSourceProvider {
    public CelestialSpriteSourceProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, ReadStar.MODID);
    }

    @Override
    protected void gather() {
        atlas(ReadStarClient.CELESTIAL_ATLAS_INFO)
                .addSource(new DirectoryLister("environment/celestial", "environment/celestial/"));

        atlas(ReadStarClient.STAR_ATLAS_INFO)
                .addSource(new StarSpriteSource());
    }
}
