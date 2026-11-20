package com.uniphore.uassist.summary.redisstream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.uassist.summary.event.helper.ConversationEventProcessingHelper;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.connection.stream.MapRecord;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentUpdateEventListenerTest {

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private ConversationEventProcessingHelper eventHelper;

  @Mock
  private LaunchDarklyHelper ldHelper;

  @InjectMocks
  private AgentUpdateEventListener agentUpdateEventListener;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testOnMessageWhenFeatureFlagEnabled() throws Exception {
    String messageJson =
        "{\"conversationId\":\"test-convo-id\",\"tenantId\":\"test-tenant-id\",\"environment\":\"test-env\"}";
    AgentUpdateRequest request =
        AgentUpdateRequest.builder().conversationId("test-convo-id").tenantId("test-tenant-id")
            .environment("test-env").build();

    MapRecord<byte[], byte[], byte[]> record = MapRecord.create("stream-key".getBytes(),
        Map.of(AgentUpdateEventPublisher.MESSAGE.getBytes(), messageJson.getBytes()));

    when(objectMapper.readValue(any(String.class), eq(AgentUpdateRequest.class))).thenReturn(
        request);
    when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
        LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(true);
    agentUpdateEventListener.onMessage(record);
    verify(objectMapper).readValue(messageJson, AgentUpdateRequest.class);
    verify(ldHelper).isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
        LaunchDarklyConstants.FF_CONTEXT_KIND);
    verify(eventHelper).pushEventToConversationQueueFromConsumer(request);
    verifyNoMoreInteractions(objectMapper, ldHelper, eventHelper);
  }

  @Test
  void testOnMessageWhenFeatureFlagDisabled() throws Exception {
    String messageJson =
        "{\"conversationId\":\"test-convo-id\",\"tenantId\":\"test-tenant-id\",\"environment\":\"test-env\"}";
    AgentUpdateRequest request =
        AgentUpdateRequest.builder().conversationId("test-convo-id").tenantId("test-tenant-id")
            .environment("test-env").build();

    MapRecord<byte[], byte[], byte[]> record = MapRecord.create("stream-key".getBytes(),
        Map.of(AgentUpdateEventPublisher.MESSAGE.getBytes(), messageJson.getBytes()));

    when(objectMapper.readValue(any(String.class), eq(AgentUpdateRequest.class))).thenReturn(
        request);
    when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
        LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(false);
    agentUpdateEventListener.onMessage(record);
    verify(objectMapper).readValue(messageJson, AgentUpdateRequest.class);
    verify(ldHelper).isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
        LaunchDarklyConstants.FF_CONTEXT_KIND);
    verify(eventHelper, never()).pushEventToConversationQueueFromConsumer(
        any(AgentUpdateRequest.class));
    verifyNoMoreInteractions(objectMapper, ldHelper, eventHelper);
  }

  @Test
  void testOnMessageWhenMessageIsEmpty() throws Exception {
    MapRecord<byte[], byte[], byte[]> record = MapRecord.create("stream-key".getBytes(),
        Map.of("random-key".getBytes(), "random-value".getBytes()));
    agentUpdateEventListener.onMessage(record);
    verify(objectMapper, never()).readValue(any(String.class), eq(AgentUpdateRequest.class));
    verify(ldHelper, never()).isFFEnabled(anyString(), anyString());
    verify(eventHelper, never()).pushEventToConversationQueueFromConsumer(
        any(AgentUpdateRequest.class));
    verifyNoMoreInteractions(objectMapper, ldHelper, eventHelper);
  }

  @Test
  void testOnMessageWhenExceptionIsThrown() throws Exception {
    String messageJson =
        "{\"conversationId\":\"test-convo-id\",\"tenantId\":\"test-tenant-id\",\"environment\":\"test-env\"}";
    MapRecord<byte[], byte[], byte[]> record = MapRecord.create("stream-key".getBytes(),
        Map.of(AgentUpdateEventPublisher.MESSAGE.getBytes(), messageJson.getBytes()));
    when(objectMapper.readValue(any(String.class), eq(AgentUpdateRequest.class))).thenThrow(
        new RuntimeException("Parsing error"));
    agentUpdateEventListener.onMessage(record);
    verify(objectMapper).readValue(messageJson, AgentUpdateRequest.class);
    verifyNoMoreInteractions(objectMapper, ldHelper, eventHelper);
  }
}
