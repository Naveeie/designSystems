package com.uniphore.uassist.summary.redisstream;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ConfigChangeEventPublisher {

  @Value("${redis.stream.config-change-key:uassist-config-change}")
  private String streamKey;

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;
  
  @Autowired
  private ObjectMapper objectMapper;
  
  public static final String MESSAGE = "message";

  public RecordId publishMessage(ConfigChangeEvent configChangeEvent) {
    log.info("publishMessage stream configChangeEvent :{}", configChangeEvent);
    RecordId recordId = null;
    try {
      
      Map<byte[],byte[]> map = new HashMap<>();
      map.put(MESSAGE.getBytes(), objectMapper.writeValueAsBytes(configChangeEvent));
     
      ByteRecord record = StreamRecords.rawBytes(map).withStreamKey(streamKey.getBytes());
      recordId = redisTemplate.getConnectionFactory().getConnection().xAdd(record);
      log.info("publishMessage to redis stream recordId: {}", recordId);
    } catch (Exception e) {
      log.error("Failed to publishMessage to redis stream ", e);
    }
    return recordId;
  }

  public void publishDeleteMessage(String id, String eventType, String tenantId, String env,
      Set<String> removeEntityIds, Set<String> removeVariableIds) {
    publishMessage(ConfigChangeEvent.builder()
        .id(id)
        .env(env)
        .tenantId(tenantId)
        .removeEntityIds(removeEntityIds)
        .removeVariableIds(removeVariableIds)
        .eventType(eventType)
        .build());
  }

  public void publishDeleteMessage(String id, String eventType, String bpId, String tenantId,
      String env, String expId) {
    publishMessage(ConfigChangeEvent.builder().id(id)
        .env(env)
        .tenantId(tenantId)
        .businessProcessId(bpId)
        .experienceId(expId)
        .eventType(eventType)
        .build());
  }

}
