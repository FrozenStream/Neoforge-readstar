#version 150

// Position = 球面中心，Offset = Java 侧预计算的 3D billboard 偏移量。
// FovCompensation = 反补 FOV 造成的缩放，使星点屏幕大小不受 FOV 影响。
// 由 Java 侧通过 shader.safeGetUniform("FovCompensation").set(value) 每帧注入。

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float FovCompensation;

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Offset;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    vec3 worldPos = Position + Offset * FovCompensation;
    gl_Position = ProjMat * ModelViewMat * vec4(worldPos, 1.0);
    texCoord0 = UV0;
    vertexColor = Color * ColorModulator;
}
