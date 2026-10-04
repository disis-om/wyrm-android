/* Host stand-in so settings_ext_test can compile user_settings.c without Vulkan.
   Key codes match thermite/src/framework/twindow.h. */
#ifndef TIKTAALIK_H
#define TIKTAALIK_H
#include <stdbool.h>
#include <stdint.h>
#define GLFW_KEY_UNKNOWN -1
#define GLFW_KEY_SPACE 32
#define GLFW_KEY_A 65
#define GLFW_KEY_F 70
#define GLFW_KEY_H 72
#define GLFW_KEY_K 75
#define GLFW_KEY_M 77
#define GLFW_KEY_N 78
#define GLFW_KEY_P 80
#define GLFW_KEY_Q 81
#define GLFW_KEY_R 82
#define GLFW_KEY_T 84
#define GLFW_KEY_V 86
#define GLFW_KEY_Z 90
#define GLFW_KEY_RIGHT 262
#define GLFW_KEY_LEFT 263
#define GLFW_KEY_F11 300
#endif
