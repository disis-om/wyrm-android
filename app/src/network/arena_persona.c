#include "arena_persona.h"

/* The web client's identity: `client_version` and `cpw` in game1107241958.js,
   the same as Vlither's CLIENT_VERSION and `cwa`. */
static const arena_persona web = {.name = "web",
                                  .version = 291,
                                  .fingerprint = {54, 206, 204, 169, 97, 178,
                                                  74, 136, 124, 117, 14, 210,
                                                  106, 236, 8, 208, 136, 213,
                                                  140, 111}};

const arena_persona* arena_persona_get(int index) {
  (void)index;
  return &web;
}
