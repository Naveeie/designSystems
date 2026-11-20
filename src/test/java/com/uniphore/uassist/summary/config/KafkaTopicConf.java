package com.uniphore.uassist.summary.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConf {

  @Bean
  public NewTopic websiteMessageTopic() {
    return TopicBuilder.name("test")
        .partitions(1)
        .replicas(1)
        .build();
  }

  @Bean
  public NewTopic summaryupdateTopic() {
    return TopicBuilder.name("summary-update")
        .partitions(1)
        .replicas(1)
        .build();
  }

  @Bean
  public NewTopic reviewedsummaryTopic() {
    return TopicBuilder.name("reviewed-summary")
        .partitions(1)
        .replicas(1)
        .build();
  }

  @Bean
  public NewTopic conversationEventsTopic() {
    return TopicBuilder.name("conversation-events")
        .partitions(1)
        .replicas(1)
        .build();
  }
}
