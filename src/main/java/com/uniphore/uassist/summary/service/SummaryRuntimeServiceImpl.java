package com.uniphore.uassist.summary.service;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Timestamp;
import com.uniphore.conversationmetric.v1.MetricScope;
import com.uniphore.conversations.v1.*;
import com.uniphore.featureflag.LaunchDarklyFeatureFlags;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.platform.telemetry.context.ContextSetter;
import com.uniphore.ri.common.tenant.TenantPojo;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.ri.common.validation.OutputProcessor;
import com.uniphore.summary.v1.Header;
import com.uniphore.summary.v1.IntentSummary;
import com.uniphore.summary.v1.Source;
import com.uniphore.uassist.summary.cache.*;
import com.uniphore.uassist.summary.common.*;
import com.uniphore.uassist.summary.dao.AgentEditsRepository;
import com.uniphore.uassist.summary.dao.AgentStepsRepo;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.documents.*;
import com.uniphore.uassist.summary.event.helper.ConversationEventProcessingHelper;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.execption.ErrorResponse;
import com.uniphore.uassist.summary.helper.LookerGenAISummaryMetricHelper;
import com.uniphore.uassist.summary.kafka.MessagePublisher;
import com.uniphore.uassist.summary.model.*;
import com.uniphore.uassist.summary.util.DateUtil;
import com.uniphore.uassist.summary.util.SummaryHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.uassist.summary.common.Constants.*;

@Service
@Slf4j
public class SummaryRuntimeServiceImpl implements SummaryRuntimeService {

  public static final String DEFAULT_HEADER_SUMMARY = "default_header_summary";
  public static final String DEFAULT_INTENT_SUMMARY = "default_intent_summary";
  public static final String HEADER_SUMMARY = "header_summary";
  public static final String INTENT_SUMMARY = "intent_summary";

  private static final String[] EMPTY_STR_ARR = new String[] {};
  public static final String UASSIST_IGNORE_EMPTY_INTENT_FF = "uassist-ignore-empty-intent-ff";
  public static final String UASSIST_ENABLE_MULTI_FEATURE_SUBMIT_FF = "uassist-enable-multi-feature-submit-ff";
  private static final String LOG_AI_GENERATED_SUMMARY_FF_ENABLED = "AI generated summary ff enabled: {}";

  @Autowired
  private CacheRefreshHelper cacheRefreshHelper;
  @Autowired
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;
  @Autowired
  private IntentTemplateCacheHelper intentTemplateCacheHelper;
  @Autowired
  private MessagePublisher messagePublisher;
  @Autowired
  private AgentEditsRepository agentEditsRepository;
  @Autowired
  private CustomMetricsPublisher customMetricsPublisher;
  @Autowired
  private LaunchDarklyHelper ldHelper;
  @Autowired
  private SummaryHelper summaryHelper;
  @Autowired
  private GenAISummaryService genAISummaryService;
  @Autowired
  private SummaryRuntimeDao summaryRuntimeDao;
  @Autowired
  private AgentStepsCacheHelper agentStepsCacheHelper;
  @Autowired
  @Lazy
  private ConversationEventProcessingHelper conversationEventProcessingHelper;
  @Autowired
  private AgentStepsRepo agentStepsRepo;
  @Autowired
  private ThreadPoolTaskExecutor threadPoolExecutor;
  @Autowired
  private GenAISummaryConfigCacheHelper genAISummaryConfigCacheHelper;
  @Autowired
  private LookerGenAISummaryMetricHelper metricHelper;

  @Override
  public MessageResponse saveAgentNotes(AgentRequestPayload agentNotes) {
    TenantPojo tenantPojo = TenantUtils.getTenantDetails();
    ContextSetter.setContextWithSpan("uassist-summary", "saveAgentNotes",
        agentNotes.getConversationId(), tenantPojo.getTenantId(), tenantPojo.getEnvironment());
    log.info("AgentNotes Received {}", agentNotes);
    Commons.AgentAction agentAction = Commons.AgentAction.AGENT_NOTES;
    try {
      long timestamp = DateUtil.TIME.millis();

      boolean eventProcessingEnabled =
          this.ldHelper.isFFEnabled(UASSIST_AGENT_EDITS_QUEUE_PROCESSING, FF_CONTEXT_KIND);
      ArrayList<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
      // new event processing push
      AgentUpdateRequest agentUpdateRequest =
          AgentUpdateRequest.builder().agentAction(agentAction).agentRequestPayload(agentNotes)
              .environment(TenantUtils.getTenantDetails().getEnvironment()).timestamp(timestamp)
              .conversationId(agentNotes.getConversationId())
              .tenantId(TenantUtils.getTenantDetails().getTenantId()).build();
      if (eventProcessingEnabled) {
        this.conversationEventProcessingHelper.pushEventToConversationQueue(agentUpdateRequest);
      } else {
        this.processAgentUpdates(agentUpdateRequest, eventData);
      }
    } catch (CustomException e) {
      if (e.getCode().equals("50037")) {
        log.error("Conversation not found hence not able to save agent notes", e);
        return new MessageResponse("Conversation not found hence not able to save agent notes",
            agentNotes.getConversationId());
      }
      log.error("Failed to save the AgentNotes ", e);
      throw e;
    } catch (Exception e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("Failed to save the AgentNotes ", e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    } finally {
      ContextSetter.clearContext();
    }
    return new MessageResponse(Constants.SAVE, agentNotes.getConversationId());
  }

  @Override
  public MessageResponse saveAgentEdits(AgentRequestPayload agentRequestPayload) {
    TenantPojo tenantPojo = TenantUtils.getTenantDetails();
    this.setConversationId(agentRequestPayload);
    ContextSetter.setContextWithSpan("uassist-summary", "saveAgentEdits",
        agentRequestPayload.getConversationId(), tenantPojo.getTenantId(),
        tenantPojo.getEnvironment());
    boolean eventProcessingEnabled =
        this.ldHelper.isFFEnabled(UASSIST_AGENT_EDITS_QUEUE_PROCESSING, FF_CONTEXT_KIND);
    log.info("AgentEdits Received {} event processing of edits enabled: {}", agentRequestPayload,
        eventProcessingEnabled);
    Commons.AgentAction agentAction = Commons.AgentAction.INVALID;
    try {
      long eventStartTime = DateUtil.TIME.millis();
      List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();

      // for empty intent summary id saving or gen ai summary
      boolean intentSummaryEditEmptyCheck =
          StringUtils.isEmpty(agentRequestPayload.getInstanceId()) || (
              StringUtils.isEmpty(agentRequestPayload.getIntentSummary()) && StringUtils.isEmpty(
                  agentRequestPayload.getDisposition()));
      boolean genAiSummaryEditEmptyCheck =
          ObjectUtils.isEmpty(agentRequestPayload.getGenAiSummaryDoc());
      log.debug("Saving agent edit: intentSummaryEditEmptyCheck: {},genAiSummaryEditEmptyCheck: {}",
          intentSummaryEditEmptyCheck, genAiSummaryEditEmptyCheck);
      if (genAiSummaryEditEmptyCheck && intentSummaryEditEmptyCheck) {
        log.info("ignored agent edit as no object edited: conversationId: {}",
            agentRequestPayload.getConversationId());
        return new MessageResponse(Constants.IGNORE, agentRequestPayload.getConversationId());
      }

      BeanValidation.validateAgentEditRequst(agentRequestPayload);
      Timestamp timestamp = DateUtil.getCurrentTimestamp();

      // save the agent edits in AgentEdits collection
      if (StringUtils.isNotEmpty(agentRequestPayload.getIntentId()) || StringUtils.isNotEmpty(
          agentRequestPayload.getInstanceId())) {
        agentAction = Commons.AgentAction.INTENT_EDIT;
      } else if (StringUtils.isNotEmpty(agentRequestPayload.getHeaderSummary())) {
        agentAction = Commons.AgentAction.HEADER_EDIT;
      } else if (ObjectUtils.isNotEmpty(agentRequestPayload.getGenAiSummaryDoc())) {
        agentAction = Commons.AgentAction.GEN_AI_SUMMARY_EDIT;
      }
      AgentUpdateRequest agentUpdateRequest = AgentUpdateRequest.builder().agentAction(agentAction)
          .agentRequestPayload(agentRequestPayload)
          .environment(TenantUtils.getTenantDetails().getEnvironment()).timestamp(eventStartTime)
          .conversationId(agentRequestPayload.getConversationId())
          .tenantId(TenantUtils.getTenantDetails().getTenantId()).build();
      if (eventProcessingEnabled) {
        this.conversationEventProcessingHelper.pushEventToConversationQueue(agentUpdateRequest);
      } else {
        this.processAgentUpdates(agentUpdateRequest, eventData);
      }
    } catch (CustomException e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("Failed to save the AgentEdits error: ", e);
      throw e;
    } catch (Exception e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("Failed to save the AgentEdits error: ", e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, e);
    } finally {
      ContextSetter.clearContext();
    }

    return new MessageResponse(Constants.SAVE, agentRequestPayload.getConversationId());
  }

  private void setConversationId(AgentRequestPayload agentRequestPayload) {
    if (agentRequestPayload.getConversationId() == null || agentRequestPayload.getConversationId()
        .isEmpty()) {
      // situation where session id is asked, but conversation id needs to be provided.
      final SummaryDoc summ =
          this.summaryRuntimeDao.getSummaryByExtId(agentRequestPayload.getExtConversationId());
      if (summ == null) {
        throw new CustomException(ErrorCodes.CONVERSATION_NOT_FOUND, HttpStatus.BAD_REQUEST);
      }
      agentRequestPayload.setConversationId(summ.getConversationId());
    }
  }

  @Override
  public void processAgentUpdates(final AgentUpdateRequest agentUpdateRequest,
      List<SummaryUpdateEventLog.EventData> eventData) {
    Commons.AgentAction agentAction = agentUpdateRequest.getAgentAction();
    AgentRequestPayload agentRequestPayload = agentUpdateRequest.getAgentRequestPayload();
    AgentUpdateRequest.AgentStepUpdate agentStepUpdate = agentUpdateRequest.getAgentStepUpdate();
    long eventStartTime = agentUpdateRequest.getTimestamp();
    Timestamp timestamp = DateUtil.convertTimestamp(eventStartTime);
    try {
      log.debug("processAgentUpdates::got request to process agent update: action: {}",
          agentAction);
      if (agentAction.equals(Commons.AgentAction.INVALID)) {
        log.debug("processAgentUpdates:: got invalid event ignore it");
      } else {
        this.summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);
      }
      switch (agentAction) {
        case GEN_AI_SUMMARY_EDIT:
          log.debug("processAgentUpdates::processing gent ai edit: action: {}", agentAction);
          this.summaryHelper.handleSummaryTemplateEdits(agentRequestPayload, agentAction, timestamp,
              eventStartTime, eventData);
          break;
        case HEADER_EDIT:
          log.debug("processAgentUpdates::processing header template edit: action: {}",
              agentAction);
          this.summaryHelper.handleSummaryTemplateEdits(agentRequestPayload, agentAction, timestamp,
              eventStartTime, eventData);
          break;
        case INTENT_EDIT:
          log.debug("processAgentUpdates::processing intent template edit: action: {}",
              agentAction);
          this.summaryHelper.handleSummaryTemplateEdits(agentRequestPayload, agentAction, timestamp,
              eventStartTime, eventData);
          break;
        case AGENT_NOTES:
          log.debug("processing agent notes edit: action: {}", agentAction);
          this.summaryHelper.handleAgentNotesUpdate(agentRequestPayload, eventStartTime, eventData);
          break;
        case INTENT_ADD:
          log.debug("processAgentUpdates::processing intent add edit: action: {}", agentAction);
          this.summaryHelper.handleIntentAddByAgent(agentRequestPayload, timestamp, eventStartTime,
              eventData);
          break;
        case INTENT_DELETE:
          log.debug("processAgentUpdates::processing intent delete edit: action: {}", agentAction);
          this.summaryHelper.handleIntentDeleteByAgent(agentRequestPayload, timestamp,
              eventStartTime, eventData);
          break;
        case ADD_AGENT_STEP:
          log.debug("processAgentUpdates::processing agent step manually add edit: action: {}",
              agentAction);
          this.addedAgentStep(agentUpdateRequest, agentStepUpdate.getValue(),
              agentStepUpdate.getId(), eventData);
          break;
        case DELETE_AGENT_STEP:
          this.processDeleteAgentStep(agentUpdateRequest, agentStepUpdate.getId(), eventData);
          break;
        case UPDATE_AGENT_STEP:
          this.processUpdateAgentStep(agentUpdateRequest, agentStepUpdate.getId(),
              agentStepUpdate.getValue(), eventData);
          break;
        default:
          log.info("invalid agent edit");
          break;
      }
      this.customMetricsPublisher.counterUpdateMap(Constants.AGENT_ACTION,
          ImmutableMap.of(Constants.ACTION_TYPE, agentAction.toString().toLowerCase(),
              Constant.TAG_TYPE, Constants.SUCCESS));
    } catch (CustomException e) {
      log.error(" custom exception while processing the agent notes {}  error ", agentAction, e);
      this.customMetricsPublisher.counterUpdateMap(Constants.AGENT_ACTION,
          ImmutableMap.of(Constants.ACTION_TYPE, agentAction.toString().toLowerCase(),
              Constant.TAG_TYPE, Constants.FAILED));
      throw e;
    } catch (Exception e) {
      log.error(" error while processing the agent notes {}  error ", agentAction, e);
      this.customMetricsPublisher.counterUpdateMap(Constants.AGENT_ACTION,
          ImmutableMap.of(Constants.ACTION_TYPE, agentAction.toString().toLowerCase(),
              Constant.TAG_TYPE, Constants.FAILED));
      throw e;
    }
  }

  @Override
  public MessageResponse addIntent(AgentRequestPayload agentRequestPayload) {
    long startTime = DateUtil.TIME.millis();
    TenantPojo tenantPojo = TenantUtils.getTenantDetails();
    ContextSetter.setContextWithSpan("uassist-summary", "addIntent",
        agentRequestPayload.getConversationId(), tenantPojo.getTenantId(),
        tenantPojo.getEnvironment());
    boolean eventProcessingEnabled =
        this.ldHelper.isFFEnabled(UASSIST_AGENT_EDITS_QUEUE_PROCESSING, FF_CONTEXT_KIND);
    log.info("Agent-AddIntent Received {}", agentRequestPayload);
    try {
      long eventTime = DateUtil.TIME.millis();
      List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
      BeanValidation.validateIntentId(agentRequestPayload);

      agentRequestPayload.setSource(Source.AGENT.toString());

      AgentUpdateRequest agentUpdateRequest =
          AgentUpdateRequest.builder().agentAction(Commons.AgentAction.INTENT_ADD)
              .agentRequestPayload(agentRequestPayload)
              .environment(TenantUtils.getTenantDetails().getEnvironment()).timestamp(eventTime)
              .conversationId(agentRequestPayload.getConversationId())
              .tenantId(TenantUtils.getTenantDetails().getTenantId()).build();
      if (eventProcessingEnabled) {
        this.conversationEventProcessingHelper.pushEventToConversationQueue(agentUpdateRequest);
      } else {
        this.processAgentUpdates(agentUpdateRequest, eventData);
      }
    } catch (CustomException e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("Exception Occured while Saving Agent Add Intent error ", e);
      throw e;
    } catch (Exception e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("Exception Occured while Saving Agent Add Intent error ", e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    } finally {
      ContextSetter.clearContext();
    }
    return new MessageResponse(Constants.SAVE, agentRequestPayload.getIntentId());
  }

  @Override
  public MessageResponse deleteIntent(AgentRequestPayload agentRequestPayload) {
    TenantPojo tenantPojo = TenantUtils.getTenantDetails();
    ContextSetter.setContextWithSpan("uassist-summary", "deleteIntent",
        agentRequestPayload.getConversationId(), tenantPojo.getTenantId(),
        tenantPojo.getEnvironment());
    log.info("Agent-DeleteIntent Received  {}", agentRequestPayload);

    long eventStartTime = DateUtil.TIME.millis();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    try {
      boolean eventProcessingEnabled =
          this.ldHelper.isFFEnabled(UASSIST_AGENT_EDITS_QUEUE_PROCESSING, FF_CONTEXT_KIND);
      AgentUpdateRequest agentUpdateRequest =
          AgentUpdateRequest.builder().agentAction(Commons.AgentAction.INTENT_DELETE)
              .agentRequestPayload(agentRequestPayload)
              .environment(TenantUtils.getTenantDetails().getEnvironment())
              .timestamp(eventStartTime).conversationId(agentRequestPayload.getConversationId())
              .tenantId(TenantUtils.getTenantDetails().getTenantId()).build();
      if (eventProcessingEnabled) {
        this.conversationEventProcessingHelper.pushEventToConversationQueue(agentUpdateRequest);
      } else {
        this.processAgentUpdates(agentUpdateRequest, eventData);
      }
      return new MessageResponse(Constants.DELETE, agentRequestPayload.getIntentId());
    } catch (CustomException e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("Exception Occured while deleting the intent error: {} ", e);
      throw e;
    } catch (Exception e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("Exception Occured while deleting the intent error: {} ", e);
    } finally {
      ContextSetter.clearContext();
    }
    return new MessageResponse(Constants.FAILURE, agentRequestPayload.getIntentId());
  }

  @Override
  public void handleConversationEvent(ConversationEventEnvelope conversationEventEnvelope,
      final long eventStartTime) {
    if (StringUtils.isNotEmpty(conversationEventEnvelope.getEntityAdded().toString())) {
      log.info("EntityAdded Event Recieved {}", conversationEventEnvelope.toString());
      this.handleEntityAddedEvent(conversationEventEnvelope, eventStartTime);
    } else if (StringUtils.isNotEmpty(conversationEventEnvelope.getEntityUpdated().toString())) {
      log.info("EntityUpdated Event Recieved {}", conversationEventEnvelope.toString());
      this.handleEntityUpdatedEvent(conversationEventEnvelope, eventStartTime);
    } else if (StringUtils.isNotEmpty(conversationEventEnvelope.getVariablesAdded().toString())) {
      log.info("VariablesAdded Event Recieved {}", conversationEventEnvelope.toString());
      this.handleVariableEvent(conversationEventEnvelope,
          conversationEventEnvelope.getVariablesAdded().getVariablesList(), eventStartTime,
          Constants.TAG_VARIABLE_ADD);
    } else if (StringUtils.isNotEmpty(conversationEventEnvelope.getIntentsAdded().toString())) {
      log.info("IntentsAdded Event Recieved {}", conversationEventEnvelope.toString());
      this.handleIntentsAddedEvent(conversationEventEnvelope, eventStartTime);
    } else if (StringUtils.isNotEmpty(conversationEventEnvelope.getIntentDeleted().toString())) {
      log.info("IntentsDeleted Event Recieved {}", conversationEventEnvelope.toString());
      this.handleIntentsDeletedEvent(conversationEventEnvelope, eventStartTime);
    } else if (StringUtils.isNotEmpty(conversationEventEnvelope.getVariablesUpdated().toString())) {
      log.info("VariablesUpdated Event Recieved {}", conversationEventEnvelope.toString());
      this.handleVariableEvent(conversationEventEnvelope,
          conversationEventEnvelope.getVariablesUpdated().getVariablesList(), eventStartTime,
          Constants.TAG_VARIABLE_UPDATE);
    } else if (StringUtils.isNotEmpty(
        conversationEventEnvelope.getConversationStarted().toString())) {
      log.info("ConversationStarted Event Received {}", conversationEventEnvelope.toString());
      this.handleConversationStartedEvent(conversationEventEnvelope, eventStartTime);
    } else if (StringUtils.isNotEmpty(conversationEventEnvelope.getFlowEnded().toString())) {
      log.info("FlowEnded Event Received {}", conversationEventEnvelope.toString());
      this.handleFlowEndedEvent(conversationEventEnvelope, eventStartTime);
    } else if (StringUtils.isNotEmpty(conversationEventEnvelope.getStateV2Updated().toString())) {
      this.handleConversationStateUpdated(conversationEventEnvelope, eventStartTime);
    } else if (StringUtils.isNotEmpty(conversationEventEnvelope.getStateUpdated().toString())) {
      this.handleConversationStateUpdated(conversationEventEnvelope, eventStartTime);
    } else if (StringUtils.isNotEmpty(conversationEventEnvelope.getPoliciesAdded().toString())) {
      this.handlePolicyAddedEvent(conversationEventEnvelope);
    } else if (StringUtils.isNotEmpty(
        conversationEventEnvelope.getTranscriptChunkAdded().toString())) {
      log.info("TranscriptChuckAdded Event Received {}", conversationEventEnvelope.toString());
      handleTranscriptChunkAdded(conversationEventEnvelope, eventStartTime);
    }
  }

  private void handlePolicyAddedEvent(ConversationEventEnvelope conversationEventEnvelope) {
    if (conversationEventEnvelope.getPoliciesList().stream().anyMatch(
        policy -> policy.getActionType().equals(PolicyActionType.POLICY_ACTION_TYPE_BLOCKLIST))) {
      log.info("Conversation blocked for conversationId: {}",
          conversationEventEnvelope.getConversationId());
      summaryHelper.deleteSummaryDoc(conversationEventEnvelope.getConversationId());
    }
  }

  private void handleTranscriptChunkAdded(final ConversationEventEnvelope conversationEventEnvelope,
      final long eventStartTime) {
    SummaryDoc summaryDoc =
        summaryHelper.getSummaryDoc(conversationEventEnvelope.getConversationId());
    log.debug("Fetched summaryDoc: {}", summaryDoc);

    if (summaryDoc == null) {
      log.error("SummaryDoc is null for conversationId: {}",
          conversationEventEnvelope.getConversationId());
      return;
    }

    ConversationEventEnvelope.TranscriptChunkAdded transcriptAddedChunk =
        conversationEventEnvelope.getTranscriptChunkAdded();
    TranscriptChunk transcriptChunk = transcriptAddedChunk.getTranscriptChunk();
    List<Turn> turnList = transcriptChunk.getTurnsList();

    // Extract existing metadata details
    Map<String, Object> existingMetadata = summaryDoc.getMetadata();
    int existingAgentTurns = getIntegerValue(existingMetadata, Constants.AGENT_TURNS);
    int existingCustomerTurns = getIntegerValue(existingMetadata, Constants.CUSTOMER_TURNS);
    String lastSpokenChannel = getStringValue(existingMetadata, Constants.LAST_SPOKEN_CHANNEL);

    // Process transcript chunk and update metadata
    Map<String, Object> updatedMetadata =
        extractingChannelTurnCount(turnList, existingAgentTurns, existingCustomerTurns,
            lastSpokenChannel);

    summaryDoc.setMetadata(updatedMetadata);
    summaryHelper.saveSummaryDoc(summaryDoc);
    log.debug("Updated summary metadata: {}", updatedMetadata);
  }

  private int getIntegerValue(Map<String, Object> metadata, String key) {
    return (metadata != null && metadata.get(key) instanceof Number) ? ((Number) metadata.get(
        key)).intValue() : 0;
  }

  private String getStringValue(Map<String, Object> metadata, String key) {
    return (metadata != null && metadata.get(key) instanceof String)
        ? (String) metadata.get(key)
        : null;
  }

  private Map<String, Object> extractingChannelTurnCount(List<Turn> turns, int currentAgentTurns,
      int currentCustomerTurns, String prevLastSpokenChannel) {
    String lastSpokenChannel = prevLastSpokenChannel;

    for (Turn turn : turns) {
      String participant = turn.getParticipantType().toLowerCase();

      if (turn.getWordsList() == null || turn.getWordsList().isEmpty()) {
        continue;
      }

      if (participant.contains(Constants.AGENT_GEN_AI)) {
        if (!Constants.AGENT_GEN_AI.equals(lastSpokenChannel)) {
          currentAgentTurns++;
          lastSpokenChannel = Constants.AGENT_GEN_AI;
        }
      } else if (participant.contains(Constants.CUSTOMER)) {
        if (!Constants.CUSTOMER.equals(lastSpokenChannel)) {
          currentCustomerTurns++;
          lastSpokenChannel = Constants.CUSTOMER;
        }
      }
    }

    Map<String, Object> metadata = new HashMap<>();
    metadata.put(Constants.AGENT_TURNS, currentAgentTurns);
    metadata.put(Constants.CUSTOMER_TURNS, currentCustomerTurns);
    metadata.put(Constants.LAST_SPOKEN_CHANNEL, lastSpokenChannel);
    return metadata;
  }

  private void handleConversationStateUpdated(
      final ConversationEventEnvelope conversationEventEnvelope, final long eventStartTime) {
    final boolean isStateV2FfEnabled = ldHelper.isFFEnabled(Constants.UASSIST_STATE_V2_UPDATE_FF,
        LaunchDarklyFeatureFlags.FF_CONTEXT_KIND);
    log.debug("isStateV2FfEnabled: {} for convId: {}", isStateV2FfEnabled, conversationEventEnvelope.getConversationId());
    ConversationState conversationState = isStateV2FfEnabled
        ? conversationEventEnvelope.getStateV2Updated().getState().getState()
        : conversationEventEnvelope.getStateUpdated().getState().getState();
    Timestamp telephonyCallEndTime = isStateV2FfEnabled
        ? conversationEventEnvelope.getStateV2Updated().getState().getTimestamp()
        : conversationEventEnvelope.getStateUpdated().getState().getTimestamp();
    log.debug("ConversationStateUpdated Event Received {} for conversationId: {}",
        conversationState, conversationEventEnvelope.getConversationId());
    ConversationState state = isStateV2FfEnabled
        ? ConversationState.CONVERSATION_STATE_ENDED
        : ConversationState.CONVERSATION_STATE_FINISHED;
    log.debug("target state: {} for convId: {}", state, conversationEventEnvelope.getConversationId());
    Timestamp eventEndTime = conversationEventEnvelope.getTimestamp();
    if (conversationState.equals(state)) {
      log.warn("Conversation Ended/Finished event received for conversationId: {}, tenantId: {}, "
              + "telephonyCallEndTime: {}, eventEndTimeReceived: {}",
          conversationEventEnvelope.getConversationId(), conversationEventEnvelope.getTenantId(),
          telephonyCallEndTime, eventEndTime);
      boolean ffEnabled = this.ldHelper.isFFEnabled(GENAI_SUMMARY_FF, FF_CONTEXT_KIND);
      long timestamp = System.currentTimeMillis();
      Long telephonyCallEndTimeMillis =
          telephonyCallEndTime.getSeconds() * 1000 + telephonyCallEndTime.getNanos() / 1_000_000;
      if (ffEnabled) {
        final Optional<String> experienceIdOpt =
            Optional.of(conversationEventEnvelope.getExperienceId());
        this.threadPoolExecutor.execute(() -> {
          //handle generate summary in different thread as it may take some time and block the processing
          TenantContext.setTenantContext(conversationEventEnvelope.getTenantId(),
              conversationEventEnvelope.getEnvironment());
          this.genAISummaryService.createGenAiSummary(conversationEventEnvelope.getConversationId(),
              conversationEventEnvelope.getTenantId(), experienceIdOpt, true, false, timestamp,
              null, telephonyCallEndTimeMillis);
        });
      } else {
        SummaryDoc summaryDoc = new SummaryDoc();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("EventEndTime", eventEndTime);
        summaryDoc.setMetadata(metadata);
        log.debug("updating event time in summaryDoc:{}", summaryDoc);
      }
    } else {
      log.debug("ignoring conversation state updated event {} for convId: {}",
          conversationEventEnvelope.getStateUpdated().getState(), conversationEventEnvelope.getConversationId());
    }
  }

  private void handleEntityUpdatedEvent(ConversationEventEnvelope conversationEventEnvelope,
      long eventStartTime) {
    ContextSetter.setContextWithSpan("uassist-summary", "event-entity-updated",
        conversationEventEnvelope.getConversationId(), conversationEventEnvelope.getTenantId(),
        conversationEventEnvelope.getEnvironment());
    boolean ffEnabled = this.ldHelper.isFFEnabled(ENABLE_INTENT_BOUNDARY, FF_CONTEXT_KIND);
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    Header header = null;
    Set<String> bpIdList = null;
    Map<String, IntentSummary> intentSummaryMap = new HashMap<>();
    List<String> effectedIntents = new ArrayList<>();
    boolean headerEffected = false;
    boolean isAgentEdit = false;
    Entity entity = conversationEventEnvelope.getEntityUpdated().getEntity();
    log.debug("handleEntityUpdatedEvent::Intent boundary enabled: {}, entity: {}", ffEnabled,
        entity);
    SummaryDoc summaryDoc =
        this.summaryHelper.getSummaryDoc(conversationEventEnvelope.getConversationId());
    summaryDoc.getEntities().put(entity.getEntityId(),
        SummaryDoc.Slot.builder().id(entity.getEntityId()).instanceId(entity.getEntityInstanceId())
            .value(entity.getValue()).build());
    if (entity.getEditSourceType().equals(EditSourceType.EDIT_SOURCE_TYPE_HEADER_SUMMARY)) {
      header = this.summaryHelper.updateHeaderSummary(summaryDoc, entity.getEntityId(),
          entity.getValue(), entity.getEntityInstanceId(), HtmlParser.ENTITY,
          conversationEventEnvelope.getTimestamp());
      if (header != null) {
        headerEffected = true;
        isAgentEdit = true;
      }
    } else if (entity.getEditSourceType()
        .equals(EditSourceType.EDIT_SOURCE_TYPE_INTENT_CALL_REASON)) {
      this.summaryHelper.updateIntentSummary(summaryDoc, entity.getEntityId(), entity.getValue(),
          entity.getEntityInstanceId(), entity.getEditSourceInstanceId(), HtmlParser.ENTITY,
          intentSummaryMap, true);
      isAgentEdit = true;
      effectedIntents.add(entity.getEditSourceInstanceId());
    } else {
      header = this.checkAndUpdateHeaderSummary(conversationEventEnvelope.getConversationId(),
          HtmlParser.ENTITY, entity.getEntityId(), entity.getValue(), entity.getEntityInstanceId(),
          conversationEventEnvelope.getBusinessProcessId(), summaryDoc,
          conversationEventEnvelope.getTimestamp());
      if (header != null) {
        headerEffected = true;
      }
      intentSummaryMap.putAll(
          this.checkAndUpdateIntentSummary(conversationEventEnvelope.getConversationId(),
              summaryDoc, entity.getEntityId(), entity.getValue(), entity.getEntityInstanceId(),
              HtmlParser.ENTITY, effectedIntents, true));
    }
    eventData.add(SummaryUpdateEventLog.EventData.builder().eventId(entity.getEntityId())
        .value(entity.getValue()).instanceId(entity.getEntityInstanceId())
        .headerEffected(headerEffected).effectedIntents(effectedIntents)
        .detectedBy(Constants.DETECTED_BY_AGENT).build());
    SummaryUpdateEventLog summaryUpdateEventLog =
        this.summaryHelper.getSummaryUpdateEventLog(conversationEventEnvelope.getConversationId(),
            eventStartTime, Constants.TAG_ENTITY_UPDATE, eventData, summaryDoc.toString(),
            conversationEventEnvelope.getBusinessProcessId());

    if (headerEffected || !intentSummaryMap.isEmpty()) {
      this.summaryHelper.saveAndPublish(summaryDoc,
          ProtoConverter.getSummaryProto(summaryDoc, header, intentSummaryMap,
              Collections.emptyList(), isAgentEdit), summaryUpdateEventLog);
      this.customMetricsPublisher.updateAndLatencyMap(Constants.SUMMARY_ENTITY_PROCESSED,
          eventStartTime,
          ImmutableMap.of(Constant.TAG_TYPE, Constants.SUCCESS, Constant.CONVERSATION__ID,
              conversationEventEnvelope.getConversationId(), "detected_by", "agent"));
    } else {
      this.summaryHelper.saveSummaryDoc(summaryDoc);
    }

  }

  private void handleFlowEndedEvent(ConversationEventEnvelope conversationEventEnvelope,
      long eventStartTime) {
    ContextSetter.setContextWithSpan("uassist-summary", "event-flow-ended",
        conversationEventEnvelope.getConversationId(), conversationEventEnvelope.getTenantId(),
        conversationEventEnvelope.getEnvironment());
    log.debug("got flow ended event clearing out all current intents in cache");
    this.intentTemplateCacheHelper.deleteIntentBoundaryCache(
        conversationEventEnvelope.getConversationId());
    SummaryUpdateEventLog.EventData event = SummaryUpdateEventLog.EventData.builder()
        .instanceId(conversationEventEnvelope.getFlowEnded().getFlowInstanceId())
        .eventId(conversationEventEnvelope.getEventId()).build();
    SummaryUpdateEventLog summaryUpdateEventLog =
        this.summaryHelper.getSummaryUpdateEventLog(conversationEventEnvelope.getConversationId(),
            eventStartTime, Constants.TAG_FLOW_ENDED_EVENT, Collections.singletonList(event),
            Constants.EMPTY, conversationEventEnvelope.getBusinessProcessId());
    this.summaryHelper.saveSummaryEventDoc(summaryUpdateEventLog);
  }

  @Override
  public MessageResponse summaryReviewed(String conversationId, Optional<Boolean> optAutoSubmit,Optional<Boolean> optForcedsubmit,
      Optional<AutoSubmitOption> autoSubmitOption) {
    log.warn("SummaryReviewed Received for conversation id {}", conversationId);
    long eventStartTime = DateUtil.TIME.millis();

    TenantPojo tenantPojo = TenantUtils.getTenantDetails();
    ContextSetter.setContextWithSpan("uassist-summary", "summaryReviewed", conversationId,
        tenantPojo.getTenantId(), tenantPojo.getEnvironment());

    boolean forcedSubmit = optForcedsubmit.orElse(false);
    boolean auto = optAutoSubmit.orElse(false);
    String submitType = forcedSubmit
        ? FORCED_AUTO_SUBMIT
        : auto ? AUTO_SUBMIT : MANUAL_SUBMIT;

    SummaryDoc summaryDoc;
    try {
      try {
        summaryDoc = this.summaryHelper.getSummaryDoc(conversationId);
      } catch (CustomException ce) {
        log.error("For convId: {}, no summary was found in cache or database", conversationId);
        customMetricsPublisher.counterUpdate(Constants.SUMMARY_SUBMITTED, Constants.FAILED);
        this.pushSubmitSummaryMetrics(conversationId,"UNKNOWN_EXP_ID",null,Constants.FAILED);
        return new MessageResponse("No summary was found in cache or database", conversationId);
      }
      String experienceId = summaryDoc.getExperienceId();

      boolean genAiSummaryFFEnabled = this.ldHelper.isFFEnabled(GENAI_SUMMARY_FF, FF_CONTEXT_KIND);
      log.debug("genAiSummaryFFEnabled: {}", genAiSummaryFFEnabled);

      if (genAiSummaryFFEnabled) {
        if (Objects.nonNull(summaryDoc.getGenAiSummary()) && Objects.nonNull(
            summaryDoc.getGenAiSummary().getSections()) && !summaryDoc.getGenAiSummary()
            .getSections().isEmpty()) {
          log.debug("gen ai summary available:{}: autsubmit:{}",summaryDoc,optAutoSubmit);

          int genAiWordCount = this.summaryHelper.getGenAiWordCount(summaryDoc.getGenAiSummary());
          log.warn(
              "SummaryReviewed found existing summary before submit:: ConversationId - {}, TenantId - {}, GenAI Words - {}",
              summaryDoc.getConversationId(), summaryDoc.getTenantId(), genAiWordCount);

          this.summaryHelper.saveSummaryDoc(summaryDoc);
          this.submitSummary(conversationId, summaryDoc, eventStartTime);
          this.pushSubmitSummaryMetrics(conversationId,experienceId,submitType, Constants.SUCCESS);
        } else {
          SummaryDoc finalSummaryDoc = summaryDoc;
          threadPoolExecutor.execute(() -> {
            TenantContext.setTenantContext(tenantPojo.getTenantId(), tenantPojo.getEnvironment());
            SummaryDoc genAiSummary =
                this.genAISummaryService.createGenAiSummary(finalSummaryDoc.getConversationId(),
                    finalSummaryDoc.getTenantId(), Optional.ofNullable(finalSummaryDoc.getExperienceId()),
                    false, false, System.currentTimeMillis(), null);
            if (Objects.isNull(genAiSummary)) {
              genAiSummary = this.summaryHelper.getSummaryDoc(finalSummaryDoc.getConversationId());
            }
            log.debug("There is no summary generated while submitting:{}, autoSubmit:{}",genAiSummary,optAutoSubmit);

            int genAiWordCount = genAiSummary == null
                ? 0
                : this.summaryHelper.getGenAiWordCount(genAiSummary.getGenAiSummary());
            log.warn(
                "SummaryReviewed regenerated summary before submit:: ConversationId - {}, TenantId - {}, GenAI Words - {}",
                finalSummaryDoc.getConversationId(), finalSummaryDoc.getTenantId(), genAiWordCount);

            this.summaryHelper.saveSummaryDoc(genAiSummary);
            this.submitSummary(finalSummaryDoc.getConversationId(), genAiSummary, eventStartTime);
          });
          this.pushSubmitSummaryMetrics(conversationId,experienceId,submitType,Constants.SUCCESS);
        }
        if (autoSubmitOption.isPresent()
            && autoSubmitOption.get() == AutoSubmitOption.END_OF_CURRENT_CALL) {
          log.debug("publishing UI metric only at the auto aubmit-end id current call:{}",autoSubmitOption);
          this.pushSummaryStatusUIMetrics(conversationId, experienceId);
        }
      } else {
        // in case auto submit/genAI FF is not enabled.
        log.debug("genAiSummaryFFEnabled:{}, autosubmit:{}",genAiSummaryFFEnabled,optAutoSubmit);
        this.submitSummary(conversationId, summaryDoc, eventStartTime);
        customMetricsPublisher.counterUpdate(Constants.SUMMARY_SUBMITTED, Constants.SUCCESS);
      }
    } catch (CustomException ce) {
      ContextSetter.setContextWithSpanError(ce);
      log.error("custom exception occurred while reviewing summary: error_code: {}", ce.getCode(),
          ce);
      customMetricsPublisher.counterUpdate(Constants.SUMMARY_SUBMITTED, Constants.FAILED);
      this.pushSubmitSummaryMetrics(conversationId,"UNKNOWN_EXP_ID",null,Constants.FAILED);
      throw ce;
    } catch (Exception e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("exception occurred while reviewing summary: ", e);
      customMetricsPublisher.counterUpdate(Constants.SUMMARY_SUBMITTED, Constants.FAILED);
      this.pushSubmitSummaryMetrics(conversationId,"UNKNOWN_EXP_ID",null,Constants.FAILED);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    } finally {
      ContextSetter.clearContext();
    }
    return new MessageResponse(Constants.SUCCESS, conversationId);
  }

  private void submitSummary(String conversationId, SummaryDoc summaryDoc, long eventStartTime) {
    if (Objects.nonNull(summaryDoc)) {
      List<AgentStepPojo> agentStepList;
      if (this.ldHelper.isFFEnabled(ENABLE_AGENT_STEPS, FF_CONTEXT_KIND)) {
        agentStepList = this.agentStepsCacheHelper.getAsList(conversationId);
      } else {
        agentStepList = Collections.emptyList();
      }
      messagePublisher.summaryReviewed(
          ProtoConverter.getSummaryProtoForReviewed(summaryDoc, agentStepList));

      boolean isMultiFeatureEnabledOnSubmit = this.ldHelper.isFFEnabled(UASSIST_ENABLE_MULTI_FEATURE_SUBMIT_FF, FF_CONTEXT_KIND);
      log.debug("isMultiFeatureEnabledOnSubmit: {}", isMultiFeatureEnabledOnSubmit);
      if (isMultiFeatureEnabledOnSubmit) {
        messagePublisher.conversationReviewed(
            ProtoConverter.buildConversationReviewedProto(summaryDoc, agentStepList, eventStartTime));
      }

      final boolean multiSegmentSupportFF =
          this.ldHelper.isFFEnabled(MULTI_SEGMENT_SUPPORT, FF_CONTEXT_KIND);
      if (!multiSegmentSupportFF) { // If FF is disabled, carry on my wayward son.
        summaryHelper.removeSummaryDoc(summaryDoc.getConversationId());
        this.intentTemplateCacheHelper.deleteIntentBoundaryCache(conversationId);
        this.agentStepsCacheHelper.remove(conversationId);
      } else {
        // if FF is enabled, removal falls back to cache TTL.
        // Secondly, we need to reset GenAI flag.
        this.genAISummaryService.resetGenAIRequested(conversationId);
      }
      this.metricsUpdate(summaryDoc);
      this.printCallSubmitLog(summaryDoc, eventStartTime);
    }
  }

  private void pushSubmitSummaryMetrics(String conversationId, String experienceId,
      String submitType, String submitStatus) {
    metricHelper.pushGenAICounterMetric(Constants.SUBMIT_METRIC, conversationId, experienceId,
        submitStatus, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY, submitType);
    if (Objects.equals(submitType, FORCED_AUTO_SUBMIT)) {
      metricHelper.pushGenAICounterMetric(Constants.GEN_AI_SUMMARY_EDITED_AGENT_UI, conversationId,
          experienceId, UNMODIFIED, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY, null);
    }
  }

  private void pushSummaryStatusUIMetrics(String conversationId, String experienceId) {
    metricHelper.pushGenAICounterMetric(Constants.GEN_AI_SUMMARY_STATUS_AGENT_UI, conversationId, experienceId,
        SUCCESS, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY, null);
  }

  private void printCallSubmitLog(final SummaryDoc summaryDoc, long eventStartTime) {
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
          "LIVE CALL FLOW (Summary) : Submit Summary:: GenAI Words - {}, ClassicalAI Words - {}, "
              + "Session Id - {}, Conversation Id - {}, latency: - {}", genAICount, intentCount,
          summaryDoc.getExtConversationId(), summaryDoc.getConversationId(),
          DateUtil.TIME.millis() - eventStartTime);
    }
  }

  private void metricsUpdate(SummaryDoc summaryDoc) {
    log.debug("summary doc metrics {}", summaryDoc);
    final Set<String> headerEntityIds = getHeaderIds(summaryDoc, HtmlParser.ENTITY);
    final Set<String> intentEntityIds = getIntentIds(summaryDoc, HtmlParser.ENTITY);
    final Set<String> headerVariableIds = getHeaderIds(summaryDoc, HtmlParser.VARIABLE);
    final Set<String> intentVariableIds = getIntentIds(summaryDoc, HtmlParser.VARIABLE);
    String headerType = headerEntityIds.isEmpty() && headerVariableIds.isEmpty()
        ? DEFAULT_HEADER_SUMMARY
        : HEADER_SUMMARY;
    String intentType = intentEntityIds.isEmpty() && intentVariableIds.isEmpty()
        ? DEFAULT_INTENT_SUMMARY
        : INTENT_SUMMARY;
    headerEntityIds.addAll(intentEntityIds);
    long entityCount = headerEntityIds.size();
    headerVariableIds.addAll(intentVariableIds);
    log.debug(
        "metrics values conversation-id {} ,headerType {},intentType {}, intent count {}  headerEntity size {} headerVariable size {} ,intentEntity size {},intentVariable size {} ",
        summaryDoc.getConversationId(), headerType, intentType,
        summaryDoc.getIntentSummaries().size(), headerEntityIds.size(), headerVariableIds.size(),
        intentEntityIds.size(), intentVariableIds.size());
    customMetricsPublisher.counterUpdateType(Constants.SUMMARY_SUBMITTED_TYPE, Constants.SUCCESS,
        headerType, intentType);
    if (entityCount != 0) {
      customMetricsPublisher.histogramCount(HtmlParser.ENTITY.toLowerCase(), entityCount);
    }
    if (!summaryDoc.getIntentSummaries().isEmpty()) {
      customMetricsPublisher.histogramCount(Constants.INTENT.toLowerCase(),
          summaryDoc.getIntentSummaries().size());
    }
    if (!headerVariableIds.isEmpty()) {
      customMetricsPublisher.histogramCount(HtmlParser.VARIABLE.toLowerCase(),
          headerVariableIds.size());
    }
  }

  private static Set<String> getIntentIds(SummaryDoc summaryDoc, String type) {
    return summaryDoc.getIntentSummaries().values().stream().filter(intentSummaryDoc -> {
          if (Objects.isNull(intentSummaryDoc) || Objects.isNull(intentSummaryDoc.getTemplate())) {
            return false;
          }
          return HtmlParser.extractValue(intentSummaryDoc.getTemplate()).stream().anyMatch(
              e -> (Objects.nonNull(e.getName()) && !e.getName().isEmpty()) && e.getType()
                  .equalsIgnoreCase(type));
        }).map(e -> HtmlParser.extractValue(e.getTemplate())).flatMap(List::stream)
        .map(HtmlExtractData::getId).collect(Collectors.toSet());
  }

  private static Set<String> getHeaderIds(SummaryDoc summaryDoc, String type) {
    if (Objects.isNull(summaryDoc.getHeaderSummary()) || Objects.isNull(
        summaryDoc.getHeaderSummary().getTemplate())) {
      return new HashSet<>();
    }
    HeaderSummaryDoc headerSummary =
        summaryDoc.getHeaderSummary();
    List<HtmlExtractData> htmlExtractData =
        HtmlParser.extractValue(headerSummary.getTemplate());
    return htmlExtractData.stream().filter(
        e -> ((Objects.nonNull(e.getName()) && !e.getName().isEmpty()) && e.getType()
            .equalsIgnoreCase(type))).map(HtmlExtractData::getId).collect(Collectors.toSet());
  }

  private void handleEntityAddedEvent(ConversationEventEnvelope conversationEventEnvelope,
      final long eventStartTime) {
    ContextSetter.setContextWithSpan("uassist-summary", "event-entity-added",
        conversationEventEnvelope.getConversationId(), conversationEventEnvelope.getTenantId(),
        conversationEventEnvelope.getEnvironment());
    boolean ffEnabled = this.ldHelper.isFFEnabled(ENABLE_INTENT_BOUNDARY, FF_CONTEXT_KIND);
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    log.debug("Intent boundary enabled: {}", ffEnabled);
    Header header = null;
    Map<String, IntentSummary> intentSummaryMap = new HashMap<>();
    List<String> effectedIntents = new ArrayList<>();
    boolean headerEffected = false;
    try {
      Entity entity = conversationEventEnvelope.getEntityAdded().getEntity();
      SummaryDoc summaryDoc =
          this.summaryHelper.getSummaryDoc(conversationEventEnvelope.getConversationId());
      summaryDoc.getEntities().put(entity.getEntityId(),
          SummaryDoc.Slot.builder().id(entity.getEntityId())
              .instanceId(entity.getEntityInstanceId()).value(entity.getValue()).build());
      log.debug("handleEntityAdded:: summary doc before update: {}", summaryDoc);
      // header summary
      header = this.checkAndUpdateHeaderSummary(conversationEventEnvelope.getConversationId(),
          HtmlParser.ENTITY, entity.getEntityId(), entity.getValue(), entity.getEntityInstanceId(),
          conversationEventEnvelope.getBusinessProcessId(), summaryDoc,
          conversationEventEnvelope.getTimestamp());
      if (header != null) {
        headerEffected = true;
      }
      intentSummaryMap =
          this.checkAndUpdateIntentSummary(conversationEventEnvelope.getConversationId(),
              summaryDoc, entity.getEntityId(), entity.getValue(), entity.getEntityInstanceId(),
              HtmlParser.ENTITY, effectedIntents, false);
      eventData.add(SummaryUpdateEventLog.EventData.builder().eventId(entity.getEntityId())
          .value(entity.getValue()).instanceId(entity.getEntityInstanceId())
          .headerEffected(headerEffected).effectedIntents(effectedIntents)
          .detectedBy(Constants.DETECTED_BY_AI).build());
      SummaryUpdateEventLog summaryUpdateEventLog =
          this.summaryHelper.getSummaryUpdateEventLog(conversationEventEnvelope.getConversationId(),
              eventStartTime, Constants.TAG_ENTITY_ADD, eventData, summaryDoc.toString(),
              conversationEventEnvelope.getBusinessProcessId());
      log.debug("handleEntityAdded:: summary doc after update: {}", summaryDoc);
      if (headerEffected || CollectionUtils.isNotEmpty(intentSummaryMap.values())) {
        this.summaryHelper.saveAndPublish(summaryDoc,
            ProtoConverter.getSummaryProto(summaryDoc, header, intentSummaryMap,
                Collections.emptyList(), false), summaryUpdateEventLog);
      } else {
        this.summaryHelper.saveSummaryDoc(summaryDoc);
      }
      this.customMetricsPublisher.updateAndLatencyMap(Constants.SUMMARY_ENTITY_PROCESSED,
          eventStartTime,
          ImmutableMap.of(Constant.TAG_TYPE, Constants.SUCCESS, Constant.CONVERSATION__ID,
              conversationEventEnvelope.getConversationId(), "detected_by", "ai"));
    } catch (Exception e) {
      this.customMetricsPublisher.counterUpdateMap(Constants.SUMMARY_ENTITY_PROCESSED,
          ImmutableMap.of(Constant.TAG_TYPE, Constants.FAILED, Constant.CONVERSATION__ID,
              conversationEventEnvelope.getConversationId()));
      ContextSetter.setContextWithSpanError(e);
      log.error("EntityAdded Event error :", e);
      throw e;
    }
  }

  public Header checkAndUpdateHeaderSummary(final String conversationId, final String type,
      final String componentId, final String componentValue, final String componentInstanceId,
      final String businessId, final SummaryDoc summaryDoc, final Timestamp timestamp) {
    Set<String> bpIdList = new HashSet<>();
    if (type.equalsIgnoreCase(HtmlParser.ENTITY)) {
      bpIdList = headerTemplateCacheHelper.getHeaderTemplateEntityBpMap(componentId);
    } else if (type.equalsIgnoreCase(HtmlParser.VARIABLE)) {
      bpIdList = headerTemplateCacheHelper.getHeaderTemplateVariableBpMap(componentId);
    }
    if (bpIdList.contains(businessId)) {
      log.info("checkAndUpdateHeaderSummary:: updating header template: {}", conversationId);
      return this.summaryHelper.updateHeaderSummary(summaryDoc, componentId, componentValue,
          componentInstanceId, type, timestamp);
    }
    return null;
  }

  public Map<String, IntentSummary> checkAndUpdateIntentSummary(final String conversationId,
      final SummaryDoc summaryDoc, final String componentId, final String componentValue,
      final String componentInstanceid, final String type, final List<String> effectedIntents,
      final Boolean isAgentEdited) {
    Map<String, IntentSummary> intentSummaryMap = new HashMap<>();
    boolean ffEnabled = this.ldHelper.isFFEnabled(ENABLE_INTENT_BOUNDARY, FF_CONTEXT_KIND);
    Set<String> intentIdList = new HashSet<>();
    if (type.equalsIgnoreCase(HtmlParser.ENTITY)) {
      intentIdList = intentTemplateCacheHelper.getIntentTemplateEntityIntentMap(componentId);
    } else if (type.equalsIgnoreCase(HtmlParser.VARIABLE)) {
      intentIdList = intentTemplateCacheHelper.getIntentTemplateVariableIntentMap(componentId);
    }
    Map<String, Set<String>> intentIdAndIntentInstanceIdMap = Optional.ofNullable(summaryDoc).map(
        x -> Optional.ofNullable(x.getIntentSummaries()).map(y -> y.entrySet().stream().collect(
                Collectors.groupingBy(k -> k.getValue().getIntentId(),
                    Collectors.mapping(Map.Entry::getKey, Collectors.toSet()))))
            .orElse(new HashMap<>())).orElse(new HashMap<>());
    log.debug(
        "checkAndUpdateIntentSummary::type: {} componentId: {}, intentIds: {}, intentIdInstanceidMap: {}",
        type, componentId, intentIdList, intentIdAndIntentInstanceIdMap);
    if (ffEnabled) {
      // intent summary with intent boundary
      String currentIntent =
          this.intentTemplateCacheHelper.getCurrentIntentForConversation(conversationId);
      if (StringUtils.isNotEmpty(currentIntent)) {
        boolean currentIntentDependency = intentIdList.stream().anyMatch(
            i -> intentIdAndIntentInstanceIdMap.containsKey(i)
                && intentIdAndIntentInstanceIdMap.get(i).stream()
                .anyMatch(isId -> isId.equalsIgnoreCase(currentIntent)));
        if (currentIntentDependency) {
          this.summaryHelper.handleIntentTemplateUpdate(conversationId, intentSummaryMap,
              effectedIntents, componentId, componentValue, type, summaryDoc, currentIntent,
              isAgentEdited);
        } else {
          log.debug(
              "checkAndUpdateIntentSummary::type: {} is not part of any intent summary: conversationId {}, entityId: {}",
              type, conversationId, componentId);
        }
      } else {
        log.debug(
            "checkAndUpdateIntentSummary::type: {} no intent summary detected: conversationId {}, entityId: {}",
            type, conversationId, componentId);
      }
    } else {
      // without intent boundary just update all intents data
      if (summaryDoc != null && summaryDoc.getIntentSummaries() != null
          && CollectionUtils.isNotEmpty(summaryDoc.getIntentSummaries().keySet())) {
        Set<String> instanceIds = intentIdList.stream().map(
                i -> Optional.ofNullable(intentIdAndIntentInstanceIdMap.get(i))
                    .map(x -> x.stream().collect(Collectors.toSet())).orElse(new HashSet<>()))
            .flatMap(Set::stream).collect(Collectors.toSet());
        log.debug(
            "checkAndUpdateIntentSummary::type: {} updating intent template: conversation id: {},entity id: {} instance ids: {}",
            type, conversationId, componentId, instanceIds);
        for (String instanceId : instanceIds) {
          this.summaryHelper.handleIntentTemplateUpdate(conversationId, intentSummaryMap,
              effectedIntents, componentId, componentValue, type, summaryDoc, instanceId,
              isAgentEdited);
        }
      }
    }
    return intentSummaryMap;
  }

  private void handleIntentsAddedEvent(ConversationEventEnvelope conversationEventEnvelope,
      final long eventStartTime) {
    ContextSetter.setContextWithSpan("uassist-summary", "event-intent-added",
        conversationEventEnvelope.getConversationId(), conversationEventEnvelope.getTenantId(),
        conversationEventEnvelope.getEnvironment());
    try {
      SummaryDoc summaryDoc = this.getSummaryDocFromCache(conversationEventEnvelope);

      if (processTemplateBasedGenAISummary(conversationEventEnvelope, summaryDoc)) {
        return;
      }

      Map<String, String> processedIntent = new HashMap<>();
      List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
      Timestamp timestamp = DateUtil.getCurrentTimestamp();
      log.debug("handleIntentsAddedEvent summaryDoc: {}", summaryDoc);
      List<IntentSummaryDoc> intentSummaryDocs =
          this.getIntentSummaryDocs(conversationEventEnvelope, summaryDoc, processedIntent,
              timestamp, eventData);
      // add agent steps as well!
      List<AgentStepPojo> allAgentSteps = this.getAgentSteps(conversationEventEnvelope);

      if (CollectionUtils.isEmpty(intentSummaryDocs) && CollectionUtils.isEmpty(allAgentSteps)) {
        log.info("IntentsAdded Event TenantId : {}, environment : {} ConversationId : {} {}",
            conversationEventEnvelope.getTenantId(), conversationEventEnvelope.getEnvironment(),
            conversationEventEnvelope.getConversationId(),
            "Intents are already detected and agent steps empty");
      } else {
        SummaryUpdateEventLog summaryUpdateEventLog = this.summaryHelper.getSummaryUpdateEventLog(
            conversationEventEnvelope.getConversationId(), eventStartTime, Constants.TAG_INTENT_ADD,
            eventData, summaryDoc.toString(), conversationEventEnvelope.getBusinessProcessId());
        this.summaryHelper.saveAndPublish(summaryDoc,
            ProtoConverter.intentTemplateToSummaryProto(summaryDoc, intentSummaryDocs,
                allAgentSteps), summaryUpdateEventLog);
        log.debug("handleIntentAdded:: summary doc after update: {} conv-id {}", summaryDoc,
            conversationEventEnvelope.getConversationId());

        Map<String, String> tags = new HashMap<>();
        tags.put(Constants.TAG_TYPE, Constants.SUCCESS);
        tags.put(Constant.CONVERSATION__ID, conversationEventEnvelope.getConversationId());
        tags.put("intent_process_type", "intent_added");
        if (!eventData.isEmpty()) {
          tags.put("detected_by", eventData.get(0).getDetectedBy());
        }
        this.customMetricsPublisher.updateAndLatencyMap(Constants.SUMMARY_INTENT_PROCESSED,
            eventStartTime, tags);
      }
    } catch (Exception e) {
      this.customMetricsPublisher.counterUpdateMap(Constants.SUMMARY_INTENT_PROCESSED,
          ImmutableMap.of(Constants.TAG_TYPE, Constants.FAILED, Constant.CONVERSATION__ID,
              conversationEventEnvelope.getConversationId()));
      ContextSetter.setContextWithSpanError(e);
      log.error("handleIntentAdded:: Event error : {},error : ",
          conversationEventEnvelope.getConversationId(), e);
      throw e;
    }
  }

  private List<AgentStepPojo> getAgentSteps(ConversationEventEnvelope conversationEventEnvelope) {
    List<AgentStepPojo> allAgentSteps = new ArrayList<>();
    if (this.ldHelper.isFFEnabled(ENABLE_AGENT_STEPS, FF_CONTEXT_KIND)) {
      List<AgentStepPojo> agentSteps = null;
      log.debug("processing agent steps for conversation id {}",
          conversationEventEnvelope.getConversationId());
      AtomicInteger nextIndex = new AtomicInteger(
          this.agentStepsCacheHelper.getNextIndex(conversationEventEnvelope.getConversationId()));
      agentSteps = conversationEventEnvelope.getIntentsAdded().getIntentsList().stream()
          .filter(intent -> intent.getIntentType().equals(IntentType.INTENT_TYPE_AGENT_STEPS)).map(
              intent -> AgentStepPojo.builder().id(intent.getInstanceId()).text(intent.getLabel())
                  .index(nextIndex.getAndIncrement()).build()).collect(Collectors.toList());
      log.debug("found {} agent step intents in this event", agentSteps.size());
      if (CollectionUtils.isNotEmpty(agentSteps)) {
        allAgentSteps =
            this.agentStepsCacheHelper.add(conversationEventEnvelope.getConversationId(),
                agentSteps);
        this.agentStepsRepo.save(
            DocConverter.toAgentStepsDoc(conversationEventEnvelope, allAgentSteps));
      }
      log.debug("total agent steps : {}", allAgentSteps.size());
    } else {
      if (log.isDebugEnabled()) {
        log.debug("agent steps not enabled for current tenant!");
      }
    }
    return allAgentSteps;
  }

  private List<IntentSummaryDoc> getIntentSummaryDocs(
      ConversationEventEnvelope conversationEventEnvelope, SummaryDoc summaryDoc,
      Map<String, String> processedIntent, Timestamp timestamp,
      List<SummaryUpdateEventLog.EventData> eventData) {
    boolean duplicateIntentsEnabled =
        this.ldHelper.isFFEnabled(ENABLE_DUPLICATE_INTENTS, FF_CONTEXT_KIND);
    boolean flowSelectedIntentsEnabled =
        this.ldHelper.isFFEnabled(UASSIST_ENABLE_FLOW_SELECTED_INTENTS_FF, FF_CONTEXT_KIND);
    boolean systemSummaryffEnabled =
        this.ldHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND);

    if (null == summaryDoc.getIntentSummaries()) {
      summaryDoc.setIntentSummaries(new LinkedHashMap<String, IntentSummaryDoc>());
    }
    if (systemSummaryffEnabled && MapUtils.isEmpty(summaryDoc.getSystemIntentSummaries())) {
      summaryDoc.setSystemIntentSummaries(new LinkedHashMap<String, IntentSummaryDoc>());
    }
    log.debug(
        "handleIntentsAddedEvent:: ENABLE_DUPLICATE_INTENTS: {}, UASSIST_ENABLE_FLOW_SELECTED_INTENTS_FF: {}, SYSTEM_TEMPLATE_SUMMARY_FLAG : {}",
        duplicateIntentsEnabled, flowSelectedIntentsEnabled, systemSummaryffEnabled);
    Set<String> intentIds =
        summaryDoc.getIntentSummaries().values().stream().map(i -> i.getIntentId())
            .collect(Collectors.toSet());
    log.debug("handleIntentsAddedEvent:: existing intents conversationId: {}, intentId: {}",
        conversationEventEnvelope.getConversationId(), intentIds);
    List<IntentSummaryDoc> intentSummaryDocs = new ArrayList<>();
    conversationEventEnvelope.getIntentsAdded().getIntentsList().stream()
        .filter(intent -> intent.getIntentType().equals(IntentType.INTENT_TYPE_CALL_REASON))
        .forEach(intent -> {
          if (!duplicateIntentsEnabled && intentIds.contains(intent.getLabel())) {
            log.debug("duplicate intent detected ignoring it: conversationId: {}, intentId: {}",
                conversationEventEnvelope.getConversationId(), intent.getLabel());
            return;
          }
          if (flowSelectedIntentsEnabled && !StringUtils.isBlank(intent.getDetectedBy())) {
            log.debug(
                "flow selected event is enabled ignoring auto detected intent, intent detected by {}",
                intent.getDetectedBy());
            return;
          }
          this.createIntentSummary(conversationEventEnvelope, summaryDoc, timestamp, eventData,
              intent, systemSummaryffEnabled, intentSummaryDocs);
          processedIntent.put(intent.getLabel(), intent.getInstanceId());
        });
    return intentSummaryDocs;
  }

  private void createIntentSummary(ConversationEventEnvelope conversationEventEnvelope,
      SummaryDoc summaryDoc, Timestamp timestamp, List<SummaryUpdateEventLog.EventData> eventData,
      Intent intent, boolean systemSummaryffEnabled, List<IntentSummaryDoc> intentSummaryDocs) {
    String detectedBy = Constants.DETECTED_BY_AI;
    boolean ignoreEmptyIntent =
        this.ldHelper.isFFEnabled(UASSIST_IGNORE_EMPTY_INTENT_FF, FF_CONTEXT_KIND);

    // update this intent as current intent
    this.intentTemplateCacheHelper.pushIntentToIntentBoundaryStack(
        conversationEventEnvelope.getConversationId(), intent.getInstanceId());

    String htmlTemplate = this.summaryHelper.getIntentTemplate(intent.getLabel(),
        conversationEventEnvelope.getTenantId(), conversationEventEnvelope.getEnvironment(),
        conversationEventEnvelope.getConversationId(), summaryDoc, false);

    if (ignoreEmptyIntent && (htmlTemplate == null || htmlTemplate.equalsIgnoreCase(
        Constants.EMPTY))) {
      return;
    }

    String systemHtmlTemplate = this.summaryHelper.getIntentTemplate(intent.getLabel(),
        conversationEventEnvelope.getTenantId(), conversationEventEnvelope.getEnvironment(),
        conversationEventEnvelope.getConversationId(), summaryDoc, true);

    log.debug("createIntentSummary: {}", htmlTemplate);

    Map<String, Boolean> editedEntityMap = summaryDoc.getEntities().values().stream()
        .collect(Collectors.toMap(SummaryDoc.Slot::getId, SummaryDoc.Slot::isEdited));

    editedEntityMap.putAll(summaryDoc.getVariables().values().stream()
        .collect(Collectors.toMap(SummaryDoc.Slot::getId, SummaryDoc.Slot::isEdited)));

    Collection<SummaryDoc.Slot> variableValues = summaryDoc.getVariables().values();
    IntentSummaryDoc intentSummaryDoc =
        IntentSummaryDoc.builder().intentId(intent.getLabel()).instanceId(intent.getInstanceId())
            .source(intent.getDetectedBy()).template(htmlTemplate)
            .plainText(HtmlParser.extractPlainText(htmlTemplate)).entityInstanceIdMap(
                summaryDoc.getEntities().values().stream()
                    .collect(Collectors.toMap(SummaryDoc.Slot::getId, SummaryDoc.Slot::getInstanceId)))
            .variableInstanceIdMap(variableValues.stream()
                .collect(Collectors.toMap(SummaryDoc.Slot::getId, SummaryDoc.Slot::getInstanceId)))
            .timestamp(DateUtil.toTimestamp(intent.getTimestamp()))
            .entityIdIsEdited(editedEntityMap).build();

    Map<String, Boolean> unEdtedEntityMap = editedEntityMap.entrySet().stream()
        .filter(stringBooleanEntry -> !stringBooleanEntry.getValue())
        .collect(Collectors.toMap(Map.Entry::getKey, stringBooleanEntry1 -> false));

    summaryDoc.getIntentSummaries().put(intent.getInstanceId(), intentSummaryDoc);
    if (systemSummaryffEnabled) {
      IntentSummaryDoc summaryIntentSummaryDoc =
          IntentSummaryDoc.builder().intentId(intent.getLabel()).instanceId(intent.getInstanceId())
              .source(intent.getDetectedBy()).template(systemHtmlTemplate)
              .plainText(HtmlParser.extractPlainText(systemHtmlTemplate)).entityInstanceIdMap(
                  summaryDoc.getEntities().values().stream().collect(
                      Collectors.toMap(SummaryDoc.Slot::getId, SummaryDoc.Slot::getInstanceId)))
              .variableInstanceIdMap(variableValues.stream().collect(
                  Collectors.toMap(SummaryDoc.Slot::getId, SummaryDoc.Slot::getInstanceId)))
              .timestamp(DateUtil.toTimestamp(intent.getTimestamp()))
              .entityIdIsEdited(unEdtedEntityMap).build();
      summaryDoc.getSystemIntentSummaries().put(intent.getInstanceId(), summaryIntentSummaryDoc);
    }
    intentSummaryDocs.add(intentSummaryDoc);
    if (intent.getDetectedBy().equalsIgnoreCase("") || intent.getDetectedBy()
        .equalsIgnoreCase(Constants.USER)) {
      this.agentEditsRepository.save(
          DocConverter.agentAgentEditsDoc(summaryDoc, intentSummaryDoc, timestamp,
              Commons.AgentAction.INTENT_ADD.toString()));
      detectedBy = Constants.DETECTED_BY_AGENT;
      log.debug("agent added intent is processed");
    }
    eventData.add(
        SummaryUpdateEventLog.EventData.builder().eventId(intent.getLabel()).headerEffected(false)
            .value(intent.getLabel()).instanceId(intent.getInstanceId()).detectedBy(detectedBy)
            .effectedIntents(Collections.singletonList(intent.getInstanceId())).build());
  }

  private void handleIntentsDeletedEvent(ConversationEventEnvelope conversationEventEnvelope,
      final long eventStartTime) {
    ContextSetter.setContextWithSpan("uassist-summary", "event-intent-deleted",
        conversationEventEnvelope.getConversationId(), conversationEventEnvelope.getTenantId(),
        conversationEventEnvelope.getEnvironment());
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    boolean ffEnabled = this.ldHelper.isFFEnabled(ENABLE_INTENT_BOUNDARY, FF_CONTEXT_KIND);
    log.info("Intent boundary enabled: {}", ffEnabled);
    final ConversationEventEnvelope.IntentDeleted intentDeleted =
        conversationEventEnvelope.getIntentDeleted();
    String currentIntent = this.intentTemplateCacheHelper.getCurrentIntentForConversation(
        conversationEventEnvelope.getConversationId());

    if (ffEnabled) {
      if (StringUtils.isNotEmpty(currentIntent) && currentIntent.equalsIgnoreCase(
          intentDeleted.getIntent().getInstanceId())) {
        this.intentTemplateCacheHelper.popIntentFromIntentBoundaryStack(
            conversationEventEnvelope.getConversationId());
      } else {
        final List<String> newIntentboundaryStack = new ArrayList<>();
        final List<String> allIntentBoundaryStack =
            this.intentTemplateCacheHelper.getAllIntentBoundaryStack(
                conversationEventEnvelope.getConversationId());
        this.intentTemplateCacheHelper.deleteIntentBoundaryCache(
            conversationEventEnvelope.getConversationId());
        int stackLenght = allIntentBoundaryStack.toArray().length;
        int currentIndex = stackLenght - 1;
        for (int i = 0; i <= stackLenght - 1; i++) {
          if (!intentDeleted.getIntent().getInstanceId()
              .equalsIgnoreCase(allIntentBoundaryStack.get(i))) {
            newIntentboundaryStack.add(currentIndex, allIntentBoundaryStack.get(i));
          }
          currentIndex--;
        }
      }
    }
    SummaryDoc summaryDoc =
        this.summaryHelper.getSummaryDoc(conversationEventEnvelope.getConversationId());
    summaryDoc.getIntentSummaries().remove(intentDeleted.getIntent().getInstanceId());

    eventData.add(
        SummaryUpdateEventLog.EventData.builder().eventId(intentDeleted.getIntent().getLabel())
            .headerEffected(false).value(intentDeleted.getIntent().getLabel())
            .instanceId(intentDeleted.getIntent().getInstanceId())
            .detectedBy(Constants.DETECTED_BY_AI)
            .effectedIntents(Collections.singletonList(intentDeleted.getIntent().getInstanceId()))
            .build());
    SummaryUpdateEventLog summaryUpdateEventLog =
        this.summaryHelper.getSummaryUpdateEventLog(conversationEventEnvelope.getConversationId(),
            eventStartTime, Constants.TAG_INTENT_DELETE, eventData, summaryDoc.toString(),
            conversationEventEnvelope.getBusinessProcessId());
    this.summaryHelper.saveSummaryDoc(summaryDoc);
    log.debug("handleIntentsDeleted:: summary doc after update: {}", summaryDoc);
    this.summaryHelper.saveSummaryEventDoc(summaryUpdateEventLog);
    this.customMetricsPublisher.updateAndLatencyMap(Constants.SUMMARY_INTENT_PROCESSED,
        eventStartTime,
        ImmutableMap.of(Constants.TAG_TYPE, Constants.SUCCESS, Constant.CONVERSATION__ID,
            conversationEventEnvelope.getConversationId(), "intent_process_type",
            "intent_deleted"));
  }

  private void handleConversationStartedEvent(ConversationEventEnvelope conversationEventEnvelope,
      final long eventStartTime) {
    try {
      ContextSetter.setContextWithSpan("uassist-summary", "event-conversation-start",
          conversationEventEnvelope.getConversationId(), conversationEventEnvelope.getTenantId(),
          conversationEventEnvelope.getEnvironment());

      final boolean multiSegmentSupportFF =
          this.ldHelper.isFFEnabled(MULTI_SEGMENT_SUPPORT, FF_CONTEXT_KIND);
      if (multiSegmentSupportFF) {
        try {
          SummaryDoc summaryDoc =
              this.summaryHelper.getSummaryDoc(conversationEventEnvelope.getConversationId());
          if (summaryDoc != null) {
            // if the summary doc already exists for this call and multi segment call is turned on
            // for this tenant, skip conv start event work
            log.warn("Next leg of a multi segmented call for conversation id {} has started",
                conversationEventEnvelope.getConversationId());
            return;
          }
        } catch (CustomException caught) {
          log.info("Starting a new conversation for id {}",
              conversationEventEnvelope.getConversationId());
        }
      }

      String headerTemplate =
          this.getHeaderTemplate(conversationEventEnvelope.getBusinessProcessId(),
              conversationEventEnvelope.getTenantId(), conversationEventEnvelope.getEnvironment(),
              conversationEventEnvelope.getConversationId());
      final boolean isStateV2FfEnabled = ldHelper.isFFEnabled(Constants.UASSIST_STATE_V2_UPDATE_FF,
          LaunchDarklyFeatureFlags.FF_CONTEXT_KIND);
      SummaryDoc summaryDoc =
          DocConverter.getSummaryDoc(conversationEventEnvelope, headerTemplate, isStateV2FfEnabled);
      SummaryUpdateEventLog summaryUpdateEventLog =
          this.summaryHelper.getSummaryUpdateEventLog(conversationEventEnvelope.getConversationId(),
              eventStartTime, Constants.TAG_CONVERSATION_START, new ArrayList<>(),
              summaryDoc.toString(), conversationEventEnvelope.getBusinessProcessId());

      if (this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
          FF_CONTEXT_KIND)) {
        final Optional<GenAISummaryCache> genAISummaryCache =
            this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(
                conversationEventEnvelope.getExperienceId());

        if (genAISummaryCache.isEmpty()) {
          log.debug(
              "ConversationStarted Event: Gen-AI config not found for experienceId: {}, conversationId: {}",
              conversationEventEnvelope.getExperienceId(),
              conversationEventEnvelope.getConversationId());
        } else {
          if (CollectionUtils.isEmpty(genAISummaryCache.get().getCallTypeTemplateMappings())
              || genAISummaryCache.get().getCallTypeTemplateMappings().isEmpty()) {
            log.debug(
                "ConversationStarted Event: Intent to template mappings is not configured for expId: {} and hence"
                    + " falling back to standard Gen-AI summary generation mechanism, conversationId: {}",
                conversationEventEnvelope.getExperienceId(),
                conversationEventEnvelope.getConversationId());
          } else {
            log.debug(
                "ConversationStarted Event: Intent to template mappings is configured for expId: {}, conversationId: {}, mappings: {}",
                conversationEventEnvelope.getExperienceId(),
                conversationEventEnvelope.getConversationId(),
                genAISummaryCache.get().getCallTypeTemplateMappings());
            final GenAISummaryCache.CallTypeTemplateMapping defaultTemplateMapping =
                genAISummaryCache.get().getCallTypeTemplateMappings().get(0);
            summaryDoc.setIntentLabel(defaultTemplateMapping.getIntentLabel());
            summaryDoc.setTemplateId(defaultTemplateMapping.getTemplateId());
            summaryDoc.setTemplateVersion(defaultTemplateMapping.getVersion());
            summaryDoc.setTemplateAssignedBy(TemplateConfigurer.CONVERSATION_START_EVENT);
            log.debug(
                "Summary template and Version to generate Gen-AI summary was assigned by : {}, conversationId: {}",
                TemplateConfigurer.CONVERSATION_START_EVENT,
                conversationEventEnvelope.getConversationId());
          }
        }
      }

      this.summaryHelper.saveAndPublish(summaryDoc,
          ProtoConverter.headerTemplateToSummaryProto(conversationEventEnvelope, headerTemplate,
              new HashMap<>(), new HashMap<>()), summaryUpdateEventLog);
      this.customMetricsPublisher.updateAndLatencyMap(Constants.SUMMARY_CREATED, eventStartTime,
          ImmutableMap.of(Constants.TAG_TYPE, Constants.SUCCESS, Constant.CONVERSATION__ID,
              conversationEventEnvelope.getConversationId()));
    } catch (Exception e) {
      log.error("Exception occurred for the ConversationStarted Event error: ", e);
      this.customMetricsPublisher.counterUpdateMap(Constants.SUMMARY_CREATED,
          ImmutableMap.of(Constants.TAG_TYPE, Constants.SUCCESS, Constant.CONVERSATION__ID,
              conversationEventEnvelope.getConversationId()));
      ContextSetter.setContextWithSpanError(e);
      throw e;
    }
  }

  private String getHeaderTemplate(String businessProcessId, String tenantId, String environment,
      String converastionId) {
    String headerTemplate = headerTemplateCacheHelper.getHeaderTemplate(businessProcessId);

    // dynamic cache
    if (headerTemplate == null) {
      headerTemplate = this.cacheRefreshHelper.refreshHeaderTemplate(
          CacheRefreshBean.builder().businessProcessId(businessProcessId).build());
    }

    if (StringUtils.isEmpty(headerTemplate)) {
      log.debug(
          "ConversationStartedEvent TenantId : {}, environment : {} ConverastionId : {} header template is not configured for BusinessProcessId :{}",
          tenantId, environment, converastionId, businessProcessId);
      headerTemplate = StringUtils.EMPTY;
    } else {
      headerTemplate = HtmlParser.setDefaultData(headerTemplate, Constants.TBD);
    }
    return headerTemplate;
  }

  private void handleVariableEvent(ConversationEventEnvelope conversationEventEnvelope,
      List<Variable> variablesList, final long eventStartTime, final String event) {
    ContextSetter.setContextWithSpan("uassist-summary",
        String.format("%s-%s", "event-variable", event),
        conversationEventEnvelope.getConversationId(), conversationEventEnvelope.getTenantId(),
        conversationEventEnvelope.getEnvironment());
    List<SummaryUpdateEventLog.EventData> processedVariables = new ArrayList<>();
    boolean ffEnabled = this.ldHelper.isFFEnabled(ENABLE_INTENT_BOUNDARY, FF_CONTEXT_KIND);
    log.info("Intent boundary enabled: {}", ffEnabled);
    boolean systemSummaryffEnabled =
        this.ldHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND);
    log.debug("System generated summary ff enabled: {}", systemSummaryffEnabled);
    boolean genAiEntitiesEnhancementsEnabled =
        this.ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND);
    log.debug("GenAI entities enhancements enabled: {}", genAiEntitiesEnhancementsEnabled);
    Header header = null;
    Map<String, IntentSummary> intentSummaryMap = new HashMap<>();
    boolean publish = false;
    boolean isGenAiUpdated = false;
    try {
      SummaryDoc summaryDoc = this.getSummaryDocFromCache(conversationEventEnvelope);
      log.debug("VariableAddedEvent:: ConvID:= {}   variable: {} , summary doc before update: {}",
          conversationEventEnvelope.getConversationId(), variablesList, summaryDoc);

      List<String> effectedIntents = new ArrayList<>();
      for (Variable variable : variablesList) {
        log.debug("VariableAddedEvent:: ConvID:= {} VARIABLE_DATA:= {}",
            conversationEventEnvelope.getConversationId(), variable);
        boolean isAgentEdit = false;
        SummaryUpdateEventLog.EventData eventData =
            SummaryUpdateEventLog.EventData.builder().eventId(variable.getVariableId())
                .value(variable.getValue()).instanceId(variable.getVariableInstanceId())
                .effectedIntents(effectedIntents).headerEffected(false).detectedBy(
                    variable.getSourceType().equals(VariableSourceType.VARIABLE_SOURCE_UNKNOWN)
                        ? Constants.DETECTED_BY_AGENT
                        : Constants.DETECTED_BY_AI).build();

        log.debug("VariableAddedEvent:: ConvID:= {} VARIABLE_SOURCE:= {}",
            conversationEventEnvelope.getConversationId(), variable.getSourceType());
        // intent summary process
        if (variable.getSourceType()
            .equals(VariableSourceType.VARIABLE_SOURCE_INTENT_CALL_REASON)) {
          isAgentEdit = true;
          log.debug("VariableAddedEvent:: ConvID:= {} VARIABLE_SOURCE_INTENT_CALL_REASON:= {}",
              conversationEventEnvelope.getConversationId(), variable);
          this.summaryHelper.updateIntentSummary(summaryDoc, variable.getVariableId(),
              variable.getValue(), variable.getVariableInstanceId(), variable.getSourceInstanceId(),
              HtmlParser.VARIABLE, intentSummaryMap, isAgentEdit);
        } else {
          if (!variable.getSourceType().equals(VariableSourceType.VARIABLE_SOURCE_ENTITY)) {
            isAgentEdit = true;
            log.debug(
                "VariableAddedEvent:: ConvID:= {} VARIABLE_SOURCE_ENTITY:= {} SOURCE_TYPE:= {}",
                conversationEventEnvelope.getConversationId(), variable, variable.getSourceType());
          }
          log.debug(
              "VariableAddedEvent:: ConvID:= {} VARIABLE_SOURCE_OTHER_THAN_NOT_ENTITY:= {} and isEdited:= {} SOURCE_TYPE:= {}",
              conversationEventEnvelope.getConversationId(), variable, isAgentEdit,
              variable.getSourceType());
          intentSummaryMap.putAll(
              this.checkAndUpdateIntentSummary(conversationEventEnvelope.getConversationId(),
                  summaryDoc, variable.getVariableId(), variable.getValue(),
                  variable.getVariableInstanceId(), HtmlParser.VARIABLE, effectedIntents,
                  isAgentEdit));
        }

        Header localHeader = null;
        // header summary check
        if (variable.getSourceType().equals(VariableSourceType.VARIABLE_SOURCE_HEADER_SUMMARY)) {
          localHeader = this.summaryHelper.updateHeaderSummary(summaryDoc, variable.getVariableId(),
              variable.getValue(), variable.getVariableInstanceId(), HtmlParser.VARIABLE,
              conversationEventEnvelope.getTimestamp());
        } else {
          localHeader =
              this.checkAndUpdateHeaderSummary(conversationEventEnvelope.getConversationId(),
                  HtmlParser.VARIABLE, variable.getVariableId(), variable.getValue(),
                  variable.getVariableInstanceId(),
                  conversationEventEnvelope.getBusinessProcessId(), summaryDoc,
                  conversationEventEnvelope.getTimestamp());
        }
        if (localHeader != null) {
          eventData.setHeaderEffected(true);
          header = localHeader;
        }

        if (genAiEntitiesEnhancementsEnabled) {
          if (variable.getSourceType().equals(VariableSourceType.VARIABLE_SOURCE_GEN_AI_SUMMARY)) {
            log.debug("VariableAddedEvent:: ConvID:= {} VARIABLE_SOURCE_GEN_AI_SUMMARY:= {}",
                conversationEventEnvelope.getConversationId(), variable);
            isAgentEdit = true;
            isGenAiUpdated =
                this.summaryHelper.updateGenAISummary(summaryDoc, variable.getVariableId(),
                    variable.getValue(), variable.getVariableInstanceId(), isAgentEdit);
          } else {
            if (variable.getSourceType().equals(VariableSourceType.VARIABLE_SOURCE_UNKNOWN)) {
              log.debug("VariableAddedEvent:: ConvID:= {} VARIABLE_SOURCE_UNKNOWN:= {}",
                  conversationEventEnvelope.getConversationId(), variable);
              isAgentEdit = false;
            }
            log.debug(
                "VariableAddedEvent:: ConvID:= {} VARIABLE_SOURCE_UNKNOWN:= {} and isEdited:= {}",
                conversationEventEnvelope.getConversationId(), variable, isAgentEdit);
            isGenAiUpdated = this.summaryHelper.checkAndUpdateGenAISummary(summaryDoc,
                conversationEventEnvelope.getExperienceId(), variable.getVariableId(),
                variable.getValue(), variable.getVariableInstanceId(), isAgentEdit);
          }
        }
        processedVariables.add(eventData);
        buildSlot(variable, summaryDoc, isAgentEdit);
      }

      if (!intentSummaryMap.isEmpty() || header != null || isGenAiUpdated) {
        publish = true;
      }
      log.debug(
          "VariableAddedEvent:: summary doc after update: {},variable:{}, header updated: {}, intents updated: {}, publish: {}",
          summaryDoc, variablesList, header, intentSummaryMap, publish);
      if (publish) {
        SummaryUpdateEventLog summaryUpdateEventLog = this.summaryHelper.getSummaryUpdateEventLog(
            conversationEventEnvelope.getConversationId(), eventStartTime,
            Constants.TAG_VARIABLE_ADD, processedVariables, summaryDoc.toString(),
            conversationEventEnvelope.getBusinessProcessId());
        this.summaryHelper.saveAndPublish(summaryDoc,
            ProtoConverter.getSummaryProto(summaryDoc, header, intentSummaryMap,
                Collections.emptyList(), false), summaryUpdateEventLog);
      } else {
        this.summaryHelper.saveSummaryDoc(summaryDoc);
      }

      this.customMetricsPublisher.updateAndLatencyMap(Constants.SUMMARY_VARIABLE_PROCESSED,
          eventStartTime,
          ImmutableMap.of(Constant.TAG_TYPE, Constants.SUCCESS, Constant.CONVERSATION__ID,
              conversationEventEnvelope.getConversationId()));

    } catch (Exception e) {
      this.customMetricsPublisher.counterUpdateMap(Constants.SUMMARY_VARIABLE_PROCESSED,
          ImmutableMap.of(Constant.TAG_TYPE, Constants.FAILED, Constant.CONVERSATION__ID,
              conversationEventEnvelope.getConversationId()));
      ContextSetter.setContextWithSpanError(e);
      log.error("VariablesAdded Event error : ", e);
      throw e;
    }
  }

  private static void buildSlot(Variable variable, SummaryDoc summaryDoc, boolean isAgentEdit) {
    summaryDoc.getVariables().put(variable.getVariableId(),
        SummaryDoc.Slot.builder().id(variable.getVariableId())
            .instanceId(variable.getVariableInstanceId()).value(variable.getValue())
            .isEdited(isAgentEdit).build());
  }

  SummaryDoc getSummaryDocFromCache(ConversationEventEnvelope conversationEventEnvelope)
      throws CustomException {
    return this.summaryHelper.getSummaryDoc(conversationEventEnvelope.getConversationId());
  }

  @Override
  public SummaryResponse fetchSummary(String conversationId) {
    TenantPojo tenantPojo = TenantUtils.getTenantDetails();
    ContextSetter.setContextWithSpan("uassist-summary", "fetchSummary", conversationId,
        tenantPojo.getTenantId(), tenantPojo.getEnvironment());
    log.info("fetchSummary tenantId : {}, environment : {} ConverastionId : {}",
        tenantPojo.getTenantId(), tenantPojo.getEnvironment(), conversationId);
    try {
      boolean ffEnabled = this.ldHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND);
      log.debug(LOG_AI_GENERATED_SUMMARY_FF_ENABLED, ffEnabled);
      SummaryDoc summaryDoc = this.summaryHelper.getSummaryDoc(conversationId);
      return convertToSummaryResponse(summaryDoc, ffEnabled);
    } catch (CustomException e) {
      handleFetchSummaryError(e, "fetchSummary");
      throw e;
    } catch (Exception e) {
      handleFetchSummaryError(e, "fetchSummary");
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }finally {
      ContextSetter.clearContext();
    }
  }

  @Override
  public List<SummaryResponse> fetchSummaries(List<String> conversationIds) {
    TenantPojo tenantPojo = TenantUtils.getTenantDetails();
    ContextSetter.setContextWithSpan("uassist-summary", "fetchSummaries",
        String.join(",", conversationIds), tenantPojo.getTenantId(), tenantPojo.getEnvironment());
    log.info("fetchSummaries tenantId : {}, environment : {} ConversationIds : {}",
        tenantPojo.getTenantId(), tenantPojo.getEnvironment(), conversationIds);
    try {
      boolean ffEnabled = this.ldHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND);
      log.debug(LOG_AI_GENERATED_SUMMARY_FF_ENABLED, ffEnabled);
      List<SummaryDoc> summaryDocs = this.summaryHelper.getSummaryDocs(conversationIds);
      return summaryDocs.stream()
          .map(doc -> convertToSummaryResponse(doc, ffEnabled))
          .collect(Collectors.toList());
    } catch (CustomException e) {
      handleFetchSummaryError(e, "fetchSummaries");
      throw e;
    } catch (Exception e) {
      handleFetchSummaryError(e, "fetchSummaries");
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }finally {
      ContextSetter.clearContext();
    }
  }

  private SummaryResponse convertToSummaryResponse(SummaryDoc summaryDoc, boolean ffEnabled) {
    return ffEnabled ? DocConverter.getSystemSummary(summaryDoc) : DocConverter.getSummary(summaryDoc);
  }

  private void handleFetchSummaryError(Exception e, String methodName) {
    ContextSetter.setContextWithSpanError(e);
    if (e instanceof CustomException) {
      log.debug("{} error : ", methodName, e);
    } else {
      log.error("{} error : ", methodName, e);
    }
  }

  @Override
  public SummaryDoc createGenAiSummary(String conversationId, String tenantId,
      Optional<String> experienceIdOpt, boolean formatEnable, final Optional<String> callTypeOpt) {
    String callType = null;
    long eventTime = DateUtil.TIME.millis();
    TenantPojo tenantPojo = TenantUtils.getTenantDetails();
    if (callTypeOpt.isPresent()) {
      callType = callTypeOpt.get();
    }
    ContextSetter.setContextWithSpan("uassist-summary", "createGenAiSummary", conversationId,
        tenantPojo.getTenantId(), tenantPojo.getEnvironment());
    log.info(
        "genAI on-demand summary requested: TenantId : {}, environment : {} ConverastionId : {}",
        tenantPojo.getTenantId(), tenantPojo.getEnvironment(), conversationId);
    SummaryDoc genAiSummary = null;
    genAiSummary =
        this.genAISummaryService.createGenAiSummary(conversationId, tenantId, experienceIdOpt,
            false, formatEnable, System.currentTimeMillis(), callType);

    log.debug("response before controller {}", genAiSummary);
    return genAiSummary;
  }

  @Override
  public List<SummaryUpdateEventLog> fetchSummaryEventLog(String conversationId) {
    return this.summaryRuntimeDao.getSummaryEventLog(conversationId);
  }

  @Override
  public SummaryReportResponse getSummaryReportByConversationId(String conversationId) {
    final List<SummaryReportResponse.IntentSummary> intentSummaries = new ArrayList<>();
    SummaryReportResponse.HeaderSummary headerSummary =
        SummaryReportResponse.HeaderSummary.builder().build();
    boolean ffEnabled = this.ldHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND);
    log.debug("getSummaryReportByConversationId:: SYSTEM_TEMPLATE_SUMMARY_FLAG {}", ffEnabled);
    Optional<SummaryDoc> summaryDoc = this.summaryRuntimeDao.getSummaryDoc(conversationId);
    try {
      if (summaryDoc.isPresent()) {
        if (ffEnabled) {
          SummaryDoc summary = summaryDoc.get();
          Map<String, IntentSummaryDoc> finalSummaries = summary.getIntentSummaries();
          Map<String, IntentSummaryDoc> systemIntentSummaries = summary.getSystemIntentSummaries();

          finalSummaries.forEach((key, finalSummaryDoc) -> {
            if (systemIntentSummaries.containsKey(key)) {
              IntentSummaryDoc systemGeneratedDoc = systemIntentSummaries.get(key);
              Map<String, SummaryReportResponse.Entity> systemEntityValue =
                  HtmlParser.extractValue(systemGeneratedDoc.getTemplate()).stream()
                      .filter(e -> e.getType().equalsIgnoreCase(HtmlParser.ENTITY))
                      .filter(e -> StringUtils.isNotEmpty(e.getName())).map(
                          e -> SummaryReportResponse.Entity.builder().id(e.getId()).value(e.getName())
                              .detectedBy(Constants.DETECTED_BY_AI).aiValue(e.getName()).build())
                      .collect(Collectors.toMap(SummaryReportResponse.Entity::getId,
                          Function.identity()));
              Map<String, SummaryReportResponse.Entity> systemVariableValue =
                  HtmlParser.extractValue(systemGeneratedDoc.getTemplate()).stream()
                      .filter(e -> e.getType().equalsIgnoreCase(HtmlParser.VARIABLE))
                      .filter(e -> StringUtils.isNotEmpty(e.getName())).map(
                          e -> SummaryReportResponse.Entity.builder().id(e.getId()).value(e.getName())
                              .detectedBy(Constants.DETECTED_BY_AI).aiValue(e.getName()).build())
                      .collect(Collectors.toMap(SummaryReportResponse.Entity::getId,
                          Function.identity()));
              log.debug(
                  "getSummaryReportByConversationId::template: {} html extracted data: {} system entity value: {}, system variable values: {}",
                  systemGeneratedDoc.getTemplate(),
                  HtmlParser.extractValue(systemGeneratedDoc.getTemplate()), systemEntityValue,
                  systemVariableValue);
              List<SummaryReportResponse.Entity> finalEntities =
                  HtmlParser.extractValue(finalSummaryDoc.getTemplate()).stream()
                      .filter(e -> e.getType().equalsIgnoreCase(HtmlParser.ENTITY))
                      .filter(e -> StringUtils.isNotEmpty(e.getName()))
                      .map(e -> this.getEntity(e, systemEntityValue)).collect(Collectors.toList());
              List<SummaryReportResponse.Entity> finalVariables =
                  HtmlParser.extractValue(finalSummaryDoc.getTemplate()).stream()
                      .filter(e -> e.getType().equalsIgnoreCase(HtmlParser.VARIABLE))
                      .filter(e -> StringUtils.isNotEmpty(e.getName()))
                      .map(e -> this.getEntity(e, systemVariableValue))
                      .collect(Collectors.toList());
              log.debug(
                  "getSummaryReportByConversationId:: template: {} html extracted data: {} system entity value: {}, system variable values: {}",
                  finalSummaryDoc.getTemplate(),
                  HtmlParser.extractValue(finalSummaryDoc.getTemplate()), finalEntities,
                  finalVariables);
              SummaryReportResponse.IntentSummary intentSummary =
                  SummaryReportResponse.IntentSummary.builder()
                      .submittedSummary(finalSummaryDoc.getPlainText())
                      .systemSummary(systemGeneratedDoc.getPlainText()).isEdited(
                          !finalSummaryDoc.getPlainText()
                              .equalsIgnoreCase(systemGeneratedDoc.getPlainText()))
                      .intentId(finalSummaryDoc.getIntentId()).instanceId(key)
                      .entities(finalEntities).variables(finalVariables).build();
              intentSummaries.add(intentSummary);
            } else {
              List<SummaryReportResponse.Entity> finalEntities =
                  HtmlParser.extractValue(finalSummaryDoc.getTemplate()).stream()
                      .filter(e -> e.getType().equalsIgnoreCase(HtmlParser.ENTITY))
                      .filter(e -> StringUtils.isNotEmpty(e.getValue())).map(e -> {
                        return this.getEntity(e, new HashMap<>());
                      }).collect(Collectors.toList());
              List<SummaryReportResponse.Entity> finalVariables =
                  HtmlParser.extractValue(finalSummaryDoc.getTemplate()).stream()
                      .filter(e -> e.getType().equalsIgnoreCase(HtmlParser.VARIABLE))
                      .filter(e -> StringUtils.isNotEmpty(e.getValue())).map(e -> {
                        return this.getEntity(e, new HashMap<>());
                      }).collect(Collectors.toList());
              SummaryReportResponse.IntentSummary intentSummary =
                  SummaryReportResponse.IntentSummary.builder()
                      .intentId(finalSummaryDoc.getIntentId()).instanceId(key).isEdited(true)
                      .submittedSummary(finalSummaryDoc.getPlainText()).entities(finalEntities)
                      .variables(finalVariables).build();
              intentSummaries.add(intentSummary);
            }
          });

        } else {

          List<SummaryEventAggrByEvent> summaryEventAggrByEvents =
              this.summaryRuntimeDao.getSummaryAggrByEventForConvId(conversationId);
          log.debug("summaryEventAggrByEvents for convId: {},data: {}", conversationId,
              summaryEventAggrByEvents);

          Map<String, SummaryEventAggrByEvent> eventDataMap = summaryEventAggrByEvents.stream()
              .collect(Collectors.toMap(SummaryEventAggrByEvent::getEvent, Function.identity()));

          Map<String, List<SummaryUpdateEventLog>> entityVariableEventMap =
              eventDataMap.entrySet().stream().filter(
                      e -> e.getKey().equalsIgnoreCase(Constants.TAG_ENTITY_ADD) || e.getKey()
                          .equalsIgnoreCase(Constants.TAG_VARIABLE_ADD))
                  .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getEventData()));

          Map<String, List<SummaryUpdateEventLog>> intentEventMap = eventDataMap.entrySet().stream()
              .filter(e -> e.getKey().equalsIgnoreCase(Constants.TAG_INTENT_ADD) || e.getKey()
                  .equalsIgnoreCase(Constants.TAG_INTENT_ADD_AGENT))
              .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getEventData()));

          Map<String, List<SummaryUpdateEventLog>> headerTemplateEvents =
              entityVariableEventMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
                  e -> e.getValue().stream()
                      .filter(k -> k.getEventData().stream().anyMatch(h -> h.isHeaderEffected()))
                      .collect(Collectors.toList())));
          headerSummary =
              this.createHeaderTemplateReport(summaryDoc.get().getHeaderSummary().getPlainText(),
                  headerTemplateEvents);
          if (MapUtils.isNotEmpty(summaryDoc.get().getIntentSummaries())) {
            this.createIntentSummaryReport(summaryDoc, entityVariableEventMap, intentEventMap,
                intentSummaries);
          }
        }
      } else {
        log.info("generating summary report convId: {}, summary not found:", conversationId);
      }

    } catch (Exception e) {
      log.error("error at generating summary report convId: {}, ", conversationId, e);
    }
    return SummaryReportResponse.builder().headerSummary(headerSummary)
        .intentSummaries(intentSummaries).build();
  }

  private SummaryReportResponse.Entity getEntity(HtmlExtractData e,
      Map<String, SummaryReportResponse.Entity> systemEntityValue) {
    if (systemEntityValue.containsKey(e.getId())) {
      SummaryReportResponse.Entity entity = systemEntityValue.get(e.getId());
      entity.setValue(e.getName());
      entity.setDetectedBy(entity.getValue().equalsIgnoreCase(entity.getAiValue())
          ? Constants.DETECTED_BY_AI
          : Constants.DETECTED_BY_AGENT);
      return entity;
    } else {
      return SummaryReportResponse.Entity.builder().id(e.getId()).value(e.getName())
          .detectedBy(Constants.DETECTED_BY_AGENT).build();
    }
  }

  private void createIntentSummaryReport(Optional<SummaryDoc> summaryDoc,
      Map<String, List<SummaryUpdateEventLog>> entityVariableEventMap,
      Map<String, List<SummaryUpdateEventLog>> intentEventMap,
      final List<SummaryReportResponse.IntentSummary> intentSummaries) {
    // defining the response here
    try {

      // collect the instanceIds of the intents stored in summary template
      Set<String> finalIntentInstanceIds = summaryDoc.get().getIntentSummaries().keySet();

      if (CollectionUtils.isNotEmpty(finalIntentInstanceIds)) {
        // capture the intent instance Id and start and end time stamp for boundary
        Map<String, List<Long>> intentTimestampMap = new HashMap<>();
        // capture the intent vs entity and variable ids
        Map<String, List<String>> intentInstanceIdEntityIdMap = new HashMap<>();
        entityVariableEventMap.values().stream().flatMap(List::stream)
            .flatMap(e -> e.getEventData().stream()).forEach(e -> {
              e.getEffectedIntents().forEach(i -> {
                List<String> value = intentInstanceIdEntityIdMap.getOrDefault(i, new ArrayList<>());
                value.add(e.getEventId());
                intentInstanceIdEntityIdMap.put(i, value);
              });
            });
        log.debug("createIntentSummaryReport intentInstanceIdEntityIdMap: {}",
            intentInstanceIdEntityIdMap);
        // map to store final intents add event data from all intent added events
        Map<String, SummaryUpdateEventLog> finalIntentInstanceIdEventData = new HashMap<>();

        finalIntentInstanceIds.stream().forEach(instanceId -> {
          var thing = intentEventMap.values().stream().flatMap(List::stream).filter(
              i -> i.getEventData().stream()
                  .anyMatch(e -> e.getInstanceId().equalsIgnoreCase(instanceId))).findFirst().get();
          finalIntentInstanceIdEventData.put(instanceId, thing);
        });

        log.debug("createIntentSummaryReport final intents: {}", finalIntentInstanceIdEventData);

        // sort the time stamps of all final intent data events
        List<Long> timestampsOfIntent = finalIntentInstanceIdEventData.values().stream()
            .map(SummaryUpdateEventLog::getEventTime).collect(Collectors.toList());
        timestampsOfIntent.sort(Long::compareTo);

        log.debug("createIntentSummaryReport sorted timestamps of intents: {}", timestampsOfIntent);
        // capture the start and end times stamp of intents on instaneId
        finalIntentInstanceIdEventData.values().stream().forEach(e -> {
          List<Long> timeStamp = new ArrayList<>();
          List<String> instanceIds =
              e.getEventData().stream().map(SummaryUpdateEventLog.EventData::getInstanceId)
                  .collect(Collectors.toList());
          timeStamp.add(e.getEventTime());
          Optional<Long> nextIntentEventTime =
              timestampsOfIntent.stream().filter(t -> t > e.getEventTime()).findFirst();
          if (nextIntentEventTime.isPresent()) {
            timeStamp.add(nextIntentEventTime.get());
          } else {
            timeStamp.add(Long.MAX_VALUE);
          }
          instanceIds.forEach(i -> intentTimestampMap.put(i, timeStamp));
        });
        log.debug("createIntentSummaryReport intent timestamp boundary map: {}",
            intentTimestampMap);

        Map<String, String> intentInstanceidPlainTextMap =
            summaryDoc.get().getIntentSummaries().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getPlainText()));
        log.debug("createIntentSummaryReport summary intents {}",
            intentInstanceidPlainTextMap.keySet());
        intentInstanceidPlainTextMap.entrySet().forEach(e -> {
          List<SummaryReportResponse.Entity> entities = new ArrayList<>();
          List<SummaryReportResponse.Entity> variables = new ArrayList<>();
          SummaryUpdateEventLog currentProcessingIntentEvent =
              finalIntentInstanceIdEventData.get(e.getKey());
          SummaryUpdateEventLog.EventData eventData =
              currentProcessingIntentEvent.getEventData().stream()
                  .filter(d -> d.getInstanceId().equalsIgnoreCase(e.getKey())).findFirst()
                  .map(x -> x).orElse(null);
          long startTimefCurrentIntent = intentTimestampMap.get(e.getKey()).get(0).longValue();
          long endTimeOfCurrentIntent = intentTimestampMap.get(e.getKey()).get(1).longValue();

          this.extractMembersOfIntent(entityVariableEventMap, e.getKey(), entities,
              startTimefCurrentIntent, endTimeOfCurrentIntent, intentInstanceIdEntityIdMap,
              Constants.TAG_ENTITY_ADD);
          this.extractMembersOfIntent(entityVariableEventMap, e.getKey(), variables,
              startTimefCurrentIntent, endTimeOfCurrentIntent, intentInstanceIdEntityIdMap,
              Constants.TAG_VARIABLE_ADD);

          intentSummaries.add(
              SummaryReportResponse.IntentSummary.builder().submittedSummary(e.getValue())
                  .intentId(eventData.getEventId()).instanceId(eventData.getInstanceId())
                  .startTime(startTimefCurrentIntent).endTime(endTimeOfCurrentIntent)
                  .entities(entities).variables(variables).build());
        });
        log.debug("createIntentSummaryReport intent report: {}", intentSummaries);

      }

    } catch (Exception e) {
      log.error("exception at intent summary report: convId {} error {}",
          summaryDoc.get().getConversationId(), e);

    }

  }

  private static void extractMembersOfIntent(
      Map<String, List<SummaryUpdateEventLog>> entityVariableEventMap, String intentInstanceId,
      List<SummaryReportResponse.Entity> entities, long startTimeOfCurrentIntent,
      long endTimeOfCurrentIntent, Map<String, List<String>> intentInstanceIdEntityIdMap,
      final String type) {
    if (entityVariableEventMap.containsKey(type)) {
      List<String> memberIds = intentInstanceIdEntityIdMap.get(intentInstanceId);
      SummaryReportResponse.Entity startEntity = SummaryReportResponse.Entity.builder().build();
      final AtomicLong entitySmallestDistance = new AtomicLong(Long.MAX_VALUE);

      entityVariableEventMap.get(type).stream().forEach(k -> {

        if (k.getEventTime() < endTimeOfCurrentIntent) {
          long distance = startTimeOfCurrentIntent - k.getEventTime();
          log.debug("start entity update for convId: {},distance: {},intent: {},eventData: {}",
              k.getConversationId(), distance, intentInstanceId, k);
          if (distance > 0) {
            if (distance < entitySmallestDistance.get()) {
              k.getEventData().stream().filter(g -> memberIds.contains(g.getEventId()))
                  .forEach(g -> {
                    log.debug(
                        "start entity update for convId: {},distance: {},intent: {},entity: {}",
                        k.getConversationId(), distance, intentInstanceId, g);
                    entitySmallestDistance.set(distance);
                    startEntity.setId(g.getEventId());
                    startEntity.setInstanceId(g.getInstanceId());
                    startEntity.setValue(g.getValue());
                    startEntity.setDetectedBy(g.getDetectedBy());
                    startEntity.setEventTime(k.getEventTime());
                  });
            }
          } else {
            k.getEventData().stream().filter(g -> memberIds.contains(g.getEventId())).forEach(g -> {
              entities.add(SummaryReportResponse.Entity.builder().id(g.getEventId())
                  .instanceId(g.getInstanceId()).value(g.getValue()).eventTime(k.getEventTime())
                  .detectedBy(g.getDetectedBy()).build());
            });
          }
        }

      });
      entities.add(startEntity);
    }
  }

  private SummaryReportResponse.HeaderSummary createHeaderTemplateReport(String plainText,
      Map<String, List<SummaryUpdateEventLog>> headerTemplateEvents) {
    List<SummaryReportResponse.Entity> entities = new ArrayList<>();
    List<SummaryReportResponse.Entity> variables = new ArrayList<>();
    headerTemplateEvents.entrySet().stream().forEach(e -> {
      if (e.getKey().equalsIgnoreCase(Constants.TAG_ENTITY_ADD)) {
        e.getValue().stream().forEach(k -> {
          k.getEventData().stream().forEach(g -> entities.add(
              SummaryReportResponse.Entity.builder().id(g.getEventId())
                  .instanceId(g.getInstanceId()).eventTime(k.getEventTime()).value(g.getValue())
                  .detectedBy(g.getDetectedBy()).build()));
        });
      } else {
        e.getValue().stream().forEach(k -> {
          k.getEventData().stream().forEach(g -> variables.add(
              SummaryReportResponse.Entity.builder().id(g.getEventId())
                  .instanceId(g.getInstanceId()).eventTime(k.getEventTime()).value(g.getValue())
                  .detectedBy(g.getDetectedBy()).build()));
        });
      }
    });
    return SummaryReportResponse.HeaderSummary.builder().text(plainText).entities(entities)
        .variables(variables).build();
  }

  @Override
  public MessageResponse submitReviewedIntentSummaries(String conversationId,
      IntentSummaryReviewedRequest intentSummaryReviewedRequest) {
    log.debug("submitReviewedIntentSummaries: convId: {}, request: {}", conversationId,
        intentSummaryReviewedRequest);
    Optional<SummaryDoc> summaryDocOpt = this.summaryRuntimeDao.getSummaryDoc(conversationId);
    if (summaryDocOpt.isPresent()) {
      SummaryDoc summaryDoc = summaryDocOpt.get();
      HashMap<String, IntentSummaryDoc> intentSummaries = new HashMap<>();
      summaryDoc.setIntentSummaries(intentSummaries);
      intentSummaryReviewedRequest.getIntentSummaries().stream().forEach(i -> {
        IntentSummaryDoc summary =
            IntentSummaryDoc.builder().instanceId(i.getInstanceId()).intentId(i.getIntentId())
                .timestamp(DateUtil.toTimestamp(DateUtil.getCurrentTimestamp()))
                .template(i.getTemplate()).plainText(i.getPlainText())
                .disposition(i.getDisposition()).source(Constants.DETECTED_BY_AGENT).build();
        intentSummaries.put(i.getInstanceId(), summary);
      });
      this.summaryRuntimeDao.saveSummaryDoc(summaryDoc);
      return new MessageResponse(Constants.SAVE, conversationId);
    } else {
      return new MessageResponse("summary not found", ErrorCodes.INTENT_SUMMARY_NOT_FOUND);
    }
  }

  @Override
  public String saveAgentStep(String convId, String text) {
    log.debug("adding agent step to conversation id {} with text '{}'", convId, text);
    String id = UUID.randomUUID().toString();
    try {
      boolean eventProcessingEnabled =
          this.ldHelper.isFFEnabled(UASSIST_AGENT_EDITS_QUEUE_PROCESSING, FF_CONTEXT_KIND);
      AgentUpdateRequest agentUpdateRequest = AgentUpdateRequest.builder()
          .agentRequestPayload(AgentRequestPayload.builder().conversationId(convId).build())
          .agentAction(Commons.AgentAction.ADD_AGENT_STEP)
          .environment(TenantUtils.getTenantDetails().getEnvironment())
          .timestamp(DateUtil.TIME.millis()).conversationId(convId)
          .agentStepUpdate(AgentUpdateRequest.AgentStepUpdate.builder().id(id).value(text).build())
          .tenantId(TenantUtils.getTenantDetails().getTenantId()).build();
      if (eventProcessingEnabled) {
        this.conversationEventProcessingHelper.pushEventToConversationQueue(agentUpdateRequest);
      } else {
        this.processAgentUpdates(agentUpdateRequest, new ArrayList<>());
      }
      return id;
    } catch (Exception e) {
      log.error("error agent action add agent step {}", e);
      throw e;
    }
  }

  private void addedAgentStep(AgentUpdateRequest agentUpdateRequest, String text, String id,
      List<SummaryUpdateEventLog.EventData> eventData) {
    int index = this.agentStepsCacheHelper.getNextIndex(agentUpdateRequest.getConversationId());
    this.agentStepsCacheHelper.add(agentUpdateRequest.getConversationId(),
        AgentStepPojo.builder().id(id).text(text).index(index).build());
    log.debug("added agent step and published with id {} to conversation id {}", id,
        agentUpdateRequest.getConversationId());
    Optional<AgentStepsDoc> byConversationId =
        this.agentStepsRepo.findById(agentUpdateRequest.getConversationId());
    if (byConversationId.isPresent()) {
      AgentStepsDoc agentStepsDoc = byConversationId.get();
      Map<String, AgentStepsDoc.AgentStep> agentSteps = agentStepsDoc.getAgentSteps();
      agentSteps.put(id, AgentStepsDoc.AgentStep.builder().id(id).order(index).text(text)
          .detectedBy(Constants.DETECTED_BY_AGENT).build());
      this.agentStepsRepo.save(agentStepsDoc);
    } else {
      Map<String, AgentStepsDoc.AgentStep> agentSteps = new HashMap<>();
      agentSteps.put(id, AgentStepsDoc.AgentStep.builder().id(id).order(index).text(text)
          .detectedBy(Constants.DETECTED_BY_AGENT).build());
      this.agentStepsRepo.save(
          AgentStepsDoc.builder().conversationId(agentUpdateRequest.getConversationId())
              .environment(agentUpdateRequest.getEnvironment())
              .tenantId(agentUpdateRequest.getTenantId()).agentSteps(agentSteps).build());
    }
    SummaryUpdateEventLog summaryUpdateEventLog =
        this.summaryHelper.getSummaryUpdateEventLog(agentUpdateRequest.getConversationId(),
            agentUpdateRequest.getTimestamp(), Constants.ADD_AGENT_STEP, eventData, Constants.EMPTY,
            Constants.EMPTY);
    this.summaryHelper.saveSummaryEventDoc(summaryUpdateEventLog);

  }

  @Override
  public boolean deleteAgentStep(String convId, String id) {
    log.debug("deleting agent step in conversation id {} with id {}", convId, id);
    boolean isExist = this.agentStepsCacheHelper.agentStepExist(convId, id);
    if (isExist) {
      boolean eventProcessingEnabled =
          this.ldHelper.isFFEnabled(UASSIST_AGENT_EDITS_QUEUE_PROCESSING, FF_CONTEXT_KIND);
      AgentUpdateRequest agentUpdateRequest = AgentUpdateRequest.builder()
          .agentRequestPayload(AgentRequestPayload.builder().conversationId(convId).build())
          .agentAction(Commons.AgentAction.DELETE_AGENT_STEP)
          .environment(TenantUtils.getTenantDetails().getEnvironment())
          .timestamp(DateUtil.TIME.millis()).conversationId(convId)
          .agentStepUpdate(AgentUpdateRequest.AgentStepUpdate.builder().id(id).build())
          .tenantId(TenantUtils.getTenantDetails().getTenantId()).build();
      if (eventProcessingEnabled) {
        this.conversationEventProcessingHelper.pushEventToConversationQueue(agentUpdateRequest);
      } else {
        this.processAgentUpdates(agentUpdateRequest, new ArrayList<>());
      }
      return true;
    }
    return false;
  }

  private void processDeleteAgentStep(AgentUpdateRequest agentUpdateRequest, final String id,
      List<SummaryUpdateEventLog.EventData> eventData) {
    this.agentStepsCacheHelper.delete(agentUpdateRequest.getConversationId(), id);
    Optional<AgentStepsDoc> agentStepDoc =
        this.agentStepsRepo.findById(agentUpdateRequest.getConversationId());
    if (agentStepDoc.isPresent()) {
      AgentStepsDoc agentStepsDoc = agentStepDoc.get();
      Map<String, AgentStepsDoc.AgentStep> agentSteps = agentStepsDoc.getAgentSteps();
      agentSteps.remove(id);
      this.agentStepsRepo.save(agentStepsDoc);
    } else {
      log.error("processDeleteAgentStep::agent step doc not found in database");
    }
    SummaryUpdateEventLog summaryUpdateEventLog =
        this.summaryHelper.getSummaryUpdateEventLog(agentUpdateRequest.getConversationId(),
            agentUpdateRequest.getTimestamp(), Constants.DELETE_AGENT_STEP, eventData,
            Constants.EMPTY, Constants.EMPTY);
    this.summaryHelper.saveSummaryEventDoc(summaryUpdateEventLog);
  }

  @Override
  public boolean updateAgentStep(String convId, String id, String text) {
    log.debug("editing agent step in conversation id {} with id {}", convId, id);
    boolean isExist = this.agentStepsCacheHelper.agentStepExist(convId, id);
    if (isExist) {
      boolean eventProcessingEnabled =
          this.ldHelper.isFFEnabled(UASSIST_AGENT_EDITS_QUEUE_PROCESSING, FF_CONTEXT_KIND);
      AgentUpdateRequest agentUpdateRequest = AgentUpdateRequest.builder()
          .agentRequestPayload(AgentRequestPayload.builder().conversationId(convId).build())
          .agentAction(Commons.AgentAction.UPDATE_AGENT_STEP)
          .environment(TenantUtils.getTenantDetails().getEnvironment())
          .timestamp(DateUtil.TIME.millis()).conversationId(convId)
          .agentStepUpdate(AgentUpdateRequest.AgentStepUpdate.builder().id(id).value(text).build())
          .tenantId(TenantUtils.getTenantDetails().getTenantId()).build();
      if (eventProcessingEnabled) {
        this.conversationEventProcessingHelper.pushEventToConversationQueue(agentUpdateRequest);
      } else {
        this.processAgentUpdates(agentUpdateRequest, new ArrayList<>());
      }
      return true;
    }
    return false;
  }

  private void processUpdateAgentStep(AgentUpdateRequest agentUpdateRequest, final String id,
      final String text, List<SummaryUpdateEventLog.EventData> eventData) {
    this.agentStepsCacheHelper.update(agentUpdateRequest.getConversationId(), id, text);
    Optional<AgentStepsDoc> agentStepDoc =
        this.agentStepsRepo.findById(agentUpdateRequest.getConversationId());
    if (agentStepDoc.isPresent()) {
      AgentStepsDoc agentStepsDoc = agentStepDoc.get();
      Map<String, AgentStepsDoc.AgentStep> agentSteps = agentStepsDoc.getAgentSteps();
      AgentStepsDoc.AgentStep agentStep = agentSteps.get(id);
      if (ObjectUtils.isNotEmpty(agentStep)) {
        agentStep.setText(text);
        agentStep.setDetectedBy(Constants.AGENT);
      }
      this.agentStepsRepo.save(agentStepsDoc);
    } else {
      log.error("processUpdateAgentStep::agent step doc not found in database");
    }
    SummaryUpdateEventLog summaryUpdateEventLog =
        this.summaryHelper.getSummaryUpdateEventLog(agentUpdateRequest.getConversationId(),
            agentUpdateRequest.getTimestamp(), Constants.UPDATE_AGENT_STEP, eventData,
            Constants.EMPTY, Constants.EMPTY);
    this.summaryHelper.saveSummaryEventDoc(summaryUpdateEventLog);
  }

  public List<SummaryResponse> fetchSummaryBySessionIds(List<String> sessionIds) {
    TenantPojo tenantPojo = TenantUtils.getTenantDetails();
    ContextSetter.setContextWithSpan("uassist-summary", "fetchSummaryBySessionId",
        String.join(",", sessionIds), tenantPojo.getTenantId(), tenantPojo.getEnvironment());

    List<SummaryResponse> summaryResponses = new ArrayList<>();
    try {
      List<SummaryDoc> summaries =
          this.summaryHelper.getSummaryDocsByExtConversationIds(sessionIds);
      boolean ffEnabled = this.ldHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND);
      log.debug(LOG_AI_GENERATED_SUMMARY_FF_ENABLED, ffEnabled);

      for (SummaryDoc summary : summaries) {
        SummaryResponse summaryResponse;
        if (ffEnabled) {
          summaryResponse = DocConverter.getSystemSummary(summary);
        } else {
          summaryResponse = DocConverter.getSummary(summary);
        }
        summaryResponses.add(summaryResponse);
      }
    } catch (CustomException e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("fetchSummary by session ids error with CustomException!", e);
      throw e;
    } catch (Exception e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("fetchSummary by session ids error!", e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    } finally {
      ContextSetter.clearContext();
    }
    return summaryResponses;
  }

  public String[] getConvIdAndExpIdByExtId(final String extConversationId) {
    SummaryDoc summ = this.summaryRuntimeDao.getSummaryByExtId(extConversationId);
    if (null == summ) {
      return EMPTY_STR_ARR;
    }
    return new String[] {summ.getConversationId(), summ.getExperienceId()};
  }

  @Override
  public ResponseEntity<Map<String, Object>> updateSummaryTemplate(
      final SummaryTemplate summaryTemplate) {

    List<ErrorResponse> errors =
        SummaryHelper.validateUpdateSummaryTemplateRequest(summaryTemplate);
    if (!errors.isEmpty()) {
      return Commons.getErrorResponse(errors, HttpStatus.BAD_REQUEST);
    }

    SummaryDoc summaryDoc;
    try {
      summaryDoc = summaryHelper.getSummaryDoc(summaryTemplate.getConversationId());
      if (ObjectUtils.isEmpty(summaryDoc)) {
        log.error("Summary is unavailable for conversationId: {}",
            summaryTemplate.getConversationId());
        return OutputProcessor.failureUIResponse(HttpStatus.BAD_REQUEST,
            String.format("Summary is unavailable for conversationId: %s",
                summaryTemplate.getConversationId()));
      }
    } catch (CustomException ce) {
      log.error("Summary is unavailable for conversationId: {}",
          summaryTemplate.getConversationId());
      return OutputProcessor.failureUIResponse(HttpStatus.BAD_REQUEST,
          String.format("Summary is unavailable for conversationId: %s",
              summaryTemplate.getConversationId()));
    }

    log.debug("SummaryDoc for conversationId: {}, {}", summaryTemplate.getConversationId(),
        summaryDoc);

    if ((ObjectUtils.isNotEmpty(summaryDoc.getGenAiSummary())) && (
        CollectionUtils.isNotEmpty(summaryDoc.getGenAiSummary().getSections())
            || MapUtils.isNotEmpty(
            summaryDoc.getGenAiSummary().getUnmappedVariableInGenAiSummaries()))) {
      log.error(
          "Updating Summary template ID and Version is not allowed at the moment as Gen-AI summary has already been generated for the current conversationId: {}",
          summaryTemplate.getConversationId());
      return OutputProcessor.failureUIResponse(HttpStatus.PRECONDITION_FAILED, String.format(
          "Updating Summary template ID and Version is not allowed at the moment as Gen-AI summary"
              + " has already been generated for the current conversationId: %s",
          summaryTemplate.getConversationId()));
    }

    if (summaryTemplate.isAgentSelected()) {
      summaryDoc.setTemplateAssignedBy(TemplateConfigurer.AGENT_SELECTION);
    } else {
      if (StringUtils.isNoneBlank(summaryDoc.getTemplateId()) && (
          ObjectUtils.isNotEmpty(summaryDoc.getTemplateVersion())
              && summaryDoc.getTemplateVersion() > 0) && (
          ObjectUtils.isNotEmpty(summaryDoc.getTemplateAssignedBy())
              && summaryDoc.getTemplateAssignedBy().equals(TemplateConfigurer.AGENT_SELECTION))) {
        log.error(
            "Updating Summary template ID and Version is not allowed at the moment as it has been finalized by agent for the current conversationId: {}",
            summaryTemplate.getConversationId());
        return OutputProcessor.failureUIResponse(HttpStatus.PRECONDITION_FAILED, String.format(
            "Updating Summary template ID and Version is not allowed at the moment as it has been finalized"
                + " by agent for the current conversationId: %s",
            summaryTemplate.getConversationId()));
      }
      summaryDoc.setTemplateAssignedBy(TemplateConfigurer.TEMPLATE_MAPPING_API);
    }

    summaryDoc.setTemplateId(summaryTemplate.getSummaryTemplateId());
    summaryDoc.setTemplateVersion(summaryTemplate.getVersion());
    if(StringUtils.isNoneBlank(summaryTemplate.getIntentLabel())) {
      summaryDoc.setIntentLabel(summaryTemplate.getIntentLabel());
    }
    this.summaryHelper.saveSummaryDoc(summaryDoc);

    log.info(
        "Successfully mapped Summary TemplateId and version for the current conversationId: {}",
        summaryTemplate.getConversationId());
    return OutputProcessor.successUIResponse(HttpStatus.OK, null);
  }

  @Override
  public GenAiSummaryDoc createGenAiSummaryIndependent(String conversationId, String templateId,int version) {
    return this.genAISummaryService.createGenAiSummary(conversationId,templateId,TenantUtils.getTenantDetails()
        .getTenantId(),version);
  }

  private boolean processTemplateBasedGenAISummary(
      ConversationEventEnvelope conversationEventEnvelope, SummaryDoc summaryDoc) {
    if (!this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
        FF_CONTEXT_KIND)) {
      return false;
    }

    String convId = conversationEventEnvelope.getConversationId();
    ConversationEventEnvelope.IntentsAdded intentsAdded =
        conversationEventEnvelope.getIntentsAdded();

    if (ObjectUtils.isNotEmpty(summaryDoc.getGenAiGeneratedSummary())) {
      log.debug(
          "Avoiding further processing of INTENTS_ADDED event since Gen-AI summary is already generated for convId: {}, event: {}",
          convId, intentsAdded);
      return true;
    }

    TemplateConfigurer templateAssignedBy = summaryDoc.getTemplateAssignedBy();
    if (ObjectUtils.isNotEmpty(templateAssignedBy)) {
      if (TemplateConfigurer.AGENT_SELECTION.equals(templateAssignedBy)
          || TemplateConfigurer.TEMPLATE_MAPPING_API.equals(templateAssignedBy)) {
        log.debug(
            "Avoiding further processing of INTENTS_ADDED event since templateId is already decided by: {}, for convId: {}, event: {}",
            templateAssignedBy, convId, intentsAdded);
        return true;
      }

      Optional<GenAISummaryCache> cacheOpt =
          genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(
              conversationEventEnvelope.getExperienceId());

      if (cacheOpt.isEmpty()) {
        log.debug(
            "IntentsAdded Event: Gen-AI config not found for experience id: {}, conversationId: {}",
            conversationEventEnvelope.getExperienceId(), convId);
        return false;
      }

      GenAISummaryCache cache = cacheOpt.get();
      List<GenAISummaryCache.CallTypeTemplateMapping> mappings =
          cache.getCallTypeTemplateMappings();

      if (CollectionUtils.isEmpty(mappings)) {
        log.debug(
            "IntentsAdded Event: Intent to template mappings is not configured for expId: {} and hence falling back to standard Gen-AI summary generation mechanism, conversationId: {}",
            conversationEventEnvelope.getExperienceId(), convId);
        return false;
      }

      log.debug(
          "IntentsAdded Event: Intent to template mappings is configured for expId: {}, conversationId: {}, mappings: {}",
          conversationEventEnvelope.getExperienceId(), convId, mappings);

      String intentLabel = conversationEventEnvelope.getIntentsAdded().getIntents(0).getLabel();
      Optional<GenAISummaryCache.CallTypeTemplateMapping> mappingOpt =
          mappings.stream().filter(m -> intentLabel.equals(m.getIntentLabel())).findAny();

      if (mappingOpt.isEmpty()) {
        log.debug(
            "Avoiding further processing of INTENTS_ADDED event as no Summary Template is mapped to intent: {}, conversationId: {}, event: {}",
            intentLabel, convId, intentsAdded);
        return true;
      }

      var mapping = mappingOpt.get();
      summaryDoc.setIntentLabel(mapping.getIntentLabel());
      summaryDoc.setTemplateId(mapping.getTemplateId());
      summaryDoc.setTemplateVersion(mapping.getVersion());
      summaryDoc.setTemplateAssignedBy(TemplateConfigurer.INTENT_ADDED_EVENT);

      log.debug(
          "Summary template and Version to generate Gen-AI summary was assigned by: {}, conversationId: {}",
          TemplateConfigurer.INTENT_ADDED_EVENT, convId);
      return false;
    }

    log.debug(
        "Falling back to standard Gen-AI summary generation mechanism since Summary Template was not assigned during ConversationStarted Event, conversationId: {}",
        convId);
    return false;
  }

}
