#ifndef TWINDOW_H
#define TWINDOW_H

#ifdef WYRM_SDL_JNI
#include <SDL3/SDL.h>

/* Keep the desktop key-code contract so settings remain portable. */
#define GLFW_KEY_UNKNOWN -1
#define GLFW_KEY_0 48
#define GLFW_KEY_1 49
#define GLFW_KEY_2 50
#define GLFW_KEY_3 51
#define GLFW_KEY_4 52
#define GLFW_KEY_5 53
#define GLFW_KEY_6 54
#define GLFW_KEY_7 55
#define GLFW_KEY_8 56
#define GLFW_KEY_9 57
#define GLFW_KEY_A 65
#define GLFW_KEY_B 66
#define GLFW_KEY_C 67
#define GLFW_KEY_D 68
#define GLFW_KEY_E 69
#define GLFW_KEY_F 70
#define GLFW_KEY_G 71
#define GLFW_KEY_H 72
#define GLFW_KEY_I 73
#define GLFW_KEY_J 74
#define GLFW_KEY_K 75
#define GLFW_KEY_L 76
#define GLFW_KEY_M 77
#define GLFW_KEY_N 78
#define GLFW_KEY_O 79
#define GLFW_KEY_P 80
#define GLFW_KEY_Q 81
#define GLFW_KEY_R 82
#define GLFW_KEY_S 83
#define GLFW_KEY_T 84
#define GLFW_KEY_U 85
#define GLFW_KEY_V 86
#define GLFW_KEY_W 87
#define GLFW_KEY_X 88
#define GLFW_KEY_Y 89
#define GLFW_KEY_Z 90
#define GLFW_KEY_SPACE 32
#define GLFW_KEY_RIGHT 262
#define GLFW_KEY_LEFT 263
#define GLFW_KEY_UP 265
#define GLFW_KEY_DOWN 264
#define GLFW_KEY_F11 300
#define GLFW_MOUSE_BUTTON_LEFT 0
#define GLFW_MOUSE_BUTTON_RIGHT 1
#define GLFW_MOUSE_BUTTON_MIDDLE 2
#ifdef WYRM_DESKTOP
/* WYRM_DESKTOP: keys a computer has beyond the phone list, and the mouse's
   other buttons as keys (above GLFW's last key, 348), so any of them can be
   bound to an action. The left button stays boost and is never a key. */
#define GLFW_KEY_TAB 258
#define GLFW_KEY_F1 290
#define GLFW_KEY_F2 291
#define GLFW_KEY_F3 292
#define GLFW_KEY_F4 293
#define GLFW_KEY_F5 294
#define GLFW_KEY_F6 295
#define GLFW_KEY_F7 296
#define GLFW_KEY_F8 297
#define GLFW_KEY_F9 298
#define GLFW_KEY_F10 299
#define GLFW_KEY_F12 301
#define GLFW_KEY_LEFT_SHIFT 340
#define GLFW_KEY_LEFT_CONTROL 341
#define WYRM_KEY_MOUSE_RIGHT 401
#define WYRM_KEY_MOUSE_MIDDLE 402
#define WYRM_KEY_MOUSE_BACK 403
#define WYRM_KEY_MOUSE_FORWARD 404
#define WYRM_DESKTOP_KEY_SLOTS 512
/* The next wheel turn went to a panel; it must not zoom. */
void twindow_desktop_take_wheel(void);
#endif

double glfwGetTime(void);
void glfwSetTime(double value);
#else
#include <GLFW/glfw3.h>
#endif
#include <cglm/struct.h>
#include <stdbool.h>

typedef struct tkeyboard tkeyboard;
typedef struct tmouse tmouse;
typedef struct twindow twindow;
typedef struct tenv tenv;

typedef void (*trender_func)(tenv* env);
typedef void (*tresize_func)(tenv* env);
typedef bool (*tevent_func)(const void* event);

typedef struct twindow {
#ifdef WYRM_SDL_JNI
  SDL_Window* handle;
#else
  GLFWwindow* handle;
#endif
  ivec2 size;
  ivec2 lsize;
  ivec2 lpos;
  trender_func _render_func;
  tresize_func _resize_func;
  tenv* env;
  bool _refresh;
  bool _closed;
  tevent_func _event_func;
} twindow;

void twindow_request_refresh(twindow* twindow);
twindow* twindow_create(tenv* env, trender_func render_func,
                        tresize_func resize_func);
void twindow_poll_input(twindow* window);
void twindow_wait_input(twindow* window);
void twindow_toggle_fullscreen(twindow* window);
bool twindow_key_down(twindow* window, int key);
bool twindow_button_down(twindow* window, int button);
bool twindow_closed(twindow* window);
void twindow_set_event_handler(twindow* window, tevent_func event_func);
void twindow_destroy(twindow* window);

#endif
