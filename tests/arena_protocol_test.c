#include "../app/src/network/arena_protocol.h"
#include <assert.h>
#include <stdio.h>
#ifdef _WIN32
#include <windows.h>
#endif

static void timing(void) {
  assert(ARENA_AIM_MS==33 && ARENA_TURN_MS==50 && ARENA_BOOST_MS==50);
  assert(ARENA_PING_MS==250 && ARENA_LAG_MS==750 && ARENA_RETRY_MS==3333);
  assert(arena_death_step(1,1600,5)==1);
  assert(fabsf(arena_death_step(1,1601,1)-.996f)<1e-6f);
  float opacity=1;
  for(int i=0;i<50;i++) opacity=arena_death_step(opacity,1601,5);
  assert(opacity<1e-6f);
  assert(arena_lag_step(.2f,true)==.2f);
  assert(fabsf(arena_lag_step(1,true)-.85f)<1e-6f);
  assert(fabsf(arena_lag_step(.5f,false)-.55f)<1e-6f);
  assert(arena_lag_step(.99f,false)==1);
}

static void turns(void) {
  arena_turn t;
  uint8_t own[]={'d',64,128,18};
  assert(arena_decode_turn(own,sizeof own,14,&t));
  assert(t.own && t.dir==1 && t.speed==1 && fabsf(t.ang-1.5707963f)<1e-6f);
  uint8_t named[]={'e',0x12,0x34,64,128,36};
  assert(arena_decode_turn(named,sizeof named,6,&t));
  assert(!t.own && t.id==0x1234 && t.dir==1 && t.speed==2);
  uint8_t v3[]={'e',0x12,0x34,0x40,0,0x80,0,18};
  assert(arena_decode_turn(v3,sizeof v3,3,&t) && t.speed==1);
  uint8_t v2[]={'e',0x12,0x34,'1',0x40,0,0,0x80,0,0,3,232};
  assert(arena_decode_turn(v2,sizeof v2,2,&t) && t.dir==1 && t.speed==1);
  uint8_t speed[]={'3',36};
  assert(arena_decode_turn(speed,sizeof speed,14,&t) && t.own && t.speed==2);
  assert(!arena_decode_turn(speed,sizeof speed,13,&t));
  assert(!arena_decode_turn(own,1,14,&t));
}

static void maps(void) {
  uint8_t out[256]={0};
  uint8_t bits[]={64};
  arena_reader r={bits,1,0,true};
  assert(arena_map_layer(&r,out,4,4,false,true,false));
  assert(out[15]==255 && out[14]==0 && out[0]==0);
  r=(arena_reader){bits,1,0,true};
  assert(arena_map_layer(&r,out,4,4,false,true,true) && out[15]==0);
  r=(arena_reader){bits,1,0,true};
  assert(arena_map_layer(&r,out,4,4,true,false,false) && out[0]==255);
  memset(out,0,sizeof out);
  uint8_t run[]={255,1,64};
  r=(arena_reader){run,3,0,true};
  assert(arena_map_layer(&r,out,16,16,false,true,false));
  assert(out[129]==255 && out[128]==0);
  memset(out,0,sizeof out);
  r=(arena_reader){run,3,0,true};
  assert(arena_map_layer(&r,out,16,16,false,false,false));
  assert(out[122]==255 && out[129]==0);
  r=(arena_reader){run,1,0,true};
  assert(!arena_map_layer(&r,out,16,16,false,true,false));
  uint8_t layers[]={64,64};
  r=(arena_reader){layers,2,0,true};
  assert(arena_map_layer(&r,out,2,2,false,true,false) && r.pos==1);
  assert(arena_map_layer(&r,out,2,2,false,true,false) && r.pos==2);
}

static void packets(void) {
  uint8_t config[32]={'a',0,100,0,0,100,0,128};
  assert(!arena_packet_valid(config,22,14));
  assert(arena_packet_valid(config,23,14));
  config[23]=15; config[24]=42;
  assert(arena_packet_valid(config,25,14));
  assert(!arena_packet_valid(config,26,14));
  assert(arena_packet_valid(config,27,14));
  assert(!arena_packet_valid(config,28,14));
  assert(arena_packet_valid(config,32,14));
  uint8_t spawn[40]={'s'};
  assert(arena_packet_valid(spawn,29,10));
  assert(arena_packet_valid(spawn,30,11));
  assert(arena_packet_valid(spawn,31,12));
  assert(!arena_packet_valid(spawn,30,12));
  spawn[22]=255;
  assert(!arena_packet_valid(spawn,sizeof spawn,15));
  uint8_t food[12]={'f'};
  assert(arena_packet_valid(food,7,4));
  assert(!arena_packet_valid(food,8,4));
  assert(arena_packet_valid(food,12,2));
  for(size_t n=4;n<=7;n++) assert(arena_packet_valid(food,n,14));
  uint8_t killed[]={'k',0,1,0,2,3};
  assert(arena_packet_valid(killed,6,15));
  assert(!arena_packet_valid(killed,5,15));
  uint8_t prey[20]={'y'};
  assert(arena_packet_valid(prey,20,15));
  assert(!arena_packet_valid(prey,19,15));
  uint8_t point[10]={'+'};
  assert(arena_packet_valid(point,10,15));
  assert(!arena_packet_valid(point,9,15));
}

static void bounded_inputs(void) {
  uint8_t data[96];
#ifdef _WIN32
  SYSTEM_INFO info;
  GetSystemInfo(&info);
  uint8_t* pages=VirtualAlloc(NULL,2*info.dwPageSize,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);
  assert(pages);
  DWORD previous;
  assert(VirtualProtect(pages+info.dwPageSize,info.dwPageSize,PAGE_NOACCESS,&previous));
#endif
  uint32_t seed=1;
  for(int trial=0;trial<100000;trial++) {
    for(size_t i=0;i<sizeof data;i++) {
      seed=seed*1664525u+1013904223u;
      data[i]=(uint8_t)(seed>>24);
    }
    size_t n=(seed>>16)%sizeof data;
    int version=seed%17;
    const uint8_t* packet=data;
#ifdef _WIN32
    packet=pages+info.dwPageSize-n;
    memcpy((void*)packet,data,n);
#endif
    (void)arena_packet_valid(packet,n,version);
    arena_turn turn;
    (void)arena_decode_turn(packet,n,version,&turn);
    uint8_t out[256]={0};
    arena_reader r={packet,n,0,true};
    (void)arena_map_layer(&r,out,16,16,seed&1,seed&2,seed&4);
    assert(r.pos<=r.size);
  }
#ifdef _WIN32
  assert(VirtualFree(pages,0,MEM_RELEASE));
#endif
}

int main(void) {
  timing(); turns(); maps(); packets(); bounded_inputs();
  puts("PASS: timings, v2/v3/v6/v14 turns, minimaps, packet boundaries, 100000 bounded inputs");
  return 0;
}
