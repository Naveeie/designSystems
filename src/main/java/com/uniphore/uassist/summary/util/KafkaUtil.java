package com.uniphore.uassist.summary.util;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

@Component
public class KafkaUtil {

  @Autowired
  private KafkaAdmin kafkaAdmin;

  private Map<String, Integer> partitionTopicMap;

  @PostConstruct
  public void init() {
    partitionTopicMap = new HashMap<>();
  }

  public int getPartitionCount(String topic) throws Exception {
    if (partitionTopicMap.containsKey(topic)) {
      return partitionTopicMap.get(topic);
    } else {
      try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
        DescribeTopicsResult result = client.describeTopics(List.of(topic));
        TopicDescription desc = result.all().get().get(topic);
        partitionTopicMap.put(topic, desc.partitions().size());
        return desc.partitions().size();
      }
    }
  }

  public int getPartition(String key, int partitionCount) {
    if (partitionCount <= 0) {
      return -1; // Equivalent to PartitionAny
    }
    CRC32 crc32 = new CRC32();
    crc32.update(key.getBytes(StandardCharsets.UTF_8));
    long checksum = crc32.getValue();

    // Mask to unsigned 32-bit and mod by partition count
    return (int) ((checksum & 0xFFFFFFFFL) % partitionCount);
  }
}
