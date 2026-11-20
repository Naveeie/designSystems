package com.uniphore.uassist.summary.event.service;

import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.event.pojo.ConversationEventsWrapper;
import com.uniphore.uassist.summary.event.pojo.EventMetricsResponse;
import com.uniphore.uassist.summary.event.processing.ConversationEventQueue;
import com.uniphore.uassist.summary.service.SummaryRuntimeService;
import com.uniphore.uassist.summary.util.DateUtil;
import com.uniphore.uassist.summary.util.SummaryHelper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@Getter
public class ConversationEventQueueManager {

  @Autowired
  @Lazy
  private SummaryRuntimeService summaryRuntimeService;

  @Autowired
  private SummaryHelper summaryHelper;

  @Getter
  private Map<String, Long> conversationIdLastUpdatedTimeStamp;

  private ConversationEventQueue eventQueue;

  @PostConstruct
  public void init() {
    this.eventQueue = new ConversationEventQueue(this.summaryRuntimeService);
    this.conversationIdLastUpdatedTimeStamp = new ConcurrentHashMap<>();
    this.eventQueue.start();
  }

  public void processEvent(final ConversationEventsWrapper event) {
    if (!this.checkSummaryEffectedEvent(event)) {
      return;
    }
    if (event.getEventType().equals(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT)) {
      ConversationEventEnvelope cEvent = event.getConversationEventEnvelope();
      if (StringUtils.isNotEmpty(cEvent.getConversationStarted().toString())) {
        // conversation start creates the SummaryDoc → mark as tracked on this pod and enqueue
        log.info("processEvent for conversation start conv id: {}", event.getConversationId());
        this.conversationIdLastUpdatedTimeStamp.put(event.getConversationId(),
            DateUtil.TIME.millis());
        this.eventQueue.addEvent(event);
      } else {
        checkSummaryDocAndProcessEvent(event, event.getEventType().toString());
      }
    } else {
      checkSummaryDocAndProcessEvent(event, event.getEventType().toString());
    }
  }

  private void checkSummaryDocAndProcessEvent(ConversationEventsWrapper event, String eventType) {
    // Fast path: conversation already tracked on this pod → SummaryDoc is known to exist.
    // Refresh the timestamp so an active conversation is not evicted by the idle cleaner.
    if (this.conversationIdLastUpdatedTimeStamp.containsKey(event.getConversationId())) {
      this.conversationIdLastUpdatedTimeStamp.put(event.getConversationId(),
          DateUtil.TIME.millis());
      this.eventQueue.addEvent(event);
      return;
    }
    // Slow path: first event for this conversation on this pod (e.g. after rebalance).
    // Only start tracking / enqueue once the SummaryDoc is confirmed to exist, so orphan events
    // (doc never created or already deleted) are dropped here instead of failing in the queue.
    Optional<SummaryDoc> summaryDoc =
        this.summaryHelper.getSummaryDocById(event.getConversationId());
    log.info("processEvent for conversation conv id: {}, summary doc status: {}",
        event.getConversationId(), summaryDoc.isPresent());
    if (summaryDoc.isPresent()) {
      this.conversationIdLastUpdatedTimeStamp.put(event.getConversationId(),
          DateUtil.TIME.millis());
      this.eventQueue.addEvent(event);
    } else {
      log.warn(
          "process event ignored as summary doc not found for conversation conv id: {} and eventType: {}",
          event.getConversationId(), eventType);
    }
  }

  private boolean checkSummaryEffectedEvent(ConversationEventsWrapper event) {
    if (event.getEventType().equals(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT)) {
      ConversationEventEnvelope conversationEventEnvelope = event.getConversationEventEnvelope();
      return StringUtils.isNotEmpty(conversationEventEnvelope.getConversationStarted().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getEntityAdded().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getVariablesAdded().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getVariablesUpdated().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getIntentsAdded().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getTranscriptAdded().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getFlowEnded().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getIntentDeleted().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getEntityUpdated().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getTranscriptChunkAdded().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getStateV2Updated().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getStateUpdated().toString())
          || StringUtils.isNotEmpty(conversationEventEnvelope.getPoliciesAdded().toString());
    } else
      return event.getEventType().equals(ConversationEventsWrapper.RequestType.AGENT_UPDATE);
  }

  public EventMetricsResponse getMetrics() {
    EventMetricsResponse response =
        EventMetricsResponse.builder().size(this.eventQueue.getQueueSize())
            .conversationLastEventTime(String.valueOf(this.eventQueue.getLastEventTime())).build();
    return response;
  }
}
