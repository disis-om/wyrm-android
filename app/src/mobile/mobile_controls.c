#include "mobile_controls.h"
#include "../game/sheet_text.h" /* the zoom bar's - and + (OM, 2026-11-01) */

#include <math.h>
#include <string.h>

#include "../constants.h"
#include "../game/arena_theme.h"
#include "../game/ai_mode.h"
#include "../game/ui_overlay.h"
#include "../platform/android_team.h"
#include "../platform/android_arrows.h"
#include "mobile_hotkeys.h"
#include "../platform/android_home.h"
#include "../user.h"

#ifdef WYRM_SDL_JNI
#include <SDL3/SDL.h>
#endif

static float clampf(float value, float lo, float hi) {
  return value < lo ? lo : (value > hi ? hi : value);
}

static float control_scale(tenv* env) {
  /* The short side: the height sideways, the width upright (portrait play). */
  int short_side = env->wnd->size[0] < env->wnd->size[1] ? env->wnd->size[0]
                                                         : env->wnd->size[1];
  return clampf(short_side / 720.0f, 1.0f, 1.55f);
}

/* Upright play steers with the arrow only (OM, 2026-10-02): both joystick
   modes are off while the phone is held upright, whatever is chosen for
   sideways play (that stored choice is kept). Every reader of the steering
   mode goes through here; nothing is sent differently. */
int mobile_controls_steering_mode(tenv* env) {
  if (env->wnd->size[1] > env->wnd->size[0] &&
      env->usr->usrs.mobile_controls.joystick_mode != MOBILE_STEERING_CLASSIC)
    return MOBILE_STEERING_ARROW;
  return env->usr->usrs.mobile_controls.joystick_mode;
}

/*
 * Near Original (OM, 2026-10-02): slither's own controls, from the original
 * game's Main.as. Its unit is the short side / 480 (`force_game_scale`).
 * Fixed joystick at (150u, H - 130u), boost at (W - 140u, H - 140u) on a
 * phone, (100u, H - 100u) and (W - 100u, H - 100u) on a larger screen
 * (Main.as isPhone, 2026-10-30), both
 * mirrored when the joystick is on the right (Wyrm's Handedness = the
 * original's flip). Display and touch only.
 */
static float original_unit(tenv* env) {
  int short_side = env->wnd->size[0] < env->wnd->size[1] ? env->wnd->size[0]
                                                         : env->wnd->size[1];
  return short_side / 480.0f;
}

/*
 * Play feel (OM, 2026-10-05), set by the app (Settings > Controls) through
 * mobile_controls_set_play_feel:
 *  - original_arrow: slither's own arrow motion, number for number (Main.as).
 *    Near Original always moves like this; Wyrm's arrow does when
 *    "Customise arrow movement" is off.
 *  - look_ahead: slither's look ahead (Main.as `look_ahead`), both modes.
 *  - zoom_spring: the zoom bar as a spring: the knob rests in the middle,
 *    toward + zooms in, toward - zooms out, and it springs back on release.
 * Plain ints: written from the app's thread, read once a frame here.
 */
static volatile int feel_original_arrow = 0;
static volatile int feel_look_ahead = 0;
static volatile int feel_zoom_spring = 0;

void mobile_controls_set_play_feel(bool original_arrow, bool look_ahead,
                                   int zoom_style) {
  feel_original_arrow = original_arrow ? 1 : 0;
  feel_look_ahead = look_ahead ? 1 : 0;
  feel_zoom_spring = zoom_style == 1 ? 1 : 0;
}

/* Whether the arrow moves exactly as slither's does. */
static bool arrow_original_motion(void) {
  return android_home_near_original() || feel_original_arrow != 0;
}

/* The spring zoom knob: -1 (all the way to -) .. 1 (all the way to +). */
static float zoom_spring_t = 0.0f;

/* Look ahead (Main.as `view_lav`, `lav_d`), in the original's units. */
static float look_lav = 0.0f;
static float look_lav_d = 75.0f;

/* The player's own snake while alive, or NULL. */
static snake* feel_own_snake(tenv* env) {
  game_data* gdata = &env->usr->gdata;
  int count = tdarray_length(gdata->data.snakes);
  if (count <= 0) return NULL;
  snake* own = gdata->data.snakes + (count - 1);
  return own->local_player ? own : NULL;
}

/*
 * Look ahead, once a frame (Main.as 12891-12950). Sideways the camera moves
 * up or down toward where the snake is heading (sin of its eased angle,
 * squared with its sign), upright left or right (cos); 75 units ahead, 125
 * while boosting, eased by slither's own p005 / p01 tables and never more than
 * 160 a step. Off: no offset at all, and it starts from zero when turned on.
 */
void mobile_controls_look_ahead_step(tenv* env) {
  snake* own = feel_own_snake(env);
  if (!feel_look_ahead || !own) {
    look_lav = 0.0f;
    look_lav_d = 75.0f;
    return;
  }
  if (own->dead) return;
  game_data* gdata = &env->usr->gdata;
  float vfr = gdata->data.vfr;
  if (!(vfr > 0.0f)) vfr = 0.0f;
  int vfrb = gdata->data.vfrb;
  if (vfrb < 0) vfrb = 0;
  if (vfrb > 120) vfrb = 120;
  bool upright = env->wnd->size[1] > env->wnd->size[0];
  float s = upright ? cosf(own->eang) : sinf(own->eang);
  s = s < 0.0f ? -(s * s) : s * s;
  bool wmd = gdata->data.wmd || mobile_controls_boost_down(env);
  if (wmd) {
    if (look_lav_d != 125.0f) {
      look_lav_d += vfr * 0.5f;
      if (look_lav_d >= 125.0f) look_lav_d = 125.0f;
    }
  } else if (look_lav_d != 75.0f) {
    look_lav_d -= vfr * 0.25f;
    if (look_lav_d <= 75.0f) look_lav_d = 75.0f;
  }
  float step = s * look_lav_d - look_lav;
  if (step < -160.0f) step = -160.0f;
  if (step > 160.0f) step = 160.0f;
  float k = wmd ? 0.01f : 0.005f;
  look_lav += step * (1.0f - powf(1.0f - k, (float)vfrb));
}

/* Where the camera sits ahead of the snake, in screen pixels (the snake is
   drawn this far the other way from the middle). */
void mobile_controls_look_ahead_offset(tenv* env, float* x, float* y) {
  *x = 0.0f;
  *y = 0.0f;
  if (!feel_look_ahead) return;
  float px = look_lav * original_unit(env);
  if (env->wnd->size[1] > env->wnd->size[0])
    *x = px;
  else
    *y = px;
}

static bool original_joystick_right(tenv* env) {
  return env->usr->usrs.mobile_controls.handedness != MOBILE_LEFT_HANDED;
}

/* Main.as `isPhone()` (OM, 2026-10-30): the long side in inches under 7.
   Phones get the original's 150/130 joystick and 140/140 boost, larger
   screens its 100/100 and 100/100 (map top left). Inches = long side in
   pixels / (display scale x 160 on Android, x 163 on iOS). Unknown scale =
   phone, the original's case for every handset. */
static bool original_is_phone(tenv* env) {
  int long_side = env->wnd->size[0] > env->wnd->size[1] ? env->wnd->size[0]
                                                        : env->wnd->size[1];
  float scale = env->wnd->handle ? SDL_GetWindowDisplayScale(env->wnd->handle)
                                 : 0.0f;
  if (!(scale > 0.0f) || long_side <= 0) return true;
#ifdef __APPLE__
  const float dpi = scale * 163.0f;
#else
  const float dpi = scale * 160.0f;
#endif
  return (float)long_side / dpi < 7.0f;
}

static void original_joystick_centre(tenv* env, float* x, float* y) {
  /* Moved in the layout editor (OM, 2026-11-01): that place. */
  const user_settings_ext* ext = &env->usr->usrs.ext;
  if (ext->near_joy_x >= 0.0f && ext->near_joy_y >= 0.0f) {
    *x = clampf(ext->near_joy_x, 0.0f, 1.0f) * env->wnd->size[0];
    *y = clampf(ext->near_joy_y, 0.0f, 1.0f) * env->wnd->size[1];
    return;
  }
  float u = original_unit(env);
  bool phone = original_is_phone(env);
  float in = phone ? 150.0f : 100.0f, up = phone ? 130.0f : 100.0f;
  *x = original_joystick_right(env) ? env->wnd->size[0] - in * u : in * u;
  *y = env->wnd->size[1] - up * u;
}

static void original_boost_centre(tenv* env, float* x, float* y) {
  float u = original_unit(env);
  float in = original_is_phone(env) ? 140.0f : 100.0f;
  *x = original_joystick_right(env) ? in * u : env->wnd->size[0] - in * u;
  *y = env->wnd->size[1] - in * u;
}

/* The boost button's alpha (0.2 idle, 0.4 boosting) and the arrow's boost
   glow (`accel_a`, `accel_fr`), stepped once a frame. */
static float original_boost_alpha = 0.3f;

/* Arrow mode boosts like slither's own (OM, 2026-10-31; Main.as control mode 2;
   Wyrm iOS/Scripts/engine_arrow_boost_2026_10_31.py): a double tap boosts while
   that finger keeps steering. When and where the last finger came down. */
/* Main.as mode 1: mean absolute positions of every non-button finger,
   relative to the screen centre. IDs are owned until up/cancel, never timed out. */
static struct { uint64_t id; float x, y; bool down, ignored; } classic_fingers[16];
static uint64_t classic_boost_finger;
static bool arrow_tap_boost;
static Uint64 arrow_last_down_ms;
static uint64_t arrow_last_down_finger;
static bool arrow_last_tap_released;
static bool boost_secondary;
static bool boost_steering_promoted;
static float boost_motion_total, boost_motion_last[2];
static float arrow_last_down[2];

/* Wyrm's arrow steering (not Near Original): boost by a second finger, a double
   tap, or the fixed button; never a button under the finger. */
static bool wyrm_arrow_mode(tenv* env) {
  return !android_home_near_original() &&
         mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW;
}

/* Main.as: a touch within 400 ms and 92 units of the last one, alone on the glass. */
static void arrow_note_down(tenv* env, float x, float y, bool alone, uint64_t finger) {
  Uint64 now = SDL_GetTicks();
  float reach = 92.0f * original_unit(env);
  arrow_tap_boost = alone && arrow_last_tap_released && arrow_last_down_ms != 0 &&
      now - arrow_last_down_ms < 400 && fabsf(x-arrow_last_down[0]) < reach &&
      fabsf(y-arrow_last_down[1]) < reach;
  arrow_last_down_ms = alone ? now : 0;
  arrow_last_down_finger = finger;
  arrow_last_tap_released = false;
  arrow_last_down[0] = x; arrow_last_down[1] = y;
}
static float original_accel_a = 0.0f;
static float original_accel_fr = 0.0f;

static void normalized_position(tenv* env, float nx, float ny, float* x,
                                float* y) {
  *x = clampf(nx, 0.0f, 1.0f) * env->wnd->size[0];
  *y = clampf(ny, 0.0f, 1.0f) * env->wnd->size[1];
}

static void store_position(tenv* env, float x, float y, float* nx, float* ny) {
  *nx = clampf(x / env->wnd->size[0], 0.0f, 1.0f);
  *ny = clampf(y / env->wnd->size[1], 0.0f, 1.0f);
}

static bool inside_circle(float x, float y, float cx, float cy, float radius) {
  float dx = x - cx;
  float dy = y - cy;
  return dx * dx + dy * dy <= radius * radius;
}

static void zoom_geometry(tenv* env, float* cx, float* cy, float* length,
                          float* thickness) {
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  float scale = control_scale(env);
  normalized_position(env, cfg->zoom_x, cfg->zoom_y, cx, cy);
  *length = 260.0f * scale * cfg->zoom_length;
  *thickness = 58.0f * scale;
}

static bool inside_zoom(tenv* env, float x, float y) {
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  if (!cfg->zoom_enabled) return false;
  float cx, cy, length, thickness;
  zoom_geometry(env, &cx, &cy, &length, &thickness);
  if (cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL)
    return fabsf(x - cx) <= length * 0.5f && fabsf(y - cy) <= thickness;
  return fabsf(y - cy) <= length * 0.5f && fabsf(x - cx) <= thickness;
}

static void set_zoom_from_touch(tenv* env, float x, float y) {
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  float cx, cy, length, thickness;
  zoom_geometry(env, &cx, &cy, &length, &thickness);
  /* A flipped bar reads the finger mirrored around its centre (2026-10-30). */
  if (env->usr->usrs.ext.zoom_flip) {
    x = 2.0f * cx - x;
    y = 2.0f * cy - y;
  }
  if (feel_zoom_spring) {
    /* The spring bar (OM, 2026-10-05): the finger pulls the knob from the
       middle; + is right (sideways bar) or up (upright bar). */
    float pull = cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL
                     ? (x - cx) / (length * 0.5f)
                     : (cy - y) / (length * 0.5f);
    zoom_spring_t = clampf(pull, -1.0f, 1.0f);
    return;
  }
  float t = cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL
                ? (x - (cx - length * 0.5f)) / length
                : 1.0f - (y - (cy - length * 0.5f)) / length;
  t = clampf(t, 0.0f, 1.0f);
  env->usr->gdata.data.ms_zoom =
      expf(logf(MAX_ZOOM_OUT) + t * (logf(MAX_ZOOM_IN) - logf(MAX_ZOOM_OUT)));
}

static void reset_touches(mobile_controls_state* state) {
  arrow_tap_boost = false;
  arrow_last_down_ms = 0;
  arrow_last_tap_released = false;
  boost_secondary = false;
  boost_steering_promoted = false; boost_motion_total = 0;
  memset(classic_fingers, 0, sizeof(classic_fingers));
  state->joystick_down = false;
  state->boost_down = false;
  state->zoom_down = false;
  state->edit_down = false;
  state->ui_scroll_down = false;
  state->arrow_drag_distance = 0.0f;
  state->edit_target = MOBILE_EDIT_NONE;
}

static void sync_touch_policy(tenv* env) {
  static int previous = -1;
  int policy = mobile_controls_steering_mode(env) |
      (env->usr->usrs.mobile_controls.boost_mode == MOBILE_BOOST_FIXED ? 4 : 0) |
      (android_home_near_original() ? 8 : 0) |
      (env->usr->usrs.ext.arrow_double_tap_boost ? 16 : 0);
  if (previous != -1 && previous != policy) reset_touches(&env->usr->mobile_controls);
  previous = policy;
}

/* Dear ImGui's SDL backend receives Android touch as pointer input, but it
   does not turn a one-finger drag into a scroll gesture. Feed a small wheel
   delta while menus are open so every ImGui child list behaves like a native
   mobile scroll view. Gameplay/editor touches keep their dedicated routing. */
static void process_ui_scroll_touch(mobile_controls_state* state,
                                    const SDL_Event* event, uint64_t finger,
                                    float y) {
  if (event->type == SDL_EVENT_FINGER_DOWN) {
    if (!state->ui_scroll_down) {
      state->ui_scroll_down = true;
      state->ui_scroll_finger = finger;
      state->ui_scroll_last_y = y;
    }
    return;
  }
  if (event->type == SDL_EVENT_FINGER_MOTION && state->ui_scroll_down &&
      state->ui_scroll_finger == finger) {
    float delta = y - state->ui_scroll_last_y;
    state->ui_scroll_last_y = y;
    if (fabsf(delta) >= 0.5f)
      ImGuiIO_AddMouseWheelEvent(igGetIO_Nil(), 0.0f, delta / 42.0f);
    return;
  }
  if ((event->type == SDL_EVENT_FINGER_UP ||
       event->type == SDL_EVENT_FINGER_CANCELED) &&
      state->ui_scroll_down && state->ui_scroll_finger == finger) {
    state->ui_scroll_down = false;
  }
}

void mobile_controls_init(tenv* env) {
  memset(&env->usr->mobile_controls, 0, sizeof(env->usr->mobile_controls));
#ifdef WYRM_SDL_JNI
  /* UI and gameplay consume SDL finger events directly. Do not ask SDL to
     generate duplicate mouse events for each touch. */
  SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS, "0");
#endif
}

void mobile_controls_set_handedness(tenv* env, int handedness) {
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  handedness = handedness == MOBILE_LEFT_HANDED ? MOBILE_LEFT_HANDED
                                                : MOBILE_RIGHT_HANDED;
  if (cfg->handedness == handedness) return;
  cfg->handedness = handedness;
  cfg->joystick_x = 1.0f - cfg->joystick_x;
  cfg->boost_x = 1.0f - cfg->boost_x;
  reset_touches(&env->usr->mobile_controls);
}

void mobile_controls_reset_layout(tenv* env) {
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  bool left = cfg->handedness == MOBILE_LEFT_HANDED;
  cfg->joystick_x = left ? 0.20f : 0.80f;
  cfg->joystick_y = 0.72f;
  cfg->boost_x = left ? 0.82f : 0.18f;
  cfg->boost_y = 0.72f;
  cfg->zoom_x = 0.50f;
  cfg->zoom_y = 0.88f;
}

void mobile_controls_begin_editor(tenv* env) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  state->edit_backup = env->usr->usrs.mobile_controls;
  state->editor_active = true;
  reset_touches(state);
}

void mobile_controls_finish_editor(tenv* env, bool save) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  if (!save) env->usr->usrs.mobile_controls = state->edit_backup;
  state->editor_active = false;
  reset_touches(state);
  if (save) save_user_settings(&env->usr->usrs);
}

/*
 * The joystick knob follows the snake (OM, 2026-10-01).
 *
 * The own snake's heading, as the head is drawn (`ehang`, the same smoothed
 * angle the head bead turns with). A dynamic joystick starts with its knob on
 * that side instead of in the centre, and a fixed one rests there between
 * touches, so the stick always shows where the snake is going. Steering is
 * unchanged: the first frame of a new touch aims exactly where the snake
 * already goes, and moving the finger steers from there.
 */
/* Controls › Joystick knob (OM, 2026-10-13): back to the centre instead of
   following the snake. Any thread; read each frame. Near Original draws
   slither's own joystick and is not touched. */
static volatile int knob_centre = 0;

void mobile_controls_set_knob_centre(bool centre) { knob_centre = centre ? 1 : 0; }

static bool knob_follows_snake(void) {
  return !knob_centre || android_home_near_original();
}

#ifdef WYRM_SDL_JNI
#include <jni.h>
JNIEXPORT void JNICALL
Java_com_wyrm_omrajput_WyrmActivity_nativeSetJoystickKnob(JNIEnv* jenv,
                                                          jclass clazz,
                                                          jboolean centre) {
  (void)jenv;
  (void)clazz;
  mobile_controls_set_knob_centre(centre == JNI_TRUE);
}
#endif

static bool joystick_heading(tenv* env, float* hx, float* hy) {
  game_data* gdata = &env->usr->gdata;
  snake* own = get_snake(gdata, gdata->data.snake_id);
  if (!own || own->dead) return false;
  *hx = cosf(own->ehang);
  *hy = sinf(own->ehang);
  return true;
}

#ifdef WYRM_SDL_JNI
static void update_joystick(tenv* env, float x, float y) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  float radius = 92.0f * control_scale(env) * cfg->joystick_size;
  float dx = x - state->joystick_origin[0];
  float dy = y - state->joystick_origin[1];
  float length = sqrtf(dx * dx + dy * dy);
  if (length > radius && length > 0.0f) {
    dx *= radius / length;
    dy *= radius / length;
  }
  state->joystick_axis[0] = dx / radius;
  state->joystick_axis[1] = dy / radius;
  if (fabsf(state->joystick_axis[0]) > 0.08f ||
      fabsf(state->joystick_axis[1]) > 0.08f)
    state->aim_valid = true;
}

/**
 * The distance the steering vector is seeded at, in screen pixels.
 *
 * `58 * snake.sc * gsc` is slither's own — the one place in Arrow mode where a
 * size-aware "in front of the snake" distance appears. Seeding there rather
 * than at zero is what stops the first touch snapping the heading.
 */
static float arrow_seed_distance(tenv* env) {
  game_data* gdata = &env->usr->gdata;
  mobile_arrow_settings* arrow = &env->usr->usrs.arrow_controls;
  int count = tdarray_length(gdata->data.snakes);
  float sc = count > 0 ? gdata->data.snakes[count - 1].sc : 1.0f;
  /* slither's own start distance (Main.as touch begin): 58 x snake.sc x its
     zoom for this length (dgsc = 0.35 + 0.35 / max(1, (sct + 16) / 36)) in its
     unit, with no floor (OM, 2026-10-05). */
  if (arrow_original_motion()) {
    int sct = count > 0 ? gdata->data.snakes[count - 1].sct : 2;
    float wanted = (sct + 16) / 36.0f;
    float dgsc = 0.35f + 0.35f / (wanted > 1.0f ? wanted : 1.0f);
    return 58.0f * sc * dgsc * original_unit(env);
  }
  float separation = arrow->separation;
  float d = 58.0f * sc * gdata->data.gsc * separation;
  float least = 40.0f * control_scale(env);
  return d < least ? least : d;
}

/** Lays the steering vector out along the heading the snake already has. */
static void arrow_seed(tenv* env, float x, float y) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  game_data* gdata = &env->usr->gdata;
  int count = tdarray_length(gdata->data.snakes);
  float heading = count > 0 ? gdata->data.snakes[count - 1].ang : 0.0f;
  /* slither seeds along the last steering angle (`twang`), not the snake's. */
  if (arrow_original_motion() && state->aim_valid &&
      (state->joystick_axis[0] != 0.0f || state->joystick_axis[1] != 0.0f))
    heading = atan2f(state->joystick_axis[1], state->joystick_axis[0]);
  float d = arrow_seed_distance(env);
  state->arrow_vec[0] = cosf(heading) * d;
  state->arrow_vec[1] = sinf(heading) * d;
  state->arrow_draw[0] = state->arrow_vec[0];
  state->arrow_draw[1] = state->arrow_vec[1];
  state->arrow_last[0] = x;
  state->arrow_last[1] = y;
  state->arrow_dead = 0.0f;
  state->joystick_axis[0] = cosf(heading);
  state->joystick_axis[1] = sinf(heading);
  state->aim_valid = true;
}

/**
 * One frame of arrow steering.
 *
 * The vector takes the *increment* since the last report, not the offset from
 * where the finger landed. That difference is the whole scheme: a stick asks
 * where your thumb is, this asks how far it has moved, so the heading carries
 * on from wherever it was instead of jumping to wherever you happened to touch.
 * There is no dead zone, no maximum, and no smoothing of the angle — slither
 * smooths only the arrow it draws, and smoothing the heading is what made this
 * feel like a stick with lag.
 */
/* Sensitivity (OM, 2026-10-18): the finger's move times this. 0 on the
   slider = 1x, the original; full (100%) = ARROW_SENS_MAX, which is what the
   first build's half-way was (sqrt 12); exponential between. */
#define ARROW_SENS_MAX 3.4641f
static float arrow_gain(tenv* env) {
  if (arrow_original_motion()) return 1.0f;
  float s = clampf(env->usr->usrs.ext.arrow_sens, 0.0f, 1.0f);
  return powf(ARROW_SENS_MAX, s);
}

static void update_arrow(tenv* env, float x, float y) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  float gain = arrow_gain(env);
  state->arrow_vec[0] += (x - state->arrow_last[0]) * gain;
  state->arrow_vec[1] += (y - state->arrow_last[1]) * gain;
  state->arrow_last[0] = x;
  state->arrow_last[1] = y;

  float length = sqrtf(state->arrow_vec[0] * state->arrow_vec[0] +
                       state->arrow_vec[1] * state->arrow_vec[1]);
  if (length < 0.001f) return;
  state->joystick_axis[0] = state->arrow_vec[0] / length;
  state->joystick_axis[1] = state->arrow_vec[1] / length;
  state->aim_valid = true;
  state->arrow_drag_distance =
      clampf(length / (260.0f * control_scale(env)), 0.0f, 1.0f);
}

static void edit_touch_down(tenv* env, uint64_t finger, float x, float y) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  float scale = control_scale(env);
  float jx, jy, bx, by;
  normalized_position(env, cfg->joystick_x, cfg->joystick_y, &jx, &jy);
  normalized_position(env, cfg->boost_x, cfg->boost_y, &bx, &by);
  if (mobile_controls_steering_mode(env) != MOBILE_STEERING_ARROW &&
      inside_circle(x, y, jx, jy, 120.0f * scale * cfg->joystick_size))
    state->edit_target = MOBILE_EDIT_JOYSTICK;
  else if (inside_circle(x, y, bx, by, 105.0f * scale * cfg->boost_size))
    state->edit_target = MOBILE_EDIT_BOOST;
  else if (inside_zoom(env, x, y))
    state->edit_target = MOBILE_EDIT_ZOOM;
  else
    return;
  state->edit_down = true;
  state->edit_finger = finger;
}

static void edit_touch_move(tenv* env, float x, float y) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  switch (state->edit_target) {
    case MOBILE_EDIT_JOYSTICK:
      store_position(env, x, y, &cfg->joystick_x, &cfg->joystick_y);
      break;
    case MOBILE_EDIT_BOOST:
      store_position(env, x, y, &cfg->boost_x, &cfg->boost_y);
      break;
    case MOBILE_EDIT_ZOOM:
      store_position(env, x, y, &cfg->zoom_x, &cfg->zoom_y);
      break;
    default: break;
  }
}

/* The original's steering: the angle from the fixed joystick centre to the
   finger, wherever it is on the joystick's half. */
static void original_aim(tenv* env, float x, float y) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  float cx, cy;
  original_joystick_centre(env, &cx, &cy);
  float dx = x - cx;
  float dy = y - cy;
  float length = sqrtf(dx * dx + dy * dy);
  if (length < 0.5f) return;
  state->joystick_axis[0] = dx / length;
  state->joystick_axis[1] = dy / length;
  state->aim_valid = true;
}

/* No position/hit-test: the original owner receives up/cancel across panels. */
static void release_owned_touch(mobile_controls_state* state, uint64_t finger) {
  if (state->joystick_down && state->joystick_finger == finger) {
    if (arrow_last_down_finger == finger && !arrow_last_tap_released) arrow_last_down_ms = 0;
    state->joystick_down = false; arrow_tap_boost = false;
    boost_steering_promoted = false; boost_motion_total = 0;
  }
  if (state->boost_down && state->boost_finger == finger) {
    state->boost_down = false; boost_secondary = false;
    boost_steering_promoted = false; boost_motion_total = 0;
  }
  if (state->zoom_down && state->zoom_finger == finger) state->zoom_down = false;
}

static void take_boost(mobile_controls_state* state, uint64_t finger, float x, float y, bool secondary) {
  state->boost_down = true; state->boost_finger = finger;
  state->boost_origin[0] = x; state->boost_origin[1] = y;
  boost_secondary = secondary;
  boost_steering_promoted = false; boost_motion_total = 0;
  boost_motion_last[0] = x; boost_motion_last[1] = y;
  arrow_last_down_ms = 0; arrow_last_tap_released = false;
}

static bool touch_is_owned(mobile_controls_state* state, uint64_t finger) {
  if ((state->joystick_down && state->joystick_finger == finger) ||
      (state->boost_down && state->boost_finger == finger) ||
      (state->zoom_down && state->zoom_finger == finger)) return true;
  for (int i=0;i<16;++i) if (classic_fingers[i].down && classic_fingers[i].id == finger) return true;
  return false;
}

static bool classic_touch(tenv* env, const SDL_Event* event, uint64_t finger,
                          float x, float y) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  int slot = -1;
  for (int i = 0; i < 16; ++i)
    if (classic_fingers[i].down && classic_fingers[i].id == finger) { slot = i; break; }
  if (event->type == SDL_EVENT_FINGER_DOWN && slot < 0) {
    float bx, by;
    bool original = android_home_near_original();
    if (original) original_boost_centre(env, &bx, &by);
    else normalized_position(env, env->usr->usrs.mobile_controls.boost_x,
                                  env->usr->usrs.mobile_controls.boost_y, &bx, &by);
    bool shown = original || env->usr->usrs.mobile_controls.boost_mode == MOBILE_BOOST_FIXED;
    float radius = original ? 160.0f * original_unit(env) :
        78.0f * control_scale(env) * env->usr->usrs.mobile_controls.boost_size;
    if (shown && !state->boost_down && inside_circle(x, y, bx, by, radius)) {
      take_boost(state,finger,bx,by,false); return true;
    }
    if (!state->zoom_down && inside_zoom(env, x, y)) {
      state->zoom_down = true; state->zoom_finger = finger;
      set_zoom_from_touch(env, x, y); return true;
    }
    for (int i = 0; i < 16; ++i) if (!classic_fingers[i].down) { slot = i; break; }
    if (slot >= 0) {
      bool alone = true;
      for(int i=0;i<16;++i) if(classic_fingers[i].down) alone=false;
      if (original) arrow_note_down(env,x,y,alone && !state->boost_down && !state->zoom_down,finger);
      else { arrow_tap_boost=false; arrow_last_down_ms=0; }
      if(arrow_tap_boost) classic_boost_finger=finger;
      float px=0,py=0; int count=0;
      for(int i=0;i<16;++i) if(classic_fingers[i].down && !classic_fingers[i].ignored) {
        px+=classic_fingers[i].x; py+=classic_fingers[i].y; ++count;
      }
      float dx=count ? x-px/count : 0, dy=count ? y-py/count : 0;
      float reach=180*original_unit(env);
      classic_fingers[slot].ignored = original ? (count && dx*dx+dy*dy>reach*reach) : count > 0;
      classic_fingers[slot].id = finger; classic_fingers[slot].down = true;
    }
  }
  if (slot < 0) return false;
  classic_fingers[slot].x = x; classic_fingers[slot].y = y;
  if (event->type == SDL_EVENT_FINGER_UP || event->type == SDL_EVENT_FINGER_CANCELED)
    { if (arrow_last_down_finger == finger) {
        arrow_last_tap_released = event->type == SDL_EVENT_FINGER_UP;
        if (!arrow_last_tap_released) arrow_last_down_ms=0;
      }
      classic_fingers[slot].down = false;
      if(classic_boost_finger==finger) arrow_tap_boost=false;
    }
  if (!android_home_near_original()) {
    bool steering=false;
    for (int i=0;i<16;++i) if(classic_fingers[i].down && !classic_fingers[i].ignored) steering=true;
    if (!steering) for(int i=0;i<16;++i) if(classic_fingers[i].down) {
      classic_fingers[i].ignored=false; break;
    }
  }
  float dx = 0, dy = 0; int count = 0;
  for (int i = 0; i < 16; ++i) if (classic_fingers[i].down && !classic_fingers[i].ignored) {
    dx += classic_fingers[i].x - env->wnd->size[0] * 0.5f;
    dy += classic_fingers[i].y - env->wnd->size[1] * 0.5f; ++count;
  }
  if (count) {
    float length = sqrtf(dx * dx + dy * dy);
    if (length > 0.5f) {
      state->joystick_axis[0] = dx / length; state->joystick_axis[1] = dy / length;
      state->aim_valid = true;
    }
  }
  return true;
}

/* Near Original touch (Main.as touch begin): sideways the boost button takes
   a finger within 160u of its centre; the arrow takes the first finger
   anywhere; the joystick takes a finger on its half; upright a second finger
   boosts (the original hides the button there). Releases go through the
   normal path. */
/* A booster may steer alone, but a fresh steering contact takes that role back. */
static void reclaim_arrow_steering(mobile_controls_state* state) {
  if (boost_steering_promoted && state->boost_down && state->joystick_down &&
      state->joystick_finger == state->boost_finger) {
    state->joystick_down = false;
    boost_steering_promoted = false; boost_motion_total = 0;
  }
}

static bool original_touch(tenv* env, const SDL_Event* event, uint64_t finger,
                           float x, float y) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  bool upright = env->wnd->size[1] > env->wnd->size[0];
  bool arrow = mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW;
  if (event->type == SDL_EVENT_FINGER_DOWN) {
    if (!state->zoom_down && inside_zoom(env, x, y)) {
      state->zoom_down = true;
      state->zoom_finger = finger;
      set_zoom_from_touch(env, x, y);
      return true;
    }
    if (!upright && !state->boost_down) {
      float bx, by;
      original_boost_centre(env, &bx, &by);
      if (inside_circle(x, y, bx, by, 160.0f * original_unit(env))) {
        take_boost(state,finger,bx,by,false);
        return true;
      }
    }
    if (arrow) {
      reclaim_arrow_steering(state);
      if (!state->joystick_down) {
        state->joystick_down = true;
        state->joystick_finger = finger;
        state->joystick_origin[0] = x;
        state->joystick_origin[1] = y;
        state->arrow_drag_distance = 0.0f;
        arrow_seed(env, x, y);
        if (env->usr->usrs.ext.arrow_double_tap_boost)
          arrow_note_down(env,x,y,!state->boost_down && !state->zoom_down,finger);
        else { arrow_tap_boost=false; arrow_last_down_ms=0; }
        return true;
      }
      if (upright && !state->boost_down && state->joystick_finger != finger) {
        take_boost(state,finger,x,y,true);
      }
      return true;
    }
    /* The half the joystick is on, wherever it was moved (OM, 2026-11-01). */
    float side_x, side_y;
    original_joystick_centre(env, &side_x, &side_y);
    bool on_side = side_x >= env->wnd->size[0] * 0.5f
                       ? x >= env->wnd->size[0] * 0.5f
                       : x < env->wnd->size[0] * 0.5f;
    if (on_side && !state->joystick_down) {
      float cx, cy;
      original_joystick_centre(env, &cx, &cy);
      state->joystick_down = true;
      state->joystick_finger = finger;
      state->joystick_origin[0] = cx;
      state->joystick_origin[1] = cy;
      original_aim(env, x, y);
    }
    return true;
  }
  if (event->type == SDL_EVENT_FINGER_MOTION) {
    if (state->joystick_down && state->joystick_finger == finger) {
      if (arrow)
        update_arrow(env, x, y);
      else
        original_aim(env, x, y);
      return true;
    }
    if (state->zoom_down && state->zoom_finger == finger) {
      set_zoom_from_touch(env, x, y);
      return true;
    }
  }
  return false;
}

static void finish_touch(tenv* env, uint32_t type, uint64_t finger) {
  mobile_controls_state* state=&env->usr->mobile_controls;
    if (arrow_last_down_finger==finger && arrow_last_down_ms) {
      arrow_last_tap_released=type==SDL_EVENT_FINGER_UP;
      if (!arrow_last_tap_released) arrow_last_down_ms=0;
    }
    release_owned_touch(state, finger);
}

static bool wyrm_touch_down(tenv* env, uint64_t finger, float x, float y) {
  mobile_controls_state* state=&env->usr->mobile_controls;
  mobile_control_settings* cfg=&env->usr->usrs.mobile_controls;
  if (touch_is_owned(state,finger)) return true;
  float jx,jy,bx,by;
  normalized_position(env,cfg->joystick_x,cfg->joystick_y,&jx,&jy);
  normalized_position(env,cfg->boost_x,cfg->boost_y,&bx,&by);
  int mode=mobile_controls_steering_mode(env);
  bool fixed=cfg->boost_mode==MOBILE_BOOST_FIXED;
  float mid=env->wnd->size[0]*.5f;
  bool left=mode==MOBILE_JOYSTICK_FIXED ? jx<mid : cfg->handedness==MOBILE_LEFT_HANDED;
  bool own_half=left ? x<mid : x>=mid;
  float scale=control_scale(env);
  if (!state->zoom_down && inside_zoom(env,x,y)) {
    state->zoom_down=true; state->zoom_finger=finger; set_zoom_from_touch(env,x,y); return true;
  }
  if (fixed && !state->boost_down && inside_circle(x,y,bx,by,78*scale*cfg->boost_size)) {
    take_boost(state,finger,bx,by,false); return true;
  }
  if (mode==MOBILE_JOYSTICK_FIXED && !state->joystick_down &&
      (own_half || inside_circle(x,y,jx,jy,125*scale*cfg->joystick_size))) {
    state->joystick_down=true; state->joystick_finger=finger;
    state->joystick_origin[0]=jx; state->joystick_origin[1]=jy;
    update_joystick(env,x,y); return true;
  }
  if (mode==MOBILE_STEERING_ARROW) {
    reclaim_arrow_steering(state);
    if (state->joystick_down && state->joystick_finger!=finger) {
      if (!fixed && !state->boost_down) { take_boost(state,finger,x,y,true); return true; }
      return false;
    }
    if (!state->joystick_down) {
      state->joystick_down=true; state->joystick_finger=finger;
      state->joystick_origin[0]=x; state->joystick_origin[1]=y; state->arrow_drag_distance=0;
      arrow_seed(env,x,y);
      if (!fixed && env->usr->usrs.ext.arrow_double_tap_boost)
        arrow_note_down(env,x,y,!state->boost_down && !state->zoom_down,finger);
      else { arrow_tap_boost=false; arrow_last_down_ms=0; }
      return true;
    }
  } else if (mode==MOBILE_JOYSTICK_DYNAMIC && !state->joystick_down && own_half) {
    state->joystick_down=true; state->joystick_finger=finger;
    state->joystick_origin[0]=x; state->joystick_origin[1]=y;
    float hx,hy;
    if (knob_follows_snake() && joystick_heading(env,&hx,&hy)) {
      float reach=92*scale*cfg->joystick_size;
      state->joystick_origin[0]=x-hx*reach; state->joystick_origin[1]=y-hy*reach;
    }
    update_joystick(env,x,y); return true;
  }
  if (!fixed && mode!=MOBILE_STEERING_ARROW && !own_half && !state->boost_down) {
    take_boost(state,finger,x,y,false); return true;
  }
  return false;
}

/* Main.as trackTouch: an otherwise ignored booster can steer after 74 units
   of accumulated movement when no steering contact remains. Boost ownership
   stays independent, including while the returning contact resumes steering. */
static bool track_boost_motion(tenv* env, uint64_t finger, float x, float y) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  if (!state->boost_down || state->boost_finger != finger) return false;
  float travel = fabsf(x-boost_motion_last[0]) + fabsf(y-boost_motion_last[1]);
  boost_motion_last[0]=x; boost_motion_last[1]=y;
  state->boost_origin[0]=x; state->boost_origin[1]=y;
  if (mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW) {
    if (!state->joystick_down) {
      boost_motion_total += travel;
      if (boost_motion_total > 74.0f * original_unit(env)) {
        state->joystick_down=true; state->joystick_finger=finger;
        state->joystick_origin[0]=x; state->joystick_origin[1]=y;
        state->arrow_drag_distance=0; arrow_seed(env,x,y);
        boost_steering_promoted=true;
      }
    } else if (boost_steering_promoted && state->joystick_finger==finger) {
      update_arrow(env,x,y);
    }
  }
  return true;
}

static void decay_boost_motion(tenv* env) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  if (mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW &&
      state->boost_down && !state->joystick_down) {
    int steps = env->usr->gdata.data.vfrb;
    if (steps < 0) steps=0; else if (steps > 120) steps=120;
    boost_motion_total -= steps * 1.3f * original_unit(env);
    if (boost_motion_total < 0) boost_motion_total=0;
  }
}

bool mobile_controls_process_event(tenv* env, const void* raw_event) {
  const SDL_Event* event = raw_event;
  mobile_controls_state* state = &env->usr->mobile_controls;

#ifdef WYRM_DESKTOP
  /* WYRM_DESKTOP: the snake follows the mouse from the middle of the game
     area; the left button boosts. The keys (boost, turns, hotkeys) are read
     from their bindings by input.c and ai_mode.c. ImGui still sees every
     event. The AI arena's first card closes with a click on its button or
     Enter, and takes every click while it is up (it could only be tapped,
     so on a computer it never closed and held boost and keys off). */
  if (ai_mode_notice_process_event(env, raw_event)) return true;
  /* The team roster and chat window take the left button as a finger: a
     click on the chat header folds it, on its message box opens the composer,
     a drag scrolls. A press there never boosts; the wheel over them scrolls. */
  {
    static bool panel_drag;
    if (event->type == SDL_EVENT_MOUSE_BUTTON_DOWN &&
        event->button.button == SDL_BUTTON_LEFT &&
        android_team_hud_touch(env, SDL_EVENT_FINGER_DOWN, 1ull,
                               event->button.x, event->button.y)) {
      panel_drag = true;
      return true;
    }
    if (panel_drag && event->type == SDL_EVENT_MOUSE_MOTION) {
      android_team_hud_touch(env, SDL_EVENT_FINGER_MOTION, 1ull,
                             event->motion.x, event->motion.y);
      return true;
    }
    if (panel_drag && event->type == SDL_EVENT_MOUSE_BUTTON_UP &&
        event->button.button == SDL_BUTTON_LEFT) {
      panel_drag = false;
      android_team_hud_touch(env, SDL_EVENT_FINGER_UP, 1ull, event->button.x,
                             event->button.y);
      return true;
    }
    if (event->type == SDL_EVENT_MOUSE_WHEEL &&
        android_team_hud_wheel(env, event->wheel.mouse_x,
                               event->wheel.mouse_y, event->wheel.y)) {
      twindow_desktop_take_wheel();
      return true;
    }
  }
  if (event->type == SDL_EVENT_MOUSE_MOTION) {
    float dx = event->motion.x - env->wnd->size[0] * 0.5f;
    float dy = event->motion.y - env->wnd->size[1] * 0.5f;
    float length = sqrtf(dx * dx + dy * dy);
    if (length > 1.0f) {
      state->joystick_axis[0] = dx / length;
      state->joystick_axis[1] = dy / length;
      state->aim_valid = true;
    }
    return false;
  }
  if ((event->type == SDL_EVENT_MOUSE_BUTTON_DOWN ||
       event->type == SDL_EVENT_MOUSE_BUTTON_UP) &&
      event->button.button == SDL_BUTTON_LEFT) {
    state->boost_down = event->type == SDL_EVENT_MOUSE_BUTTON_DOWN;
    return false;
  }
  if (event->type == SDL_EVENT_KEY_DOWN &&
      event->key.scancode == SDL_SCANCODE_AC_BACK) {
    state->back_requested = true; /* Esc in the shell: leave the match */
    return true;
  }
  return false;
#endif

  if (event->type == SDL_EVENT_KEY_DOWN &&
      event->key.scancode == SDL_SCANCODE_AC_BACK) {
    state->back_requested = true;
    return true;
  }
  if (event->type == SDL_EVENT_WILL_ENTER_BACKGROUND ||
      event->type == SDL_EVENT_WINDOW_FOCUS_LOST) {
    reset_touches(state);
    return false;
  }
  if (event->type != SDL_EVENT_FINGER_DOWN &&
      event->type != SDL_EVENT_FINGER_MOTION &&
      event->type != SDL_EVENT_FINGER_UP &&
      event->type != SDL_EVENT_FINGER_CANCELED)
    return false;

  /* Compose owns every pointer in the local layout editor. If SDL receives a
     copy, consume it without steering, boosting or pressing a hotkey. */
  if (ai_mode_is_editor()) {
    reset_touches(state);
    mobile_hotkeys_reset_runtime(env);
    return true;
  }

  uint64_t finger = (uint64_t)event->tfinger.fingerID;
  float x = event->tfinger.x * env->wnd->size[0];
  float y = event->tfinger.y * env->wnd->size[1];

  sync_touch_policy(env);

  /* Releases belong to the input owner even when a panel now covers it.
     Clear before any UI consumer can swallow the event. */
  if (event->type == SDL_EVENT_FINGER_UP || event->type == SDL_EVENT_FINGER_CANCELED) {
    finish_touch(env,event->type,finger);
    mobile_hotkeys_process_event(env, raw_event);
    if (mobile_controls_steering_mode(env) == MOBILE_STEERING_CLASSIC)
      classic_touch(env, event, finger, x, y);
  }

  /* Playable AI's first-entry notice owns the whole touch surface. The layout
     editor never shows it, and no steering/button state can leak underneath. */
  if (ai_mode_notice_process_event(env, raw_event)) {
    reset_touches(state);
    mobile_hotkeys_reset_runtime(env);
    return true;
  }

  if (event->type==SDL_EVENT_FINGER_DOWN && touch_is_owned(state,finger)) return true;
  if (event->type==SDL_EVENT_FINGER_MOTION && track_boost_motion(env,finger,x,y)) return true;

  /* The team roster and chat window own the finger that lands on them
     (scrolling, folding, the message box); every other finger keeps
     steering, so nothing is reset (OM, 2026-10-04). */
  if (android_team_hud_touch(env, (int)event->type,
                             (unsigned long long)finger, x, y))
    return true;

  /* The interface's own buttons belong to the interface, not to steering. */
  if (event->type == SDL_EVENT_FINGER_DOWN &&
      ui_overlay_leaderboard_hit(env, x, y)) {
    reset_touches(state);
    mobile_hotkeys_reset_runtime(env);
    return true;
  }
  if (event->type == SDL_EVENT_FINGER_DOWN &&
      android_team_respawn_toggle_hit(env, x, y)) {
    reset_touches(state);
    mobile_hotkeys_reset_runtime(env);
    return true;
  }
  if (event->type == SDL_EVENT_FINGER_DOWN &&
      android_team_chat_button_hit(env, x, y)) {
    reset_touches(state);
    mobile_hotkeys_reset_runtime(env);
    return true;
  }

  if (state->editor_active) {
    if (event->type == SDL_EVENT_FINGER_DOWN && !state->edit_down)
      edit_touch_down(env, finger, x, y);
    else if (event->type == SDL_EVENT_FINGER_MOTION && state->edit_down &&
             state->edit_finger == finger)
      edit_touch_move(env, x, y);
    else if ((event->type == SDL_EVENT_FINGER_UP ||
              event->type == SDL_EVENT_FINGER_CANCELED) &&
             state->edit_down && state->edit_finger == finger) {
      state->edit_down = false;
      state->edit_target = MOBILE_EDIT_NONE;
    }
    return state->edit_down;
  }

  if (env->usr->gdata.curr_screen != PLAYING ||
      (env->usr->gdata.conn != CONNECTED &&
       env->usr->gdata.conn != AI_CONNECTED)) {
    process_ui_scroll_touch(state, event, finger, y);
    return false;
  }

  /* The zoom bar is on top (OM, 2026-11-01): a finger that lands on it
     zooms, even where an on-screen button or the boost button lies under
     it (Classic's zoom did nothing beside a big boost button). */
  if (event->type == SDL_EVENT_FINGER_DOWN && !state->zoom_down &&
      inside_zoom(env, x, y)) {
    state->zoom_down = true;
    state->zoom_finger = finger;
    set_zoom_from_touch(env, x, y);
    return true;
  }
  if (event->type == SDL_EVENT_FINGER_MOTION && state->zoom_down &&
      state->zoom_finger == finger) {
    set_zoom_from_touch(env, x, y);
    return true;
  }

  // Visible on-screen buttons own their finger before joystick, boost or zoom.
  // This preserves the direct-touch isolation contract for every button.
  if (mobile_hotkeys_process_event(env, raw_event)) return true;

  if (mobile_controls_steering_mode(env) == MOBILE_STEERING_CLASSIC) {
    if (state->zoom_down && state->zoom_finger == finger && event->type == SDL_EVENT_FINGER_MOTION) {
      set_zoom_from_touch(env, x, y); return true;
    }
    return classic_touch(env, event, finger, x, y);
  }

  /* Near Original: slither's own fixed controls (releases fall through). */
  if (android_home_near_original() && event->type != SDL_EVENT_FINGER_UP &&
      event->type != SDL_EVENT_FINGER_CANCELED)
    return original_touch(env, event, finger, x, y);

  if (event->type == SDL_EVENT_FINGER_DOWN) {
    return wyrm_touch_down(env,finger,x,y);
  } else if (event->type == SDL_EVENT_FINGER_MOTION) {
    if (state->joystick_down && state->joystick_finger == finger) {
      if (mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW)
        update_arrow(env, x, y);
      else
        update_joystick(env, x, y);
      return true;
    }
    if (state->zoom_down && state->zoom_finger == finger) {
      set_zoom_from_touch(env, x, y);
      return true;
    }
  } else {
    bool released_owned_touch = false;
    if (state->joystick_down && state->joystick_finger == finger) {
      state->joystick_down = false;
      arrow_tap_boost = false; /* the double tap's boost ends with its finger */
      released_owned_touch = true;
    }
    if (state->boost_down && state->boost_finger == finger) {
      state->boost_down = false;
      released_owned_touch = true;
    }
    if (state->zoom_down && state->zoom_finger == finger) {
      state->zoom_down = false;
      released_owned_touch = true;
    }
    return released_owned_touch;
  }
  return false;
}
#else
bool mobile_controls_process_event(tenv* env, const void* event) {
  (void)env;
  (void)event;
  return false;
}
#endif

void mobile_controls_update(tenv* env) {
  mobile_controls_state* state = &env->usr->mobile_controls;
#ifdef WYRM_SDL_JNI
  sync_touch_policy(env);
#endif

#ifdef WYRM_SDL_JNI
  static Uint64 last_touch_audit;
  Uint64 now = SDL_GetTicks();
  if (now-last_touch_audit >= 100) {
    last_touch_audit=now;
    uint64_t live[64]; int live_count=0, device_count=0;
    SDL_TouchID* devices=SDL_GetTouchDevices(&device_count);
    for(int d=0;devices && d<device_count;++d) {
      int count=0; SDL_Finger** fingers=SDL_GetTouchFingers(devices[d],&count);
      for(int f=0;fingers && f<count && live_count<64;++f) live[live_count++]=(uint64_t)fingers[f]->id;
      SDL_free(fingers);
    }
    SDL_free(devices);
    uint64_t owners[3]={state->joystick_finger,state->boost_finger,state->zoom_finger};
    for(int i=0;i<3;++i) {
      bool found=false; for(int j=0;j<live_count;++j) if(owners[i]==live[j]) found=true;
      if(!found) release_owned_touch(state,owners[i]);
    }
    for(int i=0;i<16;++i) if(classic_fingers[i].down) {
      bool found=false; for(int j=0;j<live_count;++j) if(classic_fingers[i].id==live[j]) found=true;
      if(!found) {
        if(classic_boost_finger==classic_fingers[i].id) arrow_tap_boost=false;
        if(arrow_last_down_finger==classic_fingers[i].id) { arrow_last_down_ms=0; arrow_last_tap_released=false; }
        classic_fingers[i].down=false;
      }
    }
    mobile_hotkeys_state* keys=&env->usr->mobile_hotkeys;
    for(int i=0;i<NUM_MOBILE_ACTIONS;++i) if(keys->down[i]) {
      bool found=false; for(int j=0;j<live_count;++j) if(keys->finger[i]==live[j]) found=true;
      if(!found) keys->down[i]=false;
    }
  }
#endif

  /*
   * Outside a live match, nothing may still be holding a gameplay control.
   *
   * A finger that was on boost when the arena ended never got to let go. The
   * up arrives after the connection has gone, and `mobile_controls_process_event`
   * returns at its "not playing" gate before it reaches the release, so the
   * button stayed down into the next match — held, so a fresh press could not
   * take it, and latched to a finger id that no longer existed, so no release
   * could clear it either. That is the boost that sticks down. When it is the
   * joystick instead it is a snake that will not steer, which is why toggling
   * the bot and back looked like the cure: the bot was doing the steering.
   *
   * This runs every frame from `tinput`, so it also covers the case where the
   * finger never comes up at all.
   */
  if (env->usr->gdata.curr_screen != PLAYING ||
      (env->usr->gdata.conn != CONNECTED &&
       env->usr->gdata.conn != AI_CONNECTED)) {
    reset_touches(state);
    state->aim_valid = false;
    state->arrow_drag_distance = 0.0f;
    /* The overlay buttons have the same gate and so had the same wound, but
       worse: a button left down is skipped by the press loop, which will not
       take a button
       that is already held, and the release loop only answers to a finger id
       that went away with the last match. A button caught like that never worked
       again. */
    mobile_hotkeys_reset_runtime(env);
  }

  /*
   * The arrow, once a frame.
   *
   * Everything here is appearance and nothing here touches the heading. The
   * drawn position eases towards the steering vector at 0.6 a frame; while a
   * finger is steering the arrow fades up towards 0.85; once it is gone the
   * arrow fades out and drifts on ahead. slither's own rates, and `vfr` is the
   * engine's own frame factor, which is what those rates are per.
   */
  {
    game_data* gdata = &env->usr->gdata;
    decay_boost_motion(env);
    float vfr = gdata->data.vfr;
    if (!(vfr > 0.0f) || vfr > 4.0f) vfr = 1.0f;

    bool steering = mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW &&
                    state->joystick_down && state->aim_valid;

    /* The setting reads as how much it lags, so it is one minus the catch-up.
       slither eases at 0.6, which is a smoothness of 0.4. */
    /* The original motion eases 0.6 every frame, as Main.as does (not per
       unit of time); Wyrm's own uses the player's lag, frame-rate free. */
    /* With Customise on it is the same 0.6, frame-rate free (Sensitivity
       replaced the lag slider, OM 2026-10-18). */
    float ease;
    if (arrow_original_motion()) {
      ease = 0.6f;
    } else {
      ease = 1.0f - powf(0.4f, vfr);
    }
    state->arrow_draw[0] += (state->arrow_vec[0] - state->arrow_draw[0]) * ease;
    state->arrow_draw[1] += (state->arrow_vec[1] - state->arrow_draw[1]) * ease;

    if (steering) {
      state->arrow_dead = 0.0f;
      state->arrow_opacity += vfr * 0.03f;
      if (state->arrow_opacity > 0.85f) state->arrow_opacity = 0.85f;
    } else {
      state->arrow_opacity -= vfr * 0.01f;
      if (state->arrow_opacity < 0.0f) state->arrow_opacity = 0.0f;
      state->arrow_dead += vfr * 0.01f;
      if (state->arrow_dead > 1.0f) state->arrow_dead = 1.0f;
    }

    /* The spring zoom bar: while pulled it zooms (gently near the middle,
       quickly at the ends); let go, it springs back to the middle. */
    if (feel_zoom_spring) {
      if (!state->zoom_down) {
        zoom_spring_t *= powf(0.72f, vfr);
        if (fabsf(zoom_spring_t) < 0.01f) zoom_spring_t = 0.0f;
      }
      if (fabsf(zoom_spring_t) > 0.04f) {
        float rate = 1.4f * zoom_spring_t * fabsf(zoom_spring_t);
        float* zoom = &env->usr->gdata.data.ms_zoom;
        *zoom *= expf(rate * vfr * 0.008f);
        *zoom = clampf(*zoom, MAX_ZOOM_OUT, MAX_ZOOM_IN);
      }
    } else {
      zoom_spring_t = 0.0f;
    }

    /* Near Original: the boost button's alpha (Main.as: 0.2 idle, up to 0.4
       while boosting, 0.01 a frame) and the arrow's boost glow (accel_a
       +0.02 / -0.03 a frame, accel_fr +0.15 while boosting). */
    /* Arrow's tap-boost glows too (OM, 2026-11-01). */
    if (state->boost_down || (arrow_tap_boost && state->joystick_down)) {
      original_boost_alpha += 0.01f * vfr;
      if (original_boost_alpha > 0.4f) original_boost_alpha = 0.4f;
      original_accel_a += 0.02f * vfr;
      if (original_accel_a > 1.0f) original_accel_a = 1.0f;
      original_accel_fr += 0.15f * vfr;
    } else {
      original_boost_alpha -= 0.01f * vfr;
      if (original_boost_alpha < 0.2f) original_boost_alpha = 0.2f;
      original_accel_a -= 0.03f * vfr;
      if (original_accel_a < 0.0f) original_accel_a = 0.0f;
    }
  }

  if (!state->back_requested) return;
  state->back_requested = false;
  switch (env->usr->gdata.curr_screen) {
    case SKIN_EDITOR: env->usr->gdata.curr_screen = TITLE_SCREEN; break;
    case PLAYING: state->exit_requested = true; break;
    case LOBBY:
      env->usr->gdata.stay_in_lobby = false;
      env->usr->gdata.leaving = true;
      env->usr->gdata.curr_screen = TITLE_SCREEN;
      break;
    case TITLE_SCREEN: env->config.running = false; break;
  }
}

bool mobile_controls_get_aim(tenv* env, int* x, int* y) {
#ifdef WYRM_SDL_JNI
  mobile_controls_state* state = &env->usr->mobile_controls;
  if (!state->aim_valid) return false;
  *x = (int)(state->joystick_axis[0] * 1000.0f);
  *y = (int)(state->joystick_axis[1] * 1000.0f);
  return true;
#else
  (void)env;
  (void)x;
  (void)y;
  return false;
#endif
}

bool mobile_controls_boost_down(tenv* env) {
#ifdef WYRM_SDL_JNI
  mobile_controls_state* state=&env->usr->mobile_controls;
  int mode=mobile_controls_steering_mode(env);
  bool original=android_home_near_original();
  bool fixed=env->usr->usrs.mobile_controls.boost_mode==MOBILE_BOOST_FIXED;
  if (mode==MOBILE_STEERING_CLASSIC) {
    int count=0;
    for(int i=0;i<16;++i) if(classic_fingers[i].down && (!original || !classic_fingers[i].ignored)) ++count;
    if ((original || !fixed) && count>1) return true;
    if (original && arrow_tap_boost)
      for(int i=0;i<16;++i) if(classic_fingers[i].down && classic_fingers[i].id==classic_boost_finger) return true;
  }
  if (arrow_tap_boost && state->joystick_down && (wyrm_arrow_mode(env) || (original && mode==MOBILE_STEERING_ARROW)) &&
      env->usr->usrs.ext.arrow_double_tap_boost && (original || !fixed)) return true;
  return state->boost_down;
#else
  (void)env; return false;
#endif
}

static ImU32 color_u32(float r, float g, float b, float a) {
  return igColorConvertFloat4ToU32((ImVec4){r, g, b, a});
}

static bool arrow_geometry(tenv* env, float* ax, float* ay, float* dx,
                           float* dy, float* length, float* width) {
  mobile_arrow_settings* arrow = &env->usr->usrs.arrow_controls;
  mobile_controls_state* state = &env->usr->mobile_controls;
  if (mobile_controls_steering_mode(env) != MOBILE_STEERING_ARROW || !state->aim_valid ||
      state->arrow_opacity <= 0.004f)
    return false;
  float scale = control_scale(env);
  *dx = state->joystick_axis[0];
  *dy = state->joystick_axis[1];

  /* Drawn where the eased vector points, not at some interpolated distance
     along the heading: the arrow is the steering vector made visible, so a
     longer drag genuinely puts it further out. Once the finger is gone it
     carries on forward as it fades — 260 pixels by the time it is invisible. */
  /* The original motion drifts 260 of the original's units. */
  float drift = 260.0f * (arrow_original_motion() ? original_unit(env) : scale) *
                powf(state->arrow_dead, 2.5f);
  /* Look ahead moves the camera, so the arrow keeps to the snake (Main.as:
     arrow_batch at -view_lav). */
  float lax, lay;
  mobile_controls_look_ahead_offset(env, &lax, &lay);
  *ax = env->wnd->size[0] * 0.5f - lax + state->arrow_draw[0] + *dx * drift;
  *ay = env->wnd->size[1] * 0.5f - lay + state->arrow_draw[1] + *dy * drift;
  *length = 70.0f * scale * arrow->size;
  *width = 52.0f * scale * arrow->size;
  return true;
}

/* The assist laser follows the finger in Classic, as it follows the arrow
   in Arrow (OM, 2026-11-01). Draw-only: classic_touch already steers by
   this same axis. */
bool mobile_controls_classic_aim(tenv* env, float* dx, float* dy) {
  if (!dx || !dy || mobile_controls_steering_mode(env) != MOBILE_STEERING_CLASSIC)
    return false;
  mobile_controls_state* state = &env->usr->mobile_controls;
  float x = 0, y = 0; int count = 0;
  for (int i = 0; i < 16; ++i)
    if (classic_fingers[i].down && !classic_fingers[i].ignored) {
      x += classic_fingers[i].x; y += classic_fingers[i].y; ++count;
    }
  if (!count || !state->aim_valid) return false;
  *dx = x/count; *dy = y/count;
  return true;
}

bool mobile_controls_get_arrow_position(tenv* env, float* x, float* y) {
#ifdef WYRM_DESKTOP
  /* Wyrm Desktop (OM, 2026-10-11): the pointer is the arrow, so assist's
     laser runs from the head to it, as Android's arrow laser runs to the
     arrow (and as the reference client's PC laser did). */
  if (!x || !y || !env->usr->mobile_controls.aim_valid) return false;
  *x = env->ms->pos[0];
  *y = env->ms->pos[1];
  return true;
#endif
#ifdef WYRM_SDL_JNI
  float dx, dy, length, width;
  return x && y && arrow_geometry(env, x, y, &dx, &dy, &length, &width);
#else
  (void)env;
  (void)x;
  (void)y;
  return false;
#endif
}

/*
 * The controls, in paper.
 *
 * Paint changes stop here. Touch ownership, hit circles and control geometry
 * are calculated above and remain the same. One shadow, one paper fill and an
 * ink hairline are also cheaper than the layered glass they replace.
 */
static void draw_paper_disc(ImDrawList* dl, float cx, float cy, float radius,
                            float alpha, bool active) {
  /* Wyrm's ink halo (OM, 2026-10-02): the edge holds on a white floor too. */
  for (int i = 0; i < 4; ++i)
    ImDrawList_AddCircle(dl, (ImVec2){cx, cy}, radius + 2.0f + i * 2.5f,
                         color_u32(0.035f, 0.040f, 0.055f, alpha * (0.16f - i * 0.035f)),
                         48, 2.5f);
  if (active)
    ImDrawList_AddCircle(dl, (ImVec2){cx, cy}, radius + 3.5f,
                         color_u32(0.247f, 0.933f, 0.588f, alpha * 0.55f), 48, 3.0f);
  ImDrawList_AddCircleFilled(dl, (ImVec2){cx, cy + radius * 0.045f},
                             radius * 1.025f,
                             color_u32(0, 0, 0, alpha * 0.18f), 48);
  ImDrawList_AddCircleFilled(dl, (ImVec2){cx, cy}, radius,
                             active
                                 ? arena_theme_colour(ARENA_THEME_INK, alpha * 0.96f)
                                 : arena_theme_colour(ARENA_THEME_CARD, alpha * 0.94f),
                             48);
  ImDrawList_AddCircle(dl, (ImVec2){cx, cy}, radius,
                       arena_theme_colour(ARENA_THEME_INK,
                                          active ? 0.82f : 0.48f),
                       48, 1.5f);
}

static void original_halo(ImDrawList* dl, float cx, float cy, float radius,
                          float spread, float alpha);
static void draw_joystick(tenv* env, ImDrawList* dl, float cx, float cy,
                          bool active, bool editor) {
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  mobile_controls_state* state = &env->usr->mobile_controls;
  float radius = 92.0f * control_scale(env) * cfg->joystick_size;
  float alpha = env->usr->usrs.joystick_opacity;

  /* Wyrm keeps the same paper/ink skin before, during and after a hold. */
  float a = clampf(alpha, 0.05f, 1.0f);
  float base = radius * 0.73f;
  float ax = state->joystick_axis[0];
  float ay = state->joystick_axis[1];
  float hx, hy;
  bool still = fabsf(ax) < 0.08f && fabsf(ay) < 0.08f;
  if (editor) {
    ax = ay = 0.0f;
  } else if (!active && !knob_follows_snake()) {
    ax = ay = 0.0f; /* the reset knob: back to the middle */
  } else if ((!state->aim_valid || still) && knob_follows_snake() &&
             joystick_heading(env, &hx, &hy)) {
    /* Nothing steered yet: where the snake goes. Once let go, the knob
       stays exactly where it was (OM, 2026-11-01): no jump back to the
       middle or to the head's still-turning angle. */
    ax = hx;
    ay = hy;
  }
  draw_paper_disc(dl,cx,cy,base,a,false);
  float knob = base * 0.402f;
  float kx = cx + ax * base * 0.536f;
  float ky = cy + ay * base * 0.536f;
  original_halo(dl, kx, ky, knob, knob * 0.146f, a);
  ImDrawList_AddCircleFilled(dl, (ImVec2){kx, ky}, knob, arena_theme_colour(ARENA_THEME_INK,a), 32);
  (void)active;

}

static void draw_boost(tenv* env, ImDrawList* dl, float cx, float cy,
                       bool active, bool editor) {
  (void)editor;
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  float radius = 78.0f * control_scale(env) * cfg->boost_size;
  float alpha = env->usr->usrs.boost_opacity;

  draw_paper_disc(dl, cx, cy, radius, alpha, active);

  /* The original boost's triangle (OM, 2026-11-01): boostie's, pointing
     up, with its soft shadow, on the disc's scale (r 110), in Wyrm's ink.
     It follows the button's size and opacity. */
  float tk = radius / 110.0f;
  for (int i = 1; i <= 4; ++i) {
    float drop = (2.0f + 4.0f * i) * tk;
    ImDrawList_AddTriangleFilled(
        dl, (ImVec2){cx, cy - 41.5f * tk + drop},
        (ImVec2){cx + 56.0f * tk, cy + 25.5f * tk + drop},
        (ImVec2){cx - 56.0f * tk, cy + 25.5f * tk + drop},
        color_u32(0, 0, 0, alpha * 0.07f));
  }
  ImDrawList_AddTriangleFilled(dl, (ImVec2){cx, cy - 41.5f * tk},
                               (ImVec2){cx + 56.0f * tk, cy + 25.5f * tk},
                               (ImVec2){cx - 56.0f * tk, cy + 25.5f * tk},
                               arena_theme_colour(ARENA_THEME_INK, alpha));
}

typedef struct mobile_arrow_shape {
  int count;
  const float* along;
  const float* across;
} mobile_arrow_shape;

/* These normalized silhouettes are mirrored in `ControlsScreen.kt`. Keeping
 * them as points instead of textures lets every style inherit the player's
 * live colour, opacity and size without a second rendering path. */
static mobile_arrow_shape arrow_shape(int style) {
  static const float current_x[] = {0.66f, 0.08f, 0.01f, -0.52f,
                                    -0.52f, 0.01f, 0.08f};
  static const float current_y[] = {0.00f, -0.56f, -0.24f, -0.24f,
                                    0.24f, 0.24f, 0.56f};
  static const float wide_x[] = {0.72f, 0.02f, -0.06f, -0.58f,
                                 -0.58f, -0.06f, 0.02f};
  static const float wide_y[] = {0.00f, -0.72f, -0.30f, -0.30f,
                                 0.30f, 0.30f, 0.72f};
  static const float needle_x[] = {0.82f, 0.05f, 0.16f, -0.72f,
                                   -0.72f, 0.16f, 0.05f};
  static const float needle_y[] = {0.00f, -0.22f, -0.075f, -0.075f,
                                   0.075f, 0.075f, 0.22f};
  static const float blade_x[] = {0.78f, 0.12f, -0.10f, -0.25f,
                                  -0.70f, -0.25f, -0.10f, 0.12f};
  static const float blade_y[] = {0.00f, -0.42f, -0.22f, -0.16f,
                                  0.00f, 0.16f, 0.22f, 0.42f};
  static const float triangle_x[] = {0.82f, -0.64f, -0.64f};
  static const float triangle_y[] = {0.00f, -0.26f, 0.26f};
  /* slither's own arrow (Main.as, 64 px shape): shaft, then the head. */
  static const float original_x[] = {-0.56f, -0.56f, 0.00f, 0.00f,
                                     0.56f, 0.00f, 0.00f};
  static const float original_y[] = {-0.3155f, 0.3155f, 0.2227f, 0.7423f,
                                     0.00f, -0.7423f, -0.2227f};

  switch (style) {
    case MOBILE_ARROW_CLASSIC_WIDE:
      return (mobile_arrow_shape){7, wide_x, wide_y};
    case MOBILE_ARROW_NEEDLE:
      return (mobile_arrow_shape){7, needle_x, needle_y};
    case MOBILE_ARROW_BLADE:
      return (mobile_arrow_shape){8, blade_x, blade_y};
    case MOBILE_ARROW_TRIANGLE:
      return (mobile_arrow_shape){3, triangle_x, triangle_y};
    case MOBILE_ARROW_ORIGINAL:
      return (mobile_arrow_shape){7, original_x, original_y};
    case MOBILE_ARROW_CURRENT:
    default:
      return (mobile_arrow_shape){7, current_x, current_y};
  }
}

static void draw_original_arrow(tenv* env, ImDrawList* dl);
static ImU32 original_arrow_colour(tenv* env, float alpha);

/* slither's arrow layers on any outline (OM, 2026-11-01), as
   draw_original_arrow lays them: the DropShadowFilter halo as rings out
   from the path along each corner's bisector (erfc profile, sigma 6 px,
   at most 11.5 px past the outline), the 9 px black outline, the fill, and
   while boosting the ADD copy (the colour doubled at `glow`). `s` is screen
   px per texture px. `convex` fills in one call, else a fan from `centre`. */
static void draw_layered_arrow(ImDrawList* dl, const ImVec2* p, int n,
                               ImVec2 centre, float s, float alpha,
                               ImU32 fill, float glow, bool convex) {
  if (n < 3 || n > 8 || alpha <= 0.0f) return;
  float area = 0.0f;
  for (int i = 0; i < n; ++i) {
    int next = (i + 1) % n;
    area += p[i].x * p[next].y - p[next].x * p[i].y;
  }
  float side = area > 0.0f ? 1.0f : -1.0f;
  const int bands = 6;
  const float sigma = 6.0f, reach = 11.5f;
  float before = 0.0f;
  for (int j = bands; j >= 1; --j) {
    float x = (j - 0.5f) * (reach / bands);
    float want = alpha * 0.5f * erfcf(x / (sigma * 1.41421356f));
    float a = before >= 0.999f ? 0.0f : 1.0f - (1.0f - want) / (1.0f - before);
    if (a > 0.003f) {
      float d = (4.5f + j * (reach / bands)) * s;
      ImVec2 q[8];
      for (int i = 0; i < n; ++i) {
        int prev = (i + n - 1) % n, next = (i + 1) % n;
        float e1x = p[i].x - p[prev].x, e1y = p[i].y - p[prev].y;
        float e2x = p[next].x - p[i].x, e2y = p[next].y - p[i].y;
        float l1 = sqrtf(e1x * e1x + e1y * e1y), l2 = sqrtf(e2x * e2x + e2y * e2y);
        if (l1 < 0.0001f) l1 = 0.0001f;
        if (l2 < 0.0001f) l2 = 0.0001f;
        float n1x = e1y / l1, n1y = -e1x / l1;
        float n2x = e2y / l2, n2y = -e2x / l2;
        float bx = n1x + n2x, by = n1y + n2y;
        float bl = sqrtf(bx * bx + by * by);
        if (bl < 0.0001f) { bx = n1x; by = n1y; bl = 1.0f; }
        q[i] = (ImVec2){p[i].x + bx / bl * d * side, p[i].y + by / bl * d * side};
      }
      ImU32 shade = color_u32(0, 0, 0, a);
      for (int i = 0; i < n; ++i) {
        int next = (i + 1) % n;
        ImDrawList_AddTriangleFilled(dl, p[i], p[next], q[next], shade);
        ImDrawList_AddTriangleFilled(dl, p[i], q[next], q[i], shade);
      }
    }
    if (want > before) before = want;
  }
  ImDrawList_AddPolyline(dl, p, n, color_u32(0, 0, 0, alpha),
                         ImDrawFlags_Closed, 9.0f * s);
  for (int pass = 0; pass < 2; ++pass) {
    ImU32 colour = fill;
    if (pass == 1) {
      if (glow <= 0.004f) break;
      ImVec4 base;
      igColorConvertU32ToFloat4(&base, fill);
      colour = color_u32(base.x * 2.0f > 1.0f ? 1.0f : base.x * 2.0f,
                         base.y * 2.0f > 1.0f ? 1.0f : base.y * 2.0f,
                         base.z * 2.0f > 1.0f ? 1.0f : base.z * 2.0f, glow);
    }
    if (convex) {
      ImDrawList_AddConvexPolyFilled(dl, p, n, colour);
    } else {
      for (int i = 0; i < n; ++i)
        ImDrawList_AddTriangleFilled(dl, centre, p[i], p[(i + 1) % n], colour);
    }
  }
}

static void draw_arrow(tenv* env, ImDrawList* dl) {
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  mobile_arrow_settings* arrow = &env->usr->usrs.arrow_controls;
  float ax, ay, dx, dy, length, width;
  if (!arrow_geometry(env, &ax, &ay, &dx, &dy, &length, &width)) return;
  float px = -dy;
  float py = dx;
  /* The player's own opacity setting scaled by where the arrow is in its own
     fade, so releasing the finger takes it out rather than cutting it. */
  float alpha = cfg->opacity * (env->usr->mobile_controls.arrow_opacity / 0.85f);
  /* An image arrow keeps its own colours: only its fade applies. */
  if (android_arrows_draw(dl, ax, ay, dx, dy, length,
                          env->usr->mobile_controls.arrow_opacity / 0.85f))
    return;
  float brightness = android_arrow_brightness();
  /* The Original style is slither's own arrow, drawn as Near Original draws
     it: outline, shadow, the snake's arrow colour, its size (OM, 2026-10-05).
     An image arrow (above) still wins. */
  if (env->usr->usrs.arrow_style == MOBILE_ARROW_ORIGINAL) {
    draw_original_arrow(env, dl);
    return;
  }
  mobile_arrow_shape shape = arrow_shape(env->usr->usrs.arrow_style);
  ImVec2 points[8];
  for (int i = 0; i < shape.count; ++i) {
    float along = shape.along[i] * length;
    float across = shape.across[i] * width;
    points[i] = (ImVec2){ax + dx * along + px * across,
                         ay + dy * along + py * across};
  }

  /* Keep Current on the exact fill call it shipped with. The new silhouettes
   * have deliberate shoulders/notches, so a centre fan preserves those cuts
   * instead of letting a convex fill bridge over them. */
  ImU32 fill = color_u32(arrow->color[0] * brightness,
                         arrow->color[1] * brightness,
                         arrow->color[2] * brightness, alpha);
  /* The snake's arrow colour, as slither's, until the player picks one
     (OM, 2026-11-01); the brightness slider is for a picked colour. */
  if (!env->usr->usrs.ext.arrow_colour_own) fill = original_arrow_colour(env, alpha);
  /* slither's layers (OM, 2026-11-01): its 9 px outline and halo are for a
     52 px long arrow, so they scale with this one's length. */
  float lo = 0.0f, hi = 0.0f;
  for (int i = 0; i < shape.count; ++i) {
    if (shape.along[i] < lo) lo = shape.along[i];
    if (shape.along[i] > hi) hi = shape.along[i];
  }
  float s = (hi - lo) * length / 52.0f;
  float glow = alpha * original_accel_a * (0.5f + 0.5f * cosf(original_accel_fr));
  draw_layered_arrow(dl, points, shape.count, (ImVec2){ax, ay}, s, alpha, fill,
                     glow, env->usr->usrs.arrow_style == MOBILE_ARROW_CURRENT);
}

/* ---- Near Original drawing (Main.as textures, drawn here) ---- */

/* A soft dark halo outside a disc: DropShadowFilter(0, 90, black, 1, 14, 14). */
static void original_halo(ImDrawList* dl, float cx, float cy, float radius,
                          float spread, float alpha) {
  for (int i = 0; i < 6; ++i) {
    float t = (i + 0.5f) / 6.0f;
    float fade = (1.0f - t) * (1.0f - t);
    ImDrawList_AddCircle(dl, (ImVec2){cx, cy}, radius + spread * t,
                         color_u32(0, 0, 0, alpha * 0.55f * fade), 48,
                         spread / 6.0f + 0.5f);
  }
}

/* The snake's arrow colour (Main.as 20085-20120): a quarter of white, three
   quarters of the snake's colour, with the original's fixed overrides. */
static ImU32 original_arrow_colour(tenv* env, float alpha) {
  game_data* gdata = &env->usr->gdata;
  snake* own = get_snake(gdata, gdata->data.snake_id);
  int cv = own ? own->cv : 0;
  if (cv < 0 || cv >= NUM_COLOR_GROUPS) cv = 0;
  float r, g, b;
  switch (cv) {
    case 29: r = 0xCC; g = 0xCC; b = 0xCC; break;
    case 30: r = 0x40; g = 0x40; b = 0xFF; break;
    case 31: r = 0xFF; g = 0x40; b = 0x40; break;
    case 32: r = 0xFF; g = 0xFF; b = 0x40; break;
    case 33: r = 0xFF; g = 0x90; b = 0x40; break;
    case 34: r = 0xFF; g = 0x40; b = 0xFF; break;
    case 35: r = 0x50; g = 0xFF; b = 0x50; break;
    case 36: r = 0xFF; g = 0x40; b = 0x40; break;
    case 41: r = 0x80; g = 0x80; b = 0xFF; break;
    default: {
      vec3s c = gdata->cg_colors[cv];
      float cr = roundf(c.x * 256.0f), cg = roundf(c.y * 256.0f),
            cb = roundf(c.z * 256.0f);
      r = roundf(64.0f + 0.75f * (cr > 255.0f ? 255.0f : cr));
      g = roundf(64.0f + 0.75f * (cg > 255.0f ? 255.0f : cg));
      b = roundf(64.0f + 0.75f * (cb > 255.0f ? 255.0f : cb));
    }
  }
  if (r > 255.0f) r = 255.0f;
  if (g > 255.0f) g = 255.0f;
  if (b > 255.0f) b = 255.0f;
  return color_u32(r / 255.0f, g / 255.0f, b / 255.0f, alpha);
}

/* slither's arrow: the 64 px polygon from its left-middle pivot, a 9 px black
   mitred outline under a fill in the snake's colour, a soft shadow, scale
   0.5 + 0.25 accel_a (in units, times the player's arrow size), and an extra
   pulsing copy while boosting. */
static void draw_original_arrow(tenv* env, ImDrawList* dl) {
  float ax, ay, dx, dy, length, width;
  if (!arrow_geometry(env, &ax, &ay, &dx, &dy, &length, &width)) return;
  float alpha = env->usr->mobile_controls.arrow_opacity;
  if (alpha > 1.0f) alpha = 1.0f;
  float size = env->usr->usrs.arrow_controls.size;
  if (!(size > 0.0f)) size = 1.0f;
  float s = (0.5f + 0.25f * original_accel_a) * original_unit(env) * size;
  float px = -dy;
  float py = dx;
  static const float shape_x[] = {15.0f, 15.0f, 41.0f, 41.0f, 67.0f, 41.0f, 41.0f};
  static const float shape_y[] = {-10.88f, 10.88f, 7.68f, 25.6f, 0.0f, -25.6f, -7.68f};
  /* (e.y, -e.x) of each edge points out of a counter-clockwise shape. */
  float shadow_area = 0.0f;
  for (int i = 0; i < 7; ++i) {
    int next = (i + 1) % 7;
    shadow_area += shape_x[i] * shape_y[next] - shape_x[next] * shape_y[i];
  }
  float shadow_side = shadow_area > 0.0f ? 1.0f : -1.0f;
  ImVec2 p[7];
  for (int i = 0; i < 7; ++i)
    p[i] = (ImVec2){ax + dx * shape_x[i] * s + px * shape_y[i] * s,
                    ay + dy * shape_x[i] * s + py * shape_y[i] * s};
  /* The texture's DropShadowFilter(0, 90, black, 1, 12, 12, 1, 3): a soft
     black halo (sigma ~6 texture px) around the outlined arrow. Nested strokes,
     widest first, each set so the pile at a distance x outside the 9 px outline
     reads 0.5 erfc(x / (sigma sqrt 2)) of the arrow's alpha. */
  /* OM, 2026-10-05: the shadow spread the arrow. slither draws it inside an
     84 px texture with the arrow 10 px in, so it reaches only about 11.5 px
     past the 9 px outline; and thick closed strokes grew mitre spikes at the
     head's corners. Now: nested fills of the outline pushed out by d along
     each corner's bisector (no mitre), widest first, the same erfc profile,
     never past 11.5 texture px, drawn as rings outside the path. */
  {
    const int bands = 6;
    const float sigma = 6.0f;
    const float reach = 11.5f;
    /* The outline's outside edge: 4.5 px out from the path. */
    float before = 0.0f;
    for (int j = bands; j >= 1; --j) {
      float x = (j - 0.5f) * (reach / bands);
      float want = alpha * 0.5f * erfcf(x / (sigma * 1.41421356f));
      float a = before >= 0.999f ? 0.0f : 1.0f - (1.0f - want) / (1.0f - before);
      if (a > 0.003f) {
        float d = 4.5f + j * (reach / bands);
        ImVec2 q[7];
        for (int i = 0; i < 7; ++i) {
          int prev = (i + 6) % 7, next = (i + 1) % 7;
          /* Outward normals of the two edges at this corner (the shape winds
             clockwise in screen space: x along, y across). */
          float e1x = shape_x[i] - shape_x[prev], e1y = shape_y[i] - shape_y[prev];
          float e2x = shape_x[next] - shape_x[i], e2y = shape_y[next] - shape_y[i];
          float l1 = sqrtf(e1x * e1x + e1y * e1y), l2 = sqrtf(e2x * e2x + e2y * e2y);
          float n1x = e1y / l1, n1y = -e1x / l1;
          float n2x = e2y / l2, n2y = -e2x / l2;
          float bx = n1x + n2x, by = n1y + n2y;
          float bl = sqrtf(bx * bx + by * by);
          if (bl < 0.0001f) { bx = n1x; by = n1y; bl = 1.0f; }
          float ox = shape_x[i] + bx / bl * d * shadow_side;
          float oy = shape_y[i] + by / bl * d * shadow_side;
          q[i] = (ImVec2){ax + dx * ox * s + px * oy * s, ay + dy * ox * s + py * oy * s};
        }
        /* A ring from the path out to q: nothing lands under the fill, which
           the arrow's own alpha would let show through. */
        ImU32 shade = color_u32(0, 0, 0, a);
        for (int i = 0; i < 7; ++i) {
          int next = (i + 1) % 7;
          ImDrawList_AddTriangleFilled(dl, p[i], p[next], q[next], shade);
          ImDrawList_AddTriangleFilled(dl, p[i], q[next], q[i], shade);
        }
      }
      if (want > before) before = want;
    }
  }
  ImDrawList_AddPolyline(dl, p, 7, color_u32(0, 0, 0, alpha),
                         ImDrawFlags_Closed, 9.0f * s);
  ImU32 fill = original_arrow_colour(env, alpha);
  ImVec2 shaft[4] = {p[0], p[1], p[2], p[6]};
  ImVec2 head[3] = {p[5], p[4], p[3]};
  ImDrawList_AddConvexPolyFilled(dl, shaft, 4, fill);
  ImDrawList_AddConvexPolyFilled(dl, head, 3, fill);
  float glow = alpha * original_accel_a *
               (0.5f + 0.5f * cosf(original_accel_fr));
  if (glow > 0.004f) {
    /* The ADD copy (arrow_add_ii): over the fill, the arrow's colour added at
       `glow`, drawn as the colour doubled at that alpha. */
    ImVec4 base;
    igColorConvertU32ToFloat4(&base, fill);
    ImU32 bright = color_u32(base.x * 2.0f > 1.0f ? 1.0f : base.x * 2.0f,
                             base.y * 2.0f > 1.0f ? 1.0f : base.y * 2.0f,
                             base.z * 2.0f > 1.0f ? 1.0f : base.z * 2.0f, glow);
    ImDrawList_AddConvexPolyFilled(dl, shaft, 4, bright);
    ImDrawList_AddConvexPolyFilled(dl, head, 3, bright);
  }
}

/* The joystick (Main.as 26514-26539, 28315-28326): a #808080 disc r 64 at
   scale 0.7 and a white knob r 48 at scale 0.375, both with a black halo and
   alpha 0.35; the knob sits 24u from the centre toward the steering angle. */
static void draw_original_joystick(tenv* env, ImDrawList* dl) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  float u = original_unit(env);
  float cx, cy;
  original_joystick_centre(env, &cx, &cy);
  /* Its size from the layout editor (OM, 2026-11-01), 1 = the original's. */
  float js = env->usr->usrs.ext.near_joy_size;
  if (!(js >= 0.65f && js <= 1.45f)) js = 1.0f;
  u *= js;
  float base = 64.0f * 0.7f * u;
  original_halo(dl, cx, cy, base, 7.0f * 0.7f * u, 0.35f);
  ImDrawList_AddCircleFilled(dl, (ImVec2){cx, cy}, base,
                             color_u32(0.502f, 0.502f, 0.502f, 0.35f), 48);
  float kx = cx, ky = cy;
  if (state->aim_valid) {
    kx += state->joystick_axis[0] * 24.0f * u;
    ky += state->joystick_axis[1] * 24.0f * u;
  }
  float knob = 48.0f * 0.375f * u;
  original_halo(dl, kx, ky, knob, 7.0f * 0.375f * u, 0.35f);
  ImDrawList_AddCircleFilled(dl, (ImVec2){kx, ky}, knob,
                             color_u32(1, 1, 1, 0.35f), 32);
}

/* The boost button, "boostie" (sheet0 of the original, 294 px, scale 0.35):
   a #A0A0A0 disc r 110 with a soft black halo and a white triangle with a
   shadow under it. Measured from the original image. */
static void draw_original_boost(tenv* env, ImDrawList* dl) {
  float cx, cy;
  original_boost_centre(env, &cx, &cy);
  float k = 0.35f * original_unit(env);
  float a = original_boost_alpha;
  static const float halo[] = {0.42f, 0.33f, 0.25f, 0.18f, 0.13f, 0.08f,
                               0.05f, 0.02f, 0.01f};
  for (int i = 0; i < 9; ++i)
    ImDrawList_AddCircle(dl, (ImVec2){cx, cy}, (110.0f + 2.0f + 4.0f * i) * k,
                         color_u32(0, 0, 0, a * halo[i]), 48, 4.0f * k + 0.5f);
  ImDrawList_AddCircleFilled(dl, (ImVec2){cx, cy}, 110.0f * k,
                             color_u32(0.627f, 0.627f, 0.627f, a), 48);
  for (int i = 1; i <= 4; ++i) {
    float drop = (2.0f + 4.0f * i) * k;
    ImDrawList_AddTriangleFilled(
        dl, (ImVec2){cx, cy - 41.5f * k + drop},
        (ImVec2){cx + 56.0f * k, cy + 25.5f * k + drop},
        (ImVec2){cx - 56.0f * k, cy + 25.5f * k + drop},
        color_u32(0, 0, 0, a * 0.07f));
  }
  ImDrawList_AddTriangleFilled(dl, (ImVec2){cx, cy - 41.5f * k},
                               (ImVec2){cx + 56.0f * k, cy + 25.5f * k},
                               (ImVec2){cx - 56.0f * k, cy + 25.5f * k},
                               color_u32(1, 1, 1, a));
}

static void draw_original_controls(tenv* env, ImDrawList* dl) {
  if (mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW)
    draw_original_arrow(env, dl);
  else if (mobile_controls_steering_mode(env) != MOBILE_STEERING_CLASSIC)
    draw_original_joystick(env, dl);
  /* Upright the original has no button: a second finger boosts. */
  if (env->wnd->size[0] >= env->wnd->size[1]) draw_original_boost(env, dl);
}

static void draw_zoom(tenv* env, ImDrawList* dl, bool editor) {
  (void)editor;
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  if (!cfg->zoom_enabled) return;
  float cx, cy, length, thickness;
  zoom_geometry(env, &cx, &cy, &length, &thickness);
  float t = (logf(env->usr->gdata.data.ms_zoom) - logf(MAX_ZOOM_OUT)) /
            (logf(MAX_ZOOM_IN) - logf(MAX_ZOOM_OUT));
  t = clampf(t, 0.0f, 1.0f);
  float alpha = env->usr->usrs.zoom_opacity;
  /* A paper capsule with an ink run and round knob. Geometry is unchanged. */
  float half = thickness * 0.30f;
  ImVec2 track_min, track_max, knob;
  if (cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL) {
    track_min = (ImVec2){cx - length * 0.5f, cy - half};
    track_max = (ImVec2){cx + length * 0.5f, cy + half};
    knob = (ImVec2){track_min.x + length * t, cy};
  } else {
    track_min = (ImVec2){cx - half, cy - length * 0.5f};
    track_max = (ImVec2){cx + half, cy + length * 0.5f};
    knob = (ImVec2){cx, track_max.y - length * t};
  }

  /* Wyrm's ink halo around the bar (OM, 2026-10-02). */
  for (int i = 0; i < 4; ++i) {
    float grow = 2.0f + i * 2.5f;
    ImDrawList_AddRect(dl, (ImVec2){track_min.x - grow, track_min.y - grow},
                       (ImVec2){track_max.x + grow, track_max.y + grow},
                       color_u32(0.035f, 0.040f, 0.055f, alpha * (0.16f - i * 0.035f)),
                       half + grow, 0, 2.5f);
  }
  ImDrawList_AddRectFilled(dl, (ImVec2){track_min.x, track_min.y + half * 0.18f},
                           (ImVec2){track_max.x, track_max.y + half * 0.18f},
                           color_u32(0, 0, 0, alpha * 0.16f), half, 0);
  ImDrawList_AddRectFilled(dl, track_min, track_max,
                           arena_theme_colour(ARENA_THEME_CARD, alpha * 0.94f),
                           half, 0);

  /* The spring bar (OM, 2026-10-05): the knob rests in the middle, the run
     reads from the middle to the knob, with - and + at the two ends. */
  if (feel_zoom_spring) {
    float half_len = length * 0.5f;
    if (cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL)
      knob = (ImVec2){cx + zoom_spring_t * half_len, cy};
    else
      knob = (ImVec2){cx, cy - zoom_spring_t * half_len};
  }
  /* Flipped (OM, 2026-10-30): the knob, run and marks mirror along the bar. */
  const bool zoom_flip = env->usr->usrs.ext.zoom_flip;
  if (zoom_flip) {
    if (cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL)
      knob.x = 2.0f * cx - knob.x;
    else
      knob.y = 2.0f * cy - knob.y;
  }
  /* The run reads from the near end to the knob, whichever way the bar sits. */
  ImVec2 fill_min = track_min;
  ImVec2 fill_max = track_max;
  if (feel_zoom_spring) {
    if (cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL) {
      fill_min.x = knob.x < cx ? knob.x : cx;
      fill_max.x = knob.x < cx ? cx : knob.x;
    } else {
      fill_min.y = knob.y < cy ? knob.y : cy;
      fill_max.y = knob.y < cy ? cy : knob.y;
    }
    /* Fit the complete baked glyph, including its halo, inside the track. */
    ImU32 sign = arena_theme_colour(ARENA_THEME_INK, alpha * 0.9f);
    float inset = half * 2.2f;
    ImVec2 minus, plus;
    if (cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL) {
      minus = (ImVec2){track_min.x + inset, cy};
      plus = (ImVec2){track_max.x - inset, cy};
    } else {
      minus = (ImVec2){cx, track_max.y - inset};
      plus = (ImVec2){cx, track_min.y + inset};
    }
    if (zoom_flip) {
      ImVec2 swap = minus;
      minus = plus;
      plus = swap;
    }
    /* The original's - and + (OM, 2026-11-01): the sheet's Black glyphs,
       white over their baked outline, as wide as the old marks. */
    (void)sign;
    ImU32 white = color_u32(1, 1, 1, alpha * 0.95f);
    sheet_glyph_fit('-', SHEET_BLACK, half*1.1f, half*1.1f, minus.x, minus.y, white);
    sheet_glyph_fit('+', SHEET_BLACK, half*1.1f, half*1.1f, plus.x, plus.y, white);
  } else if (cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL) {
    if (zoom_flip) fill_min.x = knob.x; else fill_max.x = knob.x;
  } else {
    if (zoom_flip) fill_max.y = knob.y; else fill_min.y = knob.y;
  }
  ImDrawList_AddRectFilled(dl, fill_min, fill_max,
                           arena_theme_colour(ARENA_THEME_INK, alpha * 0.78f),
                           half, 0);
  ImDrawList_AddRect(dl, track_min, track_max,
                     arena_theme_colour(ARENA_THEME_INK, alpha * 0.34f), half,
                     0, 1.5f);

  float knob_radius = thickness * 0.40f;
  ImDrawList_AddCircleFilled(dl, (ImVec2){knob.x, knob.y + knob_radius * 0.14f},
                             knob_radius * 1.05f,
                             color_u32(0, 0, 0, alpha * 0.20f), 32);
  ImDrawList_AddCircleFilled(dl, knob, knob_radius,
                             arena_theme_colour(ARENA_THEME_INK, alpha * 0.96f),
                             32);
}

void mobile_controls_draw_gameplay(tenv* env) {
#ifdef WYRM_DESKTOP
  /* WYRM_DESKTOP: mouse and keyboard; no touch controls are drawn. */
  (void)env;
  return;
#endif
#ifdef WYRM_SDL_JNI
  if (env->usr->gdata.curr_screen != PLAYING ||
      (env->usr->gdata.conn != CONNECTED &&
       env->usr->gdata.conn != AI_CONNECTED))
    return;
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  mobile_controls_state* state = &env->usr->mobile_controls;
  ImDrawList* dl = igGetForegroundDrawList_ViewportPtr(igGetMainViewport());
  /* Near Original: slither's own controls; the zoom bar and the on-screen
     buttons stay the player's. */
  if (android_home_near_original()) {
    draw_original_controls(env, dl);
    mobile_hotkeys_draw_gameplay(env);
    draw_zoom(env, dl, false); /* on top (OM, 2026-11-01) */
    return;
  }
  float jx, jy, bx, by;
  normalized_position(env, cfg->joystick_x, cfg->joystick_y, &jx, &jy);
  normalized_position(env, cfg->boost_x, cfg->boost_y, &bx, &by);

  if (mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW) {
    draw_arrow(env, dl);
  } else if (mobile_controls_steering_mode(env) == MOBILE_JOYSTICK_FIXED ||
             state->joystick_down) {
    if (mobile_controls_steering_mode(env) == MOBILE_JOYSTICK_DYNAMIC) {
      jx = state->joystick_origin[0];
      jy = state->joystick_origin[1];
    }
    draw_joystick(env, dl, jx, jy, state->joystick_down, false);
  }
  /* The fixed button only, and only while "Show boost button" is on (OM,
     2026-10-31); it lights while any boost is on. No button follows a finger. */
  bool arrow_boost = mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW;
  bool boosting = state->boost_down || (arrow_boost && arrow_tap_boost && state->joystick_down);
  if (cfg->boost_mode == MOBILE_BOOST_FIXED)
    draw_boost(env, dl, bx, by, boosting, false);
  mobile_hotkeys_draw_gameplay(env);
  /* The zoom bar over the buttons and boost, as it takes their touches
     (OM, 2026-11-01). */
  draw_zoom(env, dl, false);
#else
  (void)env;
#endif
}
