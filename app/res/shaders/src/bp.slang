import modules.common;

struct vs_input {
  [[vk::location(0)]] float2 pos;
  [[vk::location(1)]] float4 circ;
  [[vk::location(2)]] float4 uv;
  [[vk::location(3)]] float4 color;
};

struct vs_output {
  float4 clipspace : SV_Position;
  [[vk::location(0)]] float2 uv;
  [[vk::location(1)]] float2 uv2;
  [[vk::location(2)]] float4 color;
};

[[vk::binding(0, 0)]] ConstantBuffer<Global> global;
[[vk::binding(1, 0)]] Sampler2D tex_atlas;
/* Skin Studio (OM, 2026-10-12): the player's own atlas. */
[[vk::binding(4, 0)]] Sampler2D studio_tex;

vs_output vs_main(vs_input input) {
  vs_output output;

  float2 p = (input.pos - 0.5) * (input.circ.z + EDGE_SOFTNESS);

  float s = sin(input.circ.w);
  float c = cos(input.circ.w);

  float2 r = float2(p.x * c - p.y * s, p.x * s + p.y * c) + 0.5 * (input.circ.z + EDGE_SOFTNESS * 0.5);

  float2 hv = global.viewport * 0.5;
  float2 wp = r + input.circ.xy;
  float2 o = (wp - hv) / hv;

  output.clipspace = float4(o, 1);
  output.uv = input.pos;
  output.uv2 = input.uv.xy + input.pos * input.uv.zw;
  output.color = input.color;

  return output;
}

float4 fs_main(vs_output input) {
  /* A uv shifted right by 4 samples the Studio atlas (game/studio.h).
     The gradients are taken before the branch, so the mip choice stays
     defined whichever texture a sprite reads. */
  float2 uv = input.uv2;
  float2 gx = ddx(uv);
  float2 gy = ddy(uv);
  float4 c;
  if (uv.x >= 3.0)
    c = studio_tex.SampleGrad(uv - float2(4.0, 0.0), gx, gy).rgba;
  else
    c = tex_atlas.SampleGrad(uv, gx, gy).rgba;
  float d = length(2 * input.uv - 1);
  float p = length(float2(ddx(d), ddy(d)));
  float a = smoothstep(0.0, EDGE_SOFTNESS, (1 - d) / p);
  return input.color * float4(c.rgb, a * c.a);
}