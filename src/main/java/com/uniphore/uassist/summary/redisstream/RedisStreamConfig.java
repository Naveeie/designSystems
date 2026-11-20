package com.uniphore.uassist.summary.redisstream;

import java.net.UnknownHostException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

  @Value("${redis.stream.config-change-key:uassist-config-change}")
  private String configChangeStreamKey;

  @Value("${redis.stream.pollTimeout:500}")
  private int pollTimeout;

  @Value("${redis.stream.agent-event-key:uassist-summary-agent-update}")
  private String agentUpdateStreamKey;

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  @Autowired
  private ConfigChangeEventListener configChangeEventListener;

  @Autowired
  private AgentUpdateEventListener agentUpdateEventListener;

  @Bean
  public Subscription configChangeEventsubscription() throws UnknownHostException {
    RedisConnectionFactory connectionFactory = this.redisTemplate.getConnectionFactory();
    this.createConsumerGroupIfNotExists(connectionFactory, configChangeStreamKey,
        configChangeStreamKey);
    StreamMessageListenerContainer<byte[], MapRecord<byte[], byte[], byte[]>> listenerContainer =
        this.getListenerContainer(connectionFactory);

    StreamOffset<byte[]> streamOffsetConfigChangeEvent =
        StreamOffset.create(configChangeStreamKey.getBytes(), ReadOffset.lastConsumed());
    StreamMessageListenerContainer.StreamReadRequest<byte[]> readRequestConfigChangeListener =
        StreamMessageListenerContainer.StreamReadRequest.builder(streamOffsetConfigChangeEvent)
            .cancelOnError((err) -> false).errorHandler(
                (err) -> log.debug("StreamMessageListenerContainer ConfigChangeStreamKey", err))
            .build();

    Subscription subscription =
        listenerContainer.register(readRequestConfigChangeListener, configChangeEventListener);
    listenerContainer.start();
    return subscription;
  }

  @Bean
  public Subscription agentUpdateEventsubscription() throws UnknownHostException {
    RedisConnectionFactory connectionFactory = this.redisTemplate.getConnectionFactory();
    this.createConsumerGroupIfNotExists(connectionFactory, agentUpdateStreamKey,
        agentUpdateStreamKey);

    StreamMessageListenerContainer<byte[], MapRecord<byte[], byte[], byte[]>> listenerContainer =
        this.getListenerContainer(connectionFactory);
    StreamOffset<byte[]> streamOffsetAgentUpdateEvent =
        StreamOffset.create(agentUpdateStreamKey.getBytes(), ReadOffset.lastConsumed());
    StreamMessageListenerContainer.StreamReadRequest<byte[]> readRequestAgentUpdateListener =
        StreamMessageListenerContainer.StreamReadRequest.builder(streamOffsetAgentUpdateEvent)
            .cancelOnError((err) -> false).errorHandler(
                (err) -> log.debug("StreamMessageListenerContainer streamOffsetAgentUpdateEvent", err))
            .build();

    Subscription subscription =
        listenerContainer.register(readRequestAgentUpdateListener, agentUpdateEventListener);
    listenerContainer.start();
    return subscription;
  }

  private StreamMessageListenerContainer<byte[], MapRecord<byte[], byte[], byte[]>> getListenerContainer(
      RedisConnectionFactory connectionFactory) {

    StreamMessageListenerContainer.StreamMessageListenerContainerOptions<byte[], MapRecord<byte[], byte[], byte[]>>
        options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
        .keySerializer(RedisSerializer.byteArray()).hashKeySerializer(RedisSerializer.byteArray())
        .<byte[], byte[]>hashValueSerializer(RedisSerializer.byteArray())
        .pollTimeout(Duration.ofMillis(pollTimeout))
        .errorHandler((err) -> log.debug("StreamMessageListenerContainerOptions", err)).build();

    StreamMessageListenerContainer<byte[], MapRecord<byte[], byte[], byte[]>> listenerContainer =
        StreamMessageListenerContainer.create(connectionFactory, options);
    return listenerContainer;
  }

  private void createConsumerGroupIfNotExists(RedisConnectionFactory redisConnectionFactory,
      String streamKey, String groupName) {
    try {
      try {
        redisConnectionFactory.getConnection().streamCommands()
            .xGroupCreate(streamKey.getBytes(), streamKey, ReadOffset.from("0-0"), true);
      } catch (RedisSystemException exception) {
        log.warn(exception.getCause().getMessage());
      }
    } catch (RedisSystemException ex) {
      log.error(ex.getMessage());
    }
  }
}
