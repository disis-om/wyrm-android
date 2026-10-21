#include <string.h>

#include "../game/backgrounds.h"
#include "../game/studio.h"
#include <external/stb/stb_image.h>
#include "../user.h"
#ifdef WYRM_SDL_JNI
#include "../platform/android_startup.h"
#include "../platform/android_arrows.h"
#include "../platform/android_look.h"
#endif

#define BACKGROUND_LOGICAL_WIDTH 4096.0f
#define BACKGROUND_LOGICAL_HEIGHT 3548.0f

/* The arena floor's background swap (below renderer_set_background). */
static void renderer_bg_swap_step(renderer* r, tcontext* ctx);
static void renderer_bg_swap_destroy(renderer* r, tcontext* ctx);
/* Skin Studio's atlas swap (below renderer_set_studio). */
static void renderer_studio_swap_step(renderer* r, tcontext* ctx);
static void renderer_studio_swap_destroy(renderer* r, tcontext* ctx);

void _create_image_data(renderer* r, tcontext* ctx, ivec2 size) {
  glm_ivec2_copy(size, r->size);
  for (int i = 0; i < ctx->fif; i++) {
    vmaCreateImage(
        ctx->allocator,
        &(VkImageCreateInfo){
            .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
            .pNext = NULL,
            .flags = 0,
            .imageType = VK_IMAGE_TYPE_2D,
            .format = VK_FORMAT_R8G8B8A8_UNORM,
            .extent = {.width = size[0], .height = size[1], .depth = 1},
            .mipLevels = 1,
            .arrayLayers = 1,
            .samples = VK_SAMPLE_COUNT_1_BIT,
            .tiling = VK_IMAGE_TILING_OPTIMAL,
            .usage = VK_IMAGE_USAGE_SAMPLED_BIT |
                     VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
            .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
            .queueFamilyIndexCount = 0,
            .pQueueFamilyIndices = NULL,
            .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED},
        &(VmaAllocationCreateInfo){
            .flags = VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT,
            .usage = VMA_MEMORY_USAGE_AUTO,
            .priority = 1.0f},
        &r->images[i].color, &r->images[i].memory, NULL);
    vkCreateImageView(
        ctx->device,
        &(VkImageViewCreateInfo){
            .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
            .pNext = NULL,
            .flags = 0,
            .image = r->images[i].color,
            .viewType = VK_IMAGE_VIEW_TYPE_2D,
            .format = VK_FORMAT_R8G8B8A8_UNORM,
            .components = {.r = VK_COMPONENT_SWIZZLE_IDENTITY,
                           .g = VK_COMPONENT_SWIZZLE_IDENTITY,
                           .b = VK_COMPONENT_SWIZZLE_IDENTITY,
                           .a = VK_COMPONENT_SWIZZLE_IDENTITY},
            .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                                 .baseMipLevel = 0,
                                 .levelCount = 1,
                                 .baseArrayLayer = 0,
                                 .layerCount = 1}},
        NULL, &r->images[i].color_view);

    vkCreateImageView(
        ctx->device,
        &(VkImageViewCreateInfo){
            .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
            .pNext = NULL,
            .flags = 0,
            .image = r->images[i].color,
            .viewType = VK_IMAGE_VIEW_TYPE_2D,
            .format = VK_FORMAT_R8G8B8A8_UNORM,
            .components = {.r = VK_COMPONENT_SWIZZLE_R,
                           .g = VK_COMPONENT_SWIZZLE_G,
                           .b = VK_COMPONENT_SWIZZLE_B,
                           .a = VK_COMPONENT_SWIZZLE_ONE},
            .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                                 .baseMipLevel = 0,
                                 .levelCount = 1,
                                 .baseArrayLayer = 0,
                                 .layerCount = 1}},
        NULL, &r->images[i].color_view_alpha);

    vkCreateFramebuffer(ctx->device,
                        &(VkFramebufferCreateInfo){
                            .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO,
                            .pNext = NULL,
                            .flags = 0,
                            .renderPass = r->render_pass,
                            .attachmentCount = 1,
                            .pAttachments = &r->images[i].color_view,
                            .width = size[0],
                            .height = size[1],
                            .layers = 1},
                        NULL, &r->images[i].framebuffer);
  }
}

void _destroy_image_data(renderer* r, tcontext* ctx) {
  for (int i = 0; i < ctx->fif; i++) {
    vkDestroyFramebuffer(ctx->device, r->images[i].framebuffer, NULL);
    vkDestroyImageView(ctx->device, r->images[i].color_view_alpha, NULL);
    vkDestroyImageView(ctx->device, r->images[i].color_view, NULL);
    vmaDestroyImage(ctx->allocator, r->images[i].color, r->images[i].memory);
  }
}

renderer* renderer_create(tenv* env) {
  tcontext* ctx = env->ctx;
  tuser_data* usr = env->usr;
  renderer* r = calloc(1, sizeof(renderer));
#ifdef WYRM_SDL_JNI
  android_startup_stage(8, "Uploading arena textures",
                        "Preparing background, sprite atlas and brand assets");
#endif

  vkCreateSampler(
      ctx->device,
      &(VkSamplerCreateInfo){.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO,
                             .pNext = NULL,
                             .flags = 0,
                             .magFilter = VK_FILTER_LINEAR,
                             .minFilter = VK_FILTER_LINEAR,
                             .mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR,
                             .addressModeU = VK_SAMPLER_ADDRESS_MODE_REPEAT,
                             .addressModeV = VK_SAMPLER_ADDRESS_MODE_REPEAT,
                             .addressModeW = VK_SAMPLER_ADDRESS_MODE_REPEAT,
                             .maxLod = VK_LOD_CLAMP_NONE},
      NULL, &r->linear_sampler);

  // The sprite atlas is tightly packed and has no per-cell mip gutters. Keep
  // linear filtering inside the base image but lock sampling to LOD 0 so
  // distant sprites cannot pick up colors from neighboring atlas cells.
  vkCreateSampler(
      ctx->device,
      &(VkSamplerCreateInfo){.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO,
                             .pNext = NULL,
                             .flags = 0,
                             .magFilter = VK_FILTER_LINEAR,
                             .minFilter = VK_FILTER_LINEAR,
                             .mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST,
                             .addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                             .addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                             .addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                             .minLod = 0.0f,
                             .maxLod = 0.0f},
      NULL, &r->atlas_sampler);

  /* Beads at the size they are drawn (OM, 2026-10-18). The game atlas was
     sampled at LOD 0 only, so every bead of a zoomed-out crowd read the full
     3136 x 4032 atlas: on the Moto (Adreno 619) the arena pass went from
     5 ms with 250 beads to 21 ms with 1,900 (frame probe), while the
     reference client mipmaps the same kind of atlas. The cells stay clean
     because they line up with every level used: bead cells are 448 px
     (7 x 64) and the accessory cells 112 px (7 x 16), so up to level 4 a
     cell never shares a texel with its neighbour (beads 28 px, accessories
     7 px there). Level 4 is also about the smallest a bead is drawn, so the
     cap costs nothing in speed. The small negative bias keeps near beads
     crisp; the bead shader masks each cell to its circle anyway. */
  vkCreateSampler(
      ctx->device,
      &(VkSamplerCreateInfo){.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO,
                             .pNext = NULL,
                             .flags = 0,
                             .magFilter = VK_FILTER_LINEAR,
                             .minFilter = VK_FILTER_LINEAR,
                             .mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR,
                             .addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                             .addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                             .addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                             .mipLodBias = BEAD_ATLAS_LOD_BIAS,
                             .minLod = 0.0f,
                             .maxLod = BEAD_ATLAS_MAX_LOD},
      NULL, &r->bead_sampler);

  vkCreateSampler(ctx->device,
                  &(VkSamplerCreateInfo){
                      .sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO,
                      .pNext = NULL,
                      .flags = 0,
                      .magFilter = VK_FILTER_NEAREST,
                      .minFilter = VK_FILTER_NEAREST,
                      .mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST,
                      .addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER,
                      .addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER,
                      .addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER,
                      .borderColor = VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK,
                      .maxLod = VK_LOD_CLAMP_NONE},
                  NULL, &r->nearest_sampler);

  /* Whatever the player last chose. Only one is ever resident: swapping is a
     settings action, not a per-frame one, so paying a load for it is cheaper
     than keeping twenty of these in video memory. */
  r->background = background_clamp(env->usr->usrs.arena_background);
  const background_def* bg = &BACKGROUNDS[r->background];
  r->bg_tex = create_mipmap_texture(
      ctx, bg->path ? bg->path : "app/res/textures/background_4k.png");
#ifdef WYRM_SDL_JNI
  android_startup_texture("background", r->bg_tex->size[0], r->bg_tex->size[1],
                          1, 4);
#endif
  r->tex_atlas = create_mipmap_texture(ctx, "app/res/textures/tex_atlas_8k.png");
#ifdef WYRM_SDL_JNI
  android_startup_texture("atlas", r->tex_atlas->size[0], r->tex_atlas->size[1],
                          2, 4);
#endif
  r->logo_tex =
      create_mipmap_texture(ctx, "app/res/textures/wyrm_logo.png");
#ifdef WYRM_SDL_JNI
  android_startup_texture("logo", r->logo_tex->size[0], r->logo_tex->size[1],
                          3, 4);
#endif
  r->logo_descriptor = igImplVulkan_AddTexture(
      r->atlas_sampler, r->logo_tex->view,
      VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
  r->home_icons_tex =
      create_mipmap_texture(ctx, "app/res/textures/home_navigation_icons.png");
#ifdef WYRM_SDL_JNI
  android_startup_texture("home-icons", r->home_icons_tex->size[0],
                          r->home_icons_tex->size[1], 4, 4);
#endif
  r->home_icons_descriptor = igImplVulkan_AddTexture(
      r->linear_sampler, r->home_icons_tex->view,
      VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

  r->tags_tex = create_mipmap_texture(ctx, "app/res/textures/wyrm_tags.png");
  r->tags_descriptor = igImplVulkan_AddTexture(
      r->linear_sampler, r->tags_tex->view,
      VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
  android_arrows_create(r, ctx);
  android_look_create(r, ctx);

  vkCreateRenderPass(
      ctx->device,
      &(VkRenderPassCreateInfo){
          .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
          .pNext = NULL,
          .flags = 0,
          .attachmentCount = 1,
          .pAttachments =
              &(VkAttachmentDescription){
                  .flags = 0,
                  .format = VK_FORMAT_R8G8B8A8_UNORM,
                  .samples = VK_SAMPLE_COUNT_1_BIT,
                  .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR,
                  .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
                  .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                  .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
                  .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
                  .finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL},
          .subpassCount = 1,
          .pSubpasses =
              &(VkSubpassDescription){
                  .flags = 0,
                  .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
                  .inputAttachmentCount = 0,
                  .pInputAttachments = NULL,
                  .colorAttachmentCount = 1,
                  .pColorAttachments =
                      &(VkAttachmentReference){
                          .attachment = 0,
                          .layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL}},
          .dependencyCount = 0,
          .pDependencies = NULL},
      NULL, &r->render_pass);

  r->images = malloc(sizeof(renderer_image_data) * ctx->fif);
  _create_image_data(r, ctx, ctx->size);
  vec2 quad_data[4] = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
  r->quad_buffer = tbuffer_create(ctx, quad_data, sizeof(quad_data),
                                  VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);

  vkCreateDescriptorSetLayout(
      ctx->device,
      &(VkDescriptorSetLayoutCreateInfo){
          .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
          .pNext = NULL,
          .flags = 0,
          .bindingCount = 5,
          .pBindings =
              (VkDescriptorSetLayoutBinding[]){
                  {.binding = 0,
                   .descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                   .descriptorCount = 1,
                   .stageFlags = VK_SHADER_STAGE_VERTEX_BIT |
                                 VK_SHADER_STAGE_FRAGMENT_BIT},
                  {.binding = 1,
                   .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                   .descriptorCount = 1,
                   .stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT},
                  {.binding = 2,
                   .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                   .descriptorCount = 1,
                   .stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT},
                  {.binding = 3,
                   .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                   .descriptorCount = 1,
                   .stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT},
                  /* Skin Studio's atlas (OM, 2026-10-12). */
                  {.binding = 4,
                   .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                   .descriptorCount = 1,
                   .stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT}}},
      NULL, &r->global_set_layout);

  vkCreatePipelineLayout(
      ctx->device,
      &(VkPipelineLayoutCreateInfo){
          .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
          .pNext = NULL,
          .flags = 0,
          .setLayoutCount = 1,
          .pSetLayouts = &r->global_set_layout,
          .pushConstantRangeCount = 0,
          .pPushConstantRanges = NULL},
      NULL, &r->pipeline_layout);

  r->mmr = mm_renderer_create(ctx, r->pipeline_layout, r->render_pass);

  r->global_set = malloc(ctx->fif * sizeof(VkDescriptorSet));
  VkDescriptorSetLayout* layouts =
      malloc(ctx->fif * sizeof(VkDescriptorSetLayout));
  for (int i = 0; i < ctx->fif; i++) {
    layouts[i] = r->global_set_layout;
  }

  vkAllocateDescriptorSets(
      ctx->device,
      &(VkDescriptorSetAllocateInfo){
          .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,
          .pNext = NULL,
          .descriptorPool = ctx->descriptor_pool,
          .descriptorSetCount = ctx->fif,
          .pSetLayouts = layouts},
      r->global_set);
  free(layouts);

  r->global.viewport[0] = r->size[0];
  r->global.viewport[1] = r->size[1];
  // Preserve the original arena tiling scale after the mobile PNG is halved.
  r->global.bg_size[0] = BACKGROUNDS[r->background].tile_w;
  r->global.bg_size[1] = BACKGROUNDS[r->background].tile_h;
  r->global.bg_color[0] = 1;
  r->global.bg_color[1] = 1;
  r->global.bg_scale = 1;
  r->global.bg_color[2] = 1;
  r->global.bd_color[0] = 1;
  r->global.bd_color[1] = 1;
  r->global.bd_color[2] = 1;
  r->global.bg_opacity = 0;
  r->global.bd_opacity = 0;
  r->global.view[0] = 0;
  r->global.view[1] = 0;
  r->global.zoom = 1;
  r->global.bd_radius = 1;
  r->global.grd = 1;
  r->global.max_minimap_size = MAX_MINIMAP_SIZE;
  r->global.minimap_data_size = 136;
  r->global.minimap_circ[0] = 0;
  r->global.minimap_circ[1] = 0;
  r->global.minimap_circ[2] = 256;
  r->global.minimap_opacity = 1;
  r->global.lview[0] = -1;
  r->global.lview[1] = -1;

  r->global_buffer = tdbuffer_create(ctx, &r->global, sizeof(r->global),
                                     VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);

  for (int i = 0; i < ctx->fif; i++)
    vkUpdateDescriptorSets(
        ctx->device, 5,
        (VkWriteDescriptorSet[]){
            {
                .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                .pNext = NULL,
                .dstSet = r->global_set[i],
                .dstBinding = 0,
                .dstArrayElement = 0,
                .descriptorCount = 1,
                .descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                .pBufferInfo =
                    &(VkDescriptorBufferInfo){r->global_buffer[i].handle, 0,
                                              sizeof(r->global)},
            },
            {
                .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                .pNext = NULL,
                .dstSet = r->global_set[i],
                .dstBinding = 1,
                .dstArrayElement = 0,
                .descriptorCount = 1,
                .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                .pImageInfo =
                    &(VkDescriptorImageInfo){
                        r->bead_sampler, r->tex_atlas->view,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL},
            },
            {
                .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                .pNext = NULL,
                .dstSet = r->global_set[i],
                .dstBinding = 2,
                .dstArrayElement = 0,
                .descriptorCount = 1,
                .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                .pImageInfo =
                    &(VkDescriptorImageInfo){
                        r->linear_sampler, r->bg_tex->view,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL},
            },
            {
                .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                .pNext = NULL,
                .dstSet = r->global_set[i],
                .dstBinding = 3,
                .dstArrayElement = 0,
                .descriptorCount = 1,
                .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                .pImageInfo =
                    &(VkDescriptorImageInfo){
                        r->nearest_sampler, r->mmr->minimap_tex[i]->view,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL},
            },
            {
                /* Skin Studio: the game atlas until one is worn (never
                   sampled then; a descriptor must point at something). */
                .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                .pNext = NULL,
                .dstSet = r->global_set[i],
                .dstBinding = 4,
                .dstArrayElement = 0,
                .descriptorCount = 1,
                .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                .pImageInfo =
                    &(VkDescriptorImageInfo){
                        r->bead_sampler, r->tex_atlas->view,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL},
            }},
        0, NULL);

  r->bgr = bg_renderer_create(ctx, r->pipeline_layout, r->render_pass);
  r->bstb = bst_renderer_create(ctx, MAX_BOOST_INSTANCES, r->pipeline_layout, r->render_pass);
  r->bpr = bp_renderer_create(ctx, MAX_SPRITE_INSTANCES, r->pipeline_layout, r->render_pass);
  r->bsta = bst_renderer_create(ctx, MAX_BOOST_INSTANCES, r->pipeline_layout, r->render_pass);
  r->fdr = fd_renderer_create(ctx, MAX_FOOD_INSTANCES, MAX_PREYS, r->pipeline_layout, r->render_pass);
  r->bdr = bd_renderer_create(ctx, r->pipeline_layout, r->render_pass);
  r->astr = bp_renderer_create(ctx, 2, r->pipeline_layout, r->render_pass);
  r->cr = spr_renderer_create(ctx, 1, r->pipeline_layout, env->ctx->renderpass);

#ifdef WYRM_SDL_JNI
  android_startup_stage(9, "Graphics pipelines assembled",
                        "Arena, sprites, food, minimap and UI pipelines are ready");
#endif

  return r;
}

void renderer_render(renderer* r, tcontext* ctx, vec4 clear_color) {
  tcontext_frame* fr = ctx->frames + ctx->current_frame;

  r->global.viewport[0] = r->size[0];
  r->global.viewport[1] = r->size[1];
  if (r->bg_swap) renderer_bg_swap_step(r, ctx);
  if (r->studio_swap) renderer_studio_swap_step(r, ctx);
  memcpy(r->global_buffer[ctx->current_frame].data, &r->global,
         sizeof(r->global));

  mm_renderer_prerender(r->mmr, ctx);

  vkCmdBeginRenderPass(
      fr->cmd,
      &(VkRenderPassBeginInfo){
          .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
          .pNext = NULL,
          .renderPass = r->render_pass,
          .framebuffer = r->images[ctx->current_frame].framebuffer,
          .renderArea = {.offset = {0, 0}, .extent = {r->size[0], r->size[1]}},
          .clearValueCount = 1,
          .pClearValues =
              &(VkClearValue){
                  .color = {.float32 = {clear_color[0], clear_color[1],
                                        clear_color[2], clear_color[3]}}}},
      VK_SUBPASS_CONTENTS_INLINE);
  vkCmdBindVertexBuffers(fr->cmd, 0, 1, &r->quad_buffer->handle,
                         (VkDeviceSize[]){0});

  vkCmdSetViewport(fr->cmd, 0, 1, &(VkViewport){0, 0, r->size[0], r->size[1]});
  vkCmdSetScissor(fr->cmd, 0, 1, &(VkRect2D){{0, 0}, {r->size[0], r->size[1]}});
  vkCmdBindDescriptorSets(fr->cmd, VK_PIPELINE_BIND_POINT_GRAPHICS,
                          r->pipeline_layout, 0, 1,
                          &r->global_set[ctx->current_frame], 0, NULL);
  bg_renderer_render(r->bgr, ctx);
  fd_renderer_render(r->fdr, ctx);
  bst_renderer_render(r->bstb, ctx);
  bp_renderer_render(r->bpr, ctx);
  bst_renderer_render(r->bsta, ctx);
  bd_renderer_render(r->bdr, ctx);
  bp_renderer_render(r->astr, ctx);
  mm_renderer_render(r->mmr, ctx);
  vkCmdEndRenderPass(ctx->frames[ctx->current_frame].cmd);
}

void renderer_render_cursor(renderer* r, tcontext* ctx) {
  tcontext_frame* fr = ctx->frames + ctx->current_frame;
  vkCmdBindVertexBuffers(fr->cmd, 0, 1, &r->quad_buffer->handle,
                         (VkDeviceSize[]){0});

  vkCmdSetViewport(fr->cmd, 0, 1, &(VkViewport){0, 0, r->size[0], r->size[1]});
  vkCmdSetScissor(fr->cmd, 0, 1, &(VkRect2D){{0, 0}, {r->size[0], r->size[1]}});
  vkCmdBindDescriptorSets(fr->cmd, VK_PIPELINE_BIND_POINT_GRAPHICS,
                          r->pipeline_layout, 0, 1,
                          &r->global_set[ctx->current_frame], 0, NULL);

  spr_renderer_render(r->cr, ctx);
}

/**
 * Swaps the arena floor. See renderer.h.
 *
 * "None" keeps a texture bound rather than unbinding one — a descriptor that
 * points at nothing is undefined behaviour in the shader, and the pass is
 * skipped by opacity elsewhere. What it must not do is keep the previous
 * background's tile size, or a later selection would inherit it.
 */
/* 5 (OM, 2026-10-12, from the reference client): the arena floor changes without
   a hitch. The PNG decodes on a worker thread, the GPU copy runs on its own
   fence (texture_upload_*), and each frame in flight takes the new floor in
   its descriptor set once its last use of the old one has finished: the
   current frame (its fence was waited in tcontext_begin) or any frame whose
   fence has signalled. The old floor is freed once every frame that could
   still read it has been waited for. Before, the whole thing ran on the
   engine thread and ended in vkDeviceWaitIdle. Driven by renderer_render(),
   so a floor chosen while nothing is drawn applies with the next frame. */
static void renderer_set_background_now(renderer* r, tcontext* ctx, int index);

typedef struct bg_swap {
  int want;              /* the floor asked for last */
  int job;               /* the floor being decoded or uploaded, -1 none */
  SDL_Thread* thread;
  SDL_AtomicInt decoded; /* 1 once the worker has finished */
  char path[512];
  unsigned char* pixels;
  int w, h;
  texture_upload* upload;
  texture* ready;        /* uploaded, being bound frame by frame */
  int ready_index;
  uint32_t bound;        /* frames whose set already shows `ready` */
  texture* retired;      /* the old floor, until no frame can read it */
  int retire_after;      /* renders left before it is freed */
} bg_swap;

static int bg_swap_decode(void* data) {
  bg_swap* s = data;
  int channels = 0;
  s->pixels = stbi_load(s->path, &s->w, &s->h, &channels, 4);
  SDL_SetAtomicInt(&s->decoded, 1);
  return 0;
}

static void renderer_bind_background(renderer* r, tcontext* ctx, int frame,
                                     texture* tex) {
  vkUpdateDescriptorSets(
      ctx->device, 1,
      (VkWriteDescriptorSet[]){
          {
              .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
              .dstSet = r->global_set[frame],
              .dstBinding = 2,
              .dstArrayElement = 0,
              .descriptorCount = 1,
              .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
              .pImageInfo =
                  &(VkDescriptorImageInfo){r->linear_sampler, tex->view,
                                           VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL},
          },
      },
      0, NULL);
}

static void renderer_bg_swap_step(renderer* r, tcontext* ctx) {
  bg_swap* s = r->bg_swap;
  int cur = ctx->current_frame;
  int fif = ctx->fif < 32 ? ctx->fif : 32;
  uint32_t all = fif >= 32 ? 0xFFFFFFFFu : ((1u << fif) - 1u);

  if (s->retired && --s->retire_after <= 0) {
    destroy_texture(ctx, s->retired);
    s->retired = NULL;
  }

  if (s->ready) {
    for (int i = 0; i < fif; ++i) {
      if (s->bound & (1u << i)) continue;
      if (i != cur &&
          vkGetFenceStatus(ctx->device, ctx->frames[i].wait_fence) != VK_SUCCESS)
        continue;
      renderer_bind_background(r, ctx, i, s->ready);
      s->bound |= 1u << i;
    }
    if ((s->bound & all) == all) {
      s->retired = r->bg_tex;
      s->retire_after = fif + 1;
      r->bg_tex = s->ready;
      r->background = s->ready_index;
      s->ready = NULL;
      s->bound = 0;
    }
  } else if (s->upload) {
    texture* done = NULL;
    if (texture_upload_poll(ctx, s->upload, &done)) {
      s->upload = NULL;
      if (done && s->job == s->want && s->want != r->background) {
        s->ready = done;
        s->ready_index = s->job;
        s->bound = 0;
      } else if (done) {
        destroy_texture(ctx, done); /* chosen again meanwhile, or not needed */
      }
      s->job = -1;
    }
  } else if (s->thread) {
    if (SDL_GetAtomicInt(&s->decoded)) {
      SDL_WaitThread(s->thread, NULL);
      s->thread = NULL;
      if (s->pixels) {
        if (s->job == s->want && s->want != r->background)
          s->upload = texture_upload_begin(ctx, s->pixels, s->w, s->h, true);
        stbi_image_free(s->pixels);
        s->pixels = NULL;
      } else {
        SDL_Log("Wyrm floor: could not decode %s", s->path);
      }
      if (!s->upload) s->job = -1;
    }
  } else if (!s->retired && s->want != r->background) {
    const background_def* bg = &BACKGROUNDS[s->want];
    const char* path = bg->path ? bg->path : BACKGROUNDS[BACKGROUND_WYRM].path;
    snprintf(s->path, sizeof(s->path), "%s", path);
    s->job = s->want;
    SDL_SetAtomicInt(&s->decoded, 0);
    s->thread = SDL_CreateThread(bg_swap_decode, "wyrm-floor", s);
    if (!s->thread) {
      /* No worker: the old way, on this thread. */
      s->job = -1;
      renderer_set_background_now(r, ctx, s->want);
    }
  }

  /* This frame's tile size follows the floor its own set shows. */
  int shown = (s->ready && (s->bound & (1u << cur))) ? s->ready_index
                                                     : r->background;
  r->global.bg_size[0] = BACKGROUNDS[shown].tile_w;
  r->global.bg_size[1] = BACKGROUNDS[shown].tile_h;
}

static void renderer_bg_swap_destroy(renderer* r, tcontext* ctx) {
  bg_swap* s = r->bg_swap;
  if (!s) return;
  if (s->thread) SDL_WaitThread(s->thread, NULL);
  if (s->pixels) stbi_image_free(s->pixels);
  if (s->upload) texture_upload_cancel(ctx, s->upload);
  if (s->ready) destroy_texture(ctx, s->ready);
  if (s->retired) destroy_texture(ctx, s->retired);
  free(s);
  r->bg_swap = NULL;
}

void renderer_set_background(renderer* r, tcontext* ctx, int index) {
  index = background_clamp(index);
  if (!r->bg_swap) {
    if (index == r->background) return;
    r->bg_swap = calloc(1, sizeof(bg_swap));
    if (!r->bg_swap) {
      renderer_set_background_now(r, ctx, index);
      return;
    }
    r->bg_swap->job = -1;
    r->bg_swap->ready_index = -1;
  }
  r->bg_swap->want = index;
}

static void renderer_set_background_now(renderer* r, tcontext* ctx, int index) {
  index = background_clamp(index);
  if (index == r->background) return;
  const background_def* bg = &BACKGROUNDS[index];
  const char* path = bg->path ? bg->path : BACKGROUNDS[BACKGROUND_WYRM].path;

  texture* next = create_mipmap_texture(ctx, path);
  if (!next) return; /* keep what is on screen rather than showing nothing */

  /* Frames already recorded may still read the old image. */
  vkDeviceWaitIdle(ctx->device);
  destroy_texture(ctx, r->bg_tex);
  r->bg_tex = next;
  r->background = index;
  r->global.bg_size[0] = bg->tile_w;
  r->global.bg_size[1] = bg->tile_h;

  for (int i = 0; i < ctx->fif; i++)
    vkUpdateDescriptorSets(
        ctx->device, 1,
        (VkWriteDescriptorSet[]){
            {
                .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                .pNext = NULL,
                .dstSet = r->global_set[i],
                .dstBinding = 2,
                .dstArrayElement = 0,
                .descriptorCount = 1,
                .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                .pImageInfo =
                    &(VkDescriptorImageInfo){
                        r->linear_sampler, r->bg_tex->view,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL},
            },
        },
        0, NULL);
}

/* Skin Studio (OM, 2026-10-12): the player's own atlas, swapped the way the
   arena floor is (renderer_bg_swap_step): the PNG decodes on a worker
   thread, the GPU copy runs on its own fence, and each frame in flight takes
   the new atlas on binding 4 once its last use of the old one has finished.
   The skin that goes with it is worn (studio_commit) only when every frame
   shows the new atlas, so a pattern never points into an old one. A newer
   request supersedes an older one at every step. */
typedef struct studio_swap {
  int want;                 /* the newest request, by revision */
  char want_path[512];      /* "" = off */
  studio_skin want_skin;
  int job;                  /* the revision decoding or uploading, -1 none */
  studio_skin job_skin;
  char path[512];
  SDL_Thread* thread;
  SDL_AtomicInt decoded;
  unsigned char* pixels;
  int w, h;
  texture_upload* upload;
  texture* ready;           /* uploaded, being bound frame by frame */
  int ready_rev;
  studio_skin ready_skin;
  uint32_t bound;
  texture* retired;         /* the old atlas, until no frame can read it */
  int retire_after;
  int shown;                /* the revision worn now, 0 none */
} studio_swap;

static int studio_swap_decode(void* data) {
  studio_swap* s = data;
  int channels = 0;
  s->pixels = stbi_load(s->path, &s->w, &s->h, &channels, 4);
  SDL_SetAtomicInt(&s->decoded, 1);
  return 0;
}

static void renderer_bind_studio(renderer* r, tcontext* ctx, int frame,
                                 texture* tex) {
  vkUpdateDescriptorSets(
      ctx->device, 1,
      (VkWriteDescriptorSet[]){
          {
              .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
              .dstSet = r->global_set[frame],
              .dstBinding = 4,
              .dstArrayElement = 0,
              .descriptorCount = 1,
              .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
              .pImageInfo =
                  /* The Studio atlas is 8 x 8 cells of 128 px, clean
                     on every level the bead sampler reads (2026-10-18). */
                  &(VkDescriptorImageInfo){r->bead_sampler, tex->view,
                                           VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL},
          },
      },
      0, NULL);
}

static void renderer_studio_swap_step(renderer* r, tcontext* ctx) {
  studio_swap* s = r->studio_swap;
  int cur = ctx->current_frame;
  int fif = ctx->fif < 32 ? ctx->fif : 32;
  uint32_t all = fif >= 32 ? 0xFFFFFFFFu : ((1u << fif) - 1u);

  if (s->retired && --s->retire_after <= 0) {
    destroy_texture(ctx, s->retired);
    s->retired = NULL;
  }

  if (s->ready) {
    for (int i = 0; i < fif; ++i) {
      if (s->bound & (1u << i)) continue;
      if (i != cur &&
          vkGetFenceStatus(ctx->device, ctx->frames[i].wait_fence) != VK_SUCCESS)
        continue;
      renderer_bind_studio(r, ctx, i, s->ready);
      s->bound |= 1u << i;
    }
    if ((s->bound & all) == all) {
      if (r->studio_tex) {
        s->retired = r->studio_tex;
        s->retire_after = fif + 1;
      }
      r->studio_tex = s->ready;
      if (s->ready_rev == s->want && s->want_path[0]) {
        studio_commit(&s->ready_skin);
        s->shown = s->ready_rev;
      }
      s->ready = NULL;
      s->bound = 0;
    }
  } else if (s->upload) {
    texture* done = NULL;
    if (texture_upload_poll(ctx, s->upload, &done)) {
      s->upload = NULL;
      if (done && s->job == s->want && s->want_path[0]) {
        s->ready = done;
        s->ready_rev = s->job;
        s->ready_skin = s->job_skin;
        s->bound = 0;
      } else if (done) {
        destroy_texture(ctx, done); /* asked again meanwhile, or turned off */
      }
      s->job = -1;
    }
  } else if (s->thread) {
    if (SDL_GetAtomicInt(&s->decoded)) {
      SDL_WaitThread(s->thread, NULL);
      s->thread = NULL;
      if (s->pixels) {
        if (s->job == s->want && s->want_path[0])
          s->upload = texture_upload_begin(ctx, s->pixels, s->w, s->h, false);
        stbi_image_free(s->pixels);
        s->pixels = NULL;
      } else {
        SDL_Log("Wyrm studio: could not decode %s", s->path);
      }
      if (!s->upload) s->job = -1;
    }
  } else if (!s->retired && s->want_path[0] && s->want != s->shown) {
    snprintf(s->path, sizeof(s->path), "%s", s->want_path);
    s->job = s->want;
    s->job_skin = s->want_skin;
    SDL_SetAtomicInt(&s->decoded, 0);
    s->thread = SDL_CreateThread(studio_swap_decode, "wyrm-studio", s);
    if (!s->thread) {
      SDL_Log("Wyrm studio: no worker thread");
      s->job = -1;
      s->shown = s->want; /* do not retry every frame */
    }
  }
}

static void renderer_studio_swap_destroy(renderer* r, tcontext* ctx) {
  studio_swap* s = r->studio_swap;
  if (!s) return;
  if (s->thread) SDL_WaitThread(s->thread, NULL);
  if (s->pixels) stbi_image_free(s->pixels);
  if (s->upload) texture_upload_cancel(ctx, s->upload);
  if (s->ready) destroy_texture(ctx, s->ready);
  if (s->retired) destroy_texture(ctx, s->retired);
  free(s);
  r->studio_swap = NULL;
}

void renderer_set_studio(renderer* r, tcontext* ctx, const char* path,
                         const studio_skin* skin) {
  (void)ctx;
  if (!r) return;
  if (!r->studio_swap) {
    r->studio_swap = calloc(1, sizeof(studio_swap));
    if (!r->studio_swap) return;
    r->studio_swap->job = -1;
    r->studio_swap->ready_rev = -1;
  }
  studio_swap* s = r->studio_swap;
  s->want += 1;
  if (!path || !path[0] || !skin) {
    s->want_path[0] = 0;
    s->shown = 0;
    studio_clear();
    return;
  }
  snprintf(s->want_path, sizeof(s->want_path), "%s", path);
  s->want_skin = *skin;
}

void renderer_destroy(renderer* r, tcontext* ctx) {
  spr_renderer_destroy(r->cr, ctx);
  bd_renderer_destroy(r->bdr, ctx);
  bp_renderer_destroy(r->astr, ctx);
  bst_renderer_destroy(r->bsta, ctx);
  bp_renderer_destroy(r->bpr, ctx);
  bst_renderer_destroy(r->bstb, ctx);
  fd_renderer_destroy(r->fdr, ctx);
  bg_renderer_destroy(r->bgr, ctx);
  tdbuffer_destroy(ctx, r->global_buffer);
  free(r->global_set);
  mm_renderer_destroy(r->mmr, ctx);
  vkDestroyPipelineLayout(ctx->device, r->pipeline_layout, NULL);
  vkDestroyDescriptorSetLayout(ctx->device, r->global_set_layout, NULL);
  tbuffer_destroy(ctx, r->quad_buffer);
  _destroy_image_data(r, ctx);
  vkDestroyRenderPass(ctx->device, r->render_pass, NULL);
  free(r->images);
  if (r->logo_descriptor)
    igImplVulkan_RemoveTexture(r->logo_descriptor);
  if (r->home_icons_descriptor)
    igImplVulkan_RemoveTexture(r->home_icons_descriptor);
  android_arrows_destroy(ctx);
  android_look_destroy(ctx);
  if (r->tags_descriptor) igImplVulkan_RemoveTexture(r->tags_descriptor);
  destroy_texture(ctx, r->tags_tex);
  destroy_texture(ctx, r->home_icons_tex);
  destroy_texture(ctx, r->logo_tex);
  renderer_bg_swap_destroy(r, ctx);
  renderer_studio_swap_destroy(r, ctx);
  if (r->studio_tex) destroy_texture(ctx, r->studio_tex);
  destroy_texture(ctx, r->tex_atlas);
  destroy_texture(ctx, r->bg_tex);
  vkDestroySampler(ctx->device, r->nearest_sampler, NULL);
  vkDestroySampler(ctx->device, r->atlas_sampler, NULL);
  vkDestroySampler(ctx->device, r->bead_sampler, NULL);
  vkDestroySampler(ctx->device, r->linear_sampler, NULL);
  free(r);
}

void renderer_resize(renderer* r, tcontext* ctx, ivec2 size) {
  vkQueueWaitIdle(ctx->queue);
  _destroy_image_data(r, ctx);
  _create_image_data(r, ctx, size);
}

void renderer_clear_instances(renderer* r) {
  r->fdr->num_instances = 0;
  r->fdr->num_p_instances = 0;
  r->bsta->num_instances = 0;
  r->bpr->num_instances = 0;
  r->bstb->num_instances = 0;
  r->astr->num_instances = 0;
  r->cr->num_instances = 0;
}
