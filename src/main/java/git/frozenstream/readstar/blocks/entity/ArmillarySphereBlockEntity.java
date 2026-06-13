package git.frozenstream.readstar.blocks.entity;

import git.frozenstream.readstar.blocks.ArmillarySphereBlock;
import git.frozenstream.readstar.elements.CelestialBody;
import git.frozenstream.readstar.elements.CelestialBodyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 浑天仪方块实体
 * <p>
 * 存储运行时状态（模拟时间、时间流速、缩放），驱动 ARM 天体动画。
 * 注意：MC 26.1.2 NBT API 变化，暂时不持久化（失载后状态重置）。
 */
public class ArmillarySphereBlockEntity extends BlockEntity {

    private float simulationTime = 0f;
    private float timeSpeed = 1f;
    private float zoomLevel = 1f;

    public ArmillarySphereBlockEntity(BlockPos pos, BlockState blockState) {
        super(TYPE, pos, blockState);
    }

    // ==================== 客户端 Tick ====================

    public static void clientTick(ArmillarySphereBlockEntity be) {
        be.simulationTime += be.timeSpeed * (1f / 20f);
    }

    // ==================== 访问器 ====================

    public float getSimulationTime() { return simulationTime; }
    public float getTimeSpeed() { return timeSpeed; }
    public float getZoomLevel() { return zoomLevel; }

    public void setTimeSpeed(float v) { this.timeSpeed = v; setChanged(); }
    public void setZoomLevel(float v) { this.zoomLevel = Math.clamp(v, 0.1f, 10f); setChanged(); }

    public CelestialBody getRootBody() { return CelestialBodyManager.getInstance().Root; }

    // ==================== 方块实体类型 ====================

    /** 由 ReadStar 主类中的 DeferredRegister 注册后赋值 */
    public static net.minecraft.world.level.block.entity.BlockEntityType<ArmillarySphereBlockEntity> TYPE;
}
