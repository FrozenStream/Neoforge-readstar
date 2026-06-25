package git.frozenstream.readstar.skybox;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.*;
import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.elements.CelestialBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class ReadstarSkyboxRenderer implements CustomSkyboxRenderer, ResourceManagerReloadListener {
    private static final ReadstarSkyboxRenderer INSTANCE = new ReadstarSkyboxRenderer();

    public static ReadstarSkyboxRenderer getInstance() {
        return INSTANCE;
    }

    private ReadstarSkyRenderer skyRenderer = null;
    private CelestialBody observer;
    private List<ReadstarSkyRenderer.Star> stars;
    public List<ReadstarSkyRenderer.Star> brightstars;

    private ReadstarSkyboxRenderer() {}

    public CelestialBody getObserver() { return observer; }
    public void setObserver(CelestialBody observer) { this.observer = observer; }

    /** 获取当前天空渲染器实例（供 HUD 等外部调用） */
    public ReadstarSkyRenderer getSkyRenderer() {
        return skyRenderer;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        if (this.skyRenderer != null) {
            this.skyRenderer.close();
        }

        this.stars = parseStars(resourceManager, 6.5f);
        this.brightstars = filterStars(6);
        Minecraft minecraft = Minecraft.getInstance();
        this.skyRenderer = new ReadstarSkyRenderer(minecraft.getTextureManager(), minecraft.getAtlasManager());
        this.skyRenderer.buildStarsBuffer(this.stars);
    }

    /**
     * 扫描 stars/ 目录下所有 .json 文件，合并解析星表数据，返回不可变列表。
     */
    private static List<ReadstarSkyRenderer.Star> parseStars(ResourceManager resourceManager, float maxVmag) {
        List<ReadstarSkyRenderer.Star> result = new ArrayList<>();

        Map<Identifier, Resource> starResources = resourceManager.listResources(
                "stars", id -> id.getPath().endsWith(".json"));

        if (starResources.isEmpty()) {
            ReadStar.LOGGER.warn("No star data files found in stars/");
            return List.of();
        }

        for (Map.Entry<Identifier, Resource> entry : starResources.entrySet()) {
            Identifier resPath = entry.getKey();
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(),
                    StandardCharsets.UTF_8)) {
                JsonArray starsArray = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("Stars");
                if (starsArray == null) {
                    ReadStar.LOGGER.warn("No 'Stars' array in: {}", resPath);
                    continue;
                }
                int before = result.size();
                for (int i = 0; i < starsArray.size(); i++) {
                    JsonObject obj = starsArray.get(i).getAsJsonObject();
                    JsonArray pos = obj.getAsJsonArray("position");
                    float px = pos.get(0).getAsFloat();
                    float py = pos.get(1).getAsFloat();
                    float pz = pos.get(2).getAsFloat();
                    String name = obj.get("name").getAsString();
                    float vmag = obj.get("Vmag").getAsFloat();
                    int color = obj.get("color").getAsInt();
                    if (vmag > maxVmag)
                        continue;
                    result.add(new ReadstarSkyRenderer.Star(name, new Vector3f(px, py, pz).normalize(), vmag, color));
                }
                ReadStar.LOGGER.info("Parsed {} stars from {}", result.size() - before, resPath);
            } catch (Exception e) {
                ReadStar.LOGGER.error("Failed to load star data from {}: {}", resPath, e.getMessage());
            }
        }

        ReadStar.LOGGER.info("Total parsed {} stars from {} file(s)", result.size(), starResources.size());
        return List.copyOf(result);
    }


    public void rebuildStarswithVmag(float vmag) {
        this.stars = parseStars(Minecraft.getInstance().getResourceManager(), vmag);
        this.brightstars = filterStars(6);
        this.skyRenderer.buildStarsBuffer(this.stars);
    }

    private List<ReadstarSkyRenderer.Star> filterStars(float vmag){
        return this.stars.stream().filter(star -> star.vmag() <= vmag).toList();
    }

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState, Matrix4fc modelViewMatrix, Runnable setupFog) {
        setupFog.run();
        SkyRenderState state = skyRenderState;
        if (state.skybox == DimensionType.Skybox.END) {
            skyRenderer.renderEndSky();
            if (state.endFlashIntensity > 1.0E-5F) {
                PoseStack poseStack = new PoseStack();
                skyRenderer.renderEndFlash(poseStack, state.endFlashIntensity, state.endFlashXAngle, state.endFlashYAngle);
            }
        } else {
            PoseStack poseStack = new PoseStack();
            // 1. 天空底色 — 天球图纹理（跟随 observer 天球坐标系旋转）
            skyRenderer.renderSkyDisc(state.skyColor, this.observer);
            skyRenderer.renderSunriseAndSunset(poseStack, state.sunAngle, state.sunriseAndSunsetColor);
            // 2. 天体 + 星星
            skyRenderer.renderCelestialAndStars(poseStack, state.rainBrightness, state.starBrightness, this.observer, levelRenderState.gameTime);
            // ===== METEORS (在 frameQuat 框架内渲染) =====
            skyRenderer.buildAndRenderMeteors(poseStack, state.starBrightness, levelRenderState.gameTime);
            // 3. 大气散射叠加层（平滑衰减，夜晚自动透明）
            skyRenderer.renderAtmosphereOverlay(this.observer, state.skyColor);
            if (state.shouldRenderDarkDisc) {
                skyRenderer.renderDarkDisc();
            }
        }
        
        return true;
    }
}
