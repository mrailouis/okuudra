#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

out vec4 vColor;
out vec2 vLocal;
flat out vec2 vHalf;
flat out float vRadius;
flat out float vStroke;
flat out float vFeather;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    vColor = Color;
    vLocal = UV0;
    vRadius = Position.z;
    vFeather = float(UV1.y) / 16.0;
    if (Position.z < 0.0) {
        vStroke = float(UV1.x) / 4000.0;
        vHalf = vec2(UV2) / 8.0;
    } else {
        vStroke = float(UV1.x) / 16.0;
        vHalf = abs(UV0) - vec2(1.0 + vFeather * 0.5);
    }
}
