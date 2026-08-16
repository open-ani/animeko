uniform sampler2D
uOriginalSampler;
#define MAIN_pos vTexSamplingCoord
#define MAIN_tex(position) texture2D(uOriginalSampler, position)
