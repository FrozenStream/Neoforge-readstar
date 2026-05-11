package git.frozenstream.readstar;

import git.frozenstream.readstar.datagen.CelestialSpriteSourceProvider;
import git.frozenstream.readstar.datagen.StarSpriteSource;
import git.frozenstream.readstar.skybox.ReadStarCloudsRenderer;
import git.frozenstream.readstar.skybox.ReadstarSkyboxRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;
import net.neoforged.neoforge.client.event.RegisterTextureAtlasesEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.data.event.GatherDataEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ReadStar.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ReadStar.MODID, value = Dist.CLIENT)
public class ReadStarClient {
    // 静态保存天空渲染器实例，以便在多个地方使用
    private static final ReadstarSkyboxRenderer skyboxRenderer = new ReadstarSkyboxRenderer();
    public static final Identifier CELESTIAL_ATLAS_TEXTURE = Identifier.fromNamespaceAndPath(ReadStar.MODID, "textures/atlas/celestial.png");
    public static final Identifier CELESTIAL_ATLAS_INFO = Identifier.fromNamespaceAndPath(ReadStar.MODID, "celestial");
    public static final Identifier STAR_ATLAS_TEXTURE = Identifier.fromNamespaceAndPath(ReadStar.MODID, "textures/atlas/star.png");
    public static final Identifier STAR_ATLAS_INFO = Identifier.fromNamespaceAndPath(ReadStar.MODID, "star");


    public ReadStarClient(ModContainer container) {
        // Allows NeoForge to create semiMajorAxis config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onRegisterSpriteSources(RegisterSpriteSourcesEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(ReadStar.MODID, "star"),
                StarSpriteSource.CODEC
        );
        ReadStar.LOGGER.info("Registered sprite-source codec: readstar:star via event");
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        ReadStar.LOGGER.info("HELLO FROM CLIENT SETUP");
        ReadStar.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void onRegisterReloadListeners(AddClientReloadListenersEvent event) {
        Identifier skyId = Identifier.fromNamespaceAndPath(ReadStar.MODID, "skybox");
        event.addListener(skyId, skyboxRenderer);
    }

    @SubscribeEvent
    static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        if (event.getLevel().dimension() == Level.OVERWORLD) {
            event.getRenderState().customSkyboxRenderer = skyboxRenderer;
            event.getRenderState().customCloudsRenderer = new ReadStarCloudsRenderer();
        }
    }

    @SubscribeEvent
     public static void registerAtlases(RegisterTextureAtlasesEvent event) {
        event.register(new AtlasManager.AtlasConfig(CELESTIAL_ATLAS_TEXTURE, CELESTIAL_ATLAS_INFO, false));
        event.register(new AtlasManager.AtlasConfig(STAR_ATLAS_TEXTURE, STAR_ATLAS_INFO, false));
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        // 仅注册单个 provider（Celestial provider 同时负责 star atlas），避免数据生成阶段 Duplicate provider 错误
        event.createProvider(CelestialSpriteSourceProvider::new);
    }
}
