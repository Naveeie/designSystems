package com.uniphore.uassist.summary.redisstream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class AgentUpdateEventPublisherTest {

  @InjectMocks
  private AgentUpdateEventPublisher publisher;

  @Mock
  private RedisTemplate<String, Object> redisTemplate;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private RedisConnectionFactory connectionFactory;

  @Mock
  private RedisConnection connection;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
    when(connectionFactory.getConnection()).thenReturn(connection);
  }

  @Test
  void testPublishAgentUpdateEventMessageSuccess() throws Exception {
    // Arrange
    AgentUpdateRequest request = new AgentUpdateRequest(); // Initialize with necessary values
    byte[] requestBytes = new byte[] {1, 2, 3}; // Example byte array
    RecordId expectedRecordId = RecordId.of(1, 23);

    when(objectMapper.writeValueAsBytes(request)).thenReturn(requestBytes);
    when(connection.xAdd(any(ByteRecord.class))).thenReturn(expectedRecordId);

    publisher.publishAgentUpdateEventMessage(request);
  }

  @Test
  void testPublishAgentUpdateEventMessageFailure() throws Exception {
    // Arrange
    AgentUpdateRequest request = new AgentUpdateRequest(); // Initialize with necessary values
    when(objectMapper.writeValueAsBytes(request)).thenThrow(
        new RuntimeException("Serialization error"));

    // Act
    RecordId actualRecordId = publisher.publishAgentUpdateEventMessage(request);

    // Assert
    assertNull(actualRecordId);
    verify(objectMapper).writeValueAsBytes(request);
    verifyNoInteractions(connection); // No interaction with Redis should occur
  }
}
