#version 100
precision highp float;
varying vec2 vTexSamplingCoord;
uniform vec2 uTexelSize;
uniform sampler2D uTexSampler;

#define conv2d_tf_texOff(offset) texture2D(uTexSampler, vTexSamplingCoord + (offset) * uTexelSize)

{{PASS_BODY}}

void main() {
    gl_FragColor = hook();
}
