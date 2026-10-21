#ifndef BACKGROUNDS_H
#define BACKGROUNDS_H

/**
 * The arenas' floors.
 *
 * Each entry is one seamlessly tiling image plus the world-space size of a
 * single tile. The size is not the pixel size and is not optional: the shader
 * divides a world position by it to get the sampling coordinate, so a wrong
 * number is a pattern that stretches or seams. These are the sizes the
 * reference client declares for the same images, kept exactly.
 *
 * `BACKGROUND_NONE` is the absence of one rather than an image of black: the
 * renderer drops the background pass entirely, which is cheaper than sampling a
 * texture that is the same colour as the clear.
 */
typedef struct background_def {
  const char* id;    /* stable, stored in settings and never reordered */
  const char* label; /* what a player sees */
  const char* path;  /* NULL for the ones that draw nothing */
  float tile_w;
  float tile_h;
} background_def;

enum {
  BACKGROUND_WYRM = 0,
  BACKGROUND_NONE = 1,
  BACKGROUND_BLACK = 22,
};

/**
 * Order is the grid order and the stored value, so entries are only ever
 * appended. Wyrm's own first, then nothing, then the imported set.
 */
static const background_def BACKGROUNDS[] = {
    {"wyrm", "Wyrm", "app/res/textures/background_4k.png", 4096.0f, 3548.0f},
    {"none", "None", NULL, 512.0f, 512.0f},
    {"classic", "Classic", "app/res/textures/backgrounds/bgee_classic.png", 599.0f, 519.0f},
    {"bgee2", "Slither", "app/res/textures/backgrounds/bgee2.png", 800.0f, 800.0f},
    {"asanoha", "Asanoha", "app/res/textures/backgrounds/bg_asanoha.png", 600.0f, 520.0f},
    {"seigaiha", "Seigaiha", "app/res/textures/backgrounds/bg_seigaiha.png", 801.0f, 810.0f},
    {"graygrid", "Grey grid", "app/res/textures/backgrounds/bg_graygrid.png", 768.0f, 768.0f},
    {"rizz", "Rizz", "app/res/textures/backgrounds/bg_rizz.png", 800.0f, 794.0f},
    {"usastar", "Stars", "app/res/textures/backgrounds/bg_usastar.png", 599.0f, 519.0f},
    {"circuits", "Circuits", "app/res/textures/backgrounds/bg_circuits.png", 800.0f, 800.0f},
    {"circuits2", "Circuits II", "app/res/textures/backgrounds/bg_circuits2.png", 800.0f, 800.0f},
    {"hexice", "Hex ice", "app/res/textures/backgrounds/bg_hexice.png", 800.0f, 800.0f},
    {"hexb", "Hex", "app/res/textures/backgrounds/bg_hexB.png", 800.0f, 693.0f},
    {"hearts", "Hearts", "app/res/textures/backgrounds/bg_hearts.png", 800.0f, 781.0f},
    {"leaves", "Leaves", "app/res/textures/backgrounds/bg_leaves.png", 800.0f, 800.0f},
    {"paint", "Paint", "app/res/textures/backgrounds/bg_paint.png", 800.0f, 800.0f},
    {"snakey", "Snakey", "app/res/textures/backgrounds/bg_snakey.png", 800.0f, 800.0f},
    {"stainedglass", "Stained glass", "app/res/textures/backgrounds/bg_stainedglass.png", 800.0f, 800.0f},
    {"kitties", "Kitties", "app/res/textures/backgrounds/bg_kitties.png", 800.0f, 786.0f},
    {"bluecube", "Blue cube", "app/res/textures/backgrounds/bg_bluecube.png", 872.0f, 882.0f},
    {"purplecube", "Purple cube", "app/res/textures/backgrounds/bg_purplecube.png", 872.0f, 882.0f},
    {"redcube", "Red cube", "app/res/textures/backgrounds/bg_redcube.png", 872.0f, 882.0f},
    /* Wyrm's own (OM, 2026-10-01). Black is assist mode's floor for any mode:
       the pass draws at full opacity with no colour, so it is true black (None
       lets the arena's slate clear show instead). The seven tiles come from
       tools/generate-focus-backgrounds.py; their sizes put them at their
       designed world size at the default bg_scale (599/4096). */
    {"black", "Black", NULL, 512.0f, 512.0f},
    {"wyrm_midnight", "Midnight", "app/res/textures/backgrounds/wyrm_midnight.png", 3790.04f, 3282.27f},
    {"wyrm_carbon", "Carbon", "app/res/textures/backgrounds/wyrm_carbon.png", 2954.04f, 2954.04f},
    {"wyrm_abyss", "Abyss", "app/res/textures/backgrounds/wyrm_abyss.png", 7002.18f, 7002.18f},
    {"wyrm_nebula", "Nebula", "app/res/textures/backgrounds/wyrm_nebula.png", 7002.18f, 7002.18f},
    {"wyrm_dotgrid", "Dot grid", "app/res/textures/backgrounds/wyrm_dotgrid.png", 2735.23f, 2735.23f},
    {"wyrm_contours", "Contours", "app/res/textures/backgrounds/wyrm_contours.png", 7002.18f, 7002.18f},
    {"wyrm_scales", "Scales", "app/res/textures/backgrounds/wyrm_scales.png", 3282.27f, 3282.27f},
    /* Round two (OM, 2026-10-15): nine dark and ten soft mid-tone floors,
       Wyrm Android/tools/floors-2026-10-15 (append only). */
    {"wyrm_ember", "Ember", "app/res/textures/backgrounds/wyrm_ember.png", 5251.63f, 5251.63f},
    {"wyrm_dunes", "Dunes", "app/res/textures/backgrounds/wyrm_dunes.png", 7002.18f, 7002.18f},
    {"wyrm_moss", "Moss", "app/res/textures/backgrounds/wyrm_moss.png", 5251.63f, 5251.63f},
    {"wyrm_cells", "Cells", "app/res/textures/backgrounds/wyrm_cells.png", 6154.26f, 6154.26f},
    {"wyrm_aurora", "Aurora", "app/res/textures/backgrounds/wyrm_aurora.png", 7002.18f, 7002.18f},
    {"wyrm_facets", "Facets", "app/res/textures/backgrounds/wyrm_facets.png", 5470.45f, 5470.45f},
    {"wyrm_frost", "Frost", "app/res/textures/backgrounds/wyrm_frost.png", 5251.63f, 5251.63f},
    {"wyrm_tartan", "Tartan", "app/res/textures/backgrounds/wyrm_tartan.png", 3282.27f, 3282.27f},
    {"wyrm_reptile", "Reptile", "app/res/textures/backgrounds/wyrm_reptile.png", 6154.26f, 6154.26f},
    {"wyrm_sage", "Sage", "app/res/textures/backgrounds/wyrm_sage.png", 3501.09f, 3501.09f},
    {"wyrm_shore", "Shore", "app/res/textures/backgrounds/wyrm_shore.png", 7002.18f, 7002.18f},
    {"wyrm_terrazzo", "Terrazzo", "app/res/textures/backgrounds/wyrm_terrazzo.png", 5251.63f, 5251.63f},
    {"wyrm_mist", "Mist", "app/res/textures/backgrounds/wyrm_mist.png", 7002.18f, 7002.18f},
    {"wyrm_lavender", "Lavender", "app/res/textures/backgrounds/wyrm_lavender.png", 3411.04f, 2954.04f},
    {"wyrm_clay", "Clay", "app/res/textures/backgrounds/wyrm_clay.png", 2625.82f, 2625.82f},
    {"wyrm_paper", "Paper", "app/res/textures/backgrounds/wyrm_paper.png", 2735.23f, 2735.23f},
    {"wyrm_seafoam", "Seafoam", "app/res/textures/backgrounds/wyrm_seafoam.png", 3501.09f, 1750.54f},
    {"wyrm_marble", "Marble", "app/res/textures/backgrounds/wyrm_marble.png", 7002.18f, 7002.18f},
    {"wyrm_pebbles", "Pebbles", "app/res/textures/backgrounds/wyrm_pebbles.png", 5251.63f, 5251.63f},
};

enum { NUM_BACKGROUNDS = (int)(sizeof(BACKGROUNDS) / sizeof(BACKGROUNDS[0])) };

/** Any stored value that is no longer in the table falls back to Wyrm's own. */
static inline int background_clamp(int index) {
  return (index >= 0 && index < NUM_BACKGROUNDS) ? index : BACKGROUND_WYRM;
}

#endif
