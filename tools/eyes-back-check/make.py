"""Eyes Back check (2026-10-06), from this folder: Wyrm's C (sliced from input.c) vs NTL VANCED's own NTL_EB JS.
python make.py [ios]  then  node run.mjs
Builds two wasm harnesses from the text really in input.c:
  eb_double.wasm  the same text with float -> double, to compare logic with JS
  eb_float.wasm   the text as shipped (float), for the closed-loop check
and slices NTL_EB's IIFE from NTL VANCED/main-mt.js."""
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
W = HERE.parents[2]  # the wyrm folder
SRC = ("Wyrm iOS/build-original-source/app/src/game/input.c" if "ios" in sys.argv
       else "Wyrm Android/app/src/game/input.c")
src = (W / SRC).read_text(encoding="utf-8").replace("\r\n", "\n")


def between(text, start, end):
    assert text.count(start) == 1, start
    i = text.index(start)
    return text[i:text.index(end, i)]


core = between(src, "#define EB_EPS 8.0f", "static void eb_send(")
# The tick itself: NTL runs NTL_EB.tick under its 33 ms send gate (`33<ab-qc`),
# so eb_send must use ARENA_EYES_BACK_MS = 33, not the web aim gate (2026-10-09).
send = between(src, "static void eb_send(", "static void eyes_back_toggle(")
assert "last_e_mtm > ARENA_EYES_BACK_MS)) return;" in send, "eb_send is not on the Eyes Back gate"
proto = (W / SRC.replace("game/input.c", "network/arena_protocol.h")).read_text(encoding="utf-8")
assert "ARENA_EYES_BACK_MS = 33" in proto, "ARENA_EYES_BACK_MS is not 33 ms"
print("gate: eb_send ticks every 33 ms like NTL")
heading = between(src, "void eyes_back_heading(float ang, float now) {", "static void input_with_policy(")

PRE = r'''
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
typedef __SIZE_TYPE__ size_t_;
static void* memset(void* d, int c, unsigned long n) { unsigned char* p = d; while (n--) *p++ = (unsigned char)c; return d; }
static void* memmove(void* d, const void* s, unsigned long n) {
  unsigned char* a = d; const unsigned char* b = s;
  if (a < b) while (n--) *a++ = *b++; else { a += n; b += n; while (n--) *--a = *--b; }
  return d; }
'''


def harness(double):
    body = core + heading
    if double:
        body = re.sub(r"\bfloat\b", "double", body)
        body = re.sub(r"(\d)f\b", r"\1", body)
        for fn in ("fmodf", "fabsf", "fminf", "fmaxf", "floorf"):
            body = body.replace(fn + "(", fn[:-1] + "(")
        math = r'''
#define PI2 6.283185307179586
#define PI 3.141592653589793
static double fmod(double x, double y) { return x - y * __builtin_trunc(x / y); }
static double fabs(double x) { return __builtin_fabs(x); }
static double fmin(double a, double b) { return a < b ? a : b; }
static double fmax(double a, double b) { return a > b ? a : b; }
static double floor(double x) { return __builtin_floor(x); }
typedef struct { double ang, scang, spang; int id; } snake;
#define REAL double
'''
    else:
        math = r'''
#define PI2 6.2831853f
#define PI 3.1415926f
static float fmodf(float x, float y) { return (float)((double)x - (double)y * __builtin_trunc((double)x / (double)y)); }
static float fabsf(float x) { return __builtin_fabsf(x); }
static float fminf(float a, float b) { return a < b ? a : b; }
static float fmaxf(float a, float b) { return a > b ? a : b; }
static float floorf(float x) { return __builtin_floorf(x); }
typedef struct { float ang, scang, spang; int id; } snake;
#define REAL float
'''
    api = r'''
static snake S;
__attribute__((export_name("set_on"))) void set_on(int v) { eb.on = v != 0; eb_reset(); }
__attribute__((export_name("tick")))
int tick(int aiming, double target, double ang, double scang, double spang, double now, double rtt, double mamu) {
  S.ang = (REAL)ang; S.scang = (REAL)scang; S.spang = (REAL)spang;
  return eb_tick(aiming != 0, (REAL)target, &S, (REAL)now, (REAL)rtt, (REAL)mamu);
}
__attribute__((export_name("report"))) void report(double ang, double now) { eyes_back_heading((REAL)ang, (REAL)now); }
'''
    return PRE + math + body + api


# The NDK clang (set CLANG to override).
import os
CLANG = os.environ.get("CLANG") or str(Path(os.environ.get("ANDROID_HOME") or Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk")
    / "ndk" / "28.2.13676358" / "toolchains" / "llvm" / "prebuilt" / "windows-x86_64" / "bin" / "clang.exe")
for name, dbl in (("eb_double", True), ("eb_float", False)):
    (HERE / f"{name}.c").write_text(harness(dbl), encoding="utf-8")
    r = subprocess.run([CLANG, "--target=wasm32", "-O1", "-nostdlib", "-ffreestanding", "-Wall",
                        "-Wno-unused-function", "-Wno-incompatible-library-redeclaration",
                        "-Wl,--no-entry", "-Wl,--export-dynamic", "-o", str(HERE / f"{name}.wasm"),
                        str(HERE / f"{name}.c")], capture_output=True, text=True)
    print(name, "clang rc", r.returncode, r.stderr[-2500:])
    if r.returncode:
        sys.exit(1)

js = (W / "NTL VANCED/main-mt.js").read_bytes().decode("utf-8", errors="replace")
i = js.index("var NTL_EB = (function () {")
j = js.index("  /* Current toggle key", i)
iife = js[i + len("var NTL_EB = (function () {"):j]
assert "on = false" in iife
iife = iife.replace("on = false", "on = true", 1)
(HERE / "ntl_eb_body.js").write_text(iife, encoding="utf-8")
print("NTL_EB body", len(iife), "chars; C core", len(core), "chars from", SRC)
