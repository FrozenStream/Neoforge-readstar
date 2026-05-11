package git.frozenstream.readstar.elements;

import org.joml.Vector3f;

public record Star (
    String name,
    Vector3f position,
    int type,
    float Vmag,
    int color
){
}
