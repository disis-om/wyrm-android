
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
typedef __SIZE_TYPE__ size_t_;
static void* memset(void* d, int c, unsigned long n) { unsigned char* p = d; while (n--) *p++ = (unsigned char)c; return d; }
static void* memmove(void* d, const void* s, unsigned long n) {
  unsigned char* a = d; const unsigned char* b = s;
  if (a < b) while (n--) *a++ = *b++; else { a += n; b += n; while (n--) *--a = *--b; }
  return d; }

#define PI2 6.283185307179586
#define PI 3.141592653589793
static double fmod(double x, double y) { return x - y * __builtin_trunc(x / y); }
static double fabs(double x) { return __builtin_fabs(x); }
static double fmin(double a, double b) { return a < b ? a : b; }
static double fmax(double a, double b) { return a > b ? a : b; }
static double floor(double x) { return __builtin_floor(x); }
typedef struct { double ang, scang, spang; int id; } snake;
#define REAL double
#define EB_EPS 8.0
#define EB_TURN_MARGIN 4.0
#define EB_EPS_MAX 22.0
#define EB_SNAP 0.55
#define EB_BAND 2.5
#define EB_LOG_MAX 160

typedef struct eb_cmd {
  double te; /* when it reaches the server, client clock (ms) */
  double w;  /* the angle sent (rad) */
  double r;  /* the snake's turn rate then (rad/ms) */
} eb_cmd;

static struct {
  bool on;
  double err;
  double last_t;
  bool anchored;
  double anchor_t;
  double anchor_a;
  eb_cmd log[EB_LOG_MAX];
  int log_n;
  double res_max;
  bool holding;
  double hold;
  bool seen;
  double last_seen;
  const void* conn; /* the socket and snake the model belongs to */
  int snake_id;
  bool sent;
  double sent_ang; /* the last byte sent, in radians: where the eyes look */
  double rtt;
} eb = {.rtt = 100.0, .snake_id = -1};

static double eb_norm(double a) {
  a = fmod(a, PI2);
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
  eb.rtt = 100.0;
  eb.snake_id = -1;
}

static double eb_turn(double a, double w, double r, double ms) {
  double d = eb_norm(w - a), mx = r * ms;
  return a + (d > mx ? mx : d < -mx ? -mx : d);
}

/* The server's heading at client time tau: replay every command in effect
   from the anchor, each turning toward its angle at full rate. */
static double eb_heading_at(double tau) {
  double a = eb.anchor_a, t = eb.anchor_t;
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
static void eb_sample(double v, double now) {
  double tau = now - eb.rtt / 2.0;
  if (eb.anchored) {
    double res = fabs(eb_norm(v - eb_heading_at(tau)));
    eb.res_max = fmax(res, eb.res_max * 0.92);
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
static int eb_tick(bool aiming, double target, const snake* s, double now,
                   double rtt, double mamu) {
  eb.rtt = rtt;
  if (!eb.anchored) {
    eb_sample(s->ang, now);
    eb.seen = true;
    eb.last_seen = s->ang;
  }
  double dt = eb.last_t != 0.0 ? now - eb.last_t : 33.0;
  eb.last_t = now;
  if (dt > 200.0) dt = 200.0;
  double te = now + rtt / 2.0; /* when this byte reaches the server */
  double r = mamu * s->scang * s->spang / 8.0; /* rad per ms */
  if (r < 1e-6) r = 1e-6;
  double est = eb_heading_at(te); /* the server heading when it lands */
  if (!aiming) {                 /* finger on the head: keep the heading */
    if (!eb.holding) {
      eb.holding = true;
      eb.hold = est;
    }
    target = eb.hold;
  } else
    eb.holding = false;
  double d = eb_norm(target - est);
  double k = d / (r * dt * EB_BAND);
  if (k > 1.0)
    k = 1.0;
  else if (k < -1.0)
    k = -1.0;
  eb.err += (1.0 + k) / 2.0;
  bool plus = eb.err >= 1.0;
  if (plus) eb.err -= 1.0;
  double eps = EB_EPS + fabs(k) * EB_TURN_MARGIN +
              fmin(EB_EPS_MAX, 1.3 * eb.res_max * 251.0 / PI2);
  double base = 251.0 * fmod(fmod(est + PI, PI2) + PI2, PI2) / PI2;
  int b = (int)floor((plus ? base - eps : base + eps) + 0.5);
  b = ((b % 251) + 251) % 251;
  if (eb.log_n == EB_LOG_MAX) eb_log_drop_first();
  eb.log[eb.log_n++] = (eb_cmd){te, b * PI2 / 251.0, r};
  while (eb.log_n > 1 && eb.log[1].te < now - 2000.0) eb_log_drop_first();
  return b;
}

void eyes_back_heading(double ang, double now) {
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
