package com.uniphore.uassist.summary.redisstream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AgentUpdateEventPublisher {

  @Value("${redis.stream.agent-event-key:uassist-summary-agent-update}")
  private String agentUpdateStreamKey;

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  public static final String MESSAGE = "message";

  public RecordId publishAgentUpdateEventMessage(AgentUpdateRequest request) {
    log.info("publishMessage stream configChangeEvent :{}", request);
    RecordId recordId = null;
    try {
      Map<byte[], byte[]> map = new HashMap<>();
      map.put(MESSAGE.getBytes(), objectMapper.writeValueAsBytes(request));

      ByteRecord record =
          StreamRecords.rawBytes(map).withStreamKey(agentUpdateStreamKey.getBytes());
      recordId = redisTemplate.getConnectionFactory().getConnection().xAdd(record);
      log.info("publishMessage to redis stream recordId: {}", recordId);
    } catch (Exception e) {
      log.error("Failed to publishMessage to redis stream ", e);
    }
    return recordId;
  }

}
