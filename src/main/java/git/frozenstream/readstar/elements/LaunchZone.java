package git.frozenstream.readstar.elements;

import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/**
 * 流星启动区域（服务端发送给客户端的区域参数）
 * 客户端 MeteorCollector 根据此区域参数自行生成流星
 *
 * @param azimuth     方位角（度），控制流星在水平方向上的来源区域
 * @param direction   流星飞行方向（归一化向量）
 * @param density     密度（每 tick 生成的流星平均数量）
 * @param endTime     终止时间（游戏 tick），到达此时间后该区域不再生成新流星
 * @param dimensionId 维度 ID，用于区分区域所属维度
 */
public record LaunchZone(float azimuth, Vector3f direction, float density, long endTime, Identifier dimensionId) {

    public LaunchZone {
        direction = new Vector3f(direction);
    }
}
