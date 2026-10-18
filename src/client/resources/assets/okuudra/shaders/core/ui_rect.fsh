#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vColor;
in vec2 vLocal;
flat in vec2 vHalf;
flat in float vRadius;
flat in float vStroke;
flat in float vFeather;

out vec4 fragColor;

const float PI = 3.14159265;

float box(vec2 p, vec2 ext, float r) {
    vec2 q = abs(p) - ext + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float arc(vec2 p, float inner, float outer, float start, float sweep) {
    float len = length(p);
    float ring = max(inner - len, len - outer);
    float a = atan(p.y, p.x);
    float mid = start + sweep * 0.5;
    float da = mod(a - mid + PI, 2.0 * PI) - PI;
    float ang = (abs(da) - sweep * 0.5) * max(len, 1e-3);
    return max(ring, ang);
}

void main() {
    float d;
    float stroke = vStroke;
    if (vRadius < 0.0) {
        d = arc(vLocal, vHalf.x, vHalf.y, vStroke, -vRadius - 1.0);
        stroke = 0.0;
    } else {
        float r = min(vRadius, min(vHalf.x, vHalf.y));
        d = box(vLocal, vHalf, r);
    }
    float aa = max(fwidth(d), 1e-4);
    float outer = clamp(0.5 - d / aa, 0.0, 1.0);
    float coverage = outer;
    if (vRadius >= 0.0 && vFeather > 0.0) {
        coverage = 1.0 - clamp((d + vFeather * 0.5) / vFeather, 0.0, 1.0);
        stroke = 0.0;
    }
    if (stroke > 0.0) {
        float inner = clamp(0.5 - (d + stroke) / aa, 0.0, 1.0);
        coverage = outer - inner;
    }
    if (coverage <= 0.0) {
        discard;
    }
    vec4 c = vColor * ColorModulator;
    fragColor = vec4(c.rgb, c.a * coverage);
}
