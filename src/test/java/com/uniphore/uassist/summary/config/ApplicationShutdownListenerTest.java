package com.uniphore.uassist.summary.config;

import com.mongodb.client.MongoClient;
import com.uniphore.uassist.summary.conf.ApplicationShutdownListener;
import com.uniphore.uassist.summary.event.processing.ConversationEventQueue;
import com.uniphore.uassist.summary.event.service.ConversationEventQueueManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.stream.binding.InputBindingLifecycle;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.*;

public class ApplicationShutdownListenerTest {

  @Mock
  private ThreadPoolTaskExecutor threadPoolMongoExecutor;

  @Mock
  private ThreadPoolTaskExecutor threadPoolExecutor;

  @Mock
  private ExecutorService fixedThreadPoolExecutor;

  @Mock
  private ExecutorService tenantOperationExecutorService;

  @Mock
  private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  @Mock
  private InputBindingLifecycle inputBindingLifecycle;

  @Mock
  private LettuceConnectionFactory redisConnectionFactory;

  @Mock
  private MongoClient mongoClient;

  @Mock
  private ConversationEventQueueManager conversationEventQueueManager;

  @Mock
  private ConversationEventQueue conversationEventQueue;

  @InjectMocks
  private ApplicationShutdownListener applicationShutdownListener;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void testOnApplicationEvent() {
    ApplicationContext context = mock(ApplicationContext.class);
    ContextClosedEvent event = new ContextClosedEvent(context);
    final Map<String, MessageListenerContainer> listenerContainers = new ConcurrentHashMap();
    when(kafkaListenerEndpointRegistry.getListenerContainers()).thenReturn(
        listenerContainers.values());
    when(conversationEventQueueManager.getEventQueue()).thenReturn(conversationEventQueue);

    applicationShutdownListener.onApplicationEvent(event);

    // ingestion is halted first
    verify(inputBindingLifecycle, times(1)).stop();
    verify(kafkaListenerEndpointRegistry, times(1)).getListenerContainers();
    // event queue is drained
    verify(conversationEventQueueManager, atLeastOnce()).getEventQueue();
    verify(conversationEventQueue, times(1)).shutdown();
    // executors drained, then connections closed
    verify(threadPoolExecutor, times(1)).shutdown();
    verify(threadPoolMongoExecutor, times(1)).shutdown();
    verify(fixedThreadPoolExecutor, times(1)).shutdown();
    verify(tenantOperationExecutorService, times(1)).shutdown();
    verify(redisConnectionFactory, times(1)).destroy();
    verify(mongoClient, times(1)).close();
  }

  @Test
  public void testCloseKafkaConsumers() {
    applicationShutdownListener.closeKafkaConsumers();
    verify(inputBindingLifecycle, times(1)).stop();
    verify(kafkaListenerEndpointRegistry, times(1)).getListenerContainers();
  }

  @Test
  public void testCloseRedisClientConnections() {
    applicationShutdownListener.closeRedisClientConnections();
    verify(redisConnectionFactory, times(1)).destroy();
  }

  @Test
  public void testCloseMongoClientConnections() {
    applicationShutdownListener.closeMongoClientConnections();
    verify(mongoClient, times(1)).close();
  }
}
