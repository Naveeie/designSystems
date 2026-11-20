package com.uniphore.uassist.summary.event.helper;

import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.conversations.v1.PolicyActionType;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.event.pojo.ConversationEventsWrapper;
import com.uniphore.uassist.summary.event.service.ConversationEventQueueManager;
import com.uniphore.uassist.summary.redisstream.AgentUpdateEventPublisher;
import com.uniphore.uassist.summary.util.PolicyHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConversationEventProcessingHelper {

  @Autowired
  private ConversationEventQueueManager eventProcessService;

  @Autowired
  private AgentUpdateEventPublisher eventPublisher;

  @Autowired
  private PolicyHelper policyHelper;

  public void pushEventToConversationQueue(final ConversationEventEnvelope eventEnvelope) {
    ConversationEventsWrapper request =
        ConversationEventsWrapper.builder().conversationEventEnvelope(eventEnvelope)
            .conversationId(eventEnvelope.getConversationId())
            .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT)
            .environment(eventEnvelope.getEnvironment()).tenantId(eventEnvelope.getTenantId())
            .build();
    final boolean isConversationBlocked = policyHelper.isConversationBlocked(eventEnvelope);
    if (isConversationBlocked) {
      eventProcessService.getConversationIdLastUpdatedTimeStamp().remove(eventEnvelope.getConversationId());
      log.info("pushEventToConversationQueue: conversation blocked for id:-> {}",
          eventEnvelope.getConversationId());
      return;
    }
    this.eventProcessService.processEvent(request);
  }


  public void pushEventToConversationQueue(final AgentUpdateRequest agentUpdateRequest) {
    if (this.eventProcessService.getConversationIdLastUpdatedTimeStamp()
        .containsKey(agentUpdateRequest.getConversationId())) {
      log.info("pushEventToConversationQueue: processed event: {} ,event: {}",
          agentUpdateRequest.getConversationId(), agentUpdateRequest.getAgentAction());
      ConversationEventsWrapper request =
          ConversationEventsWrapper.builder().agentUpdateRequest(agentUpdateRequest)
              .conversationId(agentUpdateRequest.getConversationId())
              .eventType(ConversationEventsWrapper.RequestType.AGENT_UPDATE)
              .environment(agentUpdateRequest.getEnvironment())
              .tenantId(agentUpdateRequest.getTenantId()).build();
      this.eventProcessService.processEvent(request);
    } else {
      log.info("pushEventToConversationQueue: publishing event to all nodes: {} ,event: {}",
          agentUpdateRequest.getConversationId(), agentUpdateRequest.getAgentAction());
      this.eventPublisher.publishAgentUpdateEventMessage(agentUpdateRequest);
    }
  }

  public void pushEventToConversationQueueFromConsumer(
      final AgentUpdateRequest agentUpdateRequest) {
    log.debug("conversation event queue {}",this.eventProcessService.getConversationIdLastUpdatedTimeStamp());
    if (this.eventProcessService.getConversationIdLastUpdatedTimeStamp()
        .containsKey(agentUpdateRequest.getConversationId())) {
      log.info("pushEventToConversationQueueFromEvent: processed event: {} ,event: {}",
          agentUpdateRequest.getConversationId(), agentUpdateRequest.getAgentAction());
      ConversationEventsWrapper request =
          ConversationEventsWrapper.builder().agentUpdateRequest(agentUpdateRequest)
              .conversationId(agentUpdateRequest.getConversationId())
              .eventType(ConversationEventsWrapper.RequestType.AGENT_UPDATE)
              .environment(agentUpdateRequest.getEnvironment())
              .tenantId(agentUpdateRequest.getTenantId()).build();
      this.eventProcessService.processEvent(request);
    }
  }
}
