package git.frozenstream.readstar.datagen;

import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.ReadStarClient;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.data.SpriteSourceProvider;

import java.util.concurrent.CompletableFuture;


public class CelestialSpriteSourceProvider extends SpriteSourceProvider {

    public CelestialSpriteSourceProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, ReadStar.MODID);
    }

    @Override
    protected void gather() {
        atlas(ReadStarClient.CELESTIAL_ATLAS_INFO)
                .addSource(new SingleFile(Identifier.fromNamespaceAndPath(ReadStar.MODID, "environment/celestialbody/earth")))
                .addSource(new SingleFile(Identifier.fromNamespaceAndPath(ReadStar.MODID, "environment/celestialbody/mars")))
                .addSource(new SingleFile(Identifier.fromNamespaceAndPath(ReadStar.MODID, "environment/celestialbody/mercury")))
                .addSource(new SingleFile(Identifier.fromNamespaceAndPath(ReadStar.MODID, "environment/celestialbody/venus")));

        // Star 图集：运行时由 StarSpriteSource 从 custom/stars/stars.json 驱动生成
        atlas(ReadStarClient.STAR_ATLAS_INFO)
                .addSource(new StarSpriteSource());
    }
}
