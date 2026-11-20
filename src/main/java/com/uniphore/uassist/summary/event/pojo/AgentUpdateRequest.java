package com.uniphore.uassist.summary.event.pojo;

import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.model.AgentRequestPayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentUpdateRequest {
  private String conversationId;
  private String tenantId;
  private String environment;
  private Commons.AgentAction agentAction;
  private long timestamp;
  private AgentRequestPayload agentRequestPayload;
  private AgentStepUpdate agentStepUpdate;


  @Builder
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class AgentStepUpdate {
    private String id;
    private String value;
  }
}
