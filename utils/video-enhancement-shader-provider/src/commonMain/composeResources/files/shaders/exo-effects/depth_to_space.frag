#version 100
precision highp float;
varying vec2 vTexSamplingCoord;
uniform vec2 uInputSize;
uniform sampler2D uFeatureSampler;
uniform sampler2D uOriginalSampler;

vec4 hook() {
    vec2 sourcePosition = vTexSamplingCoord * uInputSize;
    vec2 fraction = fract(sourcePosition);
    vec2 featureUv = (floor(sourcePosition) + 0.5) / uInputSize;
    vec4 features = texture2D(uFeatureSampler, featureUv);
    int component = int(fraction.y * 2.0) * 2 + int(fraction.x * 2.0);
    float residual = features.r;
    if (component == 1) residual = features.g;
    if (component == 2) residual = features.b;
    if (component == 3) residual = features.a;
    vec4 original = texture2D(uOriginalSampler, vTexSamplingCoord);
    return vec4(original.rgb + vec3(residual), original.a);
}

void main() {
    gl_FragColor = hook();
}
