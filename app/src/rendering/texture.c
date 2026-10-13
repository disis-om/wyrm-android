#include "texture.h"
#include <external/stb/stb_image.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef WYRM_SDL_JNI
#include "../platform/android_startup.h"
#endif

static const char* texture_display_name(const char* filename) {
  if (strstr(filename, "tex_atlas") != NULL) return "sprite atlas";
  if (strstr(filename, "background") != NULL) return "arena background";
  if (strstr(filename, "logo") != NULL) return "brand logo";
  return "texture";
}

static void texture_startup_step(const char* filename, const char* action) {
#ifdef WYRM_SDL_JNI
  char title[128];
  char detail[256];
  const char* name = texture_display_name(filename);
  snprintf(title, sizeof(title), "%s: %s", name, action);
  snprintf(detail, sizeof(detail), "%s (%s)", action, filename);
  android_startup_stage(8, title, detail);
#else
  (void)filename;
  (void)action;
#endif
}

static void texture_terminal_failure(const char* title, const char* detail) {
  SDL_Log("Wyrm: %s: %s", title, detail);
#ifdef WYRM_SDL_JNI
  android_startup_failure(8, title, detail);
  // Keep the Java diagnostic surface alive. Returning an incomplete Vulkan
  // image would only turn the useful error screen into a native crash.
  for (;;) SDL_Delay(1000);
#else
  abort();
#endif
}

texture* create_mipmap_texture(tcontext* ctx, const char* filename) {
  texture* r = calloc(1, sizeof(texture));
  if (!r) texture_terminal_failure("Texture allocation failed",
                                   "Native texture metadata is unavailable");

  VkBuffer staging_buffer;
  VmaAllocation staging_memory;
  VmaAllocationInfo staging_info;

  int w = 0, h = 0, c = 0;
  texture_startup_step(filename, "decoding image data");
  stbi_uc* data = stbi_load(filename, &w, &h, &c, 4);
  stbi_uc fallback[] = {
      28, 35, 39, 255, 50, 214, 151, 255,
      50, 214, 151, 255, 28, 35, 39, 255,
  };
  bool using_fallback = false;
  if (!data || w <= 0 || h <= 0) {
    SDL_Log("Wyrm: texture decode failed for %s: %s; using fallback",
            filename, stbi_failure_reason());
    data = fallback;
    w = 2;
    h = 2;
    c = 4;
    using_fallback = true;
  }
  texture_startup_step(filename, "image decoded");
#ifdef WYRM_SDL_JNI
  VkPhysicalDeviceProperties gpu_properties = {0};
  vkGetPhysicalDeviceProperties(ctx->ph_device, &gpu_properties);
  if ((uint32_t)w > gpu_properties.limits.maxImageDimension2D ||
      (uint32_t)h > gpu_properties.limits.maxImageDimension2D) {
    char detail[320];
    snprintf(detail, sizeof(detail),
             "%s is %d x %d but this GPU supports at most %u px",
             filename, w, h, gpu_properties.limits.maxImageDimension2D);
    android_startup_failure(8, "Texture exceeds GPU limit", detail);
    if (!using_fallback) stbi_image_free(data);
    data = fallback;
    w = 2;
    h = 2;
    c = 4;
    using_fallback = true;
  }
#endif
  bool generate_mipmaps = strstr(filename, "tex_atlas") == NULL;
#ifdef WYRM_SDL_JNI
  // Several Android Mali drivers terminate the device when asked to blit a
  // freshly uploaded image through a full mip chain. Keep the original source
  // resolution, but use a single sampled level on Mali so the upload remains
  // a simple buffer copy followed by one layout transition.
  if (strstr(gpu_properties.deviceName, "Mali") != NULL) {
    generate_mipmaps = false;
    texture_startup_step(filename, "using Mali-safe base-level upload");
  }
#endif
  int mip_levels = generate_mipmaps
                       ? (uint32_t)(floorf(log2f(GLM_MAX(w, h))) + 1)
                       : 1;
  if (generate_mipmaps) {
    VkFormatProperties format_properties = {0};
    vkGetPhysicalDeviceFormatProperties(ctx->ph_device,
                                        VK_FORMAT_R8G8B8A8_UNORM,
                                        &format_properties);
    const VkFormatFeatureFlags required =
        VK_FORMAT_FEATURE_BLIT_SRC_BIT |
        VK_FORMAT_FEATURE_BLIT_DST_BIT |
        VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
    if ((format_properties.optimalTilingFeatures & required) != required) {
      SDL_Log("Wyrm: linear mip blits unavailable for %s; using base level",
              filename);
      mip_levels = 1;
    }
  }
  const VkDeviceSize image_size =
      (VkDeviceSize)w * (VkDeviceSize)h * (VkDeviceSize)4;

  texture_startup_step(filename, "allocating upload memory");
  VkResult result = vmaCreateBuffer(
      ctx->allocator,
      &(VkBufferCreateInfo){.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
                            .pNext = NULL,
                            .flags = 0,
                            .size = image_size,
                            .usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                            .sharingMode = VK_SHARING_MODE_EXCLUSIVE},
      &(VmaAllocationCreateInfo){
          .flags = VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT |
                   VMA_ALLOCATION_CREATE_MAPPED_BIT,
          .usage = VMA_MEMORY_USAGE_AUTO},
      &staging_buffer, &staging_memory, &staging_info);
  if (result != VK_SUCCESS || staging_info.pMappedData == NULL) {
    char detail[256];
    snprintf(detail, sizeof(detail),
             "%s staging buffer failed with Vulkan result %d (%llu bytes)",
             texture_display_name(filename), result,
             (unsigned long long)image_size);
    texture_terminal_failure("Texture upload memory unavailable", detail);
  }

  memcpy(staging_info.pMappedData, data, (size_t)image_size);
  result = vmaFlushAllocation(ctx->allocator, staging_memory, 0, image_size);
  if (result != VK_SUCCESS) {
    char detail[192];
    snprintf(detail, sizeof(detail),
             "%s staging flush failed with Vulkan result %d",
             texture_display_name(filename), result);
    texture_terminal_failure("Texture upload flush failed", detail);
  }
  if (!using_fallback) stbi_image_free(data);

  texture_startup_step(filename, "allocating GPU image");
  result = vmaCreateImage(
      ctx->allocator,
      &(VkImageCreateInfo){.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
                           .pNext = NULL,
                           .flags = 0,
                           .imageType = VK_IMAGE_TYPE_2D,
                           .format = VK_FORMAT_R8G8B8A8_UNORM,
                           .extent = {w, h, 1},
                           .mipLevels = mip_levels,
                           .arrayLayers = 1,
                           .samples = VK_SAMPLE_COUNT_1_BIT,
                           .tiling = VK_IMAGE_TILING_OPTIMAL,
                           .usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT |
                                    VK_IMAGE_USAGE_SAMPLED_BIT |
                                    (mip_levels > 1
                                         ? VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                                         : 0),
                           .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
                           .queueFamilyIndexCount = 0,
                           .pQueueFamilyIndices = NULL,
                           .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED},
      &(VmaAllocationCreateInfo){
          .flags = 0, .usage = VMA_MEMORY_USAGE_AUTO, .priority = 1.0f},
      &r->image, &r->memory, NULL);
  if (result != VK_SUCCESS || r->image == VK_NULL_HANDLE) {
    char detail[256];
    snprintf(detail, sizeof(detail),
             "%s GPU image failed with Vulkan result %d (%d x %d, %d levels)",
             texture_display_name(filename), result, w, h, mip_levels);
    texture_terminal_failure("GPU texture allocation failed", detail);
  }

  texture_startup_step(filename, "recording GPU transfer");
  result = vkResetCommandBuffer(ctx->transfer_cmd, 0);
  if (result != VK_SUCCESS) {
    char detail[160];
    snprintf(detail, sizeof(detail),
             "Transfer command reset failed with Vulkan result %d", result);
    texture_terminal_failure("Texture command reset failed", detail);
  }
  result = vkBeginCommandBuffer(ctx->transfer_cmd,
                       &(VkCommandBufferBeginInfo){
                           .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
                           .pNext = NULL,
                           .flags = 0,
                           .pInheritanceInfo = NULL});
  if (result != VK_SUCCESS) {
    char detail[160];
    snprintf(detail, sizeof(detail),
             "Transfer command begin failed with Vulkan result %d", result);
    texture_terminal_failure("Texture command recording failed", detail);
  }
  vkCmdPipelineBarrier(
      ctx->transfer_cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
      VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1,
      &(VkImageMemoryBarrier){
          .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
          .pNext = NULL,
          .srcAccessMask = VK_ACCESS_NONE,
          .dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
          .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED,
          .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
          .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
          .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
          .image = r->image,
          .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                               .baseMipLevel = 0,
                               .levelCount = 1,
                               .baseArrayLayer = 0,
                               .layerCount = 1}});
  vkCmdCopyBufferToImage(
      ctx->transfer_cmd, staging_buffer, r->image,
      VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &(VkBufferImageCopy){
        .bufferOffset = 0,
        .bufferRowLength = 0,
        .bufferImageHeight = 0,
        .imageSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                             .mipLevel = 0,
                             .baseArrayLayer = 0,
                             .layerCount = 1},
        .imageOffset = {0, 0, 0},
        .imageExtent = {w, h, 1}});

  if (mip_levels > 1) {
    vkCmdPipelineBarrier(
        ctx->transfer_cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1,
        &(VkImageMemoryBarrier){
            .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            .pNext = NULL,
            .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT,
            .oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = r->image,
            .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                                 .baseMipLevel = 0,
                                 .levelCount = 1,
                                 .baseArrayLayer = 0,
                                 .layerCount = 1}});

    for (int i = 1; i < mip_levels; i++) {
      vkCmdPipelineBarrier(
          ctx->transfer_cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
          VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1,
          &(VkImageMemoryBarrier){
              .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
              .pNext = NULL,
              .srcAccessMask = VK_ACCESS_NONE,
              .dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
              .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED,
              .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
              .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
              .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
              .image = r->image,
              .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                                   .baseMipLevel = i,
                                   .levelCount = 1,
                                   .baseArrayLayer = 0,
                                   .layerCount = 1}});

      const int32_t src_w = GLM_MAX(1, w >> (i - 1));
      const int32_t src_h = GLM_MAX(1, h >> (i - 1));
      const int32_t dst_w = GLM_MAX(1, w >> i);
      const int32_t dst_h = GLM_MAX(1, h >> i);
      vkCmdBlitImage(
          ctx->transfer_cmd, r->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
          r->image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1,
          &(VkImageBlit){
              .srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, i - 1, 0, 1},
              .srcOffsets = {{0, 0, 0}, {src_w, src_h, 1}},
              .dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, i, 0, 1},
              .dstOffsets = {{0, 0, 0}, {dst_w, dst_h, 1}}},
          VK_FILTER_LINEAR);

      vkCmdPipelineBarrier(
          ctx->transfer_cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
          VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1,
          &(VkImageMemoryBarrier){
              .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
              .pNext = NULL,
              .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
              .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT,
              .oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
              .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
              .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
              .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
              .image = r->image,
              .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                                   .baseMipLevel = i,
                                   .levelCount = 1,
                                   .baseArrayLayer = 0,
                                   .layerCount = 1}});
    }

    vkCmdPipelineBarrier(
        ctx->transfer_cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, NULL, 0, NULL, 1,
        &(VkImageMemoryBarrier){
            .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            .pNext = NULL,
            .srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT,
            .dstAccessMask = VK_ACCESS_SHADER_READ_BIT,
            .oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            .newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = r->image,
            .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                                 .baseMipLevel = 0,
                                 .levelCount = mip_levels,
                                 .baseArrayLayer = 0,
                                 .layerCount = 1}});
  } else {
    vkCmdPipelineBarrier(
        ctx->transfer_cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, NULL, 0, NULL, 1,
        &(VkImageMemoryBarrier){
            .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            .pNext = NULL,
            .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_SHADER_READ_BIT,
            .oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            .newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = r->image,
            .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                                 .baseMipLevel = 0,
                                 .levelCount = 1,
                                 .baseArrayLayer = 0,
                                 .layerCount = 1}});
  }
                               
  result = vkEndCommandBuffer(ctx->transfer_cmd);
  if (result != VK_SUCCESS) {
    char detail[160];
    snprintf(detail, sizeof(detail),
             "Transfer command end failed with Vulkan result %d", result);
    texture_terminal_failure("Texture command finalization failed", detail);
  }

  texture_startup_step(filename, "submitting GPU transfer");
  result = vkQueueSubmit(ctx->queue, 1,
                &(VkSubmitInfo){.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                                .pNext = NULL,
                                .commandBufferCount = 1,
                                .pCommandBuffers = &ctx->transfer_cmd},
                ctx->transfer_fence);
  if (result != VK_SUCCESS) {
    char detail[160];
    snprintf(detail, sizeof(detail),
             "Texture queue submit failed with Vulkan result %d", result);
    texture_terminal_failure("Texture GPU submission failed", detail);
  }

  texture_startup_step(filename, "waiting for GPU completion");
  result = vkWaitForFences(ctx->device, 1, &ctx->transfer_fence, VK_TRUE,
                           15ULL * 1000ULL * 1000ULL * 1000ULL);
  if (result != VK_SUCCESS) {
    char detail[224];
    if (result == VK_TIMEOUT) {
      snprintf(detail, sizeof(detail),
               "%s GPU transfer exceeded the 15 second safety limit (Vulkan result %d)",
               texture_display_name(filename), result);
      texture_terminal_failure("Texture GPU transfer timed out", detail);
    }
    snprintf(detail, sizeof(detail),
             "%s GPU transfer failed before completion (Vulkan result %d)",
             texture_display_name(filename), result);
    texture_terminal_failure(result == VK_ERROR_DEVICE_LOST
                                 ? "GPU rejected texture transfer"
                                 : "Texture GPU transfer failed",
                             detail);
  }
  result = vkResetFences(ctx->device, 1, &ctx->transfer_fence);
  if (result != VK_SUCCESS) {
    char detail[160];
    snprintf(detail, sizeof(detail),
             "Transfer fence reset failed with Vulkan result %d", result);
    texture_terminal_failure("Texture synchronization failed", detail);
  }

  result = vkCreateImageView(
      ctx->device,
      &(VkImageViewCreateInfo){
          .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
          .pNext = NULL,
          .flags = 0,
          .image = r->image,
          .viewType = VK_IMAGE_VIEW_TYPE_2D,
          .format = VK_FORMAT_R8G8B8A8_UNORM,
          .components = {VK_COMPONENT_SWIZZLE_IDENTITY,
                         VK_COMPONENT_SWIZZLE_IDENTITY,
                         VK_COMPONENT_SWIZZLE_IDENTITY,
                         VK_COMPONENT_SWIZZLE_IDENTITY},
          .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                               .baseMipLevel = 0,
                               .levelCount = mip_levels,
                               .baseArrayLayer = 0,
                               .layerCount = 1}},
      NULL, &r->view);
  if (result != VK_SUCCESS || r->view == VK_NULL_HANDLE) {
    char detail[160];
    snprintf(detail, sizeof(detail),
             "Texture image view failed with Vulkan result %d", result);
    texture_terminal_failure("Texture view creation failed", detail);
  }

  vmaDestroyBuffer(ctx->allocator, staging_buffer, staging_memory);
  texture_startup_step(filename, "upload complete");

  r->size[0] = w;
  r->size[1] = h;

  return r;
}

/* A texture uploading in the background (OM, 2026-10-12, from
   the reference client): the same copy and mip chain as create_mipmap_texture(),
   recorded on a command buffer of its own and submitted on a fence of its
   own, so nothing waits on the GPU. texture_upload_poll() finishes it once
   the fence has signalled. Engine thread only (the command pool and the
   queue are the frame's). */
struct texture_upload {
  texture* tex;
  VkBuffer staging;
  VmaAllocation staging_memory;
  VkCommandBuffer cmd;
  VkFence fence;
  int mip_levels;
};

static void texture_upload_release(tcontext* ctx, texture_upload* up) {
  if (up->cmd) vkFreeCommandBuffers(ctx->device, ctx->cmd_pool, 1, &up->cmd);
  if (up->fence) vkDestroyFence(ctx->device, up->fence, NULL);
  if (up->staging) vmaDestroyBuffer(ctx->allocator, up->staging, up->staging_memory);
  free(up);
}

static void texture_upload_drop_image(tcontext* ctx, texture_upload* up) {
  if (up->tex) {
    if (up->tex->image) vmaDestroyImage(ctx->allocator, up->tex->image, up->tex->memory);
    free(up->tex);
    up->tex = NULL;
  }
}

static void texture_barrier(VkCommandBuffer cmd, VkImage image, int level,
                            int levels, VkImageLayout from, VkImageLayout to,
                            VkAccessFlags src, VkAccessFlags dst,
                            VkPipelineStageFlags src_stage,
                            VkPipelineStageFlags dst_stage) {
  vkCmdPipelineBarrier(
      cmd, src_stage, dst_stage, 0, 0, NULL, 0, NULL, 1,
      &(VkImageMemoryBarrier){
          .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
          .srcAccessMask = src,
          .dstAccessMask = dst,
          .oldLayout = from,
          .newLayout = to,
          .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
          .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
          .image = image,
          .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                               .baseMipLevel = level,
                               .levelCount = levels,
                               .baseArrayLayer = 0,
                               .layerCount = 1}});
}

texture_upload* texture_upload_begin(tcontext* ctx, const unsigned char* rgba,
                                     int w, int h) {
  if (!ctx || !rgba || w <= 0 || h <= 0) return NULL;
  VkPhysicalDeviceProperties gpu = {0};
  vkGetPhysicalDeviceProperties(ctx->ph_device, &gpu);
  if ((uint32_t)w > gpu.limits.maxImageDimension2D ||
      (uint32_t)h > gpu.limits.maxImageDimension2D) {
    SDL_Log("Wyrm texture: %d x %d is over this GPU's %u px", w, h,
            gpu.limits.maxImageDimension2D);
    return NULL;
  }
  /* As create_mipmap_texture(): no mip blits on Mali, nor where the format
     cannot be blitted. */
  int mip_levels = (int)(floorf(log2f((float)GLM_MAX(w, h))) + 1);
  if (strstr(gpu.deviceName, "Mali") != NULL) mip_levels = 1;
  if (mip_levels > 1) {
    VkFormatProperties format = {0};
    vkGetPhysicalDeviceFormatProperties(ctx->ph_device, VK_FORMAT_R8G8B8A8_UNORM,
                                        &format);
    const VkFormatFeatureFlags need = VK_FORMAT_FEATURE_BLIT_SRC_BIT |
                                      VK_FORMAT_FEATURE_BLIT_DST_BIT |
                                      VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
    if ((format.optimalTilingFeatures & need) != need) mip_levels = 1;
  }

  texture_upload* up = calloc(1, sizeof(*up));
  if (!up) return NULL;
  up->tex = calloc(1, sizeof(texture));
  if (!up->tex) { free(up); return NULL; }
  up->mip_levels = mip_levels;

  const VkDeviceSize bytes = (VkDeviceSize)w * (VkDeviceSize)h * 4;
  VmaAllocationInfo info = {0};
  if (vmaCreateBuffer(
          ctx->allocator,
          &(VkBufferCreateInfo){.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
                                .size = bytes,
                                .usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                                .sharingMode = VK_SHARING_MODE_EXCLUSIVE},
          &(VmaAllocationCreateInfo){
              .flags = VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT |
                       VMA_ALLOCATION_CREATE_MAPPED_BIT,
              .usage = VMA_MEMORY_USAGE_AUTO},
          &up->staging, &up->staging_memory, &info) != VK_SUCCESS ||
      !info.pMappedData) {
    up->staging = VK_NULL_HANDLE;
    texture_upload_drop_image(ctx, up);
    texture_upload_release(ctx, up);
    return NULL;
  }
  memcpy(info.pMappedData, rgba, (size_t)bytes);
  vmaFlushAllocation(ctx->allocator, up->staging_memory, 0, bytes);

  texture* t = up->tex;
  if (vmaCreateImage(
          ctx->allocator,
          &(VkImageCreateInfo){
              .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
              .imageType = VK_IMAGE_TYPE_2D,
              .format = VK_FORMAT_R8G8B8A8_UNORM,
              .extent = {w, h, 1},
              .mipLevels = mip_levels,
              .arrayLayers = 1,
              .samples = VK_SAMPLE_COUNT_1_BIT,
              .tiling = VK_IMAGE_TILING_OPTIMAL,
              .usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT |
                       (mip_levels > 1 ? VK_IMAGE_USAGE_TRANSFER_SRC_BIT : 0),
              .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
              .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED},
          &(VmaAllocationCreateInfo){.usage = VMA_MEMORY_USAGE_AUTO, .priority = 1.0f},
          &t->image, &t->memory, NULL) != VK_SUCCESS) {
    t->image = VK_NULL_HANDLE;
    texture_upload_drop_image(ctx, up);
    texture_upload_release(ctx, up);
    return NULL;
  }
  t->size[0] = w;
  t->size[1] = h;

  if (vkAllocateCommandBuffers(
          ctx->device,
          &(VkCommandBufferAllocateInfo){
              .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
              .commandPool = ctx->cmd_pool,
              .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
              .commandBufferCount = 1},
          &up->cmd) != VK_SUCCESS ||
      vkCreateFence(ctx->device,
                    &(VkFenceCreateInfo){.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO},
                    NULL, &up->fence) != VK_SUCCESS) {
    texture_upload_drop_image(ctx, up);
    texture_upload_release(ctx, up);
    return NULL;
  }

  VkCommandBuffer cmd = up->cmd;
  vkBeginCommandBuffer(cmd, &(VkCommandBufferBeginInfo){
                                .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
                                .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT});
  texture_barrier(cmd, t->image, 0, 1, VK_IMAGE_LAYOUT_UNDEFINED,
                  VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_ACCESS_NONE,
                  VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                  VK_PIPELINE_STAGE_TRANSFER_BIT);
  vkCmdCopyBufferToImage(
      cmd, up->staging, t->image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1,
      &(VkBufferImageCopy){
          .imageSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                               .mipLevel = 0,
                               .baseArrayLayer = 0,
                               .layerCount = 1},
          .imageExtent = {w, h, 1}});
  if (mip_levels > 1) {
    texture_barrier(cmd, t->image, 0, 1, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
    for (int i = 1; i < mip_levels; i++) {
      texture_barrier(cmd, t->image, i, 1, VK_IMAGE_LAYOUT_UNDEFINED,
                      VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_ACCESS_NONE,
                      VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                      VK_PIPELINE_STAGE_TRANSFER_BIT);
      const int32_t src_w = GLM_MAX(1, w >> (i - 1));
      const int32_t src_h = GLM_MAX(1, h >> (i - 1));
      const int32_t dst_w = GLM_MAX(1, w >> i);
      const int32_t dst_h = GLM_MAX(1, h >> i);
      vkCmdBlitImage(cmd, t->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                     t->image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1,
                     &(VkImageBlit){
                         .srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, i - 1, 0, 1},
                         .srcOffsets = {{0, 0, 0}, {src_w, src_h, 1}},
                         .dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, i, 0, 1},
                         .dstOffsets = {{0, 0, 0}, {dst_w, dst_h, 1}}},
                     VK_FILTER_LINEAR);
      texture_barrier(cmd, t->image, i, 1, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                      VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                      VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                      VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
    }
    texture_barrier(cmd, t->image, 0, mip_levels,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_SHADER_READ_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
  } else {
    texture_barrier(cmd, t->image, 0, 1, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
  }
  if (vkEndCommandBuffer(cmd) != VK_SUCCESS ||
      vkQueueSubmit(ctx->queue, 1,
                    &(VkSubmitInfo){.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                                    .commandBufferCount = 1,
                                    .pCommandBuffers = &cmd},
                    up->fence) != VK_SUCCESS) {
    texture_upload_drop_image(ctx, up);
    texture_upload_release(ctx, up);
    return NULL;
  }
  return up;
}

bool texture_upload_poll(tcontext* ctx, texture_upload* up, texture** out) {
  *out = NULL;
  VkResult status = vkGetFenceStatus(ctx->device, up->fence);
  if (status == VK_NOT_READY) return false;
  if (status == VK_SUCCESS &&
      vkCreateImageView(
          ctx->device,
          &(VkImageViewCreateInfo){
              .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
              .image = up->tex->image,
              .viewType = VK_IMAGE_VIEW_TYPE_2D,
              .format = VK_FORMAT_R8G8B8A8_UNORM,
              .components = {VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY,
                             VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY},
              .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                                   .baseMipLevel = 0,
                                   .levelCount = up->mip_levels,
                                   .baseArrayLayer = 0,
                                   .layerCount = 1}},
          NULL, &up->tex->view) == VK_SUCCESS) {
    *out = up->tex;
    up->tex = NULL;
  } else {
    SDL_Log("Wyrm texture: background upload failed (Vulkan result %d)", status);
    texture_upload_drop_image(ctx, up);
  }
  texture_upload_release(ctx, up);
  return true;
}

void texture_upload_cancel(tcontext* ctx, texture_upload* up) {
  if (!up) return;
  vkWaitForFences(ctx->device, 1, &up->fence, VK_TRUE, UINT64_MAX);
  texture_upload_drop_image(ctx, up);
  texture_upload_release(ctx, up);
}

texture* create_minimap_texture(tcontext* ctx, int width) {
  texture* r = malloc(sizeof(texture));
  vmaCreateImage(
      ctx->allocator,
      &(VkImageCreateInfo){
          .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
          .pNext = NULL,
          .flags = 0,
          .imageType = VK_IMAGE_TYPE_2D,
          .format = VK_FORMAT_R8_UNORM,
          .extent = {width, width, 1},
          .mipLevels = 1,
          .arrayLayers = 1,
          .samples = VK_SAMPLE_COUNT_1_BIT,
          .tiling = VK_IMAGE_TILING_OPTIMAL,
          .usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
          .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
          .queueFamilyIndexCount = 0,
          .pQueueFamilyIndices = NULL,
          .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED},
      &(VmaAllocationCreateInfo){
          .flags = 0, .usage = VMA_MEMORY_USAGE_AUTO, .priority = 1.0f},
      &r->image, &r->memory, NULL);

  vkResetCommandBuffer(ctx->transfer_cmd, 0);
  vkBeginCommandBuffer(ctx->transfer_cmd,
                       &(VkCommandBufferBeginInfo){
                           .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
                           .pNext = NULL,
                           .flags = 0,
                           .pInheritanceInfo = NULL});
  vkCmdPipelineBarrier(
      ctx->transfer_cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
      VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, NULL, 0, NULL, 1,
      &(VkImageMemoryBarrier){
          .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
          .pNext = NULL,
          .srcAccessMask = VK_ACCESS_NONE,
          .dstAccessMask = VK_ACCESS_SHADER_READ_BIT,
          .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED,
          .newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
          .srcQueueFamilyIndex = ctx->queue_family,
          .dstQueueFamilyIndex = ctx->queue_family,
          .image = r->image,
          .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                               .baseMipLevel = 0,
                               .levelCount = 1,
                               .baseArrayLayer = 0,
                               .layerCount = 1}});

  vkEndCommandBuffer(ctx->transfer_cmd);

  vkQueueSubmit(ctx->queue, 1,
                &(VkSubmitInfo){.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                                .pNext = NULL,
                                .commandBufferCount = 1,
                                .pCommandBuffers = &ctx->transfer_cmd},
                ctx->transfer_fence);

  vkWaitForFences(ctx->device, 1, &ctx->transfer_fence, VK_TRUE, UINT64_MAX);
  vkResetFences(ctx->device, 1, &ctx->transfer_fence);

  vkCreateImageView(
      ctx->device,
      &(VkImageViewCreateInfo){
          .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
          .pNext = NULL,
          .flags = 0,
          .image = r->image,
          .viewType = VK_IMAGE_VIEW_TYPE_2D,
          .format = VK_FORMAT_R8_UNORM,
          .components = {VK_COMPONENT_SWIZZLE_IDENTITY,
                         VK_COMPONENT_SWIZZLE_IDENTITY,
                         VK_COMPONENT_SWIZZLE_IDENTITY,
                         VK_COMPONENT_SWIZZLE_IDENTITY},
          .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                               .baseMipLevel = 0,
                               .levelCount = 1,
                               .baseArrayLayer = 0,
                               .layerCount = 1}},
      NULL, &r->view);

  r->size[0] = width;
  r->size[1] = width;

  return r;
}

void destroy_texture(tcontext* ctx, texture* tex) {
  vkDestroyImageView(ctx->device, tex->view, NULL);
  vmaDestroyImage(ctx->allocator, tex->image, tex->memory);

  free(tex);
}
