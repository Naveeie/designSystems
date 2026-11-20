package com.uniphore.uassist.summary.event.helper;

import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.event.pojo.ConversationEventsWrapper;
import com.uniphore.uassist.summary.event.processing.ConversationEventQueue;
import com.uniphore.uassist.summary.event.service.ConversationEventQueueManager;
import com.uniphore.uassist.summary.redisstream.AgentUpdateEventPublisher;
import com.uniphore.uassist.summary.util.PolicyHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConversationEventProcessingHelperTest {

  @InjectMocks
  private ConversationEventProcessingHelper helper;

  @Mock
  private ConversationEventQueueManager eventProcessService;

  @Mock
  private AgentUpdateEventPublisher eventPublisher;

  @Mock
  private PolicyHelper policyHelper;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testPushEventToConversationQueue_ConversationEventEnvelope() {
    ConversationEventEnvelope eventEnvelope =
        ConversationEventEnvelope.newBuilder().setConversationId("conv123")
            .setEnvironment("testEnv").setTenantId("tenant123").build();

    ConversationEventsWrapper expectedRequest =
        ConversationEventsWrapper.builder().conversationEventEnvelope(eventEnvelope)
            .conversationId("conv123")
            .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT)
            .environment("testEnv").tenantId("tenant123").build();
    helper.pushEventToConversationQueue(eventEnvelope);
    verify(eventProcessService).processEvent(eq(expectedRequest));
  }

  @Test
  void testPushEventToConversationQueue_AgentUpdateRequest_QueueContainsKey() {
    AgentUpdateRequest agentUpdateRequest = AgentUpdateRequest.builder().conversationId("conv123")
        .agentAction(Commons.AgentAction.UPDATE_AGENT_STEP).environment("testEnv")
        .tenantId("tenant123").build();
    Map<String, ConversationEventQueue> queues = new HashMap<>();
    queues.put("conv123", mock(ConversationEventQueue.class));
    Map<String, Long> lastUpdatedMap = new HashMap<>();
    lastUpdatedMap.put("conv123", System.currentTimeMillis());
    when(eventProcessService.getConversationIdLastUpdatedTimeStamp()).thenReturn(lastUpdatedMap);

    ConversationEventsWrapper expectedRequest =
        ConversationEventsWrapper.builder().agentUpdateRequest(agentUpdateRequest)
            .conversationId("conv123").eventType(ConversationEventsWrapper.RequestType.AGENT_UPDATE)
            .environment("testEnv").tenantId("tenant123").build();
    helper.pushEventToConversationQueue(agentUpdateRequest);
    verify(eventProcessService).processEvent(eq(expectedRequest));
    verify(eventPublisher, never()).publishAgentUpdateEventMessage(any());
  }

  @Test
  void testPushEventToConversationQueue_AgentUpdateRequest_QueueNotContainsKey() {
    AgentUpdateRequest agentUpdateRequest = AgentUpdateRequest.builder().conversationId("conv123")
        .agentAction(Commons.AgentAction.AGENT_NOTES).environment("testEnv").tenantId("tenant123")
        .build();
    helper.pushEventToConversationQueue(agentUpdateRequest);
    verify(eventPublisher).publishAgentUpdateEventMessage(agentUpdateRequest);
    verify(eventProcessService, never()).processEvent(any());
  }

  @Test
  void testPushEventToConversationQueueFromConsumer() {
    AgentUpdateRequest agentUpdateRequest = AgentUpdateRequest.builder().conversationId("conv123")
        .agentAction(Commons.AgentAction.GEN_AI_SUMMARY_EDIT).environment("testEnv")
        .tenantId("tenant123").build();
    Map<String, ConversationEventQueue> queues = new HashMap<>();
    queues.put("conv123", mock(ConversationEventQueue.class));
    Map<String, Long> lastUpdatedMap = new HashMap<>();
    lastUpdatedMap.put("conv123", System.currentTimeMillis());
    when(eventProcessService.getConversationIdLastUpdatedTimeStamp()).thenReturn(lastUpdatedMap);
    ConversationEventsWrapper expectedRequest =
        ConversationEventsWrapper.builder().agentUpdateRequest(agentUpdateRequest)
            .conversationId("conv123").eventType(ConversationEventsWrapper.RequestType.AGENT_UPDATE)
            .environment("testEnv").tenantId("tenant123").build();
    helper.pushEventToConversationQueueFromConsumer(agentUpdateRequest);
    verify(eventProcessService).processEvent(eq(expectedRequest));
  }
}
