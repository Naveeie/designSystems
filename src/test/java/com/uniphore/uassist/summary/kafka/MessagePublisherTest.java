package com.uniphore.uassist.summary.kafka;

import com.uniphore.conversationmetric.v1.ConversationMetric;
import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.environments.v1.DeploymentTask;
import com.uniphore.environments.v1.ExportResponse;
import com.uniphore.environments.v1.ImportResponse;
import com.uniphore.environments.v1.RollbackResponse;
import com.uniphore.summary.v1.Summary;
import com.uniphore.uassist.summary.util.KafkaUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import static com.uniphore.uassist.summary.conf.DataStreamConfig.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

class MessagePublisherTest {

  @InjectMocks
  private MessagePublisher messagePublisher;

  @Mock
  private StreamBridge streamBridge;

  @Mock
  private KafkaUtil kafkaUtil;

  @Captor
  private ArgumentCaptor<Message<byte[]>> messageCaptor;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testMetricReport_success() {
    ConversationMetric metric =
        ConversationMetric.newBuilder().setTenantId("tenant123").setConversationId("conv123")
            .build();

    messagePublisher.metricReport(metric, "SENTIMENT");

    verify(streamBridge).send(eq("metricReportEvents-out-0"), any());
  }

  @Test
  void testMetricReport_exception() {
    ConversationMetric metric =
        ConversationMetric.newBuilder().setTenantId("tenant123").setConversationId("conv123")
            .build();

    doThrow(new RuntimeException("mock failure")).when(streamBridge)
        .send(eq("metric-report-events"), any());

    messagePublisher.metricReport(metric, "EXCEPTION_METRIC");
  }

  @Test
  void testSummaryUpdate_success() {
    Summary summary = Summary.newBuilder().setTenantId("tenant123").setConversationId("conv123")
        .setEnvironment("env").setExperienceId("exp-001").build();

    messagePublisher.summaryUpdate(summary, "SUMMARY_CREATED");

    verify(streamBridge).send(eq("summaryUpdate-out-0"), any());
  }

  @Test
  void testSummaryUpdate_exception() {
    Summary summary = Summary.newBuilder().setTenantId("tenant123").setConversationId("conv123")
        .setEnvironment("env").setExperienceId("exp-001").build();

    doThrow(new RuntimeException("mock failure")).when(streamBridge)
        .send(eq("summary-update"), any());

    messagePublisher.summaryUpdate(summary, "SUMMARY_EXCEPTION");
  }

  @Test
  void testSummaryReviewed_success() {
    Summary summary = Summary.newBuilder().setConversationId("conv123").build();

    messagePublisher.summaryReviewed(summary);

    verify(streamBridge).send(eq("summaryReviewed-out-0"), any());
  }

  @Test
  void testSummaryReviewed_exception() {
    Summary summary = Summary.newBuilder().setConversationId("conv123").build();

    doThrow(new RuntimeException("mock failure")).when(streamBridge)
        .send(eq("summary-reviewed"), any());

    messagePublisher.summaryReviewed(summary);
  }

  @Test
  void testConversationEventsOut_success() {
    ConversationEventEnvelope envelope =
        ConversationEventEnvelope.newBuilder().setTenantId("tenant123").setConversationId("conv123")
            .build();

    messagePublisher.conversationEventsOut(envelope);

    verify(streamBridge).send(eq("conversationEvents-out-0"), any());
  }

  @Test
  void testConversationEventsOut_exception() {
    ConversationEventEnvelope envelope =
        ConversationEventEnvelope.newBuilder().setTenantId("tenant123").setConversationId("conv123")
            .build();

    doThrow(new RuntimeException("mock failure")).when(streamBridge)
        .send(eq("conversation-events-out"), any());

    messagePublisher.conversationEventsOut(envelope);
  }

  @Test
  void testDeploymentEvents_success() {
    DeploymentTask task =
        DeploymentTask.newBuilder().setTenantId("tenant123").setDeploymentId("deploy001")
            .setSourceEnvironment("dev").setTargetEnvironment("prod").build();

    messagePublisher.deploymentEvents(task);

    verify(streamBridge).send(eq("deploymentRequest-out-0"), any());
  }

  @Test
  void testDeploymentEvents_exception() {
    DeploymentTask task =
        DeploymentTask.newBuilder().setTenantId("tenant123").setDeploymentId("deploy001")
            .setSourceEnvironment("dev").setTargetEnvironment("prod").build();

    doThrow(new RuntimeException("mock failure")).when(streamBridge)
        .send(eq("deployment-request"), any());

    messagePublisher.deploymentEvents(task);
  }

  @Test
  void testPublishExportSummary_success() throws Exception {
    ExportResponse response = ExportResponse.newBuilder().setRequestId("req123").build();

    when(kafkaUtil.getPartitionCount("environments.export-response")).thenReturn(5);
    when(kafkaUtil.getPartition("req123", 5)).thenReturn(2);

    messagePublisher.publishExportSummary(response);

    verify(streamBridge).send(eq(OUTPUT_EXPORT_SUMMARY_RESPONSE), messageCaptor.capture());
    Message<byte[]> message = messageCaptor.getValue();
    assertEquals("req123", new String(message.getHeaders().get(KafkaHeaders.KEY, String.class)));
    assertEquals(2, message.getHeaders().get(KafkaHeaders.PARTITION));
  }

  @Test
  void testPublishImportSummary_success() throws Exception {
    ImportResponse response = ImportResponse.newBuilder().setRequestId("req456").build();

    when(kafkaUtil.getPartitionCount("environments.import-response")).thenReturn(5);
    when(kafkaUtil.getPartition("req456", 5)).thenReturn(3);

    messagePublisher.publishImportSummary(response);

    verify(streamBridge).send(eq(OUTPUT_IMPORT_SUMMARY_RESPONSE), messageCaptor.capture());
    Message<byte[]> message = messageCaptor.getValue();
    assertEquals("req456", new String(message.getHeaders().get(KafkaHeaders.KEY, String.class)));
    assertEquals(3, message.getHeaders().get(KafkaHeaders.PARTITION));
  }

  @Test
  void testPublishRollbackSummary_success() throws Exception {
    RollbackResponse response = RollbackResponse.newBuilder().setRequestId("req789").build();

    when(kafkaUtil.getPartitionCount("environments.rollback-response")).thenReturn(5);
    when(kafkaUtil.getPartition("req789", 5)).thenReturn(1);

    messagePublisher.publishRollbackSummary(response);

    verify(streamBridge).send(eq(OUTPUT_ROLLBACK_SUMMARY_RESPONSE), messageCaptor.capture());
    Message<byte[]> message = messageCaptor.getValue();
    assertEquals("req789", new String(message.getHeaders().get(KafkaHeaders.KEY, String.class)));
    assertEquals(1, message.getHeaders().get(KafkaHeaders.PARTITION));
  }

  @Test
  void testPublishExportSummary_exception() throws Exception {
    ExportResponse response = ExportResponse.newBuilder().setRequestId("req123").build();

    when(kafkaUtil.getPartitionCount("environments.export-response")).thenThrow(
        new RuntimeException("Kafka error"));

    messagePublisher.publishExportSummary(response);

    verify(streamBridge, never()).send(eq("exportSummaryResponse-out-0"), any());
  }

  @Test
  void testPublishImportSummary_exception() throws Exception {
    ImportResponse response = ImportResponse.newBuilder().setRequestId("req456").build();

    when(kafkaUtil.getPartitionCount("environments.import-response")).thenThrow(
        new RuntimeException("Kafka error"));

    messagePublisher.publishImportSummary(response);

    verify(streamBridge, never()).send(eq("importSummaryResponse-out-0"), any());
  }

  @Test
  void testPublishRollbackSummary_exception() throws Exception {
    RollbackResponse response = RollbackResponse.newBuilder().setRequestId("req789").build();

    when(kafkaUtil.getPartitionCount("environments.rollback-response")).thenThrow(
        new RuntimeException("Kafka error"));

    messagePublisher.publishRollbackSummary(response);

    verify(streamBridge, never()).send(eq("rollbackSummaryResponse-out-0"), any());
  }
}
