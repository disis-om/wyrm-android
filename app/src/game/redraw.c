#include "redraw.h"
#include "original_art.h"
#include "sheet_text.h"
#include "../platform/android_home.h"
#include "../platform/android_team.h"
#include "ui_overlay.h"

#include "../user.h"
#include "backgrounds.h"
#include "tags.h"
#include "../network/battledome.h"
#include "studio.h"
#include "wyrm_skins.h"
#include "wyrm_extras.h"
#include "wyrm_audio.h"
#include "wyrm_extras.h"
#include "../mobile/mobile_controls.h"
#if defined(WYRM_SDL_JNI)
#include "../platform/android_look.h"
#endif

void lerp_minimap_float(float* dst, const uint8_t* src, int mmsz, float alpha) {
  int stride = MAX_MINIMAP_SIZE;

  for (int y = 0; y < mmsz; y++) {
    for (int x = 0; x < mmsz; x++) {
      int i = y * stride + x;
      float target = (float)src[i];
      dst[i] += alpha * (target - dst[i]);
    }
  }
}

void minimap_float_to_u8(const float* src, uint8_t* dst, int mmsz) {
  int stride = MAX_MINIMAP_SIZE;

  for (int y = 0; y < mmsz; y++) {
    for (int x = 0; x < mmsz; x++) {
      int i = y * stride + x;
      float v = src[i];
      dst[i] = (uint8_t)(v + 0.5f);
    }
  }
}

/**
 * Wyrm's own colour for one point of a built skin, or 0 to render from the
 * arena's palette exactly as before.
 *
 * A built skin carries an exact colour and an alpha the wire has no field for.
 * Those live in this device's settings, not in anything the arena sent, so they
 * are applied to the local snake only — another player's body is drawn from
 * what the arena actually said about it and nothing else.
 */
/* Colour wheel and Wyrm beads on our own snake (OM, 2026-10-04; 0 for one
 * drop-test build): 1 draws the exact colours we built, on this device only.
 * Everyone else, and the arena, get the slither colours the join carried.
 * 0 draws our snake from those slither colours too. */
#define WYRM_BUILT_BEADS_IN_ARENA 1

/* Boost glow (OM, 2026-10-23): the reference size and strength (the 10-18
   half-size glow hid under the body), drawn lighter. The glow is additive, so
   every k-th sprite at k times the alpha adds up to the same light; k keeps
   sprites at most WYRM_BOOST_SPACING of a glow radius apart and one sprite's
   blended alpha under 1 (bst.slang's falloff peaks at 0.4 of the instance
   alpha, so the instance alpha may reach WYRM_BOOST_PEAK). */
#define WYRM_BOOST_SPACING 0.45f
#define WYRM_BOOST_MAX_STRIDE 4
#define WYRM_BOOST_PEAK 2.5f

static int wyrm_boost_stride(game_data* gdata, int bp, float glsz,
                             float peak) {
  if (bp < 2 || !(glsz > 0)) return 1;
  float dx = gdata->data.pbx[1] - gdata->data.pbx[0];
  float dy = gdata->data.pby[1] - gdata->data.pby[0];
  float step = sqrtf(dx * dx + dy * dy) * gdata->data.gsc;
  if (!(step > 0.01f)) return 1;
  int k = (int)(glsz * WYRM_BOOST_SPACING / step);
  int cap = (int)(WYRM_BOOST_PEAK / fmaxf(peak, 0.01f));
  if (k > cap) k = cap;
  if (k > WYRM_BOOST_MAX_STRIDE) k = WYRM_BOOST_MAX_STRIDE;
  return k < 1 ? 1 : k;
}

/* The bead a strided sprite takes its colour from: each run of k sprites
   steps through every phase, so a skin whose colours repeat every k beads
   still shows all of them. */
static int wyrm_boost_bead(int j, int k) {
  return k > 1 ? j + (j / k) % k : j;
}

static const snake* colour_cache_snake;
static remote_skin* colour_cache_skin;

static uint32_t built_skin_rgba(tenv* env, snake* o, int index) {
  tuser_data* usr = env->usr;
  if (!o->cusk || index < 0 || index >= MAX_SKIN_CODE_LEN) return 0;
  /* Skin Studio (OM, 2026-10-12): the bead's own colour, opaque. */
  if (o->id == usr->gdata.data.snake_id && studio_on())
    return 0xFF000000u | studio_rgb_at(index);
  if (o->id == usr->gdata.data.snake_id)
    return WYRM_BUILT_BEADS_IN_ARENA ? usr->usrs.skin_rgba[index] : 0;

  /* Someone else's, if Wyrm's own backend has told us about them. Snake ids are
   * recycled between lives, so the name has to still match: otherwise whoever
   * inherits an id would inherit a stranger's colours with it. */
  game_data* gdata = &usr->gdata;
  if (colour_cache_snake != o) {
    colour_cache_snake = o;
    colour_cache_skin = NULL;
    int count = gdata->remote_skins ? (int)tdarray_length(gdata->remote_skins) : 0;
    for (int i = 0; i < count; ++i) {
      remote_skin* skin = gdata->remote_skins + i;
      if (skin->snake_id != o->id) continue;
      if (strncmp(skin->nickname, o->nk, sizeof(skin->nickname) - 1) == 0) {
        colour_cache_skin = skin;
        skin->last_used_ms = SDL_GetTicks();
      }
      break;
    }
  }
  if (colour_cache_skin && index < colour_cache_skin->length)
    return colour_cache_skin->rgba[index];
  return 0;
}

/* Wyrm's own beads (OM, 2026-09-28). A built segment whose alpha byte is
 * 0xE0 + k (beads 0-23) or 0xC0 + k - 24 (beads 24-53) wears Wyrm bead k,
 * painted by Wyrm iOS Scripts/generate-wyrm-beads.py into six atlas cells
 * nothing else samples (row 7 cols 3 and 6, row 8 cols 0-3), nine to a cell
 * in a 3 x 3 grid. Every bead is drawn in its own painted colours; the RGB
 * only picks the arena's nearest colour group. Like every slither bead, the
 * motif sits on the +x side, the side a body drawn tail first leaves showing. */
#define WYRM_BEAD_TAG 0xE0u
#define WYRM_BEAD_TAG2 0xC0u
#define WYRM_BEAD_FIRST_COUNT 24
#define WYRM_BEAD_COUNT 54

static const unsigned char wyrm_bead_cells[WYRM_BEAD_COUNT / 9][2] = {
    {7, 3}, {7, 6}, {8, 0}, {8, 1}, {8, 2}, {8, 3}};

static int wyrm_bead_kind(uint32_t rgba) {
  uint32_t tag = rgba >> 24;
  if (tag >= WYRM_BEAD_TAG && tag < WYRM_BEAD_TAG + WYRM_BEAD_FIRST_COUNT)
    return (int)(tag - WYRM_BEAD_TAG);
  if (tag >= WYRM_BEAD_TAG2 &&
      tag < WYRM_BEAD_TAG2 + (WYRM_BEAD_COUNT - WYRM_BEAD_FIRST_COUNT))
    return (int)(tag - WYRM_BEAD_TAG2) + WYRM_BEAD_FIRST_COUNT;
  return -1;
}

static vec4s wyrm_bead_uv(int kind) {
  const unsigned char* cell = wyrm_bead_cells[kind / 9];
  float qx = (float)(kind % 3) / 3.0f, qy = (float)((kind % 9) / 3) / 3.0f;
  return (vec4s){{(cell[1] + qx) / 7.0f, (cell[0] + qy) / 9.0f,
                  (1.0f / 3.0f) / 7.0f, (1.0f / 3.0f) / 9.0f}};
}

static vec4s wyrm_bead_color(uint32_t rgba, int kind, float alpha) {
  (void)rgba;
  (void)kind;
  return (vec4s){{1, 1, 1, alpha}};
}

/** The packed colour above, as the renderer's own RGBA. A Wyrm bead's alpha
 * byte names its texture, so where it is drawn as a plain colour (the flat
 * render mode) it is opaque. */
static vec4s built_skin_color(uint32_t rgba, float alpha_scale) {
  float alpha = wyrm_bead_kind(rgba) >= 0 ? 1.0f : ((rgba >> 24) & 0xFF) / 255.0f;
  return (vec4s){{((rgba >> 16) & 0xFF) / 255.0f, ((rgba >> 8) & 0xFF) / 255.0f,
                  (rgba & 0xFF) / 255.0f, alpha * alpha_scale}};
}

/* Wyrm (ported from Wyrm iOS) — the slither.io Android client's Build-a-Slither beads.
 *
 * A bead built with the colour wheel keeps its exact picked RGB in the low
 * 24 bits and names its Android texture in the alpha byte, so it travels
 * unchanged through settings and Wyrm's arena skin sync:
 *   0xFE  nsk 0, AIR `kmc_ts[9][0]`  (plain bead)
 *   0xFD  nsk 1, AIR `kmc_ts[29][0]` (dark core, light rim)
 * Their atlas cells hold exact ports of those AIR bitmaps and of `ksmc_t`,
 * the outline and drop shadow AIR draws beneath each such bead. */
#define APPLE_AIR_SHADOW_SCALE (102.0f / 64.0f)
/* Off (OM, 2026-09-28): the `ksmc_t` stamps read as a black shadow wrapped
 * round the whole snake, which beads picked from the grid never had. With it
 * off, wheel beads get the same shadows as every other bead. 1 restores AIR. */
#define WYRM_AIR_BEAD_SHADOW 0

static int apple_air_kind(uint32_t rgba) {
  uint32_t tag = rgba >> 24;
  /* AIR bead styles 2..5 (OM, 2026-10-26): 0xFC..0xF9 name nsk 2..5. */
  if (tag >= 0xF9u && tag <= 0xFEu) return (int)(0xFEu - tag);
  return -1;
}

/* The styles' 224 px half-cells (generate-air-styles.py), as (row, column)
   in 224 px units of the 3136 x 4032 atlas: base, then shine. */
static const unsigned char apple_air_style_cells[4][2][2] = {
    {{12, 3}, {15, 0}}, {{13, 3}, {15, 1}}, {{14, 3}, {15, 2}}, {{15, 3}, {16, 8}}};
static const float apple_air_style_shine_a[4] = {0.8f, 0.8f, 0.7f, 0.55f};
#define APPLE_AIR_STYLE_SPAN 2.5f

static vec4s apple_air_style_uv(int kind, int shine) {
  const unsigned char* cell = apple_air_style_cells[kind - 2][shine ? 1 : 0];
  return (vec4s){{cell[1] / 14.0f, cell[0] / 18.0f, 1 / 14.0f, 1 / 18.0f}};
}

static vec4s apple_air_bead_uv(int kind) {
  if (kind >= 2) return apple_air_style_uv(kind, 0);
  return (vec4s){{(2 + kind) / 7.0f, 6 / 9.0f, 1 / 7.0f, 1 / 9.0f}};
}

/* After a style bead was pushed (its quad one bead wide): the picture is 2.5
   beads wide round the same centre, and its shine goes over it. */
static void apple_air_style_finish(bp_renderer* r, int kind, float alpha) {
  if (kind < 2 || kind > 5 || r->num_instances <= 0) return;
  bp_instance* last = r->instances + r->num_instances - 1;
  float grow = last->circle.z * (APPLE_AIR_STYLE_SPAN - 1) * 0.5f;
  last->circle.x -= grow;
  last->circle.y -= grow;
  last->circle.z *= APPLE_AIR_STYLE_SPAN;
  /* Light at a distance (OM, 2026-10-26): under 8 px the shine is not seen,
     so it is not drawn; nothing else changes, so nothing flickers. */
  if (last->circle.z < 8.0f) return;
  bp_instance shine = *last;
  shine.uv_rect = apple_air_style_uv(kind, 1);
  shine.color = (vec4s){{1, 1, 1, alpha * apple_air_style_shine_a[kind - 2]}};
  bp_renderer_push(r, &shine);
}

static vec4s apple_air_shadow_uv(void) {
  return (vec4s){{4 / 7.0f, 6 / 9.0f, APPLE_AIR_SHADOW_SCALE / 7.0f,
                  APPLE_AIR_SHADOW_SCALE / 9.0f}};
}

/* AIR setSkin: when mid + max channel is below nsk_min2c (255 for nsk 0 and
 * 1) every channel is lifted by 1 + (255 - (mid + max)) / 2, capped at 255,
 * then truncated by the `<< 16 | << 8 |` pack. */
static vec4s apple_air_tint(uint32_t rgba, float alpha) {
  float c[3] = {(float)((rgba >> 16) & 0xFF), (float)((rgba >> 8) & 0xFF),
                (float)(rgba & 0xFF)};
  float lo = fminf(c[0], fminf(c[1], c[2]));
  float hi = fmaxf(c[0], fmaxf(c[1], c[2]));
  float mid = c[0] + c[1] + c[2] - lo - hi;
  /* nsk_min2c: 255 for nsk 0 and 1, 128 for the drawn styles. */
  float min2c = apple_air_kind(rgba) >= 2 ? 128.0f : 255.0f;
  if (mid + hi < min2c) {
    float lift = 1 + (min2c - (mid + hi)) / 2;
    for (int i = 0; i < 3; ++i) c[i] = fminf(255, c[i] + lift);
  }
  for (int i = 0; i < 3; ++i) c[i] = floorf(c[i]);
  return (vec4s){{c[0] / 255.0f, c[1] / 255.0f, c[2] / 255.0f, alpha}};
}

static int apple_air_kind_at(tenv* env, snake* o, int point) {
  if (!o->cusk || o->cusk_len <= 0 || point < 0) return -1;
  uint32_t built = built_skin_rgba(env, o, point % o->cusk_len);
  return built ? apple_air_kind(built) : -1;
}

/* One `ksmc_t` stamp: unrotated, centred on the point, AIR's size. */
static void apple_air_shadow(tenv* env, int point, float half, float alpha,
                             float mww2, float mhh2) {
  game_data* gdata = &env->usr->gdata;
  float fix = (gdata->data.pbx[point] - gdata->data.view_xx) * gdata->data.gsc + mww2;
  float fiy = (gdata->data.pby[point] - gdata->data.view_yy) * gdata->data.gsc + mhh2;
  /* Only a stamp that shows (OM, 2026-10-12): on screen, and not faded to
     nothing where the points bunch up. */
  if (alpha < 1.0f / 255.0f) return;
  if (fix + half < 0 || fiy + half < 0 || fix - half > 2 * mww2 ||
      fiy - half > 2 * mhh2)
    return;
  bp_renderer_push(env->usr->r->bpr,
                   &(bp_instance){{fix - half, fiy - half, 2 * half, 0},
                                  apple_air_shadow_uv(),
                                  {0, 0, 0, alpha}});
}

/* AIR's `_loc18_`: a shadow fades where consecutive stamps bunch up. */
static float apple_air_spacing(tenv* env, int point, float* sx, float* sy) {
  game_data* gdata = &env->usr->gdata;
  float ox = *sx, oy = *sy;
  *sx = gdata->data.pbx[point];
  *sy = gdata->data.pby[point];
  float d = fabsf(*sx - ox) + fabsf(*sy - oy);
  return fminf(1, d / 6);
}

/* Modes › Snake shadow (OM, 2026-10-09). The AIR client gives every snake
 * `has_shadow` (setSkin) and draws `ksmc_t` beneath it (Main.as redraw): the
 * head's first nine points fading out, then the tail's last four, then, as
 * the body is drawn from the tail, one four points behind each body point,
 * faded where the points bunch up. Same stamp, size and order as the wheel
 * beads' shadow above, for every point of every snake. */
static void air_snake_shadow_ends(tenv* env, int bp, float half, float a,
                                  float mww2, float mhh2, float* sx,
                                  float* sy) {
  game_data* gdata = &env->usr->gdata;
  for (int p = bp - 1 < 8 ? bp - 1 : 8; p >= 0; p--)
    if (gdata->data.pbu[p] == 2)
      apple_air_shadow(env, p, half, a * (1 - p / 9.0f), mww2, mhh2);
  for (int n = 1; n <= 4; ++n) {
    int p = bp - n;
    if (p < 0 || gdata->data.pbu[p] != 2) continue;
    float spacing = apple_air_spacing(env, p, sx, sy);
    if (n == 1) spacing = 1;
    apple_air_shadow(env, p, half, spacing * a * (p < 9 ? p / 9.0f : 1), mww2,
                     mhh2);
  }
}

static void air_snake_shadow_behind(tenv* env, int j, float half, float a,
                                    float mww2, float mhh2, float* sx,
                                    float* sy) {
  game_data* gdata = &env->usr->gdata;
  int p = j - 4;
  if (p < 0 || gdata->data.pbu[p] != 2) return;
  float spacing = apple_air_spacing(env, p, sx, sy);
  apple_air_shadow(env, p, half, spacing * a * (p < 9 ? p / 9.0f : 1), mww2,
                   mhh2);
}

/* Food style is presentation only. Mixed hashes the arena's existing id so a
 * morsel keeps its shape for its whole life without adding state or wire data. */
static float food_shape(const food* fo, int style) {
  if (style == 0) return 0.0f;
  if (style == 1) return 1.0f;
  if (style >= 3) return (float)(style - 1);

  uint64_t hash = (uint64_t)fo->id;
  hash ^= hash >> 33;
  hash *= UINT64_C(0xff51afd7ed558ccd);
  hash ^= hash >> 33;
  return (float)(hash % UINT64_C(8));
}

/* Skin Studio (OM, 2026-10-12): the player's own snake, on this phone. */
static bool studio_own(tenv* env, snake* o) {
  return studio_on() && o->id == env->usr->gdata.data.snake_id;
}

/* Which bead a body point wears: the Studio's pattern for the player's own
   snake, else the arena's skin. */
static int studio_slot(tenv* env, snake* o, int point, int cusk_len) {
  if (studio_own(env, o)) {
    int len = studio_current()->pattern_len;
    return len > 0 ? point % len : 0;
  }
  return cusk_len > 0 ? point % cusk_len : 0;
}

/* A Studio cell as a sprite uv: shifted right, so bp.slang samples the
   Studio atlas. */
static vec4s studio_cell_uv(int cell) {
  float g = 1.0f / STUDIO_GRID;
  return (vec4s){{STUDIO_UV_SHIFT + (cell % STUDIO_GRID) * g,
                  (cell / STUDIO_GRID) * g, g, g}};
}

/* The boost glow of a Studio bead: its own colour. */
static vec3s* studio_glow_for(tenv* env, snake* o, int point, vec3s* scratch,
                              vec3s* fallback) {
  if (!studio_own(env, o)) return fallback;
  uint32_t rgb = studio_rgb_at(studio_slot(env, o, point, 1));
  scratch->r = ((rgb >> 16) & 0xFF) / 255.0f;
  scratch->g = ((rgb >> 8) & 0xFF) / 255.0f;
  scratch->b = (rgb & 0xFF) / 255.0f;
  return scratch;
}

/* The Studio's eyes: any number, anywhere on the head, each a white and a
   pupil from the atlas, turned with the head. The pupil sits half a unit
   forward and follows where the snake looks (rex/rey) as slither's does. */
static void studio_draw_eyes(tenv* env, snake* o, float fang, float hx,
                             float hy, float ssc, float ea, float mww2,
                             float mhh2) {
  game_data* gdata = &env->usr->gdata;
  const studio_skin* s = studio_current();
  float c = cosf(fang), sn = sinf(fang);
  for (int i = 0; i < s->eye_count; ++i) {
    const studio_eye* e = s->eyes + i;
    float ex = (c * e->fwd - sn * e->side) * ssc;
    float ey = (sn * e->fwd + c * e->side) * ssc;
    float wr = e->white_r * ssc * gdata->data.gsc;
    if (wr > 0.0f) {
      float cx = mww2 + (ex + hx - gdata->data.view_xx) * gdata->data.gsc;
      float cy = mhh2 + (ey + hy - gdata->data.view_yy) * gdata->data.gsc;
      bp_renderer_push(env->usr->r->bpr,
                       &(bp_instance){{cx - wr, cy - wr, wr * 2, fang},
                                      studio_cell_uv(e->white_cell),
                                      {1, 1, 1, ea}});
    }
    float pr = e->pupil_r * ssc * gdata->data.gsc;
    if (pr > 0.0f) {
      float px = ex + c * 0.5f + o->rex * ssc * e->look;
      float py = ey + sn * 0.5f + o->rey * ssc * e->look;
      float cx = mww2 + (px + hx - gdata->data.view_xx) * gdata->data.gsc;
      float cy = mhh2 + (py + hy - gdata->data.view_yy) * gdata->data.gsc;
      bp_renderer_push(env->usr->r->bpr,
                       &(bp_instance){{cx - pr, cy - pr, pr * 2, fang},
                                      studio_cell_uv(e->pupil_cell),
                                      {1, 1, 1, ea}});
    }
  }
}

int arp(snake* o, int q, float xx, float yy) {
  if (q < tdarray_length(o->gptz)) {
    int gpo_i = q;
    o->gptz[gpo_i].xx = xx;
    o->gptz[gpo_i].yy = yy;
    return gpo_i;
  } else {
    gpt gpo = {.xx = xx, .yy = yy};
    tdarray_push(&o->gptz, &gpo);
    int gptz_len = tdarray_length(o->gptz);
    return gptz_len - 1;
  }
}

static inline bool point_in_snake_view(const game_data* gdata, float x,
                                       float y) {
  return x >= gdata->data.bpx1 && y >= gdata->data.bpy1 &&
         x <= gdata->data.bpx2 && y <= gdata->data.bpy2;
}

static void draw_rope_backbone(tenv* env, snake* o, int point_count,
                               float center_x, float center_y, float alpha) {
  game_data* gdata = &env->usr->gdata;
  int color_id = o->cusk && o->cusk_len > 0 ? o->cusk_data[0]
                                            : gdata->default_skins[o->cv][1];
  vec3s* rope_color = gdata->cg_colors + color_id;
  ImDrawList* draw = igGetWindowDrawList();
  const ImU32 outline =
      igColorConvertFloat4ToU32((ImVec4){0.01f, 0.012f, 0.012f, alpha * 0.78f});
  const ImU32 core = igColorConvertFloat4ToU32(
      (ImVec4){rope_color->r, rope_color->g, rope_color->b, alpha});

  /* The sampled points are the same centreline used by the normal body. Two

   * passes keep that line readable over food without making its visual width

   * resemble a collision-safe snake. */
  for (int pass = 0; pass < 2; ++pass) {
    bool have_previous = false;
    ImVec2 previous = {0};
    for (int point = 0; point < point_count; ++point) {
      if (gdata->data.pbu[point] < 1) {
        have_previous = false;
        continue;
      }
      ImVec2 current = {
          center_x +
              (gdata->data.pbx[point] - gdata->data.view_xx) * gdata->data.gsc,
          center_y +
              (gdata->data.pby[point] - gdata->data.view_yy) * gdata->data.gsc};
      if (have_previous)
        ImDrawList_AddLine(draw, previous, current, pass == 0 ? outline : core,
                           pass == 0 ? 4.2f : 2.0f);
      previous = current;
      have_previous = true;
    }
  }

  if (point_count > 0 && gdata->data.pbu[0] >= 1) {
    ImVec2 head = {
        center_x + (gdata->data.pbx[0] - gdata->data.view_xx) * gdata->data.gsc,
        center_y +
            (gdata->data.pby[0] - gdata->data.view_yy) * gdata->data.gsc};
    ImDrawList_AddCircleFilled(draw, head, 2.1f, outline, 12);
    ImDrawList_AddCircleFilled(draw, head, 1.0f, core, 12);
  }
}

/* mobile/mobile_controls.c: the arrow upright, the chosen steering sideways. */
int mobile_controls_steering_mode(tenv* env);


/* Skinless strip and the spine (OM, 2026-10-05). ImGui is drawn after the
   sprite batches, so a line here covers the body. Eyes for these modes are
   circles on the same list and therefore sit on top. */
static void snake_screen_point(const game_data* gdata, int point, float cx,
                               float cy, ImVec2* out) {
  out->x = cx + (gdata->data.pbx[point] - gdata->data.view_xx) * gdata->data.gsc;
  out->y = cy + (gdata->data.pby[point] - gdata->data.view_yy) * gdata->data.gsc;
}

static void stroke_smooth(ImDrawList* draw, ImVec2* pts, int count, ImU32 col,
                          float width) {
  int i;
  if (count < 2 || width <= 0.0f) return;
  ImDrawList_PathClear(draw);
  ImDrawList_PathLineTo(draw, pts[0]);
  for (i = 1; i < count - 1; ++i) {
    ImVec2 mid;
    mid.x = (pts[i].x + pts[i + 1].x) * 0.5f;
    mid.y = (pts[i].y + pts[i + 1].y) * 0.5f;
    ImDrawList_PathBezierQuadraticCurveTo(draw, pts[i], mid, 0);
  }
  ImDrawList_PathLineTo(draw, pts[count - 1]);
  ImDrawList_PathStroke(draw, col, 0, width);
}

/* On-screen runs only. A full buffer is stroked and continued from its last
   point so a long snake does not need a 32k stack. */
static void draw_body_line(tenv* env, int bp, int start, float cx, float cy,
                           ImU32 under, float under_w, ImU32 over, float over_w) {
  game_data* gdata = &env->usr->gdata;
  ImDrawList* draw = igGetWindowDrawList();
  ImVec2 buf[160];
  int n = 0;
  int i;
  for (i = start; i <= bp; ++i) {
    int live = i < bp && gdata->data.pbu[i] >= 1;
    if (live && n < 160) {
      snake_screen_point(gdata, i, cx, cy, &buf[n]);
      n += 1;
      if (n < 160) continue;
    }
    if (n >= 2) {
      if (under_w > 0.0f) stroke_smooth(draw, buf, n, under, under_w);
      stroke_smooth(draw, buf, n, over, over_w);
    }
    if (live) {
      buf[0] = buf[n > 0 ? n - 1 : 0];
      n = 1;
    } else {
      n = 0;
    }
  }
}

static void snake_strip_colour(tenv* env, snake* o, float* red, float* green,
                               float* blue) {
  game_data* gdata = &env->usr->gdata;
  uint32_t built = built_skin_rgba(env, o, 0);
  int cg_id;
  vec3s* col;
  if (built) {
    *red = (float)((built >> 16) & 255) / 255.0f;
    *green = (float)((built >> 8) & 255) / 255.0f;
    *blue = (float)(built & 255) / 255.0f;
    return;
  }
  cg_id = o->cusk && o->cusk_len > 0 ? o->cusk_data[0]
                                    : gdata->default_skins[o->cv][1];
  col = gdata->cg_colors + cg_id;
  *red = col->r;
  *green = col->g;
  *blue = col->b;
}

static float snake_line_dpi(tenv* env) {
  float dpi = 1.0f;
  int w;
  int h;
  float short_side;
  if (!env->wnd) return dpi;
  w = env->wnd->size[0];
  h = env->wnd->size[1];
  short_side = (float)(w < h ? w : h);
  dpi = short_side / 480.0f;
  if (dpi < 1.0f) dpi = 1.0f;
  return dpi;
}

/*
 * Skinless is drawn as its own mesh (OM, 2026-10-05: the strip broke up and
 * squished as a loop tightened). ImGui's thick stroke joins every bend with a
 * mitre; once a bend is tighter than the strip is wide, the mitre flips and
 * the strip folds over itself. Here the centre line is the same midpoint curve
 * (NTL's quadraticCurveTo path), sampled densely; each sample gets a plain
 * perpendicular (no mitre); the inside of a bend never reaches past that
 * bend's own centre, so nothing folds; the ends are round (NTL's lineCap);
 * and a 1 px fade on the outside edge stands in for ImGui's anti-aliasing.
 */
#define SKINLESS_RUN 1024
#define SKINLESS_DENSE 8192
#define SKINLESS_CAP_SEGMENTS 12
static ImVec2 skinless_pts[SKINLESS_DENSE];

static void skinless_push(ImVec2 p, int* m) {
  if (*m > 0) {
    float dx = p.x - skinless_pts[*m - 1].x;
    float dy = p.y - skinless_pts[*m - 1].y;
    if (dx * dx + dy * dy < 0.0001f) return;
  }
  if (*m < SKINLESS_DENSE) skinless_pts[(*m)++] = p;
}

static int skinless_pieces(float len, float step) {
  int k = (int)ceilf(len / step);
  if (k < 1) k = 1;
  if (k > 24) k = 24;
  return k;
}

/* The same curve stroke_smooth lays: start, quadratics through the midpoints,
   straight to the end. */
static int skinless_sample(const ImVec2* pts, int n, float step) {
  int m = 0;
  int i;
  int s;
  int k;
  ImVec2 from = pts[0];
  skinless_push(from, &m);
  for (i = 1; i < n - 1; ++i) {
    ImVec2 c = pts[i];
    ImVec2 to = {(pts[i].x + pts[i + 1].x) * 0.5f,
                 (pts[i].y + pts[i + 1].y) * 0.5f};
    k = skinless_pieces(hypotf(c.x - from.x, c.y - from.y) +
                            hypotf(to.x - c.x, to.y - c.y),
                        step);
    for (s = 1; s <= k; ++s) {
      float t = (float)s / (float)k;
      float u = 1.0f - t;
      ImVec2 q = {u * u * from.x + 2.0f * u * t * c.x + t * t * to.x,
                  u * u * from.y + 2.0f * u * t * c.y + t * t * to.y};
      skinless_push(q, &m);
    }
    from = to;
  }
  if (n >= 2) {
    ImVec2 to = pts[n - 1];
    k = skinless_pieces(hypotf(to.x - from.x, to.y - from.y), step);
    for (s = 1; s <= k; ++s) {
      float t = (float)s / (float)k;
      ImVec2 q = {from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t};
      skinless_push(q, &m);
    }
  }
  return m;
}

static ImVec2 skinless_unit(float x, float y) {
  float len = sqrtf(x * x + y * y);
  ImVec2 out = {0.0f, 0.0f};
  if (len > 0.000001f) {
    out.x = x / len;
    out.y = y / len;
  }
  return out;
}

/* A round end: a half disc out of `end`, from the +normal rail round to the
   -normal rail through `out`, with the same 1 px fade. */
static void skinless_cap(ImDrawList* draw, ImVec2 uv, ImVec2 end, ImVec2 nrm,
                         ImVec2 out, float r, ImU32 col, ImU32 clear) {
  const int k = SKINLESS_CAP_SEGMENTS;
  int i;
  ImDrawIdx base;
  ImDrawList_PrimReserve(draw, 9 * k, 1 + 2 * (k + 1));
  base = (ImDrawIdx)draw->_VtxCurrentIdx;
  ImDrawList_PrimWriteVtx(draw, end, uv, col);
  for (i = 0; i <= k; ++i) {
    float a = (float)PI * (float)i / (float)k;
    float dx = nrm.x * cosf(a) + out.x * sinf(a);
    float dy = nrm.y * cosf(a) + out.y * sinf(a);
    ImDrawList_PrimWriteVtx(draw, (ImVec2){end.x + dx * r, end.y + dy * r}, uv,
                            col);
    ImDrawList_PrimWriteVtx(
        draw, (ImVec2){end.x + dx * (r + 1.0f), end.y + dy * (r + 1.0f)}, uv,
        clear);
  }
  for (i = 0; i < k; ++i) {
    ImDrawIdx rim = (ImDrawIdx)(base + 1 + 2 * i);
    ImDrawIdx next = (ImDrawIdx)(rim + 2);
    ImDrawList_PrimWriteIdx(draw, base);
    ImDrawList_PrimWriteIdx(draw, rim);
    ImDrawList_PrimWriteIdx(draw, next);
    ImDrawList_PrimWriteIdx(draw, rim);
    ImDrawList_PrimWriteIdx(draw, (ImDrawIdx)(rim + 1));
    ImDrawList_PrimWriteIdx(draw, (ImDrawIdx)(next + 1));
    ImDrawList_PrimWriteIdx(draw, rim);
    ImDrawList_PrimWriteIdx(draw, (ImDrawIdx)(next + 1));
    ImDrawList_PrimWriteIdx(draw, next);
  }
}

/* One run of the strip: `buf` is the on-screen body points, head first. */
static void draw_skinless_run(ImDrawList* draw, ImVec2* buf, int n, ImU32 col,
                              float width, int cap_start, int cap_end) {
  float r = width * 0.5f;
  ImU32 clear = col & 0x00FFFFFFu; /* same colour, alpha 0 (alpha is the top byte) */
  ImVec2 uv;
  ImVec2 first_n = {0.0f, 0.0f};
  ImVec2 first_t = {0.0f, 0.0f};
  ImVec2 last_n = {0.0f, 0.0f};
  ImVec2 last_t = {0.0f, 0.0f};
  int m;
  int j;
  ImDrawIdx base;
  if (n < 1 || width <= 0.0f) return;
  if (n == 1) {
    if (cap_start || cap_end) ImDrawList_AddCircleFilled(draw, buf[0], r, col, 24);
    return;
  }
  {
    /* Samples about a third of the strip's half-width apart, spread wider on
       a very long run so the whole run always fits the buffer. */
    float length = 0.0f;
    float step;
    for (j = 1; j < n; ++j)
      length += hypotf(buf[j].x - buf[j - 1].x, buf[j].y - buf[j - 1].y);
    step = fmaxf(3.0f, r * 0.35f); /* 3 px (was 1.5): lighter (2026-10-12) */
    step = fmaxf(step, length / (float)(SKINLESS_DENSE - 2 * n - 64));
    m = skinless_sample(buf, n, step);
  }
  if (m < 2) {
    if (cap_start || cap_end) ImDrawList_AddCircleFilled(draw, buf[0], r, col, 24);
    return;
  }
  igGetFontTexUvWhitePixel(&uv);

  ImDrawList_PrimReserve(draw, 18 * (m - 1), 4 * m);
  base = (ImDrawIdx)draw->_VtxCurrentIdx;
  for (j = 0; j < m; ++j) {
    ImVec2 p = skinless_pts[j];
    ImVec2 a = j > 0 ? skinless_unit(p.x - skinless_pts[j - 1].x,
                                     p.y - skinless_pts[j - 1].y)
                     : skinless_unit(skinless_pts[1].x - p.x,
                                     skinless_pts[1].y - p.y);
    ImVec2 b = j < m - 1 ? skinless_unit(skinless_pts[j + 1].x - p.x,
                                         skinless_pts[j + 1].y - p.y)
                         : a;
    ImVec2 t = skinless_unit(a.x + b.x, a.y + b.y);
    ImVec2 nrm;
    float off_l = r;
    float off_r = r;
    float fade_l = 1.0f;
    float fade_r = 1.0f;
    if (t.x == 0.0f && t.y == 0.0f) t = b;
    nrm = (ImVec2){-t.y, t.x};
    if (j > 0 && j < m - 1) {
      float cross = a.x * b.y - a.y * b.x;
      float dot = a.x * b.x + a.y * b.y;
      float turn = fabsf(atan2f(cross, dot));
      if (turn > 0.0001f) {
        float la = hypotf(p.x - skinless_pts[j - 1].x, p.y - skinless_pts[j - 1].y);
        float lb = hypotf(skinless_pts[j + 1].x - p.x, skinless_pts[j + 1].y - p.y);
        float bend = 0.5f * (la + lb) / turn; /* this bend's radius */
        /* The inside of the bend is the side the path turns to. */
        if (bend < r) {
          if (cross > 0.0f) {
            off_l = bend;
            fade_l = 0.0f;
          } else {
            off_r = bend;
            fade_r = 0.0f;
          }
        }
      }
    }
    if (j == 0) {
      first_n = nrm;
      first_t = t;
    }
    if (j == m - 1) {
      last_n = nrm;
      last_t = t;
    }
    ImDrawList_PrimWriteVtx(draw, (ImVec2){p.x + nrm.x * (off_l + fade_l),
                                           p.y + nrm.y * (off_l + fade_l)},
                            uv, clear);
    ImDrawList_PrimWriteVtx(draw, (ImVec2){p.x + nrm.x * off_l, p.y + nrm.y * off_l},
                            uv, col);
    ImDrawList_PrimWriteVtx(draw, (ImVec2){p.x - nrm.x * off_r, p.y - nrm.y * off_r},
                            uv, col);
    ImDrawList_PrimWriteVtx(draw, (ImVec2){p.x - nrm.x * (off_r + fade_r),
                                           p.y - nrm.y * (off_r + fade_r)},
                            uv, clear);
  }
  for (j = 0; j < m - 1; ++j) {
    ImDrawIdx s = (ImDrawIdx)(base + 4 * j);
    ImDrawIdx e = (ImDrawIdx)(s + 4);
    int q;
    for (q = 0; q < 3; ++q) {
      ImDrawList_PrimWriteIdx(draw, (ImDrawIdx)(s + q));
      ImDrawList_PrimWriteIdx(draw, (ImDrawIdx)(s + q + 1));
      ImDrawList_PrimWriteIdx(draw, (ImDrawIdx)(e + q + 1));
      ImDrawList_PrimWriteIdx(draw, (ImDrawIdx)(s + q));
      ImDrawList_PrimWriteIdx(draw, (ImDrawIdx)(e + q + 1));
      ImDrawList_PrimWriteIdx(draw, (ImDrawIdx)(e + q));
    }
  }
  if (cap_start)
    skinless_cap(draw, uv, skinless_pts[0], first_n,
                 (ImVec2){-first_t.x, -first_t.y}, r, col, clear);
  if (cap_end)
    skinless_cap(draw, uv, skinless_pts[m - 1], last_n, last_t, r, col, clear);
}

/* Skinless, like NTL's of() (OM, 2026-10-05). lsz is already half of 29*sc,
   so a plain disc is 2 * lsz * gsc across and this stroke is that wide.
   Round caps add that radius at each real end, which is the disc. Alpha is
   NTL's skinless transparency: .8 times the snake's own fade. Runs continue
   past SKINLESS_RUN points so a long snake is not cut short. */
static void draw_skinless_strip(tenv* env, snake* o, int bp, float cx, float cy,
                                float lsz, float alpha) {
  game_data* gdata = &env->usr->gdata;
  ImDrawList* draw = igGetWindowDrawList();
  ImVec2 buf[SKINLESS_RUN];
  int n = 0;
  int i;
  int continued = 0;
  float red, green, blue;
  float width = 2.0f * lsz * gdata->data.gsc;
  ImU32 main_col;
  ImU32 glow_col;
  int boosting = o->tsp > o->fsp;
  snake_strip_colour(env, o, &red, &green, &blue);
  main_col = igColorConvertFloat4ToU32((ImVec4){red, green, blue, 0.8f * alpha});
  glow_col = igColorConvertFloat4ToU32((ImVec4){red, green, blue, 0.22f * alpha});
  for (i = 0; i <= bp; ++i) {
    int live = i < bp && gdata->data.pbu[i] >= 1;
    int more;
    int cap_start;
    int cap_end;
    if (live && n < SKINLESS_RUN) {
      snake_screen_point(gdata, i, cx, cy, &buf[n]);
      n += 1;
      if (n < SKINLESS_RUN) continue;
    }
    if (n >= 1) {
      more = live;
      cap_start = !continued;
      cap_end = !more;
      if (boosting)
        draw_skinless_run(draw, buf, n, glow_col, width * 1.08f, cap_start,
                          cap_end);
      draw_skinless_run(draw, buf, n, main_col, width, cap_start, cap_end);
    }
    if (live) {
      buf[0] = buf[n > 0 ? n - 1 : 0];
      n = 1;
      continued = 1;
    } else {
      n = 0;
      continued = 0;
    }
  }
}

/* A spine wider than the thread is drawn on the skinless mesh, so it does
   not fold where the body coils tight (a thick ImGui polyline mitres). One
   layer (OM, 2026-10-12). */
static void draw_spine_mesh(tenv* env, int bp, float cx, float cy, ImU32 over,
                            float over_w) {
  game_data* gdata = &env->usr->gdata;
  ImDrawList* draw = igGetWindowDrawList();
  ImVec2 buf[SKINLESS_RUN];
  int n = 0;
  int i;
  int continued = 0;
  for (i = 1; i <= bp; ++i) {
    int live = i < bp && gdata->data.pbu[i] >= 1;
    if (live && n < SKINLESS_RUN) {
      snake_screen_point(gdata, i, cx, cy, &buf[n]);
      n += 1;
      if (n < SKINLESS_RUN) continue;
    }
    if (n >= 1)
      draw_skinless_run(draw, buf, n, over, over_w, !continued, !live);
    if (live) {
      buf[0] = buf[n > 0 ? n - 1 : 0];
      n = 1;
      continued = 1;
    } else {
      n = 0;
      continued = 0;
    }
  }
}

/* The reference client's centre line (OM, 2026-10-12): one straight line per segment,
   skipped when neither end is within 8 px of the screen. */
static void draw_spine_lines(tenv* env, int bp, float cx, float cy, ImU32 col,
                             float width) {
  game_data* gdata = &env->usr->gdata;
  ImDrawList* draw = igGetWindowDrawList();
  const float margin = 8.0f;
  const float right = 2.0f * cx + margin;
  const float bottom = 2.0f * cy + margin;
  ImVec2 last = {0.0f, 0.0f};
  int have = 0;
  int i;
  for (i = 1; i < bp; ++i) {
    ImVec2 at;
    int seen;
    if (gdata->data.pbu[i] < 1) {
      have = 0;
      continue;
    }
    snake_screen_point(gdata, i, cx, cy, &at);
    seen = (at.x >= -margin && at.y >= -margin && at.x <= right && at.y <= bottom) ||
           (last.x >= -margin && last.y >= -margin && last.x <= right &&
            last.y <= bottom);
    if (have && seen) ImDrawList_AddLine(draw, last, at, col, width);
    last = at;
    have = 1;
  }
}

/* Spine width (OM, 2026-10-09): the slider is a share of the body's width
   (`body_w`, the bead's diameter on screen): 0 draws nothing, 1 is as wide as
   the snake, smooth in between. Light like the reference client's centre line (OM,
   2026-10-12): one colour, white, or black on a very light snake (the reference client's
   luminance rule); up to 2 dpi one line per segment, wider the skinless mesh
   in one layer. Returns 1 when the mesh was drawn (it can cover the head, so
   the eyes go on top). */
static int draw_snake_spine(tenv* env, snake* o, int bp, float cx, float cy,
                            float alpha, float body_w, float width) {
  float dpi = snake_line_dpi(env);
  float over_w;
  float red, green, blue;
  float ink;
  ImU32 col;
  if (!(width > 0.0f) || !(body_w > 0.0f)) return 0;
  if (width > 1.0f) width = 1.0f;
  over_w = body_w * width;
  snake_strip_colour(env, o, &red, &green, &blue);
  ink = red * 0.2126f + green * 0.7152f + blue * 0.0722f > 0.82f ? 0.0f : 1.0f;
  col = igColorConvertFloat4ToU32((ImVec4){ink, ink, ink, 0.8f * alpha});
  if (over_w <= 2.0f * dpi) {
    draw_spine_lines(env, bp, cx, cy, col, over_w);
    return 0;
  }
  draw_spine_mesh(env, bp, cx, cy, col, over_w);
  return 1;
}

static void draw_snake_imgui_eyes(tenv* env, snake* o, float fang, float hx,
                                  float hy, float ssc, float ea, float cx,
                                  float cy) {
  game_data* gdata = &env->usr->gdata;
  default_skin_data* dfs = gdata->dfs + ((1 - o->cusk) * (1 + o->cv));
  float ed = 6 * ssc;
  float esp = 6 * ssc;
  float iris_r = 6 * ssc * gdata->data.gsc;
  float pupil_r = dfs->pr * ssc * gdata->data.gsc;
  ImDrawList* draw = igGetWindowDrawList();
  ImU32 iris = igColorConvertFloat4ToU32((ImVec4){dfs->ec.r, dfs->ec.g, dfs->ec.b, ea});
  ImU32 pupil =
      igColorConvertFloat4ToU32((ImVec4){dfs->ppc.r, dfs->ppc.g, dfs->ppc.b, ea});
  float side;
  float ex;
  float ey;
  ImVec2 at;
  /* Only for a head on screen (OM, 2026-10-12). */
  if (!point_in_snake_view(gdata, hx, hy)) return;
  for (side = -1.0f; side <= 1.0f; side += 2.0f) {
    ex = cosf(fang) * ed + cosf(fang + side * (PI / 2)) * (esp + 0.5f);
    ey = sinf(fang) * ed + sinf(fang + side * (PI / 2)) * (esp + 0.5f);
    at.x = cx + (ex + hx - gdata->data.view_xx) * gdata->data.gsc;
    at.y = cy + (ey + hy - gdata->data.view_yy) * gdata->data.gsc;
    ImDrawList_AddCircleFilled(draw, at, iris_r, iris, 0);
    ex = cosf(fang) * (ed + 0.5f) + o->rex * ssc +
         cosf(fang + side * (PI / 2)) * esp;
    ey = sinf(fang) * (ed + 0.5f) + o->rey * ssc +
         sinf(fang + side * (PI / 2)) * esp;
    at.x = cx + (ex + hx - gdata->data.view_xx) * gdata->data.gsc;
    at.y = cy + (ey + hy - gdata->data.view_yy) * gdata->data.gsc;
    ImDrawList_AddCircleFilled(draw, at, pupil_r, pupil, 0);
  }
}

/* Battledome letters (OM, 2026-10-24; network/battledome.h). The server
 * spells its words with one pellet per pixel of a 5 x 7 font, `cell` apart.
 * Shown as pixels they read as loose dots, so each pixel is joined to its
 * neighbours by a straight stroke, like the font's own lines: across, down
 * and diagonally, but a diagonal only where no pixel already turns that
 * corner (else an L or a box would get a needless slash). The ring words are
 * turned, so neighbours are found by distance, never by a fixed grid. */
typedef struct bd_px {
  float x, y, cell;
  vec3s c;
  float a;
} bd_px;

#define BD_MAX_PX 6144
static bd_px bd_pxs[BD_MAX_PX];

static int bd_px_cmp(const void* a, const void* b) {
  float d = ((const bd_px*)a)->x - ((const bd_px*)b)->x;
  return (d > 0) - (d < 0);
}

/* Whether a letter pixel of this cell size sits at world (x, y). The list is
   sorted by x. */
static bool bd_px_at(int n, float x, float y, float cell) {
  float tol = cell * 0.3f;
  int lo = 0, hi = n;
  while (lo < hi) {
    int mid = (lo + hi) / 2;
    if (bd_pxs[mid].x < x - tol) lo = mid + 1;
    else hi = mid;
  }
  for (int i = lo; i < n && bd_pxs[i].x <= x + tol; i++)
    if (fabsf(bd_pxs[i].y - y) <= tol && fabsf(bd_pxs[i].cell - cell) < 0.5f)
      return true;
  return false;
}

static void bd_dot(fd_renderer* fdr, float sx, float sy, float w, vec3s c,
                   float a) {
  /* fd.slang fills 0.9 of the quad. */
  float z = w / 0.9f;
  fd_renderer_push_t(fdr, &(fd_instance){{sx - z * 0.5f, sy - z * 0.5f, z},
                                         (vec4s){c.r, c.g, c.b, a / 0.7f},
                                         0.0f, 0.0f});
}

/* A round-capped line as a row of dots close enough to read as one. */
static void bd_line(fd_renderer* fdr, float ax, float ay, float bx, float by,
                    float w, vec3s c, float a) {
  float len = hypotf(bx - ax, by - ay);
  int n = (int)ceilf(len / fmaxf(1.0f, w * 0.25f));
  if (n > 256) n = 256;
  for (int k = 1; k < n; k++) {
    float f = (float)k / (float)n;
    bd_dot(fdr, ax + (bx - ax) * f, ay + (by - ay) * f, w, c, a);
  }
}

static void draw_battledome(tenv* env, float mww2, float mhh2) {
  tuser_data* usr = env->usr;
  game_data* gdata = &usr->gdata;
  fd_renderer* fdr = usr->r->fdr;
  float gsc = gdata->data.gsc;

  int n = 0;
  int foods_len = tdarray_length(gdata->data.foods);
  for (int i = 0; i < foods_len && n < BD_MAX_PX; i++) {
    food* fo = gdata->data.foods + i;
    float cell;
    bool square;
    if (fo->eaten ||
        !battledome_text_pixel(fo->xx, fo->yy, fo->sz, &cell, &square))
      continue;
    /* A cell beyond the food margin, so a stroke into the edge still draws. */
    if (fo->xx < gdata->data.fpx1 - 2 * cell || fo->yy < gdata->data.fpy1 - 2 * cell ||
        fo->xx > gdata->data.fpx2 + 2 * cell || fo->yy > gdata->data.fpy2 + 2 * cell)
      continue;
    bd_pxs[n++] = (bd_px){fo->xx, fo->yy, cell, gdata->cg_colors[fo->cv], fo->fr};
  }
  if (n > 1) qsort(bd_pxs, (size_t)n, sizeof(bd_px), bd_px_cmp);

  for (int i = 0; i < n; i++) {
    bd_px* p = bd_pxs + i;
    float w = p->cell * 0.62f * gsc;
    float sx = mww2 + gsc * (p->x - gdata->data.view_xx);
    float sy = mhh2 + gsc * (p->y - gdata->data.view_yy);
    bd_dot(fdr, sx, sy, w, p->c, p->a);

    /* Each pair once: only neighbours further along x (or level, below). */
    for (int j = i + 1; j < n && bd_pxs[j].x - p->x <= p->cell * 1.6f; j++) {
      bd_px* q = bd_pxs + j;
      if (fabsf(q->cell - p->cell) >= 0.5f) continue;
      float dx = q->x - p->x, dy = q->y - p->y;
      float d = sqrtf(dx * dx + dy * dy) / p->cell;
      bool straight = fabsf(d - 1.0f) <= 0.2f;
      bool diagonal = fabsf(d - 1.41421f) <= 0.2f;
      if (!straight && !diagonal) continue;
      if (diagonal) {
        /* The two corner pixels between them, in the text's own (maybe
           turned) grid: d = u + v with u, v one cell and at right angles. */
        float ux = (dx + dy) * 0.5f, uy = (dy - dx) * 0.5f;
        if (bd_px_at(n, p->x + ux, p->y + uy, p->cell) ||
            bd_px_at(n, p->x + dx - ux, p->y + dy - uy, p->cell))
          continue;
      }
      float qx = mww2 + gsc * (q->x - gdata->data.view_xx);
      float qy = mhh2 + gsc * (q->y - gdata->data.view_yy);
      bd_line(fdr, sx, sy, qx, qy, w, p->c, fminf(p->a, q->a));
    }
  }

  /* The dome: a green ring of the server's radius round the middle, as the
     server's own live view draws it, only where it is on screen. */
  float dome_x, dome_y, dome_r;
  if (battledome_dome(&dome_x, &dome_y, &dome_r)) {
    float sx = mww2 + gsc * (dome_x - gdata->data.view_xx);
    float sy = mhh2 + gsc * (dome_y - gdata->data.view_yy);
    float rr = dome_r * gsc;
    float half = sqrtf(mww2 * mww2 + mhh2 * mhh2);
    float dist = hypotf(sx - mww2, sy - mhh2);
    if (rr > 1 && dist + half > rr && dist - half < rr) {
      float unit = fminf(mww2, mhh2) * 2.0f / 480.0f;
      float w = fmaxf(3.0f, 2.5f * unit);
      int steps = (int)fminf(20000.0f, 2 * PI * rr / (w * 0.4f));
      vec3s green = {0.0f, 1.0f, 0.0f};
      for (int k = 0; k < steps; k++) {
        float a = 2 * PI * (float)k / (float)steps;
        float px = sx + cosf(a) * rr, py = sy + sinf(a) * rr;
        if (px < -w || py < -w || px > 2 * mww2 + w || py > 2 * mhh2 + w)
          continue;
        bd_dot(fdr, px, py, w, green, 1.0f);
      }
    }
  }
}

/* Food textures (OM, 2026-10-25; fd.slang food_texture). `shape` carries
   texture * 16 + shape. A pellet under WYRM_FOOD_LIGHT_PX across, or any
   pellet on a screen with more than WYRM_FOOD_CROWD in view, is one cheap
   dot whatever the texture: the far and the crowded cost least. */
#define WYRM_FOOD_LIGHT_PX 6.0f
#define WYRM_FOOD_CROWD 1800

static void wyrm_food_push(fd_renderer* fdr, float cx, float cy, float d, vec3s c,
                           float fade, float flicker, float shape, int tex,
                           bool crowded) {
  /* One look at every distance (OM, 2026-10-26): no far or crowded swap,
     which showed a different pellet while zooming and as food appeared. */
  (void)crowded;
  switch (tex) {
    case 1: {
      /* The AIR client: a dark disc a little larger and lower, blurred,
         under the orb (Main.as foimg_t / per_color_imgs img_t). Too small to
         see, the shadow is left out. */
      float z = d * 0.58f;
      if (d * 0.9f >= WYRM_FOOD_LIGHT_PX)
        fd_renderer_push_u(fdr, &(fd_instance){{cx - z * 0.5f, cy + d * 0.08f - z * 0.5f, z},
                                             (vec4s){0, 0, 0, fade}, 0.0f, 6 * 16.0f});
      fd_renderer_push(fdr, &(fd_instance){{cx - d * 0.5f, cy - d * 0.5f, d},
                                           (vec4s){c.r, c.g, c.b, fade}, flicker,
                                           1 * 16.0f});
      return;
    }
    case 2:
      fd_renderer_push(fdr, &(fd_instance){{cx - d * 0.5f, cy - d * 0.5f, d},
                                           (vec4s){c.r, c.g, c.b, fade}, 0.0f,
                                           2 * 16.0f});
      return;
    case 3: {
      float z = d * 0.3f;
      fd_renderer_push(fdr, &(fd_instance){{cx - z * 0.5f, cy - z * 0.5f, z},
                                           (vec4s){c.r, c.g, c.b, fade}, 0.0f,
                                           3 * 16.0f});
      return;
    }
    case 4:
      fd_renderer_push(fdr, &(fd_instance){{cx - d * 0.5f, cy - d * 0.5f, d},
                                           (vec4s){c.r, c.g, c.b, fade}, 0.0f,
                                           4 * 16.0f});
      return;
    case 6:
    case 7:
      /* Candy and Pixel (OM, 2026-10-26): fd.slang styles 7 and 8 (6 is the
         Slither app's shadow). */
      fd_renderer_push(fdr, &(fd_instance){{cx - d * 0.5f, cy - d * 0.5f, d},
                                           (vec4s){c.r, c.g, c.b, fade}, 0.0f,
                                           (tex + 1) * 16.0f});
      return;
    case 5:
      /* Gem (OM, 2026-10-26; it replaced Small): a cut stone. */
      fd_renderer_push(fdr, &(fd_instance){{cx - d * 0.5f, cy - d * 0.5f, d},
                                           (vec4s){c.r, c.g, c.b, fade}, 0.0f,
                                           5 * 16.0f});
      return;
    default:
      fd_renderer_push(fdr, &(fd_instance){{cx - d * 0.5f, cy - d * 0.5f, d},
                                           (vec4s){c.r, c.g, c.b, fade}, flicker,
                                           shape});
      return;
  }
}

/* Skin textures (OM, 2026-10-25), NTL 9.76's "Skin texture": 1 Rounded
   plain (a disc in the bead's colour), 2 Squared plain (upright squares, the
   head's first two round), 3 NTL textured (a soft shaded ball, keeping the
   body's light and dark rhythm), 4 Striped plain (squares turned with the
   body). Runs after a snake's beads are pushed and rewrites them; shadows
   (black) and Skin Studio beads (their own atlas) are left alone. */
/* Hidden bead parts, far snakes and the frame budget (OM, 2026-10-30;
   Wyrm iOS/Scripts/engine_bead_cull_2026_10_30.py). */
static int wyrm_budget_level;
static float wyrm_budget_avg_ms, wyrm_budget_target_ms = 1000.0f / 60.0f;
static Uint64 wyrm_budget_last_ns, wyrm_budget_changed_ns, wyrm_budget_rate_ns;

/* Once a frame: how far this frame came after the last, against the display. */
static void wyrm_budget_tick(tenv* env) {
  Uint64 now = SDL_GetTicksNS();
  if (wyrm_budget_rate_ns == 0 || now - wyrm_budget_rate_ns > SDL_NS_PER_SECOND) {
    wyrm_budget_rate_ns = now;
    SDL_DisplayID display =
        env->wnd && env->wnd->handle ? SDL_GetDisplayForWindow(env->wnd->handle) : 0;
    const SDL_DisplayMode* dm = display ? SDL_GetCurrentDisplayMode(display) : NULL;
    if (dm && dm->refresh_rate >= 30.0f && dm->refresh_rate <= 360.0f)
      wyrm_budget_target_ms = 1000.0f / dm->refresh_rate;
  }
  float dt = wyrm_budget_last_ns ? (float)(now - wyrm_budget_last_ns) / 1e6f : -1;
  wyrm_budget_last_ns = now;
  /* A pause (menu, background) starts the average afresh. */
  if (dt <= 0 || dt > 250) {
    wyrm_budget_avg_ms = wyrm_budget_target_ms;
    return;
  }
  wyrm_budget_avg_ms += (dt - wyrm_budget_avg_ms) * 0.05f;
  float since = (float)(now - wyrm_budget_changed_ns) / 1e9f;
  int before = wyrm_budget_level;
  if (wyrm_budget_avg_ms > wyrm_budget_target_ms * 1.2f && since > 1.0f &&
      wyrm_budget_level < 3)
    wyrm_budget_level++;
  else if (wyrm_budget_avg_ms < wyrm_budget_target_ms * 1.05f && since > 4.0f &&
           wyrm_budget_level > 0)
    wyrm_budget_level--;
  if (wyrm_budget_level != before) {
    wyrm_budget_changed_ns = now;
    SDL_Log("Wyrm frame budget: level %d (%.1f ms a frame, display %.1f ms)",
            wyrm_budget_level, wyrm_budget_avg_ms, wyrm_budget_target_ms);
  }
}

/* A round body bead of this snake (not a shadow, not the square plain bead). */
static bool bead_round_body(const bp_instance* in, float size) {
  if (fabsf(in->circle.z - size) > 0.01f) return false;
  if (in->color.r == 0 && in->color.g == 0 && in->color.b == 0) return false;
  float u = in->uv_rect.x;
  return !(u < -1.5f && u > -2.5f);
}

/* Solid to its rim: alpha 1 and a colour-group cell (rows 0..5) or a plain kind. */
static bool bead_solid_cover(const bp_instance* in) {
  if (in->color.a < 0.999f) return false;
  float u = in->uv_rect.x;
  if (u < -0.5f) return true;
  if (u >= 3.0f) return false;
  return fabsf(in->uv_rect.z - 1.0f / 7.0f) < 1e-5f &&
         fabsf(in->uv_rect.w - 1.0f / 9.0f) < 1e-5f &&
         in->uv_rect.y < 6.0f / 9.0f - 1e-4f;
}

static bool bead_shadow(const bp_instance* in) {
  return in->uv_rect.x < 3.0f && in->uv_rect.x > -0.5f && in->color.r == 0 &&
         in->color.g == 0 && in->color.b == 0;
}

#define WYRM_CULL_MAX 8192
static void bead_cull_apply(tenv* env, snake* o, int start, float size) {
  static int idx[WYRM_CULL_MAX];
  static unsigned char drop[WYRM_CULL_MAX];
  bp_renderer* r = env->usr->r->bpr;
  int end = r->num_instances;
  if (start < 0 || end - start < 2 || size < 1.0f) return;
  /* Every bead and every shadow stays (OM, 2026-11-07: "zooming made the
     beads fewer and more; lower their quality, not their count", and
     shadows vanished mid-match). Thinning every other bead and dropping
     other snakes' shadows under load are gone; what is left only clips the
     parts of a bead hidden under the next one (less overdraw, same look).
     Quality already falls with distance by itself: the bead atlas is
     mipmapped (bead_sampler), so small beads read smaller levels.
     Wyrm iOS/Scripts/engine_bead_count_2026_11_07.py. */
  (void)o;
  const float thin = 0.0f;
  const bool drop_shadows = false;

  int n = 0;
  for (int i = start; i < end && n < WYRM_CULL_MAX; i++)
    if (bead_round_body(r->instances + i, size)) {
      idx[n] = i;
      drop[n] = 0;
      n++;
    }

  /* Far beads: every other one, where its kept neighbours still overlap by
     half a bead or more. The tail tip and the last four (the head) stay. */
  for (int k = 1; k < n - 4; k += 2) {
    const bp_instance* a = r->instances + idx[k - 1];
    const bp_instance* b = r->instances + idx[k + 1];
    float dx = b->circle.x - a->circle.x, dy = b->circle.y - a->circle.y;
    if (dx * dx + dy * dy < 4.0f * thin * thin) drop[k] = 1;
  }

  /* Hidden parts: each kept bead against the next kept one. */
  const float edge = 2.0f; /* EDGE_SOFTNESS in modules/common.slang */
  const float half = 0.5f * (size + edge);
  const float solid = fminf(half - edge - 0.5f, 0.95f * half);
  for (int k = 0; k < n; k++) {
    if (drop[k]) continue;
    bp_instance* in = r->instances + idx[k];
    in->clip = (vec4s){{0, 0, 0, 0}};
    int next = k + 1;
    while (next < n && drop[next]) next++;
    if (next >= n || solid <= 0) continue;
    const bp_instance* cover = r->instances + idx[next];
    if (!bead_solid_cover(cover)) continue;
    float dx = cover->circle.x - in->circle.x, dy = cover->circle.y - in->circle.y;
    float s = sqrtf(dx * dx + dy * dy);
    if (s < 0.5f) continue;
    float d = (s * s + half * half - solid * solid) / (2.0f * s);
    if (d >= half - 0.5f) continue;
    in->clip = (vec4s){{dx / s, dy / s, d, 1}};
  }

  bool any = drop_shadows;
  for (int k = 0; k < n && !any; k++) any = drop[k];
  if (!any) return;
  int kept = start, k = 0;
  for (int i = start; i < end; i++) {
    if (k < n && idx[k] == i) {
      if (drop[k++]) continue;
    } else if (drop_shadows && bead_shadow(r->instances + i)) {
      continue;
    }
    r->instances[kept++] = r->instances[i];
  }
  r->num_instances = kept;
}

static bool skin_uv_same(vec4s a, vec4s b) {
  return a.x == b.x && a.y == b.y && a.z == b.z && a.w == b.w;
}

static bool skin_texture_bead(const bp_instance* in) {
  if (in->uv_rect.x >= 3.0f) return false;
  return !(in->color.r == 0 && in->color.g == 0 && in->color.b == 0);
}

static void skin_texture_apply(tenv* env, snake* o, int start) {
  user_settings* usrs = &env->usr->usrs;
  int tex = usrs->ext.skin_tex;
  /* Only Rounded plain (1) and NTL textured (3) are offered (OM, 2026-10-26);
     a saved Squared or Striped plain draws Wyrm's beads. */
  /* Rounded plain (1), NTL textured (3) and the five of 2026-10-26 (5..9). */
  if (tex != 1 && tex != 3 && (tex < 5 || tex > 9)) return;
  game_data* gdata = &env->usr->gdata;
  if (usrs->ext.skin_tex_mine && o->id != gdata->data.snake_id) return;
  bp_renderer* r = env->usr->r->bpr;
  int end = r->num_instances;
  if (start < 0 || start >= end) return;

  int beads = 0;
  float head_px = 0;
  for (int i = start; i < end; i++)
    if (skin_texture_bead(r->instances + i)) {
      beads++;
      head_px = r->instances[i].circle.z; /* the head is pushed last */
    }
  /* Every bead at every zoom (OM, 2026-11-07): the far-away "every other
     bead" thinning made the snake's beads come and go while zooming. */
  bool thin = false;
  (void)head_px;

  int seen = 0, kept = start;
  for (int i = start; i < end; i++) {
    bp_instance in = r->instances[i];
    if (!skin_texture_bead(&in)) {
      /* No shadow under a plain or NTL snake: NTL draws none with them. Studio
         beads (their own atlas) stay. */
      if (in.uv_rect.x >= 3.0f) r->instances[kept++] = in;
      continue;
    }
    int from_head = beads - 1 - seen;
    seen++;
    if (thin && from_head > 2 && (from_head & 1)) continue;

    vec3s col = {{in.color.r, in.color.g, in.color.b}};
    float shade = 1.0f;
    for (int g = 0; g < NUM_COLOR_GROUPS; g++) {
      if (g == BLANK_UV) continue;
      if (skin_uv_same(gdata->cg_uvs[g], in.uv_rect)) {
        shade = in.color.r;
        col = gdata->cg_colors[g];
        break;
      }
    }
    int kind = 2;
    if (tex == 1) kind = 1;
    else if (tex == 2) {
      kind = from_head < 2 ? 1 : 2;
      in.circle.w = 0;
    } else if (tex >= 5) {
      /* Glossy, Matte, Neon, Pearl, Metal (OM, 2026-10-26): upright like NTL
         textured, with the body's light and dark rhythm. */
      kind = tex;
      in.circle.w = 0;
      col.r *= shade;
      col.g *= shade;
      col.b *= shade;
    } else if (tex == 3) {
      /* Upright, as NTL draws its sheet: the light comes from the top left
         whichever way the body runs. */
      kind = 3;
      in.circle.w = 0;
      col.r *= shade;
      col.g *= shade;
      col.b *= shade;
    }
    in.uv_rect = (vec4s){{-(float)kind, 0, 0, 0}};
    in.color = (vec4s){{col.r, col.g, col.b, in.color.a}};
    r->instances[kept++] = in;
  }
  r->num_instances = kept;
}

void redraw(tenv* env) {
  colour_cache_snake = NULL;
  colour_cache_skin = NULL;
  android_team_begin_frame();
  tuser_data* usr = env->usr;
  tcontext* ctx = env->ctx;
  game_data* gdata = &usr->gdata;
  user_settings* usrs = &usr->usrs;

  /* Once a frame, so that every rope in the frame is advanced by the same
     amount of time rather than by whatever the clock said when its snake
     happened to be drawn. */
  tags_tick(env);
  /* Tags flying off dead snakes (OM, 2026-10-13). */
  tags_draw_flying(env);
  /* The frame budget (OM, 2026-10-30): measured once a frame. */
  wyrm_budget_tick(env);

  float lvx = gdata->data.view_xx;
  float lvy = gdata->data.view_xx;

  float mww2 = ctx->size[0] / 2.0f;
  float mhh2 = ctx->size[1] / 2.0f;
  /* AIR's sounds: the view box (follows the zoom) and the boost. */
  wyrm_audio_view(mww2, mhh2);
  wyrm_audio_frame(gdata);

  float mwwp50 = ctx->size[0] + 50;
  float mhhp50 = ctx->size[1] + 50;

  gameplay_mode* mode = usrs->modes + usrs->hotkeys[HOTKEY_ASSIST].active;

  if (!gdata->data.dead) {
    if (gdata->data.fvtg > 0) {
      gdata->data.fvtg--;
      gdata->data.fvx = gdata->data.fvxs[gdata->data.fvpos];
      gdata->data.fvy = gdata->data.fvys[gdata->data.fvpos];
      gdata->data.fvxs[gdata->data.fvpos] = 0;
      gdata->data.fvys[gdata->data.fvpos] = 0;
      gdata->data.fvpos++;
      if (gdata->data.fvpos >= GD_VFC) gdata->data.fvpos = 0;
    }

    int snakes_len = tdarray_length(gdata->data.snakes);
    /* Nothing to follow means the camera stays where it was, rather than
       following a snake that is no longer in the list. */
    if (gdata->data.follow_view && snakes_len > 0) {
      snake* me = gdata->data.snakes + (snakes_len - 1);
      gdata->data.view_xx = me->xx + me->fx + gdata->data.fvx;
      gdata->data.view_yy = me->yy + me->fy + gdata->data.fvy;
      /* Look ahead (OM, 2026-10-05): slither's camera sits ahead of the
         snake; off, the offset is zero. */
      mobile_controls_look_ahead_step(env);
      float ahead_x, ahead_y;
      mobile_controls_look_ahead_offset(env, &ahead_x, &ahead_y);
      if (gdata->data.gsc > 0.0f) {
        gdata->data.view_xx += ahead_x / gdata->data.gsc;
        gdata->data.view_yy += ahead_y / gdata->data.gsc;
      }
    }

    gdata->data.bpx1 = gdata->data.view_xx - (mww2 / gdata->data.gsc + 84);
    gdata->data.bpy1 = gdata->data.view_yy - (mhh2 / gdata->data.gsc + 84);
    gdata->data.bpx2 = gdata->data.view_xx + (mww2 / gdata->data.gsc + 84);
    gdata->data.bpy2 = gdata->data.view_yy + (mhh2 / gdata->data.gsc + 84);
    gdata->data.fpx1 = gdata->data.view_xx - (mww2 / gdata->data.gsc + 24);
    gdata->data.fpy1 = gdata->data.view_yy - (mhh2 / gdata->data.gsc + 24);
    gdata->data.fpx2 = gdata->data.view_xx + (mww2 / gdata->data.gsc + 24);
    gdata->data.fpy2 = gdata->data.view_yy + (mhh2 / gdata->data.gsc + 24);
  }

  // draw foods and preys:
  int foods_len = tdarray_length(gdata->data.foods);
  int preys_len = tdarray_length(gdata->data.preys);

  /* Every texture follows the snake's zoom; the one Food size slider is
     applied once in d. Legacy const_food_scale storage is ignored. */
  float food_scale = gdata->data.gsc;
  float bd_cell = 0;
  /* Food texture and the crowded screen (OM, 2026-10-25). */
  const int food_tex =
      usrs->ext.food_tex[usrs->hotkeys[HOTKEY_ASSIST].active ? 1 : 0];
  const bool food_crowded = false; /* texture appearance never depends on crowd */
  bool bd_square = false;

  if (mode->uniform_food_color) {
    for (int i = foods_len - 1; i >= 0; i--) {
      food* fo = gdata->data.foods + i;

      if (battledome_text_pixel(fo->xx, fo->yy, fo->sz, &bd_cell, &bd_square))
        continue;
      if (usrs->hotkeys[HOTKEY_BIG_FOOD].active && fo->sz < 10) continue;

      if (fo->rx >= gdata->data.fpx1 && fo->ry >= gdata->data.fpy1 &&
          fo->rx <= gdata->data.fpx2 && fo->ry <= gdata->data.fpy2) {
        float d = gdata->fsz[fo->cv2] * food_scale * fo->rad * mode->food_scale;
        vec3s c = {mode->food_color[0], mode->food_color[1],
                   mode->food_color[2]};

        float fx =
            mww2 + gdata->data.gsc * (fo->rx - gdata->data.view_xx) - d * 0.5;
        float fy =
            mhh2 + gdata->data.gsc * (fo->ry - gdata->data.view_yy) - d * 0.5;

        if (food_tex == 1) {
          original_food(usr->r,fx+d*.5f,fy+d*.5f,d,c,fo->fr,
                        mode->uniform_food_color ? -1 : fo->cv);
        } else
        wyrm_food_push(usr->r->fdr, fx + d * 0.5f, fy + d * 0.5f, d, c, fo->fr,
                       mode->food_flicker * (.5 + .5 * cosf(fo->gfr / 13)),
                       food_shape(fo, mode->food_type), food_tex, food_crowded);
      }
    }
  } else {
    for (int i = foods_len - 1; i >= 0; i--) {
      food* fo = gdata->data.foods + i;

      if (battledome_text_pixel(fo->xx, fo->yy, fo->sz, &bd_cell, &bd_square))
        continue;
      if (usrs->hotkeys[HOTKEY_BIG_FOOD].active && fo->sz < 10) continue;

      if (fo->rx >= gdata->data.fpx1 && fo->ry >= gdata->data.fpy1 &&
          fo->rx <= gdata->data.fpx2 && fo->ry <= gdata->data.fpy2) {
        float d = gdata->fsz[fo->cv2] * food_scale * fo->rad * mode->food_scale;
        vec3s c = gdata->cg_colors[fo->cv];

        float fx =
            mww2 + gdata->data.gsc * (fo->rx - gdata->data.view_xx) - d * 0.5;
        float fy =
            mhh2 + gdata->data.gsc * (fo->ry - gdata->data.view_yy) - d * 0.5;

        if (food_tex == 1) {
          original_food(usr->r,fx+d*.5f,fy+d*.5f,d,c,fo->fr,
                        mode->uniform_food_color ? -1 : fo->cv);
        } else
        wyrm_food_push(usr->r->fdr, fx + d * 0.5f, fy + d * 0.5f, d, c, fo->fr,
                       mode->food_flicker * (.5 + .5 * cosf(fo->gfr / 13)),
                       food_shape(fo, mode->food_type), food_tex, food_crowded);
      }
    }
  }

  /* Battledome letters and dome: their own layer, drawn after the food
     (so above it) and before the snakes. */
  draw_battledome(env, mww2, mhh2);

  for (int i = preys_len - 1; i >= 0; i--) {
    prey* pr = gdata->data.preys + i;
    float tx = pr->xx + pr->fx;
    float ty = pr->yy + pr->fy;
    float px = mww2 + gdata->data.gsc * (tx - gdata->data.view_xx);
    float py = mhh2 + gdata->data.gsc * (ty - gdata->data.view_yy);
    if (px >= -50 && py >= -50 && px <= mwwp50 && py <= mhhp50) {
      if (pr->eaten) {
        snake* o = get_snake(gdata, pr->ebid);
        float k = powf(pr->eaten_fr, 2);
        tx += (o->xx + o->fx + cosf(o->ang + o->fa) * (43 - k * 24) * (1 - k) -
               tx) *
              k;
        ty += (o->yy + o->fy + sinf(o->ang + o->fa) * (43 - k * 24) * (1 - k) -
               ty) *
              k;
        px = mww2 + gdata->data.gsc * (tx - gdata->data.view_xx);
        py = mhh2 + gdata->data.gsc * (ty - gdata->data.view_yy);
      }

      float d = gdata->psz[pr->cv2] * gdata->data.gsc * pr->rad;
      vec3s c = gdata->cg_colors[pr->cv];
      float fx = px - d * 0.5f;
      float fy = py - d * 0.5f;

      fd_renderer_push_p(usr->r->fdr,
                         &(fd_instance){{fx, fy, d},
                                        (vec4s){c.r, c.g, c.b, pr->fr * 0.75f},
                                        .5 + .5 * cosf(pr->gfr / 13), 0.0f});
    }
  }

  // draw snakes:
  int snakes_len = tdarray_length(gdata->data.snakes);
  for (int i = snakes_len - 1; i >= 0; i--) {
    snake* o = gdata->data.snakes + i;
    int pts_len = tdarray_length(o->pts);
    /* The head can enter before any stored body point crosses the edge. The
       renderer still needs one body point before it can build the centreline. */
    int iiv = pts_len > 0 &&
              point_in_snake_view(gdata, o->xx + o->fx, o->yy + o->fy);

    for (int j = pts_len - 1; !iiv && j >= 0; j--) {
      body_part* po = o->pts + j;
      float px = po->xx + po->fx;
      float py = po->yy + po->fy;

      if (point_in_snake_view(gdata, px, py)) {
        iiv = true;
      }
    }
    if (o->iiv != iiv) {
      o->iiv = iiv;
      if (iiv) o->ehang = o->wehang = o->ang;
    }
  }

  ImFont* snake_name_font = usr->imgui_data.mono_font[FONT_SIZE_REGULAR];
#if defined(__ANDROID__)
  const float snake_name_font_size =
      13.0f*fminf(ctx->size[0],ctx->size[1])/480.0f/SHEET_EM*usrs->ext.name_scale;
#else
  const float snake_name_font_size = snake_name_font->LegacySize*usrs->ext.name_scale;
#endif
  /* Measure fallback strings at the same size that is actually drawn. */
  igPushFont(snake_name_font, snake_name_font_size);
#if !defined(__ANDROID__)
  ImFont* snake_score_font =
      usr->imgui_data.mono_font_bold[FONT_SIZE_REGULAR];
  ImDrawList* snake_name_draw_list =
      igGetForegroundDrawList_ViewportPtr(igGetMainViewport());
#endif
#if defined(__ANDROID__) || defined(WYRM_MOBILE)
  /* Names (OM, 2026-10-12): the font atlas is bound once for the whole snake
     loop, so every name joins one draw command (the reference client draws them in one
     batch; a push/pop per name made each its own). Direct world-space text
     does not go through ImGui's widget path, and with the dynamic font atlas
     this list can otherwise have an unrelated texture bound: Adreno then
     samples the glyph UVs from it (opaque black strips behind remote snakes).
     The spine, skinless strip and ImGui eyes in this loop use the atlas's
     white pixel, so they want the atlas bound too. */
  ImDrawList* name_draw_list = igGetWindowDrawList();
  ImDrawList_PushTexture(name_draw_list,
                         snake_name_font->ContainerAtlas->TexRef);
  /* Names on any floor (OM, 2026-10-18): the floor picks the ink once. */
  const float name_floor_luma = wyrm_floor_luma_now(env);
#endif
  for (int i = snakes_len - 1; i >= 0; i--) {
    snake* o = gdata->data.snakes + i;
    int sct = o->sct + o->rsc;

    if (o->iiv) {
      float hx = o->xx + o->fx;
      float hy = o->yy + o->fy;
      float px = hx;
      float py = hy;
      float a = o->alive_amt * (1 - o->dead_amt);

      if (usrs->hotkeys[HOTKEY_SHOW_NAMES].active) {
        int score = (int)floorf((gdata->data.fpsls[sct] +
                                 o->fam / gdata->data.fmlts[sct] - 1) *
                                    15 -
                                5) /
                    1;
        score = GLM_MIN(GLM_MAX(score, 0), 999999);
        double score_rep = score / 1000.0;
        char nk_label_buff[MAX_NICKNAME_LEN + 1 + 7 + 1] = {0};
        char score_rep_str[9] = {0};
        sprintf(score_rep_str, " %.1fK", score_rep);
        sprintf(nk_label_buff, "%s%s", o->nk, score_rep_str);

        /* Your own name is skipped by default — you know what you called
           yourself, and it would sit over the one snake you are already
           looking at. Asked for by a player who wanted to find herself in a
           crowd, which is the one moment it earns its space. */
        if (o->id != gdata->data.snake_id || usrs->show_own_name) {
          float ntx = o->xx + o->fx;
          float nty = o->yy + o->fy;

          ImVec2 tsize;
          ImVec2 nsize;
          igCalcTextSize(&nsize, o->nk, NULL, false, -1);
          igCalcTextSize(&tsize, nk_label_buff, NULL, false, -1);

          ntx = mww2 + (ntx - gdata->data.view_xx) * gdata->data.gsc;
          nty = mhh2 + (nty - gdata->data.view_yy) * gdata->data.gsc;

          vec3s* scolor =
              gdata->cg_colors +
              (o->cusk ? o->cusk_data[0] : gdata->default_skins[o->cv][1]);

          vec3 ncolor;
          glm_vec3_lerp((float*)scolor, (vec3){1, 1, 1},
                        mode->player_names_outline ? 0.7f : 0.6f, ncolor);
          vec3 lcolor;
          glm_vec3_lerp((float*)scolor, (vec3){1, 1, 1},
                        mode->player_names_outline ? 0.8f : 0.7f, lcolor);

          if (android_home_near_original()) {
            float z=gdata->data.gsc;
            float unit=fminf(ctx->size[0],ctx->size[1])/480.0f;
            float textz=z+((gdata->ai_mode ? .8f : .7f)*unit-z)*.25f;
            float size=13*z*unit/(textz > .001f ? textz : .001f)*usrs->ext.name_scale;
            vec3s colour=*scolor;
            for(int j=0;j<3;++j) colour.raw[j] = fminf(255,roundf(127.5f+colour.raw[j]*255*.64f))/255;
            original_name(usr->r,o->nk,size,ntx,
                mhh2+(o->yy+o->fy+30+13*o->sc-gdata->data.view_yy)*z,colour,a);
          } else {
          ntx = ntx - (usrs->snake_scores ? tsize.x : nsize.x) * 0.5f;
          nty = nty + 32 + 11 * o->sc * gdata->data.gsc;

#if defined(__ANDROID__)
          // The font atlas is bound once around the snake loop (above).
          // The floor picks a light or dark ink, with a soft shadow of the
          // other tone under it (OM, 2026-10-18).
          {
            ImU32 name_ink, name_shadow;
            wyrm_name_inks(name_floor_luma, *scolor, a, &name_ink, &name_shadow);
            if (o->id != gdata->data.snake_id &&
                android_team_is_teammate(env, o->ntl_id, o->nk)) {
              name_ink = igColorConvertFloat4ToU32((ImVec4){0.224f, 1.0f, 0.353f, a});
              name_shadow = igColorConvertFloat4ToU32((ImVec4){0, 0, 0, 0.9f * a});
            }
            const char* label = usrs->snake_scores ? nk_label_buff : o->nk;
            float size = 13.0f*fminf(ctx->size[0],ctx->size[1])/480.0f/SHEET_EM*usrs->ext.name_scale;
            float lift = size * 0.07f;
            if (lift < 1.0f) lift = 1.0f;
            if (sheet_ok(label)) {
              /* The original's thin glyphs (OM, 2026-11-01), centred on
                 their own width. */
              float shown = usrs->snake_scores ? tsize.x : nsize.x;
              float sx = ntx + (shown - sheet_width(label, size, SHEET_THIN)) * 0.5f;
              /* Main.as names (OM, 2026-11-01): csw = 0.5 + 0.64 c, face at
                 the name's alpha, outline 0.75 of it, both NORMAL. Not the
                 floor's dark ink: that read dark and muddy on light floors. */
              ImU32 sheet_ink = name_ink;
              if (o->id == gdata->data.snake_id ||
                  !android_team_is_teammate(env, o->ntl_id, o->nk)) {
                ImVec4 csw = {fminf(1, fmaxf(0, roundf(127.5f + scolor->r*255*.64f)/255)),
                              fminf(1, fmaxf(0, roundf(127.5f + scolor->g*255*.64f)/255)),
                              fminf(1, fmaxf(0, roundf(127.5f + scolor->b*255*.64f)/255)), a};
                sheet_ink = igColorConvertFloat4ToU32(csw);
              }
              sheet_draw(name_draw_list, label, size, (ImVec2){sx, nty}, sheet_ink,
                         SHEET_THIN, 0.75f, false);
            } else {
            ImDrawList_AddText_FontPtr(name_draw_list, snake_name_font, size,
                                       (ImVec2){ntx + lift, nty + lift},
                                       name_shadow, label, NULL, 0, NULL);
            ImDrawList_AddText_FontPtr(name_draw_list, snake_name_font, size,
                                       (ImVec2){ntx, nty}, name_ink, label,
                                       NULL, 0, NULL);
            }
          }
#else
          if (mode->player_names_outline) {
            const ImVec2 shadow_offsets[] = {
                {-1.5f, 0}, {1.5f, 0}, {0, -1.5f}, {0, 1.5f}};
            for (int shadow = 0; shadow < 4; shadow++) {
              ImDrawList_AddText_FontPtr(
                  snake_name_draw_list, snake_name_font, snake_name_font_size,
                  (ImVec2){ntx + shadow_offsets[shadow].x,
                           nty + shadow_offsets[shadow].y},
                  igColorConvertFloat4ToU32((ImVec4){0, 0, 0, a}), o->nk, NULL,
                  0, NULL);
            }

            if (usrs->snake_scores) {
              for (int shadow = 0; shadow < 4; shadow++) {
                ImDrawList_AddText_FontPtr(
                    snake_name_draw_list, snake_score_font,
                    snake_name_font_size,
                    (ImVec2){ntx + nsize.x + shadow_offsets[shadow].x,
                             nty + shadow_offsets[shadow].y},
                    igColorConvertFloat4ToU32((ImVec4){0, 0, 0, a}),
                    score_rep_str, NULL, 0, NULL);
              }
            }
          }

          ImDrawList_AddText_FontPtr(
              snake_name_draw_list, snake_name_font, snake_name_font_size,
              (ImVec2){ntx, nty},
              igColorConvertFloat4ToU32(
                  (ImVec4){ncolor[0], ncolor[1], ncolor[2],
                           mode->player_names_outline ? a : 0.5f * a}),
              o->nk, NULL, 0, NULL);

          if (usrs->snake_scores) {
            ImDrawList_AddText_FontPtr(
                snake_name_draw_list, snake_score_font, snake_name_font_size,
                (ImVec2){ntx + nsize.x, nty},
                igColorConvertFloat4ToU32(
                    (ImVec4){lcolor[0], lcolor[1], lcolor[2],
                             mode->player_names_outline ? a : 0.7f * a}),
                score_rep_str, NULL, 0, NULL);
          }
#endif
          }
        }
      }

      if (sct >= 2) {
        float fang = o->ehang;
        float ssc = o->sc;
        float lsz = 29 * ssc;
        float rl = o->cfl;
        int pts_len = tdarray_length(o->pts);
        int po_i = pts_len - 1;

        lsz *= .5;
        float ix1 = 0, iy1 = 0, ix2 = 0, iy2 = 0, ax1 = 0, ay1 = 0, ax2 = 0,
              ay2 = 0, cx2 = 0, cy2 = 0, pax1 = 0, pay1 = 0;
        int bp = 0;
        px = hx;
        py = hy;
        ax2 = px;
        ay2 = py;
        float ax = px;
        float ay = py;
        bp = 0;
        float px2 = 0, py2 = 0;
        float px3 = 0, py3 = 0;
        int po2_i = 0, po3_i = 0;
        int lpo_i = 0;
        float d = 0, d2 = 0;
        float dx = 0, dy = 0;
        float d3 = 0;
        float tx = 0, ty = 0;
        float ox = 0, oy = 0;
        float rx = 0, ry = 0;
        tx = 0;
        ty = 0;
        float j = 0, k = 0, l = 0, m = 0;
        float j2 = 0;
        float k2 = 0;
        float irl = 0;
        float wk = 0;
        float wwk = 0;
        float nkr = 0;
        float msl = o->msl;
        float mct = 6 / (mode->qsm * o->sep / 6.0f) * air_looks_density(o);

        float omct = mct;
        float rmct = 1 / mct;
        float sep = msl / mct;
        int ll = 0;
        po_i = pts_len - 1;
        px = o->pts[po_i].xx + o->pts[po_i].fx;
        py = o->pts[po_i].yy + o->pts[po_i].fy;
        d = sqrtf(powf(hx - px, 2) + powf(hy - py, 2));
        dx = (hx - px) / d;
        dy = (hy - py) / d;
        nkr = d / msl;
        int gpt_i, lgpt_i;
        int gpt2_i, lgpt2_i;
        int gpo_i;
        int q = 0;
        po3_i = pts_len - 2;
        po2_i = pts_len - 1;
        px = hx;
        py = hy;
        px2 = o->pts[po2_i].xx + o->pts[po2_i].fx;
        py2 = o->pts[po2_i].yy + o->pts[po2_i].fy;

        if (po3_i >= 0) {
          px3 = o->pts[po3_i].xx + o->pts[po3_i].fx;
          py3 = o->pts[po3_i].yy + o->pts[po3_i].fy;
        }
        if (d > msl) {
          px = px2 + dx * msl;
          py = py2 + dy * msl;
        }
        ax1 = px + (px2 - px) * .5;
        ay1 = py + (py2 - py) * .5;
        if (nkr < 1) {
          ax1 += (px - ax1) * (1 - nkr);
          ay1 += (py - ay1) * (1 - nkr);
        }
        ax2 = px3 + (px2 - px3) * .5;
        ay2 = py3 + (py2 - py3) * .5;
        d2 = sqrtf(powf(hx - ax1, 2) + powf(hy - ay1, 2));
        k = sep;
        m = 1;
        gpt_i = arp(o, q, hx, hy);
        q++;
        o->gptz[gpt_i].d = 0;
        lgpt_i = gpt_i;
        wk++;
        while (k < d2) {
          tx = hx - m * dx * sep;
          ty = hy - m * dy * sep;
          gpt_i = arp(o, q, tx, ty);
          q++;
          d = sep;
          o->gptz[gpt_i].d = d;
          lgpt_i = gpt_i;
          wk++;
          if (ll == 1) {
            ll = 2;
            break;
          }
          rl -= rmct;
          if (rl <= 0) {
            ll = 1;
            m += (rmct + rl) / rmct;
            k += sep * (rmct + rl) / rmct;
          } else {
            m++;
            k += sep;
          }
        }
        irl = (k - d2) / msl;
        if (ll <= 1) {
          if (rl >= -1E-4 && rl <= 0) rl = 0;
          if (rl >= 0 || ll == 1) {
            if (nkr < 1) {
              px2 += (ax2 - px2) * .5 * (1 - nkr);
              py2 += (ay2 - py2) * .5 * (1 - nkr);
            }
            m = .5 + nkr - d2 / msl;
            while (irl >= 0 && irl < m) {
              k = irl / m;
              ix1 = ax1 + (px2 - ax1) * k;
              iy1 = ay1 + (py2 - ay1) * k;
              ix2 = px2 + (ax2 - px2) * k;
              iy2 = py2 + (ay2 - py2) * k;
              rx = ix1 + (ix2 - ix1) * k;
              ry = iy1 + (iy2 - iy1) * k;
              gpt_i = arp(o, q, rx, ry);
              q++;
              d = sqrtf(powf(o->gptz[gpt_i].xx - o->gptz[lgpt_i].xx, 2) +
                        powf(o->gptz[gpt_i].yy - o->gptz[lgpt_i].yy, 2));
              o->gptz[gpt_i].d = d;
              lgpt_i = gpt_i;
              wk++;
              if (ll == 1) {
                ll = 2;
                break;
              }
              rl -= rmct;
              if (rl <= 0) {
                ll = 1;
                irl += rmct + rl;
                rl = 0;
              } else
                irl += rmct;
            }
            irl -= m;
          }
          if (rl >= -1E-4 && rl <= 0) rl = 0;
        }
        int lj = tdarray_length(o->pts);
        pts_len = tdarray_length(o->pts);
        bool wsirl = false;
        if (ll <= 1) {
          wsirl = false;
          if (rl >= 0 || ll == 1) {
            float rmr = 0;
            po_i = lj - 1;
            for (j = pts_len - 1; j >= 2; j--) {
              lj = j;
              lpo_i = po_i;
              po3_i = (int)(j - 2);
              po2_i = (int)(j - 1);
              po_i = (int)j;
              px = o->pts[po_i].xx + o->pts[po_i].fx;
              py = o->pts[po_i].yy + o->pts[po_i].fy;
              px2 = o->pts[po2_i].xx + o->pts[po2_i].fx;
              py2 = o->pts[po2_i].yy + o->pts[po2_i].fy;
              px3 = o->pts[po3_i].xx + o->pts[po3_i].fx;
              py3 = o->pts[po3_i].yy + o->pts[po3_i].fy;
              ax1 = px + (px2 - px) * .5;
              ay1 = py + (py2 - py) * .5;
              ax2 = px2 + (px3 - px2) * .5;
              ay2 = py2 + (py3 - py2) * .5;
              m = o->pts[po_i].ltn + o->pts[po_i].fltn;
              wwk = omct * 2 + 2;
              if (o->pts[po_i].smu != o->pts[lpo_i].smu ||
                  o->pts[po_i].fsmu != o->pts[lpo_i].fsmu) {
                irl *= (o->pts[lpo_i].smu + o->pts[lpo_i].fsmu) /
                       (o->pts[po_i].smu + o->pts[po_i].fsmu);
                mct = omct * (o->pts[po_i].smu + o->pts[po_i].fsmu);
                rmct = 1 / mct;
                sep = msl / mct;
              }
              rl -= rmr * rmct;
              while (irl < m) {
                k = irl / m;
                ix1 = ax1 + (px2 - ax1) * k;
                iy1 = ay1 + (py2 - ay1) * k;
                ix2 = px2 + (ax2 - px2) * k;
                iy2 = py2 + (ay2 - py2) * k;
                rx = ix1 + (ix2 - ix1) * k;
                ry = iy1 + (iy2 - iy1) * k;
                gpt_i = arp(o, q, rx, ry);
                q++;
                if (wk <= wwk) {
                  d = sqrtf(powf(o->gptz[gpt_i].xx - o->gptz[lgpt_i].xx, 2) +
                            powf(o->gptz[gpt_i].yy - o->gptz[lgpt_i].yy, 2));
                  o->gptz[gpt_i].d = d;
                  lgpt_i = gpt_i;
                  wk++;
                }
                if (ll == 1) {
                  ll = 2;
                  j = -9999;
                  break;
                }
                rl -= rmct;
                if (rl <= 0) {
                  ll = 1;
                  irl += rmct + rl;
                } else
                  irl += rmct;
              }
              irl -= m;
              rmr = irl / rmct;
              rl += irl;
              wsirl = true;
            }
          }
          if (wsirl) rl -= irl;
        }
        if (ll <= 1) {
          if (rl >= -1E-4 && rl <= 0) rl = 0;
          if (rl >= 0 || ll == 1) {
            po_i = (int)(lj - 1);
            po2_i = (int)(lj - 2);
            if (po_i >= 0) {
              px = o->pts[po_i].xx + o->pts[po_i].fx;
              py = o->pts[po_i].yy + o->pts[po_i].fy;
            }
            px2 = o->pts[po2_i].xx + o->pts[po2_i].fx;
            py2 = o->pts[po2_i].yy + o->pts[po2_i].fy;
            while (rl >= 0 || ll == 1) {
              rx = px2 - (px - px2) * (irl - .5);
              ry = py2 - (py - py2) * (irl - .5);
              gpt_i = arp(o, q, rx, ry);
              q++;
              if (wk <= wwk) {
                d = sqrtf(powf(o->gptz[gpt_i].xx - o->gptz[lgpt_i].xx, 2) +
                          powf(o->gptz[gpt_i].yy - o->gptz[lgpt_i].yy, 2));
                o->gptz[gpt_i].d = d;
                lgpt_i = gpt_i;
                wk++;
              }
              if (ll == 1) {
                ll = 2;
                j = -9999;
                break;
              }
              rl -= rmct;
              if (rl <= 0) {
                ll = 1;
                irl += rmct + rl;
              } else
                irl += rmct;
              if (rl >= -1E-4 && rl <= 0) rl = 0;
            }
          }
        }
        k = wk - 1;
        int gptz_len = tdarray_length(o->gptz);
        if (k >= gptz_len) k = gptz_len;

        if (k >= 3) {
          d3 = 0;
          for (j = 0; j < k - 1; j++) {
            gpt_i = j;
            d3 += o->gptz[gpt_i].d;
          }
          lgpt_i = 0;
          lgpt2_i = 0;
          m = d3 / (k - 2);
          j = 1;
          j2 = 1;
          float v = m;
          for (j = 0; j < k; j++) {
            o->gptz[(int)j].ox = o->gptz[(int)j].xx;
            o->gptz[(int)j].oy = o->gptz[(int)j].yy;
          }
          for (j = 1; j < k; j++) {
            gpt_i = j;
            while (true) {
              gpt2_i = j2;
              if (v < o->gptz[gpt2_i].d) {
                o->gptz[gpt_i].xx = o->gptz[lgpt2_i].ox +
                                    (o->gptz[gpt2_i].ox - o->gptz[lgpt2_i].ox) *
                                        v / o->gptz[gpt2_i].d;
                o->gptz[gpt_i].yy = o->gptz[lgpt2_i].oy +
                                    (o->gptz[gpt2_i].oy - o->gptz[lgpt2_i].oy) *
                                        v / o->gptz[gpt2_i].d;
                o->gptz[gpt_i].xx +=
                    (o->gptz[gpt_i].ox - o->gptz[gpt_i].xx) * powf(j / k, 2);
                o->gptz[gpt_i].yy +=
                    (o->gptz[gpt_i].oy - o->gptz[gpt_i].yy) * powf(j / k, 2);
                v += m;
                break;
              } else {
                v -= o->gptz[gpt2_i].d;
                lgpt2_i = gpt2_i;
                j2++;
                if (j2 >= k) {
                  j = k + 1;
                  break;
                }
              }
            }
            lgpt_i = gpt_i;
          }
        }

        float lpx = 0, lpy = 0;
        for (j = 0; j < q; j++) {
          px = o->gptz[(int)j].xx;
          py = o->gptz[(int)j].yy;
          gdata->data.pbx[bp] = px;
          gdata->data.pby[bp] = py;
          gdata->data.pba[bp] = 0;

          /* pbu is scratch memory shared by every snake. Always overwrite it:
             otherwise an off-screen point inherits the previous snake's 2,
             fills the fixed sprite batch and makes body pieces flicker out. */
          gdata->data.pbu[bp] = point_in_snake_view(gdata, px, py) ? 2 : 0;

          if (bp >= 1) {
            tx = px - lpx;
            ty = py - lpy;
            gdata->data.pba[bp] = atan2f(ty, tx);
          }
          lpx = px;
          lpy = py;
          bp++;
        }

        if (q >= 2) {
          gdata->data.pba[0] = gdata->data.pba[1];
          o->wehang = gdata->data.pba[1] + PI;
        } else
          o->wehang = o->ang;

        float shsz = gdata->data.gsc * lsz * 1.5f;
        a *= a;

        const bool rope_mode =
            WYRM_EXPERIMENTAL_ROPE_MODE &&
            env->usr->mobile_hotkeys.rope_mode && o->id == gdata->data.snake_id;
        if (rope_mode) draw_rope_backbone(env, o, bp, mww2, mhh2, a);

        /* Wyrm skins (OM, 2026-10-13, wyrm_skins.c): a creature or strip look
           takes the place of the beads, eyes, boost glow, tag and accessory.
           Yours from your setting, everyone else's from their corner. */
        /* Assist mode shows no Wyrm skin (OM, 2026-10-25): the snake wears
           its ordinary beads there, as its skin block carries them. */
        const int wyrm_skin =
            rope_mode || usrs->hotkeys[HOTKEY_ASSIST].active ? 0
            : o->id == gdata->data.snake_id ? user_settings_wyrm_skin(usrs)
                                            : o->wyrm_skin;
        if (wyrm_skin) {
          float skin_r, skin_g, skin_b;
          snake_strip_colour(env, o, &skin_r, &skin_g, &skin_b);
          wyrm_skin_draw(wyrm_skin, gdata->data.pbx, gdata->data.pby,
                         gdata->data.pbu, bp, gdata->data.view_xx,
                         gdata->data.view_yy, gdata->data.gsc, mww2, mhh2, lsz,
                         fang, o->wang, &o->wyrm_skin_look, o->tsp > o->fsp,
                         o->alive_amt * (1 - o->dead_amt), skin_r, skin_g,
                         skin_b);
        } else if (!rope_mode) {
          bool show_snake_shadows = mode->show_shadows;
#if defined(WYRM_SDL_JNI)
          // Dense private servers can put hundreds of tiny remote snakes in the
          // viewport. Their four atlas-based desktop shadow stamps overlap into
          // opaque black capsules on mobile GPUs. Keep the useful local-player
          // depth cue while avoiding both the artifact and the extra overdraw.
          show_snake_shadows =
              show_snake_shadows && o->id == gdata->data.snake_id;
#endif

          /* Modes › Snake shadow (OM, 2026-10-09), normal and assist apart.
             Not under a skinless strip, which is meant to be see-through. */
          const bool air_shadow =
              usrs->ext.snake_shadow[usrs->hotkeys[HOTKEY_ASSIST].active ? 1 : 0] &&
              mode->render_mode != 3;
          const float air_half =
              gdata->data.gsc * lsz * APPLE_AIR_SHADOW_SCALE;
          float air_sx = 31337357, air_sy = 31337357;

          float om = 0;
          float mr = 0;

          /* Skin texture (OM, 2026-10-25): this snake's beads start here. */
          int skin_tex_start = usr->r->bpr->num_instances;
          if (mode->render_mode == 0) {
            float shadow_strength = 0.25f;

            /* Wyrm: AIR draws `ksmc_t` beneath every Build-a-Slither
               bead — the head's first nine fading out, then the tail's last
               four; the rest interleave with the body below, four points
               behind, exactly as AIR's redraw does. */
            const float apple_air_half =
                gdata->data.gsc * lsz * APPLE_AIR_SHADOW_SCALE;
            bool apple_air_any = false;
            for (int s = 0; WYRM_AIR_BEAD_SHADOW && o->cusk && s < o->cusk_len && !apple_air_any; ++s)
              apple_air_any = apple_air_kind_at(env, o, s) >= 0;
            float apple_air_sx = 31337357, apple_air_sy = 31337357;
            if (apple_air_any) {
              for (int p = bp - 1 < 8 ? bp - 1 : 8; p >= 0; p--)
                if (gdata->data.pbu[p] == 2 && apple_air_kind_at(env, o, p) >= 0)
                  apple_air_shadow(env, p, apple_air_half, a * (1 - p / 9.0f),
                                   mww2, mhh2);
              for (int n = 1; n <= 4; ++n) {
                int p = bp - n;
                if (p < 0 || gdata->data.pbu[p] != 2 ||
                    apple_air_kind_at(env, o, p) < 0)
                  continue;
                float spacing =
                    apple_air_spacing(env, p, &apple_air_sx, &apple_air_sy);
                if (n == 1) spacing = 1;
                apple_air_shadow(env, p, apple_air_half,
                                 spacing * a * (p < 9 ? p / 9.0f : 1), mww2,
                                 mhh2);
              }
            }

            if (air_shadow)
              air_snake_shadow_ends(env, bp, air_half, a, mww2, mhh2, &air_sx,
                                    &air_sy);
            else if (show_snake_shadows) {
              // draw last 4 body parts' shadow:
              int start = bp >= 4 ? bp - 4 : 0;
              for (j = start; j < bp; j++) {
                if (gdata->data.pbu[(int)j] >= 1 &&
                    !(apple_air_any && apple_air_kind_at(env, o, (int)j) >= 0)) {
                  px = gdata->data.pbx[(int)j];
                  py = gdata->data.pby[(int)j];

                  float fix =
                      ((px - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                  float fiy =
                      ((py - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                  bp_renderer_push(
                      usr->r->bpr,
                      &(bp_instance){{fix - shsz, fiy - shsz, 2 * shsz,
                                      gdata->data.pba[(int)j]},
                                     gdata->SHADOW_UV,
                                     {0, 0, 0, shadow_strength * a}});
                }
              }
            }

            // draw body parts:
            if (o->cusk) {
              for (j = bp - 1; j >= 0; j--)
                if (gdata->data.pbu[(int)j] >= 1) {
                  px = gdata->data.pbx[(int)j];
                  py = gdata->data.pby[(int)j];

                  if (j >= 4 && apple_air_any &&
                      apple_air_kind_at(env, o, (int)j - 4) >= 0) {
                    int p = (int)j - 4;
                    if (gdata->data.pbu[p] == 2) {
                      float spacing = apple_air_spacing(env, p, &apple_air_sx,
                                                        &apple_air_sy);
                      apple_air_shadow(env, p, apple_air_half,
                                       spacing * a * (p < 9 ? p / 9.0f : 1),
                                       mww2, mhh2);
                    }
                  } else if (air_shadow) {
                    air_snake_shadow_behind(env, (int)j, air_half * air_looks_swell(o, (int)j - 4), a, mww2, mhh2,
                                            &air_sx, &air_sy);
                  } else if (j >= 4 && show_snake_shadows) {
                    k = j - 4;
                    if (gdata->data.pbu[(int)k] == 2) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)k];
                      ty = gdata->data.pby[(int)k];
                      float fix =
                          ((tx - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                      float fiy =
                          ((ty - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                      bp_renderer_push(
                          usr->r->bpr,
                          /* Main.as swells the head's shadows with its beads. */
                          &(bp_instance){{fix - shsz * air_looks_swell(o, (int)k),
                                          fiy - shsz * air_looks_swell(o, (int)k),
                                          2 * shsz * air_looks_swell(o, (int)k),
                                          gdata->data.pba[(int)j]},
                                         gdata->SHADOW_UV,
                                         {0, 0, 0, shadow_strength * a}});
                    }
                  }

                  float fix =
                      ((px - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                  float fiy =
                      ((py - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                  int slot = studio_slot(env, o, (int)j, o->cusk_len);
                  int cg_id = o->cusk_data[(int)j % o->cusk_len];
                  uint32_t built = built_skin_rgba(env, o, slot);

                  if (studio_own(env, o))
                    bp_renderer_push(
                        usr->r->bpr,
                        &(bp_instance){
                            {fix - (gdata->data.gsc * lsz),
                             fiy - (gdata->data.gsc * lsz),
                             gdata->data.gsc * 2 * lsz, gdata->data.pba[(int)j]},
                            studio_cell_uv(studio_cell_at(slot)),
                            {1, 1, 1, a}});
                  else
                  bp_renderer_push(
                      usr->r->bpr,
                      &(bp_instance){
                          {fix - (gdata->data.gsc * lsz),
                           fiy - (gdata->data.gsc * lsz),
                           gdata->data.gsc * 2 * lsz, gdata->data.pba[(int)j]},
                          /* A mixed colour is not in the atlas, so a built
                           * point drops to the blank bead and is tinted. */
                          wyrm_bead_kind(built) >= 0
                              ? wyrm_bead_uv(wyrm_bead_kind(built))
                          : apple_air_kind(built) >= 0
                              ? apple_air_bead_uv(apple_air_kind(built))
                          : built ? gdata->cg_uvs[BLANK_UV]
                                  : gdata->cg_uvs[cg_id],
                          wyrm_bead_kind(built) >= 0
                              ? wyrm_bead_color(built, wyrm_bead_kind(built), a)
                          : apple_air_kind(built) >= 0
                              ? apple_air_tint(built, a)
                          : built ? built_skin_color(built, a)
                                  : (vec4s){{1, 1, 1, a}}});
                  if (!studio_own(env, o) && apple_air_kind(built) >= 2)
                    apple_air_style_finish(usr->r->bpr, apple_air_kind(built), a);
                }
            } else {
              for (j = bp - 1; j >= 0; j--)
                if (gdata->data.pbu[(int)j] >= 1) {
                  px = gdata->data.pbx[(int)j];
                  py = gdata->data.pby[(int)j];

                  if (air_shadow) {
                    air_snake_shadow_behind(env, (int)j, air_half * air_looks_swell(o, (int)j - 4), a, mww2, mhh2,
                                            &air_sx, &air_sy);
                  } else if (j >= 4 && show_snake_shadows) {
                    k = j - 4;
                    if (gdata->data.pbu[(int)k] == 2) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)k];
                      ty = gdata->data.pby[(int)k];
                      float fix =
                          ((tx - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                      float fiy =
                          ((ty - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                      bp_renderer_push(
                          usr->r->bpr,
                          /* Main.as swells the head's shadows with its beads. */
                          &(bp_instance){{fix - shsz * air_looks_swell(o, (int)k),
                                          fiy - shsz * air_looks_swell(o, (int)k),
                                          2 * shsz * air_looks_swell(o, (int)k),
                                          gdata->data.pba[(int)j]},
                                         gdata->SHADOW_UV,
                                         {0, 0, 0, shadow_strength * a}});
                    }
                  }

                  float fix =
                      ((px - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                  float fiy =
                      ((py - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                  int default_skin_len = gdata->default_skins[o->cv][0];
                  int cg_id =
                      gdata->default_skins[o->cv]
                                          [1 + ((int)j % default_skin_len)];
                  float se = gdata->worm_effect[(int)j % WORM_EFFECT_LEN];
                  /* Main.as `swell` (skin 27): the head's beads grow. */
                  float slsz = lsz * air_looks_swell(o, (int)j);

                  bp_renderer_push(
                      usr->r->bpr,
                      &(bp_instance){
                          {fix - (gdata->data.gsc * slsz),
                           fiy - (gdata->data.gsc * slsz),
                           gdata->data.gsc * 2 * slsz, gdata->data.pba[(int)j]},
                          gdata->cg_uvs[cg_id],
                          {se, se, se, a}});
                  /* Main.as fdhc / fdtc / fdl (skin 60): bead 37 fades out over the
                     first 30 beads from the head and bead 38 over the last 30,
                     each drawn on top a touch larger (1 + 0.075 fdam). */
                  if (air_looks_on(o) && o->cv == 60) {
                    const float fdl = 30.0f;
                    float fdam = 0.0f;
                    int fdc = -1;
                    if (j < fdl) { fdam = 1.0f - j / fdl; fdc = 37; }
                    else if (j > bp - fdl) { fdam = 1.0f - (bp - j) / fdl; fdc = 38; }
                    if (fdc >= 0 && fdam > 0.0f) {
                      float fsz = (1.0f + 0.075f * fdam) * slsz;
                      bp_renderer_push(
                          usr->r->bpr,
                          &(bp_instance){
                              {fix - (gdata->data.gsc * fsz),
                               fiy - (gdata->data.gsc * fsz),
                               gdata->data.gsc * 2 * fsz, gdata->data.pba[(int)j]},
                              gdata->cg_uvs[fdc],
                              {se, se, se, a * fdam}});
                    }
                  }
                }
            }
            skin_texture_apply(env, o, skin_tex_start);
          } else if (mode->render_mode == 1) {
            if (air_shadow)
              air_snake_shadow_ends(env, bp, air_half, a, mww2, mhh2, &air_sx,
                                    &air_sy);
            else if (show_snake_shadows) {
              // draw last 4 body parts' shadow:
              int start = bp >= 4 ? bp - 4 : 0;
              for (j = start; j < bp; j++) {
                if (gdata->data.pbu[(int)j] >= 1) {
                  px = gdata->data.pbx[(int)j];
                  py = gdata->data.pby[(int)j];

                  float fix =
                      ((px - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                  float fiy =
                      ((py - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                  bp_renderer_push(
                      usr->r->bpr,
                      &(bp_instance){{fix - (lsz * gdata->data.gsc + 1),
                                      fiy - (lsz * gdata->data.gsc + 1),
                                      (lsz * gdata->data.gsc + 1) * 2,
                                      gdata->data.pba[(int)j]},
                                     gdata->cg_uvs[BLANK_UV],
                                     {0, 0, 0, a * a}});
                }
              }
            }

            // draw body parts:
            if (o->cusk) {
              for (j = bp - 1; j >= 0; j--)
                if (gdata->data.pbu[(int)j] >= 1) {
                  px = gdata->data.pbx[(int)j];
                  py = gdata->data.pby[(int)j];

                  if (air_shadow) {
                    air_snake_shadow_behind(env, (int)j, air_half * air_looks_swell(o, (int)j - 4), a, mww2, mhh2,
                                            &air_sx, &air_sy);
                  } else if (j >= 4 && show_snake_shadows) {
                    k = j - 4;
                    if (gdata->data.pbu[(int)k] == 2) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)k];
                      ty = gdata->data.pby[(int)k];
                      float fix =
                          ((tx - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                      float fiy =
                          ((ty - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                      bp_renderer_push(
                          usr->r->bpr,
                          &(bp_instance){{fix - (lsz * gdata->data.gsc + 1),
                                          fiy - (lsz * gdata->data.gsc + 1),
                                          (lsz * gdata->data.gsc + 1) * 2,
                                          gdata->data.pba[(int)j]},
                                         gdata->cg_uvs[BLANK_UV],
                                         {0, 0, 0, a * a}});
                    }
                  }

                  float fix =
                      ((px - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                  float fiy =
                      ((py - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                  int slot = studio_slot(env, o, (int)j, o->cusk_len);
                  int cg_id = o->cusk_data[(int)j % o->cusk_len];
                  vec3s* cg_col = gdata->cg_colors + cg_id;
                  uint32_t built = built_skin_rgba(env, o, slot);

                  bp_renderer_push(
                      usr->r->bpr,
                      &(bp_instance){
                          {fix - (gdata->data.gsc * lsz),
                           fiy - (gdata->data.gsc * lsz),
                           gdata->data.gsc * 2 * lsz, gdata->data.pba[(int)j]},
                          gdata->cg_uvs[BLANK_UV],
                          built ? built_skin_color(built, a)
                                : (vec4s){{cg_col->r, cg_col->g, cg_col->b, a}}});
                }
            } else {
              for (j = bp - 1; j >= 0; j--)
                if (gdata->data.pbu[(int)j] >= 1) {
                  px = gdata->data.pbx[(int)j];
                  py = gdata->data.pby[(int)j];

                  if (air_shadow) {
                    air_snake_shadow_behind(env, (int)j, air_half * air_looks_swell(o, (int)j - 4), a, mww2, mhh2,
                                            &air_sx, &air_sy);
                  } else if (j >= 4 && show_snake_shadows) {
                    k = j - 4;
                    if (gdata->data.pbu[(int)k] == 2) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)k];
                      ty = gdata->data.pby[(int)k];
                      float fix =
                          ((tx - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                      float fiy =
                          ((ty - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                      bp_renderer_push(
                          usr->r->bpr,
                          &(bp_instance){{fix - (lsz * gdata->data.gsc + 1),
                                          fiy - (lsz * gdata->data.gsc + 1),
                                          (lsz * gdata->data.gsc + 1) * 2,
                                          gdata->data.pba[(int)j]},
                                         gdata->cg_uvs[BLANK_UV],
                                         {0, 0, 0, a * a}});
                    }
                  }

                  float fix =
                      ((px - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                  float fiy =
                      ((py - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                  int default_skin_len = gdata->default_skins[o->cv][0];
                  int cg_id =
                      gdata->default_skins[o->cv]
                                          [1 + ((int)j % default_skin_len)];
                  vec3s* cg_col = gdata->cg_colors + cg_id;

                  /* Main.as `swell` in Solid and Flat too (skin 27). */
                  float wlsz = lsz * air_looks_swell(o, (int)j);
                  bp_renderer_push(
                      usr->r->bpr,
                      &(bp_instance){
                          {fix - (gdata->data.gsc * wlsz),
                           fiy - (gdata->data.gsc * wlsz),
                           gdata->data.gsc * 2 * wlsz, gdata->data.pba[(int)j]},
                          gdata->cg_uvs[BLANK_UV],
                          {cg_col->r, cg_col->g, cg_col->b, a}});
                }
            }
          } else if (mode->render_mode == 2) {
            if (air_shadow)
              air_snake_shadow_ends(env, bp, air_half, a, mww2, mhh2, &air_sx,
                                    &air_sy);
            else if (show_snake_shadows) {
              // draw last 4 body parts' shadow:
              int start = bp >= 4 ? bp - 4 : 0;
              for (j = start; j < bp; j++) {
                if (gdata->data.pbu[(int)j] >= 1) {
                  px = gdata->data.pbx[(int)j];
                  py = gdata->data.pby[(int)j];

                  float fix =
                      ((px - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                  float fiy =
                      ((py - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                  bp_renderer_push(
                      usr->r->bpr,
                      &(bp_instance){{fix - (lsz * gdata->data.gsc + 1),
                                      fiy - (lsz * gdata->data.gsc + 1),
                                      (lsz * gdata->data.gsc + 1) * 2,
                                      gdata->data.pba[(int)j]},
                                     gdata->cg_uvs[BLANK_UV],
                                     {0, 0, 0, a * a}});
                }
              }
            }

            // draw body parts:
            if (o->cusk) {
              for (j = bp - 1; j >= 0; j--)
                if (gdata->data.pbu[(int)j] >= 1) {
                  px = gdata->data.pbx[(int)j];
                  py = gdata->data.pby[(int)j];

                  if (air_shadow) {
                    air_snake_shadow_behind(env, (int)j, air_half * air_looks_swell(o, (int)j - 4), a, mww2, mhh2,
                                            &air_sx, &air_sy);
                  } else if (j >= 4 && show_snake_shadows) {
                    k = j - 4;
                    if (gdata->data.pbu[(int)k] == 2) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)k];
                      ty = gdata->data.pby[(int)k];
                      float fix =
                          ((tx - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                      float fiy =
                          ((ty - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                      bp_renderer_push(
                          usr->r->bpr,
                          &(bp_instance){{fix - (lsz * gdata->data.gsc + 1),
                                          fiy - (lsz * gdata->data.gsc + 1),
                                          (lsz * gdata->data.gsc + 1) * 2,
                                          gdata->data.pba[(int)j]},
                                         gdata->cg_uvs[BLANK_UV],
                                         {0, 0, 0, a * a}});
                    }
                  }

                  float fix =
                      ((px - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                  float fiy =
                      ((py - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                  int cg_id = o->cusk_data[0];
                  vec3s* cg_col = gdata->cg_colors + cg_id;
                  uint32_t built = built_skin_rgba(env, o, 0);

                  bp_renderer_push(
                      usr->r->bpr,
                      &(bp_instance){
                          {fix - (gdata->data.gsc * lsz),
                           fiy - (gdata->data.gsc * lsz),
                           gdata->data.gsc * 2 * lsz, gdata->data.pba[(int)j]},
                          gdata->cg_uvs[BLANK_UV],
                          built
                              ? built_skin_color(built, a * a)
                              : (vec4s){{cg_col->r, cg_col->g, cg_col->b, a * a}}});
                }
            } else {
              for (j = bp - 1; j >= 0; j--)
                if (gdata->data.pbu[(int)j] >= 1) {
                  px = gdata->data.pbx[(int)j];
                  py = gdata->data.pby[(int)j];

                  if (air_shadow) {
                    air_snake_shadow_behind(env, (int)j, air_half * air_looks_swell(o, (int)j - 4), a, mww2, mhh2,
                                            &air_sx, &air_sy);
                  } else if (j >= 4 && show_snake_shadows) {
                    k = j - 4;
                    if (gdata->data.pbu[(int)k] == 2) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)k];
                      ty = gdata->data.pby[(int)k];
                      float fix =
                          ((tx - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                      float fiy =
                          ((ty - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                      bp_renderer_push(
                          usr->r->bpr,
                          &(bp_instance){{fix - (lsz * gdata->data.gsc + 1),
                                          fiy - (lsz * gdata->data.gsc + 1),
                                          (lsz * gdata->data.gsc + 1) * 2,
                                          gdata->data.pba[(int)j]},
                                         gdata->cg_uvs[BLANK_UV],
                                         {0, 0, 0, a}});
                    }
                  }

                  float fix =
                      ((px - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                  float fiy =
                      ((py - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                  int default_skin_len = gdata->default_skins[o->cv][0];
                  int cg_id = gdata->default_skins[o->cv][1];
                  vec3s* cg_col = gdata->cg_colors + cg_id;

                  /* Main.as `swell` in Solid and Flat too (skin 27). */
                  float wlsz = lsz * air_looks_swell(o, (int)j);
                  bp_renderer_push(
                      usr->r->bpr,
                      &(bp_instance){
                          {fix - (gdata->data.gsc * wlsz),
                           fiy - (gdata->data.gsc * wlsz),
                           gdata->data.gsc * 2 * wlsz, gdata->data.pba[(int)j]},
                          gdata->cg_uvs[BLANK_UV],
                          {cg_col->r, cg_col->g, cg_col->b, a}});
                }
            }
          } else if (mode->render_mode == 3) {
            /* Skinless: the plain body's width and length, skin see-through. */
            draw_skinless_strip(env, o, bp, mww2, mhh2, lsz, a);
          }
          /* Hidden bead parts and far beads (OM, 2026-10-30). */
          if (mode->render_mode != 3)
            bead_cull_apply(env, o, skin_tex_start, gdata->data.gsc * 2 * lsz);

          // debugging
          // for (int bpi = pts_len - 1; bpi >= 0; bpi--) {
          //   body_part* po = o->pts + bpi;
          //   float fix = ((po->xx - gdata->data.view_xx) * gdata->data.gsc) +
          //   mww2; float fiy = ((po->yy - gdata->data.view_yy) *
          //   gdata->data.gsc) + mhh2;

          //   ImDrawList_AddCircleFilled(igGetWindowDrawList(), (ImVec2){fix,
          //   fiy}, 5, UINT32_MAX, 10);
          // }

          // if (o->id == gdata->data.snake_id) {
          //   ImDrawList_AddLine(igGetWindowDrawList(), (ImVec2){mww2, mhh2},
          //   (ImVec2){mww2 + 120 * cosf(o->ang), mhh2 + 120 * sinf(o->ang)},
          //   igColorConvertFloat4ToU32((ImVec4){1, 0, 0, 1}), 2);
          //   ImDrawList_AddLine(igGetWindowDrawList(), (ImVec2){mww2, mhh2},
          //   (ImVec2){mww2 + 120 * cosf(o->wang), mhh2 + 120 * sinf(o->wang)},
          //   igColorConvertFloat4ToU32((ImVec4){0, 1, 0, 1}), 2);
          //   ImDrawList_AddLine(igGetWindowDrawList(), (ImVec2){mww2, mhh2},
          //   (ImVec2){mww2 + 120 * cosf(o->pts[pts_len - 1].iang), mhh2 + 120
          //   * sinf(o->pts[pts_len - 1].iang)},
          //   igColorConvertFloat4ToU32((ImVec4){0.3f, 0.3f, 1, 1}), 2);
          // }

          if (mode->death_effect && o->dead) {
            float falf = (.15 + .15 * fabsf(sinf(5 * PI * o->dead_amt))) *
                         sinf(PI * o->dead_amt);
            float dsz = gdata->data.gsc * lsz;

            for (j = bp - 1; j >= 0; j--)
              if (gdata->data.pbu[(int)j] == 2) {
                ox = tx;
                oy = ty;
                tx = gdata->data.pbx[(int)j];
                ty = gdata->data.pby[(int)j];
                if (tx > ox)
                  d2 = tx - ox;
                else
                  d2 = ox - tx;
                if (ty > oy)
                  d2 += ty - oy;
                else
                  d2 += oy - ty;
                d2 /= 6;
                if (d2 > 1) d2 = 1;
                px = gdata->data.pbx[(int)j];
                py = gdata->data.pby[(int)j];
                float alpha =
                    d2 * falf * (.6 + .4 * cosf(j / 4 - 15 * o->dead_amt));

                float fix =
                    ((px - gdata->data.view_xx) * gdata->data.gsc) + mww2;
                float fiy =
                    ((py - gdata->data.view_yy) * gdata->data.gsc) + mhh2;

                bp_renderer_push(
                    usr->r->bpr,
                    &(bp_instance){{fix - dsz, fiy - dsz, dsz * 2, 0},
                                   gdata->cg_uvs[BLANK_UV],
                                   {1, 1, 1, alpha * 0.5f}});
              }
          }

          /* Show boost on (OM, 2026-10-18): only mine, or every snake's. */
          if (mode->show_boost && mode->render_mode != 3 &&
              (!usrs->ext.boost_own[mode == usrs->modes ? 0 : 1] ||
               o->id == gdata->data.snake_id)) {
            if (mode->render_mode == 2) {
              if (o->tsp > o->fsp) {
                m = a *
                    fmaxf(0, fminf(1, (o->tsp - o->ssp) / (o->msp - o->ssp)));
                om = m * .37;
                mr = powf(m, .5);
                float glsz =
                    (1 + mode->boost_type + mr) * gdata->data.gsc * lsz;
                float strength = 1 + mode->boost_type * mode->boost_strength;
                int bstk = wyrm_boost_stride(gdata, bp, glsz, strength * .38f);
                strength *= bstk;

                if (o->cusk) {
                  for (j = bp - 1; j >= 0; j--)
                    if (gdata->data.pbu[(int)j] == 2 && ((int)j % bstk) == 0) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)j];
                      ty = gdata->data.pby[(int)j];
                      if (tx > ox)
                        d2 = tx - ox;
                      else
                        d2 = ox - tx;
                      if (ty > oy)
                        d2 += ty - oy;
                      else
                        d2 += oy - ty;
                      d2 /= 6;
                      if (d2 > 1) d2 = 1;

                      float fix =
                          (tx - gdata->data.view_xx) * gdata->data.gsc + mww2;
                      float fiy =
                          (ty - gdata->data.view_yy) * gdata->data.gsc + mhh2;

                      float alpha = strength * d2 * a * mr * .38 *
                                    (.6 + .4 * cosf(j / 4 - 1.15 * o->sfr));
                      int cg_id = o->cusk_data[0];
                      vec3s studio_glow;
                      vec3s* shc = studio_glow_for(env, o, 0, &studio_glow,
                                                   gdata->cg_glow_colors + cg_id);

                      bst_renderer_push(
                          usr->r->bstb,
                          &(bst_instance){{fix - glsz, fiy - glsz, 2 * glsz},
                                          {shc->r, shc->g, shc->b, alpha}});
                    }
                } else {
                  for (j = bp - 1; j >= 0; j--)
                    if (gdata->data.pbu[(int)j] == 2 && ((int)j % bstk) == 0) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)j];
                      ty = gdata->data.pby[(int)j];
                      if (tx > ox)
                        d2 = tx - ox;
                      else
                        d2 = ox - tx;
                      if (ty > oy)
                        d2 += ty - oy;
                      else
                        d2 += oy - ty;
                      d2 /= 6;
                      if (d2 > 1) d2 = 1;

                      float fix =
                          (tx - gdata->data.view_xx) * gdata->data.gsc + mww2;
                      float fiy =
                          (ty - gdata->data.view_yy) * gdata->data.gsc + mhh2;

                      float alpha = strength * d2 * a * mr * .38 *
                                    (.6 + .4 * cosf(j / 4 - 1.15 * o->sfr));

                      int default_skin_len = gdata->default_skins[o->cv][0];
                      int cg_id = gdata->default_skins[o->cv][1];
                      vec3s* shc = gdata->cg_glow_colors + cg_id;

                      bst_renderer_push(
                          usr->r->bstb,
                          &(bst_instance){{fix - glsz, fiy - glsz, 2 * glsz},
                                          {shc->r, shc->g, shc->b, alpha}});
                    }
                }
              }
              // boost effect 2:
              if (mode->boost_type == 0 && o->tsp > o->fsp) {
                float glsz = lsz * 3 * gdata->data.gsc;
                float strength = 0.7f * mode->boost_strength;
                int bstk = wyrm_boost_stride(gdata, bp, glsz, strength * .37f);
                strength *= bstk;

                if (o->cusk) {
                  for (j = bp - 1; j >= 0; j--)
                    if (gdata->data.pbu[(int)j] == 2 && ((int)j % bstk) == 0) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)j];
                      ty = gdata->data.pby[(int)j];
                      if (tx > ox)
                        d2 = tx - ox;
                      else
                        d2 = ox - tx;
                      if (ty > oy)
                        d2 += ty - oy;
                      else
                        d2 += oy - ty;
                      d2 /= 6;
                      if (d2 > 1) d2 = 1;

                      float fix =
                          (tx - gdata->data.view_xx) * gdata->data.gsc + mww2;
                      float fiy =
                          (ty - gdata->data.view_yy) * gdata->data.gsc + mhh2;

                      float alpha = strength * d2 * a * om *
                                    (.5 + .5 * cosf(j / 4 - o->sfr));
                      int cg_id = o->cusk_data[0];
                      vec3s studio_glow;
                      vec3s* shc = studio_glow_for(env, o, 0, &studio_glow,
                                                   gdata->cg_glow_colors + cg_id);

                      bst_renderer_push(
                          usr->r->bsta,
                          &(bst_instance){{fix - glsz, fiy - glsz, 2 * glsz},
                                          {shc->r, shc->g, shc->b, alpha}});
                    }
                } else {
                  for (j = bp - 1; j >= 0; j--)
                    if (gdata->data.pbu[(int)j] == 2 && ((int)j % bstk) == 0) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)j];
                      ty = gdata->data.pby[(int)j];
                      if (tx > ox)
                        d2 = tx - ox;
                      else
                        d2 = ox - tx;
                      if (ty > oy)
                        d2 += ty - oy;
                      else
                        d2 += oy - ty;
                      d2 /= 6;
                      if (d2 > 1) d2 = 1;

                      float fix =
                          (tx - gdata->data.view_xx) * gdata->data.gsc + mww2;
                      float fiy =
                          (ty - gdata->data.view_yy) * gdata->data.gsc + mhh2;

                      float alpha = strength * d2 * a * om *
                                    (.5 + .5 * cosf(j / 4 - o->sfr));

                      int default_skin_len = gdata->default_skins[o->cv][0];
                      int cg_id = gdata->default_skins[o->cv][1];
                      vec3s* shc = gdata->cg_glow_colors + cg_id;

                      bst_renderer_push(
                          usr->r->bsta,
                          &(bst_instance){{fix - glsz, fiy - glsz, 2 * glsz},
                                          {shc->r, shc->g, shc->b, alpha}});
                    }
                }
              }
            } else {
              // boost effect 1:
              if (o->tsp > o->fsp) {
                m = a *
                    fmaxf(0, fminf(1, (o->tsp - o->ssp) / (o->msp - o->ssp)));
                om = m * .37;
                mr = powf(m, .5);
                float glsz =
                    (1 + mode->boost_type + mr) * gdata->data.gsc * lsz;
                float strength = 1 + mode->boost_type * mode->boost_strength;
                int bstk = wyrm_boost_stride(gdata, bp, glsz, strength * .38f);
                strength *= bstk;

                if (o->cusk) {
                  for (j = bp - 1; j >= 0; j--)
                    if (gdata->data.pbu[(int)j] == 2 && ((int)j % bstk) == 0) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)j];
                      ty = gdata->data.pby[(int)j];
                      if (tx > ox)
                        d2 = tx - ox;
                      else
                        d2 = ox - tx;
                      if (ty > oy)
                        d2 += ty - oy;
                      else
                        d2 += oy - ty;
                      d2 /= 6;
                      if (d2 > 1) d2 = 1;

                      float fix =
                          (tx - gdata->data.view_xx) * gdata->data.gsc + mww2;
                      float fiy =
                          (ty - gdata->data.view_yy) * gdata->data.gsc + mhh2;

                      float alpha = strength * d2 * a * mr * .38 *
                                    (.6 + .4 * cosf(j / 4 - 1.15 * o->sfr));
                      int cg_id = o->cusk_data[wyrm_boost_bead((int)j, bstk) % o->cusk_len];
                      vec3s studio_glow;
                      vec3s* shc = studio_glow_for(env, o, wyrm_boost_bead((int)j, bstk), &studio_glow,
                                                   gdata->cg_glow_colors + cg_id);

                      bst_renderer_push(
                          usr->r->bstb,
                          &(bst_instance){{fix - glsz, fiy - glsz, 2 * glsz},
                                          {shc->r, shc->g, shc->b, alpha}});
                    }
                } else {
                  for (j = bp - 1; j >= 0; j--)
                    if (gdata->data.pbu[(int)j] == 2 && ((int)j % bstk) == 0) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)j];
                      ty = gdata->data.pby[(int)j];
                      if (tx > ox)
                        d2 = tx - ox;
                      else
                        d2 = ox - tx;
                      if (ty > oy)
                        d2 += ty - oy;
                      else
                        d2 += oy - ty;
                      d2 /= 6;
                      if (d2 > 1) d2 = 1;

                      float fix =
                          (tx - gdata->data.view_xx) * gdata->data.gsc + mww2;
                      float fiy =
                          (ty - gdata->data.view_yy) * gdata->data.gsc + mhh2;

                      float alpha = strength * d2 * a * mr * .38 *
                                    (.6 + .4 * cosf(j / 4 - 1.15 * o->sfr));

                      int default_skin_len = gdata->default_skins[o->cv][0];
                      int cg_id =
                          gdata->default_skins[o->cv]
                                              [1 + (wyrm_boost_bead((int)j, bstk) % default_skin_len)];
                      vec3s* shc = gdata->cg_glow_colors + cg_id;

                      bst_renderer_push(
                          usr->r->bstb,
                          &(bst_instance){{fix - glsz, fiy - glsz, 2 * glsz},
                                          {shc->r, shc->g, shc->b, alpha}});
                    }
                }
              }
              // boost effect 2:
              if (mode->boost_type == 0 && o->tsp > o->fsp) {
                float glsz = lsz * 3 * gdata->data.gsc;
                float strength = 0.7f * mode->boost_strength;
                int bstk = wyrm_boost_stride(gdata, bp, glsz, strength * .37f);
                strength *= bstk;

                if (o->cusk) {
                  for (j = bp - 1; j >= 0; j--)
                    if (gdata->data.pbu[(int)j] == 2 && ((int)j % bstk) == 0) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)j];
                      ty = gdata->data.pby[(int)j];
                      if (tx > ox)
                        d2 = tx - ox;
                      else
                        d2 = ox - tx;
                      if (ty > oy)
                        d2 += ty - oy;
                      else
                        d2 += oy - ty;
                      d2 /= 6;
                      if (d2 > 1) d2 = 1;

                      float fix =
                          (tx - gdata->data.view_xx) * gdata->data.gsc + mww2;
                      float fiy =
                          (ty - gdata->data.view_yy) * gdata->data.gsc + mhh2;

                      float alpha = strength * d2 * a * om *
                                    (.5 + .5 * cosf(j / 4 - o->sfr));
                      int cg_id = o->cusk_data[wyrm_boost_bead((int)j, bstk) % o->cusk_len];
                      vec3s studio_glow;
                      vec3s* shc = studio_glow_for(env, o, wyrm_boost_bead((int)j, bstk), &studio_glow,
                                                   gdata->cg_glow_colors + cg_id);

                      bst_renderer_push(
                          usr->r->bsta,
                          &(bst_instance){{fix - glsz, fiy - glsz, 2 * glsz},
                                          {shc->r, shc->g, shc->b, alpha}});
                    }
                } else {
                  for (j = bp - 1; j >= 0; j--)
                    if (gdata->data.pbu[(int)j] == 2 && ((int)j % bstk) == 0) {
                      ox = tx;
                      oy = ty;
                      tx = gdata->data.pbx[(int)j];
                      ty = gdata->data.pby[(int)j];
                      if (tx > ox)
                        d2 = tx - ox;
                      else
                        d2 = ox - tx;
                      if (ty > oy)
                        d2 += ty - oy;
                      else
                        d2 += oy - ty;
                      d2 /= 6;
                      if (d2 > 1) d2 = 1;

                      float fix =
                          (tx - gdata->data.view_xx) * gdata->data.gsc + mww2;
                      float fiy =
                          (ty - gdata->data.view_yy) * gdata->data.gsc + mhh2;

                      float alpha = strength * d2 * a * om *
                                    (.5 + .5 * cosf(j / 4 - o->sfr));

                      int default_skin_len = gdata->default_skins[o->cv][0];
                      int cg_id =
                          gdata->default_skins[o->cv]
                                              [1 + (wyrm_boost_bead((int)j, bstk) % default_skin_len)];
                      vec3s* shc = gdata->cg_glow_colors + cg_id;

                      bst_renderer_push(
                          usr->r->bsta,
                          &(bst_instance){{fix - glsz, fiy - glsz, 2 * glsz},
                                          {shc->r, shc->g, shc->b, alpha}});
                    }
                }
              }
            }
          }

          // draw eyes:
          float ed = 6 * ssc;   // o->ed
          float esp = 6 * ssc;  // o->esp
          float er = 6;         // o->er
          air_looks_eye_params(o, &ed, &esp, &er);
          default_skin_data* dfs = gdata->dfs + ((1 - o->cusk) * (1 + o->cv));
          float pr = dfs->pr;
          float iris_r = er * ssc * gdata->data.gsc;
          float pupil_r = pr * ssc * gdata->data.gsc;

          float ex = cosf(fang) * ed + cosf(fang - PI / 2) * (esp + .5);
          float ey = sinf(fang) * ed + sinf(fang - PI / 2) * (esp + .5);

          float ea = mode->death_effect
                         ? o->alive_amt * o->alive_amt * sqrtf(1 - o->dead_amt)
                         : a;

          /* Spine sits on the ImGui list, which is drawn after the sprites,
             so eyes for a spine or a skinless strip are circles on that list
             and end up on top (OM, 2026-10-05). */
          {
            int assist_on = usrs->hotkeys[HOTKEY_ASSIST].active ? 1 : 0;
            /* Mine only, or everyone (OM, 2026-10-14). */
            int spine_on = usrs->ext.spine[assist_on] &&
                                   (!usrs->ext.spine_mine[assist_on] ||
                                    o->id == gdata->data.snake_id)
                               ? 1
                               : 0;
            int spine_mesh = 0;
            if (spine_on)
              spine_mesh = draw_snake_spine(env, o, bp, mww2, mhh2, a,
                                            2.0f * lsz * gdata->data.gsc,
                                            usrs->ext.spine_width[assist_on]);
            /* A thin spine starts a point behind the head and leaves the
               sprite eyes uncovered (OM, 2026-10-12). */
            if (mode->render_mode == 3 || spine_mesh)
              draw_snake_imgui_eyes(env, o, fang, hx, hy, ssc, ea, mww2, mhh2);
            else if (air_looks_hide_eyes(o))
              ;
            else if (studio_own(env, o))
              studio_draw_eyes(env, o, fang, hx, hy, ssc, ea, mww2, mhh2);
            else {
          /* Main.as `eo`: the slug's eyes sit on a dark rim (OM, 2026-11-07). */
          {
            float rim = air_looks_eye_rim(o);
            for (int side = -1; rim > 0 && side <= 1; side += 2) {
              float rx2 = cosf(fang) * ed + cosf(fang + side * (PI / 2)) * (esp + .5);
              float ry2 = sinf(fang) * ed + sinf(fang + side * (PI / 2)) * (esp + .5);
              float rr = iris_r + rim * 0.5f * gdata->data.gsc;
              bp_renderer_push(
                  usr->r->bpr,
                  &(bp_instance){
                      {(mww2 + (rx2 + hx - gdata->data.view_xx) * gdata->data.gsc) - rr,
                       (mhh2 + (ry2 + hy - gdata->data.view_yy) * gdata->data.gsc) - rr,
                       rr * 2, 0},
                      gdata->cg_uvs[BLANK_UV],
                      {0, 0, 0, ea}});
            }
          }
          bp_renderer_push(
              usr->r->bpr,
              &(bp_instance){
                  {(mww2 + (ex + hx - gdata->data.view_xx) * gdata->data.gsc) -
                       iris_r,
                   (mhh2 + (ey + hy - gdata->data.view_yy) * gdata->data.gsc) -
                       iris_r,
                   iris_r * 2, 0},
                  gdata->cg_uvs[BLANK_UV],
                  {dfs->ec.r, dfs->ec.g, dfs->ec.b, ea}});

          ex = cosf(fang) * ed + cosf(fang + PI / 2) * (esp + .5);
          ey = sinf(fang) * ed + sinf(fang + PI / 2) * (esp + .5);

          bp_renderer_push(
              usr->r->bpr,
              &(bp_instance){
                  {(mww2 + (ex + hx - gdata->data.view_xx) * gdata->data.gsc) -
                       iris_r,
                   (mhh2 + (ey + hy - gdata->data.view_yy) * gdata->data.gsc) -
                       iris_r,
                   iris_r * 2, 0},
                  gdata->cg_uvs[BLANK_UV],
                  {dfs->ec.r, dfs->ec.g, dfs->ec.b, ea}});

          ex =
              cosf(fang) * (ed + .5) + o->rex * ssc + cosf(fang - PI / 2) * esp;
          ey =
              sinf(fang) * (ed + .5) + o->rey * ssc + sinf(fang - PI / 2) * esp;

          bp_renderer_push(
              usr->r->bpr,
              &(bp_instance){
                  {(mww2 + (ex + hx - gdata->data.view_xx) * gdata->data.gsc) -
                       pupil_r,
                   (mhh2 + (ey + hy - gdata->data.view_yy) * gdata->data.gsc) -
                       pupil_r,
                   pupil_r * 2, 0},
                  gdata->cg_uvs[BLANK_UV],
                  {dfs->ppc.r, dfs->ppc.g, dfs->ppc.b, ea}});

          ex =
              cosf(fang) * (ed + .5) + o->rex * ssc + cosf(fang + PI / 2) * esp;
          ey =
              sinf(fang) * (ed + .5) + o->rey * ssc + sinf(fang + PI / 2) * esp;

          bp_renderer_push(
              usr->r->bpr,
              &(bp_instance){
                  {(mww2 + (ex + hx - gdata->data.view_xx) * gdata->data.gsc) -
                       pupil_r,
                   (mhh2 + (ey + hy - gdata->data.view_yy) * gdata->data.gsc) -
                       pupil_r,
                   pupil_r * 2, 0},
                  gdata->cg_uvs[BLANK_UV],
                  {dfs->ppc.r, dfs->ppc.g, dfs->ppc.b, ea}});

            }
          }

          /* AIR's special skins (OM, 2026-11-07, game/wyrm_extras.c). */
          air_looks_draw(env, o, hx, hy, fang, ssc, ea, mww2, mhh2, lsz, ed,
                         !(usrs->hotkeys[HOTKEY_ASSIST].active && usrs->ext.assist_hide_cosmetics) &&
                             mode->show_accessories);

          // The tag hangs off the head, over everything else the snake is
          // wearing, which is where the mod puts it too.
          const int hide_cosmetics =
              usrs->hotkeys[HOTKEY_ASSIST].active &&
              usrs->ext.assist_hide_cosmetics;
          if (!hide_cosmetics)
          tags_draw(env, o, o->id == gdata->data.snake_id, false);

          // accessory:
          /* A special AIR skin draws its accessory after its art (wyrm_extras.c). */
          if (!hide_cosmetics && mode->show_accessories && o->accessory < NUM_ACCESSORIES &&
              !air_looks_owns_accessory(o)) {
            accessory_data* acc = gdata->accessories + o->accessory;
            ex = acc->of * cosf(fang) * ed;
            ey = acc->of * sinf(fang) * ed;
            m = lsz * gdata->data.gsc * acc->sc;
            float acx =
                mww2 + (ex + hx - gdata->data.view_xx) * gdata->data.gsc;
            float acy =
                mhh2 + (ey + hy - gdata->data.view_yy) * gdata->data.gsc;

            bp_renderer_push(
                usr->r->bpr,
                &(bp_instance){
                    {acx - m, acy - m, m * 2, fang}, acc->uv, {1, 1, 1, ea}});
          }
#if defined(WYRM_SDL_JNI)
          // Wyrm's own hair, ears and glasses: the player's snake only, and
          // only on this phone (platform/android_look.c).
          if (!hide_cosmetics && o->id == gdata->data.snake_id)
            wyrm_look_draw(env, hx, hy, fang, lsz, ea, mww2, mhh2);
#endif
        } else {
          // Tags are a separate cosmetic hanging from the head, not snake
          // thickness, so Rope Mode leaves the player's chosen tag intact.
          if (!(usrs->hotkeys[HOTKEY_ASSIST].active &&
                usrs->ext.assist_hide_cosmetics))
          tags_draw(env, o, true, false);
        }

        // ex = cosf(fang) * lsz;
        // ey = sinf(fang) * lsz;
        // float sz = 2 * gdata->data.gsc;

        // bp_renderer_push(
        //     usr->r->astr,
        //     &(bp_instance){
        //         {(mww2 + (ex + hx - gdata->data.view_xx) * gdata->data.gsc)
        //         -
        //              (sz + 2),
        //          (mhh2 + (ey + hy - gdata->data.view_yy) * gdata->data.gsc)
        //          -
        //              (sz + 2),
        //          (sz + 2) * 2, 0},
        //         gdata->cg_uvs[BLANK_UV],
        //         {0, 0, 0, a}});

        // bp_renderer_push(
        //     usr->r->astr,
        //     &(bp_instance){
        //         {(mww2 + (ex + hx - gdata->data.view_xx) * gdata->data.gsc)
        //         -
        //              sz,
        //          (mhh2 + (ey + hy - gdata->data.view_yy) * gdata->data.gsc)
        //          -
        //              sz,
        //          sz * 2, 0},
        //         gdata->cg_uvs[BLANK_UV],
        //         {1, 1, 1, a}});
      }
    }
  }

#if defined(__ANDROID__) || defined(WYRM_MOBILE)
  ImDrawList_PopTexture(name_draw_list);
#endif
  igPopFont();

  usr->r->global.view[0] = gdata->data.view_xx;
  usr->r->global.view[1] = gdata->data.view_yy;
  usr->r->global.zoom = gdata->data.gsc;
  usr->r->global.grd = gdata->data.grd;
  usr->r->global.bd_radius = gdata->data.flux_grd;
  usr->r->global.bd_color[0] = usrs->bd_color[0];
  usr->r->global.bd_color[1] = usrs->bd_color[1];
  usr->r->global.bd_color[2] = usrs->bd_color[2];
  usr->r->global.bg_scale = mode->bg_scale;
  /* "None" skips the floor draw; the clear shows through as flat black. */
  const int floor_index = background_clamp(usrs->arena_background);
  /* Your photo's path can arrive after the renderer started (OM, 2026-11-08). */
  if (floor_index == BACKGROUND_CUSTOM && usr->r->background != BACKGROUND_CUSTOM)
    renderer_set_background(usr->r, env->ctx, BACKGROUND_CUSTOM);
  usr->r->global.bg_opacity = floor_index == BACKGROUND_NONE ? 0.0f : 1.0f;
  usr->r->global.bd_opacity = 0.8f;
  usr->r->global.minimap_data_size = gdata->data.mmsz;

#if defined(__ANDROID__)
  /* This is the front edge of the local head, not the touch cursor.  The
     14.5 radius is the same half-size used to draw the head bead above, and
     ehang is the same smoothed angle used by the rendered head, so the marker
     turns at exactly the visible head's speed rather than jumping to input.
     Keeping this in the draw path makes it a guide only: steering, collision
     and every byte sent to the arena remain untouched. */
  if (mode->show_crosshair &&
      mobile_controls_steering_mode(env) != MOBILE_STEERING_ARROW) {
    snake* own = get_snake(gdata, gdata->data.snake_id);
    if (own && own->iiv && !own->dead) {
      const float head_radius = 14.5f * own->sc;
      const float collision_x =
          own->xx + own->fx + cosf(own->ehang) * head_radius;
      const float collision_y =
          own->yy + own->fy + sinf(own->ehang) * head_radius;
      const ImVec2 point = {
          mww2 + (collision_x - gdata->data.view_xx) * gdata->data.gsc,
          mhh2 + (collision_y - gdata->data.view_yy) * gdata->data.gsc};
      const int dot_mode = usrs->hotkeys[HOTKEY_ASSIST].active ? 1 : 0;
      /* The setting is a world-space diameter, like the 29-unit head bead.
         Project both through the snake scale and camera zoom so their ratio
         cannot change with viewport size, resolution or zoom. */
      const float radius = usrs->head_dot_size[dot_mode] * own->sc *
                           gdata->data.gsc * 0.5f;
      const float* color = usrs->head_dot_color[dot_mode];
      ImDrawList_AddCircleFilled(
          igGetWindowDrawList(), point, radius,
          igColorConvertFloat4ToU32(
              (ImVec4){color[0], color[1], color[2], own->alive_amt}),
          20);
    }
  }
#endif

  /* Black is drawn the way assist mode's hidden floor is: no colour, full
     opacity, so it is true black in either mode. */
  usr->r->global.bg_color[0] = usr->r->global.bg_color[1] =
      usr->r->global.bg_color[2] =
          floor_index == BACKGROUND_BLACK ? 0.0f : (float)mode->show_background;
  /* The solid pipeline now owns every analytic shape, including rings. Preys
   * remain original circles in fd_renderer_render(). */
  usr->r->fdr->pipeline_idx = 0;

  /* About half a second to cross-fade a minimap cell (was ~0.3 s), so a
     snake's mark glides to its new cells (OM, 2026-10-09). */
  lerp_minimap_float(gdata->data.mm_data_follow, gdata->data.mm_data,
                     gdata->data.mmsz, 0.035f * gdata->data.vfr);

  /* The visible ImGui minimap reads the smooth float data directly. */
  if (usr->r->global.minimap_opacity > 0.0f)
    minimap_float_to_u8(gdata->data.mm_data_follow, usr->r->mmr->minimap,
                        gdata->data.mmsz);

  gdata->data.frames++;

  if (gdata->data.fps_etm >= 1000) {
    gdata->data.fps = gdata->data.frames;
    gdata->data.frames = 0;
    gdata->data.fps_ltm = gdata->data.ctm;
  }

  // ping: measured per pong in oef.c (ping_mark_received, OM 2026-10-12);
  // this only eases the colour towards it.
  gdata->data.ping_follow = glm_lerp(
      gdata->data.ping_follow,
      GLM_MAX(GOOD_PING, GLM_MIN((float)gdata->data.ping, BAD_PING)),
      0.1f * gdata->data.vfr);
}
