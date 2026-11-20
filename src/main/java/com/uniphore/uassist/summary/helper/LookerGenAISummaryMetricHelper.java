package com.uniphore.uassist.summary.helper;

import com.google.protobuf.Timestamp;
import com.uniphore.conversationmetric.v1.*;
import com.uniphore.ri.common.tenant.TenantPojo;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.kafka.MessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static com.uniphore.uassist.summary.common.Constants.SUBMIT_TYPE;

@Slf4j
@Component
public class LookerGenAISummaryMetricHelper {

  @Autowired
  private MessagePublisher messagePublisher;

  public void pushGenAICounterMetric(String metricName, String convId, String expId, String reasonType, MetricScope scope, String tag) {
    if (StringUtils.isBlank(metricName) || StringUtils.isBlank(convId) || StringUtils.isBlank(expId)) {
      log.debug("MetricName, ConversationId, or ExperienceId is empty");
      return;
    }

    if (scope == MetricScope.METRIC_SCOPE_INVALID) {
      log.debug("Invalid MetricScope for conversationId: {}", convId);
      return;
    }

    Counter counter = Counter.newBuilder().setType(reasonType).putAdditionalTags(SUBMIT_TYPE,StringUtils.defaultIfBlank(tag, "unknown")).build();
    Metric metric = buildMetric(metricName, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY, counter, null);
    ConversationMetric genAICounter = buildConversationMetric(convId, expId, metric);

    log.debug("Publishing counter metric - name: {}, data: {}", metricName, genAICounter);
    messagePublisher.metricReport(genAICounter, metricName);
  }

  public void pushGenAILatencyMetricForSummaryGenerate(String metricName, String convId, String expId, long latency, MetricScope scope) {
    if (!isValidMetric(metricName, convId, scope,expId)) {
      return;
    }

    Latency latencyMetric = Latency.newBuilder().setLatency(latency).build();
    Metric metric = buildMetric(metricName, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY, null, latencyMetric);
    ConversationMetric conversationMetric = buildConversationMetric(convId, expId, metric);

    log.debug("Publishing latency metric - name: {}, data: {}", metricName, conversationMetric);
    messagePublisher.metricReport(conversationMetric, metricName);
  }

  private boolean isValidMetric(String metricName, String convId, MetricScope scope,String expId) {
    if (StringUtils.isBlank(metricName)|| StringUtils.isBlank(convId) || StringUtils.isBlank(expId)) {
      log.debug("MetricName, ConversationId, or ExperienceId is empty for latency{}", convId);
      return false;
    }
    return scope != MetricScope.METRIC_SCOPE_INVALID;
  }

  private Metric buildMetric(String metricName, MetricScope scope, Counter counter, Latency latency) {
    Metric.Builder builder = Metric.newBuilder()
        .setName(metricName)
        .setMetricScope(scope);

    if (counter != null) {
      builder.setCounterMetric(counter);
    }
    if (latency != null) {
      builder.setLatencyMetric(latency);
    }

    return builder.build();
  }

  private ConversationMetric buildConversationMetric(String convId, String expId, Metric metric) {
    TenantPojo tenantPojo = TenantUtils.getTenantDetails();
    Instant current = Instant.now();
    Timestamp timestamp = Timestamp.newBuilder()
        .setSeconds(current.getEpochSecond())
        .setNanos(current.getNano())
        .build();

    return ConversationMetric.newBuilder()
        .setConversationId(convId)
        .setTenantId(tenantPojo.getTenantId())
        .setEnvironment(tenantPojo.getEnvironment())
        .setExperienceId(expId)
        .addMetric(metric)
        .setTimestamp(timestamp)
        .build();
  }
  
}
