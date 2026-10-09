
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
typedef __SIZE_TYPE__ size_t_;
static void* memset(void* d, int c, unsigned long n) { unsigned char* p = d; while (n--) *p++ = (unsigned char)c; return d; }
static void* memmove(void* d, const void* s, unsigned long n) {
  unsigned char* a = d; const unsigned char* b = s;
  if (a < b) while (n--) *a++ = *b++; else { a += n; b += n; while (n--) *--a = *--b; }
  return d; }

#define PI2 6.2831853f
#define PI 3.1415926f
static float fmodf(float x, float y) { return (float)((double)x - (double)y * __builtin_trunc((double)x / (double)y)); }
static float fabsf(float x) { return __builtin_fabsf(x); }
static float fminf(float a, float b) { return a < b ? a : b; }
static float fmaxf(float a, float b) { return a > b ? a : b; }
static float floorf(float x) { return __builtin_floorf(x); }
typedef struct { float ang, scang, spang; int id; } snake;
#define REAL float
#define EB_EPS 8.0f
#define EB_TURN_MARGIN 4.0f
#define EB_EPS_MAX 22.0f
#define EB_SNAP 0.55f
#define EB_BAND 2.5f
#define EB_LOG_MAX 160

typedef struct eb_cmd {
  float te; /* when it reaches the server, client clock (ms) */
  float w;  /* the angle sent (rad) */
  float r;  /* the snake's turn rate then (rad/ms) */
} eb_cmd;

static struct {
  bool on;
  float err;
  float last_t;
  bool anchored;
  float anchor_t;
  float anchor_a;
  eb_cmd log[EB_LOG_MAX];
  int log_n;
  float res_max;
  bool holding;
  float hold;
  bool seen;
  float last_seen;
  const void* conn; /* the socket and snake the model belongs to */
  int snake_id;
  bool sent;
  float sent_ang; /* the last byte sent, in radians: where the eyes look */
  float rtt;
} eb = {.rtt = 100.0f, .snake_id = -1};

static float eb_norm(float a) {
  a = fmodf(a, PI2);
  if (a > PI)
    a -= PI2;
  else if (a < -PI)
    a += PI2;
  return a;
}

static void eb_reset(void) {
  bool on = eb.on;
  memset(&eb, 0, sizeof(eb));
  eb.on = on;
  eb.rtt = 100.0f;
  eb.snake_id = -1;
}

static float eb_turn(float a, float w, float r, float ms) {
  float d = eb_norm(w - a), mx = r * ms;
  return a + (d > mx ? mx : d < -mx ? -mx : d);
}

/* The server's heading at client time tau: replay every command in effect
   from the anchor, each turning toward its angle at full rate. */
static float eb_heading_at(float tau) {
  float a = eb.anchor_a, t = eb.anchor_t;
  const eb_cmd* cmd = NULL;
  int i = 0;
  for (; i < eb.log_n && eb.log[i].te <= t; i++) cmd = &eb.log[i];
  for (; i < eb.log_n && eb.log[i].te < tau; i++) {
    if (cmd) a = eb_turn(a, cmd->w, cmd->r, eb.log[i].te - t);
    t = eb.log[i].te;
    cmd = &eb.log[i];
  }
  return cmd ? eb_turn(a, cmd->w, cmd->r, tau - t) : a;
}

/* A heading from the server, seen at client time now (it describes the
   server about rtt/2 earlier): measure the model's error, then re-anchor. */
static void eb_sample(float v, float now) {
  float tau = now - eb.rtt / 2.0f;
  if (eb.anchored) {
    float res = fabsf(eb_norm(v - eb_heading_at(tau)));
    eb.res_max = fmaxf(res, eb.res_max * 0.92f);
    if (res > EB_SNAP) eb.log_n = 0; /* the model diverged: drop its turns */
  }
  eb.anchored = true;
  eb.anchor_t = tau;
  eb.anchor_a = v;
}

static void eb_log_drop_first(void) {
  memmove(eb.log, eb.log + 1, sizeof(eb_cmd) * (size_t)(eb.log_n - 1));
  eb.log_n--;
}

/* One send tick (about every 33 ms). Returns the 0-250 byte to send. */
static int eb_tick(bool aiming, float target, const snake* s, float now,
                   float rtt, float mamu) {
  eb.rtt = rtt;
  if (!eb.anchored) {
    eb_sample(s->ang, now);
    eb.seen = true;
    eb.last_seen = s->ang;
  }
  float dt = eb.last_t != 0.0f ? now - eb.last_t : 33.0f;
  eb.last_t = now;
  if (dt > 200.0f) dt = 200.0f;
  float te = now + rtt / 2.0f; /* when this byte reaches the server */
  float r = mamu * s->scang * s->spang / 8.0f; /* rad per ms */
  if (r < 1e-6f) r = 1e-6f;
  float est = eb_heading_at(te); /* the server heading when it lands */
  if (!aiming) {                 /* finger on the head: keep the heading */
    if (!eb.holding) {
      eb.holding = true;
      eb.hold = est;
    }
    target = eb.hold;
  } else
    eb.holding = false;
  float d = eb_norm(target - est);
  float k = d / (r * dt * EB_BAND);
  if (k > 1.0f)
    k = 1.0f;
  else if (k < -1.0f)
    k = -1.0f;
  eb.err += (1.0f + k) / 2.0f;
  bool plus = eb.err >= 1.0f;
  if (plus) eb.err -= 1.0f;
  float eps = EB_EPS + fabsf(k) * EB_TURN_MARGIN +
              fminf(EB_EPS_MAX, 1.3f * eb.res_max * 251.0f / PI2);
  float base = 251.0f * fmodf(fmodf(est + PI, PI2) + PI2, PI2) / PI2;
  int b = (int)floorf((plus ? base - eps : base + eps) + 0.5f);
  b = ((b % 251) + 251) % 251;
  if (eb.log_n == EB_LOG_MAX) eb_log_drop_first();
  eb.log[eb.log_n++] = (eb_cmd){te, b * PI2 / 251.0f, r};
  while (eb.log_n > 1 && eb.log[1].te < now - 2000.0f) eb_log_drop_first();
  return b;
}

void eyes_back_heading(float ang, float now) {
  if (!eb.on || !eb.anchored) return;
  if (eb.seen && ang == eb.last_seen) return;
  eb.seen = true;
  eb.last_seen = ang;
  eb_sample(ang, now);
}


static snake S;
__attribute__((export_name("set_on"))) void set_on(int v) { eb.on = v != 0; eb_reset(); }
__attribute__((export_name("tick")))
int tick(int aiming, double target, double ang, double scang, double spang, double now, double rtt, double mamu) {
  S.ang = (REAL)ang; S.scang = (REAL)scang; S.spang = (REAL)spang;
  return eb_tick(aiming != 0, (REAL)target, &S, (REAL)now, (REAL)rtt, (REAL)mamu);
}
__attribute__((export_name("report"))) void report(double ang, double now) { eyes_back_heading((REAL)ang, (REAL)now); }
