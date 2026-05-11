package git.frozenstream.readstar.elements;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;

public class StarManager {
    public static Star[] stars = new Star[32768];
    public static int starCount = 0;
    public static void init() {

    }


    public static Star lookingAt(Vector3f eye, float nearDistance){
        float minDistance = nearDistance;
        Star closestStar = null;
        for (int i = 0; i < starCount; i++) {
            Vector3f starPos = stars[i].position();
            float distance = eye.distance(starPos);
            if (distance < minDistance) {
                minDistance = distance;
                closestStar = stars[i];
            }
        }
        return closestStar;
    }


    public static ArrayList<Star> lookingNear(Vector3f eye, float nearDistance){
        ArrayList<Star> closestStars = new ArrayList<>();
        for (int i = 0; i < starCount; i++) {
            Vector3f starPos = stars[i].position();
            float distance = eye.distance(starPos);
            if (distance < nearDistance) {
                closestStars.add(stars[i]);
            }
        }
        return closestStars;
    }


    /**
     * 将星等(Vmag)映射到0-1的透明度值
     * 使用天文星等公式: m1 - m2 = -2.5 * log10(I1/I2)
     * 其中较亮的星星星等数值更小
     *
     * @param Vmag 视星等
     * @param maxVisibleMagnitude 最大可见星等（默认为6等星，人眼极限）
     * @return 透明度值，范围0-1
     */
    public static float getAlphaFromVmag(float Vmag, float maxVisibleMagnitude) {
        // 对于超过最大可见星等的星体，透明度为0
        if (Vmag > maxVisibleMagnitude) return 0.05f;
        else if(Vmag < 0) return 1f;

        // 计算相对亮度比例
        // 公式: I1/I2 = 10^((m2-m1)/2.5)
        final float benchmarkVmag = 0f;
        float brightnessRatio = (float) Math.pow(2, (benchmarkVmag - Vmag) / 2.5);
        float alpha = Math.min(1.0f, brightnessRatio);
        return Math.max(0.1f, alpha);
    }

    /**
     * 将星等(Vmag)映射到0-1的透明度值（使用默认最大可见星等6）
     *
     * @param Vmag 视星等
     * @return 透明度值，范围0-1
     */
    public static float getAlphaFromVmag(float Vmag) {
        return getAlphaFromVmag(Vmag, 7.0f) * 0.8f;
    }





}
