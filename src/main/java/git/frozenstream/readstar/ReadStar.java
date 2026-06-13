package git.frozenstream.readstar;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import git.frozenstream.readstar.blocks.ArmillarySphereBlock;
import git.frozenstream.readstar.blocks.entity.ArmillarySphereBlockEntity;
import git.frozenstream.readstar.network.MeteorLauncher;
import git.frozenstream.readstar.network.NetworkHelper;
import git.frozenstream.readstar.network.CelestialReloader;
import git.frozenstream.readstar.network.CelestialSystemPayload;
import git.frozenstream.readstar.command.TestMessageCommand;
import net.neoforged.neoforge.network.PacketDistributor;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(ReadStar.MODID)
public class ReadStar {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "readstar";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // ==================== DeferredRegister ====================

    /** 方块注册器 */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    /** 物品注册器 */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    /** 方块实体注册器 */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    // ==================== 方块 ====================

    /** 浑天仪方块 */
    public static final DeferredBlock<ArmillarySphereBlock> ARMILLARY_SPHERE_BLOCK = BLOCKS.register(
            "armillary_sphere",
            () -> new ArmillarySphereBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MODID, "armillary_sphere")))
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
                    .noOcclusion()
            ));

    /** 浑天仪物品 */
    public static final DeferredItem<BlockItem> ARMILLARY_SPHERE_ITEM = ITEMS.registerSimpleBlockItem(
            ARMILLARY_SPHERE_BLOCK);

    // ==================== 方块实体 ====================

    /** 浑天仪方块实体类型 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArmillarySphereBlockEntity>>
            ARMILLARY_SPHERE_BE = BLOCK_ENTITIES.register(
            "armillary_sphere",
            () -> new BlockEntityType<>(
                    ArmillarySphereBlockEntity::new,
                    ARMILLARY_SPHERE_BLOCK.get()
            ));

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public ReadStar(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // 注册方块、物品、方块实体到 mod event bus
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        NeoForge.EVENT_BUS.register(this);

        // 注册服务端流星发射器
        NeoForge.EVENT_BUS.register(new MeteorLauncher());

        // // Register the item to semiMajorAxis creative tab
        // modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }
    }

    // // Add the example block item to the building blocks tab
    // private void addCreative(BuildCreativeModeTabContentsEvent event) {
    //     if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
    //         event.accept(EXAMPLE_BLOCK_ITEM);
    //     }
    // }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    @SubscribeEvent
    public void onRegisterServerReloadListeners(AddServerReloadListenersEvent event) {
        Identifier PLANET_SYSTEM_ID = Identifier.fromNamespaceAndPath(ReadStar.MODID, "celestial/server");
        event.addListener(PLANET_SYSTEM_ID, new CelestialReloader());
    }


    /**
     * 当玩家登录游戏时触发
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        var player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            // 向刚登录的玩家发送个人欢迎消息
            NetworkHelper.sendMessageToPlayer(
                    serverPlayer,
                    "你好 %s！感谢使用 Read Star 模组。",
                    serverPlayer.getName().getString()
            );
            
            // 发送行星系统数据给新登录的玩家
            String planetData = CelestialReloader.getCachedPlanetData();
            if (planetData != null) {
                PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new CelestialSystemPayload(planetData)
                );
                LOGGER.info("Sent cached planet system data to player: {}", serverPlayer.getName().getString());
            }
        }
    }

    /**
     * 注册命令
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TestMessageCommand.register(event.getDispatcher());
    }
}