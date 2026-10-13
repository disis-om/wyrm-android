#include "studio.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

/* Skin Studio (OM, 2026-10-12): see studio.h. Engine thread only. */

static studio_skin current;
static bool on = false;

void studio_commit(const studio_skin* skin) {
  if (!skin || skin->pattern_len <= 0) {
    on = false;
    return;
  }
  current = *skin;
  on = true;
}

void studio_clear(void) { on = false; }

bool studio_on(void) { return on; }

const studio_skin* studio_current(void) { return &current; }

int studio_cell_at(int index) {
  if (current.pattern_len <= 0) return 0;
  int i = index % current.pattern_len;
  if (i < 0) i += current.pattern_len;
  return current.pattern[i];
}

uint32_t studio_rgb_at(int index) {
  return current.cell_rgb[studio_cell_at(index)] & 0xFFFFFFu;
}

static float clampf(float v, float lo, float hi) {
  if (!isfinite(v)) return lo;
  return v < lo ? lo : v > hi ? hi : v;
}

static const char* skip_comma(const char* p, const char* end, char mark) {
  if (p < end && *p == mark) ++p;
  return p;
}

bool studio_parse(const char* text, studio_skin* out) {
  if (!text || !out) return false;
  memset(out, 0, sizeof(*out));
  const char* p = text;
  while (*p) {
    char key = p[0];
    if (p[1] != ':') return false;
    p += 2;
    const char* end = strchr(p, '|');
    if (!end) end = p + strlen(p);
    if (key == 'P') {
      while (p < end && out->pattern_len < STUDIO_MAX_PATTERN) {
        char* next = NULL;
        long cell = strtol(p, &next, 10);
        if (next == p || next > end || cell < 0 || cell >= STUDIO_CELLS)
          return false;
        out->pattern[out->pattern_len++] = (uint8_t)cell;
        p = skip_comma(next, end, ',');
      }
    } else if (key == 'C') {
      int i = 0;
      while (p < end && i < STUDIO_CELLS) {
        char* next = NULL;
        unsigned long rgb = strtoul(p, &next, 16);
        if (next == p || next > end) return false;
        out->cell_rgb[i++] = (uint32_t)rgb & 0xFFFFFFu;
        p = skip_comma(next, end, ',');
      }
    } else if (key == 'E') {
      while (p < end && out->eye_count < STUDIO_MAX_EYES) {
        float f[5];
        char* next = NULL;
        for (int k = 0; k < 5; ++k) {
          f[k] = strtof(p, &next);
          if (next == p || next > end) return false;
          p = skip_comma(next, end, ',');
        }
        long white = strtol(p, &next, 10);
        if (next == p || next > end) return false;
        p = skip_comma(next, end, ',');
        long pupil = strtol(p, &next, 10);
        if (next == p || next > end) return false;
        p = skip_comma(next, end, ';');
        if (white < 0 || white >= STUDIO_CELLS || pupil < 0 ||
            pupil >= STUDIO_CELLS)
          return false;
        studio_eye* e = out->eyes + out->eye_count++;
        e->fwd = clampf(f[0], -40.0f, 40.0f);
        e->side = clampf(f[1], -40.0f, 40.0f);
        e->white_r = clampf(f[2], 0.0f, 30.0f);
        e->pupil_r = clampf(f[3], 0.0f, 30.0f);
        e->look = clampf(f[4], 0.0f, 3.0f);
        e->white_cell = (int)white;
        e->pupil_cell = (int)pupil;
      }
    }
    /* An unknown section is skipped: an older engine reads a newer app. */
    p = *end == '|' ? end + 1 : end;
  }
  return out->pattern_len > 0;
}
