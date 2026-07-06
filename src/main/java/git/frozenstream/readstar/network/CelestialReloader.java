package git.frozenstream.readstar.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.elements.CelestialBodyManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;

/**
 * 行星系统资源重载监听器（服务端） — 1.21.1 版本。
 */
public class CelestialReloader extends SimpleJsonResourceReloadListener {

    private static final ResourceLocation SYSTEM_ID = ResourceLocation.fromNamespaceAndPath(ReadStar.MODID, "system");
    private static final Gson GSON = new Gson();
    
    private static String cachedPlanetData = null;
    
    public static String getCachedPlanetData() {
        return cachedPlanetData;
    }

    public CelestialReloader() {
        super(GSON, "celestial");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        ReadStar.LOGGER.info("PlanetReloader: prepared map size: {}", prepared.size());

        JsonElement element = prepared.get(SYSTEM_ID);
        if (element == null || !element.isJsonObject()) {
            ReadStar.LOGGER.error("PlanetReloader: Could not find system.json with id: {}", SYSTEM_ID);
            return;
        }
        JsonObject jsonObject = element.getAsJsonObject();

        String jsonString = GSON.toJson(jsonObject);
        
        cachedPlanetData = jsonString;
        ReadStar.LOGGER.info("PlanetReloader: Cached planet system data");

        CelestialBodyManager.getInstance().initializeFromJson(jsonObject);

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !server.isSameThread()) {
            server.execute(() -> sendToAllPlayers(jsonString));
        } else if (server != null) {
            sendToAllPlayers(jsonString);
        } else {
            ReadStar.LOGGER.info("PlanetReloader: Server not ready yet, data will be sent when players login");
        }
    }
    
    private void sendToAllPlayers(String jsonData) {
        PacketDistributor.sendToAllPlayers(new CelestialSystemPayload(jsonData));
        ReadStar.LOGGER.info("PlanetReloader: Sent planet system data to all clients");
    }
}
