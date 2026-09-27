#ifndef ANDROID_LOOK_H
#define ANDROID_LOOK_H

#ifdef VLITHER_ANDROID
#include "../user.h"

void android_look_create(renderer* r, tcontext* ctx);
void android_look_destroy(tcontext* ctx);
/* Wyrm's own hair, ears and glasses on the player's snake (never on the wire).
   Called from redraw.c right after slither's own accessory, with the head's
   world centre, angle, world radius (lsz), fade and screen centre offsets. */
void wyrm_look_draw(tenv* env, float hx, float hy, float fang, float lsz,
                    float alpha, float mww2, float mhh2);
#endif

#endif
