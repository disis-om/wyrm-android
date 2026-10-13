#ifndef TEXTURE_H
#define TEXTURE_H

#include <thermite.h>

typedef struct texture {
  ivec2 size;
  VkImage image;
  VkImageView view;
  VmaAllocation memory;
} texture;

texture* create_mipmap_texture(tcontext* ctx, const char* filename);
texture* create_minimap_texture(tcontext* ctx, int width);
void destroy_texture(tcontext* ctx, texture* tex);

/* A texture uploading in the background (OM, 2026-10-12): begin copies the
   pixels and returns at once (NULL when it cannot start); poll returns true
   once the GPU has finished, with the texture in *out (NULL if it failed);
   cancel waits for it and throws it away. */
typedef struct texture_upload texture_upload;
texture_upload* texture_upload_begin(tcontext* ctx, const unsigned char* rgba,
                                     int w, int h);
bool texture_upload_poll(tcontext* ctx, texture_upload* up, texture** out);
void texture_upload_cancel(tcontext* ctx, texture_upload* up);

#endif