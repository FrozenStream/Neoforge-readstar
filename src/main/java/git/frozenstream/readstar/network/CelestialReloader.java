package git.frozenstream.readstar.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import git.frozenstream.readstar.ReadStar;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;

/**
 * 行星系统资源重载监听器（服务端）
 * 负责从 JSON 文件读取行星配置，并在加载完成后发送给所有客户端
 */
public class CelestialReloader extends SimpleJsonResourceReloadListener<JsonObject> {

    private static final FileToIdConverter LISTER = FileToIdConverter.json("celestial");
    private static final Identifier SYSTEM_ID = Identifier.fromNamespaceAndPath(ReadStar.MODID, "system");
    private static final Gson GSON = new Gson();
    
    // 缓存最后一次加载的行星系统数据
    private static String cachedPlanetData = null;
    
    /**
     * 获取缓存的行星系统数据
     */
    public static String getCachedPlanetData() {
        return cachedPlanetData;
    }

    // Codec for JsonObject - use JsonOps directly
    private static final Codec<JsonObject> JSON_OBJECT_CODEC = Codec.PASSTHROUGH.xmap(
        dynamic -> (JsonObject) dynamic.getValue(),
        jsonObject -> new com.mojang.serialization.Dynamic<>(JsonOps.INSTANCE, jsonObject)
    );

    public CelestialReloader() {
        super(
            JSON_OBJECT_CODEC,
            LISTER
        );
    }

    @Override
    protected void apply(Map<Identifier, JsonObject> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        ReadStar.LOGGER.info("PlanetReloader: prepared map size: {}", prepared.size());

        JsonObject jsonObject = prepared.get(SYSTEM_ID);
        if (jsonObject == null) {
            ReadStar.LOGGER.error("PlanetReloader: Could not find system.json with identifier: {}", SYSTEM_ID);
            return;
        }

        // 直接发送原始 JSON 字符串（保留格式）
        String jsonString = GSON.toJson(jsonObject);
        
        // 缓存数据，用于后续发送给新登录的玩家
        cachedPlanetData = jsonString;
        ReadStar.LOGGER.info("PlanetReloader: Cached planet system data");

        // 尝试立即发送给所有在线玩家（如果服务器已就绪）
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !server.isSameThread()) {
            // 如果不在主线程，调度到主线程执行
            server.execute(() -> {
                sendToAllPlayers(jsonString);
            });
        } else if (server != null) {
            // 已经在主线程，直接发送
            sendToAllPlayers(jsonString);
        } else {
            ReadStar.LOGGER.info("PlanetReloader: Server not ready yet, data will be sent when players login");
        }
    }
    
    /**
     * 向所有在线玩家发送行星系统数据
     */
    private void sendToAllPlayers(String jsonData) {
        PacketDistributor.sendToAllPlayers(new CelestialSystemPayload(jsonData));
        ReadStar.LOGGER.info("PlanetReloader: Sent planet system data to all clients");
    }
}
