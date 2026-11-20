package com.uniphore.uassist.summary.service;

import static com.uniphore.featureflag.LaunchDarklyConstants.FF_CONTEXT_KIND;
import static com.uniphore.featureflag.LaunchDarklyConstants.UASSIST_STREAM_SUMMARY_FF;
import static com.uniphore.uassist.summary.util.GenAIConstants.PARAGRAPH;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uniphore.llminference.v1.CreateGenerativeSummaryRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;
import com.uniphore.conversationmetric.v1.MetricScope;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.llminference.v1.CreateGenerativeSummaryResponse;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.platform.telemetry.context.ContextSetter;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.cache.GenAISummaryCache;
import com.uniphore.uassist.summary.cache.GenAISummaryConfigCacheHelper;
import com.uniphore.uassist.summary.cache.SummaryCacheHelper;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.CustomMetricsPublisher;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryUpdateEventLog;
import com.uniphore.uassist.summary.dao.documents.TimeStampDoc;
import com.uniphore.uassist.summary.execption.GenAiException;
import com.uniphore.uassist.summary.helper.LookerGenAISummaryMetricHelper;
import com.uniphore.uassist.summary.model.ProtoConverter;
import com.uniphore.uassist.summary.util.DateUtil;
import com.uniphore.uassist.summary.util.GenAiSummaryHelper;
import com.uniphore.uassist.summary.util.GrpcClientHelper;
import com.uniphore.uassist.summary.util.SummaryHelper;
import io.grpc.Status;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GenAISummaryService {

  @Autowired
  private SummaryHelper summaryHelper;
  @Autowired
  private GrpcClientHelper grpcClientHelper;

  @Autowired
  private CustomMetricsPublisher customMetricsPublisher;
  @Autowired
  private GenAISummaryConfigCacheHelper genAiSummaryCacheHelper;
  @Autowired
  private SummaryCacheHelper summaryCacheHelper;
  @Autowired
  private LookerGenAISummaryMetricHelper metricHelper;

  @Autowired
  private LaunchDarklyHelper ldHelper;

  @Autowired
  private GenAiSummaryHelper genAiSummaryHelper;

  public SummaryDoc createGenAiSummary(final String conversationId, final String tenantId,
      Optional<String> experienceIdOpt, boolean publish, final boolean formatEnable,
      final long conversationEndTime, final String callType) {
    return createGenAiSummary(conversationId, tenantId, experienceIdOpt, publish, formatEnable,
        conversationEndTime, callType, null);
  }

  public SummaryDoc createGenAiSummary(final String conversationId, final String tenantId,
      Optional<String> experienceIdOpt, boolean publish, final boolean formatEnable,
      final long conversationEndTime, final String callType, final Long telephonyCallEndTimeMillis) {
    ContextSetter.setContextWithSpan("uassist-summary", "create-gen-ai", conversationId, tenantId,
        TenantUtils.getTenantDetails().getEnvironment());
    boolean enableSummaryBuilder =
        this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
            FF_CONTEXT_KIND);
    boolean enableEntityEnhancement =
        this.ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS,
            FF_CONTEXT_KIND);
    boolean enableSummaryStream =
        this.ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND);

    long eventTime = DateUtil.TIME.millis();
    SummaryDoc summaryDoc = null;
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();

    if (experienceIdOpt.isEmpty()) {
      log.error("createGenAiSummary:: experienceIdOpt is empty");
      publishGenAiSummaryFailureMetric(conversationId, Optional.empty(),
          new RuntimeException("createGenAiSummary:: experienceIdOpt is empty"),
          Constants.RETURN_CODE_400);
      return summaryDoc;
    }

    try {
      Optional<SummaryDoc> summaryDocById = this.summaryHelper.getSummaryDocById(conversationId);
      if (summaryDocById.isPresent()) {
        summaryDoc = summaryDocById.get();
      } else {
        publishGenAiSummaryFailureMetric(conversationId, Optional.empty(),
            new RuntimeException("createGenAiSummary:: experienceIdOpt is empty"),
            Constants.RETURN_CODE_400);
        return summaryDoc;
      }
      // 1. Check if the summary has already been requested (lock check)
      boolean summaryGenerated =
          summaryCacheHelper.acquireGenAiSummaryGenerationLock(conversationId);
      log.debug("CreateGenAiSummary:: conversationId: {} summary generated lock: {}",
          conversationId, summaryGenerated);

      if (summaryGenerated) {
        // 2. If the lock is true, check in the DB with retries
        summaryDoc = checkSummaryInDbWithRetries(conversationId);
        if (summaryDoc != null) {
          return summaryDoc;
        }
      } else {
        log.info("createGenAiSummary request: conversationId: {}, summary doc: {}", conversationId,
            summaryDoc);
        // 3. If the lock is false, generate the GenAI summary by calling the LLM
        //3.1 check if it is short call
        Optional<GenAISummaryCache> genAiConfigCacheOpt =
            this.genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(experienceIdOpt.get());
        GenAISummaryCache config = null;
        if (genAiConfigCacheOpt.isPresent()) {
          config = genAiConfigCacheOpt.get();
        } else {
          //since config not found fail the gen ai summary
          publishGenAiSummaryFailureMetric(conversationId, experienceIdOpt,
              new RuntimeException("createGenAiSummary:: summaryDoc is empty"),
              Constants.RETURN_CODE_400);
          return summaryDoc;
        }
        //3.2 if it is not short call generate summary
        boolean shortCallEnabled = config.isShortCallEnabled();
        boolean isShortCall = false;
        if (shortCallEnabled) {
          long shortCallEvalTime =
              telephonyCallEndTimeMillis != null ? telephonyCallEndTimeMillis : conversationEndTime;
          isShortCall = isShortCall(summaryDoc, config, shortCallEvalTime);
          log.info("short call status convId: {} short call: {}", conversationId, isShortCall);
          if (isShortCall) {
            GenAiSummaryDoc genAiSummary =
                GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
            genAiSummary.setSections(
                generateShortCallSections(config.isFormatEnabled(), config.getShortCallConfig().getMessage()));
            summaryDoc.setGenAiSummary(genAiSummary);
            summaryDoc.setGenAiGeneratedSummary(genAiSummary);
            publishGenAiSummaryFailureMetric(conversationId, experienceIdOpt,
                new RuntimeException("createGenAiSummary:: conversation is short call"),
                Constants.RETURN_CODE_204);
            log.warn("LIVE CALL FLOW (Summary): Call is too short for GenAI summary generation for conversationId: {}", conversationId);
          } else {
            GenAiSummaryDoc genAiSummaryDoc =
                processGenAiSummary(conversationId, tenantId, summaryDoc, conversationEndTime,
                    config, callType);
            summaryDoc.setGenAiSummary(genAiSummaryDoc);
            summaryDoc.setGenAiGeneratedSummary(genAiSummaryDoc);
          }
        } else {
          GenAiSummaryDoc genAiSummaryDoc =
              processGenAiSummary(conversationId, tenantId, summaryDoc, conversationEndTime, config,
                  callType);
          summaryDoc.setGenAiSummary(genAiSummaryDoc);
          summaryDoc.setGenAiGeneratedSummary(genAiSummaryDoc);
        }

        //3.3 publish the gen ai summary based on conditions
        boolean isTemplatePresent = StringUtils.isNotBlank(summaryDoc.getTemplateId());
        publish = !(enableSummaryBuilder && isTemplatePresent) && (enableEntityEnhancement
            || !enableSummaryStream);

        publishGenAiSummary(conversationId, publish, summaryDoc, summaryDoc.getGenAiSummary(),
            eventTime, eventData);
        if (!isShortCall) {
          pushGenAiMetrics(conversationId, experienceIdOpt.get(), conversationEndTime);
        }
        return summaryDoc;
      }
      // 4. Whatever the output comes back after above flow, return the summary.
      log.debug("Final summaryDoc={}, publish={}", summaryDoc, publish);
      return summaryDoc;
    } catch (InterruptedException e) {
      log.error("InterruptedException at creating genAI summary ", e);
      this.publishGenAiSummaryFailureMetric(conversationId, experienceIdOpt, e,
          Constants.RETURN_CODE_500);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error(" Exception occurred while creating GenAI summary. conversationId={}",
          conversationId, e);
      if (e instanceof GenAiException) {
        this.publishGenAiSummaryFailureMetric(conversationId, experienceIdOpt, e,
            Constants.RETURN_CODE_500);
      } else if (e.getMessage().contains("transcript has length 0")) {
        this.publishGenAiSummaryFailureMetric(conversationId, experienceIdOpt, e,
            Constants.RETURN_CODE_404);
      } else {
        this.publishGenAiSummaryFailureMetric(conversationId, experienceIdOpt, e,
            Constants.RETURN_CODE_500);
      }

    }
    return summaryDoc;
  }

  private boolean isShortCall(SummaryDoc summaryDoc, GenAISummaryCache config,
      long conversationEndTime) {
    String durationType = config.getShortCallConfig().getDurationType();

    double callDurationSeconds = calculateCallDuration(summaryDoc, conversationEndTime, durationType);
    long configDurationThreshold = config.getShortCallConfig().getDuration();
    int configTurnThreshold = config.getShortCallConfig().getTurns().getThreshold();
    String configTurnChannel = config.getShortCallConfig().getTurns().getChannel();
    int totalTurns = getTotalTurns(summaryDoc.getMetadata(), configTurnChannel);
    String message = config.getShortCallConfig().getMessage();

    log.debug("totalTurns={}, thresholdTurns={}, callDuration={}, message={} ", totalTurns,
        configTurnThreshold, configDurationThreshold, message);

    if (totalTurns > configTurnThreshold && callDurationSeconds > configDurationThreshold) {
      log.debug("Call exceeds threshold. Generating GenAI Summary...");
      return false;
    } else {
      log.info(
          "Call skipped for summary generation due to low duration or turns. totalTurns={}, callDuration={}",
          totalTurns, callDurationSeconds);
      return true;
    }
  }

  private void publishGenAiSummaryFailureMetric(String conversationId,
      Optional<String> experienceIdOpt, Exception e, String errorCode) {
    experienceIdOpt.ifPresent(
        s -> metricHelper.pushGenAICounterMetric(Constants.GENAISUMMARY_METRIC, conversationId, s,
            errorCode, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY,null));
    ContextSetter.setContextWithSpanError(e);
    customMetricsPublisher.counterUpdateMap(Constants.SUMMARY_GEN_AI_PUBLISHED,
        ImmutableMap.of(Constant.TAG_TYPE, Constants.FAILED, Constant.CONVERSATION__ID,
            conversationId));
  }

  public SummaryDoc checkSummaryInDbWithRetries(String conversationId) throws InterruptedException {
    int retryCount = 0;
    int maxRetries = 45;
    while (maxRetries > retryCount) {
      Optional<SummaryDoc> dbSummaryDoc = this.summaryHelper.getSummaryDocById(conversationId);
      if (dbSummaryDoc.isPresent() && dbSummaryDoc.get().getGenAiSummary() != null
          && CollectionUtils.isNotEmpty(dbSummaryDoc.get().getGenAiSummary().getSections())) {

        log.debug("final summaryDoc for conversation:{},retryCount:{},Sections:{}", conversationId,
            retryCount, dbSummaryDoc.get().getGenAiSummary().getSections());
        return dbSummaryDoc.get();
      } else {
        retryCount++;
        Thread.sleep(1000); // wait for 1 second before retrying
      }
    }
    return null;
  }

  private void pushGenAiMetrics(String conversationId, String experienceId,
      long conversationEndTime) {
    long endTime = DateUtil.TIME.millis();
    metricHelper.pushGenAICounterMetric(Constants.GENAISUMMARY_METRIC, conversationId, experienceId,
        Constants.RETURN_CODE_200, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY,null);
    long latency = (endTime - conversationEndTime) / 1000;
    metricHelper.pushGenAILatencyMetricForSummaryGenerate(Constants.GENAI_SUMMARY_LATENCY_METRIC,
        conversationId, experienceId, latency, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY);
  }

  public GenAiSummaryDoc processGenAiSummary(String conversationId, String tenantId,
      SummaryDoc summaryDoc, long eventTime, GenAISummaryCache config, String callType)
      throws Exception {
    // 1. Handle formatting feature flag
    if (ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_FORMAT_DISABLED, FF_CONTEXT_KIND)
        && config.isFormatEnabled()) {
      log.debug(
          "processGenAiSummary:: Formatting feature flag is enabled for conversationId: {}",
          conversationId);
      config.setSummaryFormat(
          GenAISummaryCache.SummaryFormat.builder().agentSteps(PARAGRAPH).callReason(PARAGRAPH)
              .outcome(PARAGRAPH).build());
    }

    // 2. Template-based streaming summary
    if (ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)
        && StringUtils.isNotBlank(summaryDoc.getTemplateId()) && ObjectUtils.isNotEmpty(
        summaryDoc.getTemplateVersion())) {
      log.debug(
          "processGenAiSummary:: Template-based streaming summary feature flag is enabled for conversationId: {}",
          conversationId);
      return executeWithGrpcUnavailableRetry(
          () -> processStreamingSummary(conversationId, tenantId, config, summaryDoc, eventTime,
              callType),
          conversationId, "template streaming call");
    }

    // 3. Entity-enhanced non-streaming summary
    if (ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS,
        FF_CONTEXT_KIND)) {
      log.debug(
          "processGenAiSummary:: Entity-enhanced non-streaming summary feature flag is enabled for conversationId: {}",
          conversationId);
      return executeWithGrpcUnavailableRetry(
          () -> buildNonStreamingSummary(conversationId, tenantId, config, summaryDoc, callType),
          conversationId, "non-streaming call");
    }

    // 4. Generic streaming summary
    if (ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)) {
      log.debug(
          "processGenAiSummary:: Generic streaming summary feature flag is enabled for conversationId: {}",
          conversationId);
      return executeWithGrpcUnavailableRetry(
          () -> processStreamingSummary(conversationId, tenantId, config, summaryDoc, eventTime,
              callType),
          conversationId, "streaming call");
    }
    log.debug(
        "processGenAiSummary:: No feature flags enabled, proceeding with default non-streaming summary for conversationId: {}",
        conversationId);
    // 5. Default non-streaming summary
    return executeWithGrpcUnavailableRetry(
        () -> buildNonStreamingSummary(conversationId, tenantId, config, summaryDoc, callType),
        conversationId, "non-streaming call");
  }

  @FunctionalInterface
  private interface GenAiSummaryCall {
    GenAiSummaryDoc execute() throws Exception;
  }

  private GenAiSummaryDoc executeWithGrpcUnavailableRetry(GenAiSummaryCall operation,
      String conversationId, String operationName) throws Exception {
    try {
      return operation.execute();
    } catch (Exception e) {
      if (isGrpcUnavailable(e)) {
        log.warn(
            "GenAI summary {} failed with UNAVAILABLE for conversationId={}, retrying once with a fresh call",
            operationName, conversationId, e);
        return operation.execute();
      }
      throw e;
    }
  }

  private boolean isGrpcUnavailable(Throwable e) {
    return Status.fromThrowable(e).getCode() == Status.Code.UNAVAILABLE;
  }

  private GenAiSummaryDoc buildNonStreamingSummary(String conversationId, String tenantId,
      GenAISummaryCache config, SummaryDoc summaryDoc, String callType) throws Exception {
    Optional<CreateGenerativeSummaryResponse> aiSummaryOpt =
        grpcClientHelper.getGenAiSummary(conversationId, tenantId, config, summaryDoc, callType);
    return genAiSummaryHelper.formattedSummaryDoc(conversationId, aiSummaryOpt, config, summaryDoc);
  }

  private GenAiSummaryDoc processStreamingSummary(String conversationId, String tenantId,
      GenAISummaryCache config, SummaryDoc summaryDoc, long eventTime, String callType)
      throws Exception {
    Iterator<CreateGenerativeSummaryResponse> iterator =
        grpcClientHelper.getStreamingSummaryIter(conversationId, tenantId, config, summaryDoc,
            callType).orElseThrow(() -> new GenAiException("Gen AI summary stream is empty"));

    genAiSummaryHelper.processStream(iterator, conversationId, config, summaryDoc, eventTime);
    return summaryDoc.getGenAiSummary();
  }

  private List<GenAiSummaryDoc.Section> generateShortCallSections(boolean isFormatEnable,
      String message) {
    log.debug("isFormatEnable:{},message:{}", isFormatEnable, message);
    if (isFormatEnable) {
      return List.of(
          GenAiSummaryDoc.Section.builder().id(Constants.CALL_REASON).text(message).build(),
          GenAiSummaryDoc.Section.builder().id(Constants.AGENT_ACTIONS).text(message).build(),
          GenAiSummaryDoc.Section.builder().id(Constants.CALL_OUTCOME).text(message).build());
    } else {
      return List.of(GenAiSummaryDoc.Section.builder().id(Constants.SUMMARY).text(message).build());
    }
  }

  private int getTotalTurns(Map<String, Object> metadata, String channel) {
    if (metadata == null) {
      return 0;
    }
    int agentTurns = metadata.get("agent_turns") != null ? (int) metadata.get("agent_turns") : 0;
    int customerTurns =
        metadata.get("customer_turns") != null ? (int) metadata.get("customer_turns") : 0;

    int totalTurns;
    if ("agent".equalsIgnoreCase(channel)) {
      totalTurns = agentTurns;
    } else if ("customer".equalsIgnoreCase(channel)) {
      totalTurns = customerTurns;
    } else {
      totalTurns = agentTurns + customerTurns;
    }
    log.debug("Calculated totalTurns for channel '{}': {}", channel, totalTurns);
    return totalTurns;
  }

  private double calculateCallDuration(SummaryDoc summaryDoc, long endMillis, String durationType) {
    TimeStampDoc startTime = summaryDoc.getTimestamp();

    long startMillis = (startTime.getSeconds() * 1000) + (startTime.getNanos() / 1_000_000);
    long durationMillis = endMillis - startMillis;

    double duration;
    if ("minutes".equalsIgnoreCase(durationType)) {
      duration = durationMillis / (1000.0 * 60);
      log.debug("Calculated call duration in minutes: {}", duration);
    } else {
      duration = durationMillis / 1000.0;
      log.debug("Calculated call duration in seconds: {}", duration);
    }
    return duration;
  }

  private void publishGenAiSummary(String conversationId, boolean publish, SummaryDoc summaryDoc,
      GenAiSummaryDoc genAiSummary, long eventTime,
      List<SummaryUpdateEventLog.EventData> eventData) {
    summaryDoc.setGenAiSummary(genAiSummary);
    summaryDoc.setGenAiGeneratedSummary(genAiSummary);
    SummaryUpdateEventLog summaryEventLog =
        this.summaryHelper.getSummaryUpdateEventLog(conversationId, eventTime, Constants.GEN_AI,
            eventData, summaryDoc.toString(), "");
    log.debug("genAiSummary for conversationId: {} whole summary: {}", conversationId, summaryDoc);
    if (publish) {
      log.debug("Publishing GenAI summary for conversationId={} (Template FF OFF, Stream FF OFF)",
          conversationId);

      this.summaryHelper.saveAndPublish(summaryDoc,
          ProtoConverter.getSummaryProto(summaryDoc, Collections.emptyList()), summaryEventLog);
      customMetricsPublisher.updateAndLatencyMap(Constants.SUMMARY_GEN_AI_PUBLISHED, eventTime,
          ImmutableMap.of(Constant.TAG_TYPE, Constants.SUCCESS, Constant.CONVERSATION__ID,
              summaryDoc.getConversationId()));
      log.debug("summary into proto:{}",
          ProtoConverter.getSummaryProto(summaryDoc, Collections.emptyList()));
    } else {
      log.info("Saving GenAI summary without publishing for conversationId={} "
          + "(Template FF or Stream FF is ON)", conversationId);

      this.summaryHelper.saveSummaryDoc(summaryDoc);
    }

    int genAiWordCount = this.summaryHelper.getGenAiWordCount(genAiSummary);
    log.warn("GenAI summary stored in Mongo:: ConversationId - {}, TenantId - {}, GenAI Words - {}",
        summaryDoc.getConversationId(), summaryDoc.getTenantId(), genAiWordCount);

    printCallFlowGenAILog(summaryDoc, publish);
    log.debug("Deleting this conversationId:{} from redis cache.", conversationId);
    summaryHelper.removeSummaryDoc(summaryDoc.getConversationId());
  }

  private void printCallFlowGenAILog(final SummaryDoc summaryDoc, final boolean isPublish) {
    if (summaryDoc != null) {
      int genAICount = 0;
      if (summaryDoc.getGenAiSummary() != null && summaryDoc.getGenAiSummary().getSections() != null
          && !summaryDoc.getGenAiSummary().getSections().isEmpty()) {
        genAICount = summaryDoc.getGenAiSummary().getSections().stream()
            .mapToInt(t -> t.getText().split(" ").length).sum();
      }
      int intentCount = 0;
      if (summaryDoc.getIntentSummaries() != null) {
        intentCount = summaryDoc.getIntentSummaries().values().stream()
            .mapToInt(t -> t.getPlainText().split(" ").length).sum();
      }
      log.warn(
          "LIVE CALL FLOW (Summary) : Gen AI Generation:: GenAI Words - {}, ClassicalAI Words - {}, "
              + "Session Id - {}, Conversation Id - {}, isPublish - {}", genAICount, intentCount,
          summaryDoc.getExtConversationId(), summaryDoc.getConversationId(), isPublish);
    }
  }

  public GenAiSummaryDoc createGenAiSummary(final String conversationId, final String templateId, final String tenantId,final int version){
    GenAiSummaryDoc summaryDoc=GenAiSummaryDoc.builder().build();
    try {
      CreateGenerativeSummaryRequest generativeSummaryRequestNg =
          this.grpcClientHelper.createGenerativeSummaryRequestNg(conversationId, tenantId, templateId,
              version);
      Iterator<CreateGenerativeSummaryResponse> genAiSummaryStream =
          this.grpcClientHelper.getGenAiSummaryStream(generativeSummaryRequestNg);
      summaryDoc=genAiSummaryHelper.processStreamWithoutUassistConfig(genAiSummaryStream, conversationId);
      return summaryDoc;
    } catch (Exception e){
      log.error("Error while creating GenAI summary", e);
      return summaryDoc;
    }

  }


      public void resetGenAIRequested(final String conversationId) {
    log.debug("Resetting genAI requested for conv id {} to false", conversationId);
    this.summaryCacheHelper.removeGenAIRequested(conversationId);
  }
}
