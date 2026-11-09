#ifndef TDBUFFER_H
#define TDBUFFER_H

#include "tcontext.h"

typedef struct tdbuffer {
  VkBuffer handle;
  VmaAllocation memory;
  void* data;
  VkDeviceSize capacity;
  VkDeviceSize limit;
  VkBufferUsageFlags usage;
  bool coherent;
} tdbuffer;

tdbuffer* tdbuffer_create(tcontext* context, const void* data,
                          VkDeviceSize size, VkBufferUsageFlags usage);
void tdbuffer_destroy(tcontext* context, tdbuffer* buffer);
/* Stream slots grow only after tcontext_begin has waited that slot's fence. */
tdbuffer* tdbuffer_create_stream(tcontext* context, VkDeviceSize limit,
                                 VkBufferUsageFlags usage);
bool tdbuffer_upload(tcontext* context, tdbuffer* buffers, int frame,
                     const void* data, VkDeviceSize size);
void tdbuffer_flush(tcontext* context, tdbuffer* buffers, int frame,
                    VkDeviceSize size);

#endif
