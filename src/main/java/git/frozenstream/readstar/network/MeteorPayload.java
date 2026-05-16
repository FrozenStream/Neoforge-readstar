package git.frozenstream.readstar.network;

import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.elements.Meteor;
import git.frozenstream.readstar.elements.MeteorCollector;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3f;

/**
 * 服务端→客户端：流星数据包
 * 传输一颗流星的全部参数，客户端收到后加入 MeteorCollector 进行渲染
 */
public record MeteorPayload(Vector3f start, Vector3f end, float speed, long startTick) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(ReadStar.MODID, "meteor");
    public static final Type<MeteorPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, MeteorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VECTOR3F, MeteorPayload::start,
            ByteBufCodecs.VECTOR3F, MeteorPayload::end,
            ByteBufCodecs.FLOAT, MeteorPayload::speed,
            ByteBufCodecs.VAR_LONG, MeteorPayload::startTick,
            (s, e, sp, st) -> new MeteorPayload(new Vector3f(s), new Vector3f(e), sp.floatValue(), st.longValue()));

    /**
     * 客户端收到数据包后，重建 Meteor 并加入 MeteorCollector
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            Meteor meteor = new Meteor(start(), end(), speed, startTick);
            MeteorCollector.getInstance().addMeteor(meteor);
        }).exceptionally(e -> {
            ReadStar.LOGGER.error("Failed to handle meteor payload", e);
            return null;
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
