package git.frozenstream.readstar.elements;

import org.joml.Vector3f;

/**
 * 天球星表数据记录，存储从 stars.json 解析的原始星数据，可复用。
 * 
 * @param name      恒星名称（如 "Sirius", "Canopus"）
 * @param direction 天球上的单位方向向量（归一化）
 * @param mag       视星等（数值越小越亮）
 * @param color     颜色索引（0-6，映射到 environment/stars/color_* 纹理）
 */
public record Star(String name, Vector3f direction, float mag, int color) {
}
