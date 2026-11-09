#include "fd_renderer.h"

#include <string.h>
#include "instance_storage.h"

/* Battledome letter strokes and dome dots, per frame. */
#define FD_MAX_T_INSTANCES 65536

fd_renderer* fd_renderer_create(tcontext* ctx, int max_instances, int max_p_instances,
                                VkPipelineLayout layout, VkRenderPass pass) {
  fd_renderer* r = malloc(sizeof(fd_renderer));
  r->pipeline_idx = 0;

  VkShaderModule vertex_shader =
      tcontext_create_shader(ctx, "app/res/shaders/bin/fdv.spv");
  VkShaderModule fragment_shader =
      tcontext_create_shader(ctx, "app/res/shaders/bin/fdf.spv");

  vkCreateGraphicsPipelines(
      ctx->device, VK_NULL_HANDLE, 1,
      &(VkGraphicsPipelineCreateInfo){
          .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
          .pNext = NULL,
          .flags = 0,
          .stageCount = 2,
          .pStages =
              (VkPipelineShaderStageCreateInfo[]){
                  {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                   .pNext = NULL,
                   .flags = 0,
                   .stage = VK_SHADER_STAGE_VERTEX_BIT,
                   .module = vertex_shader,
                   .pName = "main",
                   .pSpecializationInfo = NULL},
                  {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                   .pNext = NULL,
                   .flags = 0,
                   .stage = VK_SHADER_STAGE_FRAGMENT_BIT,
                   .module = fragment_shader,
                   .pName = "main",
                   .pSpecializationInfo = NULL}},
          .pVertexInputState =
              &(VkPipelineVertexInputStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .vertexBindingDescriptionCount = 2,
                  .pVertexBindingDescriptions =
                      (VkVertexInputBindingDescription[]){
                          {.binding = 0,
                           .stride = 2 * sizeof(float),
                           .inputRate = VK_VERTEX_INPUT_RATE_VERTEX},
                          {.binding = 1,
                           .stride = sizeof(fd_instance),
                           .inputRate = VK_VERTEX_INPUT_RATE_INSTANCE},
                      },
                  .vertexAttributeDescriptionCount = 5,
                  .pVertexAttributeDescriptions =
                      (VkVertexInputAttributeDescription[]){
                          {.location = 0,
                           .binding = 0,
                           .format = VK_FORMAT_R32G32_SFLOAT,
                           .offset = 0},
                          {.location = 1,
                           .binding = 1,
                           .format = VK_FORMAT_R32G32B32_SFLOAT,
                           .offset = 0},
                          {.location = 2,
                           .binding = 1,
                           .format = VK_FORMAT_R32G32B32A32_SFLOAT,
                           .offset = offsetof(fd_instance, color)},
                          {.location = 3,
                           .binding = 1,
                           .format = VK_FORMAT_R32_SFLOAT,
                           .offset = offsetof(fd_instance, flicker)},
                          {.location = 4,
                           .binding = 1,
                           .format = VK_FORMAT_R32_SFLOAT,
                           .offset = offsetof(fd_instance, shape)}}},
          .pInputAssemblyState =
              &(VkPipelineInputAssemblyStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP,
                  .primitiveRestartEnable = VK_FALSE},
          .pTessellationState = NULL,
          .pViewportState =
              &(VkPipelineViewportStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .viewportCount = 1,
                  .scissorCount = 1},
          .pRasterizationState =
              &(VkPipelineRasterizationStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .depthClampEnable = VK_FALSE,
                  .rasterizerDiscardEnable = VK_FALSE,
                  .polygonMode = VK_POLYGON_MODE_FILL,
                  .cullMode = VK_CULL_MODE_NONE,
                  .frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE,
                  .depthBiasEnable = VK_FALSE,
                  .depthBiasConstantFactor = 0.0f,
                  .depthBiasClamp = 0.0f,
                  .depthBiasSlopeFactor = 0.0f,
                  .lineWidth = 1.0f},
          .pMultisampleState =
              &(VkPipelineMultisampleStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT,
                  .sampleShadingEnable = VK_FALSE,
                  .minSampleShading = 0.0f,
                  .pSampleMask = NULL,
                  .alphaToCoverageEnable = VK_FALSE,
                  .alphaToOneEnable = VK_FALSE},
          .pDepthStencilState =
              &(VkPipelineDepthStencilStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .depthTestEnable = VK_FALSE,
              },
          .pColorBlendState =
              &(VkPipelineColorBlendStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .logicOpEnable = VK_FALSE,
                  .logicOp = VK_LOGIC_OP_COPY,
                  .attachmentCount = 1,
                  .pAttachments =
                      &(VkPipelineColorBlendAttachmentState){
                          .blendEnable = VK_TRUE,
                          .srcColorBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA,
                          /* Food is solid (OM, 2026-10-26): normal blending, not
                             added light, so light floors keep it. */
                          .dstColorBlendFactor =
                              VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                          .colorBlendOp = VK_BLEND_OP_ADD,
                          .srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE,
                          .dstAlphaBlendFactor = VK_BLEND_FACTOR_ZERO,
                          .alphaBlendOp = VK_BLEND_OP_ADD,
                          .colorWriteMask = VK_COLOR_COMPONENT_R_BIT |
                                            VK_COLOR_COMPONENT_G_BIT |
                                            VK_COLOR_COMPONENT_B_BIT |
                                            VK_COLOR_COMPONENT_A_BIT},
                  .blendConstants = {0.0f, 0.0f, 0.0f, 0.0f}},
          .pDynamicState =
              &(VkPipelineDynamicStateCreateInfo){
                  .sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .dynamicStateCount = 2,
                  .pDynamicStates =
                      (VkDynamicState[]){VK_DYNAMIC_STATE_VIEWPORT,
                                         VK_DYNAMIC_STATE_SCISSOR}},
          .layout = layout,
          .renderPass = pass,
          .subpass = 0,
          .basePipelineHandle = VK_NULL_HANDLE,
          .basePipelineIndex = 0},
      NULL, &r->pipelines[0]);

  /* The same food shaders with plain alpha blending, for the Battledome
     letters (fd_renderer_push_t): food adds light, so two overlapping dots
     of a stroke would show a brighter seam. */
  vkCreateGraphicsPipelines(
      ctx->device, VK_NULL_HANDLE, 1,
      &(VkGraphicsPipelineCreateInfo){
          .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
          .pNext = NULL,
          .flags = 0,
          .stageCount = 2,
          .pStages =
              (VkPipelineShaderStageCreateInfo[]){
                  {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                   .pNext = NULL,
                   .flags = 0,
                   .stage = VK_SHADER_STAGE_VERTEX_BIT,
                   .module = vertex_shader,
                   .pName = "main",
                   .pSpecializationInfo = NULL},
                  {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                   .pNext = NULL,
                   .flags = 0,
                   .stage = VK_SHADER_STAGE_FRAGMENT_BIT,
                   .module = fragment_shader,
                   .pName = "main",
                   .pSpecializationInfo = NULL}},
          .pVertexInputState =
              &(VkPipelineVertexInputStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .vertexBindingDescriptionCount = 2,
                  .pVertexBindingDescriptions =
                      (VkVertexInputBindingDescription[]){
                          {.binding = 0,
                           .stride = 2 * sizeof(float),
                           .inputRate = VK_VERTEX_INPUT_RATE_VERTEX},
                          {.binding = 1,
                           .stride = sizeof(fd_instance),
                           .inputRate = VK_VERTEX_INPUT_RATE_INSTANCE},
                      },
                  .vertexAttributeDescriptionCount = 5,
                  .pVertexAttributeDescriptions =
                      (VkVertexInputAttributeDescription[]){
                          {.location = 0,
                           .binding = 0,
                           .format = VK_FORMAT_R32G32_SFLOAT,
                           .offset = 0},
                          {.location = 1,
                           .binding = 1,
                           .format = VK_FORMAT_R32G32B32_SFLOAT,
                           .offset = 0},
                          {.location = 2,
                           .binding = 1,
                           .format = VK_FORMAT_R32G32B32A32_SFLOAT,
                           .offset = offsetof(fd_instance, color)},
                          {.location = 3,
                           .binding = 1,
                           .format = VK_FORMAT_R32_SFLOAT,
                           .offset = offsetof(fd_instance, flicker)},
                          {.location = 4,
                           .binding = 1,
                           .format = VK_FORMAT_R32_SFLOAT,
                           .offset = offsetof(fd_instance, shape)}}},
          .pInputAssemblyState =
              &(VkPipelineInputAssemblyStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP,
                  .primitiveRestartEnable = VK_FALSE},
          .pTessellationState = NULL,
          .pViewportState =
              &(VkPipelineViewportStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .viewportCount = 1,
                  .scissorCount = 1},
          .pRasterizationState =
              &(VkPipelineRasterizationStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .depthClampEnable = VK_FALSE,
                  .rasterizerDiscardEnable = VK_FALSE,
                  .polygonMode = VK_POLYGON_MODE_FILL,
                  .cullMode = VK_CULL_MODE_NONE,
                  .frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE,
                  .depthBiasEnable = VK_FALSE,
                  .depthBiasConstantFactor = 0.0f,
                  .depthBiasClamp = 0.0f,
                  .depthBiasSlopeFactor = 0.0f,
                  .lineWidth = 1.0f},
          .pMultisampleState =
              &(VkPipelineMultisampleStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT,
                  .sampleShadingEnable = VK_FALSE,
                  .minSampleShading = 0.0f,
                  .pSampleMask = NULL,
                  .alphaToCoverageEnable = VK_FALSE,
                  .alphaToOneEnable = VK_FALSE},
          .pDepthStencilState =
              &(VkPipelineDepthStencilStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .depthTestEnable = VK_FALSE,
              },
          .pColorBlendState =
              &(VkPipelineColorBlendStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .logicOpEnable = VK_FALSE,
                  .logicOp = VK_LOGIC_OP_COPY,
                  .attachmentCount = 1,
                  .pAttachments =
                      &(VkPipelineColorBlendAttachmentState){
                          .blendEnable = VK_TRUE,
                          .srcColorBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA,
                          .dstColorBlendFactor =
                              VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                          .colorBlendOp = VK_BLEND_OP_ADD,
                          .srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE,
                          .dstAlphaBlendFactor = VK_BLEND_FACTOR_ZERO,
                          .alphaBlendOp = VK_BLEND_OP_ADD,
                          .colorWriteMask = VK_COLOR_COMPONENT_R_BIT |
                                            VK_COLOR_COMPONENT_G_BIT |
                                            VK_COLOR_COMPONENT_B_BIT |
                                            VK_COLOR_COMPONENT_A_BIT},
                  .blendConstants = {0.0f, 0.0f, 0.0f, 0.0f}},
          .pDynamicState =
              &(VkPipelineDynamicStateCreateInfo){
                  .sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .dynamicStateCount = 2,
                  .pDynamicStates =
                      (VkDynamicState[]){VK_DYNAMIC_STATE_VIEWPORT,
                                         VK_DYNAMIC_STATE_SCISSOR}},
          .layout = layout,
          .renderPass = pass,
          .subpass = 0,
          .basePipelineHandle = VK_NULL_HANDLE,
          .basePipelineIndex = 0},
      NULL, &r->pipelines[2]);

  vkDestroyShaderModule(ctx->device, fragment_shader, NULL);
  vkDestroyShaderModule(ctx->device, vertex_shader, NULL);

  vertex_shader = tcontext_create_shader(ctx, "app/res/shaders/bin/fdrv.spv");
  fragment_shader = tcontext_create_shader(ctx, "app/res/shaders/bin/fdrf.spv");

  vkCreateGraphicsPipelines(
      ctx->device, VK_NULL_HANDLE, 1,
      &(VkGraphicsPipelineCreateInfo){
          .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
          .pNext = NULL,
          .flags = 0,
          .stageCount = 2,
          .pStages =
              (VkPipelineShaderStageCreateInfo[]){
                  {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                   .pNext = NULL,
                   .flags = 0,
                   .stage = VK_SHADER_STAGE_VERTEX_BIT,
                   .module = vertex_shader,
                   .pName = "main",
                   .pSpecializationInfo = NULL},
                  {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                   .pNext = NULL,
                   .flags = 0,
                   .stage = VK_SHADER_STAGE_FRAGMENT_BIT,
                   .module = fragment_shader,
                   .pName = "main",
                   .pSpecializationInfo = NULL}},
          .pVertexInputState =
              &(VkPipelineVertexInputStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .vertexBindingDescriptionCount = 2,
                  .pVertexBindingDescriptions =
                      (VkVertexInputBindingDescription[]){
                          {.binding = 0,
                           .stride = 2 * sizeof(float),
                           .inputRate = VK_VERTEX_INPUT_RATE_VERTEX},
                          {.binding = 1,
                           .stride = sizeof(fd_instance),
                           .inputRate = VK_VERTEX_INPUT_RATE_INSTANCE},
                      },
                  .vertexAttributeDescriptionCount = 5,
                  .pVertexAttributeDescriptions =
                      (VkVertexInputAttributeDescription[]){
                          {.location = 0,
                           .binding = 0,
                           .format = VK_FORMAT_R32G32_SFLOAT,
                           .offset = 0},
                          {.location = 1,
                           .binding = 1,
                           .format = VK_FORMAT_R32G32B32_SFLOAT,
                           .offset = 0},
                          {.location = 2,
                           .binding = 1,
                           .format = VK_FORMAT_R32G32B32A32_SFLOAT,
                           .offset = offsetof(fd_instance, color)},
                          {.location = 3,
                           .binding = 1,
                           .format = VK_FORMAT_R32_SFLOAT,
                           .offset = offsetof(fd_instance, flicker)},
                          {.location = 4,
                           .binding = 1,
                           .format = VK_FORMAT_R32_SFLOAT,
                           .offset = offsetof(fd_instance, shape)}}},
          .pInputAssemblyState =
              &(VkPipelineInputAssemblyStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP,
                  .primitiveRestartEnable = VK_FALSE},
          .pTessellationState = NULL,
          .pViewportState =
              &(VkPipelineViewportStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .viewportCount = 1,
                  .scissorCount = 1},
          .pRasterizationState =
              &(VkPipelineRasterizationStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .depthClampEnable = VK_FALSE,
                  .rasterizerDiscardEnable = VK_FALSE,
                  .polygonMode = VK_POLYGON_MODE_FILL,
                  .cullMode = VK_CULL_MODE_NONE,
                  .frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE,
                  .depthBiasEnable = VK_FALSE,
                  .depthBiasConstantFactor = 0.0f,
                  .depthBiasClamp = 0.0f,
                  .depthBiasSlopeFactor = 0.0f,
                  .lineWidth = 1.0f},
          .pMultisampleState =
              &(VkPipelineMultisampleStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT,
                  .sampleShadingEnable = VK_FALSE,
                  .minSampleShading = 0.0f,
                  .pSampleMask = NULL,
                  .alphaToCoverageEnable = VK_FALSE,
                  .alphaToOneEnable = VK_FALSE},
          .pDepthStencilState =
              &(VkPipelineDepthStencilStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .depthTestEnable = VK_FALSE,
              },
          .pColorBlendState =
              &(VkPipelineColorBlendStateCreateInfo){
                  .sType =
                      VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .logicOpEnable = VK_FALSE,
                  .logicOp = VK_LOGIC_OP_COPY,
                  .attachmentCount = 1,
                  .pAttachments =
                      &(VkPipelineColorBlendAttachmentState){
                          .blendEnable = VK_TRUE,
                          .srcColorBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA,
                          .dstColorBlendFactor = VK_BLEND_FACTOR_ONE,
                          .colorBlendOp = VK_BLEND_OP_ADD,
                          .srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE,
                          .dstAlphaBlendFactor = VK_BLEND_FACTOR_ZERO,
                          .alphaBlendOp = VK_BLEND_OP_ADD,
                          .colorWriteMask = VK_COLOR_COMPONENT_R_BIT |
                                            VK_COLOR_COMPONENT_G_BIT |
                                            VK_COLOR_COMPONENT_B_BIT |
                                            VK_COLOR_COMPONENT_A_BIT},
                  .blendConstants = {0.0f, 0.0f, 0.0f, 0.0f}},
          .pDynamicState =
              &(VkPipelineDynamicStateCreateInfo){
                  .sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO,
                  .pNext = NULL,
                  .flags = 0,
                  .dynamicStateCount = 2,
                  .pDynamicStates =
                      (VkDynamicState[]){VK_DYNAMIC_STATE_VIEWPORT,
                                         VK_DYNAMIC_STATE_SCISSOR}},
          .layout = layout,
          .renderPass = pass,
          .subpass = 0,
          .basePipelineHandle = VK_NULL_HANDLE,
          .basePipelineIndex = 0},
      NULL, &r->pipelines[1]);

  vkDestroyShaderModule(ctx->device, fragment_shader, NULL);
  vkDestroyShaderModule(ctx->device, vertex_shader, NULL);

  r->instance_buffer = tdbuffer_create_stream(ctx,
      (VkDeviceSize)max_instances * sizeof(fd_instance), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
  r->instances = NULL;
  r->capacity = 0;
  r->max_instances = max_instances;
  r->num_instances = 0;

  r->p_instance_buffer = tdbuffer_create_stream(ctx,
      (VkDeviceSize)max_p_instances * sizeof(fd_instance), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
  r->p_instances = NULL;
  r->p_capacity = 0;
  r->max_p_instances = max_p_instances;
  r->num_p_instances = 0;

  r->max_t_instances = FD_MAX_T_INSTANCES;
  r->t_instance_buffer = tdbuffer_create_stream(ctx,
      (VkDeviceSize)r->max_t_instances * sizeof(fd_instance), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
  r->t_instances = NULL;
  r->t_capacity = 0;
  r->num_t_instances = 0;

  r->max_u_instances = FD_MAX_T_INSTANCES;
  r->u_instance_buffer = tdbuffer_create_stream(ctx,
      (VkDeviceSize)r->max_u_instances * sizeof(fd_instance), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
  r->u_instances = NULL;
  r->u_capacity = 0;
  r->num_u_instances = 0;

  return r;
}

void fd_renderer_push(fd_renderer* r, const fd_instance* instance) {
  if (r->num_instances >= r->max_instances) {
    return;
  }
  fd_instance* grown = instance_storage_reserve(r->instances,
      &r->capacity, r->max_instances, r->num_instances + 1, sizeof(fd_instance));
  if (!grown) return;
  r->instances = grown;
  r->instances[r->num_instances++] = *instance;
}

void fd_renderer_push_p(fd_renderer* r, const fd_instance* instance) {
  if (r->num_p_instances >= r->max_p_instances) {
    return;
  }
  fd_instance* grown = instance_storage_reserve(r->p_instances,
      &r->p_capacity, r->max_p_instances, r->num_p_instances + 1, sizeof(fd_instance));
  if (!grown) return;
  r->p_instances = grown;
  r->p_instances[r->num_p_instances++] = *instance;
}

void fd_renderer_push_t(fd_renderer* r, const fd_instance* instance) {
  if (r->num_t_instances >= r->max_t_instances) {
    return;
  }
  fd_instance* grown = instance_storage_reserve(r->t_instances,
      &r->t_capacity, r->max_t_instances, r->num_t_instances + 1, sizeof(fd_instance));
  if (!grown) return;
  r->t_instances = grown;
  r->t_instances[r->num_t_instances++] = *instance;
}

void fd_renderer_push_u(fd_renderer* r, const fd_instance* instance) {
  if (r->num_u_instances >= r->max_u_instances) {
    return;
  }
  fd_instance* grown = instance_storage_reserve(r->u_instances,
      &r->u_capacity, r->max_u_instances, r->num_u_instances + 1, sizeof(fd_instance));
  if (!grown) return;
  r->u_instances = grown;
  r->u_instances[r->num_u_instances++] = *instance;
}

static void fd_draw_batch(fd_renderer* r, tcontext* ctx, tdbuffer* buffer,
                           fd_instance* instances, int count, int pipeline) {
  if (count <= 0 || !tdbuffer_upload(ctx, buffer, ctx->current_frame, instances,
                                     (VkDeviceSize)count * sizeof(fd_instance)))
    return;
  tcontext_frame* fr = ctx->frames + ctx->current_frame;
  vkCmdBindPipeline(fr->cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, r->pipelines[pipeline]);
  vkCmdBindVertexBuffers(fr->cmd, 1, 1, &buffer[ctx->current_frame].handle,
                         (VkDeviceSize[]){0});
  vkCmdDraw(fr->cmd, 4, (uint32_t)count, 0, 0);
}

void fd_renderer_render(fd_renderer* r, tcontext* ctx) {
  /* Keep the original layer order: shadows, food, preys, letter strokes. */
  fd_draw_batch(r, ctx, r->u_instance_buffer, r->u_instances, r->num_u_instances, 2);
  fd_draw_batch(r, ctx, r->instance_buffer, r->instances, r->num_instances, r->pipeline_idx);
  fd_draw_batch(r, ctx, r->p_instance_buffer, r->p_instances, r->num_p_instances, 0);
  fd_draw_batch(r, ctx, r->t_instance_buffer, r->t_instances, r->num_t_instances, 2);
}

void fd_renderer_destroy(fd_renderer* r, tcontext* ctx) {
  free(r->u_instances);
  tdbuffer_destroy(ctx, r->u_instance_buffer);
  free(r->t_instances);
  tdbuffer_destroy(ctx, r->t_instance_buffer);
  vkDestroyPipeline(ctx->device, r->pipelines[2], NULL);

  free(r->p_instances);
  tdbuffer_destroy(ctx, r->p_instance_buffer);

  free(r->instances);
  tdbuffer_destroy(ctx, r->instance_buffer);
  vkDestroyPipeline(ctx->device, r->pipelines[1], NULL);
  vkDestroyPipeline(ctx->device, r->pipelines[0], NULL);
  free(r);
}
