#ifndef TSWAPPY_H
#define TSWAPPY_H

/*
 * Android Frame Pacing (Swappy), Android only (OM, 2026-11-05).
 * https://developer.android.com/games/sdk/frame-pacing
 * https://developer.android.com/games/sdk/frame-pacing/vulkan/add-functions
 *
 * Plain FIFO lets finished frames queue up behind the display (3 swapchain
 * images = up to 2 frames waiting), so input reaches the screen late and a
 * frame that misses its slot shows twice while the next one waits. Swappy
 * presents each image for exactly the swap interval it is given, uses
 * VK_GOOGLE_display_timing where the driver has it and fences elsewhere, and
 * keeps the display on the matching refresh rate through
 * ANativeWindow_setFrameRate.
 *
 * Wyrm gives it the shortest refresh period the display supports, so the
 * game runs at the screen's maximum rate. Auto swap interval is off: Swappy
 * never halves the rate on a heavy frame. Every other platform (iOS, Wyrm
 * Desktop, macOS) presents with plain vkQueuePresentKHR; these are no-ops
 * there.
 */

#include <stdbool.h>
#include <stdint.h>
#include <vulkan/vulkan.h>

#include "tcontext.h"

#ifdef __ANDROID__
/* Before vkCreateDevice: adds the extensions Swappy wants (if the device has
   them) after `base`. Returns the list and its length; free with
   tswappy_free_extensions. NULL (count 0) when out of memory. */
const char** tswappy_device_extensions(VkPhysicalDevice ph, const char* base,
                                       uint32_t* count);
void tswappy_free_extensions(const char** list);
/* After vkGetDeviceQueue. */
void tswappy_device_created(tcontext* context);
/* After a successful vkCreateSwapchainKHR (also after a rebuild). */
void tswappy_swapchain_created(tcontext* context);
/* Before vkDestroySwapchainKHR of `swapchain`. */
void tswappy_swapchain_destroying(tcontext* context, VkSwapchainKHR swapchain);
/* Before vkDestroyDevice. */
void tswappy_device_destroying(tcontext* context);
/* The window's ANativeWindow, from _tcontext_create_surface and
   tcontext_recreate_surface. */
void tswappy_set_window(void* native_window);
/* vkQueuePresentKHR through Swappy when it runs this swapchain. */
VkResult tswappy_present(tcontext* context, const VkPresentInfoKHR* info);
#else
static inline void tswappy_set_window(void* native_window) { (void)native_window; }
#endif

#endif
