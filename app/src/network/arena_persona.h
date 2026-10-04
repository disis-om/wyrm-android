#ifndef ARENA_PERSONA_H
#define ARENA_PERSONA_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/*
 * Which slither client Wyrm claims to be on the wire: the web client only
 * (`game1107241958.js`, version 291), byte for byte as Vlither sends it.
 *
 * Wyrm used to carry a second identity, the Android AIR client (version 294,
 * its own fingerprint, a CRC32 challenge answer and a settings `c` packet),
 * and could switch to it. It was removed on 2026-10-04 (OM): every arena
 * report showed the web identity, and on `148.113.20.151` the AIR identity was
 * hung up on before the arena said anything. The removed code is in git
 * history (Android 6.4.2, iOS build 95).
 */
typedef struct arena_persona {
  /* Goes in every arena log line. */
  const char* name;
  /* Bytes 2..3 of the join packet. */
  uint16_t version;
  /* Bytes 4..23 of the join packet. */
  uint8_t fingerprint[20];
} arena_persona;

enum {
  ARENA_PERSONA_WEB = 0,
  NUM_ARENA_PERSONAS = 1
};

/** The web client, whatever `index` says: a settings file written by a build
    that could still switch to AIR (index 1) joins as the web client too. */
const arena_persona* arena_persona_get(int index);

#endif
