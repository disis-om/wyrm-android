#ifndef RENDERER_H
#define RENDERER_H

#include <stdalign.h>

#include "bg_renderer.h"
#include "fd_renderer.h"
#include "bp_renderer.h"
#include "bst_renderer.h"
#include "bd_renderer.h"
#include "mm_renderer.h"
#include "spr_renderer.h"
#include "texture.h"

typedef struct renderer_image_data {
  VkFramebuffer framebuffer;
  VkImage color;
  VkImageView color_view;
  VkImageView color_view_alpha;
  VmaAllocation memory;
} renderer_image_data;

typedef struct renderer {
  struct {
    alignas(8) vec2 viewport;
    alignas(8) vec2 bg_size;
    alignas(16) vec3 bg_color;
    alignas(16) vec3 bd_color;
    alignas(16) vec3 minimap_circ;
    alignas(8) vec2 view;
    alignas(8) vec2 lview;
    alignas(4) float zoom;
    alignas(4) float bg_opacity;
    alignas(4) float bg_scale;
    alignas(4) float bd_opacity;
    alignas(4) float bd_radius;
    alignas(4) float grd;
    alignas(4) float max_minimap_size;
    alignas(4) float minimap_data_size;
    alignas(4) float minimap_opacity;
  } global;

  texture* tex_atlas;
  texture* bg_tex;
  int background;
  /* An arena floor change in progress (OM, 2026-10-12): renderer.c. */
  struct bg_swap* bg_swap;
  /* Skin Studio (OM, 2026-10-12): the player's own atlas on binding 4, and
     its change in progress. NULL until one is worn (binding 4 then points
     at the game atlas, never read). */
  texture* studio_tex;
  struct studio_swap* studio_swap;
  texture* logo_tex;
  texture* home_icons_tex;
  /* Every tag on one sheet. Drawn through ImGui rather than through a pipeline
     of its own: a tag is a rotated rectangle over the arena, which a draw list
     does in one call, and the alternative was a whole new shader to say the
     same thing. */
  texture* tags_tex;
  VkDescriptorSet logo_descriptor;
  VkDescriptorSet home_icons_descriptor;
  VkDescriptorSet tags_descriptor;

  VkSampler linear_sampler;
  VkSampler atlas_sampler;
  VkSampler nearest_sampler;

  VkRenderPass render_pass;
  VkDescriptorSetLayout global_set_layout;
  VkDescriptorSet* global_set;
  tdbuffer* global_buffer;
  VkPipelineLayout pipeline_layout;
  renderer_image_data* images;
  ivec2 size;
  tbuffer* quad_buffer;

  bg_renderer* bgr;
  fd_renderer* fdr;
  bst_renderer* bsta;
  bp_renderer* bpr;
  bst_renderer* bstb;
  bd_renderer* bdr;
  bp_renderer* astr;
  mm_renderer* mmr;
  spr_renderer* cr;
} renderer;

renderer* renderer_create(tenv* env);
void renderer_render(renderer* r, tcontext* ctx, vec4 clear_color);
void renderer_render_cursor(renderer* r, tcontext* ctx);
void renderer_destroy(renderer* r, tcontext* ctx);
void renderer_resize(renderer* r, tcontext* ctx, ivec2 size);

/**
 * Swaps the arena floor.
 *
 * Replaces the resident texture and rewrites the descriptor that points at it,
 * so it waits for the device to go idle first — the old image may still be
 * referenced by frames in flight. Safe because this only ever runs when a
 * player picks one, never from the frame loop.
 */
void renderer_set_background(renderer* r, tcontext* ctx, int index);

/* Skin Studio (OM, 2026-10-12): load the atlas at [path] (an RGBA PNG) in
   the background and wear [skin] once every frame shows it. NULL path: off
   at once. Engine thread. */
struct studio_skin;
void renderer_set_studio(renderer* r, tcontext* ctx, const char* path,
                         const struct studio_skin* skin);
void renderer_clear_instances(renderer* r);

#endif
