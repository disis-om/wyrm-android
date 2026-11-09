#ifndef OEF_H
#define OEF_H

#include <thermite.h>

void time_step(tenv* env);
/* The display's period in ms, from the platform (Swappy's interval, iOS's
   display link rate): a seed for time_step's display-locked frame time. */
void wyrm_frame_period_hint_ms(double ms);
void oef(tenv* env);

/* Ping (OM, 2026-10-12, from the reference client): the ping sent, the pong
   received, and the end of each frame's network poll. */
void ping_mark_sent(tenv* env);
void ping_mark_received(tenv* env);
void ping_poll_done(tenv* env);

#endif