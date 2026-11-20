package com.uniphore.uassist.summary.config;

import com.uniphore.uassist.summary.ApplicationTest;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KafkaTopicConfTest extends ApplicationTest {

  @Autowired
  private ApplicationContext applicationContext;

  private KafkaTopicConf kafkaTopicConf;

  @BeforeEach
  void setUpData() {
    kafkaTopicConf = applicationContext.getBean(KafkaTopicConf.class);
  }

  @Test
  void testWebsiteMessageTopic() {
    NewTopic websiteMessageTopic =
        applicationContext.getBean("websiteMessageTopic", NewTopic.class);
    assertNotNull(websiteMessageTopic, "Website message topic should not be null");
    assertEquals("test", websiteMessageTopic.name(), "Topic name should be 'test'");
    assertEquals(1, websiteMessageTopic.numPartitions(), "Number of partitions should be 1");
    assertEquals(1, websiteMessageTopic.replicationFactor(), "Number of replicas should be 1");
  }

  @Test
  void testSummaryUpdateTopic() {
    NewTopic summaryUpdateTopic = applicationContext.getBean("summaryupdateTopic", NewTopic.class);
    assertNotNull(summaryUpdateTopic, "Summary update topic should not be null");
    assertEquals("summary-update", summaryUpdateTopic.name(),
        "Topic name should be 'summary-update'");
    assertEquals(1, summaryUpdateTopic.numPartitions(), "Number of partitions should be 1");
    assertEquals(1, summaryUpdateTopic.replicationFactor(), "Number of replicas should be 1");
  }

  @Test
  void testReviewedSummaryTopic() {
    NewTopic reviewedSummaryTopic =
        applicationContext.getBean("reviewedsummaryTopic", NewTopic.class);
    assertNotNull(reviewedSummaryTopic, "Reviewed summary topic should not be null");
    assertEquals("reviewed-summary", reviewedSummaryTopic.name(),
        "Topic name should be 'reviewed-summary'");
    assertEquals(1, reviewedSummaryTopic.numPartitions(), "Number of partitions should be 1");
    assertEquals(1, reviewedSummaryTopic.replicationFactor(), "Number of replicas should be 1");
  }

  @Test
  void testConversationEventsTopic() {
    NewTopic conversationEventsTopic =
        applicationContext.getBean("conversationEventsTopic", NewTopic.class);
    assertNotNull(conversationEventsTopic, "Conversation events topic should not be null");
    assertEquals("conversation-events", conversationEventsTopic.name(),
        "Topic name should be 'conversation-events'");
    assertEquals(1, conversationEventsTopic.numPartitions(), "Number of partitions should be 1");
    assertEquals(1, conversationEventsTopic.replicationFactor(), "Number of replicas should be 1");
  }
}
