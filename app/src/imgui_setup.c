#include "imgui_setup.h"

#define CIMGUI_DEFINE_ENUMS_AND_STRUCTS

#include "cimgui/cimgui.h"
#include "cimgui/cimgui_impl.h"

#include "user.h"
#include "mobile/mobile_controls.h"
#include "ui/ui_theme.h"

/* Arena text (OM, 2026-11-07; Wyrm iOS/Scripts/engine_arena_fonts_2026_11_07.py):
   Manrope faces with Liberation Sans and the phone's own fonts behind them
   for every script Manrope lacks. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#if defined(__ANDROID__) || defined(__APPLE__)
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#endif
#ifdef __ANDROID__
#include <dirent.h>
#endif
#if defined(WYRM_SDL_JNI) || defined(WYRM_MOBILE)
#include <SDL3/SDL.h>
#define WYRM_FONT_LOG(...) SDL_Log(__VA_ARGS__)
#else
#define WYRM_FONT_LOG(...) ((void)0)
#endif

#if defined(__APPLE__) && defined(WYRM_IOS)
/* SourcesOriginal/Main.m: the iOS system fonts for other scripts. */
int wyrm_ios_fallback_fonts(char (*paths)[1024], int* indices, int max);
#endif

typedef struct wyrm_font_blob {
  void* data;
  size_t size;
  int index; /* face in a .ttc */
} wyrm_font_blob;

#define WYRM_FONT_BLOBS 32
/* Size limits (OM, 2026-11-07, iOS build 155: right after the system fonts
   were mapped, the engine's first texture upload failed on the Simulator).
   The huge CJK collections (PingFang, Apple SD Gothic Neo, tens of MB each)
   are left out on iOS, and every platform keeps a total; each loaded file is
   logged with its size so the next run shows exactly what was taken. */
#if defined(__APPLE__)
#define WYRM_FONT_FILE_MAX (16u << 20)
#define WYRM_FONT_TOTAL_MAX (40u << 20)
#else
#define WYRM_FONT_FILE_MAX (32u << 20)
#define WYRM_FONT_TOTAL_MAX (96u << 20)
#endif
static size_t wyrm_font_blob_bytes;
static wyrm_font_blob wyrm_font_blobs[WYRM_FONT_BLOBS];
static int wyrm_font_blob_count;
static const wyrm_font_blob* wyrm_fallback_regular[WYRM_FONT_BLOBS];
static const wyrm_font_blob* wyrm_fallback_bold[WYRM_FONT_BLOBS];
static int wyrm_fallback_regular_count, wyrm_fallback_bold_count;

/* ImGui frees a source's font data with its own allocator. The blobs are
   shared by every size and face and live as long as the process: a free of
   one is ignored. */
static bool wyrm_font_blob_shared(const void* p) {
  for (int i = 0; i < wyrm_font_blob_count; ++i)
    if (wyrm_font_blobs[i].data == p) return true;
  return false;
}
static void* wyrm_imgui_alloc(size_t size, void* user) {
  (void)user;
  return malloc(size);
}
static void wyrm_imgui_free(void* p, void* user) {
  (void)user;
  if (p && !wyrm_font_blob_shared(p)) free(p);
}

static uint32_t wyrm_be32(const unsigned char* p) {
  return (uint32_t)p[0] << 24 | (uint32_t)p[1] << 16 | (uint32_t)p[2] << 8 | p[3];
}

/* A face stb_truetype can draw: cmap plus glyf+loca or CFF (not CFF2-only,
   not a colour-bitmap emoji font). */
static bool wyrm_font_face_ok(const unsigned char* d, size_t size, int index) {
  if (size < 12) return false;
  size_t face = 0;
  if (memcmp(d, "ttcf", 4) == 0) {
    if (size < 16) return false;
    uint32_t faces = wyrm_be32(d + 8);
    if (index < 0 || (uint32_t)index >= faces || 12 + 4 * (size_t)faces > size)
      return false;
    face = wyrm_be32(d + 12 + 4 * (size_t)index);
  } else if (index != 0) {
    return false;
  }
  if (face + 12 > size) return false;
  uint32_t tag = wyrm_be32(d + face);
  if (tag != 0x00010000u && tag != 0x4F54544Fu /* OTTO */ &&
      tag != 0x74727565u /* true */) return false;
  unsigned tables = (unsigned)d[face + 4] << 8 | d[face + 5];
  if (face + 12 + 16 * (size_t)tables > size) return false;
  bool cmap = false, glyf = false, loca = false, cff = false;
  for (unsigned i = 0; i < tables; ++i) {
    const unsigned char* t = d + face + 12 + 16 * (size_t)i;
    uint32_t off = wyrm_be32(t + 8), len = wyrm_be32(t + 12);
    if ((size_t)off + len > size) return false;
    if (!memcmp(t, "cmap", 4)) cmap = true;
    if (!memcmp(t, "glyf", 4)) glyf = true;
    if (!memcmp(t, "loca", 4)) loca = true;
    if (!memcmp(t, "CFF ", 4)) cff = true;
  }
  return cmap && ((glyf && loca) || cff);
}

static const wyrm_font_blob* wyrm_font_blob_load(const char* path, int index) {
  if (!path || !path[0] || wyrm_font_blob_count >= WYRM_FONT_BLOBS) return NULL;
  void* data = NULL;
  size_t size = 0;
#if defined(__ANDROID__) || defined(__APPLE__)
  int fd = open(path, O_RDONLY | O_CLOEXEC);
  if (fd < 0) return NULL;
  struct stat st;
  if (fstat(fd, &st) == 0 && st.st_size > 100 && (size_t)st.st_size <= WYRM_FONT_FILE_MAX &&
      wyrm_font_blob_bytes + (size_t)st.st_size <= WYRM_FONT_TOTAL_MAX) {
    void* map = mmap(NULL, (size_t)st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    if (map != MAP_FAILED) {
      data = map;
      size = (size_t)st.st_size;
    }
  }
  close(fd);
#else
  FILE* f = fopen(path, "rb");
  if (!f) return NULL;
  if (fseek(f, 0, SEEK_END) == 0) {
    long n = ftell(f);
    if (n > 100 && (size_t)n <= WYRM_FONT_FILE_MAX &&
        wyrm_font_blob_bytes + (size_t)n <= WYRM_FONT_TOTAL_MAX && fseek(f, 0, SEEK_SET) == 0) {
      data = malloc((size_t)n);
      if (data && fread(data, 1, (size_t)n, f) == (size_t)n) {
        size = (size_t)n;
      } else {
        free(data);
        data = NULL;
      }
    }
  }
  fclose(f);
#endif
  if (!data) return NULL;
  if (!wyrm_font_face_ok((const unsigned char*)data, size, index)) {
#if defined(__ANDROID__) || defined(__APPLE__)
    munmap(data, size);
#else
    free(data);
#endif
    return NULL;
  }
  wyrm_font_blob_bytes += size;
  {
    const char* base = strrchr(path, '/');
    WYRM_FONT_LOG("Wyrm arena text: font %s (%u KB, face %d)", base ? base + 1 : path,
                  (unsigned)(size >> 10), index);
  }
  wyrm_font_blob* b = &wyrm_font_blobs[wyrm_font_blob_count++];
  b->data = data;
  b->size = size;
  b->index = index;
  return b;
}

static void wyrm_fallback_both(const char* path, int index) {
  const wyrm_font_blob* b = wyrm_font_blob_load(path, index);
  if (!b) return;
  wyrm_fallback_regular[wyrm_fallback_regular_count++] = b;
  wyrm_fallback_bold[wyrm_fallback_bold_count++] = b;
}

#ifdef __ANDROID__
/* The phone's fonts for scripts Manrope lacks, by file name; within a
   script the first name that exists wins (newer Android first). */
static const char* const wyrm_android_scripts[][4] = {
    {"NotoSansDevanagariUI-", "NotoSansDevanagari-", NULL},
    {"NotoSansBengaliUI-", "NotoSansBengali-", NULL},
    {"NotoSansGurmukhiUI-", "NotoSansGurmukhi-", NULL},
    {"NotoSansGujaratiUI-", "NotoSansGujarati-", NULL},
    {"NotoSansTamilUI-", "NotoSansTamil-", NULL},
    {"NotoSansTeluguUI-", "NotoSansTelugu-", NULL},
    {"NotoSansKannadaUI-", "NotoSansKannada-", NULL},
    {"NotoSansMalayalamUI-", "NotoSansMalayalam-", NULL},
    {"NotoSansOriyaUI-", "NotoSansOriya-", NULL},
    {"NotoNaskhArabicUI-", "NotoNaskhArabic-", "NotoSansArabic-", NULL},
    {"NotoSansThaiUI-", "NotoSansThai-", NULL},
    {"NotoSansHebrew-", NULL},
    {"NotoSansCJK-Regular", "NotoSansCJK", "NotoSansSC-", "DroidSansFallback"},
    {"NotoSansSymbols-Regular-Subsetted.", "NotoSansSymbols-Regular", NULL},
    {"NotoSansSymbols2-", NULL},
    {"NotoSansMath-", NULL},
};

static bool wyrm_font_name_ok(const char* name) {
  size_t n = strlen(name);
  if (n < 5) return false;
  const char* ext = name + n - 4;
  return !strcmp(ext, ".ttf") || !strcmp(ext, ".otf") || !strcmp(ext, ".ttc");
}

static void wyrm_fallbacks_android(void) {
  DIR* dir = opendir("/system/fonts");
  if (!dir) return;
  static char names[768][96];
  int count = 0;
  struct dirent* e;
  while ((e = readdir(dir)) && count < 768) {
    if (!wyrm_font_name_ok(e->d_name) || strlen(e->d_name) >= 96) continue;
    strcpy(names[count++], e->d_name);
  }
  closedir(dir);
  for (size_t s = 0; s < sizeof(wyrm_android_scripts) / sizeof(wyrm_android_scripts[0]); ++s) {
    const char* chosen = NULL;
    for (int c = 0; c < 4 && wyrm_android_scripts[s][c] && !chosen; ++c) {
      const char* prefix = wyrm_android_scripts[s][c];
      size_t len = strlen(prefix);
      const char* plain = NULL;
      for (int i = 0; i < count; ++i) {
        if (strncmp(names[i], prefix, len) != 0) continue;
        if (strstr(names[i], "Bold") || strstr(names[i], "Serif")) continue;
        if (strstr(names[i], "Regular") || strstr(names[i], "VF")) {
          chosen = names[i];
          break;
        }
        if (!plain) plain = names[i];
      }
      if (!chosen) chosen = plain;
    }
    if (!chosen) continue;
    char path[160];
    snprintf(path, sizeof(path), "/system/fonts/%s", chosen);
    wyrm_fallback_both(path, 0);
  }
}
#endif

/* Once, before the first face: Liberation Sans of each weight, then the
   platform's fonts. */
static void wyrm_fallbacks_find(void) {
  static bool found;
  if (found) return;
  found = true;
  const wyrm_font_blob* regular = wyrm_font_blob_load("app/res/fonts/regular_regular.ttf", 0);
  const wyrm_font_blob* bold = wyrm_font_blob_load("app/res/fonts/regular_bold.ttf", 0);
  if (regular) wyrm_fallback_regular[wyrm_fallback_regular_count++] = regular;
  if (bold) wyrm_fallback_bold[wyrm_fallback_bold_count++] = bold;
  else if (regular) wyrm_fallback_bold[wyrm_fallback_bold_count++] = regular;
#ifdef __ANDROID__
  wyrm_fallbacks_android();
#elif defined(__APPLE__) && defined(WYRM_IOS)
  static char paths[24][1024];
  int indices[24];
  int n = wyrm_ios_fallback_fonts(paths, indices, 24);
  for (int i = 0; i < n; ++i) wyrm_fallback_both(paths[i], indices[i]);
#endif
  WYRM_FONT_LOG("Wyrm arena text: %d fallback fonts", wyrm_fallback_regular_count);
}

/* Merges the fallbacks into the face added last (after its icons). */
static void wyrm_merge_fallbacks(ImFontAtlas* atlas, float size, bool bold) {
  const wyrm_font_blob* const* list = bold ? wyrm_fallback_bold : wyrm_fallback_regular;
  int count = bold ? wyrm_fallback_bold_count : wyrm_fallback_regular_count;
  for (int i = 0; i < count; ++i) {
    ImFontConfig* cfg = ImFontConfig_ImFontConfig();
    cfg->MergeMode = true;
    cfg->FontDataOwnedByAtlas = true; /* shared: see wyrm_imgui_free */
    cfg->FontNo = (ImU32)list[i]->index;
    ImFontAtlas_AddFontFromMemoryTTF(atlas, list[i]->data, (int)list[i]->size,
                                     size, cfg, NULL);
    ImFontConfig_destroy(cfg);
  }
}

/* A Manrope face, or the old file if the new one is missing. */
static ImFont* wyrm_add_face(ImFontAtlas* atlas, const char* path,
                             const char* old_path, float size) {
  ImFont* font = NULL;
  FILE* probe = fopen(path, "rb");
  if (probe) {
    fclose(probe);
    font = ImFontAtlas_AddFontFromFileTTF(atlas, path, size, NULL, NULL);
  }
  if (!font) font = ImFontAtlas_AddFontFromFileTTF(atlas, old_path, size, NULL, NULL);
  return font;
}

#ifdef WYRM_SDL_JNI
#include <SDL3/SDL.h>

static tenv* android_env;
static bool ui_touch_active;
static SDL_FingerID ui_touch_finger;

static bool is_touch_mouse_event(const SDL_Event* event) {
  switch (event->type) {
    case SDL_EVENT_MOUSE_MOTION:
      return event->motion.which == SDL_TOUCH_MOUSEID;
    case SDL_EVENT_MOUSE_BUTTON_DOWN:
    case SDL_EVENT_MOUSE_BUTTON_UP:
      return event->button.which == SDL_TOUCH_MOUSEID;
    case SDL_EVENT_MOUSE_WHEEL:
      return event->wheel.which == SDL_TOUCH_MOUSEID;
    default:
      return false;
  }
}

/* Dear ImGui names its generic pointer queue "Mouse", but the source marker
   below is TouchScreen and the data comes straight from SDL finger events.
   This deliberately bypasses SDL's touch-to-mouse compatibility layer. */
static bool imgui_process_direct_touch(const SDL_Event* event,
                                       bool gameplay_owned) {
  if (event->type == SDL_EVENT_WILL_ENTER_BACKGROUND ||
      event->type == SDL_EVENT_WINDOW_FOCUS_LOST) {
    if (ui_touch_active) {
      ImGuiIO* io = igGetIO_Nil();
      ImGuiIO_AddMouseSourceEvent(io, ImGuiMouseSource_TouchScreen);
      ImGuiIO_AddMouseButtonEvent(io, 0, false);
      ui_touch_active = false;
    }
    return false;
  }

  if (event->type != SDL_EVENT_FINGER_DOWN &&
      event->type != SDL_EVENT_FINGER_MOTION &&
      event->type != SDL_EVENT_FINGER_UP &&
      event->type != SDL_EVENT_FINGER_CANCELED)
    return false;

  const SDL_FingerID finger = event->tfinger.fingerID;
  if (event->type == SDL_EVENT_FINGER_DOWN) {
    if (gameplay_owned || ui_touch_active) return false;
    ui_touch_active = true;
    ui_touch_finger = finger;
  } else if (!ui_touch_active || ui_touch_finger != finger) {
    return false;
  }

  ImGuiIO* io = igGetIO_Nil();
  float width = io->DisplaySize.x;
  float height = io->DisplaySize.y;
  if (width <= 0.0f || height <= 0.0f) {
    int window_width = 0;
    int window_height = 0;
    SDL_GetWindowSize(android_env->wnd->handle, &window_width, &window_height);
    width = (float)window_width;
    height = (float)window_height;
  }

  ImGuiIO_AddMouseSourceEvent(io, ImGuiMouseSource_TouchScreen);
  ImGuiIO_AddMousePosEvent(io, event->tfinger.x * width,
                          event->tfinger.y * height);

  if (event->type == SDL_EVENT_FINGER_DOWN) {
    ImGuiIO_AddMouseButtonEvent(io, 0, true);
  } else if (event->type == SDL_EVENT_FINGER_UP ||
             event->type == SDL_EVENT_FINGER_CANCELED) {
    ImGuiIO_AddMouseButtonEvent(io, 0, false);
    ui_touch_active = false;
  }
  return true;
}

static bool imgui_process_android_event(const void* event) {
  const SDL_Event* sdl_event = event;
  bool gameplay_owned = mobile_controls_process_event(android_env, event);
  bool touch_handled =
      imgui_process_direct_touch(sdl_event, gameplay_owned);

  /* SDL_TOUCH_MOUSEID events are compatibility duplicates of the raw finger
     stream above. Never queue them a second time. Physical mouse, keyboard,
     text and controller events still use the official SDL ImGui backend. */
  bool backend_handled = false;
  if (!is_touch_mouse_event(sdl_event) &&
      sdl_event->type != SDL_EVENT_FINGER_DOWN &&
      sdl_event->type != SDL_EVENT_FINGER_MOTION &&
      sdl_event->type != SDL_EVENT_FINGER_UP &&
      sdl_event->type != SDL_EVENT_FINGER_CANCELED)
    backend_handled = igImplSDL3_ProcessEvent(event);

  return gameplay_owned || touch_handled || backend_handled;
}
#endif

void imgui_init(tenv* env) {
  tuser_data* usr = env->usr;

  /* Before the context: shared font data is never freed (arena text). */
  igSetAllocatorFunctions(wyrm_imgui_alloc, wyrm_imgui_free, NULL);
  igCreateContext(NULL);
  igImplVulkan_Init(&(ImGui_ImplVulkan_InitInfo){
      .ApiVersion = VK_API_VERSION_1_0,
      .Instance = env->ctx->instance,
      .PhysicalDevice = env->ctx->ph_device,
      .Device = env->ctx->device,
      .QueueFamily = env->ctx->queue_family,
      .Queue = env->ctx->queue,
      /* ImGui's own descriptor pool (OM, 2026-10-05: Moto crash while sizing
         stats). The engine's pool holds 20 sets for the whole renderer, and
         ImGui shared it: its font atlas, logo, home icons, tags, arrows and
         looks. A HUD size slider asks for a font size not baked yet, the
         atlas grows into a new texture, its descriptor set could not be
         allocated from the full pool, and vkUpdateDescriptorSets faulted
         (ImGui_ImplVulkan_AddTexture, null pointer). The backend makes and
         frees this pool itself. */
      .DescriptorPool = VK_NULL_HANDLE,
      .DescriptorPoolSize = 64,
      .MinImageCount = env->ctx->min_image_count,
      .ImageCount = env->ctx->fif,
      .PipelineCache = VK_NULL_HANDLE,
      .PipelineInfoMain = {.RenderPass = env->ctx->renderpass,
                           .Subpass = 0,
                           .MSAASamples = VK_SAMPLE_COUNT_1_BIT},
      .UseDynamicRendering = false});
#ifdef WYRM_SDL_JNI
  android_env = env;
  ui_touch_active = false;
  SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS, "0");
  igImplSDL3_InitForVulkan(env->wnd->handle);
  twindow_set_event_handler(env->wnd, imgui_process_android_event);
#else
  igImplGlfw_InitForVulkan(env->wnd->handle, true);
#endif
  ImGuiIO* io = igGetIO_Nil();
  // io->MouseDrawCursor = true;

  wyrm_fallbacks_find();
  for (int i = 0; i < NUM_FONT_SIZES; i++) {
    ImFontConfig icons_config = {.FontDataOwnedByAtlas = true,
                                 .OversampleH = 0,
                                 .OversampleV = 0,
                                 .GlyphMaxAdvanceX = FLT_MAX,
                                 .RasterizerDensity = 1,
                                 .RasterizerMultiply = 1,
                                 .EllipsisChar = 0,
                                 .MergeMode = true,
                                 .GlyphOffset = (ImVec2){0, 2 + i},
                                 .GlyphMinAdvanceX = 26.0f + i * 6};

    /* Arena text in Manrope (OM, 2026-11-07), each face followed by its
       icons and then the fallbacks for other scripts. */
    const float px = 20 + i * 4;
    usr->imgui_data.mono_font[i] = wyrm_add_face(
        io->Fonts, "app/res/fonts/manrope_semibold.ttf",
        "app/res/fonts/mono_regular.ttf", px);

    ImFontAtlas_AddFontFromFileTTF(
        io->Fonts, "app/res/fonts/iconfont.ttf", 20 + i * 4, &icons_config,
        (const ImWchar[]){0xe900, 0xeaea, 0});
    wyrm_merge_fallbacks(io->Fonts, px, false);

    usr->imgui_data.regular_font[i] = wyrm_add_face(
        io->Fonts, "app/res/fonts/manrope_semibold.ttf",
        "app/res/fonts/regular_regular.ttf", px);

    ImFontAtlas_AddFontFromFileTTF(
        io->Fonts, "app/res/fonts/iconfont.ttf", 20 + i * 4, &icons_config,
        (const ImWchar[]){0xe900, 0xeaea, 0});
    wyrm_merge_fallbacks(io->Fonts, px, false);

    usr->imgui_data.mono_font_bold[i] = wyrm_add_face(
        io->Fonts, "app/res/fonts/manrope_bold.ttf",
        "app/res/fonts/mono_bold.ttf", px);

    ImFontAtlas_AddFontFromFileTTF(io->Fonts, "app/res/fonts/iconfont.ttf",
                                   20 + i * 4, &icons_config,
                                   (const ImWchar[]){0xe900, 0xeaea, 0});
    wyrm_merge_fallbacks(io->Fonts, px, true);

    usr->imgui_data.regular_font_bold[i] = wyrm_add_face(
        io->Fonts, "app/res/fonts/manrope_extrabold.ttf",
        "app/res/fonts/regular_bold.ttf", px);

    ImFontAtlas_AddFontFromFileTTF(io->Fonts, "app/res/fonts/iconfont.ttf",
                                   20 + i * 4, &icons_config,
                                   (const ImWchar[]){0xe900, 0xeaea, 0});
    wyrm_merge_fallbacks(io->Fonts, px, true);

    /* The two faces the app itself is set in. The display serif carries
       numbers and is loaded a size larger at every step, because a serif at
       a body size reads as decoration. Body is Manrope Medium: the app's
       variable Manrope draws at its ExtraLight master here. */
    usr->imgui_data.display_font[i] = ImFontAtlas_AddFontFromFileTTF(
        io->Fonts, "app/res/fonts/wyrm_display.ttf", 24 + i * 5, NULL, NULL);
    wyrm_merge_fallbacks(io->Fonts, 24 + i * 5, false);

    usr->imgui_data.body_font[i] = wyrm_add_face(
        io->Fonts, "app/res/fonts/manrope_medium.ttf",
        "app/res/fonts/wyrm_body.ttf", 18 + i * 4);
    wyrm_merge_fallbacks(io->Fonts, 18 + i * 4, false);
  }
  
  /* Near Original (OM, 2026-10-02): the original game's own faces, from the
     slither.io app itself (OFL). Sized per draw call. */
  usr->imgui_data.nunito_bold = ImFontAtlas_AddFontFromFileTTF(
      io->Fonts, "app/res/fonts/nunito_bold.ttf", 22, NULL, NULL);
  wyrm_merge_fallbacks(io->Fonts, 22, true);
  usr->imgui_data.nunito_black = ImFontAtlas_AddFontFromFileTTF(
      io->Fonts, "app/res/fonts/nunito_black.ttf", 22, NULL, NULL);
  wyrm_merge_fallbacks(io->Fonts, 22, true);

  io->ConfigFlags |= ImGuiConfigFlags_DockingEnable;
  io->IniFilename = NULL;

  ui_theme_apply();
}

void imgui_prerender() {
  igImplVulkan_NewFrame();
#ifdef WYRM_SDL_JNI
  igImplSDL3_NewFrame();
#else
  igImplGlfw_NewFrame();
#endif
  igNewFrame();
}

void imgui_render(VkCommandBuffer cmd) {
  igImplVulkan_RenderDrawData(igGetDrawData(), cmd, VK_NULL_HANDLE);
}

void imgui_destroy() {
#ifdef WYRM_SDL_JNI
  igImplSDL3_Shutdown();
#else
  igImplGlfw_Shutdown();
#endif
  igImplVulkan_Shutdown();
  igDestroyContext(NULL);
}
