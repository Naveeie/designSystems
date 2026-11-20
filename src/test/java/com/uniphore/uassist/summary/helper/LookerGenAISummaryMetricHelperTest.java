package com.uniphore.uassist.summary.helper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.google.protobuf.Timestamp;
import com.uniphore.conversationmetric.v1.ConversationMetric;
import com.uniphore.conversationmetric.v1.MetricScope;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.kafka.MessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static com.uniphore.uassist.summary.utils.TestConstant.USERNAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LookerGenAISummaryMetricHelperTest {

  @Mock
  private MessagePublisher messagePublisher;

  @Mock
  private Logger log;

  @InjectMocks
  private LookerGenAISummaryMetricHelper metricHelper;

  @Mock
  private Appender<ILoggingEvent> mockAppender;

  private static final String METRIC_NAME = "TestMetric";
  private static final String CONVERSATION_ID = "conv123";
  private static final String EXPERIENCE_ID = "exp123";

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    TenantContext.setTenantContext("3e37631c-78ac-4137-8415-72dcd90b19fd", USERNAME);
    Logger logger = (Logger) LoggerFactory.getLogger(LookerGenAISummaryMetricHelper.class);
    logger.addAppender(mockAppender);
  }

  @Test
  void testInvalidTimestampValues_shouldStillBuildTimestamp() {
    Instant invalidInstant = Instant.ofEpochSecond(-1, -500);
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(invalidInstant.getEpochSecond())
        .setNanos(invalidInstant.getNano()).build();

    assertNotNull(timestamp);
    assertTrue(timestamp.getSeconds() < 0);
  }

  @Test
  void testInvalidScope_shouldNotPublishMetric() {
    metricHelper.pushGenAICounterMetric(METRIC_NAME, CONVERSATION_ID, EXPERIENCE_ID, "REASON",
        MetricScope.METRIC_SCOPE_INVALID,"tag");

    verify(messagePublisher, never()).metricReport(any(), any());
  }

  @Test
  void testInvalidInputs_shouldNotPublishMetric() {
    metricHelper.pushGenAICounterMetric(null, CONVERSATION_ID, EXPERIENCE_ID, "REASON",
        MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY,"tag");
    metricHelper.pushGenAICounterMetric("", CONVERSATION_ID, EXPERIENCE_ID, "REASON",
        MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY,"tag");
    metricHelper.pushGenAICounterMetric(METRIC_NAME, "", EXPERIENCE_ID, "REASON",
        MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY,"tag");
    metricHelper.pushGenAICounterMetric(METRIC_NAME, CONVERSATION_ID, "", "REASON",
        MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY,"tag");

    verify(messagePublisher, never()).metricReport(any(), any());
  }

  @Test
  void testPushGenAICounterMetric_shouldPublishMetricSuccessfully() {
    metricHelper.pushGenAICounterMetric(METRIC_NAME, CONVERSATION_ID, EXPERIENCE_ID, "REASON",
        MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY,"tag");

    ArgumentCaptor<ConversationMetric> metricCaptor =
        ArgumentCaptor.forClass(ConversationMetric.class);
    verify(messagePublisher).metricReport(metricCaptor.capture(), eq(METRIC_NAME));

    ConversationMetric captured = metricCaptor.getValue();
    assertEquals(CONVERSATION_ID, captured.getConversationId());
    assertEquals(EXPERIENCE_ID, captured.getExperienceId());
    assertEquals(METRIC_NAME, captured.getMetricList().get(0).getName());
    assertTrue(captured.getMetricList().get(0).hasCounterMetric());
    assertTrue(captured.getTimestamp().getSeconds() > 0);
  }

  @Test
  void testPushGenAILatencyMetric_validInput_shouldPublishSuccessfully() {
    metricHelper.pushGenAILatencyMetricForSummaryGenerate(METRIC_NAME, CONVERSATION_ID,
        EXPERIENCE_ID, 150L, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY);

    ArgumentCaptor<ConversationMetric> metricCaptor =
        ArgumentCaptor.forClass(ConversationMetric.class);
    verify(messagePublisher).metricReport(metricCaptor.capture(), eq(METRIC_NAME));

    ConversationMetric captured = metricCaptor.getValue();
    assertNotNull(captured);
    assertEquals(CONVERSATION_ID, captured.getConversationId());
    assertTrue(captured.getMetricList().get(0).hasLatencyMetric());
  }

  @Test
  void testPushGenAILatencyMetric_invalidMetricName_shouldSkipPublishing() {
    metricHelper.pushGenAILatencyMetricForSummaryGenerate("", CONVERSATION_ID, EXPERIENCE_ID, 120L,
        MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY);
    verify(messagePublisher, never()).metricReport(any(), any());
  }

  @Test
  void testPushGenAILatencyMetric_invalidScope_shouldSkipPublishing() {
    metricHelper.pushGenAILatencyMetricForSummaryGenerate(METRIC_NAME, CONVERSATION_ID,
        EXPERIENCE_ID, 120L, MetricScope.METRIC_SCOPE_INVALID);
  }

  @Test
  void testPushGenAILatencyMetric_zeroLatency_shouldStillPublish() {
    metricHelper.pushGenAILatencyMetricForSummaryGenerate(METRIC_NAME, CONVERSATION_ID,
        EXPERIENCE_ID, 0L, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY);

    ArgumentCaptor<ConversationMetric> metricCaptor =
        ArgumentCaptor.forClass(ConversationMetric.class);
    verify(messagePublisher).metricReport(metricCaptor.capture(), eq(METRIC_NAME));
    assertEquals(0L,
        metricCaptor.getValue().getMetricList().get(0).getLatencyMetric().getLatency());
  }
}
