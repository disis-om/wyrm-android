import modules.common;

/* Snake shadow band (OM, 2026-11-06; game/redraw.c air_band_*): one mesh
   round each snake in place of a stamp per body point. Each vertex carries
   its screen position and the shadow's alpha there; the colour is black. */

struct vs_input {
  [[vk::location(0)]] float3 v;
};

struct vs_output {
  float4 clipspace : SV_Position;
  [[vk::location(0)]] float alpha;
};

[[vk::binding(0, 0)]] ConstantBuffer<Global> global;

vs_output vs_main(vs_input input) {
  vs_output output;
  float2 hv = global.viewport * 0.5;
  float2 o = (input.v.xy - hv) / hv;
  output.clipspace = float4(o, 0, 1);
  output.alpha = input.v.z;
  return output;
}

float4 fs_main(vs_output input) {
  return float4(0, 0, 0, input.alpha);
}
