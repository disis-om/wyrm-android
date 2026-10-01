#include "lobby.h"

#include <stdio.h>
#include <string.h>

#include "../constants.h"
#include "../game/game_data.h"
#include "../game/input.h"
#include "../game/oef.h"
#include "../network/server.h"
#include "../platform/android_home.h"
#include "../user.h"
#include "ui_theme.h"

#ifdef VLITHER_ANDROID
#include <SDL3/SDL.h>
#endif

/* The native Ready Room and its Quick settings are gone (OM, 2026-10-01):
   Compose and SwiftUI own the lobby. Only Play's door and the near-black
   backdrop under the Compose lobby are left here. */
static ImU32 lobby_rgba(unsigned char r, unsigned char g, unsigned char b,
                        unsigned char a) {
  return (ImU32)r | ((ImU32)g << 8) | ((ImU32)b << 16) | ((ImU32)a << 24);
}

static void lobby_backdrop(tcontext* ctx) {
  ImGuiViewport* vp = igGetMainViewport();
  ImDrawList* dl = igGetBackgroundDrawList(vp);
  ImVec2 p0 = vp->Pos;
  ImVec2 p1 = {vp->Pos.x + vp->Size.x, vp->Pos.y + vp->Size.y};
  ImDrawList_AddRectFilled(dl, p0, p1, lobby_rgba(8, 8, 8, 255), 0.0f, 0);
  (void)ctx;
}

void ui_lobby_play(tenv* env) {
  tuser_data* usr = env->usr;
  game_data* gdata = &usr->gdata;

  save_user_settings(&usr->usrs);
  /* Slither only calls `connect()` once `dead_mtm == -1`. A leftover death
     watch must not yank this join back to the lobby. */
  android_home_reset_death();
  gdata->stay_in_lobby = true;
  gdata->leaving = false;
  /* Same door as every other join: 3333ms since the last dial, and wait if
     the previous socket is still closing. Lobby Play used to skip both and
     dial on the spot, which is the enter-exit loop after a drop. */
  arena_request_join(env, ARENA_CONNECT_COOLDOWN_MS);
}

void ui_lobby(tenv* env) {
  tuser_data* usr = env->usr;
  game_data* gdata = &usr->gdata;
  usr->r->global.bg_opacity = 0;
  usr->r->global.bd_opacity = 0;
  usr->r->global.minimap_opacity = 0;
  gdata->stay_in_lobby = true;

  /* Nothing is left to close: no socket, no close waiting to be read, no death
     watch. Without this the state would stay non-DISCONNECTED for good, the
     port would never read free, and every later Play would be declined. */
  if (!gdata->connection && gdata->conn != DISCONNECTED && !gdata->closed &&
      !android_home_death_pending())
    gdata->closed = true;

  if (gdata->connection || gdata->conn != DISCONNECTED) {
    if (gdata->data.victory_message_requested && gdata->connection) {
      time_step(env);
      input_team_protected(env);
    }
    server_poll(env);
    if (gdata->data.want_close_socket) {
      gdata->leaving = true;
      game_close_connection(gdata, "victory exchange finished");
      gdata->data.want_close_socket = false;
    }
    if (gdata->closed) {
      game_data_reset(env);
      gdata->conn = DISCONNECTED;
      gdata->closed = false;
      gdata->curr_screen = LOBBY;
    }
  }

  /* Compose draws the 6.1.1 lobby. Native only keeps a black clear so the
     glass sits on the same near-black as Home, with no extra orbs. */
  lobby_backdrop(env->ctx);
}
