package git.frozenstream.readstar.skybox;

import git.frozenstream.readstar.elements.CelestialBody;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber
public class FogEvent {
    @SubscribeEvent
    static void onfog(ViewportEvent.ComputeFogColor event){
        CelestialBody observer = ReadstarSkyboxRenderer.getInstance().getObserver();
        if (observer == null || !observer.hasAtmosphere || observer.atmosphereHSV == 0)
            return;

        float h = RenderUtils.getHueFloat(observer.atmosphereHSV);
        float s = RenderUtils.getSaturationFloat(observer.atmosphereHSV);
        float v = RenderUtils.getValueFloat(observer.atmosphereHSV);
        float[] atmRGB = RenderUtils.hsvToRgb(h, s, v);

        // 与原有 fog 颜色混合，大气为主体，原有 fog 为染色
        float origR = event.getRed();
        float origG = event.getGreen();
        float origB = event.getBlue();
        float blend = v * 0.2f;
        event.setRed(atmRGB[0] * blend + origR * (1f - blend));
        event.setGreen(atmRGB[1] * blend + origG * (1f - blend));
        event.setBlue(atmRGB[2] * blend + origB * (1f - blend));
    }
}
