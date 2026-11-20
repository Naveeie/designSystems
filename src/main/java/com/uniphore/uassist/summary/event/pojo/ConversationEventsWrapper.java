package com.uniphore.uassist.summary.event.pojo;

import com.uniphore.conversations.v1.ConversationEventEnvelope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationEventsWrapper {
  private String conversationId;
  private String tenantId;
  private String environment;
  private String agentAction;
  private ConversationEventEnvelope conversationEventEnvelope;
  private AgentUpdateRequest agentUpdateRequest;
  private RequestType eventType;


  public enum RequestType {
    CONVERSATION_EVENT, AGENT_UPDATE
  }
}
