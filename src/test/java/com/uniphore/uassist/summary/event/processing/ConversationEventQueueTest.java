package com.uniphore.uassist.summary.event.processing;

import com.uniphore.uassist.summary.event.pojo.ConversationEventsWrapper;
import com.uniphore.uassist.summary.service.SummaryRuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.BlockingQueue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConversationEventQueueTest {

  @Mock
  private SummaryRuntimeService summaryRuntimeService;

  @InjectMocks
  private ConversationEventQueue queue;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    queue = new ConversationEventQueue(summaryRuntimeService);
  }

  @Test
  void testAddEvent() throws InterruptedException {
    ConversationEventsWrapper event = mock(ConversationEventsWrapper.class);
    queue.addEvent(event);
    BlockingQueue<ConversationEventsWrapper> queueField =
        (BlockingQueue<ConversationEventsWrapper>) getPrivateField(queue, "events");
    assert queueField.size() == 1;
    assert queueField.contains(event);
  }

  @Test
  void testRunWithEvents() throws InterruptedException {
    ConversationEventsWrapper event = mock(ConversationEventsWrapper.class);
    when(event.getEventType()).thenReturn(ConversationEventsWrapper.RequestType.CONVERSATION_EVENT);
    queue.addEvent(event);
    Thread thread = new Thread(queue);
    thread.start();
    Thread.sleep(50);
    queue.setProcess(false);
    thread.join();
    verify(summaryRuntimeService).handleConversationEvent(any(), anyLong());
    assert queue.getQueueSize() == 0;
  }

  @Test
  void testRunWithExceptionHandling() throws InterruptedException {
    ConversationEventsWrapper event = mock(ConversationEventsWrapper.class);
    when(event.getEventType()).thenReturn(ConversationEventsWrapper.RequestType.AGENT_UPDATE);
    doThrow(new RuntimeException("Test exception")).when(summaryRuntimeService)
        .processAgentUpdates(any(), any());
    queue.addEvent(event);
    Thread thread = new Thread(queue);
    thread.start();
    Thread.sleep(50);
    queue.setProcess(false);
    thread.join();
    verify(summaryRuntimeService).processAgentUpdates(any(), any());
    assert queue.getQueueSize() == 0;
  }

  private Object getPrivateField(Object obj, String fieldName) {
    try {
      java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(obj);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
