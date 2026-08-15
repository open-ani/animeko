#version 100
precision highp float;
varying vec2 vTexSamplingCoord;

{{FEATURE_BINDINGS}}
{{ORIGINAL_BINDING}}
{{PASS_BODY}}

void main() {
    gl_FragColor = hook();
}
