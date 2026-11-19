#include "ai_coach.h"

#include <math.h>
#include <stddef.h>

#define COACH_PI 3.14159265358979323846f
#define COACH_TAU (2.0f * COACH_PI)
#define COACH_HORIZON 7
#define COACH_STEP 34.0f
#define COACH_DANGER 190.0f
#define COACH_BODY_LIMIT 96

static float wrap_angle(float a) {
  while (a > COACH_PI) a -= COACH_TAU;
  while (a < -COACH_PI) a += COACH_TAU;
  return a;
}

static float point_distance_sq(float ax, float ay, float bx, float by) {
  const float dx = ax - bx, dy = ay - by;
  return dx * dx + dy * dy;
}

/* Score one possible heading against nearby heads and sampled body segments. */
static float score_heading(const snake* snakes, int count, const snake* me,
                           float angle) {
  float score = 0.0f;
  const float dx = cosf(angle), dy = sinf(angle);
  for (int step = 1; step <= COACH_HORIZON; ++step) {
    const float px = me->xx + dx * (COACH_STEP * step);
    const float py = me->yy + dy * (COACH_STEP * step);
    const float horizon_weight = 1.0f / (float)step;
    for (int i = 0; i < count; ++i) {
      const snake* other = &snakes[i];
      if (other == me || other->dead || other->sct <= 0) continue;
      const float head_d2 = point_distance_sq(px, py, other->xx, other->yy);
      if (head_d2 < COACH_DANGER * COACH_DANGER) {
        const float closeness = 1.0f - sqrtf(head_d2) / COACH_DANGER;
        score += closeness * closeness * 8.0f * horizon_weight;
      }
      if (!other->pts) continue;
      int parts = other->sct;
      if (parts > COACH_BODY_LIMIT) parts = COACH_BODY_LIMIT;
      /* Sampling avoids an expensive full-body scan on every input frame. */
      for (int p = 0; p < parts; p += 4) {
        const float d2 = point_distance_sq(px, py, other->pts[p].xx,
                                           other->pts[p].yy);
        const float radius = 58.0f;
        if (d2 < radius * radius) {
          const float closeness = 1.0f - sqrtf(d2) / radius;
          score += closeness * closeness * 5.0f * horizon_weight;
        }
      }
    }
  }
  return score;
}

bool ai_coach_steer(const snake* snakes, int count, const snake* me,
                    float* out_angle) {
  if (!snakes || count < 2 || !me || !out_angle || me->dead) return false;

  float closest = COACH_DANGER * COACH_DANGER;
  for (int i = 0; i < count; ++i) {
    const snake* other = &snakes[i];
    if (other == me || other->dead || other->sct <= 0) continue;
    const float d2 = point_distance_sq(me->xx, me->yy, other->xx, other->yy);
    if (d2 < closest) closest = d2;
    if (other->pts) {
      int parts = other->sct;
      if (parts > COACH_BODY_LIMIT) parts = COACH_BODY_LIMIT;
      for (int p = 0; p < parts; p += 4) {
        const float bd2 = point_distance_sq(me->xx, me->yy,
                                            other->pts[p].xx,
                                            other->pts[p].yy);
        if (bd2 < closest) closest = bd2;
      }
    }
  }
  if (closest >= COACH_DANGER * COACH_DANGER) return false;

  const float current = isfinite(me->eang) ? me->eang : me->wang;
  float best_angle = current;
  float best_score = score_heading(snakes, count, me, current) + 0.35f;
  /* Search a fan around the current heading; prefer smaller corrections. */
  for (int n = -8; n <= 8; ++n) {
    const float candidate = wrap_angle(current + (float)n * (COACH_PI / 12.0f));
    const float turn_cost = fabsf((float)n) * 0.018f;
    const float score = score_heading(snakes, count, me, candidate) + turn_cost;
    if (score < best_score) {
      best_score = score;
      best_angle = candidate;
    }
  }
  *out_angle = best_angle;
  return true;
}
