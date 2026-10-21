#ifndef UI_OVERLAY_H
#define UI_OVERLAY_H

#include <stdbool.h>
#include <thermite.h>

void ui_overlay(tenv* env);

/** The leaderboard is the only interactive HUD block; it owns only its bounds. */
bool ui_overlay_leaderboard_hit(tenv* env, float x, float y);
void ui_overlay_toggle_leaderboard(tenv* env);

/* Snake names on any floor (OM, 2026-10-18; ui_overlay.c). Colours are
   ImGui's packed ImU32, an unsigned int. */
float wyrm_floor_luma_now(tenv* env);
void wyrm_name_inks(float floor_luma, vec3s colour, float alpha,
                    unsigned int* text, unsigned int* shadow);

#endif
