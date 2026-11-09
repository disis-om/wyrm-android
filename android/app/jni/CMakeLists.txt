cmake_minimum_required(VERSION 3.22.1)

project(Wyrm LANGUAGES C CXX)

# APP_VERSION comes from ../.cxx-version/wyrm_version.h, written by build.gradle.

get_filename_component(WYRM_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../.." ABSOLUTE)

set(SDL_SHARED ON CACHE BOOL "Build SDL as a shared library" FORCE)
set(SDL_STATIC OFF CACHE BOOL "Disable the unused SDL static library" FORCE)
set(SDL_TESTS OFF CACHE BOOL "Disable SDL tests" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "Source export omits SDL tests" FORCE)
add_subdirectory("${WYRM_ROOT}/android-sdl" "${CMAKE_BINARY_DIR}/SDL" EXCLUDE_FROM_ALL)

file(GLOB_RECURSE WYRM_C_SOURCES CONFIGURE_DEPENDS
    "${WYRM_ROOT}/app/src/*.c"
    "${WYRM_ROOT}/thermite/src/*.c")
file(GLOB_RECURSE WYRM_CXX_SOURCES CONFIGURE_DEPENDS
    "${WYRM_ROOT}/app/src/*.cpp"
    "${WYRM_ROOT}/thermite/src/*.cpp")

list(FILTER WYRM_CXX_SOURCES EXCLUDE REGEX "imgui_impl_glfw\\.cpp$")

add_library(main SHARED ${WYRM_C_SOURCES} ${WYRM_CXX_SOURCES})

target_compile_features(main PRIVATE c_std_11 cxx_std_17)
target_compile_definitions(main PRIVATE
    SETTINGS_VERSION="2.8"
    CIMGUI_NO_EXPORT
    CGLM_FORCE_DEPTH_ZERO_TO_ONE
    MG_ENABLE_IPV6=1
    # Arena text (OM, 2026-11-07): 32-bit ImWchar, so names past U+FFFF
    # (maths letters) can be drawn. C and C++ must agree on it.
    IMGUI_USE_WCHAR32
    WYRM_SDL_JNI=1)
target_include_directories(main PRIVATE
    "${WYRM_ROOT}/app/src"
    "${CMAKE_CURRENT_LIST_DIR}/../.cxx-version"
    "${WYRM_ROOT}/thermite/include"
    "${WYRM_ROOT}/thermite/src"
    "${WYRM_ROOT}/android-sdl/include")
target_compile_options(main PRIVATE
    $<$<COMPILE_LANGUAGE:C>:-Wall;-Wextra;-Wno-unused-parameter>
    $<$<COMPILE_LANGUAGE:CXX>:-Wall;-Wextra;-Wno-unused-parameter>
    # -O3 like the reference client (OM, 2026-10-18), after the build type's
    # own -O2 so it wins. No -ffast-math: Eyes Back and the tag rope are
    # checked number for number against NTL.
    $<$<NOT:$<CONFIG:Debug>>:-O3>)

find_library(ANDROID_LIB android REQUIRED)
find_library(LOG_LIB log REQUIRED)
find_library(VULKAN_LIB vulkan REQUIRED)

# Android Frame Pacing (Swappy), from the androidx.games:games-frame-pacing
# AAR through Prefab (build.gradle). Static, built for c++_shared like Wyrm.
# Used by thermite/src/graphics/tswappy.c (OM, 2026-11-05).
find_package(games-frame-pacing REQUIRED CONFIG)

target_link_libraries(main PRIVATE
    games-frame-pacing::swappy_static
    SDL3::SDL3
    ${ANDROID_LIB}
    ${LOG_LIB}
    ${VULKAN_LIB})
