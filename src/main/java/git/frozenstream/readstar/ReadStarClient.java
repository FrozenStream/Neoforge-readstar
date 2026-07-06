package git.frozenstream.readstar;

import com.mojang.brigadier.arguments.FloatArgumentType;

import git.frozenstream.readstar.blocks.entity.ArmillarySphereBlockEntity;
import git.frozenstream.readstar.blocks.renderer.ArmillarySphereRenderer;
import git.frozenstream.readstar.skybox.ReadstarDimensionEffects;
import git.frozenstream.readstar.skybox.ReadstarSkyboxRenderer;
import git.frozenstream.readstar.sprite.MoonSpriteSource;
import git.frozenstream.readstar.sprite.StarSpriteSource;
import git.frozenstream.readstar.sprite.SunSpriteSource;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterMaterialAtlasesEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ReadStar.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods
// in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ReadStar.MODID, value = Dist.CLIENT)
public class ReadStarClient {
    // 静态保存天空渲染器实例，以便在多个地方使用
    private static final ReadstarSkyboxRenderer skyboxRenderer = ReadstarSkyboxRenderer.getInstance();

    public static final ResourceLocation CELESTIAL_ATLAS_TEXTURE = ResourceLocation.fromNamespaceAndPath(ReadStar.MODID,
            "textures/atlas/celestial.png");
    public static final ResourceLocation CELESTIAL_ATLAS_INFO = ResourceLocation.fromNamespaceAndPath(ReadStar.MODID, "celestial");
    public static final ResourceLocation STAR_ATLAS_TEXTURE = ResourceLocation.fromNamespaceAndPath(ReadStar.MODID,
            "textures/atlas/star.png");
    public static final ResourceLocation STAR_ATLAS_INFO = ResourceLocation.fromNamespaceAndPath(ReadStar.MODID, "star");


    @SubscribeEvent
    static void onRegisterShaders(RegisterShadersEvent event) {
        ReadstarRenderPipelines.registerAll(event);
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
    static void onRegisterSpriteSourceTypes(net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent event) {
        event.register(
                ResourceLocation.fromNamespaceAndPath(ReadStar.MODID, "sun"),
                SunSpriteSource.TYPE);
        event.register(
                ResourceLocation.fromNamespaceAndPath(ReadStar.MODID, "moon_crop"),
                MoonSpriteSource.TYPE);
        event.register(
                ResourceLocation.fromNamespaceAndPath(ReadStar.MODID, "star"),
                StarSpriteSource.TYPE);
        ReadStar.LOGGER.info("Registered custom sprite source types");
    }

    @SubscribeEvent
    static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Level.OVERWORLD.location(), new ReadstarDimensionEffects());
        ReadStar.LOGGER.info("Registered ReadstarDimensionEffects for overworld");
    }

    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(skyboxRenderer);
    }

    // ==================== 浑天仪 BER 注册 ====================

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 确保 TYPE 已绑定（在 EntityRenderersEvent 时 DeferredRegister 已完成）
        ArmillarySphereBlockEntity.TYPE = ReadStar.ARMILLARY_SPHERE_BE.get();
        // 注册浑天仪方块实体渲染器
        event.registerBlockEntityRenderer(
                ArmillarySphereBlockEntity.TYPE,
                ArmillarySphereRenderer::new);
        ReadStar.LOGGER.info("ReadStarClient: Registered ArmillarySphereRenderer");
    }

    @SubscribeEvent
    public static void registerAtlases(RegisterMaterialAtlasesEvent event) {
        event.register(CELESTIAL_ATLAS_TEXTURE, CELESTIAL_ATLAS_INFO);
        event.register(STAR_ATLAS_TEXTURE, STAR_ATLAS_INFO);
    }

    /**
     * 注册客户端命令：/readstar skybox mag &lt;0~10&gt;
     * 按视星等阈值重建星星渲染缓冲
     */
    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("readstar").then(Commands.literal("mag")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 10.0f))
                                .executes(ctx -> {
                                    float mag = FloatArgumentType.getFloat(ctx, "value");
                                    skyboxRenderer.rebuildStarsWithMag(mag);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable(
                                                    "command.readstar.mag.success",
                                                    mag, mag),
                                            false);
                                    return 1;
                                }))));
    }

    /**
     * 委托给 ReadstarSkyRenderer 绘制天体坐标系指向 HUD。
     */
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        var renderer = skyboxRenderer.getSkyRenderer();
        if (renderer != null) {
            renderer.renderHud(event.getGuiGraphics(), skyboxRenderer.getObserver(), skyboxRenderer.brightstars);
        }
    }
}
