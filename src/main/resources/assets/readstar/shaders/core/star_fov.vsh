#version 330

// Position = 球面中心，Offset = Java 侧预计算的 3D billboard 偏移量。
// FOV 通过 ProjMat 自然影响 Position 的屏幕位置，
// Offset 乘 FovCompensation 反补以保持屏幕大小不变。

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

#define FovCompensation TextureMat[0][0]

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
    vertexColor = Color;
}
