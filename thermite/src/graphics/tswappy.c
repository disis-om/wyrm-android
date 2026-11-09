/* Android Frame Pacing (Swappy) for the engine's swapchain. See tswappy.h. */
#include "tswappy.h"

#ifdef __ANDROID__
#include <SDL3/SDL.h>
#include <android/native_window.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <swappy/swappyVk.h>

/* game/oef.c */
void wyrm_frame_period_hint_ms(double ms);

static ANativeWindow* swappy_window;
/* The swapchain Swappy was initialised for; presents to any other one go
   straight to vkQueuePresentKHR. */
static VkSwapchainKHR swappy_swapchain = VK_NULL_HANDLE;

void tswappy_set_window(void* native_window) {
  swappy_window = (ANativeWindow*)native_window;
}

const char** tswappy_device_extensions(VkPhysicalDevice ph, const char* base,
                                       uint32_t* count) {
  uint32_t available_count = 0;
  VkExtensionProperties* available = NULL;
  vkEnumerateDeviceExtensionProperties(ph, NULL, &available_count, NULL);
  if (available_count) {
    available = calloc(available_count, sizeof(*available));
    if (available)
      vkEnumerateDeviceExtensionProperties(ph, NULL, &available_count, available);
    else
      available_count = 0;
  }
  uint32_t required = 0;
  SwappyVk_determineDeviceExtensions(ph, available_count, available, &required,
                                     NULL);
  /* One block: the pointer array, then the names. */
  size_t names = (size_t)required * (VK_MAX_EXTENSION_NAME_SIZE + 1);
  char* block = calloc(1, sizeof(char*) * (required + 1) + names);
  const char** list = (const char**)block;
  if (!list) {
    free(available);
    *count = 0;
    return NULL; /* the caller keeps its own list */
  }
  char** wanted = (char**)(list + 1);
  char* text = block + sizeof(char*) * (required + 1);
  for (uint32_t i = 0; i < required; ++i)
    wanted[i] = text + (size_t)i * (VK_MAX_EXTENSION_NAME_SIZE + 1);
  if (required)
    SwappyVk_determineDeviceExtensions(ph, available_count, available, &required,
                                       wanted);
  free(available);
  /* `base` (VK_KHR_swapchain) first; Swappy's names after it, without a
     second copy of the swapchain extension. */
  list[0] = base;
  uint32_t n = 1;
  for (uint32_t i = 0; i < required; ++i) {
    if (strcmp(wanted[i], base) == 0) continue;
    list[n++] = wanted[i];
    SDL_Log("Wyrm Swappy: device extension %s", wanted[i]);
  }
  *count = n;
  return list;
}

void tswappy_free_extensions(const char** list) { free((void*)list); }

void tswappy_device_created(tcontext* context) {
  if (context->device == VK_NULL_HANDLE || context->queue == VK_NULL_HANDLE) return;
  SwappyVk_setQueueFamilyIndex(context->device, context->queue,
                               context->queue_family);
  /* Fixed pacing: one image per refresh, never a slower rate on a heavy
     frame, no pipeline switching. */
  SwappyVk_setAutoSwapInterval(false);
  SwappyVk_setAutoPipelineMode(false);
}

void tswappy_swapchain_created(tcontext* context) {
  swappy_swapchain = VK_NULL_HANDLE;
  if (context->swapchain == VK_NULL_HANDLE) return;
  JNIEnv* env = (JNIEnv*)SDL_GetAndroidJNIEnv();
  jobject activity = (jobject)SDL_GetAndroidActivity();
  if (!env || !activity) {
    SDL_Log("Wyrm Swappy: no activity yet; plain FIFO for this swapchain");
    return;
  }
  uint64_t refresh_ns = 0;
  bool ok = SwappyVk_initAndGetRefreshCycleDuration(
      env, activity, context->ph_device, context->device, context->swapchain,
      &refresh_ns);
  (*env)->DeleteLocalRef(env, activity);
  if (!ok) {
    SDL_Log("Wyrm Swappy: init refused; plain FIFO for this swapchain");
    return;
  }
  if (swappy_window)
    SwappyVk_setWindow(context->device, context->swapchain, swappy_window);
  /* The display's fastest refresh, so the game runs at the screen's
     maximum rate (the activity asks for that mode too). */
  uint64_t interval = refresh_ns;
  int modes = SwappyVk_getSupportedRefreshPeriodsNS(NULL, 0, context->swapchain);
  if (modes > 0 && modes <= 64) {
    uint64_t periods[64];
    int got = SwappyVk_getSupportedRefreshPeriodsNS(periods, modes,
                                                    context->swapchain);
    for (int i = 0; i < got; ++i)
      if (periods[i] > 0 && (interval == 0 || periods[i] < interval))
        interval = periods[i];
  }
  if (interval == 0) interval = 16666667ULL;
  SwappyVk_setSwapIntervalNS(context->device, context->swapchain, interval);
  swappy_swapchain = context->swapchain;
  /* Seed for the game's display-locked frame time (game/oef.c, 2026-11-07);
     it learns the real period if the display runs at another rate. */
  wyrm_frame_period_hint_ms((double)interval / 1e6);
  SDL_Log("Wyrm Swappy: pacing on (refresh %.2f ms, interval %.2f ms)",
          refresh_ns / 1e6, interval / 1e6);
}

void tswappy_swapchain_destroying(tcontext* context, VkSwapchainKHR swapchain) {
  /* Swappy holds one swapchain at a time: only that one has state to free,
     and it is freed once, before the new swapchain is made. */
  if (swapchain == VK_NULL_HANDLE || swapchain != swappy_swapchain) return;
  SwappyVk_destroySwapchain(context->device, swapchain);
  swappy_swapchain = VK_NULL_HANDLE;
}

void tswappy_device_destroying(tcontext* context) {
  if (context->device == VK_NULL_HANDLE) return;
  SwappyVk_destroyDevice(context->device);
  swappy_swapchain = VK_NULL_HANDLE;
}

VkResult tswappy_present(tcontext* context, const VkPresentInfoKHR* info) {
  if (swappy_swapchain != VK_NULL_HANDLE &&
      swappy_swapchain == context->swapchain)
    return SwappyVk_queuePresent(context->queue, info);
  return vkQueuePresentKHR(context->queue, info);
}
#endif
