package git.frozenstream.readstar.skybox;

import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.elements.CelestialBody;
import git.frozenstream.readstar.elements.CelestialBodyManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 自定义 DimensionSpecialEffects — 接管天空渲染。
 * 通过 RegisterDimensionSpecialEffectsEvent 注册到主世界。
 */
public class ReadstarDimensionEffects extends DimensionSpecialEffects {
    private final ReadstarSkyboxRenderer skyboxRenderer = ReadstarSkyboxRenderer.getInstance();

    public ReadstarDimensionEffects() {
        super(192.0F, true, SkyType.NORMAL, false, false);
    }

    public Vec3 getBrightnessDependentFogColor(Vec3 p_108908_, float p_108909_) {
        return p_108908_.multiply((double)(p_108909_ * 0.94F + 0.06F), (double)(p_108909_ * 0.94F + 0.06F), (double)(p_108909_ * 0.91F + 0.09F));
    }

    public boolean isFoggyAt(int p_108905_, int p_108906_) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        SkyRenderState state = new SkyRenderState();
        
        long gameTime = level.getGameTime();
        long daylightTime = level.getDayTime();
        CelestialBodyManager.getInstance().updatePositions(20 * gameTime);

        CelestialBody observer = null;
        if (level.dimension() == Level.OVERWORLD) {
            observer = CelestialBodyManager.getInstance().getCelestialBody("earth");
            if (observer != null) {
                skyboxRenderer.setObserver(observer);
                observer.updateCurrentVec(daylightTime);
            }
        }

        // ==== 提取天空渲染数据（替代 1.21.4+ 的 extractRenderState + EnvironmentAttributeProbe）====
        state.rainBrightness = 1.0F - level.getRainLevel(partialTick);
        state.starBrightness = level.getStarBrightness(partialTick);
        state.sunAngle = level.getSunAngle(partialTick);
        var skyVec = level.getSkyColor(camera.getPosition(), partialTick);
        state.skyColor = FastColor.ARGB32.color(255, (int)(skyVec.x * 255), (int)(skyVec.y * 255), (int)(skyVec.z * 255));

        // 日出日落颜色（1.21.1 无 EnvironmentAttributes，从 DimensionSpecialEffects 获取）
        state.sunriseAndSunsetColor = 0;
        var effects = level.effects();
        if (effects != null) {
            float[] sunrise = effects.getSunriseColor(level.getTimeOfDay(partialTick), partialTick);
            if (sunrise != null)
                state.sunriseAndSunsetColor = FastColor.ARGB32.color((int)(sunrise[3] * 255), (int)(sunrise[0] * 255), (int)(sunrise[1] * 255), (int)(sunrise[2] * 255));
            else
                state.sunriseAndSunsetColor = FastColor.ARGB32.color(0, 0, 0, 0);
        }

        // 地下暗色圆盘
        state.shouldRenderDarkDisc = false;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            state.shouldRenderDarkDisc = player.getEyePosition(partialTick).y- level.getLevelData().getHorizonHeight(level) < 0.0;
        }


        return skyboxRenderer.renderSky(level.getGameTime(), state, modelViewMatrix, projectionMatrix, setupFog);
    }
}
