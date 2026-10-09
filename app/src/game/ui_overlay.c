#include "ui_overlay.h"
#include "ai_mode.h"

#include "arena_theme.h"
#include "../mobile/mobile_controls.h"
#include "../platform/android_team.h"
#include "../platform/android_voice.h"
#include "../platform/android_home.h"
#include "../ui/ui_theme.h"
#include "backgrounds.h"
#include "../user.h"

/*
 * The arena's overlay, in Wyrm's language.
 *
 * Same palette and the same two typefaces as every paper screen in the app:
 * the display serif for numbers, the body face for words, warm paper and ink.
 * This remains native ImGui paint on the engine thread. No Compose surface,
 * bridge or second state model is involved, so the arena remains the sole
 * owner of both the values and the frame in which they appear.
 *
 * One exception survives: each snake keeps its own colour, as a dot beside its
 * name rather than as the name. That is the one piece of information here that
 * colour carries better than words.
 */

#define HUD_PANEL_ROUNDING 14.0f

static float leaderboard_hit[4] = {0, 0, 0, 0};
static bool leaderboard_expanded;
static float leaderboard_expand;

/** Places a measured HUD item by its normalized centre and keeps it visible. */
/* Where the previous run ended (OM, 2026-10-02): a red dot on the arena's
   minimap during the next run, so you can see where you went down last time.
   Set by android_home.c's record_finished_run (real arenas only), kept in
   memory until the next run ends. Same frame as your own white dot (mm.slang:
   (pos - grd) / flux_grd, at 0.9 of the radius). Draw-only. */
static bool last_death_valid = false;
static float last_death_x = 0.0f;
static float last_death_y = 0.0f;

void wyrm_last_death_set(float x, float y) {
  if (!isfinite(x) || !isfinite(y) || (x == 0.0f && y == 0.0f)) return;
  last_death_x = x;
  last_death_y = y;
  last_death_valid = true;
}

static void draw_last_death(tenv* env, float left, float top, float diameter) {
  if (!last_death_valid || diameter <= 0.0f) return;
  game_data* game = &env->usr->gdata;
  if (game->ai_mode || ai_mode_editor_bare()) return;
  float world_radius = game->data.flux_grd;
  if (world_radius <= 1.0f) return;
  float nx = (last_death_x - game->data.grd) / world_radius;
  float ny = (last_death_y - game->data.grd) / world_radius;
  float reach = sqrtf(nx * nx + ny * ny);
  if (reach > 1.0f) {
    nx /= reach;
    ny /= reach;
  }
  float half = diameter * 0.5f;
  ImVec2 point = {left + half + nx * half * 0.9f, top + half + ny * half * 0.9f};
  /* A red dot with a red ring round it, a few pixels apart (OM, 2026-10-06:
     circle in circle), each over a dark edge so it survives a pale patch
     of map. Wyrm's death red (the app's Blood, #FF4D4D), never the white
     of you or a teammate's green. */
  float core = diameter * 0.016f;
  if (core < 2.4f) core = 2.4f;
  if (core > 4.0f) core = 4.0f;
  /* A clear gap between the dot and the ring (OM, 2026-10-09). */
  float ring = core + 6.0f;
  ImDrawList* draw = igGetForegroundDrawList_ViewportPtr(NULL);
  ImU32 shade = igColorConvertFloat4ToU32((ImVec4){0, 0, 0, 0.60f});
  ImU32 red = igColorConvertFloat4ToU32((ImVec4){1.0f, 0.302f, 0.302f, 1.0f});
  ImDrawList_AddCircle(draw, point, ring, shade, 28, 3.4f);
  ImDrawList_AddCircleFilled(draw, point, core + 1.2f, shade, 20);
  ImDrawList_AddCircle(draw, point, ring, red, 28, 1.6f);
  ImDrawList_AddCircleFilled(draw, point, core, red, 20);
}

static ImVec2 hud_top_left(tenv* env, float nx, float ny, float width,
                           float height, float edge) {
  return (ImVec2){glm_clamp(nx, 0.0f, 1.0f) * env->ctx->size[0] - width * 0.5f,
                  glm_clamp(ny, 0.0f, 1.0f) * env->ctx->size[1] - height * 0.5f};
}

static ImVec2 hud_clamp_top_left(tenv* env, ImVec2 value, float width,
                                  float height, float edge) {
  value.x = GLM_MAX(edge, GLM_MIN(value.x, env->ctx->size[0] - edge - width));
  value.y = GLM_MAX(edge, GLM_MIN(value.y, env->ctx->size[1] - edge - height));
  return value;
}

typedef struct hud_rgb {
  float r;
  float g;
  float b;
} hud_rgb;

static ImU32 hud_colour(float r, float g, float b, float a) {
  return igColorConvertFloat4ToU32((ImVec4){r, g, b, a});
}

/** A vertical spectrum: every snake name gets a distinct point on one flow. */
static ImU32 leaderboard_name_colour(int row, float alpha) {
  static const hud_rgb stops[] = {
      {0.96f, 0.38f, 0.55f}, {0.98f, 0.64f, 0.28f},
      {0.48f, 0.82f, 0.52f}, {0.26f, 0.72f, 0.92f},
      {0.68f, 0.46f, 0.96f}, {0.94f, 0.36f, 0.82f},
  };
  float position = (float)row / (NUM_LEADERBOARD_ENTRIES - 1) * 5.0f;
  int left = (int)floorf(position);
  if (left > 4) left = 4;
  float t = position - left;
  hud_rgb a = stops[left];
  hud_rgb b = stops[left + 1];
  return hud_colour(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t,
                    a.b + (b.b - a.b) * t, alpha);
}

bool ui_overlay_leaderboard_hit(tenv* env, float x, float y) {
  if (!env || env->usr->gdata.curr_screen != PLAYING ||
      (env->usr->gdata.conn != CONNECTED &&
       env->usr->gdata.conn != AI_CONNECTED) ||
      !env->usr->gdata.data.gotlb)
    return false;
  if (x < leaderboard_hit[0] || y < leaderboard_hit[1] ||
      x > leaderboard_hit[0] + leaderboard_hit[2] ||
      y > leaderboard_hit[1] + leaderboard_hit[3])
    return false;
  leaderboard_expanded = !leaderboard_expanded;
  return true;
}

void ui_overlay_toggle_leaderboard(tenv* env) {
  if (!env || env->usr->gdata.curr_screen != PLAYING ||
      !env->usr->gdata.data.gotlb)
    return;
  leaderboard_expanded = !leaderboard_expanded;
}

/**
 * A paper card behind a block of the overlay.
 *
 * One shadow, one fill and one hairline are deliberately cheaper than the old
 * layered glass recipe. The panel is nearly opaque because this information
 * must stay readable over pale food, but it occupies exactly the same measured
 * rectangle and does not change any arena or input geometry.
 */
static void draw_hud_paper(ImDrawList* draw, ImVec2 min, ImVec2 max,
                           float alpha) {
  ImDrawList_AddRectFilled(draw, (ImVec2){min.x, min.y + 3.0f},
                           (ImVec2){max.x, max.y + 3.0f},
                           hud_colour(0, 0, 0, 0.16f * alpha),
                           HUD_PANEL_ROUNDING, 0);
  ImDrawList_AddRectFilled(draw, min, max,
                           arena_theme_colour(ARENA_THEME_CARD, 0.96f * alpha),
                           HUD_PANEL_ROUNDING, 0);
  ImDrawList_AddRect(draw, min, max,
                     arena_theme_colour(ARENA_THEME_RULE, alpha),
                     HUD_PANEL_ROUNDING, 0, 1.0f);
}

/** How wide a string is in a given font, without disturbing the cursor. */
static float measure(ImFont* font, const char* text) {
  ImVec2 size;
  igPushFont(font, font->LegacySize);
  igCalcTextSize(&size, text, NULL, false, -1);
  igPopFont();
  return size.x;
}

static ImVec2 measure_scaled(ImFont* font, const char* text, float scale) {
  ImVec2 size;
  igPushFont(font, font->LegacySize * scale);
  igCalcTextSize(&size, text, NULL, false, -1);
  igPopFont();
  return size;
}

/**
 * Draws a name, shortened until it fits.
 *
 * Arena nicknames run past twenty characters and the column is as wide as it
 * is; without this the long ones carried straight on over the scores beside
 * them. Cut with a trailing ellipsis rather than clipped mid-letter, so a
 * shortened name still reads as a name.
 */
static void draw_fitted_text(ImDrawList* draw, ImFont* font, ImVec2 pos,
                             ImU32 colour, const char* text, float max_width) {
  if (measure(font, text) <= max_width) {
    ImDrawList_AddText_FontPtr(draw, font, font->LegacySize, pos, colour, text,
                               NULL, 0, NULL);
    return;
  }
  char shortened[MAX_NICKNAME_LEN + 8];
  int length = (int)strlen(text);
  if (length > (int)sizeof(shortened) - 5) length = (int)sizeof(shortened) - 5;
  while (length > 1) {
    snprintf(shortened, sizeof(shortened), "%.*s...", --length, text);
    if (measure(font, shortened) <= max_width) break;
  }
  ImDrawList_AddText_FontPtr(draw, font, font->LegacySize, pos, colour,
                             shortened, NULL, 0, NULL);
}

/** A label and value in the user's chosen stats size. */
static void draw_stat_row(tenv* env, ImDrawList* draw, float right, float y,
                          const char* label, const char* value, float alpha) {
  tuser_data* usr = env->usr;
  ImFont* body = usr->imgui_data.regular_font[usr->usrs.stats_font_size];
  ImFont* display =
      usr->imgui_data.regular_font_bold[usr->usrs.stats_font_size];

  float scale = usr->usrs.hud_stats_scale;
  alpha *= usr->usrs.hud_stats_opacity;
  ImVec2 value_size = measure_scaled(display, value, scale);

  ImDrawList_AddText_FontPtr(draw, display, display->LegacySize * scale,
                             (ImVec2){right - value_size.x, y},
                             arena_theme_colour(ARENA_THEME_INK, alpha), value,
                             NULL, 0, NULL);

  ImVec2 label_size = measure_scaled(body, label, scale);
  ImDrawList_AddText_FontPtr(
      draw, body, body->LegacySize * scale,
      (ImVec2){right - value_size.x - 10.0f * scale - label_size.x,
               y + (value_size.y - label_size.y) * 0.72f},
      arena_theme_colour(ARENA_THEME_QUIET, 0.92f * alpha), label, NULL, 0,
      NULL);
}

/* ---- Near Original (OM, 2026-10-02): slither's own HUD, Main.as ---- */

static float original_lb_fade = 0.0f;

static ImVec2 original_text_size(ImFont* font, float size, const char* text) {
  ImVec2 out;
  igPushFont(font, size);
  igCalcTextSize(&out, text, NULL, false, -1);
  igPopFont();
  return out;
}

/*
 * One leaderboard string as the original draws it (Main.as drawText and the
 * glyph sheets, 26865-27030). The glyphs were cut at 52 px with
 * DropShadowFilter(0, 90, black, 1, 7, 7, strength 24) = a solid black outline
 * about 6 px wide, then DropShadowFilter(3, 90, black, 0.75, 8, 8) = a soft
 * shadow 3 px below. The board draws that outlined glyph, tinted with the
 * snake's colour, at `alpha`, and the bare face again ADDITIVELY at the same
 * alpha (highscore_add_batch). Over the face the sum is bg(1 - a) + 2ca, so the
 * face is drawn here in twice its colour; that is why it reads on any floor.
 */
static void original_text(ImDrawList* draw, ImFont* font, float size,
                          ImVec2 pos, vec3s colour, float alpha,
                          const char* text) {
  if (alpha <= 0.004f) return;
  float f = size / 52.0f;
  float outline = 6.0f * f;
  float drop = 3.0f * f;
  /* Stamps overlap about three deep: each is weaker so the pile reads as alpha. */
  float stamp = 1.0f - powf(1.0f - (alpha > 0.999f ? 0.999f : alpha), 1.0f / 3.0f);
  ImU32 shadow = igColorConvertFloat4ToU32((ImVec4){0, 0, 0, stamp * 0.75f * 0.45f});
  ImU32 black = igColorConvertFloat4ToU32((ImVec4){0, 0, 0, stamp});
  for (int i = 0; i < 8; ++i) {
    float a = 6.2831853f * (i + 0.5f) / 8.0f;
    float spread = outline + 4.0f * f;
    ImDrawList_AddText_FontPtr(
        draw, font, size,
        (ImVec2){pos.x + cosf(a) * spread, pos.y + drop + sinf(a) * spread},
        shadow, text, NULL, 0, NULL);
  }
  for (int i = 0; i < 12; ++i) {
    float a = 6.2831853f * i / 12.0f;
    ImDrawList_AddText_FontPtr(
        draw, font, size,
        (ImVec2){pos.x + cosf(a) * outline, pos.y + sinf(a) * outline},
        black, text, NULL, 0, NULL);
  }
  for (int i = 0; i < 6; ++i) {
    float a = 6.2831853f * (i + 0.5f) / 6.0f;
    ImDrawList_AddText_FontPtr(
        draw, font, size,
        (ImVec2){pos.x + cosf(a) * outline * 0.5f, pos.y + sinf(a) * outline * 0.5f},
        black, text, NULL, 0, NULL);
  }
  float r = colour.x * 2.0f, g = colour.y * 2.0f, b = colour.z * 2.0f;
  ImU32 face = igColorConvertFloat4ToU32((ImVec4){r > 1.0f ? 1.0f : r,
                                                  g > 1.0f ? 1.0f : g,
                                                  b > 1.0f ? 1.0f : b, alpha});
  ImDrawList_AddText_FontPtr(draw, font, size, pos, face, text, NULL, 0, NULL);
}

/*
 * The minimap disc's colour per floor. The original fills the disc with its
 * floor image, scaled by max(1, 512 / short side) x 0.5 and blurred 64
 * (Main.as setMinimapSize 18568-18740); the classic floor first x0.6 + 4, then
 * light floors x0.75 and the rest x1.35 + 12. Measured offline from the same
 * images (mean over the disc after that blur); Wyrm's own floors as dark ones,
 * None as black. Unknown ids fall back to the original's dark default.
 */
static const struct {
  const char* id;
  unsigned char r, g, b;
} ORIGINAL_MINIMAP_FLOOR[] = {
    {"wyrm", 40, 48, 62},          {"none", 12, 12, 12},
    {"classic", 39, 46, 55},       {"bgee2", 27, 44, 62},
    {"asanoha", 82, 33, 39},       {"seigaiha", 74, 113, 146},
    {"graygrid", 126, 126, 126},   {"rizz", 159, 124, 175},
    {"usastar", 39, 63, 128},      {"circuits", 33, 29, 60},
    {"circuits2", 37, 33, 81},     {"hexice", 61, 96, 124},
    {"hexb", 27, 37, 72},          {"hearts", 176, 95, 114},
    {"leaves", 131, 78, 51},       {"paint", 144, 110, 100},
    {"snakey", 95, 108, 71},       {"stainedglass", 108, 72, 76},
    {"kitties", 155, 95, 159},     {"bluecube", 67, 81, 177},
    {"purplecube", 120, 40, 161},  {"redcube", 167, 60, 54},
    {"black", 12, 12, 12},         {"wyrm_midnight", 28, 42, 65},
    {"wyrm_carbon", 46, 50, 57},   {"wyrm_abyss", 23, 50, 57},
    {"wyrm_nebula", 23, 22, 35},   {"wyrm_dotgrid", 34, 36, 40},
    {"wyrm_contours", 32, 44, 51}, {"wyrm_scales", 35, 51, 43},
};

static vec3s original_minimap_floor(int floor) {
  const char* id = BACKGROUNDS[background_clamp(floor)].id;
  int count = (int)(sizeof(ORIGINAL_MINIMAP_FLOOR) / sizeof(ORIGINAL_MINIMAP_FLOOR[0]));
  for (int i = 0; i < count; ++i)
    if (strcmp(ORIGINAL_MINIMAP_FLOOR[i].id, id) == 0)
      return (vec3s){{ORIGINAL_MINIMAP_FLOOR[i].r / 255.0f,
                      ORIGINAL_MINIMAP_FLOOR[i].g / 255.0f,
                      ORIGINAL_MINIMAP_FLOOR[i].b / 255.0f}};
  return (vec3s){{27 / 255.0f, 44 / 255.0f, 62 / 255.0f}};
}

/*
 * The minimap's DropShadowFilter(3, 90, black, 0.5, 12, 12, 1, 3): the disc's
 * silhouette moved down `drop` and blurred (three box passes of 12 = sigma ~6),
 * seen only where the disc itself is not (the bitmap is opaque inside the disc).
 * Thin rings, each at that distance's shadow strength, cut away over the disc.
 */
static void original_disc_shadow(ImDrawList* draw, ImVec2 c, float R,
                                 float drop, float sigma, float alpha) {
  const int segments = 64;
  float step = sigma * 0.35f;
  if (step < 1.0f) step = 1.0f;
  ImVec2 s = {c.x, c.y + drop};
  float keep = (R + step * 0.5f) * (R + step * 0.5f);
  for (float d = -drop; d < sigma * 3.0f; d += step) {
    float mid = d + step * 0.5f;
    float rho = R + mid;
    if (rho <= 0.0f) continue;
    float a = alpha * 0.5f * erfcf(mid / (sigma * 1.41421356f));
    if (a < 0.003f) continue;
    ImU32 col = igColorConvertFloat4ToU32((ImVec4){0, 0, 0, a});
    for (int i = 0; i < segments; ++i) {
      float a0 = 6.2831853f * i / segments;
      float a1 = 6.2831853f * (i + 1) / segments;
      ImVec2 p0 = {s.x + cosf(a0) * rho, s.y + sinf(a0) * rho};
      ImVec2 p1 = {s.x + cosf(a1) * rho, s.y + sinf(a1) * rho};
      float mx = (p0.x + p1.x) * 0.5f - c.x;
      float my = (p0.y + p1.y) * 0.5f - c.y;
      if (mx * mx + my * my <= keep) continue;
      ImDrawList_AddLine(draw, p0, p1, col, step + 0.5f);
    }
  }
}

/* A pie slice of the minimap disc, from angle a0 to a1 (radians, y down). */
static void original_wedge(ImDrawList* draw, ImVec2 c, float r, float a0,
                           float a1, ImU32 colour) {
  ImVec2 points[18];
  points[0] = c;
  for (int i = 0; i <= 16; ++i) {
    float a = a0 + (a1 - a0) * i / 16.0f;
    points[i + 1] = (ImVec2){c.x + cosf(a) * r, c.y + sinf(a) * r};
  }
  ImDrawList_AddConvexPolyFilled(draw, points, 18, colour);
}

/*
 * The original HUD (Main.as): the minimap top-left (x 24 px, y 8 px, plus the
 * notch upright; scale 0.75u; a #202630 disc with two lighter quarters, alpha
 * 0.7, a soft shadow, "server N" in Nunito Bold 18 above it; white cells at
 * 0.475 and your position as a white dot) and the leaderboard top-right
 * (rows of 14u, text 11u in Nunito Bold, each in that snake's colour with a
 * black outline; no title). Wyrm's stats and bearing are not drawn.
 */
static void draw_original_hud(tenv* env, ImDrawList* draw) {
  tuser_data* usr = env->usr;
  game_data* gdata = &usr->gdata;
  user_settings* usrs = &usr->usrs;
  float W = env->ctx->size[0];
  float H = env->ctx->size[1];
  float u = (W < H ? W : H) / 480.0f;
  bool portrait = H > W;
  float notch = 0.0f;
  if (portrait) {
    ui_safe_area safe = ui_theme_safe_area(env);
    notch = safe.y;
    /* SDL gives the safe area in window coordinates: points on iOS (a third
       of the pixels on a 3x phone), pixels on Android. The HUD is in pixels. */
    if (env->wnd && env->wnd->handle) {
      int points_w = 0, points_h = 0;
      SDL_GetWindowSize(env->wnd->handle, &points_w, &points_h);
      if (points_h > 0 && env->wnd->size[1] > points_h)
        notch *= (float)env->wnd->size[1] / (float)points_h;
    }
    if (notch > 90.0f * u) notch = 90.0f * u;
    if (notch < 0.0f) notch = 0.0f;
  }
  ImFont* bold = usr->imgui_data.nunito_bold
                     ? usr->imgui_data.nunito_bold
                     : usr->imgui_data.regular_font_bold[1];

  /* ---- minimap ---- */
  int mmsz = gdata->data.mmsz > 0 ? gdata->data.mmsz : 80;
  if (mmsz > MAX_MINIMAP_SIZE) mmsz = MAX_MINIMAP_SIZE;
  float k = 0.75f * u;
  const float pad = 12.0f;
  float side = mmsz + pad * 2.0f;
  float ox = 24.0f;
  float oy = 8.0f + notch;
  float r = mmsz * 0.5f;
  ImVec2 c = {ox + side * 0.5f * k, oy + (side * 0.5f + 23.0f) * k};
  float R = r * k;

  /* The bitmap's shadow (0.5) under the bitmap's own alpha (0.7). */
  original_disc_shadow(draw, c, R, 3.0f * k, 6.0f * k, 0.5f * 0.7f);
  /* The disc: the floor, blurred (see ORIGINAL_MINIMAP_FLOOR), with the top-left
     and bottom-right quarters lifted by #202020 (ADD); the bitmap at 0.7. */
  vec3s floor_rgb = original_minimap_floor(usrs->arena_background);
  float lift = 32.0f / 255.0f;
  ImU32 dark = igColorConvertFloat4ToU32((ImVec4){floor_rgb.x, floor_rgb.y, floor_rgb.z, 0.7f});
  ImU32 light = igColorConvertFloat4ToU32((ImVec4){
      floor_rgb.x + lift > 1.0f ? 1.0f : floor_rgb.x + lift,
      floor_rgb.y + lift > 1.0f ? 1.0f : floor_rgb.y + lift,
      floor_rgb.z + lift > 1.0f ? 1.0f : floor_rgb.z + lift, 0.7f});
  const float pi = 3.14159265f;
  original_wedge(draw, c, R, pi, 1.5f * pi, light);
  original_wedge(draw, c, R, 1.5f * pi, 2.0f * pi, dark);
  original_wedge(draw, c, R, 0.0f, 0.5f * pi, light);
  original_wedge(draw, c, R, 0.5f * pi, pi, dark);

  int server = android_home_near_original_server();
  if (server > 0) {
    char label[32];
    snprintf(label, sizeof(label), "server %d", server);
    float size = 18.0f * k;
    ImVec2 ts = original_text_size(bold, size, label);
    ImVec2 at = {ox + (side * k - ts.x) * 0.5f, oy + 6.0f * k};
    ImU32 hush = igColorConvertFloat4ToU32((ImVec4){0, 0, 0, 0.5f * 0.7f * 0.75f * 0.22f});
    for (int i = 0; i < 6; ++i) {
      float a = 6.2831853f * i / 6.0f;
      ImDrawList_AddText_FontPtr(
          draw, bold, size,
          (ImVec2){at.x + cosf(a) * 3.0f * k, at.y + 3.0f * k + sinf(a) * 3.0f * k},
          hush, label, NULL, 0, NULL);
    }
    ImDrawList_AddText_FontPtr(
        draw, bold, size, at,
        igColorConvertFloat4ToU32((ImVec4){1, 1, 1, 0.75f * 0.7f}), label, NULL, 0, NULL);
  }

  /* The map: one rect per run of set cells in a row. */
  if (gdata->data.mmsz > 0) {
    ImU32 cell = igColorConvertFloat4ToU32((ImVec4){1, 1, 1, 0.475f});
    float left = ox + pad * k;
    float top = oy + (pad + 23.0f) * k;
    for (int y = 0; y < mmsz; ++y) {
      const uint8_t* row = gdata->data.mm_data + y * MAX_MINIMAP_SIZE;
      int x = 0;
      while (x < mmsz) {
        if (!row[x]) { ++x; continue; }
        int start = x;
        while (x < mmsz && row[x]) ++x;
        ImDrawList_AddRectFilled(draw, (ImVec2){left + start * k, top + y * k},
                                 (ImVec2){left + x * k, top + (y + 1) * k}, cell, 0, 0);
      }
    }
  }

  float world = gdata->data.flux_grd;
  if (world > 1.0f) {
    int count = tdarray_length(gdata->data.snakes);
    if (count) {
      snake* me = gdata->data.snakes + (count - 1);
      if (me->local_player && gdata->data.snake_id == me->id) {
        float nx = (me->xx + me->fx - gdata->data.grd) / world;
        float ny = (me->yy + me->fy - gdata->data.grd) / world;
        ImVec2 dot = {c.x + nx * R, c.y + ny * R};
        ImDrawList_AddCircleFilled(draw, dot, 3.0f * k + 1.0f * k,
                                   igColorConvertFloat4ToU32((ImVec4){0, 0, 0, 0.66f}), 16);
        ImDrawList_AddCircleFilled(draw, dot, 3.0f * k,
                                   igColorConvertFloat4ToU32((ImVec4){1, 1, 1, 1}), 16);
      }
    }
    /* The previous run's death dot, on this map's geometry. */
    if (last_death_valid && !gdata->ai_mode && !ai_mode_editor_bare()) {
      float nx = (last_death_x - gdata->data.grd) / world;
      float ny = (last_death_y - gdata->data.grd) / world;
      float reach = sqrtf(nx * nx + ny * ny);
      if (reach > 1.0f) { nx /= reach; ny /= reach; }
      ImVec2 dot = {c.x + nx * R, c.y + ny * R};
      ImDrawList* front = igGetForegroundDrawList_ViewportPtr(NULL);
      ImDrawList_AddCircleFilled(front, dot, 3.0f * k + 2.0f,
                                 igColorConvertFloat4ToU32((ImVec4){0, 0, 0, 0.70f}), 20);
      ImDrawList_AddCircleFilled(front, dot, 3.0f * k,
                                 igColorConvertFloat4ToU32((ImVec4){1.0f, 0.302f, 0.302f, 1.0f}), 20);
    }
  }

  android_voice_publish_hud(c.x - R, c.y - R, R * 2.0f);
  android_team_draw_minimap(env, c.x - R, c.y - R, R * 2.0f);
  android_team_set_chat_centre(usrs->hud_chat_x * W, usrs->hud_chat_y * H);

  /* ---- leaderboard ---- */
  if (!gdata->data.gotlb) {
    original_lb_fade = 0.0f;
    return;
  }
  float vfr = gdata->data.vfr;
  if (!(vfr > 0.0f) || vfr > 4.0f) vfr = 1.0f;
  original_lb_fade += 0.01f * vfr;
  if (original_lb_fade > 1.0f) original_lb_fade = 1.0f;
  float wdxo = roundf(0.057f * (W < H ? W : H) / u);
  float lx = portrait ? W - (16.0f + 241.0f) * u : W - (16.0f + 241.0f + wdxo) * u;
  float ly = notch;
  float size = 11.0f * u;
  int row_y = 0;
  for (int row = 0; row < NUM_LEADERBOARD_ENTRIES; ++row) {
    int score = gdata->data.lb.entries[row].score;
    if (score <= 0 && !gdata->data.lb.entries[row].nickname[0]) continue;
    bool mine = gdata->data.lb_pos == row + 1;
    float k2 = mine ? 1.0f : 0.9f * (0.2f + 0.8f * powf(1.0f - (row + 1) / 10.0f, 0.66f));
    float alpha = k2 * original_lb_fade;
    int cv = gdata->data.lb.entries[row].cv;
    if (cv < 0 || cv >= NUM_COLOR_GROUPS) cv = 0;
    vec3s colour = gdata->cg_colors[cv];
    float y = ly + (5.0f + 14.0f * row_y) * u;
    /* Your own row (OM, 2026-10-05): a faint plate behind it and the rank and
       name nudged right, so you see at once where you are. */
    float me_x = mine ? 6.0f * u : 0.0f;
    if (mine)
      ImDrawList_AddRectFilled(
          draw, (ImVec2){lx - 6.0f * u, y - 1.5f * u},
          (ImVec2){lx + 247.0f * u, y + size + 2.5f * u},
          igColorConvertFloat4ToU32((ImVec4){1, 1, 1, 0.14f * original_lb_fade}),
          6.0f * u, 0);
    char rank[8];
    snprintf(rank, sizeof(rank), "#%d", row + 1);
    original_text(draw, bold, size, (ImVec2){lx + me_x, y}, colour, alpha, rank);
    const char* name = gdata->data.lb.entries[row].nickname;
    if (name[0]) {
      char fitted[MAX_NICKNAME_LEN + 8];
      snprintf(fitted, sizeof(fitted), "%s", name);
      int length = (int)strlen(fitted);
      while (length > 1 && original_text_size(bold, size, fitted).x > 165.0f * u)
        fitted[--length] = 0;
      original_text(draw, bold, size, (ImVec2){lx + 28.0f * u + me_x, y}, colour, alpha, fitted);
    }
    char points[16];
    snprintf(points, sizeof(points), "%d", score);
    float pw = original_text_size(bold, size, points).x;
    original_text(draw, bold, size, (ImVec2){lx + 241.0f * u - pw, y}, colour, alpha, points);
    ++row_y;
  }
}

/* ---- Wyrm's own HUD, readable on any floor (OM, 2026-10-02) ----
 * The original reads on every floor through contrast, not colour: an outline
 * and a shadow around every glyph. Wyrm does the same in its own hand: a soft
 * ink halo (a tight outline and a wide, low shadow in deep ink, never pure
 * black), each snake's own colour lifted until it reads, a slate plate under
 * the board only on light floors, and a minimap tinted by the floor at one
 * fixed darkness. Draw-only: nothing here touches input or the arena.
 */

/* Each floor's mean colour (the same images, measured offline). */
static const struct {
  const char* id;
  unsigned char r, g, b;
} WYRM_FLOOR_MEAN[] = {
    {"wyrm", 21, 27, 37},          {"classic", 26, 35, 47},
    {"bgee2", 11, 24, 37},         {"asanoha", 109, 44, 52},
    {"seigaiha", 99, 151, 194},    {"graygrid", 168, 168, 168},
    {"rizz", 109, 83, 121},        {"usastar", 20, 38, 86},
    {"circuits", 44, 39, 80},      {"circuits2", 49, 44, 108},
    {"hexice", 81, 128, 165},      {"hexb", 36, 49, 96},
    {"hearts", 234, 126, 151},     {"leaves", 175, 104, 68},
    {"paint", 192, 146, 133},      {"snakey", 127, 144, 94},
    {"stainedglass", 145, 96, 101}, {"kitties", 207, 127, 212},
    {"bluecube", 89, 108, 235},    {"purplecube", 160, 53, 214},
    {"redcube", 223, 80, 72},      {"wyrm_midnight", 12, 22, 39},
    {"wyrm_carbon", 25, 28, 33},   {"wyrm_abyss", 8, 28, 34},
    {"wyrm_nebula", 8, 7, 17},     {"wyrm_dotgrid", 16, 18, 21},
    {"wyrm_contours", 15, 24, 29}, {"wyrm_scales", 17, 29, 23},
};

/* Deep ink for every halo: Wyrm's black with a little blue, never #000. */
static const ImVec4 WYRM_HALO = {0.035f, 0.040f, 0.055f, 1.0f};

static float wyrm_luma(vec3s c) {
  return 0.2126f * c.x + 0.7152f * c.y + 0.0722f * c.z;
}

/* The floor really under the HUD (its mean colour), or false when none shows
   (None, Black, or a mode that hides the floor). */
static bool wyrm_floor_mean(tenv* env, vec3s* out) {
  tuser_data* usr = env->usr;
  if (usr->r->global.bg_opacity <= 0.0f || usr->r->global.bg_color[0] < 0.5f)
    return false;
  const char* id = BACKGROUNDS[background_clamp(usr->usrs.arena_background)].id;
  int count = (int)(sizeof(WYRM_FLOOR_MEAN) / sizeof(WYRM_FLOOR_MEAN[0]));
  for (int i = 0; i < count; ++i)
    if (strcmp(WYRM_FLOOR_MEAN[i].id, id) == 0) {
      *out = (vec3s){{WYRM_FLOOR_MEAN[i].r / 255.0f, WYRM_FLOOR_MEAN[i].g / 255.0f,
                      WYRM_FLOOR_MEAN[i].b / 255.0f}};
      return true;
    }
  return false;
}

/* A light floor (mean luma 0.30 or more) gets the slate plate. */
static bool wyrm_floor_light(tenv* env) {
  vec3s mean;
  return wyrm_floor_mean(env, &mean) && wyrm_luma(mean) >= 0.30f;
}

/* A snake's colour, lifted toward white until it reads over the halo. */
static ImU32 wyrm_snake_ink(vec3s c, float alpha) {
  float l = wyrm_luma(c);
  const float target = 0.62f;
  if (l < target) {
    float t = (target - l) / (1.0f - l + 0.0001f);
    c.x += (1.0f - c.x) * t;
    c.y += (1.0f - c.y) * t;
    c.z += (1.0f - c.z) * t;
  }
  return igColorConvertFloat4ToU32((ImVec4){c.x, c.y, c.z, alpha});
}

/* Text in Wyrm's ink halo: a wide, low shadow and a tight outline. */
static void wyrm_halo_text(ImDrawList* draw, ImFont* font, float size,
                           ImVec2 pos, ImU32 colour, float alpha,
                           const char* text) {
  if (alpha <= 0.004f || !text || !text[0]) return;
  float soft = size * 0.16f;
  if (soft < 2.0f) soft = 2.0f;
  float tight = size * 0.07f;
  if (tight < 1.0f) tight = 1.0f;
  ImU32 shadow = igColorConvertFloat4ToU32(
      (ImVec4){WYRM_HALO.x, WYRM_HALO.y, WYRM_HALO.z, alpha * 0.10f});
  for (int i = 0; i < 8; ++i) {
    float a = 6.2831853f * (i + 0.5f) / 8.0f;
    ImDrawList_AddText_FontPtr(
        draw, font, size,
        (ImVec2){pos.x + cosf(a) * soft, pos.y + sinf(a) * soft + soft * 0.35f},
        shadow, text, NULL, 0, NULL);
  }
  /* The outline stamps overlap about two deep. */
  float stamp = 1.0f - sqrtf(1.0f - 0.72f * alpha);
  ImU32 ink = igColorConvertFloat4ToU32(
      (ImVec4){WYRM_HALO.x, WYRM_HALO.y, WYRM_HALO.z, stamp});
  for (int i = 0; i < 8; ++i) {
    float a = 6.2831853f * i / 8.0f;
    ImDrawList_AddText_FontPtr(
        draw, font, size,
        (ImVec2){pos.x + cosf(a) * tight, pos.y + sinf(a) * tight}, ink, text,
        NULL, 0, NULL);
  }
  ImDrawList_AddText_FontPtr(draw, font, size, pos, colour, text, NULL, 0, NULL);
}

/* A name in the halo, shortened with "..." until it fits. */
static void wyrm_halo_fitted(ImDrawList* draw, ImFont* font, ImVec2 pos,
                             ImU32 colour, float alpha, const char* text,
                             float max_width) {
  if (measure(font, text) <= max_width) {
    wyrm_halo_text(draw, font, font->LegacySize, pos, colour, alpha, text);
    return;
  }
  char shortened[MAX_NICKNAME_LEN + 8];
  int length = (int)strlen(text);
  if (length > (int)sizeof(shortened) - 5) length = (int)sizeof(shortened) - 5;
  while (length > 1) {
    snprintf(shortened, sizeof(shortened), "%.*s...", --length, text);
    if (measure(font, shortened) <= max_width) break;
  }
  wyrm_halo_text(draw, font, font->LegacySize, pos, colour, alpha, shortened);
}

/*
 * Wyrm's minimap, drawn here instead of the old glass shader (which was nearly
 * clear and lost on light floors). A disc tinted by the floor at one fixed
 * darkness, a faint compass cross and middle ring, the arena's cells in paper
 * white (eased, as before), a soft shadow, and you as a Wyrm-green chevron
 * pointing where your snake goes. The world circle maps to 0.9 of the radius,
 * as before (the death dot and the voice/team marks use the same frame).
 */
static void wyrm_draw_minimap(tenv* env, ImDrawList* draw, float left,
                              float top, float diameter) {
  game_data* gdata = &env->usr->gdata;
  float R = diameter * 0.5f;
  ImVec2 c = {left + R, top + R};
  original_disc_shadow(draw, c, R + 1.5f, 2.0f, 5.0f, 0.30f);

  vec3s slate = {{0.085f, 0.095f, 0.120f}};
  vec3s base = slate;
  vec3s mean;
  if (wyrm_floor_mean(env, &mean)) {
    float l = wyrm_luma(mean);
    float s = l > 0.001f ? 0.15f / l : 1.0f;
    vec3s tint = {{mean.x * s > 1.0f ? 1.0f : mean.x * s,
                   mean.y * s > 1.0f ? 1.0f : mean.y * s,
                   mean.z * s > 1.0f ? 1.0f : mean.z * s}};
    base.x = slate.x + (tint.x - slate.x) * 0.6f;
    base.y = slate.y + (tint.y - slate.y) * 0.6f;
    base.z = slate.z + (tint.z - slate.z) * 0.6f;
  }
  ImDrawList_AddCircleFilled(draw, c, R,
                             igColorConvertFloat4ToU32((ImVec4){base.x, base.y, base.z, 0.86f}),
                             64);
  ImU32 faint = igColorConvertFloat4ToU32((ImVec4){1, 1, 1, 0.07f});
  ImDrawList_AddLine(draw, (ImVec2){c.x - R * 0.92f, c.y}, (ImVec2){c.x + R * 0.92f, c.y}, faint, 1.0f);
  ImDrawList_AddLine(draw, (ImVec2){c.x, c.y - R * 0.92f}, (ImVec2){c.x, c.y + R * 0.92f}, faint, 1.0f);
  ImDrawList_AddCircle(draw, c, R * 0.45f, faint, 48, 1.0f);

  /* The cells (OM, 2026-10-09: snakes glide across the map instead of
     jumping). A cell's eased value (mm_data_follow, 0-255) is its opacity,
     smoothstepped, and it is drawn as a soft round blob a little wider than
     the cell, so the cell fading out and its neighbour fading in overlap into
     one mark that moves. The six steps before read the 0-255 value as 0-1: a
     new cell showed at full at once and an old one hung on, then vanished. */
  int mmsz = gdata->data.mmsz;
  if (mmsz > MAX_MINIMAP_SIZE) mmsz = MAX_MINIMAP_SIZE;
  if (mmsz > 0) {
    float span = R * 0.9f * 2.0f;
    float cell = span / mmsz;
    float ox = c.x - R * 0.9f;
    float oy = c.y - R * 0.9f;
    float blob = cell * 0.82f;
    if (blob < 1.2f) blob = 1.2f;
    for (int y = 0; y < mmsz; ++y) {
      const float* row = gdata->data.mm_data_follow + y * MAX_MINIMAP_SIZE;
      for (int x = 0; x < mmsz; ++x) {
        float v = row[x] / 255.0f;
        if (v <= 0.02f) continue;
        if (v > 1.0f) v = 1.0f;
        v = v * v * (3.0f - 2.0f * v);
        ImDrawList_AddCircleFilled(
            draw, (ImVec2){ox + (x + 0.5f) * cell, oy + (y + 0.5f) * cell}, blob,
            igColorConvertFloat4ToU32((ImVec4){1, 1, 1, 0.62f * v}), 10);
      }
    }
  }

  /* You: a chevron along the drawn head's angle, in the green of the mark. */
  float world = gdata->data.flux_grd;
  int count = tdarray_length(gdata->data.snakes);
  if (world > 1.0f && count) {
    snake* me = gdata->data.snakes + (count - 1);
    if (me->local_player && gdata->data.snake_id == me->id) {
      float nx = (me->xx + me->fx - gdata->data.grd) / world;
      float ny = (me->yy + me->fy - gdata->data.grd) / world;
      float reach = sqrtf(nx * nx + ny * ny);
      if (reach > 1.0f) { nx /= reach; ny /= reach; }
      ImVec2 p = {c.x + nx * R * 0.9f, c.y + ny * R * 0.9f};
      float dx = cosf(me->ehang), dy = sinf(me->ehang);
      /* Half the size it was (OM, 2026-10-06), then 30% bigger (2026-10-09). */
      float s = R * 0.05525f;
      if (s < 3.25f) s = 3.25f;
      for (int pass = 0; pass < 2; ++pass) {
        float k = pass == 0 ? s * 1.45f : s;
        ImVec2 tip = {p.x + dx * k * 1.25f, p.y + dy * k * 1.25f};
        ImVec2 l = {p.x - dx * k * 0.8f - dy * k * 0.75f, p.y - dy * k * 0.8f + dx * k * 0.75f};
        ImVec2 r = {p.x - dx * k * 0.8f + dy * k * 0.75f, p.y - dy * k * 0.8f - dx * k * 0.75f};
        ImVec2 notch = {p.x - dx * k * 0.35f, p.y - dy * k * 0.35f};
        ImU32 col = pass == 0
                        ? igColorConvertFloat4ToU32((ImVec4){WYRM_HALO.x, WYRM_HALO.y, WYRM_HALO.z, 0.78f})
                        : igColorConvertFloat4ToU32((ImVec4){0.247f, 0.933f, 0.588f, 1.0f});
        ImDrawList_AddTriangleFilled(draw, tip, l, notch, col);
        ImDrawList_AddTriangleFilled(draw, tip, notch, r, col);
      }
    }
  }
}

void ui_overlay(tenv* env) {
  tuser_data* usr = env->usr;
  tcontext* ctx = env->ctx;
  game_data* gdata = &usr->gdata;
  user_settings* usrs = &usr->usrs;

  float mww2 = ctx->size[0] / 2.0f;
  float mhh2 = ctx->size[1] / 2.0f;

  int snakes_len = tdarray_length(gdata->data.snakes);
  if (snakes_len) {
    snake* me = gdata->data.snakes + (snakes_len - 1);

    if (me->local_player && gdata->data.snake_id == me->id) {
      float a = me->alive_amt * (1 - me->dead_amt);
      int sct = me->sct + me->rsc;
      sct = GLM_MAX(0, GLM_MIN(sct, (int)tdarray_length(gdata->data.fpsls) - 1));
      float hx = me->xx + me->fx;
      float hy = me->yy + me->fy;
      gdata->data.score = (int)floorf((gdata->data.fpsls[sct] +
                                       me->fam / gdata->data.fmlts[sct] - 1) *
                                          15 -
                                      5) /
                          1;

      float arrow_x = 0.0f;
      float arrow_y = 0.0f;
      if (usrs->hotkeys[HOTKEY_ASSIST].active &&
          mobile_controls_get_arrow_position(env, &arrow_x, &arrow_y)) {
        ImDrawList_AddLine(
            igGetWindowDrawList(),
            (ImVec2){mww2 + (hx - gdata->data.view_xx) * gdata->data.gsc,
                     mhh2 + (hy - gdata->data.view_yy) * gdata->data.gsc},
            (ImVec2){arrow_x, arrow_y},
            igColorConvertFloat4ToU32(
                (ImVec4){usrs->laser_color[0], usrs->laser_color[1],
                         usrs->laser_color[2], usrs->laser_color[3] * a}),
            usrs->laser_thickness);
      }

      /* Assist laser in joystick mode (OM, 2026-10-01/02). With assist on and
         a joystick (not the arrow, which has its own line), a line from the
         front of the head where the snake is going, like the collision dot:
         the drawn head's own angle (`ehang`), never the stick. Its length is a
         share of the screen's short side, set in Settings > Modes > Assist;
         colour and thickness are the laser's. Draw-only: no input, no packet. */
      if (usrs->hotkeys[HOTKEY_ASSIST].active &&
          mobile_controls_steering_mode(env) != MOBILE_STEERING_ARROW &&
          android_home_joystick_laser_on() && a > 0.01f) {
        float lx = cosf(me->ehang);
        float ly = sinf(me->ehang);
        float shortest = ctx->size[0] < ctx->size[1] ? (float)ctx->size[0]
                                                     : (float)ctx->size[1];
        float reach = android_home_joystick_laser_length() * shortest;
        /* The head bead's half size (14.5), as the collision dot uses it. */
        float front = 14.5f * me->sc * gdata->data.gsc;
        ImVec2 from = {mww2 + (hx - gdata->data.view_xx) * gdata->data.gsc + lx * front,
                       mhh2 + (hy - gdata->data.view_yy) * gdata->data.gsc + ly * front};
        ImDrawList_AddLine(
            igGetWindowDrawList(), from,
            (ImVec2){from.x + lx * reach, from.y + ly * reach},
            igColorConvertFloat4ToU32(
                (ImVec4){usrs->laser_color[0], usrs->laser_color[1],
                         usrs->laser_color[2], usrs->laser_color[3] * a}),
            usrs->laser_thickness);
      }
    }
  }

  android_team_tick(env);

  usr->r->global.minimap_opacity = 0;
  if (usrs->hotkeys[HOTKEY_HUD].active) {
    android_team_begin_frame();
    igPushFont(usr->imgui_data.mono_font[usrs->stats_font_size],
               usr->imgui_data.mono_font[usrs->stats_font_size]->LegacySize);

    float ping_norm =
        (gdata->data.ping_follow - GOOD_PING) / (BAD_PING - GOOD_PING);
    float lag_norm = (gdata->data.lag_mult - 0.2f) / (1 - 0.2f);
    vec3 ping_col;
    glm_vec3_lerp((vec3){0.5f, 1, 0.5f}, (vec3){1, 0.5f, 0.5f}, ping_norm,
                  ping_col);
    vec3 ic_col;
    glm_vec3_lerp((vec3){1, 0.5f, 0.5f}, (vec3){1, 1, 1}, lag_norm, ic_col);

    int tot_sec = (int)gdata->data.play_etm;
    int hours = tot_sec / 3600;
    int minutes = (tot_sec % 3600) / 60;
    int seconds = tot_sec % 60;
    char kills_text[32];
    char rank_text[48];
    char score_text[32];
    char ping_text[32];
    char fps_text[32];
    char time_text[32];
    snprintf(kills_text, sizeof(kills_text), "%d", gdata->data.kills);
    snprintf(rank_text, sizeof(rank_text), "%d / %d", gdata->data.rank,
             gdata->data.slither_count);
    snprintf(score_text, sizeof(score_text), "%d", gdata->data.score);
    snprintf(ping_text, sizeof(ping_text), "%d ms", gdata->data.ping);
    snprintf(fps_text, sizeof(fps_text), "%d FPS", gdata->data.fps);
    snprintf(time_text, sizeof(time_text), "%02d:%02d:%02d", hours, minutes,
             seconds);

    float px = (((gdata->data.view_xx - gdata->data.grd) * 2) /
                ((gdata->data.flux_grd) * 2));
    float py = (((gdata->data.view_yy - gdata->data.grd) * 2) /
                ((gdata->data.flux_grd) * 2));
    int pang = (int)roundf(glm_deg(atan2f(-py, px)));
    if (pang < 0) pang += 360;
    int dst = (int)roundf(sqrtf(px * px + py * py) * 100.0f);

    igPopFont();

    ImDrawList* draw = igGetWindowDrawList();
    const float edge = 16.0f;
    const float pad = 14.0f;

    /* The leaderboard setting now scales every piece of leaderboard type,
       including its title, ranks and hint — not only the names. */
    ImFont* label_font = usr->imgui_data.regular_font[usrs->lb_font_size];
    ImFont* name_font = usr->imgui_data.regular_font_bold[usrs->lb_font_size];
    ImFont* rank_font = usr->imgui_data.regular_font[usrs->lb_font_size];
    ImFont* score_font = usr->imgui_data.regular_font_bold[usrs->lb_font_size];

    leaderboard_hit[2] = leaderboard_hit[3] = 0.0f;
    /* Near Original: slither's own map and board instead (draw_original_hud). */
    bool original_hud = android_home_near_original();
    if (gdata->data.gotlb && !original_hud) {
      /* Five rows are the quiet default. The remaining five live behind a
         clipped, eased reveal; protocol storage remains the same ten rows. */
      float target = leaderboard_expanded ? 1.0f : 0.0f;
      float step = 0.12f * GLM_MAX(gdata->data.vfr, 0.25f);
      if (leaderboard_expand < target)
        leaderboard_expand = GLM_MIN(target, leaderboard_expand + step);
      else if (leaderboard_expand > target)
        leaderboard_expand = GLM_MAX(target, leaderboard_expand - step);
      float reveal = leaderboard_expand * leaderboard_expand *
                     (3.0f - 2.0f * leaderboard_expand);

      ImVec2 rank_size, score_size, name_size, title_size, position_size,
          hint_size;
      igPushFont(rank_font, rank_font->LegacySize);
      igCalcTextSize(&rank_size, "10", NULL, false, -1);
      igPopFont();
      igPushFont(score_font, score_font->LegacySize);
      igCalcTextSize(&score_size, "999999", NULL, false, -1);
      igPopFont();
      igPushFont(name_font, name_font->LegacySize);
      char widest[MAX_NICKNAME_LEN + 1] = {0};
      memset(widest, (int)'n', 14);
      igCalcTextSize(&name_size, widest, NULL, false, -1);
      igPopFont();
      igPushFont(label_font, label_font->LegacySize);
      igCalcTextSize(&title_size, "Wyrm Leaderboard", NULL, false, -1);
      igCalcTextSize(&position_size, "Your position  999 / 999", NULL, false,
                     -1);
      igCalcTextSize(&hint_size, "Tap on leaderboard to expand", NULL, false,
                     -1);
      igPopFont();

      float row_height = score_size.y + 7.0f;
      float board_width = GLM_MAX(
          position_size.x, GLM_MAX(hint_size.x,
                                   rank_size.x + 10.0f + name_size.x + 12.0f +
                                       score_size.x));
      float board_height = title_size.y + 8.0f +
                           row_height * (5.0f + 5.0f * reveal) + 8.0f +
                           GLM_MAX(position_size.y, score_size.y) + 4.0f +
                           hint_size.y;
      ImVec2 board_min = hud_top_left(env, usrs->hud_leaderboard_x,
                                      usrs->hud_leaderboard_y, board_width,
                                      board_height, edge);
      board_min = hud_clamp_top_left(env, board_min, board_width, board_height,
                                     edge);
      ImVec2 board_max = {board_min.x + board_width, board_min.y};

      /* A slate plate only where the floor is light (OM, 2026-10-02). */
      if (wyrm_floor_light(env)) {
        ImVec2 plate_min = {board_min.x - 12.0f, board_min.y - 10.0f};
        ImVec2 plate_max = {board_max.x + 12.0f, board_min.y + board_height + 10.0f};
        ImDrawList_AddRectFilled(draw, (ImVec2){plate_min.x, plate_min.y + 3.0f},
                                 (ImVec2){plate_max.x, plate_max.y + 3.0f},
                                 hud_colour(0, 0, 0, 0.14f), HUD_PANEL_ROUNDING, 0);
        ImDrawList_AddRectFilled(draw, plate_min, plate_max,
                                 hud_colour(WYRM_HALO.x + 0.02f, WYRM_HALO.y + 0.025f,
                                            WYRM_HALO.z + 0.03f, 0.50f),
                                 HUD_PANEL_ROUNDING, 0);
        ImDrawList_AddRect(draw, plate_min, plate_max, hud_colour(1, 1, 1, 0.10f),
                           HUD_PANEL_ROUNDING, 0, 1.0f);
      }

      wyrm_halo_text(draw, label_font, label_font->LegacySize, board_min,
                     arena_theme_overlay_text(0.92f), 0.92f, "Leaderboard");
      float row_y = board_min.y + title_size.y + 8.0f;
      float rows_bottom = row_y + row_height * (5.0f + 5.0f * reveal);
      ImDrawList_PushClipRect(draw, (ImVec2){0, row_y},
                                  (ImVec2){ctx->size[0], rows_bottom}, true);
      for (int row = 0; row < NUM_LEADERBOARD_ENTRIES; row++) {
        bool mine = gdata->data.lb_pos == (row + 1);
        float alpha = mine ? 1.0f : 0.86f;

        /* Your own row (OM, 2026-10-05: so you see at once where you are): the
           ink pill, lifted with a Wyrm-green tint and edge, and your name
           nudged right. */
        float me_shift = mine ? 8.0f : 0.0f;
        if (mine) {
          ImVec2 pill_min = {board_min.x - 6.0f, row_y - 1.0f};
          ImVec2 pill_max = {board_max.x + 6.0f, row_y + row_height - 1.0f};
          ImDrawList_AddRectFilled(
              draw, pill_min, pill_max,
              hud_colour(WYRM_HALO.x, WYRM_HALO.y, WYRM_HALO.z, 0.42f),
              row_height * 0.5f, 0);
          ImDrawList_AddRectFilled(draw, pill_min, pill_max,
                                   hud_colour(0.247f, 0.933f, 0.588f, 0.16f),
                                   row_height * 0.5f, 0);
          ImDrawList_AddRect(draw, pill_min, pill_max,
                             hud_colour(0.247f, 0.933f, 0.588f, 0.55f),
                             row_height * 0.5f, 0, 1.5f);
        }

        char rank_text[8];
        snprintf(rank_text, sizeof(rank_text), "%d", row + 1);
        ImVec2 measured;
        igPushFont(rank_font, rank_font->LegacySize);
        igCalcTextSize(&measured, rank_text, NULL, false, -1);
        igPopFont();
        wyrm_halo_text(draw, rank_font, rank_font->LegacySize,
                       (ImVec2){board_min.x + rank_size.x - measured.x, row_y + 2.0f},
                       arena_theme_overlay_text(mine ? 1.0f : 0.78f),
                       mine ? 1.0f : 0.78f, rank_text);

        /* Whose snake this is: the name and the score in its own colour
           (OM, 2026-10-02: no dot), lifted until it reads. */
        int lb_cv = gdata->data.lb.entries[row].cv;
        if (lb_cv < 0 || lb_cv >= NUM_COLOR_GROUPS) lb_cv = 0;
        ImU32 snake_ink = wyrm_snake_ink(gdata->cg_colors[lb_cv], alpha);

        char score_text_row[16];
        snprintf(score_text_row, sizeof(score_text_row), "%d",
                 gdata->data.lb.entries[row].score);
        float score_width = measure(score_font, score_text_row);

        /* The name gets whatever is left after the score has taken its width,
           and is shortened to fit rather than allowed to run over it. */
        float name_x = board_min.x + rank_size.x + 10.0f + me_shift;
        wyrm_halo_fitted(draw, name_font, (ImVec2){name_x, row_y + 3.0f},
                         snake_ink, alpha, gdata->data.lb.entries[row].nickname,
                         board_max.x - score_width - 10.0f - name_x);

        measured.x = score_width;
        wyrm_halo_text(draw, score_font, score_font->LegacySize,
                       (ImVec2){board_max.x - measured.x, row_y}, snake_ink,
                       alpha, score_text_row);
        row_y += row_height;
      }
      ImDrawList_PopClipRect(draw);

      float footer_y = rows_bottom + 8.0f;
      wyrm_halo_text(draw, label_font, label_font->LegacySize,
                     (ImVec2){board_min.x, footer_y},
                     arena_theme_overlay_text(0.92f), 0.92f, "Your position");
      float rank_width = measure(score_font, rank_text);
      wyrm_halo_text(draw, score_font, score_font->LegacySize,
                     (ImVec2){board_max.x - rank_width, footer_y},
                     arena_theme_overlay_text(1.0f), 1.0f, rank_text);
      footer_y += GLM_MAX(position_size.y, score_size.y) + 4.0f;
      const char* hint = leaderboard_expanded
                             ? "Tap on leaderboard to show 5"
                             : "Tap on leaderboard to expand";
      wyrm_halo_text(draw, label_font, label_font->LegacySize,
                     (ImVec2){board_min.x, footer_y},
                     arena_theme_overlay_text(0.66f), 0.66f, hint);
      board_max.y = footer_y + hint_size.y;
      leaderboard_hit[0] = board_min.x - 10.0f;
      leaderboard_hit[1] = board_min.y - 8.0f;
      leaderboard_hit[2] = board_width + 20.0f;
      leaderboard_hit[3] = board_max.y - board_min.y + 16.0f;
    }

    /* ---- what you are doing, directly under the leaderboard ---- */
    /* Not in the background-size editor: only the map and the board there.
       Not in Near Original either: the original has no stats. */
    if (!ai_mode_editor_bare() && !original_hud) {
      const char* labels[] = {"SCORE", "KILLS", "RANK", "TIME", "PING", "FPS"};
      const char* values[] = {score_text, kills_text, rank_text,
                              time_text,  ping_text,  fps_text};
      ImFont* stats_label_font =
          usr->imgui_data.regular_font[usrs->stats_font_size];
      ImFont* value_font =
          usr->imgui_data.regular_font_bold[usrs->stats_font_size];
      float stats_scale = usrs->hud_stats_scale;
      float stats_alpha = usrs->hud_stats_opacity;
      ImVec2 sample = measure_scaled(value_font, "000000", stats_scale);
      ImVec2 caption_size = measure_scaled(stats_label_font, usrs->ipv4,
                                           stats_scale);

      /* Measured from the widest row that is actually there rather than from a
         guessed sample: "245 / 378" and "60 FPS" are both wider than the six
         digits this used to reserve, and overflowed the panel they sit in. */
      float widest = caption_size.x;
      for (int i = 0; i < 6; ++i) {
        float row = measure_scaled(stats_label_font, labels[i], stats_scale).x +
                    12.0f * stats_scale +
                    measure_scaled(value_font, values[i], stats_scale).x;
        if (row > widest) widest = row;
      }

      float row_height = sample.y + 5.0f * stats_scale;
      float panel_width = pad * 2 + widest;
      float panel_height = pad + caption_size.y + 9.0f + row_height * 6 + pad * 0.6f;
      ImVec2 min = hud_top_left(env, usrs->hud_stats_x, usrs->hud_stats_y,
                                panel_width, panel_height, edge);
      min = hud_clamp_top_left(env, min, panel_width, panel_height, edge);
      ImVec2 max = {min.x + panel_width, min.y + panel_height};
      /* BACK (OM, 2026-10-05): the plate alone fades; the rows keep
         OPACITY. */
      draw_hud_paper(draw, min, max, stats_alpha * android_team_stats_panel());

      ImDrawList_AddText_FontPtr(draw, stats_label_font,
                                 stats_label_font->LegacySize * stats_scale,
                                 (ImVec2){min.x + pad, min.y + pad * 0.7f},
                                 arena_theme_colour(ARENA_THEME_QUIET,
                                                    0.78f * stats_alpha),
                                 usrs->ipv4, NULL, 0,
                                 NULL);
      float rule_y = min.y + pad * 0.7f + caption_size.y + 5.0f;
      ImDrawList_AddLine(draw, (ImVec2){min.x + pad, rule_y},
                         (ImVec2){max.x - pad, rule_y},
                         arena_theme_colour(ARENA_THEME_RULE,
                                            0.72f * stats_alpha), 1.0f);

      float y = rule_y + 6.0f;
      for (int i = 0; i < 6; ++i) {
        draw_stat_row(env, draw, max.x - pad, y, labels[i], values[i], 0.88f);
        y += row_height;
      }

      /* Phase 3 H (OM, 2026-10-01): when Auto has stepped the frame rate down
         (heat, Battery Saver / Low Power Mode), a small chip under the stats
         says why ms or smoothness changed. The app sets the text; nothing
         here touches input or gameplay. */
      const char* chip = android_home_performance_chip();
      if (chip && chip[0] && stats_alpha > 0.01f) {
        ImVec2 chip_text = measure_scaled(stats_label_font, chip, stats_scale);
        float chip_pad = 8.0f * stats_scale;
        float chip_w = chip_text.x + chip_pad * 2;
        float chip_h = chip_text.y + chip_pad;
        float chip_y = max.y + 6.0f * stats_scale;
        if (chip_y + chip_h > ctx->size[1] - edge)
          chip_y = min.y - 6.0f * stats_scale - chip_h;
        ImVec2 chip_min = {max.x - chip_w, chip_y};
        ImVec2 chip_max = {max.x, chip_y + chip_h};
        draw_hud_paper(draw, chip_min, chip_max, stats_alpha);
        ImDrawList_AddText_FontPtr(
            draw, stats_label_font, stats_label_font->LegacySize * stats_scale,
            (ImVec2){chip_min.x + chip_pad, chip_min.y + chip_pad * 0.5f},
            arena_theme_colour(ARENA_THEME_INK, 0.86f * stats_alpha), chip,
            NULL, 0, NULL);
      }
    }

    if (original_hud) {
      draw_original_hud(env, draw);
    } else {
    // The fullscreen gameplay window can inherit a large layout padding from
    // the menu theme. Use an explicit arena edge inset so the minimap is
    // anchored to the real top-left corner instead of drifting inward.
    const float minimap_edge_padding = 16.0f;
    usr->r->global.minimap_circ[2] = usrs->minimap_size;
    // The minimap shader treats x/y as the quad's top-left origin; z is its
    // rendered diameter. Do not add half or all of z to the origin.
    float minimap_diameter = usr->r->global.minimap_circ[2];
    ImVec2 minimap_min = hud_top_left(
        env, usrs->hud_minimap_x, usrs->hud_minimap_y, minimap_diameter,
        minimap_diameter, minimap_edge_padding);
    minimap_min = hud_clamp_top_left(env, minimap_min, minimap_diameter,
                                     minimap_diameter, minimap_edge_padding);
    float minimap_left = minimap_min.x;
    float minimap_top = minimap_min.y;
    usr->r->global.minimap_circ[0] = minimap_left;
    usr->r->global.minimap_circ[1] = minimap_top;
    /* Wyrm's own disc (wyrm_draw_minimap); the glass shader stays off. */
    wyrm_draw_minimap(env, draw, minimap_left, minimap_top, minimap_diameter);

    // minimap_circ.z is already the rendered quad width/diameter.
    android_voice_publish_hud(minimap_left, minimap_top, minimap_diameter);
    android_team_draw_minimap(env, minimap_left, minimap_top, minimap_diameter);
    draw_last_death(env, minimap_left, minimap_top, minimap_diameter);
    android_team_set_chat_centre(usrs->hud_chat_x * ctx->size[0],
                                 usrs->hud_chat_y * ctx->size[1]);
    /* The paper rim around Wyrm's disc (the shadow is the disc's own). */
    ImVec2 map_centre = {minimap_left + minimap_diameter * 0.5f,
                         minimap_top + minimap_diameter * 0.5f};
    float map_radius = minimap_diameter * 0.5f;
    ImDrawList_AddCircle(draw, map_centre, map_radius + 1.5f,
                         arena_theme_overlay_text(0.96f), 64, 5.0f);
    ImDrawList_AddCircle(draw, map_centre, map_radius - 1.0f,
                         arena_theme_colour(ARENA_THEME_INK, 0.28f), 64, 1.0f);

    /* Which way the middle of the arena lies, and how far out you have drifted. */
    char bearing_text[32];
    snprintf(bearing_text, sizeof(bearing_text), "%d°  %d%%", pang, dst);
    ImFont* bearing_font =
        usr->imgui_data.regular_font[usrs->stats_font_size];
    ImVec2 bearing_size;
    igPushFont(bearing_font, bearing_font->LegacySize);
    igCalcTextSize(&bearing_size, bearing_text, NULL, false, -1);
    igPopFont();
    ImVec2 bearing_min = {map_centre.x - bearing_size.x * 0.5f - 12.0f,
                          minimap_top + minimap_diameter + 6.0f};
    if (bearing_min.y + bearing_size.y + 10.0f > ctx->size[1] - edge)
      bearing_min.y = minimap_top - bearing_size.y - 16.0f;
    ImVec2 bearing_max = {map_centre.x + bearing_size.x * 0.5f + 12.0f,
                          bearing_min.y + bearing_size.y + 10.0f};
    draw_hud_paper(draw, bearing_min, bearing_max, 0.94f);
    ImDrawList_AddText_FontPtr(draw, bearing_font, bearing_font->LegacySize,
                               (ImVec2){map_centre.x - bearing_size.x * 0.5f,
                                        bearing_min.y + 5.0f},
                               arena_theme_colour(ARENA_THEME_INK, 0.86f),
                               bearing_text, NULL,
                               0, NULL);
    }

    /* The team, under the map it is drawn on. Nothing at all when there is no
       team, which is the common case. */
    if (!ai_mode_editor_bare())
      android_team_draw_roster_centered(env, usrs->hud_team_x * ctx->size[0],
                                        usrs->hud_team_y * ctx->size[1]);
  }

  /* Last, so it can be placed against a leaderboard that has already been
     measured — and so it is drawn over everything it sits beside. */
  android_team_draw_chat_button(env);
  android_team_draw_respawn_toggle(env);
  android_team_draw_chat_help(env);
}
