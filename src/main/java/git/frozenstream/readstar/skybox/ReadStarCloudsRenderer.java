package git.frozenstream.readstar.skybox;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.CustomCloudsRenderer;
import org.joml.Matrix4fc;

public class ReadStarCloudsRenderer implements CustomCloudsRenderer {
    @Override
    public boolean renderClouds(LevelRenderState levelRenderState, Vec3 camPos, CloudStatus cloudStatus, int cloudColor, float cloudHeight, int cloudRange, Matrix4fc modelViewMatrix) {
        return true;
    }
}
