package git.frozenstream.readstar;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import git.frozenstream.readstar.elements.CelestialBodyManager;
import git.frozenstream.readstar.elements.CelestialBody;
import git.frozenstream.readstar.skybox.ReadStarCloudsRenderer;
import git.frozenstream.readstar.skybox.ReadstarSkyboxRenderer;
import git.frozenstream.readstar.sprite.CelestialSpriteSourceProvider;
import git.frozenstream.readstar.sprite.StarSpriteSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
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
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;
import net.neoforged.neoforge.client.event.RegisterTextureAtlasesEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.data.event.GatherDataEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ReadStar.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods
// in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ReadStar.MODID, value = Dist.CLIENT)
public class ReadStarClient {
    // 静态保存天空渲染器实例，以便在多个地方使用
    private static final ReadstarSkyboxRenderer skyboxRenderer = new ReadstarSkyboxRenderer();
    public static final Identifier CELESTIAL_ATLAS_TEXTURE = Identifier.fromNamespaceAndPath(ReadStar.MODID,
            "textures/atlas/celestial.png");
    public static final Identifier CELESTIAL_ATLAS_INFO = Identifier.fromNamespaceAndPath(ReadStar.MODID, "celestial");
    public static final Identifier STAR_ATLAS_TEXTURE = Identifier.fromNamespaceAndPath(ReadStar.MODID,
            "textures/atlas/star.png");
    public static final Identifier STAR_ATLAS_INFO = Identifier.fromNamespaceAndPath(ReadStar.MODID, "star");

    /** 自定义管线：与 CELESTIAL 相同但使用 POSITION_TEX_COLOR（支持逐星亮度 via setColor） */
    public static RenderPipeline STAR_TEXTURED_PIPELINE;

    @SubscribeEvent
    static void onRegisterStarPipelines(RegisterRenderPipelinesEvent event) {
        // 参照 END_SKY（用 core/position_tex_color + POSITION_TEX_COLOR），但 blend 改为
        // OVERLAY（与 CELESTIAL 一致）
        STAR_TEXTURED_PIPELINE = RenderPipeline
                .builder(new RenderPipeline.Snippet[] { RenderPipelines.MATRICES_PROJECTION_SNIPPET })
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "star_textured"))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.QUADS)
                .build();
        event.registerPipeline(STAR_TEXTURED_PIPELINE);
        ReadStar.LOGGER.info("Registered custom star pipeline: readstar:star_textured");
    }

    public ReadStarClient(ModContainer container) {
        // Allows NeoForge to create semiMajorAxis config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your
        // mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json
        // file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onRegisterSpriteSources(RegisterSpriteSourcesEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(ReadStar.MODID, "star"),
                StarSpriteSource.CODEC);
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
        long gameTime = event.getLevel().getGameTime();
        long daylightTime = event.getLevel().getDefaultClockTime();

        CelestialBodyManager.getInstance().updatePositions(gameTime);
        if (CelestialBodyManager.getInstance().hasCelestialBody("earth")) {
            CelestialBody earth = CelestialBodyManager.getInstance().getCelestialBody("earth");
            skyboxRenderer.updateObserver(earth, daylightTime);
        }
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
        // 仅注册单个 provider（Celestial provider 同时负责 star atlas），避免数据生成阶段 Duplicate
        // provider 错误
        event.createProvider(CelestialSpriteSourceProvider::new);
    }
}
