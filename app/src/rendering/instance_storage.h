#ifndef WYRM_INSTANCE_STORAGE_H
#define WYRM_INSTANCE_STORAGE_H

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

/* Grow storage, preserving the existing hard limit. Pointers into this array
   must not survive a push; geometry/culling resolve indexes after pushing. */
static inline void* instance_storage_reserve(void* data, int* capacity,
                                             int limit, int required,
                                             size_t stride) {
  if (required <= 0 || required > limit || !stride) return NULL;
  if (required <= *capacity) return data;
  int next = *capacity ? *capacity : (limit < 2048 ? limit : 2048);
  while (next < required) next = next > limit / 2 ? limit : next * 2;
  if ((size_t)next > SIZE_MAX / stride) return NULL;
  void* grown = realloc(data, (size_t)next * stride);
  if (!grown) return NULL;
  *capacity = next;
  return grown;
}

#endif
