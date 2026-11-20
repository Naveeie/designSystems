package com.uniphore.uassist.summary.integrationtests;

import com.uniphore.conversations.v1.ConversationEventEnvelope;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class IntegrationTestHelper {
  @Autowired
  private KafkaTemplate<String, byte[]> kafkaTemplate;

  private static final String TOPIC = "conversation-events";

  public void publishKafka(ConversationEventEnvelope conversationEventEnvelope) {
    try {
      byte[] serializedData = conversationEventEnvelope.toByteArray();
      
      System.out.println("Serialized Kafka Message: " + Arrays.toString(serializedData));

      ProducerRecord<String, byte[]> record = new ProducerRecord<>(TOPIC, null, serializedData);
      record.headers()
          .add(new RecordHeader("kafka_receivedMessageKey", "default-value".getBytes()));

      kafkaTemplate.send(record).get();
      System.out.println("Kafka message sent successfully!");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
