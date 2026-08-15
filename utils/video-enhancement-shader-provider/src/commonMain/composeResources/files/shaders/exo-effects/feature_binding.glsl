uniform sampler2D
uFeature{
{
FEATURE_INDEX}};
#define {{FEATURE_NAME}}_pos vTexSamplingCoord
#define {{FEATURE_NAME}}_tex(position) texture2D(uFeature{{FEATURE_INDEX}}, position)
