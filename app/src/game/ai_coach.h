#ifndef WYRM_AI_COACH_H
#define WYRM_AI_COACH_H

#include <stdbool.h>
#include "snake.h"

/* Returns true only when a nearby snake creates a predicted collision risk. */
bool ai_coach_steer(const snake* snakes, int count, const snake* me,
                    float* out_angle);

#endif
