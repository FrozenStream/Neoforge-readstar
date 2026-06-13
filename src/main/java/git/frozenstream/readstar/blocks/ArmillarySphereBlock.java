package git.frozenstream.readstar.blocks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import git.frozenstream.readstar.blocks.entity.ArmillarySphereBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 浑天仪方块 —— 在 3×3×1 区域内绘制整个恒星系
 */
public class ArmillarySphereBlock extends BaseEntityBlock {

    public static final MapCodec<ArmillarySphereBlock> CODEC =
            RecordCodecBuilder.mapCodec(inst -> inst.group(
                    propertiesCodec()
            ).apply(inst, ArmillarySphereBlock::new));

    public ArmillarySphereBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 不使用默认模型渲染，完全由 BER 绘制
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArmillarySphereBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ArmillarySphereBlockEntity.TYPE,
                (l, p, s, be) -> ArmillarySphereBlockEntity.clientTick(be));
    }
}
