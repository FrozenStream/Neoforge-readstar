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
        float[] rgb = RenderUtils.hsvToRgb(h, s, v);

        event.setRed(rgb[0]);
        event.setGreen(rgb[1]);
        event.setBlue(rgb[2]);
    }
}
