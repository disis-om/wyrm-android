/* Host check for the v2.8 ext block (OM, 2026-10-05).
   Runs in a temp directory: USER_SETTINGS_FILE is the relative "user.dat". */
#include "../app/src/game/user_settings.h"

#include <direct.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int _getpid(void);
__declspec(dllimport) void __stdcall ExitProcess(unsigned int code);

/* vcruntime is not installed here. The loader only needs these. */
int _fltused = 0;

void* memcpy(void* dst, const void* src, size_t n) {
  unsigned char* out = (unsigned char*)dst;
  const unsigned char* in = (const unsigned char*)src;
  size_t i;
  for (i = 0; i < n; i++) out[i] = in[i];
  return dst;
}

void* memset(void* dst, int value, size_t n) {
  unsigned char* out = (unsigned char*)dst;
  size_t i;
  for (i = 0; i < n; i++) out[i] = (unsigned char)value;
  return dst;
}

/* True so the loader's on-screen-button repair does not rewrite the file. */
bool mobile_hotkey_is_on_screen_button(int action) {
  (void)action;
  return true;
}

static int failures = 0;

static void expect(int ok, const char* what) {
  if (ok) return;
  fputs("FAIL ", stderr);
  fputs(what, stderr);
  fputc('\n', stderr);
  failures += 1;
}

static void label_of(char* dst, size_t cap, const char* label, const char* tail) {
  size_t i = 0;
  while (*label && i + 1 < cap) dst[i++] = *label++;
  while (*tail && i + 1 < cap) dst[i++] = *tail++;
  dst[i] = 0;
}

static long file_length(const char* path) {
  FILE* file = fopen(path, "rb");
  long size = -1;
  if (!file) return -1;
  fseek(file, 0, SEEK_END);
  size = ftell(file);
  fclose(file);
  return size;
}

static int read_all(const char* path, unsigned char** out, size_t* out_size) {
  FILE* file = fopen(path, "rb");
  long size;
  if (!file) return 0;
  fseek(file, 0, SEEK_END);
  size = ftell(file);
  rewind(file);
  *out = (unsigned char*)malloc((size_t)size);
  if (!*out) { fclose(file); return 0; }
  *out_size = fread(*out, 1, (size_t)size, file);
  fclose(file);
  return *out_size == (size_t)size;
}

static int write_all(const char* path, const void* data, size_t size) {
  FILE* file = fopen(path, "wb");
  if (!file) return 0;
  if (fwrite(data, 1, size, file) != size) { fclose(file); return 0; }
  fclose(file);
  return 1;
}

static void fill_known(user_settings* settings) {
  memset(settings, 0, sizeof(*settings));
  user_settings_default(settings);
  strcpy(settings->nickname, "KeepMe");
  settings->score = 42;
  settings->modes[0].render_mode = 1;
  settings->modes[1].render_mode = 2;
}

static void expect_known(const user_settings* settings, const char* label) {
  char what[96];
  label_of(what, sizeof(what), label, " nickname");
  expect(strcmp(settings->nickname, "KeepMe") == 0, what);
  label_of(what, sizeof(what), label, " score");
  expect(settings->score == 42, what);
  label_of(what, sizeof(what), label, " normal render");
  expect(settings->modes[0].render_mode == 1, what);
  label_of(what, sizeof(what), label, " assist render");
  expect(settings->modes[1].render_mode == 2, what);
}

static void append_uint(char* dst, unsigned long value) {
  char tmp[16];
  int n = 0;
  if (value == 0) tmp[n++] = '0';
  while (value && n < (int)sizeof(tmp)) {
    tmp[n++] = (char)('0' + (value % 10));
    value /= 10;
  }
  while (n > 0) *dst++ = tmp[--n];
  *dst = 0;
}

int main(void) {
  char dir[520];
  char* slash;
  const char* root;
  user_settings* saved = (user_settings*)malloc(sizeof(user_settings));
  user_settings* loaded = (user_settings*)malloc(sizeof(user_settings));
  unsigned char* bytes = NULL;
  size_t bytes_n = 0;
  size_t ext_at = offsetof(user_settings, ext);
  size_t magic_at = ext_at + offsetof(user_settings_ext, magic);
  size_t size_at = ext_at + offsetof(user_settings_ext, size);
  uint32_t future = (uint32_t)sizeof(user_settings_ext) + 16;
  unsigned char tail[16];

  root = getenv("TEMP");
  if (!root || !root[0]) root = getenv("TMP");
  if (!root || !root[0]) root = ".";
  slash = dir;
  while (*root && (size_t)(slash - dir) + 1 < sizeof(dir)) *slash++ = *root++;
  if ((size_t)(slash - dir) + 24 < sizeof(dir)) {
    *slash++ = '\\';
    *slash++ = 'w'; *slash++ = 'y'; *slash++ = 'r'; *slash++ = 'm'; *slash++ = '-';
    *slash++ = 's'; *slash++ = 'e'; *slash++ = 'x'; *slash++ = 't'; *slash++ = '-';
    append_uint(slash, (unsigned long)_getpid());
  } else {
    dir[0] = 0;
  }
  _mkdir(dir);
  if (_chdir(dir) != 0 || !saved || !loaded) {
    fputs("FAIL chdir\n", stderr);
    return 1;
  }

  fill_known(saved);
  save_user_settings(saved);
  expect(read_all("user.dat", &bytes, &bytes_n), "read saved file");
  expect(bytes_n >= ext_at, "saved file reaches ext");
  expect(write_all("user.dat", bytes, ext_at), "truncate to old 2.8 prefix");
  free(bytes);
  bytes = NULL;

  memset(loaded, 0x5a, sizeof(*loaded));
  read_user_settings(loaded);
  expect_known(loaded, "old");
  expect(loaded->ext.spine[0] == false && loaded->ext.spine[1] == false, "old spine default");
  expect(loaded->ext.assist_hide_cosmetics == false, "old hide default");
  expect(loaded->ext.magic == USER_SETTINGS_EXT_MAGIC, "old magic repaired");
  expect(file_length("user.dat") >= (long)sizeof(user_settings), "old file grew");

  loaded->ext.spine[0] = true;
  loaded->ext.spine[1] = true;
  loaded->ext.assist_hide_cosmetics = true;
  save_user_settings(loaded);
  memset(loaded, 0, sizeof(*loaded));
  read_user_settings(loaded);
  expect_known(loaded, "current");
  expect(loaded->ext.spine[0] == true && loaded->ext.spine[1] == true, "current spine");
  expect(loaded->ext.assist_hide_cosmetics == true, "current hide");

  expect(read_all("user.dat", &bytes, &bytes_n), "read current file");
  memcpy(bytes + size_at, &future, sizeof(future));
  memset(tail, 0xab, sizeof(tail));
  {
    FILE* file = fopen("user.dat", "wb");
    expect(file != NULL, "open future file");
    if (file) {
      expect(fwrite(bytes, 1, bytes_n, file) == bytes_n, "write future prefix");
      expect(fwrite(tail, 1, sizeof(tail), file) == sizeof(tail), "write future tail");
      fclose(file);
    }
  }
  free(bytes);
  bytes = NULL;
  memset(loaded, 0, sizeof(*loaded));
  read_user_settings(loaded);
  expect_known(loaded, "future");
  expect(loaded->ext.spine[0] == true && loaded->ext.assist_hide_cosmetics == true,
         "future bools kept");
  expect(file_length("user.dat") == (long)(bytes_n + sizeof(tail)),
         "future tail kept");

  expect(read_all("user.dat", &bytes, &bytes_n), "read for garbage magic");
  memset(bytes + magic_at, 0, 4);
  expect(write_all("user.dat", bytes, bytes_n > sizeof(user_settings)
                                          ? sizeof(user_settings)
                                          : bytes_n),
         "write garbage magic");
  free(bytes);
  memset(loaded, 0, sizeof(*loaded));
  read_user_settings(loaded);
  expect_known(loaded, "garbage");
  expect(loaded->ext.spine[0] == false && loaded->ext.assist_hide_cosmetics == false,
         "garbage ext defaulted");

  free(saved);
  free(loaded);
  if (failures) {
    fputs("settings ext failed\n", stderr);
    return 1;
  }
  fputs("settings ext ok\n", stdout);
  return 0;
}

void mainCRTStartup(void) {
  ExitProcess((unsigned int)main());
}
