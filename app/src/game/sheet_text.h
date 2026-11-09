#ifndef WYRM_SHEET_TEXT_H
#define WYRM_SHEET_TEXT_H
/* Baked glyph overlay: outline NORMAL, face ADDITIVE, after ImGui plates.
   ASCII strings only; whole Unicode strings keep the app font. */
#include "../user.h"
#include "original_art_data.h"

extern VkDescriptorSet wyrm_sheet_set;
extern bool wyrm_sheet_warned;
extern spr_renderer *wyrm_text_normal, *wyrm_text_add;
/* The death fade's darkening (main.c): this text is drawn after ImGui,
   so the veil never covers it. 1 = none. */
extern float wyrm_sheet_veil;
extern bool wyrm_high_visibility_text;

#define SHEET_THIN 285 /* Nunito Bold: leaderboard, names, keys */
#define SHEET_BLACK 0  /* Nunito Black: the stats */
#define SHEET_EM 0.7331f
#define SHEET_DROP 0.0277f

static inline bool sheet_ok(const char* text) {
  if (!wyrm_high_visibility_text || !text || !text[0]) return false;
  if (!wyrm_sheet_set || !wyrm_text_normal || !wyrm_text_add) {
    if (!wyrm_sheet_warned) {
      SDL_Log("Wyrm sheet unavailable: falling back to app font");
      wyrm_sheet_warned = true;
    }
    return false;
  }
  for (const unsigned char* p = (const unsigned char*)text; *p; ++p)
    if (*p < 32 || *p > 126) return false;
  return true;
}

/* Main.as drawText's width: the advances, the last letter to its ink. */
static inline float sheet_width(const char* text, float size, int font) {
  float w = 0, last = 0;
  for (const unsigned char* p = (const unsigned char*)text; *p; ++p) {
    if (*p < 32 || *p > 126) continue;
    const original_glyph* g = &ORIGINAL_GLYPHS[font + (*p - 32) * 3];
    w += g->advance;
    last = g->advance - g->rw;
  }
  return (w - last) * size * SHEET_EM / 52.0f;
}

/* Cull a whole glyph at clipping edges: sprite batches have no ImGui scissor.
   Test the union of both layers so an edge never loses only its outline.
   Main.as drawText order: every outline (ochars) first, then every face, so
   a face is never covered by the next letter's outline. `outline` scales the
   outline's alpha; `face_add` puts the faces in the ADD batch (leaderboard,
   HUD) or the NORMAL one (names: face alpha, outline 0.75 of it). Tints are
   premultiplied, the atlas too (texture.c). */
static inline void sheet_draw(ImDrawList* draw, const char* text, float size,
                              ImVec2 pos, ImU32 colour, int font,
                              float outline, bool face_add) {
  if (!draw || !sheet_ok(text) || !(colour & 0xFF000000u)) return;
  ImVec2 lo, hi;
  ImDrawList_GetClipRectMin(&lo, draw);
  ImDrawList_GetClipRectMax(&hi, draw);
  float scale = size * SHEET_EM / 52.0f;
  float top = pos.y + size * SHEET_DROP;
  float alpha = (colour >> 24)/255.0f;
  float veil = wyrm_sheet_veil < 0 ? 0 : wyrm_sheet_veil > 1 ? 1 : wyrm_sheet_veil;
  vec3s rgb = {{(colour & 255)/255.0f, ((colour >> 8)&255)/255.0f,
               ((colour >> 16)&255)/255.0f}};
  for (int pass = 0; pass < 2; ++pass) {
    float a = pass == 0 ? alpha*outline : alpha;
    if (a <= 0.0f) continue;
    /* The veil darkens what is under it and keeps its coverage. */
    vec4s tint = {{rgb.r*a*veil, rgb.g*a*veil, rgb.b*a*veil, a}};
    spr_renderer* batch = pass == 0 || !face_add ? wyrm_text_normal : wyrm_text_add;
    float pen = 0;
    for (const unsigned char* p = (const unsigned char*)text; *p; ++p) {
      const original_glyph* face = &ORIGINAL_GLYPHS[font + (*p - 32)*3];
      bool visible = true;
      for (int layer = 0; layer < 2; ++layer) {
        const original_glyph* g = face + (layer == 0 ? 1 : 0);
        float s = scale * g->scale;
        float x = pos.x + pen - g->px*s, y = top - g->py*s;
        if (g->w > 1 && (x < lo.x || y < lo.y || x+g->w*s > hi.x || y+g->h*s > hi.y))
          visible = false;
      }
      const original_glyph* g = face + (pass == 0 ? 1 : 0);
      if (visible && g->w > 1) {
        float s = scale*g->scale;
        float x = pos.x + pen - g->px*s, y = top - g->py*s;
        spr_renderer_push(batch, &(spr_instance){
            {{x+g->w*s*.5f, y+g->h*s*.5f, g->w*s, g->h*s}},
            {{g->x/2048.0f, g->y/2048.0f, g->w/2048.0f, g->h/2048.0f}}, tint});
      }
      pen += face->advance*scale;
    }
  }
}

static inline void sheet_layer(ImDrawList* draw, const char* text, float size,
                               ImVec2 pos, ImU32 colour, int font, int layer) {
  (void)layer; /* Kept for existing callers: every call is now the real pair. */
  sheet_draw(draw, text, size, pos, colour, font, 1.0f, true);
}

/* One sheet layer quad in screen px, premultiplied and veiled (OM, 2026-11-01). */
static inline void sheet_quad(spr_renderer* batch, float x, float y, float w,
                              float h, float u, float v, float uw, float vh,
                              vec4s tint) {
  if (!batch || w <= 0.0f || h <= 0.0f) return;
  spr_renderer_push(batch, &(spr_instance){
      {{x + w * .5f, y + h * .5f, w, h}},
      {{u / 2048.0f, v / 2048.0f, uw / 2048.0f, vh / 2048.0f}}, tint});
}

static inline vec4s sheet_tint(ImU32 colour, float alpha_mul) {
  float a = (colour >> 24) / 255.0f * alpha_mul;
  float veil = wyrm_sheet_veil < 0 ? 0 : wyrm_sheet_veil > 1 ? 1 : wyrm_sheet_veil;
  return (vec4s){{(colour & 255) / 255.0f * a * veil,
                  ((colour >> 8) & 255) / 255.0f * a * veil,
                  ((colour >> 16) & 255) / 255.0f * a * veil, a}};
}

/* One sheet glyph (X, +, -...) with its face centred on (cx, cy); `k` is
   screen px per sheet px. Outline NORMAL, face ADD, as the sheet text. */
static inline void sheet_glyph_centred(char ch, int font, float k, float cx,
                                       float cy, ImU32 colour) {
  if (!wyrm_high_visibility_text) {
    if (ch < 33 || ch > 126 || !isfinite(k) || k <= 0) return;
    char text[2] = {ch, 0};
    ImFont* face = igGetFont();
    float size = fmaxf(1.0f, k * 52.0f / SHEET_EM);
    ImVec2 bounds;
    ImFont_CalcTextSizeA(&bounds, face, size, 1e9f, 0, text, NULL, NULL);
    ImDrawList_AddText_FontPtr(igGetWindowDrawList(), face, size,
        (ImVec2){cx-bounds.x*.5f, cy-bounds.y*.5f}, colour, text, NULL, 0, NULL);
    return;
  }
  if (ch < 33 || ch > 126 || !wyrm_text_normal || !wyrm_text_add ||
      !(colour & 0xFF000000u) || k <= 0.0f) return;
  const original_glyph* face = &ORIGINAL_GLYPHS[font + (ch - 32) * 3];
  float fs = k * face->scale;
  /* The face's top-left in glyph space is (-px, -py) * its scale. */
  float ox = cx - face->w * fs * .5f + face->px * fs;
  float oy = cy - face->h * fs * .5f + face->py * fs;
  for (int pass = 0; pass < 2; ++pass) {
    const original_glyph* g = face + (pass == 0 ? 1 : 0);
    float s = k * g->scale;
    if (g->w <= 1) continue;
    sheet_quad(pass == 0 ? wyrm_text_normal : wyrm_text_add,
               ox - g->px * s, oy - g->py * s, g->w * s, g->h * s,
               g->x, g->y, g->w, g->h, sheet_tint(colour, 1.0f));
  }
}

/* Fit the union of face and outline, including their different pivots. */
static inline void sheet_glyph_fit(char ch, int font, float w, float h,
                                    float cx, float cy, ImU32 colour) {
  if (!wyrm_high_visibility_text) {
    float k = fminf(w, h) / 52.0f;
    sheet_glyph_centred(ch, font, k, cx, cy, colour);
    return;
  }
  const original_glyph* face = &ORIGINAL_GLYPHS[font+(ch-32)*3];
  float left=1e9f, top=1e9f, right=-1e9f, bottom=-1e9f;
  for(int i=0;i<2;++i) {
    const original_glyph* g=face+i;
    left=fminf(left,-g->px*g->scale); top=fminf(top,-g->py*g->scale);
    right=fmaxf(right,(g->w-g->px)*g->scale);
    bottom=fmaxf(bottom,(g->h-g->py)*g->scale);
  }
  float k=fminf(w/(right-left),h/(bottom-top));
  /* Centre the union, rather than the face, to keep the shadow in bounds. */
  sheet_glyph_centred(ch,font,k,
      cx-((left+right)*.5f-(face->w*.5f-face->px)*face->scale)*k,
      cy-((top+bottom)*.5f-(face->h*.5f-face->py)*face->scale)*k,colour);
}

/* A vertical bar from the sheet's '|'  (the scroll bar, OM 2026-11-01): its
   face spans y0..y1 at `width` px wide, centred on x. Each layer is cut in
   three, its round caps (half its width tall) kept and the middle stretched,
   so the ends and the baked outline keep their shape at any length. */
static inline void sheet_bar(float x, float y0, float y1, float width,
                             ImU32 colour, int font) {
  if (!wyrm_high_visibility_text) {
    if (width > 0 && y1 > y0)
      ImDrawList_AddLine(igGetWindowDrawList(), (ImVec2){x,y0}, (ImVec2){x,y1}, colour, width);
    return;
  }
  if (!wyrm_text_normal || !wyrm_text_add || !(colour & 0xFF000000u) ||
      y1 <= y0 || width <= 0.0f) return;
  const original_glyph* face = &ORIGINAL_GLYPHS[font + ('|' - 32) * 3];
  float k = width / (face->w * face->scale);
  float fs = k * face->scale;
  float stretch = (y1 - y0) - face->h * fs; /* extra length over the glyph */
  float ox = x - face->w * fs * .5f + face->px * fs;
  float oy = y0 + face->py * fs;
  vec4s tint = sheet_tint(colour, 1.0f);
  for (int pass = 0; pass < 2; ++pass) {
    const original_glyph* g = face + (pass == 0 ? 1 : 0);
    if (g->w <= 1) continue;
    spr_renderer* batch = pass == 0 ? wyrm_text_normal : wyrm_text_add;
    float s = k * g->scale;
    float lx = ox - g->px * s, ly = oy - g->py * s;
    float cap = g->w * .5f;
    if (cap * 2.0f > g->h) cap = g->h * .5f;
    float mid = g->h - cap * 2.0f;
    float mid_h = mid * s + stretch;
    if (mid_h < 0.0f) {
      /* Shorter than the glyph: the caps share what there is. */
      float c = (g->h * s + stretch) * .5f;
      if (c <= 0.0f) continue;
      sheet_quad(batch, lx, ly, g->w * s, c, g->x, g->y, g->w, cap, tint);
      sheet_quad(batch, lx, ly + c, g->w * s, c, g->x, g->y + g->h - cap, g->w, cap, tint);
      continue;
    }
    sheet_quad(batch, lx, ly, g->w * s, cap * s, g->x, g->y, g->w, cap, tint);
    sheet_quad(batch, lx, ly + cap * s, g->w * s, mid_h, g->x, g->y + cap, g->w, mid, tint);
    sheet_quad(batch, lx, ly + cap * s + mid_h, g->w * s, cap * s,
               g->x, g->y + g->h - cap, g->w, cap, tint);
  }
}

/* The same signature as ImGui, for engine roster/chat call sites. Wrapping
   is done by the caller using sheet advances, never ImGui's ASCII widths. */
static inline void sheet_add_text(ImDrawList* draw, ImFont* font, float size,
    ImVec2 pos, ImU32 colour, const char* text, const char* end,
    float wrap, const ImVec4* clip) {
  if (!text) return;
  char run[2048];
  if (end) {
    size_t n = (size_t)(end-text);
    if (n >= sizeof(run)) { return; }
    memcpy(run, text, n); run[n] = 0; text = run;
  }
  /* Chat in the baked letters even with a stray character (OM, 2026-11-08):
     NTL's &nbsp; arrives as U+00A0 and phones type curly quotes, and one of
     those used to drop the whole line to the app font. They become their
     ASCII twins; anything else that is not ASCII (an emoji) is drawn in the
     app font between baked runs. */
  char clean[2048];
  size_t o = 0;
  for (const unsigned char* p = (const unsigned char*)text; *p && o + 4 < sizeof(clean);) {
    if (p[0] == 0xC2 && p[1] == 0xA0) { clean[o++] = ' '; p += 2; continue; }
    if (p[0] == 0xE2 && p[1] == 0x80) {
      unsigned char c = p[2];
      if (c == 0x98 || c == 0x99) { clean[o++] = '\''; p += 3; continue; }
      if (c == 0x9C || c == 0x9D) { clean[o++] = '"'; p += 3; continue; }
      if (c == 0x93 || c == 0x94) { clean[o++] = '-'; p += 3; continue; }
      if (c == 0xA6) { clean[o++] = '.'; clean[o++] = '.'; clean[o++] = '.'; p += 3; continue; }
    }
    clean[o++] = (char)*p++;
  }
  clean[o] = 0;
  if (sheet_ok(clean)) { sheet_layer(draw,clean,size,pos,colour,SHEET_THIN,0); return; }
  if (!sheet_ok("a") || !font) {
    ImDrawList_AddText_FontPtr(draw,font,size,pos,colour,text,NULL,wrap,clip);
    return;
  }
  float x = pos.x;
  const char* p = clean;
  while (*p) {
    bool ascii = (unsigned char)*p >= 32 && (unsigned char)*p <= 126;
    const char* q = p;
    while (*q && (((unsigned char)*q >= 32 && (unsigned char)*q <= 126) == ascii)) q++;
    char piece[2048];
    size_t n = (size_t)(q - p);
    memcpy(piece, p, n);
    piece[n] = 0;
    if (ascii) {
      sheet_layer(draw, piece, size, (ImVec2){x, pos.y}, colour, SHEET_THIN, 0);
      x += sheet_width(piece, size, SHEET_THIN);
    } else {
      ImVec2 extent;
      ImFont_CalcTextSizeA(&extent, font, size, 1e9f, 0.0f, piece, NULL, NULL);
      ImDrawList_AddText_FontPtr(draw, font, size, (ImVec2){x, pos.y}, colour, piece, NULL, 0.0f, clip);
      x += extent.x;
    }
    p = q;
  }
}
#endif
