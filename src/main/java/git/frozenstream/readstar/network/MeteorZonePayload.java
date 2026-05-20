package git.frozenstream.readstar.network;

import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.elements.LaunchZone;
import git.frozenstream.readstar.elements.MeteorCollector;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3f;

/**
 * 服务端→客户端：流星启动区域数据包
 * 传输一个启动区域的参数（方位角、方向、密度、终止时间、维度），
 * 客户端收到后加入 MeteorCollector，由 Collector 自行生成流星实例
 */
public record MeteorZonePayload(float azimuth, Vector3f direction, float density, long endTime, Identifier dimensionId)
        implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(ReadStar.MODID, "meteor_zone");
    public static final Type<MeteorZonePayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, MeteorZonePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, MeteorZonePayload::azimuth,
            ByteBufCodecs.VECTOR3F, MeteorZonePayload::direction,
            ByteBufCodecs.FLOAT, MeteorZonePayload::density,
            ByteBufCodecs.VAR_LONG, MeteorZonePayload::endTime,
            Identifier.STREAM_CODEC, MeteorZonePayload::dimensionId,
            (a, d, den, et, dim) -> new MeteorZonePayload(a, new Vector3f(d), den, et.longValue(), dim));

    /**
     * 客户端收到数据包后，将启动区域加入 MeteorCollector
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            LaunchZone zone = new LaunchZone(azimuth(), direction(), density(), endTime(), dimensionId());
            MeteorCollector.getInstance().addZone(zone);
        }).exceptionally(e -> {
            ReadStar.LOGGER.error("Failed to handle meteor zone payload", e);
            return null;
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
