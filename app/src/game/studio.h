#ifndef WYRM_STUDIO_H
#define WYRM_STUDIO_H

/*
 * Skin Studio (OM, 2026-10-12): the player's own snake drawn from an atlas the
 * app bakes. Only this phone sees it; the arena gets the settings' skin.
 *
 * The atlas is STUDIO_GRID x STUDIO_GRID cells. The app decides what each
 * cell holds; the engine only reads the indices it is given:
 *   P: the body, cell index per bead, repeated along the snake (head first)
 *   C: each cell's own colour (RRGGBB), for the flat modes and the glow
 *   E: the eyes, each "forward,side,white radius,pupil radius,look,white
 *      cell,pupil cell" in snake units (slither's eye: 6,+-6.5,6,3.5,1)
 * Sections are joined by '|', items by ',' and eyes by ';'. Numbers use '.'.
 */

#include <stdbool.h>
#include <stdint.h>

#define STUDIO_GRID 8
#define STUDIO_CELLS (STUDIO_GRID * STUDIO_GRID)
#define STUDIO_MAX_PATTERN 256
#define STUDIO_MAX_EYES 8
/* A sprite uv shifted this far right samples the Studio atlas (bp.slang). */
#define STUDIO_UV_SHIFT 4.0f

typedef struct studio_eye {
  float fwd, side;    /* centre, snake units from the head, +side = right */
  float white_r;      /* eye white radius, snake units */
  float pupil_r;      /* pupil radius, snake units */
  float look;         /* how far the pupil follows where the snake looks */
  int white_cell, pupil_cell;
} studio_eye;

typedef struct studio_skin {
  int pattern_len;
  uint8_t pattern[STUDIO_MAX_PATTERN];
  uint32_t cell_rgb[STUDIO_CELLS];
  int eye_count;
  studio_eye eyes[STUDIO_MAX_EYES];
} studio_skin;

/* Parses the app's text; false (and nothing usable) when it is not valid. */
bool studio_parse(const char* text, studio_skin* out);

/* The renderer calls this once every frame in flight shows the new atlas. */
void studio_commit(const studio_skin* skin);
/* Off at once (the atlas stays bound and unused). */
void studio_clear(void);

bool studio_on(void);
const studio_skin* studio_current(void);
/* The cell the [index]th bead from the head wears. */
int studio_cell_at(int index);
/* That bead's own colour, 0xRRGGBB. */
uint32_t studio_rgb_at(int index);

#endif
