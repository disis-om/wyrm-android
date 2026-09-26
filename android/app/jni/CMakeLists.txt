cmake_minimum_required(VERSION 3.22.1)

project(VlitherEnhanced LANGUAGES C CXX)

set(WYRM_APP_VERSION "0.1.0" CACHE STRING "Wyrm application version")

get_filename_component(VLITHER_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../.." ABSOLUTE)

set(SDL_SHARED ON CACHE BOOL "Build SDL as a shared library" FORCE)
set(SDL_STATIC OFF CACHE BOOL "Disable the unused SDL static library" FORCE)
set(SDL_TESTS OFF CACHE BOOL "Disable SDL tests" FORCE)
add_subdirectory("${VLITHER_ROOT}/android-sdl" "${CMAKE_BINARY_DIR}/SDL" EXCLUDE_FROM_ALL)

file(GLOB_RECURSE VLITHER_C_SOURCES CONFIGURE_DEPENDS
    "${VLITHER_ROOT}/app/src/*.c"
    "${VLITHER_ROOT}/thermite/src/*.c")
file(GLOB_RECURSE VLITHER_CXX_SOURCES CONFIGURE_DEPENDS
    "${VLITHER_ROOT}/app/src/*.cpp"
    "${VLITHER_ROOT}/thermite/src/*.cpp")

list(FILTER VLITHER_CXX_SOURCES EXCLUDE REGEX "imgui_impl_glfw\\.cpp$")

add_library(main SHARED ${VLITHER_C_SOURCES} ${VLITHER_CXX_SOURCES})

target_compile_features(main PRIVATE c_std_11 cxx_std_17)
target_compile_definitions(main PRIVATE
    APP_VERSION="${WYRM_APP_VERSION}"
    SETTINGS_VERSION="2.8"
    CIMGUI_NO_EXPORT
    CGLM_FORCE_DEPTH_ZERO_TO_ONE
    VLITHER_ANDROID=1)
target_include_directories(main PRIVATE
    "${VLITHER_ROOT}/app/src"
    "${VLITHER_ROOT}/thermite/include"
    "${VLITHER_ROOT}/thermite/src"
    "${VLITHER_ROOT}/android-sdl/include")
target_compile_options(main PRIVATE
    $<$<COMPILE_LANGUAGE:C>:-Wall;-Wextra;-Wno-unused-parameter>
    $<$<COMPILE_LANGUAGE:CXX>:-Wall;-Wextra;-Wno-unused-parameter>)

find_library(ANDROID_LIB android REQUIRED)
find_library(LOG_LIB log REQUIRED)
find_library(VULKAN_LIB vulkan REQUIRED)

target_link_libraries(main PRIVATE
    SDL3::SDL3
    ${ANDROID_LIB}
    ${LOG_LIB}
    ${VULKAN_LIB})
