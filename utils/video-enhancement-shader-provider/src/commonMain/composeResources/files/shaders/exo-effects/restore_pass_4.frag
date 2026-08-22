#version 100
precision highp float;
varying vec2 vTexSamplingCoord;
uniform vec2 uTexelSize;
uniform sampler2D uTexSampler;
uniform sampler2D uOriginalSampler;

#define MAIN_pos vTexSamplingCoord
#define MAIN_tex(position) texture2D(uOriginalSampler, position)
#define conv2d_2_tf_texOff(offset) texture2D(uTexSampler, vTexSamplingCoord + (offset) * uTexelSize)

{{PASS_BODY}}

void main() {
    gl_FragColor = hook();
}
