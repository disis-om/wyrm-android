#include "server.h"

#include <stdio.h>
#include <string.h>

#include "../user.h"
#include "arena_trace.h"
#include "callback.h"

void arena_send(struct mg_connection* c, const void* data, size_t len) {
  /* A match can end inside the frame that reads the controls: the poll which
     delivers the close runs after `input`. Sending to an arena that has already
     hung up is what turns a finished match into a crash, so the check lives
     here rather than being remembered at every call site. */
  if (!c || !data || len == 0) return;
  arena_trace_out(((const uint8_t*)data)[0], len);
  mg_ws_send(c, data, len, WEBSOCKET_OP_BINARY);
}

/* When the last dials went out, for drop reports: about 30 connects a minute
   to one arena gets the IP reset, so the count says whether that was likely.
   A ring, oldest overwritten; 64 is more than a minute can honestly hold. */
#define CONNECT_RING 64
static uint64_t connect_ring[CONNECT_RING];
static int connect_ring_next = 0;

int server_connects_last_minute(void) {
  uint64_t now = SDL_GetTicks();
  int count = 0;
  for (int i = 0; i < CONNECT_RING; i++) {
    uint64_t at = connect_ring[i];
    if (at && now - at <= 60000) count++;
  }
  return count;
}

void server_init(tenv* env) {
  tuser_data* usr = env->usr;
  game_data* gdata = &usr->gdata;
  user_settings* usrs = &usr->usrs;
  mg_log_set(MG_LL_NONE);
  mg_mgr_init(&gdata->network_manager);
}

bool server_connect(tenv* env) {
  tuser_data* usr = env->usr;
  game_data* gdata = &usr->gdata;
  user_settings* usrs = &usr->usrs;

  /*
   * A second request must never replace a live pointer. The old socket would
   * keep running inside Mongoose but every event from it would be ignored as
   * stale.
   *
   * This used to return without saying so, and the caller had already put the
   * game into CONNECTING — so the player watched a loading screen with no
   * socket behind it at all, against a timeout measured from the *previous*
   * attempt's stamp, which had already expired. That is the whole of "fresh
   * app start works, joining again does not": a fresh start has no old socket
   * to collide with, and every join after a match does.
   *
   * Now it says no, and the caller waits for the old socket to finish rather
   * than pretending it dialled.
   */
  if (gdata->connection) {
    SDL_Log("Wyrm arena: '%s' still has a socket closing — waiting for it",
            usrs->ipv4);
    return false;
  }

  /*
   * Slither.txt: `new WebSocket("ws://"+ip+":"+port+"/slither")`.
   *
   * `wss://` on this fleet was closing with `nothing was sent or received`
   * before a protocol byte — Play never spawned. Plain `ws://` is what the
   * original client dials.
   */
  char url[256] = {};
  snprintf(url, sizeof(url), "ws://%s/slither", usrs->ipv4);

  /* Cleared first, so that nothing in the window below is holding the last
     arena's socket. Mongoose raises MG_EV_OPEN from inside the call, and the
     callback decides what to ignore by comparing against this. */
  gdata->closed = false;
  /* One record per attempt. A retry that inherited the previous attempt's
     counts would report a send gap that spans the gap between sockets. */
  arena_trace_reset();
  /* The silence watchdog counts from here. Left at whatever the last match
     ended on, a fresh socket would be judged against a clock that had already
     run out and the join would be killed before the arena got a word in. */
  gdata->last_packet_ms = SDL_GetTicks();
  /* This attempt's own clock: what it is allowed to spend, and what the next
     attempt is scheduled off. */
  gdata->attempt_started_ms = gdata->last_packet_ms;
  /* Deliberately not cleared by `game_data_reset`: it paces the next entry
     against this one, so it has to outlive the world it belongs to. */
  gdata->last_connect_ms = gdata->last_packet_ms;
  gdata->persona_tested = false;
  connect_ring[connect_ring_next] = gdata->last_connect_ms ? gdata->last_connect_ms : 1;
  connect_ring_next = (connect_ring_next + 1) % CONNECT_RING;
  /* A browser keeps Host equal to the arena URL and sends the page origin.
     Mongoose already writes Host; adding a second slither.com Host and using
     slither.com (rather than slither.io) made some live arenas reject the HTTP
     upgrade before they could send challenge 0x36. */
  gdata->connection =
    mg_ws_connect(&gdata->network_manager, url, server_callback, env,
                  "Origin: https://slither.io\r\n");
  if (!gdata->connection) {
    SDL_Log("Wyrm arena: could not open a socket to '%s'", usrs->ipv4);
    gdata->closed_by_us = false;
    gdata->closed = true;
    return true;
  }

  /* Two immediate polls, from Vlither. A TLS handshake needs poll cycles
     before the game loop's own first poll comes round, and the connect
     otherwise spends a frame doing nothing. */
#ifdef VLITHER_ANDROID
  mg_mgr_poll(&gdata->network_manager, 5);
  mg_mgr_poll(&gdata->network_manager, 5);
#endif
  return true;
}

void server_poll(tenv* env) {
  tuser_data* usr = env->usr;
  game_data* gdata = &usr->gdata;

#ifdef VLITHER_ANDROID
  /* Phase 3 G (OM, 2026-10-01). Vlither blocked 5 ms here on every frame; its
     reason was TLS, but the arena socket is plain `ws://` now (see
     server_connect), so there is no record to assemble. While the socket is
     still connecting, upgrading or answering the challenge (until the arena's
     'a') the 5 ms stays exactly as it was, so entry is untouched; once 'a'
     has come the poll only drains what is already there (0 ms), so a
     frame that finds the socket empty no longer loses up to 5 ms, and ping
     (counted in frame time) stops carrying that wait. No packet, timing or
     keepalive changed. Rollback: set WYRM_NET_POLL_OPEN_MS to 5. */
#ifndef WYRM_NET_POLL_OPEN_MS
#define WYRM_NET_POLL_OPEN_MS 0
#endif
  struct mg_connection* arena = gdata->connection;
  int wait_ms = (arena && arena->is_websocket && gdata->arena_ready &&
                 !gdata->closed)
                    ? WYRM_NET_POLL_OPEN_MS : 5;
  Uint64 started = SDL_GetTicksNS();
  mg_mgr_poll(&gdata->network_manager, wait_ms);
  Uint64 ended = SDL_GetTicksNS();

  /* Measurement for OM's before/after check: every 10 s while a WebSocket is
     open, how long the poll took and how far apart the frames were. */
  static Uint64 window_start, last_call, poll_sum, poll_max, gap_sum, gap_max;
  static unsigned frames;
  if (arena && arena->is_websocket) {
    if (!window_start) window_start = started;
    Uint64 took = ended - started;
    poll_sum += took;
    if (took > poll_max) poll_max = took;
    if (last_call) {
      Uint64 gap = started - last_call;
      gap_sum += gap;
      if (gap > gap_max) gap_max = gap;
    }
    frames++;
    if (ended - window_start >= 10000000000ULL && frames > 1) {
      SDL_Log("Wyrm net poll: wait %d ms, poll avg %.2f ms max %.2f ms, "
              "frame avg %.2f ms max %.2f ms over %u frames",
              wait_ms, poll_sum / 1e6 / frames, poll_max / 1e6,
              gap_sum / 1e6 / (frames - 1), gap_max / 1e6, frames);
      window_start = ended;
      poll_sum = poll_max = gap_sum = gap_max = 0;
      frames = 0;
    }
    last_call = started;
  } else {
    window_start = last_call = 0;
    poll_sum = poll_max = gap_sum = gap_max = 0;
    frames = 0;
  }
#else
  mg_mgr_poll(&gdata->network_manager, 0);
#endif
}

void server_destroy(tenv* env) {
  tuser_data* usr = env->usr;
  game_data* gdata = &usr->gdata;

  mg_mgr_free(&gdata->network_manager);
}

bool server_address_is_valid(const char* address) {
  if (!address || !address[0] || strlen(address) > MAX_IPV4_LEN) return false;
  unsigned int a, b, c, d, port;
  char tail = 0;
  int matched = sscanf(address, "%u.%u.%u.%u:%u%c", &a, &b, &c, &d, &port,
                       &tail);
  return matched == 5 && a <= 255 && b <= 255 && c <= 255 && d <= 255 &&
         port > 0 && port <= 65535;
}
