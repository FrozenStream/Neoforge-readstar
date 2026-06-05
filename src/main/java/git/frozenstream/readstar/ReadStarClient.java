package git.frozenstream.readstar;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import git.frozenstream.readstar.elements.CelestialBodyManager;
import git.frozenstream.readstar.elements.CelestialBody;
import git.frozenstream.readstar.elements.MeteorCollector;
import git.frozenstream.readstar.skybox.ReadStarCloudsRenderer;
import git.frozenstream.readstar.skybox.ReadstarSkyboxRenderer;
import git.frozenstream.readstar.sprite.CelestialSpriteSourceProvider;
import git.frozenstream.readstar.sprite.StarSpriteSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
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
    /** 当前观测者（地球）的天体实例，由 skybox renderer 在每帧更新 */
    public static CelestialBody Observer;

    public static final Identifier CELESTIAL_ATLAS_TEXTURE = Identifier.fromNamespaceAndPath(ReadStar.MODID,
            "textures/atlas/celestial.png");
    public static final Identifier CELESTIAL_ATLAS_INFO = Identifier.fromNamespaceAndPath(ReadStar.MODID, "celestial");
    public static final Identifier STAR_ATLAS_TEXTURE = Identifier.fromNamespaceAndPath(ReadStar.MODID,
            "textures/atlas/star.png");
    public static final Identifier STAR_ATLAS_INFO = Identifier.fromNamespaceAndPath(ReadStar.MODID, "star");

    /**
     * 自定义管线：使用 star_fov shader，支持 Position(center) + Offset(billboard) 分离，
     * 通过 FovCompensation uniform 保持星点屏幕大小不受 FOV 影响。
     */
    public static RenderPipeline STAR_TEXTURED_PIPELINE;

    /** 自定义顶点元素：billboard 偏移量 (vec3 float)，存储 rotation × (方向 × 星点大小) */
    public static final VertexFormatElement OFFSET_ELEMENT = VertexFormatElement.register(
            VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.FLOAT, false, 3);

    /** 自定义顶点格式：Position(center) + UV0 + Color + Offset */
    public static final VertexFormat POSITION_TEX_COLOR_OFFSET = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("Offset", OFFSET_ELEMENT)
            .build();

    @SubscribeEvent
    static void onRegisterStarPipelines(RegisterRenderPipelinesEvent event) {
        // 自定义管线：使用 star_fov shader（分离 center + billboard offset），
        // FovCompensation 通过 DynamicTransforms.TextureMat[0][0] 传递。
        STAR_TEXTURED_PIPELINE = RenderPipeline
                .builder(new RenderPipeline.Snippet[] { RenderPipelines.MATRICES_PROJECTION_SNIPPET })
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "star_textured"))
                .withVertexShader(Identifier.fromNamespaceAndPath(ReadStar.MODID, "core/star_fov"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(ReadStar.MODID, "core/star_fov"))
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
                .withVertexFormat(POSITION_TEX_COLOR_OFFSET, Mode.QUADS)
                .build();
        event.registerPipeline(STAR_TEXTURED_PIPELINE);
        ReadStar.LOGGER.info("Registered custom star pipeline: readstar:star_textured (fov-aware)");
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
        var level = event.getLevel();
        var BlockPos = event.getCamera().blockPosition();

        // ==== 处理星光亮度 ====
        var starBrightness = event.getRenderState().skyRenderState.starBrightness;
        starBrightness = Math.min(1.0f, starBrightness * 5.f);
        var lighting = level.getMaxLocalRawBrightness(BlockPos);
        starBrightness = starBrightness * (1 - lighting / 20.f);
        var fov = event.getCamera().getFov();
        // FOV 缩小时提升亮度（星星更大但各项发光不变 → 需要更亮）
        double brightnessFactor = 1.0 + Config.STAR_FOV_BRIGHTNESS_STRENGTH.get() * Math.max(0.0, (70.0 - fov) / 70.0);
        event.getRenderState().skyRenderState.starBrightness = starBrightness * (float)brightnessFactor;

        // ==== 更新天体位姿 ====
        long gameTime = level.getGameTime();
        long daylightTime = level.getDefaultClockTime();
        CelestialBodyManager.getInstance().updatePositions(20 * gameTime);

        // ==== 设置观测者 ====
        if (level.dimension() == Level.OVERWORLD) {
            CelestialBody observer = CelestialBodyManager.getInstance().getCelestialBody("earth");
            if (observer != null) {
                Observer = observer;
                observer.updateCurrentVec(daylightTime);
            }
        }

        // ==== 处理 skycolor + 日食检测 ====
        int skyColor = event.getRenderState().skyRenderState.skyColor;

        CelestialBody obs = ReadStarClient.Observer;
        if (obs != null && obs.hostStar != null) {
            Vector3f observerPos = obs.position;
            float hostSize = CelestialBodyManager.getApparentSize(observerPos, obs.hostStar) / 200.f;
            float maxCoverage = 0f;

            for (CelestialBody child : obs.children) {
                Vector3f obsToChild = new Vector3f(child.position).sub(observerPos).normalize();
                Vector3f obsToHost = new Vector3f(obs.hostStar.position).sub(observerPos).normalize();
                float angSep = (float) Math.acos(Math.max(-1f, Math.min(1f, obsToChild.dot(obsToHost))));
                float childSize = CelestialBodyManager.getApparentSize(observerPos, child) / 200.f;

                if (angSep >= hostSize + childSize)
                    continue;

                float d = angSep, r1 = childSize, r2 = hostSize;
                float coverage;

                if (d + r2 <= r1) {
                    coverage = 1f; // 卫星完全遮住主星
                } else if (d + r1 <= r2) {
                    coverage = (r1 * r1) / (r2 * r2); // 卫星在主星盘面内
                } else {
                    float d2 = d * d, r1_2 = r1 * r1, r2_2 = r2 * r2;
                    float cos1 = Math.max(-1f, Math.min(1f, (d2 + r1_2 - r2_2) / (2f * d * r1)));
                    float cos2 = Math.max(-1f, Math.min(1f, (d2 + r2_2 - r1_2) / (2f * d * r2)));
                    float term1 = r1_2 * (float) Math.acos(cos1);
                    float term2 = r2_2 * (float) Math.acos(cos2);
                    float sqrtArg = Math.max(0f, (-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2));
                    float overlapArea = term1 + term2 - 0.5f * (float) Math.sqrt(sqrtArg);
                    coverage = overlapArea / ((float) Math.PI * r2_2);
                }

                if (coverage > maxCoverage)
                    maxCoverage = coverage;
            }

            if (maxCoverage > 0.1f) {
                ReadStar.LOGGER.debug("Coverage: {}", maxCoverage);
                float darkFactor = 1f - maxCoverage * 0.8f;
                int r = (int) (ARGB.red(skyColor) * darkFactor);
                int g = (int) (ARGB.green(skyColor) * darkFactor);
                int b = (int) (ARGB.blue(skyColor) * darkFactor);
                skyColor = ARGB.color(255, r, g, b);
            }
        }
        event.getRenderState().skyRenderState.skyColor = skyColor;

        // 设置 Collector 的当前维度（维度变化时会自动清空旧数据）
        MeteorCollector.getInstance().setCurrentDimension(level.dimension().identifier());
        MeteorCollector.getInstance().tick(gameTime);

        if (level.dimension() == Level.OVERWORLD) {
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
