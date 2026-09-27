#ifndef ANDROID_ARROWS_H
#define ANDROID_ARROWS_H

#ifdef VLITHER_ANDROID
#include <stdbool.h>

#include "../user.h"

void android_arrows_create(renderer* r, tcontext* ctx);
void android_arrows_destroy(tcontext* ctx);
bool android_arrows_draw(ImDrawList* dl, float ax, float ay, float dx, float dy,
                         float length, float fade);
float android_arrow_brightness(void);
#endif

#endif
