package com.uniphore.uassist.summary.kafka;

import com.uniphore.conversationmetric.v1.ConversationMetric;
import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.conversations.v1.ConversationReviewed;
import com.uniphore.environments.v1.DeploymentTask;
import com.uniphore.environments.v1.ExportResponse;
import com.uniphore.environments.v1.ImportResponse;
import com.uniphore.environments.v1.RollbackResponse;
import com.uniphore.summary.v1.Summary;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.util.KafkaUtil;
import jakarta.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static com.uniphore.uassist.summary.conf.DataStreamConfig.*;

@Slf4j
@Component
public class MessagePublisher {
  @Autowired
  private StreamBridge streamBridge;

  @Autowired
  private KafkaUtil util;

  private static String TENANTID_CONVID_STRING = "%s_%s";

  public void metricReport(ConversationMetric metric, String metricName) {

    try {
      log.info("ConversationMetric event Published metricName : {} data : {}", metricName,
          metric.toString());

      String key =
          String.format(TENANTID_CONVID_STRING, metric.getTenantId(), metric.getConversationId());

      streamBridge.send(METRIC_REPORT_EVENTS,
          MessageBuilder.withPayload(metric.toByteArray()).setHeader(KafkaHeaders.KEY, key)
              .build());

    } catch (Exception e) {
      log.error("Exception while publishing metricReport: {}", metricName, e);
    }
  }

  public void summaryUpdate(Summary summary, String event) {
    log.info("SummaryUpdate event Published event: {} summary : {}", event, summary.toString());
    try {
      streamBridge.send(OUTPUT_SUMMARY_UPDATE, MessageBuilder.withPayload(summary.toByteArray())
          .setHeader(KafkaHeaders.KEY, summary.getConversationId())
          .setHeader(Constants.TENANT_ID, summary.getTenantId())
          .setHeader(Constants.META_KEY_ENVIRONMENT, summary.getEnvironment())
          .setHeader(Constants.ExpId, summary.getExperienceId()).build());

    } catch (Exception e) {
      log.error("Exception while publishing summaryUpdate: {}", summary, e);
    }
  }

  public void summaryReviewed(Summary summary) {
    try {
      log.info("SummaryReviewed event Published summary : {}", summary.toString());
      streamBridge.send(OUTPUT_SUMMARY_REVIEWED, MessageBuilder.withPayload(summary.toByteArray())
          .setHeader(KafkaHeaders.KEY, summary.getConversationId()).build());

    } catch (Exception e) {
      log.error("Exception while publishing summaryReviewed: {}", summary, e);
    }
  }

  public void conversationReviewed(ConversationReviewed request) {
    try {
      log.info("Publishing data to conversation-reviewed topic: {}", request.toString());
        streamBridge.send(OUTPUT_CONVERSATION_REVIEWED,
            MessageBuilder.withPayload(request.toByteArray())
                .setHeader(KafkaHeaders.KEY, request.getSummaryReviewed().getConversationId())
                .build());
        log.info("Published data to conversation-reviewed topic successfully");
    } catch (Exception e) {
      log.error("Exception while publishing conversationReviewedOut: {}", request,
          e);
    }
  }

  public void conversationEventsOut(ConversationEventEnvelope conversationEventEnvelope) {
    try {
      log.info("conversationEvents Published ConversationEvent : {}",
          conversationEventEnvelope.toString());
      streamBridge.send(OUTPUT_CONVERSATION_EVENTS,
          MessageBuilder.withPayload(conversationEventEnvelope.toByteArray())
              .setHeader(KafkaHeaders.KEY, conversationEventEnvelope.getConversationId()).build());

    } catch (Exception e) {
      log.error("Exception while publishing conversationEventsOut: {}", conversationEventEnvelope,
          e);
    }
  }

  /* This been used only for mock testing */
  public void deploymentEvents(DeploymentTask task) {
    try {
      log.info(
          "deployment events Published TenantId : {}, sourceEnvironment : {} targetEnvironment : {} [{}]",
          task.getTenantId(), task.getSourceEnvironment(), task.getTargetEnvironment(),
          task.toString());
      streamBridge.send(OUTPUT_DEPLOYMENT_REQUEST, MessageBuilder.withPayload(task.toByteArray())
          .setHeader(KafkaHeaders.KEY, task.getDeploymentId()).build());

    } catch (Exception e) {
      log.error("Exception while publishing deploymentEvents: {}", task, e);
    }
  }

  public void publishExportSummary(final ExportResponse response) {
    log.info("Publishing export Response for requestId : {}", response.getRequestId());
    log.debug("Publishing export Response for requestId : {} and data {}", response.getRequestId(),
        response);
    try {
      log.info(" RequestId : {} and bytes is:{}", response.getRequestId(),
          DatatypeConverter.printHexBinary(
              response.getRequestId().getBytes(StandardCharsets.UTF_8)));
      int partitionCount = util.getPartitionCount("environments.export-response");
      int partition = util.getPartition(response.getRequestId(), partitionCount);
      log.debug("Sending export response for requestId: {}, partition: {} and PartitionCount:{}",
          response.getRequestId(), partition, partitionCount);
      streamBridge.send(OUTPUT_EXPORT_SUMMARY_RESPONSE,
          MessageBuilder.withPayload(response.toByteArray())
              .setHeader(KafkaHeaders.KEY, response.getRequestId())
              .setHeader(KafkaHeaders.PARTITION, partition).build());
    } catch (Exception e) {
      log.error("Exception while publishing export summary: {}", response, e);
    }
  }

  public void publishImportSummary(final ImportResponse response) {
    log.info("Publishing import Response for requestId : {}", response.getRequestId());
    try {
      int partitionCount = util.getPartitionCount("environments.import-response");
      int partition = util.getPartition(response.getRequestId(), partitionCount);
      log.debug("Sending import response for requestId: {}, partition: {} and PartitionCount:{}",
          response.getRequestId(), partition, partitionCount);
      streamBridge.send(OUTPUT_IMPORT_SUMMARY_RESPONSE,
          MessageBuilder.withPayload(response.toByteArray())
              .setHeader(KafkaHeaders.KEY, response.getRequestId())
              .setHeader(KafkaHeaders.PARTITION, partition).build());
    } catch (Exception e) {
      log.error("Exception while publishing import summary: {}", response, e);
    }
  }

  public void publishRollbackSummary(final RollbackResponse response) {
    log.info("Publishing rollback Response for requestId : {}", response.getRequestId());
    try {
      int partitionCount = util.getPartitionCount("environments.rollback-response");
      int partition = util.getPartition(response.getRequestId(), partitionCount);
      log.debug("Sending rollback response for requestId: {}, partition: {} and PartitionCount:{}",
          response.getRequestId(), partition, partitionCount);
      streamBridge.send(OUTPUT_ROLLBACK_SUMMARY_RESPONSE,
          MessageBuilder.withPayload(response.toByteArray())
              .setHeader(KafkaHeaders.KEY, response.getRequestId())
              .setHeader(KafkaHeaders.PARTITION, partition).build());
    } catch (Exception e) {
      log.error("Exception while publishing rollback summary: {}", response, e);
    }
  }
}
