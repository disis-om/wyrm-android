#include "mobile_controls.h"

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

#ifdef VLITHER_ANDROID
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
  if (env->wnd->size[1] > env->wnd->size[0]) return MOBILE_STEERING_ARROW;
  return env->usr->usrs.mobile_controls.joystick_mode;
}

/*
 * Near Original (OM, 2026-10-02): slither's own controls, from the original
 * game's Main.as. Its unit is the short side / 480 (`force_game_scale`).
 * Fixed joystick at (150u, H - 130u), boost at (W - 140u, H - 140u), both
 * mirrored when the joystick is on the right (Wyrm's Handedness = the
 * original's flip). Display and touch only.
 */
static float original_unit(tenv* env) {
  int short_side = env->wnd->size[0] < env->wnd->size[1] ? env->wnd->size[0]
                                                         : env->wnd->size[1];
  return short_side / 480.0f;
}

static bool original_joystick_right(tenv* env) {
  return env->usr->usrs.mobile_controls.handedness != MOBILE_LEFT_HANDED;
}

static void original_joystick_centre(tenv* env, float* x, float* y) {
  float u = original_unit(env);
  *x = original_joystick_right(env) ? env->wnd->size[0] - 150.0f * u
                                    : 150.0f * u;
  *y = env->wnd->size[1] - 130.0f * u;
}

static void original_boost_centre(tenv* env, float* x, float* y) {
  float u = original_unit(env);
  *x = original_joystick_right(env) ? 140.0f * u
                                    : env->wnd->size[0] - 140.0f * u;
  *y = env->wnd->size[1] - 140.0f * u;
}

/* The boost button's alpha (0.2 idle, 0.4 boosting) and the arrow's boost
   glow (`accel_a`, `accel_fr`), stepped once a frame. */
static float original_boost_alpha = 0.3f;
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
  float t = cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL
                ? (x - (cx - length * 0.5f)) / length
                : 1.0f - (y - (cy - length * 0.5f)) / length;
  t = clampf(t, 0.0f, 1.0f);
  env->usr->gdata.data.ms_zoom =
      expf(logf(MAX_ZOOM_OUT) + t * (logf(MAX_ZOOM_IN) - logf(MAX_ZOOM_OUT)));
}

static void reset_touches(mobile_controls_state* state) {
  state->joystick_down = false;
  state->boost_down = false;
  state->zoom_down = false;
  state->edit_down = false;
  state->ui_scroll_down = false;
  state->arrow_drag_distance = 0.0f;
  state->edit_target = MOBILE_EDIT_NONE;
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
#ifdef VLITHER_ANDROID
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
static bool joystick_heading(tenv* env, float* hx, float* hy) {
  game_data* gdata = &env->usr->gdata;
  snake* own = get_snake(gdata, gdata->data.snake_id);
  if (!own || own->dead) return false;
  *hx = cosf(own->ehang);
  *hy = sinf(own->ehang);
  return true;
}

#ifdef VLITHER_ANDROID
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
  /* Near Original: the original start distance (separation 1). */
  float separation = android_home_near_original() ? 1.0f : arrow->separation;
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
static void update_arrow(tenv* env, float x, float y) {
  mobile_controls_state* state = &env->usr->mobile_controls;
  state->arrow_vec[0] += x - state->arrow_last[0];
  state->arrow_vec[1] += y - state->arrow_last[1];
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

/* Near Original touch (Main.as touch begin): sideways the boost button takes
   a finger within 160u of its centre; the arrow takes the first finger
   anywhere; the joystick takes a finger on its half; upright a second finger
   boosts (the original hides the button there). Releases go through the
   normal path. */
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
        state->boost_down = true;
        state->boost_finger = finger;
        state->boost_origin[0] = bx;
        state->boost_origin[1] = by;
        return true;
      }
    }
    if (arrow) {
      if (!state->joystick_down) {
        state->joystick_down = true;
        state->joystick_finger = finger;
        state->joystick_origin[0] = x;
        state->joystick_origin[1] = y;
        state->arrow_drag_distance = 0.0f;
        arrow_seed(env, x, y);
        return true;
      }
      if (upright && !state->boost_down && state->joystick_finger != finger) {
        state->boost_down = true;
        state->boost_finger = finger;
        state->boost_origin[0] = x;
        state->boost_origin[1] = y;
      }
      return true;
    }
    bool on_side = original_joystick_right(env)
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

bool mobile_controls_process_event(tenv* env, const void* raw_event) {
  const SDL_Event* event = raw_event;
  mobile_controls_state* state = &env->usr->mobile_controls;
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;

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

  /* Playable AI's first-entry notice owns the whole touch surface. The layout
     editor never shows it, and no steering/button state can leak underneath. */
  if (ai_mode_notice_process_event(env, raw_event)) {
    reset_touches(state);
    mobile_hotkeys_reset_runtime(env);
    return true;
  }

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

  // Visible on-screen buttons own their finger before joystick, boost or zoom.
  // This preserves the direct-touch isolation contract for every button.
  if (mobile_hotkeys_process_event(env, raw_event)) return true;

  /* Near Original: slither's own fixed controls (releases fall through). */
  if (android_home_near_original() && event->type != SDL_EVENT_FINGER_UP &&
      event->type != SDL_EVENT_FINGER_CANCELED)
    return original_touch(env, event, finger, x, y);

  if (event->type == SDL_EVENT_FINGER_DOWN) {
    float mid = env->wnd->size[0] * 0.5f;
    bool joystick_left = cfg->handedness == MOBILE_LEFT_HANDED;
    bool in_joystick_half = joystick_left ? x < mid : x >= mid;
    /* Upright there is no left or right hand (OM, 2026-10-02): the first free
       finger anywhere starts the dynamic joystick, and once a joystick is
       held (or it is a fixed one) any other finger is touch-zone boost, the
       way Arrow steering already works. Fixed controls keep their drawn hit
       circles above. Sideways is unchanged. Only which finger starts which
       control changes; nothing is sent differently. */
    if (env->wnd->size[1] > env->wnd->size[0])
      in_joystick_half = mobile_controls_steering_mode(env) == MOBILE_JOYSTICK_DYNAMIC &&
                         !state->joystick_down;
    float jx, jy, bx, by;
    normalized_position(env, cfg->joystick_x, cfg->joystick_y, &jx, &jy);
    normalized_position(env, cfg->boost_x, cfg->boost_y, &bx, &by);
    float scale = control_scale(env);

    bool fixed_boost_hit =
        cfg->boost_mode == MOBILE_BOOST_FIXED &&
        inside_circle(x, y, bx, by, 112.0f * scale * cfg->boost_size);
    bool fixed_joystick_hit =
        mobile_controls_steering_mode(env) == MOBILE_JOYSTICK_FIXED &&
        inside_circle(x, y, jx, jy, 125.0f * scale * cfg->joystick_size);

    // Fixed controls own their visible hit areas even if the user moved one
    // across the handedness divider in the layout editor.
    if (!state->boost_down && fixed_boost_hit) {
      state->boost_down = true;
      state->boost_finger = finger;
      state->boost_origin[0] = bx;
      state->boost_origin[1] = by;
      return true;
    }
    if (!state->joystick_down && fixed_joystick_hit) {
      state->joystick_down = true;
      state->joystick_finger = finger;
      state->joystick_origin[0] = jx;
      state->joystick_origin[1] = jy;
      update_joystick(env, x, y);
      return true;
    }

    // A deliberately placed fixed steering/boost control takes precedence
    // over the movable zoom bar. Outside those explicit hit circles, the zoom
    // control can safely claim its own finger.
    if (!state->zoom_down && inside_zoom(env, x, y)) {
      state->zoom_down = true;
      state->zoom_finger = finger;
      set_zoom_from_touch(env, x, y);
      return true;
    }

    if (mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW) {
      // Arrow steering is relative to the touch-down point. The first finger
      // may start anywhere on the gameplay surface; moving it changes heading
      // without snapping the snake toward the absolute tap position. In
      // touch-zone mode the second finger may boost from anywhere. Fixed
      // mode owns only its drawn button; letting this shortcut run there made
      // every second touch behave as though it had hit that button.
      if (cfg->boost_mode == MOBILE_BOOST_TOUCH_ZONE &&
          state->joystick_down && state->joystick_finger != finger &&
          !state->boost_down) {
        state->boost_down = true;
        state->boost_finger = finger;
        state->boost_origin[0] = x;
        state->boost_origin[1] = y;
        return true;
      }
      if (!state->joystick_down) {
        state->joystick_down = true;
        state->joystick_finger = finger;
        state->joystick_origin[0] = x;
        state->joystick_origin[1] = y;
        state->arrow_drag_distance = 0.0f;
        arrow_seed(env, x, y);
        return true;
      }
    } else if (mobile_controls_steering_mode(env) == MOBILE_JOYSTICK_DYNAMIC &&
               !state->joystick_down && in_joystick_half) {
      state->joystick_down = true;
      state->joystick_finger = finger;
      state->joystick_origin[0] = x;
      state->joystick_origin[1] = y;
      /* The base is placed so the finger holds the knob on the snake's side:
         the stick starts where the snake is going, not in the centre. */
      float hx, hy;
      if (joystick_heading(env, &hx, &hy)) {
        float reach = 92.0f * control_scale(env) * cfg->joystick_size;
        state->joystick_origin[0] = x - hx * reach;
        state->joystick_origin[1] = y - hy * reach;
      }
      update_joystick(env, x, y);
      return true;
    }

    bool can_take_boost = cfg->boost_mode == MOBILE_BOOST_TOUCH_ZONE &&
                          !in_joystick_half;
    if (!state->boost_down && can_take_boost) {
      state->boost_down = true;
      state->boost_finger = finger;
      state->boost_origin[0] = x;
      state->boost_origin[1] = y;
      return true;
    }
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
    state->joystick_down = false;
    state->boost_down = false;
    state->zoom_down = false;
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
    mobile_arrow_settings* arrow = &env->usr->usrs.arrow_controls;
    game_data* gdata = &env->usr->gdata;
    float vfr = gdata->data.vfr;
    if (!(vfr > 0.0f) || vfr > 4.0f) vfr = 1.0f;

    bool steering = mobile_controls_steering_mode(env) == MOBILE_STEERING_ARROW &&
                    state->joystick_down && state->aim_valid;

    /* The setting reads as how much it lags, so it is one minus the catch-up.
       slither eases at 0.6, which is a smoothness of 0.4. */
    /* Near Original: slither's own 0.6 catch-up (smoothness 0.4). */
    float smoothness = android_home_near_original() ? 0.4f : arrow->smoothness;
    float ease = clampf(1.0f - smoothness, 0.05f, 0.95f);
    ease = 1.0f - powf(1.0f - ease, vfr);
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

    /* Near Original: the boost button's alpha (Main.as: 0.2 idle, up to 0.4
       while boosting, 0.01 a frame) and the arrow's boost glow (accel_a
       +0.02 / -0.03 a frame, accel_fr +0.15 while boosting). */
    if (state->boost_down) {
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
#ifdef VLITHER_ANDROID
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
#ifdef VLITHER_ANDROID
  return env->usr->mobile_controls.boost_down;
#else
  (void)env;
  return false;
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
  /* Near Original: the release drift is in the original's unit. */
  float drift = 260.0f * (android_home_near_original() ? original_unit(env) : scale) *
                powf(state->arrow_dead, 2.5f);
  *ax = env->wnd->size[0] * 0.5f + state->arrow_draw[0] + *dx * drift;
  *ay = env->wnd->size[1] * 0.5f + state->arrow_draw[1] + *dy * drift;
  *length = 70.0f * scale * arrow->size;
  *width = 52.0f * scale * arrow->size;
  return true;
}

bool mobile_controls_get_arrow_position(tenv* env, float* x, float* y) {
#ifdef VLITHER_ANDROID
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

static void draw_joystick(tenv* env, ImDrawList* dl, float cx, float cy,
                          bool active, bool editor) {
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  mobile_controls_state* state = &env->usr->mobile_controls;
  float radius = 92.0f * control_scale(env) * cfg->joystick_size;
  float alpha = env->usr->usrs.joystick_opacity;

  draw_paper_disc(dl, cx, cy, radius, alpha, active);
  /* How far the thumb may travel, at the faintest weight that still reads. */
  ImDrawList_AddCircle(dl, (ImVec2){cx, cy}, radius * 0.62f,
                       active
                           ? arena_theme_colour(ARENA_THEME_ON_INK, alpha * 0.22f)
                           : arena_theme_colour(ARENA_THEME_INK, alpha * 0.14f),
                       48, 1.0f);

  float ax = state->joystick_axis[0];
  float ay = state->joystick_axis[1];
  /* At rest (or held in the dead zone) the knob shows where the snake goes. */
  float hx, hy;
  if (!editor && (!active || (fabsf(ax) < 0.08f && fabsf(ay) < 0.08f)) &&
      joystick_heading(env, &hx, &hy)) {
    ax = hx;
    ay = hy;
  }
  float kx = cx + ax * radius * 0.58f;
  float ky = cy + ay * radius * 0.58f;
  if (editor) {
    kx = cx;
    ky = cy;
  }
  float knob = radius * 0.39f;
  ImDrawList_AddCircleFilled(dl, (ImVec2){kx, ky + knob * 0.12f}, knob * 1.06f,
                             color_u32(0, 0, 0, alpha * 0.18f), 36);
  ImDrawList_AddCircleFilled(dl, (ImVec2){kx, ky}, knob,
                             active
                                 ? arena_theme_colour(ARENA_THEME_ON_INK, alpha * 0.96f)
                                 : arena_theme_colour(ARENA_THEME_INK, alpha * 0.92f),
                             36);
  ImDrawList_AddCircle(dl, (ImVec2){kx, ky}, knob,
                       active
                           ? arena_theme_colour(ARENA_THEME_ON_INK, alpha)
                           : arena_theme_colour(ARENA_THEME_INK, alpha),
                       36, 1.5f);
}

static void draw_boost(tenv* env, ImDrawList* dl, float cx, float cy,
                       bool active, bool editor) {
  (void)editor;
  mobile_control_settings* cfg = &env->usr->usrs.mobile_controls;
  float radius = 78.0f * control_scale(env) * cfg->boost_size;
  float alpha = env->usr->usrs.boost_opacity;

  draw_paper_disc(dl, cx, cy, radius, alpha, active);

  /* Two chevrons, drawn rather than typed: the interface font has no glyph
     worth using here, and lines stay crisp at any control size. */
  float glyph = radius * 0.30f;
  ImU32 ink = arena_theme_colour(ARENA_THEME_INK, 1.0f);
  for (int i = 0; i < 2; ++i) {
    float ox = cx + (i == 0 ? -glyph * 0.55f : glyph * 0.45f);
    ImVec2 chevron[3] = {{ox - glyph * 0.35f, cy - glyph * 0.62f},
                         {ox + glyph * 0.35f, cy},
                         {ox - glyph * 0.35f, cy + glyph * 0.62f}};
    ImDrawList_AddPolyline(dl, chevron, 3, ink, ImDrawFlags_None,
                           radius * 0.075f);
  }
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
  if (env->usr->usrs.arrow_style == MOBILE_ARROW_CURRENT) {
    ImDrawList_AddConvexPolyFilled(dl, points, shape.count, fill);
  } else {
    for (int i = 0; i < shape.count; ++i)
      ImDrawList_AddTriangleFilled(dl, (ImVec2){ax, ay}, points[i],
                                   points[(i + 1) % shape.count], fill);
  }
  ImDrawList_AddPolyline(dl, points, shape.count,
                         color_u32(0.015f, 0.022f, 0.028f, alpha),
                         ImDrawFlags_Closed, 4.0f);
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
  ImVec2 p[7];
  for (int i = 0; i < 7; ++i)
    p[i] = (ImVec2){ax + dx * shape_x[i] * s + px * shape_y[i] * s,
                    ay + dy * shape_x[i] * s + py * shape_y[i] * s};
  for (int i = 3; i >= 1; --i)
    ImDrawList_AddPolyline(dl, p, 7, color_u32(0, 0, 0, alpha * 0.10f),
                           ImDrawFlags_Closed, (9.0f + 5.0f * i) * s);
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
    ImU32 bright = color_u32(1, 1, 1, glow * 0.5f);
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
  else
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

  ImDrawList_AddRectFilled(dl, (ImVec2){track_min.x, track_min.y + half * 0.18f},
                           (ImVec2){track_max.x, track_max.y + half * 0.18f},
                           color_u32(0, 0, 0, alpha * 0.16f), half, 0);
  ImDrawList_AddRectFilled(dl, track_min, track_max,
                           arena_theme_colour(ARENA_THEME_CARD, alpha * 0.94f),
                           half, 0);

  /* The run reads from the near end to the knob, whichever way the bar sits. */
  ImVec2 fill_min = track_min;
  ImVec2 fill_max = track_max;
  if (cfg->zoom_orientation == MOBILE_ZOOM_HORIZONTAL)
    fill_max.x = knob.x;
  else
    fill_min.y = knob.y;
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
#ifdef VLITHER_ANDROID
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
    draw_zoom(env, dl, false);
    mobile_hotkeys_draw_gameplay(env);
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
  if (cfg->boost_mode == MOBILE_BOOST_FIXED || state->boost_down) {
    if (cfg->boost_mode == MOBILE_BOOST_TOUCH_ZONE) {
      bx = state->boost_origin[0];
      by = state->boost_origin[1];
    }
    draw_boost(env, dl, bx, by, state->boost_down, false);
  }
  draw_zoom(env, dl, false);
  mobile_hotkeys_draw_gameplay(env);
#else
  (void)env;
#endif
}
