package com.uniphore.uassist.summary.event.processing;

import com.uniphore.platform.telemetry.context.ContextSetter;
import com.uniphore.platform.telemetry.tracer.PlatformOpenTracing;
import com.uniphore.uassist.summary.event.pojo.ConversationEventsWrapper;
import com.uniphore.uassist.summary.service.SummaryRuntimeService;
import com.uniphore.uassist.summary.util.DateUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConversationEventQueue extends Thread {

  private final BlockingQueue<ConversationEventsWrapper> events;

  private final SummaryRuntimeService summaryRuntimeService;

  @Getter
  @Setter
  private long lastEventTime;
  private boolean isProcess;
  private int eventCount;

  public boolean isProcess() {
    return isProcess;
  }

  public void setProcess(boolean process) {
    isProcess = process;
  }

  /**
   * Signals the processing loop to stop accepting new work and drain any buffered events, then
   * interrupts the idle {@code poll} so graceful shutdown does not have to wait for the poll
   * timeout to elapse. Safe to call from the shutdown thread.
   */
  public void shutdown() {
    log.info("shutting down conversation event queue: draining {} buffered events",
        this.events.size());
    this.isProcess = false;
    this.interrupt();
  }

  public ConversationEventQueue(SummaryRuntimeService summaryRuntimeService) {
    this.events = new LinkedBlockingQueue<>();
    this.summaryRuntimeService = summaryRuntimeService;
    this.lastEventTime = DateUtil.TIME.millis();
    this.isProcess = true;
    this.eventCount = 0;
  }

  public void addEvent(final ConversationEventsWrapper event) {
    try {
      this.events.put(event);
    } catch (Exception e) {
      log.error("exception at adding event to queue: conversation id: {}  error: ",
          event.getConversationId(), e);
    }
  }

  public int getQueueSize() {
    return this.events.size();
  }

  @Override
  public void run() {
    try {
      log.info("async event processing started");
      while (this.isProcess() || !events.isEmpty()) {
        try {
          // While running, block for new events; once shutting down, drain without blocking so
          // the loop exits as soon as the queue is empty.
          ConversationEventsWrapper event =
              this.isProcess() ? events.poll(30, TimeUnit.SECONDS) : events.poll();
          if (event != null) {
            ContextSetter.setContextWithSpan("uassist-summary", "conversation-events-queue",
                event.getConversationId(), event.getTenantId(), event.getEnvironment());
            this.eventCount++;
            this.lastEventTime = DateUtil.TIME.millis();
            log.info("handling conversation event in queue: conversationId: {}, queue size: {}",
                event.getConversationId(), this.events.size());
            if (event.getEventType()
                .equals(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT)) {
              this.summaryRuntimeService.handleConversationEvent(
                  event.getConversationEventEnvelope(), DateUtil.TIME.millis());
            } else if (event.getEventType()
                .equals(ConversationEventsWrapper.RequestType.AGENT_UPDATE)) {
              this.summaryRuntimeService.processAgentUpdates(event.getAgentUpdateRequest(),
                  new ArrayList<>());
            }
          }
        } catch (InterruptedException e) {
          // shutdown() interrupts the idle poll; re-check the loop condition and drain what remains
          log.info("conversation event queue interrupted; draining remaining events: queue size: {}",
              this.events.size());
          Thread.currentThread().interrupt();
        } catch (Exception e) {
          log.error("exception handling conversation event in queue: error: ", e);
        }
      }
      log.info("blocking queue process stopped:, isprocess: {},queue size: {}, event size: {}",
          this.isProcess, this.getQueueSize(), this.eventCount);
    } finally {
      ContextSetter.clearContext();
    }
  }
}

