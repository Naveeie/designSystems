package com.uniphore.uassist.summary.util;

import com.google.common.collect.ImmutableMap;
import com.uniphore.conversationmetric.v1.MetricScope;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.llminference.v1.*;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.uassist.summary.cache.GenAISummaryCache;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.CustomMetricsPublisher;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc.Slot;
import com.uniphore.uassist.summary.helper.LookerGenAISummaryMetricHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.uassist.summary.common.Constants.*;

@Component
@Slf4j
public class GrpcClientHelper {

  @Value("${gen-ai.interface.server-host}")
  private String genAiInterfaceHost;

  @Value("${gen-ai.interface.server-port}")
  private int genAiInterfacePort;

  @Value("${gen-ai.interface.max-inbound-message-size}")
  private int grpcMaxInboundMessageSize;

  @Value("${gen-ai.interface.max-outbound-message-size}")
  private int grpcMaxOutboundMessageSize;

  @Value("${gen-ai.interface.idle-time-out}")
  private int grpcMaxIdleTime;

  @Value("${gen-ai.interface.keep-alive-time}")
  private int grpcKeepAliveTime;

  @Value("${gen-ai.interface.keep-alive-timeout}")
  private int grpcKeepAliveTimeout;

  @Value("${gen-ai.interface.call-deadline-seconds}")
  private int grpcCallDeadlineSeconds;

  @Value("${gen-ai.interface.max-retires}")
  private int grpcMaxRetires;

  @Autowired
  private CustomMetricsPublisher customMetricsPublisher;

  @Autowired
  private LookerGenAISummaryMetricHelper metricHelper;

  @Autowired
  private LaunchDarklyHelper ldHelper;

  LLMInferenceServiceGrpc.LLMInferenceServiceBlockingStub llmInferenceClient;

  @PostConstruct
  void Init() {
    final ManagedChannel managedChannel =
        ManagedChannelBuilder.forAddress(this.genAiInterfaceHost, this.genAiInterfacePort)
            .usePlaintext().idleTimeout((long) grpcMaxIdleTime, TimeUnit.MINUTES)
            .keepAliveTime((long) grpcKeepAliveTime, TimeUnit.SECONDS)
            .keepAliveTimeout((long) grpcKeepAliveTimeout, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(false)
            .enableRetry().maxRetryAttempts(grpcMaxRetires).defaultLoadBalancingPolicy("round_robin").build();
    this.llmInferenceClient = LLMInferenceServiceGrpc.newBlockingStub(managedChannel);
    this.llmInferenceClient.withMaxInboundMessageSize(grpcMaxInboundMessageSize);
    this.llmInferenceClient.withMaxOutboundMessageSize(grpcMaxOutboundMessageSize);
  }

  public Optional<CreateGenerativeSummaryResponse> getGenAiSummary(
      CreateGenerativeSummaryRequest request, String expId) throws Exception {
    long startTime = DateUtil.TIME.millis();

    log.warn("Sending request to LLM to generate summary for conversationId: {}, tenantId: {}",
        request.getConversationId(), request.getTenantId());
    CreateGenerativeSummaryResponse generativeSummary =
        this.llmInferenceClient.withDeadlineAfter(grpcCallDeadlineSeconds, TimeUnit.SECONDS)
            .createGenerativeSummary(request);
    customMetricsPublisher.counterUpdateMap(Constants.LLM_GEN_AI_SUMMARY_REQUEST,
        ImmutableMap.of(Constant.TAG_TYPE, Constants.SUCCESS, Constant.CONVERSATION__ID,
            request.getConversationId()));
    long latency = DateUtil.TIME.millis() - startTime;
    this.metricHelper.pushGenAILatencyMetricForSummaryGenerate(
        GEN_AI_SUMMARY_LLM_INFERENCE_API_LATENCY_UASSIST_SUMMARY, request.getConversationId(),
        expId, latency, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY);
    return Optional.ofNullable(generativeSummary);
  }

  public Optional<CreateGenerativeSummaryResponse> getGenAiSummary(String conversationId,
      String tenantId, GenAISummaryCache genAISummaryCache, SummaryDoc summaryDoc, String callType)
      throws Exception {
    try {
      CreateGenerativeSummaryRequest request =
          createGenerativeSummaryRequest(conversationId, tenantId, genAISummaryCache, summaryDoc,
              callType);

      return this.getGenAiSummary(request, summaryDoc.getExperienceId());
    } catch (Exception e) {
      log.error("GRPC client:Gen AI generation failed", e);
      throw e;
    }
  }

  public Optional<Iterator<CreateGenerativeSummaryResponse>> getStreamingSummaryIter(
      String conversationId, String tenantId, GenAISummaryCache genAISummaryCache,
      SummaryDoc summaryDoc, String callType) throws Exception {
    try {
      CreateGenerativeSummaryRequest request =
          createGenerativeSummaryRequest(conversationId, tenantId, genAISummaryCache, summaryDoc,
              callType);

      Iterator<CreateGenerativeSummaryResponse> response = this.getGenAiSummaryStream(request);
      return Optional.of(response);
    } catch (Exception e) {
      log.error("GRPC client:Gen AI generation failed", e);
      throw e;
    }

  }

  public Iterator<CreateGenerativeSummaryResponse> getGenAiSummaryStream(
      CreateGenerativeSummaryRequest request) throws Exception {
    log.warn("Sending streaming request to LLM to generate summary for conversationId: {}, tenantId: {}",
        request.getConversationId(), request.getTenantId());
     return this.llmInferenceClient.withDeadlineAfter(grpcCallDeadlineSeconds, TimeUnit.SECONDS)
         .createGenerativeSummaryStream(request);
  }

  public CreateGenerativeSummaryRequest createGenerativeSummaryRequestNg(final String conversationId, final String tenantId,final String templateId,final int templateVersion){
    CreateGenerativeSummaryRequest.Builder request =
        CreateGenerativeSummaryRequest.newBuilder().setConversationId(conversationId)
            .setTenantId(tenantId);
    request.setTemplateId(templateId).setVersion(templateVersion);
    log.info("CreateGenerativeSummaryRequest conversation id: {},request {}",conversationId,request);
    return request.build();
  }

  private CreateGenerativeSummaryRequest createGenerativeSummaryRequest(String conversationId,
      String tenantId, GenAISummaryCache genAISummaryCache, SummaryDoc summaryDoc,
      String callType) {
    List<Section> sections = new ArrayList<>();

    CreateGenerativeSummaryRequest.Builder request =
        CreateGenerativeSummaryRequest.newBuilder().setConversationId(conversationId)
            .setTenantId(tenantId);

    if (this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)
        && StringUtils.isNoneBlank(summaryDoc.getTemplateId()) && ObjectUtils.isNotEmpty(
        summaryDoc.getTemplateVersion())) {
      request.setTemplateId(summaryDoc.getTemplateId()).setVersion(summaryDoc.getTemplateVersion());
      log.debug("Summary template used to generate Gen-AI summary was decided by : {}",
          summaryDoc.getTemplateAssignedBy());
    } else {
      log.debug(
          "Falling back to standard Gen-AI summary generation mechanism since Summary Template was not assigned, conversationId: {}",
          conversationId);
      boolean optInOutEnabled =
          this.ldHelper.isFFEnabled(UASSIST_GENAI_OPT_IN_OUT_FF, FF_CONTEXT_KIND);
      if (optInOutEnabled) {
        if (Objects.nonNull(genAISummaryCache) && Objects.nonNull(
            genAISummaryCache.getSummaryOptIn())) {
          GenAISummaryCache.SummaryOptIn summaryOptIn = genAISummaryCache.getSummaryOptIn();
          if (summaryOptIn.isCallReasonEnabled()) {
            sections.add(buildSection(Constants.CALL_REASON, genAISummaryCache, summaryDoc));
          }
          if (summaryOptIn.isAgentStepsEnabled()) {
            sections.add(buildSection(Constants.AGENT_ACTIONS, genAISummaryCache, summaryDoc));
          }
          if (summaryOptIn.isOutcomeEnabled()) {
            sections.add(buildSection(Constants.CALL_OUTCOME, genAISummaryCache, summaryDoc));
          }
          if (summaryOptIn.isCustomerSatisfactionEnabled()) {
            sections.add(buildSection(CUSTOMER_SENTIMENT, genAISummaryCache, summaryDoc));
          }
        } else {
          this.addSections(genAISummaryCache, summaryDoc, sections);
        }
      } else {
        this.addSections(genAISummaryCache, summaryDoc, sections);
      }

      request.addAllSections(sections);

      if (null != callType) {
        request.setCallType(callType);
      }

      boolean targetLanguageFF =
          this.ldHelper.isFFEnabled(UASSIST_GENAI_TARGET_LANGUAGE_FF, FF_CONTEXT_KIND);
      if (targetLanguageFF) {
        if (Objects.nonNull(genAISummaryCache.getTargetLanguage()))
          request.setTargetLang(genAISummaryCache.getTargetLanguage());
      }
    }
    log.info("LLM summary request: conversationId: {}, request: {}", conversationId, request);
    return request.build();
  }

  private void addSections(GenAISummaryCache genAISummaryCache, SummaryDoc summaryDoc,
      List<Section> sections) {
    sections.add(buildSection(Constants.CALL_REASON, genAISummaryCache, summaryDoc));
    sections.add(buildSection(Constants.AGENT_ACTIONS, genAISummaryCache, summaryDoc));
    sections.add(buildSection(Constants.CALL_OUTCOME, genAISummaryCache, summaryDoc));
    sections.add(buildSection(CUSTOMER_SENTIMENT, genAISummaryCache, summaryDoc));
  }

  private Section buildSection(String sectionId, GenAISummaryCache genAISummaryCache,
      SummaryDoc summaryDoc) {
    boolean genAiEntitiesToLLMEnabled =
        this.ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND);
    log.debug("GenAI entities to LLM enabled: {}", genAiEntitiesToLLMEnabled);
    Section.Builder sectionBuilder = Section.newBuilder().setId(sectionId);

    if (genAiEntitiesToLLMEnabled && genAISummaryCache != null) {
      List<GenAISummaryCache.SpokenEntity> spokenEntities =
          this.getRelevantSpokenEntities(sectionId, genAISummaryCache);
      if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(spokenEntities)) {
        List<GenAISummaryCache.SpokenEntity> includedSpokenEntities = spokenEntities.stream()
            .filter(entity -> (GEN_AI_IN_LINE.equalsIgnoreCase(entity.getPlacement())
                || GEN_AI_END_OF_SECTION_FOR_DISPLAY_AND_MENTION.equalsIgnoreCase(
                entity.getPlacement()))).collect(Collectors.toList());
        log.debug("The response build for llm after filter: {}", includedSpokenEntities);

        List<SummaryEntity> summaryEntities =
            mapToSummaryEntities(includedSpokenEntities, summaryDoc);
        sectionBuilder.addAllEntities(summaryEntities);
      }
    }

    return sectionBuilder.build();
  }

  private List<GenAISummaryCache.SpokenEntity> getRelevantSpokenEntities(String sectionId,
      GenAISummaryCache genAISummaryCache) {
    if (genAISummaryCache.isFormatEnabled()) {
      log.debug("Format is enabled for sectionId: {}", sectionId);
      if (CollectionUtils.isEmpty(genAISummaryCache.getSectionMap())) {
        return new ArrayList<>();
      }
      return genAISummaryCache.getSectionMap().get(sectionId);
    } else if (!sectionId.equalsIgnoreCase(CUSTOMER_SENTIMENT)) {
      log.debug("Returning default summary section for sectionId: {}", sectionId);
      if (CollectionUtils.isEmpty(genAISummaryCache.getSectionMap())) {
        return new ArrayList<>();
      }
      return genAISummaryCache.getSectionMap().get(Constants.SUMMARY);
    }
    return null;
  }

  private List<SummaryEntity> mapToSummaryEntities(
      List<GenAISummaryCache.SpokenEntity> spokenEntities, SummaryDoc summaryDoc) {
    return spokenEntities.stream().map(spkEntity -> {
      Slot variable = summaryDoc.getVariables().get(spkEntity.getVariableId());
      String entityType = spkEntity.getType() != null && !Constants.RULE.equals(spkEntity.getType())
          ? spkEntity.getType()
          : "";
      log.debug("Mapping entity: label={}, type={}, value={}", spkEntity.getLabel(), entityType,
          variable != null ? variable.getValue() : "");
      return SummaryEntity.newBuilder().setEntityLabel(spkEntity.getLabel())
          .setEntityType(entityType).setEntityValue(variable != null ? variable.getValue() : "")
          .build();
    }).collect(Collectors.toList());
  }
}
