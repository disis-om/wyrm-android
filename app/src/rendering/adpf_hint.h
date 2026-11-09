#ifndef WYRM_ADPF_HINT_H
#define WYRM_ADPF_HINT_H

/*
 * ADPF Performance Hint (OM, 2026-11-05), Android only.
 * https://developer.android.com/games/optimize/adpf/performance-hint-api
 *
 * The engine thread tells Android how long each match frame took on the CPU
 * and how long it may take (6.94 ms, a 144 Hz frame, on every phone), so
 * the CPU governor raises the clocks before a heavy frame misses its slot
 * instead of after. Reported only while a match is drawn; the waits for the
 * GPU fence and the next swapchain image are left out (they are idle time,
 * not work), so a FIFO wait never reads as a slow frame.
 *
 * The NDK functions arrived in Android 13 (API 33) and Wyrm's minSdk is 26,
 * so they are looked up from libandroid.so at run time: an older phone, or one
 * that refuses a session, simply gets no hints. No setting, nothing saved.
 */

#ifdef __ANDROID__
#include <SDL3/SDL.h>
#include <dlfcn.h>
#include <stdbool.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>

typedef struct APerformanceHintManager APerformanceHintManager;
typedef struct APerformanceHintSession APerformanceHintSession;

typedef struct wyrm_adpf {
  bool tried;
  APerformanceHintManager* (*get_manager)(void);
  APerformanceHintSession* (*create)(APerformanceHintManager*, const int32_t*,
                                     size_t, int64_t);
  int (*update_target)(APerformanceHintSession*, int64_t);
  int (*report)(APerformanceHintSession*, int64_t);
  APerformanceHintSession* session;
  int64_t target_ns;
} wyrm_adpf;

static wyrm_adpf wyrm_adpf_state;

/* Engine thread. The CPU budget of a frame: one 144 Hz refresh (6.94 ms)
   on every phone (OM, 2026-11-05: "6.9 ms me taiyar, chahe screen kam FPS
   ki ho"), or the display's own period when it is faster still (165/240 Hz).
   A frame ready sooner than the display needs it is never late. */
#define WYRM_ADPF_BUDGET_NS ((int64_t)(1e9 / 144.0))
static int64_t wyrm_adpf_period_ns(void) {
  const SDL_DisplayMode* mode =
      SDL_GetCurrentDisplayMode(SDL_GetPrimaryDisplay());
  float hz = mode && mode->refresh_rate > 1.0f ? mode->refresh_rate : 60.0f;
  int64_t period = (int64_t)(1e9 / (double)hz);
  return period < WYRM_ADPF_BUDGET_NS ? period : WYRM_ADPF_BUDGET_NS;
}

/* What is reported (2026-11-07). Android's WorkDuration: timings are
   "measured in wall time". The 2026-11-05 build counted the swapchain
   acquire, fence wait and Swappy's vsync sleep as work, so every frame read
   as over target and the clocks stayed at maximum until the phone heated
   (60 -> 40 FPS on the Moto). 2026-11-06 switched to the thread's CPU time,
   which leaves the sleeps out but also leaves out time the thread was ready
   and not running (preempted, waiting for a core): exactly when a boost is
   needed. Now main.c reports the frame's wall time from trender's start to
   the present call, less the fence/acquire wait inside tcontext_begin; the
   late-latch acquire is before that window and Swappy's sleep after it. */

/* Engine thread, once per drawn match frame. work_ns = that wall time. */
static void wyrm_adpf_report(int64_t work_ns) {
  wyrm_adpf* a = &wyrm_adpf_state;
  if (!a->tried) {
    a->tried = true;
    void* lib = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
    if (!lib) return;
    a->get_manager = (APerformanceHintManager * (*)(void))
        dlsym(lib, "APerformanceHint_getManager");
    a->create = (APerformanceHintSession * (*)(APerformanceHintManager*,
                                               const int32_t*, size_t, int64_t))
        dlsym(lib, "APerformanceHint_createSession");
    a->update_target = (int (*)(APerformanceHintSession*, int64_t))
        dlsym(lib, "APerformanceHint_updateTargetWorkDuration");
    a->report = (int (*)(APerformanceHintSession*, int64_t))
        dlsym(lib, "APerformanceHint_reportActualWorkDuration");
    if (!a->get_manager || !a->create || !a->update_target || !a->report) {
      SDL_Log("Wyrm ADPF: performance hints not available on this Android");
      a->report = NULL;
      return;
    }
    APerformanceHintManager* manager = a->get_manager();
    int32_t tid = (int32_t)gettid();
    a->target_ns = wyrm_adpf_period_ns();
    a->session = manager ? a->create(manager, &tid, 1, a->target_ns) : NULL;
    SDL_Log("Wyrm ADPF: %s (target %.2f ms)",
            a->session ? "hint session open" : "session refused",
            a->target_ns / 1e6);
  }
  if (!a->session || !a->report || work_ns <= 0) return;
  /* The display may change rate (another mode, a power saver): follow it,
     checked every 120 frames so the query stays off the hot path. */
  static unsigned frames;
  if (++frames >= 120) {
    frames = 0;
    int64_t period = wyrm_adpf_period_ns();
    if (period != a->target_ns) {
      a->target_ns = period;
      a->update_target(a->session, period);
    }
  }
  a->report(a->session, work_ns);
}
#endif

#endif
