package com.uniphore.uassist.summary.conf;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.binding.InputBindingLifecycle;
import org.springframework.context.ApplicationListener;
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.mongodb.client.MongoClient;

import com.uniphore.uassist.summary.event.processing.ConversationEventQueue;
import com.uniphore.uassist.summary.event.service.ConversationEventQueueManager;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class ApplicationShutdownListener implements ApplicationListener<ContextClosedEvent> {
  @Autowired
  @Qualifier(value = "threadPoolMongoExecutor")
  private ThreadPoolTaskExecutor threadPoolMongoExecutor;

  @Autowired
  @Qualifier(value = "threadPoolExecutor")
  private ThreadPoolTaskExecutor threadPoolExecutor;

  @Autowired
  private ExecutorService fixedThreadPoolExecutor;

  @Autowired
  private ExecutorService tenantOperationExecutorService;
  
  @Autowired
  private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  @Autowired
  private InputBindingLifecycle inputBindingLifecycle;

  @Autowired
  private LettuceConnectionFactory redisConnectionFactory;

  @Autowired
  private MongoClient mongoClient;

  @Autowired
  private ConversationEventQueueManager conversationEventQueueManager;

  @Value("${app.graceful-shutdown.wait-time:30}")
  private int gracefulShutdownWaitTime;

  public void closeKafkaConsumers() {
    // Spring Cloud Stream functional consumers are bound via the binder, not @KafkaListener, so
    // they are not in KafkaListenerEndpointRegistry. Stop the input bindings directly to halt all
    // event ingestion before draining; the registry stop covers any @KafkaListener beans too.
    this.inputBindingLifecycle.stop();
    kafkaListenerEndpointRegistry.getListenerContainers().forEach(Lifecycle::stop);
  }

  public void closeRedisClientConnections(){
    redisConnectionFactory.destroy();
  }

  public void closeMongoClientConnections(){
    mongoClient.close();
  }

  private void awaitTermination(ExecutorService executor, String name) {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(this.gracefulShutdownWaitTime, TimeUnit.SECONDS)) {
        log.warn("Executor {} did not terminate within {}s; forcing shutdown", name,
            this.gracefulShutdownWaitTime);
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      log.warn("Interrupted while awaiting termination of executor {}; forcing shutdown", name);
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Stops the conversation-event queue thread and waits for it to drain its buffered events. Runs
   * while Redis, Mongo and the task executors are still up, so in-flight events finish cleanly
   * instead of failing against closed connections.
   */
  private void drainEventQueue() {
    ConversationEventQueue eventQueue = this.conversationEventQueueManager.getEventQueue();
    if (eventQueue == null) {
      return;
    }
    log.warn("Shutdown hook started:: conversation event queue size: {}", eventQueue.getQueueSize());
    eventQueue.shutdown();
    try {
      eventQueue.join(TimeUnit.SECONDS.toMillis(this.gracefulShutdownWaitTime));
      if (eventQueue.isAlive()) {
        log.warn("Conversation event queue did not drain within {}s; {} events still queued",
            this.gracefulShutdownWaitTime, eventQueue.getQueueSize());
      } else {
        log.info("Conversation event queue drained successfully");
      }
    } catch (InterruptedException e) {
      log.warn("Interrupted while draining conversation event queue");
      Thread.currentThread().interrupt();
    }
    finally {
      log.warn("After shutdown hook:: conversation event queue did not drain within {}s; {} events still queued",
          this.gracefulShutdownWaitTime, eventQueue.getQueueSize());
    }
  }

  @Override
  public void onApplicationEvent(@NotNull ContextClosedEvent event) {
    log.info("gracefully shutting down application: {}", event);
    //stop kafka container
    this.closeKafkaConsumers();

    // drain the conversation-event queue while its dependencies are still open
    this.drainEventQueue();

    // drain in-flight tasks before closing the connections they depend on
    this.threadPoolExecutor.shutdown();
    this.threadPoolMongoExecutor.shutdown();
    this.awaitTermination(this.fixedThreadPoolExecutor, "fixedThreadPoolExecutor");
    this.awaitTermination(this.tenantOperationExecutorService, "tenantOperationExecutorService");

    //redis connection closing
    this.closeRedisClientConnections();

    //mongo connection closing
    this.closeMongoClientConnections();

  }
}
