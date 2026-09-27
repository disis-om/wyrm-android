/* Image arrow skins, the same twenty the iOS build draws.
 *
 * The engine draws its five arrow styles as polygons. The image skins live in
 * one right-facing atlas (app/res/textures/arrow_skins.png, 5 x 4 cells of
 * 256 px, identical to the iOS ArrowSkins.png) uploaded once beside the tag
 * atlas. Drawing one is a single rotated ImGui quad. The choice and brightness
 * come from Compose through atomics; nothing here touches steering, input or
 * the arena connection.
 */
#ifdef VLITHER_ANDROID
#include "android_arrows.h"

#include <jni.h>
#include <stdatomic.h>
#include <SDL3/SDL.h>

#include "../user.h"

#define ARROW_ATLAS_COLUMNS 5
#define ARROW_ATLAS_ROWS 4
#define ARROW_SKIN_COUNT 20

static texture* arrow_texture;
static VkDescriptorSet arrow_descriptor;
static atomic_int arrow_skin = -1;           /* -1: the engine's own polygon */
static _Atomic float arrow_brightness = 1.0f; /* 0.2 … 1.0, multiplies colour */

JNIEXPORT void JNICALL
Java_com_wyrm_omrajput_WyrmActivity_nativeSetArrowSkin(JNIEnv* env, jclass clazz,
                                                       jint skin,
                                                       jfloat brightness) {
  (void)env;
  (void)clazz;
  int value = (int)skin;
  float b = (float)brightness;
  if (value < -1 || value >= ARROW_SKIN_COUNT) value = -1;
  if (!(b >= 0.2f)) b = 0.2f;
  if (b > 1.0f) b = 1.0f;
  atomic_store(&arrow_skin, value);
  atomic_store(&arrow_brightness, b);
}

float android_arrow_brightness(void) { return atomic_load(&arrow_brightness); }

void android_arrows_create(renderer* r, tcontext* ctx) {
  if (!r || !ctx || arrow_texture) return;
  arrow_texture = create_mipmap_texture(ctx, "app/res/textures/arrow_skins.png");
  /* A failed decode comes back as the engine's 2 x 2 fallback. */
  if (!arrow_texture || arrow_texture->size[0] < 64) {
    SDL_Log("Wyrm arrows: atlas unreadable; image arrows off");
    return;
  }
  /* LOD 0 only: the atlas has no gutters, so a smaller mip level would blend
     neighbouring arrows into this one and soften its colours. */
  arrow_descriptor = igImplVulkan_AddTexture(
      r->atlas_sampler, arrow_texture->view,
      VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
}

void android_arrows_destroy(tcontext* ctx) {
  if (arrow_descriptor) igImplVulkan_RemoveTexture(arrow_descriptor);
  arrow_descriptor = VK_NULL_HANDLE;
  if (arrow_texture && ctx) destroy_texture(ctx, arrow_texture);
  arrow_texture = NULL;
}

/* Called from draw_arrow with the geometry it already computed. Returns true
   when an image skin was drawn in place of the polygon. The square is sized
   from the arrow's own length so the Arrow size slider governs both kinds.
   `fade` is only the arrow's own fade: an image keeps its real colours rather
   than taking the controls opacity, which washed it into the arena. */
bool android_arrows_draw(ImDrawList* dl, float ax, float ay, float dx, float dy,
                         float length, float fade) {
  int skin = atomic_load(&arrow_skin);
  if (skin < 0 || !arrow_descriptor || !dl) return false;
  float half = length * 0.72f;
  float px = -dy, py = dx;
  ImVec2 corners[4] = {
      {ax - dx * half - px * half, ay - dy * half - py * half},
      {ax + dx * half - px * half, ay + dy * half - py * half},
      {ax + dx * half + px * half, ay + dy * half + py * half},
      {ax - dx * half + px * half, ay - dy * half + py * half},
  };
  float u0 = (float)(skin % ARROW_ATLAS_COLUMNS) / ARROW_ATLAS_COLUMNS;
  float v0 = (float)(skin / ARROW_ATLAS_COLUMNS) / ARROW_ATLAS_ROWS;
  float u1 = u0 + 1.0f / ARROW_ATLAS_COLUMNS;
  float v1 = v0 + 1.0f / ARROW_ATLAS_ROWS;
  float b = atomic_load(&arrow_brightness);
  if (fade > 1.0f) fade = 1.0f;
  ImTextureRef tex = {NULL, (ImTextureID)(uintptr_t)arrow_descriptor};
  ImDrawList_AddImageQuad(dl, tex, corners[0], corners[1], corners[2], corners[3],
                          (ImVec2){u0, v0}, (ImVec2){u1, v0}, (ImVec2){u1, v1},
                          (ImVec2){u0, v1},
                          igColorConvertFloat4ToU32((ImVec4){b, b, b, fade}));
  return true;
}
#endif
