#include "tdbuffer.h"

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

static bool tdbuffer_allocate(tcontext* context, tdbuffer* slot,
                              VkDeviceSize size, VkBufferUsageFlags usage) {
  VmaAllocationInfo info = {0};
  tdbuffer next = {0};
  VkResult result = vmaCreateBuffer(
      context->allocator,
      &(VkBufferCreateInfo){.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
                            .size = size, .usage = usage,
                            .sharingMode = VK_SHARING_MODE_EXCLUSIVE},
      &(VmaAllocationCreateInfo){
          .flags = VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT |
                   VMA_ALLOCATION_CREATE_MAPPED_BIT,
          .usage = VMA_MEMORY_USAGE_AUTO},
      &next.handle, &next.memory, &info);
  if (result != VK_SUCCESS || !info.pMappedData) {
    if (result == VK_SUCCESS)
      vmaDestroyBuffer(context->allocator, next.handle, next.memory);
    fprintf(stderr, "Wyrm: mapped buffer allocation failed (%d, %llu bytes)\n",
            (int)result, (unsigned long long)size);
    return false;
  }
  VkMemoryPropertyFlags properties = 0;
  vmaGetAllocationMemoryProperties(context->allocator, next.memory, &properties);
  next.data = info.pMappedData;
  next.capacity = size;
  next.limit = slot->limit;
  next.usage = usage;
  next.coherent = (properties & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0;
  /* Only the current fence-owned slot is replaced. In-flight slots keep
     their original handles and allocations. */
  if (slot->handle)
    vmaDestroyBuffer(context->allocator, slot->handle, slot->memory);
  *slot = next;
  return true;
}

void tdbuffer_flush(tcontext* context, tdbuffer* buffers, int frame,
                    VkDeviceSize size) {
  if (!buffers || frame < 0 || frame >= context->fif || !size) return;
  tdbuffer* slot = buffers + frame;
  if (!slot->coherent && slot->memory)
    vmaFlushAllocation(context->allocator, slot->memory, 0, size);
}

tdbuffer* tdbuffer_create(tcontext* context, const void* data,
                          VkDeviceSize size, VkBufferUsageFlags usage) {
  tdbuffer* buffer = calloc((size_t)context->fif, sizeof(tdbuffer));
  if (!buffer) return NULL;
  for (int i = 0; i < context->fif; ++i) {
    if (!tdbuffer_allocate(context, buffer + i, size, usage)) {
      tdbuffer_destroy(context, buffer);
      return NULL;
    }
    if (data) {
      memcpy(buffer[i].data, data, (size_t)size);
      tdbuffer_flush(context, buffer, i, size);
    }
  }
  return buffer;
}

tdbuffer* tdbuffer_create_stream(tcontext* context, VkDeviceSize limit,
                                 VkBufferUsageFlags usage) {
  if (!limit) return NULL;
  tdbuffer* buffers = calloc((size_t)context->fif, sizeof(tdbuffer));
  if (buffers)
    for (int i = 0; i < context->fif; ++i) {
      buffers[i].usage = usage;
      buffers[i].limit = limit;
    }
  return buffers;
}

bool tdbuffer_upload(tcontext* context, tdbuffer* buffers, int frame,
                     const void* data, VkDeviceSize size) {
  if (!buffers || !data || !size || frame < 0 || frame >= context->fif)
    return false;
  tdbuffer* slot = buffers + frame;
  if (slot->limit && size > slot->limit) return false;
  if (size > slot->capacity) {
    VkDeviceSize next = slot->capacity ? slot->capacity : 4096;
    while (next < size) {
      if (next > UINT64_MAX / 2) { next = size; break; }
      next *= 2;
    }
    if (slot->limit && next > slot->limit) next = slot->limit;
    if (!tdbuffer_allocate(context, slot, next, slot->usage)) return false;
  }
  memcpy(slot->data, data, (size_t)size);
  tdbuffer_flush(context, buffers, frame, size);
  return true;
}

void tdbuffer_destroy(tcontext* context, tdbuffer* buffer) {
  if (!buffer) return;
  for (int i = 0; i < context->fif; ++i)
    if (buffer[i].handle)
      vmaDestroyBuffer(context->allocator, buffer[i].handle, buffer[i].memory);
  free(buffer);
}
