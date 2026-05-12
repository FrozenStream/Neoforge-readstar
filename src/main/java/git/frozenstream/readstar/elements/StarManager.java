package git.frozenstream.readstar.elements;

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

}
