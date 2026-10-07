"""NTL tag corner check (2026-10-04). Usage, from this folder:
    python make.py        then  node run.mjs   (Wyrm Android's callback.c)
    python make.py ios    then  node run.mjs   (Wyrm iOS's generated callback.c,
                                               after prepare-original-engine.py)
Builds a wasm harness (NDK clang) from the C text that is really in
callback.c, and slices NTL 9.68's own JS (legacy/ntl 9.68/main-mt.js);
run.mjs compares every tag x preset x custom join and every corner read."""
import json, re, subprocess, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
W = HERE.parents[2]  # the wyrm folder
SRC = ("Wyrm iOS/build-original-source/app/src/network/callback.c" if "ios" in sys.argv
       else "Wyrm Android/app/src/network/callback.c")
cb = (W / SRC).read_text(encoding="utf-8").replace("\r\n", "\n")

def between(text, start, end, include_end=True):
    i = text.index(start)
    j = text.index(end, i)
    assert text.count(start) == 1, start[:50]
    return text[i:j + (len(end) if include_end else 0)]

helpers = between(cb, "#define NTL_TAG_FREE_MARK 254", "/*\n * The snake this player is steering", include_end=False)
join_alloc = between(cb, "    /* NTL's tag corner (see ntl_tag_corner). A preset",
                     "    ba = malloc(8 + 20 + nick_len + (skin_compressed ? 8 + skin_compressed_len : 0));\n")
header = between(cb, "    if (skin_compressed) {\n      int corner = m;\n", "                preset_block ? \"preset\" : \"custom\", skin_compressed_len);\n      }\n")
reader = between(cb, "      int corner_tag = -1;\n", "          corner_preset = a[m + 2];\n      }\n")
assign = between(cb, "      if (corner_preset >= 0) {\n", "                                     : 0;\n")

# Wyrm's tag sheet, in sheet order, as NTL numbers.
tags_c = (W / "Wyrm Android/app/src/game/tags.c").read_text(encoding="utf-8")
ntl_ids = [int(x) for x in re.findall(r"\.ntl\s*=\s*(\d+)", tags_c)]
if not ntl_ids:
    swift = (W / "Wyrm iOS/SourcesShell/WyrmSkinCatalog.generated.swift").read_text(encoding="utf-8")
    ntl_ids = [int(x) for x in re.findall(r"ntlID: (\d+)", swift)]
ntl_ids = [x for x in ntl_ids if x < 100000]  # NTL's only; Wyrm's own are stubbed after them
print("tag sheet", len(ntl_ids), ntl_ids[:3], ntl_ids[-3:])

c = r'''
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
#define SDL_Log(...) ((void)0)
#define NUM_DEFAULT_SKINS 66
static int rand(void) { return 0x5A; }
static uint8_t arr[1024]; static int arr_n;
static uint8_t* arr_create(void) { arr_n = 0; return arr; }
#define tdarray_create(T) arr_create()
#define tdarray_push(pp, vp) (arr[arr_n++] = *(vp))
#define tdarray_length(p) (arr_n)
static uint8_t heap[1024];
static void* malloc(size_t n) { (void)n; return heap; }
static const int NTL_IDS[] = {''' + ",".join(map(str, ntl_ids)) + r'''};
#define TAGS (int)(sizeof(NTL_IDS) / sizeof(NTL_IDS[0]))
/* Wyrm's own tags follow NTL's: indices TAGS .. TAGS + WYRM_TAG_COUNT - 1. */
#define WYRM_TAG_COUNT 77
static bool tags_valid(int i) { return i >= 0 && i < TAGS + WYRM_TAG_COUNT; }
static int tags_ntl_id(int i) { return i >= 0 && i < TAGS ? NTL_IDS[i] : -1; }
static int tags_from_ntl_id(int n) { for (int i = 0; i < TAGS; i++) if (NTL_IDS[i] == n) return i; return -1; }
static int tags_wyrm_id(int i) { return i >= TAGS && i < TAGS + WYRM_TAG_COUNT ? i - TAGS : -1; }
static int tags_from_wyrm_id(int w) { return w >= 0 && w < WYRM_TAG_COUNT ? TAGS + w : -1; }
typedef struct { int tag_index; uint8_t default_skin; } settings;
typedef struct { int cv; bool cusk; int cusk_len; int skin_tag; } fake_snake;
''' + helpers + r'''
__attribute__((export_name("out"))) uint8_t* out(void) { return heap; }
__attribute__((export_name("build")))
int build(int tag_index, int default_skin, int custom) {
  settings S = {tag_index, (uint8_t)default_skin};
  settings* usrs = &S;
  bool web_persona = true;
  int nick_len = 0;
  uint8_t* ba = NULL;
  uint8_t* skin_compressed = NULL;
  int skin_compressed_len = 0;
  if (custom) {
    skin_compressed = tdarray_create(uint8_t);
    uint8_t r1 = 3, c1 = 7, r2 = 2, c2 = 9;
    tdarray_push(&skin_compressed, &r1); tdarray_push(&skin_compressed, &c1);
    tdarray_push(&skin_compressed, &r2); tdarray_push(&skin_compressed, &c2);
    skin_compressed_len = 4;
  }
''' + join_alloc + r'''
  int m = 0;
''' + header + r'''
      for (int i = 0; i < skin_compressed_len; i++) ba[m++] = skin_compressed[i];
    }
  return m;
}
static int read_out[4];
__attribute__((export_name("rd"))) int* rd(void) { return read_out; }
__attribute__((export_name("read")))
void read(int b0, int b1, int b2, int b6, int b7) {
  uint8_t a[8] = {(uint8_t)b0, (uint8_t)b1, (uint8_t)b2, 0, 0, 0, (uint8_t)b6, (uint8_t)b7};
  int m = 0, skl = 8, alen = 8;
  fake_snake o = {5 % 66, true, 9, 0};
''' + reader + assign + r'''
  read_out[0] = corner_tag; read_out[1] = o.cusk ? -1 : o.cv; read_out[2] = o.skin_tag; read_out[3] = corner_wyrm;
}
'''
(HERE / "harness.c").write_text(c, encoding="utf-8")
# The NDK clang (set CLANG to override).
import os
CLANG = os.environ.get("CLANG") or str(Path(os.environ.get("ANDROID_HOME") or Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk")
    / "ndk" / "28.2.13676358" / "toolchains" / "llvm" / "prebuilt" / "windows-x86_64" / "bin" / "clang.exe")
r = subprocess.run([CLANG, "--target=wasm32", "-O1", "-nostdlib", "-ffreestanding", "-Wall", "-Wno-unused-function",
                    "-Wl,--no-entry", "-Wl,--export-dynamic", "-o", str(HERE / "harness.wasm"), str(HERE / "harness.c")],
                   capture_output=True, text=True)
print("clang rc", r.returncode, r.stderr[-3000:])
if r.returncode: sys.exit(1)

# NTL's own source, sliced verbatim.
js = (W / "legacy/ntl 9.68/main-mt.js").read_text(encoding="utf-8", errors="replace")
def slice_js(start, end):
    i = js.index(start); j = js.index(end, i) + len(end)
    return js[i:j]
ff_src = slice_js("Ff=function(bb)", "return ab}")
zf_src = slice_js('Zf="255 255 255 0 0 0 0 0 1 0"', '.split(" ")')
xa_src = slice_js("xA=[{i:24", "{i:65,t:39}]")
ha_src = slice_js("HA=function(){", "65<cb||!gb)}")
build_src = slice_js("if(!cb.length&&!Er&&!gb){cb=new Uint8Array(8+Ff(ib).length);", "cb[0]=254));")
read_src = slice_js("cb[1]==tn&&(255!=cb[2]?", "cb[6]>=fstags.length&&(bb.P=-1))")
spec = {"ff": ff_src, "zf": zf_src, "xa": xa_src, "ha": ha_src, "build": build_src, "read": read_src, "ids": ntl_ids}
(HERE / "ntl_slices.json").write_text(json.dumps(spec), encoding="utf-8")
print("slices", {k: len(v) for k, v in spec.items() if k != "ids"})
