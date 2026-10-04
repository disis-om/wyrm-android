#ifndef AI_MODE_H
#define AI_MODE_H

#include <thermite.h>

void ai_mode_start(tenv* env, const char* nickname);
void ai_mode_start_editor(tenv* env, const char* nickname);
void ai_mode_finish_editor(tenv* env);
bool ai_mode_is_editor(void);
/* Background-size editor: only the real minimap and leaderboard over the AI
   arena, assist off while it is open (OM, 2026-10-01). */
void ai_mode_set_editor_bare(bool bare);
void ai_mode_set_editor_assist(bool on);
bool ai_mode_editor_bare(void);
bool ai_mode_notice_process_event(tenv* env, const void* event);
void ai_mode_tick(tenv* env);
void ai_mode_stop(tenv* env);

#endif
