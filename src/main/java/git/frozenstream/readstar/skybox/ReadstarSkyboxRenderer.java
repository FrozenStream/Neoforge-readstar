package git.frozenstream.readstar.skybox;

import com.mojang.blaze3d.vertex.*;
import git.frozenstream.readstar.elements.CelestialBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.joml.Matrix4fc;


public class ReadstarSkyboxRenderer implements CustomSkyboxRenderer, ResourceManagerReloadListener {
    private static final ReadstarSkyboxRenderer INSTANCE = new ReadstarSkyboxRenderer();

    public static ReadstarSkyboxRenderer getInstance() {
        return INSTANCE;
    }

    private ReadstarSkyRenderer skyRenderer = null;
    private CelestialBody observer;

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

        this.skyRenderer = new ReadstarSkyRenderer(Minecraft.getInstance().getTextureManager(), Minecraft.getInstance().getAtlasManager(), resourceManager);
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
            skyRenderer.renderSkyDisc(state.skyColor);
            skyRenderer.renderSunriseAndSunset(poseStack, state.sunAngle, state.sunriseAndSunsetColor);
            skyRenderer.renderCelestialAndStars(poseStack, state.rainBrightness, state.starBrightness, this.observer, levelRenderState.gameTime);
            // ===== METEORS (在 frameQuat 框架内渲染) =====
            skyRenderer.buildAndRenderMeteors(poseStack, state.starBrightness, levelRenderState.gameTime);
            if (state.shouldRenderDarkDisc) {
                skyRenderer.renderDarkDisc();
            }
        }
        
        return true;
    }
}
