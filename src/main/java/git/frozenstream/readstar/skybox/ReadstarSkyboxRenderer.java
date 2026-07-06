package git.frozenstream.readstar.skybox;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.ReadStarClient;
import git.frozenstream.readstar.elements.CelestialBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.util.FastColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义天空盒渲染器 —— 1.21.1 版本。
 * <p>
 * 通过 NeoForge 的 CustomSkyboxRenderer 接口注册（如果有），
 * 或由外部手动调用 renderSky()。
 */
public class ReadstarSkyboxRenderer implements ResourceManagerReloadListener {
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
        ModelManager modelManager = minecraft.getModelManager();
        this.skyRenderer = new ReadstarSkyRenderer(minecraft.getTextureManager(),
            modelManager.getAtlas(ReadStarClient.CELESTIAL_ATLAS_TEXTURE),
            modelManager.getAtlas(ReadStarClient.STAR_ATLAS_TEXTURE));
        this.skyRenderer.buildStarsBuffer(this.stars);
    }

    /**
     * 扫描 stars/ 目录下所有 .csv 文件，合并解析星表数据，返回不可变列表。
     */
    private static List<ReadstarSkyRenderer.Star> parseStars(ResourceManager resourceManager, float maxmag) {
        List<ReadstarSkyRenderer.Star> result = new ArrayList<>();

        Map<ResourceLocation, Resource> starResources = resourceManager.listResources(
                "stars", id -> id.getPath().endsWith(".csv"));

        if (starResources.isEmpty()) {
            ReadStar.LOGGER.warn("No star data files found in stars/");
            return List.of();
        }

        for (Map.Entry<ResourceLocation, Resource> entry : starResources.entrySet()) {
            ResourceLocation resPath = entry.getKey();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open(),
                    StandardCharsets.UTF_8))) {
                String header = reader.readLine();
                if (header == null || !header.startsWith("name,")) {
                    ReadStar.LOGGER.warn("Invalid CSV header in: {}", resPath);
                    continue;
                }
                int before = result.size();
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split(",");
                    if (parts.length < 6) {
                        ReadStar.LOGGER.warn("Skipping malformed line in {}: {}", resPath, line);
                        continue;
                    }
                    String name = parts[0];
                    float px = Float.parseFloat(parts[1]);
                    float py = Float.parseFloat(parts[2]);
                    float pz = Float.parseFloat(parts[3]);
                    float mag = Float.parseFloat(parts[4]);
                    int color = Integer.parseUnsignedInt(parts[5]);
                    if (mag > maxmag)
                        continue;
                    result.add(new ReadstarSkyRenderer.Star(name, new Vector3f(px, py, pz).normalize(), mag, color));
                }
                ReadStar.LOGGER.info("Parsed {} stars from {}", result.size() - before, resPath);
            } catch (Exception e) {
                ReadStar.LOGGER.error("Failed to load star data from {}: {}", resPath, e.getMessage());
            }
        }

        ReadStar.LOGGER.info("Total parsed {} stars from {} file(s)", result.size(), starResources.size());
        return List.copyOf(result);
    }

    public void rebuildStarsWithMag(float mag) {
        this.stars = parseStars(Minecraft.getInstance().getResourceManager(), mag);
        this.brightstars = filterStars(6);
        this.skyRenderer.buildStarsBuffer(this.stars);
    }

    private List<ReadstarSkyRenderer.Star> filterStars(float mag) {
        return this.stars.stream().filter(star -> star.mag() <= mag).toList();
    }

    public boolean renderSky(long gameTime, SkyRenderState skyRenderState, Matrix4fc modelViewMatrix, Matrix4f projectionMatrix, Runnable setupFog) {
        setupFog.run();

        SkyRenderState state = skyRenderState;

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        PoseStack poseStack = new PoseStack();
        // 1. 天空底色 — modelViewMatrix 来自 skybox 参数，非 RenderSystem
        skyRenderer.renderSkyDisc(skyRenderState.skyColor, modelViewMatrix, projectionMatrix);
        skyRenderer.renderCosmicBackground(state.skyColor, this.observer, state.starBrightness, modelViewMatrix, projectionMatrix);
        skyRenderer.renderSunriseAndSunset(poseStack, state.sunAngle, state.sunriseAndSunsetColor, modelViewMatrix, projectionMatrix);
        // 2. 天体 + 星星
        skyRenderer.renderCelestialAndStars(poseStack, state.rainBrightness, state.starBrightness, this.observer, gameTime, modelViewMatrix, projectionMatrix);
        // ===== METEORS (在 frameQuat 框架内渲染) =====
        skyRenderer.buildAndRenderMeteors(poseStack, state.starBrightness, gameTime, modelViewMatrix, projectionMatrix);
        if (state.shouldRenderDarkDisc) {
            skyRenderer.renderDarkDisc(modelViewMatrix, projectionMatrix);
        }

        // 1.21.1的渲染确实就是一坨狗屎
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);

        return true;
    }
}
