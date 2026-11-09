#ifndef WYRM_FRAME_METRICS_H
#define WYRM_FRAME_METRICS_H

#include <SDL3/SDL.h>
#include <string.h>

/* CPU-side observations only: queue-present acceptance is not a display/GPU
   timestamp. No allocation, GPU query, extra wait, pacing or quality change. */
typedef struct wyrm_frame_metrics {
  Uint64 first, previous;
  unsigned samples, intervals, failed;
  unsigned histogram[257]; /* 0.25 ms upper bounds; last bucket >=64 ms. */
  double prepare_sum, prepare_max, submit_sum, submit_max, interval_max;
  unsigned prepare_over_budget; /* > 6.94 ms, the 144 Hz budget */
} wyrm_frame_metrics;

static void wyrm_frame_observe(bool active, bool presented, Uint64 start,
                                Uint64 prepared, Uint64 ended) {
  static wyrm_frame_metrics m;
  if (!active) { memset(&m, 0, sizeof(m)); return; }
  if (!presented) { ++m.failed; return; }
  const double to_ms = 1000.0 / (double)SDL_GetPerformanceFrequency();
  if (prepared < start || ended < prepared ||
      (m.previous && ended <= m.previous)) {
    memset(&m, 0, sizeof(m));
    return;
  }
  const double prepare = (double)(prepared - start) * to_ms;
  const double submit = (double)(ended - prepared) * to_ms;
  if (!m.samples) m.first = ended;
  ++m.samples;
  m.prepare_sum += prepare;
  m.submit_sum += submit;
  if (prepare > m.prepare_max) m.prepare_max = prepare;
  if (submit > m.submit_max) m.submit_max = submit;
  if (prepare > 1000.0 / 144.0) ++m.prepare_over_budget;
  if (m.previous) {
    const double interval = (double)(ended - m.previous) * to_ms;
    unsigned bucket = interval >= 64.0 ? 256u : (unsigned)(interval * 4.0);
    ++m.histogram[bucket];
    ++m.intervals;
    if (interval > m.interval_max) m.interval_max = interval;
  }
  m.previous = ended;
  const double elapsed = (double)(ended - m.first) * to_ms;
  if (elapsed < 5000.0 || !m.intervals) return;
  const unsigned target = (m.intervals * 95u + 99u) / 100u;
  unsigned seen = 0, p95 = 0;
  for (; p95 < 256; ++p95) {
    seen += m.histogram[p95];
    if (seen >= target) break;
  }
  SDL_Log("Wyrm perf CPU: presents/s=%.1f interval_p95%s%.2fms max=%.2fms "
          "prepare_avg/max=%.2f/%.2fms prepare>6.94ms=%u/%u "
          "submit_path_avg/max=%.2f/%.2fms failed=%u (not GPU/display timing)",
          m.intervals * 1000.0 / elapsed, p95 == 256 ? ">=" : "<=",
          p95 == 256 ? 64.0 : (p95 + 1) * 0.25, m.interval_max,
          m.prepare_sum / m.samples, m.prepare_max,
          m.prepare_over_budget, m.samples,
          m.submit_sum / m.samples, m.submit_max, m.failed);
  memset(&m, 0, sizeof(m));
}

#endif
