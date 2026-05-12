package git.frozenstream.readstar.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.elements.CelestialBodyManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端向客户端发送行星系统配置的网络包
 * 直接传输原始 JSON 字符串，由客户端负责解析
 */
public record CelestialSystemPayload(String jsonData) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ReadStar.MODID, "planet_system");
    public static final Type<CelestialSystemPayload> TYPE = new Type<>(ID);
    
    private static final Gson GSON = new Gson();
    
    /**
     * StreamCodec 用于序列化和反序列化数据包
     * 直接传输原始 JSON 字符串
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CelestialSystemPayload> STREAM_CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,  // String 的编解码器
            CelestialSystemPayload::jsonData,  // 获取 jsonData 字段
            CelestialSystemPayload::new       // 构造函数引用
        );

    /**
     * 处理接收到的数据包（在客户端执行）
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ReadStar.LOGGER.info("PlanetSystemPayload: Received planet system data from server");
                
                // 解析 JSON 字符串为 JsonObject
                JsonObject jsonObject = GSON.fromJson(jsonData, JsonObject.class);
                
                // 调用 PlanetManager 单例进行初始化
                CelestialBodyManager.getInstance().initializeFromJson(jsonObject);
                
                ReadStar.LOGGER.info("PlanetSystemPayload: Successfully initialized planet system on client");
            } catch (Exception e) {
                ReadStar.LOGGER.error("PlanetSystemPayload: Failed to process planet system data", e);
            }
        }).exceptionally(e -> {
            ReadStar.LOGGER.error("处理行星系统数据包时出错", e);
            return null;
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
