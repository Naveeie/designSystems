package com.uniphore.uassist.summary.kafka;

import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.conversations.v1.ProductType;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.uassist.summary.event.helper.ConversationEventProcessingHelper;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.service.SummaryRuntimeService;
import com.uniphore.uassist.summary.util.PolicyHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class MessageConsumerTest {

  @InjectMocks
  private MessageConsumer messageConsumer;

  @Mock private SummaryRuntimeService summaryRuntimeService;
  @Mock private ConversationEventProcessingHelper eventHelper;
  @Mock private LaunchDarklyHelper ldHelper;
  @Mock private PolicyHelper policyHelper;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testConversationEvents_validMessageWithFFDisabled() throws Exception {
    String key = "tenant_abc";
    ConversationEventEnvelope envelope = ConversationEventEnvelope.newBuilder()
        .setTenantId("tenant")
        .setConversationId("conv-id")
        .setEnvironment("dev")
        .addTargetProducts(ProductType.PRODUCT_TYPE_U_ASSIST.getNumber())
        .build();

    Message<byte[]> message = mock(Message.class);
    when(message.getPayload()).thenReturn(envelope.toByteArray());

    Map<String, Object> headers = new HashMap<>();
    headers.put(KafkaHeaders.KEY, key);
    when(message.getHeaders()).thenReturn(new MessageHeaders(headers));

    when(ldHelper.isFFEnabled(eq("uassist-process-conversation-events"), anyString()))
        .thenReturn(false);

    messageConsumer.conversationEvents().accept(message);

    verifyNoInteractions(summaryRuntimeService);
    verifyNoInteractions(eventHelper);
  }

  @Test
  void testConversationEvents_validMessageWithParallelFFEnabled() throws Exception {
    String key = "tenant";
    ConversationEventEnvelope envelope = ConversationEventEnvelope.newBuilder()
        .setTenantId("tenant")
        .setConversationId("conv-id")
        .setEnvironment("dev")
        .addTargetProducts(ProductType.PRODUCT_TYPE_U_ASSIST.getNumber())
        .build();

    Message<byte[]> message = mock(Message.class);
    when(message.getPayload()).thenReturn(envelope.toByteArray());

    Map<String, Object> headers = new HashMap<>();
    headers.put(KafkaHeaders.KEY, key);
    when(message.getHeaders()).thenReturn(new MessageHeaders(headers));

    when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);

    messageConsumer.conversationEvents().accept(message);

    verify(eventHelper).pushEventToConversationQueue(any(ConversationEventEnvelope.class));
  }

  @Test
  void testConversationEvents_validMessageWithParallelFFDisabled_andNotBlocked() throws Exception {
    String key = "tenant";
    ConversationEventEnvelope envelope = ConversationEventEnvelope.newBuilder()
        .setTenantId("tenant")
        .setConversationId("conv-id")
        .setEnvironment("dev")
        .addTargetProducts(ProductType.PRODUCT_TYPE_U_ASSIST.getNumber())
        .build();

    Message<byte[]> message = mock(Message.class);
    when(message.getPayload()).thenReturn(envelope.toByteArray());

    Map<String, Object> headers = new HashMap<>();
    headers.put(KafkaHeaders.KEY, key);
    when(message.getHeaders()).thenReturn(new MessageHeaders(headers));

    when(ldHelper.isFFEnabled(anyString(), anyString()))
        .thenReturn(true)  // feature flag enabled
        .thenReturn(false); // parallel FF disabled
    when(policyHelper.isConversationBlocked(any())).thenReturn(false);

    messageConsumer.conversationEvents().accept(message);

    verify(summaryRuntimeService).handleConversationEvent(any(), anyLong());
  }

  @Test
  void testConversationEvents_validMessageWithParallelFFDisabled_andBlocked() throws Exception {
    String key = "tenant";
    ConversationEventEnvelope envelope = ConversationEventEnvelope.newBuilder()
        .setTenantId("tenant")
        .setConversationId("conv-id")
        .setEnvironment("dev")
        .addTargetProducts(ProductType.PRODUCT_TYPE_U_ASSIST.getNumber())
        .build();

    Message<byte[]> message = mock(Message.class);
    when(message.getPayload()).thenReturn(envelope.toByteArray());

    Map<String, Object> headers = new HashMap<>();
    headers.put(KafkaHeaders.KEY, key);
    when(message.getHeaders()).thenReturn(new MessageHeaders(headers));

    when(ldHelper.isFFEnabled(anyString(), anyString()))
        .thenReturn(true)
        .thenReturn(false);
    when(policyHelper.isConversationBlocked(any())).thenReturn(true);

    messageConsumer.conversationEvents().accept(message);

    verify(summaryRuntimeService, never()).handleConversationEvent(any(), anyLong());
    verify(eventHelper, never()).pushEventToConversationQueue((ConversationEventEnvelope) any());
  }

  @Test
  void testConversationEvents_invalidProtoBuffer() {
    String key = "tenant";
    byte[] invalidData = new byte[]{1, 2, 3};

    Message<byte[]> message = mock(Message.class);
    when(message.getPayload()).thenReturn(invalidData);

    Map<String, Object> headers = new HashMap<>();
    headers.put(KafkaHeaders.KEY, key);
    when(message.getHeaders()).thenReturn(new MessageHeaders(headers));

    messageConsumer.conversationEvents().accept(message);

    verifyNoInteractions(summaryRuntimeService);
    verifyNoInteractions(eventHelper);
  }

  @Test
  void testConversationEvents_targetProductMismatch() throws Exception {
    String key = "tenant";
    ConversationEventEnvelope envelope = ConversationEventEnvelope.newBuilder()
        .setTenantId("tenant")
        .setConversationId("conv-id")
        .setEnvironment("dev")
        .build();  

    Message<byte[]> message = mock(Message.class);
    when(message.getPayload()).thenReturn(envelope.toByteArray());

    Map<String, Object> headers = new HashMap<>();
    headers.put(KafkaHeaders.KEY, key);
    when(message.getHeaders()).thenReturn(new MessageHeaders(headers));

    when(ldHelper.isFFEnabled(anyString(), anyString()))
        .thenReturn(true)
        .thenReturn(true);

    messageConsumer.conversationEvents().accept(message);

    verifyNoInteractions(summaryRuntimeService);
    verifyNoInteractions(eventHelper);
  }

  @Test
  void testConversationEvents_exceptionInHandler() throws Exception {
    String key = "tenant";
    ConversationEventEnvelope envelope = ConversationEventEnvelope.newBuilder()
        .setTenantId("tenant")
        .setConversationId("conv-id")
        .setEnvironment("dev")
        .addTargetProducts(ProductType.PRODUCT_TYPE_U_ASSIST.getNumber())
        .build();

    Message<byte[]> message = mock(Message.class);
    when(message.getPayload()).thenReturn(envelope.toByteArray());

    Map<String, Object> headers = new HashMap<>();
    headers.put(KafkaHeaders.KEY, key);
    when(message.getHeaders()).thenReturn(new MessageHeaders(headers));

    when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
    doThrow(new RuntimeException("Boom")).when(eventHelper).pushEventToConversationQueue(
        (ConversationEventEnvelope) any());

    messageConsumer.conversationEvents().accept(message);
  }

  @Test
  void testAgentUpdate_withFeatureEnabled() {
    AgentUpdateRequest request = new AgentUpdateRequest();
    request.setTenantId("tenant");
    request.setEnvironment("dev");
    request.setConversationId("conv-id");

    when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);

    messageConsumer.agentUpdate().accept(request);

    verify(eventHelper).pushEventToConversationQueueFromConsumer(eq(request));
  }

  @Test
  void testAgentUpdate_withFeatureDisabled() {
    AgentUpdateRequest request = new AgentUpdateRequest();
    request.setTenantId("tenant");
    request.setEnvironment("dev");
    request.setConversationId("conv-id");

    when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(false);

    messageConsumer.agentUpdate().accept(request);

    verify(eventHelper, never()).pushEventToConversationQueueFromConsumer(any());
  }
}
