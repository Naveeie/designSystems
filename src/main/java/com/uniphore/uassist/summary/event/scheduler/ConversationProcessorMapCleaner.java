package com.uniphore.uassist.summary.event.scheduler;

import com.uniphore.uassist.summary.event.service.ConversationEventQueueManager;
import com.uniphore.uassist.summary.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@EnableScheduling
@Slf4j
public class ConversationProcessorMapCleaner {

  @Autowired
  private ConversationEventQueueManager eventService;

  @Value("${conversations.processor.scheduler.processor-idle-duration:1800}")
  private int processorIdleDuration;

  @Scheduled(fixedDelayString = "${conversations.processor.scheduler.frequency:5000}")
  public void cleanStaleConversations() {
    if (this.eventService.getConversationIdLastUpdatedTimeStamp() == null) {
      return;
    }
    Set<String> convIds = new HashSet<>();
    log.debug("scheduler cleaned up, before size: {}",
        this.eventService.getConversationIdLastUpdatedTimeStamp().size());
    this.eventService.getConversationIdLastUpdatedTimeStamp().forEach((key, lastProcessedTime) -> {
      long lastEventTime = lastProcessedTime / 1000;
      long currentTIme = DateUtil.TIME.millis() / 1000;
      if (currentTIme - lastEventTime > (long) this.processorIdleDuration) {
        convIds.add(key);
      }
    });
    convIds.forEach(key -> this.eventService.getConversationIdLastUpdatedTimeStamp().remove(key));
    if (log.isDebugEnabled()) {
      log.debug("scheduler cleaned up, size: {},queue size: {} conversations: {}", convIds.size(),
          this.eventService.getConversationIdLastUpdatedTimeStamp().size(), convIds);
    } else {
      log.info("scheduler cleaned up, size: {} conversations", convIds.size());
    }
  }
}
