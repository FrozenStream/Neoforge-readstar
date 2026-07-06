package git.frozenstream.readstar.skybox;

import git.frozenstream.readstar.ReadStar;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = ReadStar.MODID)
public class FovEvent {
    public static double event_fov = 70;
    @SubscribeEvent
    public static void handeFOVModifier(ViewportEvent.ComputeFov event) {
        event_fov = event.getFOV();
    }
}