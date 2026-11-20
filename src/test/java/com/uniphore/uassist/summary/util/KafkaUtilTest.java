package com.uniphore.uassist.summary.util;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaUtilTest {

  @Mock
  private KafkaAdmin kafkaAdmin;

  @Mock
  private AdminClient adminClient;

  @Mock
  private DescribeTopicsResult describeTopicsResult;

  @Mock
  private TopicDescription topicDescription;

  @Mock
  private KafkaFuture<Map<String, TopicDescription>> kafkaFuture;

  @InjectMocks
  private KafkaUtil kafkaUtil;

  @BeforeEach
  void setUp() {
    kafkaUtil.init();
  }

  @Test
  void testGetPartitionCount_NewTopic() throws Exception {
    String topic = "test-topic";
    int expectedPartitions = 3;

    Map<String, Object> configProps = new HashMap<>();
    when(kafkaAdmin.getConfigurationProperties()).thenReturn(configProps);
    when(adminClient.describeTopics(Collections.singletonList(topic))).thenReturn(
        describeTopicsResult);
    when(describeTopicsResult.all()).thenReturn(kafkaFuture);
    when(kafkaFuture.get()).thenReturn(Collections.singletonMap(topic, topicDescription));
    when(topicDescription.partitions()).thenReturn(Collections.nCopies(expectedPartitions, null));

    try (var mocked = mockStatic(AdminClient.class)) {
      mocked.when(() -> AdminClient.create(configProps)).thenReturn(adminClient);

      int result = kafkaUtil.getPartitionCount(topic);

      assertEquals(expectedPartitions, result);
      assertEquals(expectedPartitions, kafkaUtil.getPartitionCount(topic));
    }
  }

  @Test
  void testGetPartitionCount_AdminClientException() throws Exception {
    String topic = "error-topic";

    Map<String, Object> configProps = new HashMap<>();
    when(kafkaAdmin.getConfigurationProperties()).thenReturn(configProps);
    when(adminClient.describeTopics(Collections.singletonList(topic))).thenReturn(
        describeTopicsResult);
    when(describeTopicsResult.all()).thenReturn(kafkaFuture);
    when(kafkaFuture.get()).thenThrow(new RuntimeException("Kafka error"));

    try (var mocked = mockStatic(AdminClient.class)) {
      mocked.when(() -> AdminClient.create(configProps)).thenReturn(adminClient);

      assertThrows(RuntimeException.class, () -> kafkaUtil.getPartitionCount(topic));
    }
  }

  @Test
  void testGetPartition_ValidInput() {
    String key1 = "test-key-1";
    String key2 = "test-key-2";
    int partitionCount = 5;

    int result1 = kafkaUtil.getPartition(key1, partitionCount);
    int result2 = kafkaUtil.getPartition(key2, partitionCount);

    assertTrue(result1 >= 0 && result1 < partitionCount);
    assertTrue(result2 >= 0 && result2 < partitionCount);
    assertEquals(result1, kafkaUtil.getPartition(key1, partitionCount));
  }

  @Test
  void testGetPartition_ZeroPartitions() {
    assertEquals(-1, kafkaUtil.getPartition("any-key", 0));
  }

  @Test
  void testGetPartition_NegativePartitions() {
    assertEquals(-1, kafkaUtil.getPartition("any-key", -1));
  }

  @Test
  void testGetPartition_NullKey() {
    assertThrows(NullPointerException.class, () -> kafkaUtil.getPartition(null, 3));
  }
}
