package git.frozenstream.readstar.network;

import git.frozenstream.readstar.ReadStar;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册和管理类
 */
@EventBusSubscriber
public class ReadStarNetwork {
    // 协议版本 - 用于确保客户端和服务端版本匹配
    private static final String PROTOCOL_VERSION = "1";

    /**
     * 注册所有的网络包处理器
     */
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        // 使用 PayloadRegistrar 注册包
        final PayloadRegistrar registrar = event.registrar(ReadStar.MODID)
                .versioned(PROTOCOL_VERSION)
                .optional(); // 标记为可选，这样即使对方没有这个mod也不会崩溃

        // 注册服务端到客户端的包
        // 使用 STREAM_CODEC 进行序列化/反序列化
        registrar.playToClient(
                ServerMessagePayload.TYPE,
                ServerMessagePayload.STREAM_CODEC,
                ServerMessagePayload::handle
        );

        // 注册行星系统配置数据包
        registrar.playToClient(
                CelestialSystemPayload.TYPE,
                CelestialSystemPayload.STREAM_CODEC,
                CelestialSystemPayload::handle
        );

        ReadStar.LOGGER.info("已注册 ReadStar 网络包处理器");
    }
}
