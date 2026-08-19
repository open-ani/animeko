#version 100
precision highp float;
varying vec2 vTexSamplingCoord;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;

#define {{INPUT_NAME}}_texOff(offset) texture2D(uTexSampler, vTexSamplingCoord + (offset) * uTexelSize)

{{PASS_BODY}}

void main() {
    gl_FragColor = hook();
}
