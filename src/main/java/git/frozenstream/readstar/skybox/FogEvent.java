package git.frozenstream.readstar.skybox;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber
public class FogEvent {
    @SubscribeEvent
    static void onfog(ViewportEvent.ComputeFogColor event){
        
    }
}
