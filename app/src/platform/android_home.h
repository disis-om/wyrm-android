#ifndef ANDROID_HOME_H
#define ANDROID_HOME_H

#include <stdbool.h>
#include <stddef.h>

typedef struct tenv tenv;

/*
 * The bridge between Compose Home and the engine.
 *
 * Wyrm's Home is a Compose screen; the engine draws nothing while it is up.
 * Compose asks for things from the Android main thread, the engine answers on
 * its own thread, so every request is parked in a small mailbox here and
 * applied by android_home_poll at the top of a frame.
 */

void android_home_bind_env(tenv* env);

/** Applies anything Compose asked for. Engine thread, once per frame. */
void android_home_poll(tenv* env);

/**
 * Tells Java which screen the engine is on.
 *
 * Java decides from this what Compose shows and which way the phone is held:
 * Home and the skin editor are Compose and portrait. The native lobby and the
 * arena belong to the engine and are landscape.
 */
void android_home_set_screen(int screen);

/** Reports when the arena socket has actually finished closing. */
void android_home_set_arena_port_available(bool available);
/** Tells Compose a Play request was declined without dialling. */
void android_home_arena_enter_ignored(void);

/** Pushes the persisted nickname and the selected arena up to Compose. */
void android_home_publish_state(tenv* env);

/**
 * Tells Compose that an arena would not take us, and for how long to say so.
 *
 * The picker is a few hundred machines that differ only in numbers, and a
 * refusal is a number it has no other way to know: an arena can answer a TCP
 * ping in twenty milliseconds and still hang up on every join. `seconds` is how
 * long the mark has left to run, so the mark expires on Compose's own clock
 * without the engine having to keep telling it.
 */
void android_home_arena_refused(const char* endpoint, int seconds);

/*
 * Arena drops (OM, 2026-09-29).
 *
 * A drop is the arena hanging up on a snake that is alive in a match, when it
 * was not a death, not the player leaving and not a refused entry. The engine
 * only notices and takes a snapshot; the match still ends exactly as before.
 * Java (`data/DropWatch.kt`) adds the network facts and asks the player whether
 * to send a report.
 */

/** A new socket: forgets the last one's close frame and error. */
void android_home_arena_socket_opened(void);
/** The arena's WebSocket CLOSE frame: 2-byte code, then the reason text. */
void android_home_arena_close_frame(const char* payload, size_t len);
/** The transport error Mongoose raised for the current socket. */
void android_home_arena_error(const char* text);
/**
 * Called just before `android_home_notify_death` where a close becomes a death.
 * Decides for itself whether this close is a drop, and snapshots at most once
 * per life, so the two call sites cannot report one drop twice.
 */
void android_home_arena_drop(tenv* env);
/* A 'v' death packet within a moment of spawning is a drop too. Call it before
   the 'v' is handled (before `android_home_notify_death`). */
void android_home_arena_fast_death(tenv* env, int death_code);
/* What the join carried, for a report on a socket that dies before spawn. */
void android_home_arena_join_sent(int packet_bytes, int skin_bytes,
                                  int skin_runs, int nick_bytes,
                                  bool custom_skin);
/* The configuration timeout closed this socket (our doing, not the arena's). */
void android_home_arena_note_timeout(void);
/* The socket closed after the WebSocket upgrade and before our snake spawned,
   and not because we left. Reports once per socket. */
void android_home_arena_prespawn_close(tenv* env, const char* phase);

/*
 * Death.
 *
 * There is no death card. The native lobby is the return from a match, and
 * Play lives there so connect never crosses JNI.
 */
void android_home_notify_death(tenv* env);

/** Opens a new once-per-life run receipt when the player's snake spawns. */
void android_home_begin_life(void);

/**
 * Play from the lobby is a new life.
 *
 * Death flags outlive the socket: `run_recorded` is only cleared on spawn,
 * and a join that never spawned left it set, so the next `'v'` was ignored.
 * Lobby Play must start clean, like slither resetting `dead_mtm` before
 * `connect()`.
 */
void android_home_reset_death(void);

/** Whether the death card is currently up. */
bool android_home_death_active(void);

/** Whether a death is still being dealt with — the wait, or the card itself. */
bool android_home_death_pending(void);
/* Phase 3 H: the HUD performance chip, "" for none (set by the app). */
void android_home_set_performance_chip(const char* text);
const char* android_home_performance_chip(void);
float android_home_death_opacity(void);
void android_home_advance_death(tenv* env, float vfr);

#endif
