#ifndef FD_RENDERER_H
#define FD_RENDERER_H

#include <thermite.h>

typedef struct fd_instance {
  vec3s circle;
  vec4s color;
  float flicker;
  float shape;
} fd_instance;

typedef struct fd_renderer {
  VkPipeline pipelines[3];
  tdbuffer* instance_buffer;
  tdbuffer* p_instance_buffer;
  fd_instance* instances;
  fd_instance* p_instances;
  /* Battledome letters and dome (OM, 2026-10-24): drawn last, with plain
     alpha blending, so overlapping stroke dots stay one even colour. */
  /* Drawn first, with the same plain alpha blending: shadows under food
     (the Slither app food texture, OM 2026-10-25). */
  tdbuffer* u_instance_buffer;
  fd_instance* u_instances;
  int max_u_instances;
  int num_u_instances;
  tdbuffer* t_instance_buffer;
  fd_instance* t_instances;
  int max_t_instances;
  int num_t_instances;
  int capacity, p_capacity, t_capacity, u_capacity;
  int max_instances;
  int num_instances;
  int num_p_instances;
  int max_p_instances;
  int pipeline_idx;
} fd_renderer;

fd_renderer* fd_renderer_create(tcontext* ctx, int max_instance, int max_p_instances,
                                VkPipelineLayout layout, VkRenderPass pass);
void fd_renderer_push(fd_renderer* r, const fd_instance* instance);
void fd_renderer_push_p(fd_renderer* r, const fd_instance* instance);
void fd_renderer_push_t(fd_renderer* r, const fd_instance* instance);
void fd_renderer_push_u(fd_renderer* r, const fd_instance* instance);
void fd_renderer_render(fd_renderer* r, tcontext* ctx);
void fd_renderer_destroy(fd_renderer* r, tcontext* ctx);

#endif
