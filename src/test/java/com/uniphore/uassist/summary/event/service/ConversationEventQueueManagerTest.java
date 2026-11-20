package com.uniphore.uassist.summary.event.service;

import com.uniphore.conversations.v1.*;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.event.pojo.ConversationEventsWrapper;
import com.uniphore.uassist.summary.event.pojo.EventMetricsResponse;
import com.uniphore.uassist.summary.event.processing.ConversationEventQueue;
import com.uniphore.uassist.summary.service.SummaryRuntimeService;
import com.uniphore.uassist.summary.util.SummaryHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConversationEventQueueManagerTest {

  @Mock
  private SummaryRuntimeService summaryRuntimeService;

  @Mock
  private SummaryHelper summaryHelper;

  @Mock
  private ConversationEventQueue eventQueue;

  @Mock
  private ConversationEventQueue conversationEventQueue;

  @InjectMocks
  private ConversationEventQueueManager conversationEventQueueManager;

  @BeforeEach
  public void setup() {
    conversationEventQueueManager.init();
    ReflectionTestUtils.setField(conversationEventQueueManager, "eventQueue", eventQueue);
    lenient().when(summaryHelper.getSummaryDocById(anyString()))
        .thenReturn(Optional.of(mock(SummaryDoc.class)));
  }

  @Test
  public void testProcessEvent_StartConversationEvent() {
    ConversationEventEnvelope envelope = ConversationEventEnvelope.newBuilder()
        .setConversationStarted(ConversationEventEnvelope.ConversationStarted.newBuilder()
            .setConversation(Conversation.newBuilder().setConversationId("convId").build()).build())
        .build();
    ConversationEventsWrapper event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    conversationEventQueueManager.processEvent(event);
  }

  @Test
  public void testProcessEvent_SummaryAddedEvent() throws InterruptedException {
    ConversationEventEnvelope envelope = ConversationEventEnvelope.newBuilder().setSummaryAdded(
        ConversationEventEnvelope.SummaryAdded.newBuilder().setSummary(Summary.newBuilder().build())
            .build()).build();

    ConversationEventsWrapper event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();

    conversationEventQueueManager.processEvent(event);

  }

  @Test
  public void testProcessEvent_SummaryEffectedEvent() {

    //entity added event
    ConversationEventEnvelope envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setEntityAdded(ConversationEventEnvelope.EntityAdded.newBuilder()
                .setEntity(Entity.newBuilder().setValue("dummy")).build()).build();

    ConversationEventsWrapper event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper finalEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(finalEvent));

    //variable added event
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setVariablesAdded(ConversationEventEnvelope.VariablesAdded.newBuilder()
                .addVariables(Variable.newBuilder().setVariableInstanceId("dummary").build()))
            .build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper vAEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(vAEvent));

    //variable updated
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setVariablesUpdated(ConversationEventEnvelope.VariablesUpdated.newBuilder()
                .addVariables(Variable.newBuilder().setVariableInstanceId("dummary").build()))
            .build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper vuEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(vuEvent));

    //intents added
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setIntentsAdded(ConversationEventEnvelope.IntentsAdded.newBuilder()
                .addIntents(Intent.newBuilder().setText("dummary").build())).build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper iaEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(iaEvent));

    //transcript added
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setTranscriptAdded(ConversationEventEnvelope.TranscriptAdded.newBuilder()
                .setTranscript(Transcript.newBuilder().setLang("dummary").build()).build()).build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper tdEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(tdEvent));

    //flow ended
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setFlowEnded(
                ConversationEventEnvelope.FlowEnded.newBuilder().setFlowInstanceId("instance id")
                    .build()).build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper feEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(feEvent));

    //intent deleted
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setIntentDeleted(ConversationEventEnvelope.IntentDeleted.newBuilder()
                .setIntent(Intent.newBuilder().setText("dummary").build()).build()).build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper idEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(idEvent));

    //entity updated
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setEntityUpdated(ConversationEventEnvelope.EntityUpdated.newBuilder()
                .setEntity(Entity.newBuilder().setValue("dummy")).build()).build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper euEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(euEvent));

    //transcript chunk added
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setTranscriptChunkAdded(ConversationEventEnvelope.TranscriptChunkAdded.newBuilder()
                .setTranscriptChunk(TranscriptChunk.newBuilder().setLang("dummary").build())
                .build()).build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper tcEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(tcEvent));

    //state updated
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setStateUpdated(ConversationEventEnvelope.StateUpdated.newBuilder().setState(
                    State.newBuilder().setState(ConversationState.CONVERSATION_STATE_ENDED).build())
                .build()).build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper suEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(suEvent));

    //state updated v2
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setStateV2Updated(ConversationEventEnvelope.StateV2Updated.newBuilder().setState(
                    StateV2.newBuilder().setState(ConversationState.CONVERSATION_STATE_ENDED).build())
                .build()).build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper su2Event = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(su2Event));

    //policied added
    envelope =
        ConversationEventEnvelope.newBuilder().setConversationId("convId").setTenantId("tenantId")
            .setPoliciesAdded(ConversationEventEnvelope.PoliciesAdded.newBuilder().addPolicies(
                    Policy.newBuilder().setPolicyId("dummary").build())
                .build()).build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper paEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(paEvent));

    //agent update
    AgentUpdateRequest agentupdate =
        AgentUpdateRequest.builder().build();

    event = ConversationEventsWrapper.builder().conversationId("convId")
        .agentUpdateRequest(agentupdate)
        .eventType(ConversationEventsWrapper.RequestType.AGENT_UPDATE).environment("dev")
        .tenantId("tenantId").build();
    ConversationEventsWrapper auEvent = event;
    Assertions.assertDoesNotThrow(()-> conversationEventQueueManager.processEvent(auEvent));
  }

  @Test
  public void testGetMetrics() {
    ConversationEventQueue queue = mock(ConversationEventQueue.class);
    when(queue.getLastEventTime()).thenReturn(System.currentTimeMillis());
    when(queue.getQueueSize()).thenReturn(1);

    ReflectionTestUtils.setField(conversationEventQueueManager, "eventQueue", queue);
    EventMetricsResponse response = conversationEventQueueManager.getMetrics();

    assertEquals(1, response.getSize());
    assertNotNull(response.getConversationLastEventTime());
  }

  @Test
  public void testProcessEvent_NullPointerException() {
    ConversationEventEnvelope envelope = ConversationEventEnvelope.newBuilder()
        .setConversationId("convId")
        .setTenantId("tenantId")
        .build();

    ConversationEventsWrapper event = ConversationEventsWrapper.builder()
        .conversationId("convId")
        .conversationEventEnvelope(envelope)
        .eventType(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT)
        .environment("dev")
        .tenantId("tenantId")
        .build();

    assertDoesNotThrow(() -> conversationEventQueueManager.processEvent(event));
  }
}
