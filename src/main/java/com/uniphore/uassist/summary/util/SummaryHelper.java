package com.uniphore.uassist.summary.util;

import com.google.protobuf.Timestamp;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.platform.telemetry.context.ContextSetter;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.ri.common.utils.StringUtil;
import com.uniphore.summary.v1.GenAiSummary;
import com.uniphore.summary.v1.Header;
import com.uniphore.summary.v1.IntentSummary;
import com.uniphore.summary.v1.Summary;
import com.uniphore.uassist.summary.cache.*;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.common.Commons.AgentAction;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.dao.AgentEditsRepository;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.documents.*;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.execption.ErrorResponse;
import com.uniphore.uassist.summary.kafka.MessagePublisher;
import com.uniphore.uassist.summary.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.uassist.summary.common.Commons.AgentAction.HEADER_EDIT;

@Component
@Slf4j
public class SummaryHelper {

  @Autowired
  private SummaryRuntimeDao summaryRuntimeDao;
  @Autowired
  private MessagePublisher messagePublisher;

  @Autowired
  private SummaryCacheHelper summaryCacheHelper;

  @Autowired
  private LaunchDarklyHelper launchDarklyHelper;

  @Autowired
  private AgentEditsRepository agentEditsRepository;

  @Autowired
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  @Autowired
  private CacheRefreshHelper cacheRefreshHelper;

  @Autowired
  private GenAISummaryConfigCacheHelper genAiSummaryConfigCacheHelper;

  public void removeSummaryDoc(String conversationId) {
    summaryCacheHelper.remove(conversationId);
  }

  public void removeGenAISummaryRequest(String conversationId) {
    summaryCacheHelper.removeGenAIRequested(conversationId);
  }

  public int getGenAiWordCount(final GenAiSummaryDoc genAiSummary) {
    if (genAiSummary == null || genAiSummary.getSections() == null) {
      return 0;
    }
    return genAiSummary.getSections().stream()
        .filter(section -> section != null && section.getText() != null)
        .mapToInt(section -> section.getText().split(" ").length)
        .sum();
  }

  public void deleteSummaryDoc(String conversationId) {
    summaryRuntimeDao.deleteSummaryDoc(conversationId);
    summaryCacheHelper.remove(conversationId);
    log.info("Removing SummaryDoc from Cache and DB for conversationId:-> {}", conversationId);
  }

  public SummaryDoc getSummaryDoc(String conversationId) throws CustomException {
    SummaryDoc summaryDoc = summaryCacheHelper.find(conversationId);

    if (null == summaryDoc) {
      log.debug("TenantId : {}, environment : {} ConversationId : {} {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), conversationId,
          "Conversation not found in summary cache");
      Optional<SummaryDoc> summaryDocOpt = summaryRuntimeDao.getSummaryDoc(conversationId);
      if (summaryDocOpt.isPresent()) {
        return summaryDocOpt.get();
      } else {
        log.error("TenantId : {}, environment : {} ConversationId : {} {}",
            TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment(), conversationId,
            "Conversation not found");
        throw new CustomException(ErrorCodes.CONVERSATION_NOT_FOUND, HttpStatus.BAD_REQUEST);
      }
    }
    return summaryDoc;
  }

  public Optional<SummaryDoc> getSummaryDocById(String conversationId) {
    SummaryDoc summaryDoc = summaryCacheHelper.find(conversationId);
    if (null == summaryDoc) {
      return summaryRuntimeDao.getSummaryDoc(conversationId);
    }
    return Optional.of(summaryDoc);
  }

  public List<SummaryDoc> getSummaryDocsByExtConversationIds(List<String> sessionIds) {
    return this.summaryRuntimeDao.getSummaryDocsByExtConversationIds(sessionIds);
  }

  public List<SummaryDoc> getSummaryDocs(List<String> conversationIds) {
    List<SummaryDoc> summaryDocs = new ArrayList<>();
    List<String> idsNotInCache = new ArrayList<>();

    for (String conversationId : conversationIds) {
      SummaryDoc summaryDoc = summaryCacheHelper.find(conversationId);
      if (summaryDoc != null) {
        summaryDocs.add(summaryDoc);
      } else {
        idsNotInCache.add(conversationId);
      }
    }
    if (!idsNotInCache.isEmpty()) {
      log.debug("TenantId : {}, environment : {} ConversationIds : {} not found in summary cache",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), idsNotInCache);
      List<SummaryDoc> docsFromDb = this.summaryRuntimeDao.getSummaryDocs(idsNotInCache);
      summaryDocs.addAll(docsFromDb);
    }

    return summaryDocs;
  }

  public void saveSummaryDoc(SummaryDoc summaryDoc) {
    log.debug("saving summary Doc in redis and db : {}", summaryDoc);
    summaryCacheHelper.set(summaryDoc);
    summaryRuntimeDao.saveSummaryDoc(summaryDoc);
  }

  public void saveSummaryEventDoc(SummaryUpdateEventLog summaryUpdateEventLog) {
    boolean ffEnabled =
        this.launchDarklyHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    if (ffEnabled) {
      summaryRuntimeDao.saveSummaryEventDoc(summaryUpdateEventLog);
    }
  }

  public void saveAndPublish(SummaryDoc summaryDoc, Summary summaryProto,
      SummaryUpdateEventLog summaryUpdateEventLog) {
    try {
      log.info("publishing and saving summary summary: conv-id {} event: {}",
          summaryDoc.getConversationId(),
          summaryUpdateEventLog.getEventType());
      this.saveSummaryDoc(summaryDoc);

      if (this.launchDarklyHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
          FF_CONTEXT_KIND) && StringUtils.isNoneBlank(summaryDoc.getIntentLabel())) {
        Summary.Builder summaryBuilder = summaryProto.toBuilder();
        summaryBuilder.setGenAiSummary(
            GenAiSummary.newBuilder().setIntentLabel(summaryDoc.getIntentLabel())
                .addAllSections(summaryBuilder.getGenAiSummary().getSectionsList()));
        messagePublisher.summaryUpdate(summaryBuilder.build(), summaryUpdateEventLog.getEventType());
      } else {
        messagePublisher.summaryUpdate(summaryProto, summaryUpdateEventLog.getEventType());
      }

      log.debug("published summary object : {}", summaryProto);
      this.saveSummaryEventDoc(summaryUpdateEventLog);
    } catch (Exception e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("error at save and publish summary: event: {}, error: ",
          summaryUpdateEventLog.getEventType(), e);
    }
  }

  public void publishNoLog(Summary summaryProto, String eventType) {
    try {
      log.debug("publishing and saving summary without event log,  summary: conv-id {} event: {}",
          summaryProto.getConversationId(), eventType);
      messagePublisher.summaryUpdate(summaryProto, eventType);
    } catch (Exception e) {
      ContextSetter.setContextWithSpanError(e);
      log.error("error at save and publish summary: event: {}, error: ",
          eventType, e);
    }
  }

  public SummaryUpdateEventLog getSummaryUpdateEventLog(String conversationId, long eventTime,
      final String eventType, List<SummaryUpdateEventLog.EventData> eventDataList,
      final String summaryString, final String businessId) {
    long eventProcessedTime = DateUtil.TIME.millis();
    return SummaryUpdateEventLog.builder().eventTime(eventTime).eventType(eventType)
        .eventData(eventDataList).environment(TenantUtils.getTenantDetails().getEnvironment())
        .eventProcessedTime(eventProcessedTime).latency(eventProcessedTime - eventTime)
        .tenantId(TenantUtils.getTenantDetails().getTenantId()).conversationId(conversationId)
        .summary(summaryString).businessProcessId(businessId).build();
  }

  public void createEventLogBasedOnAgentAction(final AgentUpdateRequest agentUpdateRequest,
      List<SummaryUpdateEventLog.EventData> eventData) {
    Commons.AgentAction agentAction = agentUpdateRequest.getAgentAction();
    AgentRequestPayload agentRequestPayload = agentUpdateRequest.getAgentRequestPayload();
    AgentUpdateRequest.AgentStepUpdate agentStepUpdate = agentUpdateRequest.getAgentStepUpdate();
    switch (agentAction) {
      case GEN_AI_SUMMARY_EDIT:
        eventData.add(
            SummaryUpdateEventLog.EventData.builder().eventId(agentAction.toString()).instanceId("")
                .value(agentRequestPayload.getGenAiSummaryDoc().toString()).headerEffected(false)
                .detectedBy(Constants.DETECTED_BY_AGENT).effectedIntents(new ArrayList<>())
                .build());
        break;
      case HEADER_EDIT:
        eventData.add(
            SummaryUpdateEventLog.EventData.builder().eventId(agentAction.toString()).instanceId("")
                .detectedBy(Constants.DETECTED_BY_AGENT).headerEffected(true)
                .effectedIntents(new ArrayList<>()).value(agentRequestPayload.getHeaderSummary())
                .build());
        break;
      case INTENT_EDIT:
        eventData.add(SummaryUpdateEventLog.EventData.builder().eventId(agentAction.toString())
            .instanceId(agentRequestPayload.getInstanceId()).detectedBy(Constants.DETECTED_BY_AGENT)
            .value(agentRequestPayload.getIntentSummary()).headerEffected(false)
            .effectedIntents(Collections.singletonList(agentRequestPayload.getInstanceId()))
            .build());
        break;
      case AGENT_NOTES:
        eventData.add(SummaryUpdateEventLog.EventData.builder().eventId(agentAction.toString())
            .detectedBy(Constants.DETECTED_BY_AGENT).value(agentRequestPayload.getAgentNotes())
            .headerEffected(false).build());
        break;
      case INTENT_ADD:
        eventData.add(
            SummaryUpdateEventLog.EventData.builder().eventId(agentRequestPayload.getIntentId())
                .value(agentRequestPayload.getIntentId()).detectedBy(Constants.DETECTED_BY_AGENT)
                .effectedIntents(
                    Collections.<String>singletonList(agentRequestPayload.getInstanceId()))
                .headerEffected(false).instanceId(agentRequestPayload.getInstanceId()).build());
        break;
      case INTENT_DELETE:
        eventData.add(
            SummaryUpdateEventLog.EventData.builder().eventId(agentRequestPayload.getIntentId())
                .instanceId(agentRequestPayload.getInstanceId()).value(Constants.DELETE)
                .headerEffected(false)
                .effectedIntents(Collections.singletonList(agentRequestPayload.getInstanceId()))
                .detectedBy(Constants.DETECTED_BY_AGENT).build());
        break;
      case ADD_AGENT_STEP:
        eventData.add(SummaryUpdateEventLog.EventData.builder().eventId(Constants.CREATE)
            .instanceId(agentStepUpdate.getId()).value(agentStepUpdate.getValue())
            .headerEffected(false).effectedIntents(new ArrayList<>())
            .detectedBy(Constants.DETECTED_BY_AGENT).build());
        break;
      case DELETE_AGENT_STEP:
        eventData.add(SummaryUpdateEventLog.EventData.builder().eventId(Constants.DELETE)
            .instanceId(agentStepUpdate.getId()).value(agentStepUpdate.getValue())
            .headerEffected(false).effectedIntents(new ArrayList<>())
            .detectedBy(Constants.DETECTED_BY_AGENT).build());
        break;
      case UPDATE_AGENT_STEP:
        eventData.add(SummaryUpdateEventLog.EventData.builder().eventId(Constants.UPDATE)
            .instanceId(agentStepUpdate.getId()).value(agentStepUpdate.getValue())
            .headerEffected(false).effectedIntents(new ArrayList<>())
            .detectedBy(Constants.DETECTED_BY_AGENT).build());
        break;
      default:
        log.info("agent edit is not valid request");
        break;
    }
  }

  public void handleIntentDeleteByAgent(AgentRequestPayload agentRequestPayload,
      Timestamp timestamp, long eventStartTime, List<SummaryUpdateEventLog.EventData> eventData) {
    SummaryDoc summaryDoc = this.getSummaryDoc(agentRequestPayload.getConversationId());
    log.debug("agentRequestPayload: {} and summaryDoc: {}", agentRequestPayload, summaryDoc);
    if (!summaryDoc.getIntentSummaries().containsKey(agentRequestPayload.getInstanceId())) {
      throw new CustomException(ErrorCodes.INTENT_SUMMARY_NOT_FOUND, HttpStatus.BAD_REQUEST);
    }
    SummaryUpdateEventLog summaryUpdateEventLog =
        this.getSummaryUpdateEventLog(agentRequestPayload.getConversationId(), eventStartTime,
            Constants.AGENT_ACTIONS, eventData, summaryDoc.toString(),
            agentRequestPayload.getBusinessProcessId());
    this.saveAndPublish(
        DocConverter.agentDeleteIntenttoSummaryDoc(summaryDoc, agentRequestPayload.getInstanceId()),
        ProtoConverter.intentDeleteToSummaryProto(summaryDoc, agentRequestPayload, timestamp),
        summaryUpdateEventLog);
    this.agentEditsRepository.save(DocConverter.agentAgentEditsDoc(summaryDoc, agentRequestPayload,
        DateUtil.getCurrentTimestamp(), Commons.AgentAction.INTENT_DELETE.toString()));
    String currentIntentForConversation =
        this.intentTemplateCacheHelper.getCurrentIntentForConversation(
            agentRequestPayload.getConversationId());
    if (currentIntentForConversation.equalsIgnoreCase(agentRequestPayload.getInstanceId())) {
      this.intentTemplateCacheHelper.popIntentFromIntentBoundaryStack(
          agentRequestPayload.getConversationId());
    }
  }

  public void handleIntentAddByAgent(AgentRequestPayload agentRequestPayload, Timestamp timestamp,
      long eventStartTime, List<SummaryUpdateEventLog.EventData> eventData) {
    SummaryDoc summaryDoc = this.getSummaryDoc(agentRequestPayload.getConversationId());
    final String instanceId = RandomUtil.random();
    agentRequestPayload.setInstanceId(instanceId);
    agentRequestPayload.setIntentSummary(this.getIntentTemplate(agentRequestPayload.getIntentId(),
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), agentRequestPayload.getConversationId(),
        summaryDoc,false));
    if (StringUtils.isNotEmpty(agentRequestPayload.getIntentSummary())) {
      // assume agent added latest intent
      this.intentTemplateCacheHelper.pushIntentToIntentBoundaryStack(
          agentRequestPayload.getConversationId(), instanceId);
      this.agentEditsRepository.save(
          DocConverter.agentAgentEditsDoc(summaryDoc, agentRequestPayload, timestamp,
              Commons.AgentAction.INTENT_ADD.toString()));
    }
    DocConverter.addIntenttoSummaryDoc(summaryDoc, agentRequestPayload, timestamp, instanceId);
    log.debug("agent added intent summary doc: {}", summaryDoc.toString());
    SummaryUpdateEventLog summaryUpdateEventLog =
        this.getSummaryUpdateEventLog(agentRequestPayload.getConversationId(), eventStartTime,
            Constants.TAG_INTENT_ADD, eventData, summaryDoc.toString(),
            agentRequestPayload.getBusinessProcessId());
    this.saveAndPublish(summaryDoc,
        ProtoConverter.intentTemplateToSummaryProto(summaryDoc, agentRequestPayload, timestamp,
            Collections.emptyList()), summaryUpdateEventLog);
  }

  public void handleAgentNotesUpdate(AgentRequestPayload agentRequestPayload, long eventStartTime,
      List<SummaryUpdateEventLog.EventData> eventData) {
    SummaryDoc summaryDoc = this.getSummaryDoc(agentRequestPayload.getConversationId());
    this.saveSummaryDoc(DocConverter.agentNotestoSummaryDoc(summaryDoc, agentRequestPayload,
        DateUtil.getCurrentTimestamp()));
    SummaryUpdateEventLog summaryUpdateEventLog =
        this.getSummaryUpdateEventLog(agentRequestPayload.getConversationId(), eventStartTime,
            Constants.AGENT_ACTIONS, eventData, summaryDoc.toString(),
            agentRequestPayload.getBusinessProcessId());
    this.saveSummaryEventDoc(summaryUpdateEventLog);
  }

  public void handleSummaryTemplateEdits(AgentRequestPayload agentRequestPayload,
      Commons.AgentAction agentAction, Timestamp timestamp, long eventStartTime,
      List<SummaryUpdateEventLog.EventData> eventData) {
    SummaryDoc summaryDoc = this.getSummaryDoc(agentRequestPayload.getConversationId());
    this.agentEditsRepository.save(
        DocConverter.agentAgentEditsDoc(summaryDoc, agentRequestPayload, timestamp,
            agentAction.toString()));
    if (agentAction.equals(AgentAction.INTENT_EDIT) ||
        agentAction.equals(AgentAction.HEADER_EDIT)) {
      summaryDoc.setTemplateSummaryEdit(true);
    } else if (agentAction.equals(AgentAction.GEN_AI_SUMMARY_EDIT)) {
      summaryDoc.setGenaiSummaryEdit(true);
      String templateId = summaryDoc.getTemplateId() != null
              ? summaryDoc.getTemplateId()
              : "";
      if (this.launchDarklyHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
          FF_CONTEXT_KIND) && StringUtils.isNotEmpty(templateId)) {
        if ((agentRequestPayload.getGenAiSummaryDoc().getSections().size() > 1)
            && (agentRequestPayload.getGenAiSummaryDoc().getSections().get(0).getId()
            .equals(Constants.SUMMARY))) {
          log.debug("Filtering out every other section where sectionId is not: {}",
              Constants.SUMMARY);
          agentRequestPayload.getGenAiSummaryDoc().setSections(Collections.singletonList(
              agentRequestPayload.getGenAiSummaryDoc().getSections().get(0)));
        }
      }
    }
    
    boolean templateEditEnabled =
        this.launchDarklyHelper.isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE, FF_CONTEXT_KIND);
    log.debug("saving agent edits for summary:templateEditEnabled: {} agentEdits:  {} summary: {}",
        templateEditEnabled, agentRequestPayload, summaryDoc);
    SummaryUpdateEventLog summaryUpdateEventLog =
        this.getSummaryUpdateEventLog(agentRequestPayload.getConversationId(), eventStartTime,
            Constants.AGENT_ACTIONS, eventData, summaryDoc.toString(),
            agentRequestPayload.getBusinessProcessId());
    IntentSummaryDoc intentSummaryDoc =
        summaryDoc.getIntentSummaries().get(agentRequestPayload.getInstanceId());
    if (StringUtils.isNotEmpty(agentRequestPayload.getIntentSummary())) {
      intentSummaryDoc.setTemplate(agentRequestPayload.getIntentSummary());
      intentSummaryDoc.setPlainText(
          HtmlParser.extractPlainText(agentRequestPayload.getIntentSummary()));
    }
    log.debug("EventData Logging: {}, intentSummaryDoc: {}, agentRequestPayload: {}", eventData,
        intentSummaryDoc, agentRequestPayload);
    if (CollectionUtils.isNotEmpty(agentRequestPayload.getEntityEditPayloads()) && Objects.nonNull(
        intentSummaryDoc)) {
        agentRequestPayload.getEntityEditPayloads().forEach(e -> {
          if (Objects.nonNull(e) && StringUtils.isNotEmpty(e.getEditedComponentId())) {
          log.debug("EntityIdisEdited:{},EditedComponentId:{}",
              intentSummaryDoc.getEntityIdIsEdited(), e.getEditedComponentId());
            intentSummaryDoc.getEntityIdIsEdited().put(e.getEditedComponentId(), true);
          }
          if (StringUtils.isNotEmpty(agentRequestPayload.getDisposition())) {
            intentSummaryDoc.setDisposition(agentRequestPayload.getDisposition());
          }
          log.debug("saving the intent summary {} summary doc {}",agentRequestPayload.getIntentSummary(),summaryDoc);
          this.saveSummaryDoc(summaryDoc);
          SummaryUpdateEventLog.EventData eventDataBuilder;
          if (Objects.nonNull(e)) {
            eventDataBuilder =
                SummaryUpdateEventLog.EventData.builder().eventId(agentAction.toString())
                    .instanceId(e.getEditedComponentId()).detectedBy(Constants.DETECTED_BY_AGENT)
                    .value(e.getEditedValue()).headerEffected(false)
                    .effectedIntents(Collections.singletonList(agentRequestPayload.getInstanceId()))
                    .build();
          } else {
            log.info("EntityEditPayload's element is null, eventData.instanceId and value is not populated");
            eventDataBuilder =
                SummaryUpdateEventLog.EventData.builder().eventId(agentAction.toString())
                    .detectedBy(Constants.DETECTED_BY_AGENT).headerEffected(false)
                    .effectedIntents(Collections.singletonList(agentRequestPayload.getInstanceId()))
                    .build();
          }
          eventData.add(eventDataBuilder);
        });
    }
    log.debug("templateEditEnabled {} - intent summary template {} summary doc {}",
        templateEditEnabled, agentRequestPayload.getIntentSummary(), summaryDoc);
    this.saveSummary(summaryDoc, agentRequestPayload, timestamp, agentAction,
        summaryUpdateEventLog,templateEditEnabled);
  }

  private void saveSummary(SummaryDoc summaryDoc, AgentRequestPayload agentRequestPayload, Timestamp timestamp, Commons.AgentAction agentAction, SummaryUpdateEventLog summaryUpdateEventLog,
      boolean templateEditEnabled) {
    // enabled it to not to work in omega as there is issue
    if (templateEditEnabled) {
      this.saveAndPublishAgentEdits(summaryDoc, agentRequestPayload, timestamp, agentAction,
          summaryUpdateEventLog);
    }else {
      this.saveSummaryDoc(summaryDoc);
      log.debug("saving summary doc - {}", summaryDoc);
    }
  }

  private void saveAndPublishAgentEdits(SummaryDoc summaryDoc,
      AgentRequestPayload agentRequestPayload, com.google.protobuf.Timestamp timestamp,
      Commons.AgentAction agentAction, SummaryUpdateEventLog summaryUpdateEventLog) {
    Summary summaryProto = null;
    SummaryDoc summaryDocument =
        DocConverter.agentEditstoSummaryDoc(summaryDoc, agentRequestPayload, timestamp);
    if (HEADER_EDIT.equals(agentAction)) {
      summaryProto =
          ProtoConverter.headerTemplateToSummaryProto(summaryDoc, agentRequestPayload, timestamp);
    } else {
      summaryProto =
          ProtoConverter.intentTemplateToSummaryProto(summaryDocument, agentRequestPayload,
              timestamp, Collections.emptyList());
    }
    this.saveAndPublish(summaryDocument, summaryProto, summaryUpdateEventLog);

  }

  public String getIntentTemplate(String intentId, String tenantId, String environment,
      String conversationId, SummaryDoc summaryDoc, boolean noEdited) {
    String template = intentTemplateCacheHelper.getIntentTemplate(intentId);

    // template not found in cache, fetch data from mongodb
    if (template == null) {
      template = this.cacheRefreshHelper.refreshIntentTemplate(
          CacheRefreshBean.builder().intentId(intentId).build());
    }

    if (StringUtils.isEmpty(template)) {
      log.debug(
          "IntentsAdded Event TenantId : {}, environment : {} ConversationId : {} Intent template is not configured for Intent: {}",
          tenantId, environment, conversationId, intentId);
      template = StringUtils.EMPTY;
    } else {
      template = HtmlParser.setDefaultData(template, Constants.TBD);
    }

    log.debug("IntentsAdded Event: intentId:{} entities: {}, variables: {}",intentId,summaryDoc.getEntities(),summaryDoc.getVariables());
    // setting already detected values
    for (Map.Entry<String, SummaryDoc.Slot> entity : summaryDoc.getEntities().entrySet()) {
      if (!(entity.getValue().isEdited() && noEdited)) {
      template = HtmlParser.setDetectedData(template, HtmlParser.ENTITY, entity.getKey(),
          entity.getValue().getValue(), intentId);
      }

    }
    for (Map.Entry<String, SummaryDoc.Slot> variable : summaryDoc.getVariables().entrySet()) {
      if (!(variable.getValue().isEdited() && noEdited)) {
      template = HtmlParser.setDetectedData(template, HtmlParser.VARIABLE, variable.getKey(),
          variable.getValue().getValue(), intentId);
      }
    }

    return template;
  }

  public void handleIntentTemplateUpdate(final String sessionId,
      Map<String, IntentSummary> intentSummaryMap, List<String> effectedIntents,
      final String componentId, final String componentValue, final String type,
      SummaryDoc summaryDoc, String currentIntent, boolean isAgentEdit) {
    boolean systemSummaryffEnabled =
        this.launchDarklyHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND);
    log.debug(
        "handleIntentTemplateUpdate:: updating intent template:type: {} conversation id: {},component id: {} intent id: {},systemSummaryEnabled: {}",
        type, sessionId, componentId,
        summaryDoc.getIntentSummaries().get(currentIntent).getIntentId(), systemSummaryffEnabled);
    if (systemSummaryffEnabled && !isAgentEdit) {
      //saving System generated Summaries
      String systemHtmlTemplate = HtmlParser.setDetectedData(
          summaryDoc.getSystemIntentSummaries().get(currentIntent).getTemplate(), type, componentId,
          componentValue, currentIntent);
      summaryDoc.getSystemIntentSummaries().get(currentIntent).setTemplate(systemHtmlTemplate);
      summaryDoc.getSystemIntentSummaries().get(currentIntent)
          .setPlainText(HtmlParser.extractPlainText(systemHtmlTemplate));
    }
    log.debug(
        "handleIntentTemplateUpdate:: updating intent template:type: {} component id: {} intent id: {},component value: {}",
        type, componentId, summaryDoc.getIntentSummaries().get(currentIntent).getIntentId(),
        componentValue);

    effectedIntents.add(currentIntent);
    this.updateIntentSummary(summaryDoc, componentId, componentValue, "", currentIntent, type,
        intentSummaryMap, isAgentEdit);
    log.debug(
        "handleIntentTemplateUpdate:: updating intent template:type: {} component id: {} intent id: {},component value: {}, updated to: {}",
        type, componentId, summaryDoc.getIntentSummaries().get(currentIntent).getIntentId(),
        componentValue, summaryDoc.getIntentSummaries().get(currentIntent));
  }

  public void updateIntentSummary(SummaryDoc summaryDoc, final String id, final String value,
      final String instanceId, final String intentInstanceId, final String type,
      Map<String, IntentSummary> intentSummaryMap, final boolean isAgentEdit) {
    Optional<IntentSummaryDoc> intentSummaryDoc =
        Optional.ofNullable(summaryDoc.getIntentSummaries().get(intentInstanceId));
    if (intentSummaryDoc.isPresent()) {
      IntentSummaryDoc intentSummary = intentSummaryDoc.get();
      String template =
          HtmlParser.setDetectedData(intentSummary.getTemplate(), type, id, value, instanceId);
      intentSummary.setTemplate(template);
      intentSummary.getEntityIdIsEdited().put(id, isAgentEdit);
      intentSummary.setPlainText(HtmlParser.extractPlainText(template));
      if (type.equalsIgnoreCase(HtmlParser.VARIABLE)) {
        Map<String, String> variableInstanceIdMap = intentSummary.getVariableInstanceIdMap();
        variableInstanceIdMap.put(id, instanceId);
      } else {
        Map<String, String> entityInstanceIdMap = intentSummary.getEntityInstanceIdMap();
        entityInstanceIdMap.put(id, instanceId);
      }
      intentSummaryMap.put(intentInstanceId,
          ProtoConverter.getIntentSummary(intentSummary, intentSummary.getEntityInstanceIdMap(),
              intentSummary.getVariableInstanceIdMap()));
      log.info("intent is updated with {} Update intent id: {}, entity id: {}, value: {}", type,
          intentSummary.getIntentId(), id, value);
    } else {
      log.error("intent is not found: {}", id);
    }
  }

  public Header updateHeaderSummary(SummaryDoc summaryDoc, final String id, final String value,
      final String instanceId, final String type, Timestamp timestamp) {
    Optional<HeaderSummaryDoc> headerSummaryOpt =
        Optional.ofNullable(summaryDoc.getHeaderSummary());
    if (headerSummaryOpt.isPresent()) {
      HeaderSummaryDoc headerSummary = headerSummaryOpt.get();
      String template =
          HtmlParser.setDetectedData(headerSummary.getTemplate(), type, id, value, instanceId);
      headerSummary.setTemplate(template);
      headerSummary.setPlainText(HtmlParser.extractPlainText(template));
      if (type.equalsIgnoreCase(HtmlParser.VARIABLE)) {
        Map<String, String> variableInstanceIdMap = headerSummary.getVariableInstanceIdMap();
        variableInstanceIdMap.put(id, instanceId);
      } else {
        Map<String, String> entityInstanceIdMap = headerSummary.getEntityInstanceIdMap();
        entityInstanceIdMap.put(id, instanceId);
      }
      summaryDoc.getHeaderSummary().setTimestamp(DateUtil.toTimestamp(timestamp));
      log.info("header summary updated with {} updated event: id: {}, value: {}", type, id, value);
      return ProtoConverter.getHeaderSummary(template, timestamp,
          headerSummary.getEntityInstanceIdMap(), headerSummary.getVariableInstanceIdMap());
    } else {
      log.info("update header summary: header summary empty");
      return null;
    }
  }

  public boolean updateGenAISummary(SummaryDoc summaryDoc, final String id, final String value,
      final String instanceId, final boolean isEdited) {
    Optional<GenAiSummaryDoc> genAiSummaryDocOpt = Optional.ofNullable(summaryDoc.getGenAiSummary());
    if (genAiSummaryDocOpt.isPresent()) {
      GenAiSummaryDoc genAiSummary = genAiSummaryDocOpt.get();

      if (!genAiSummary.getSections().isEmpty()) {
        genAiSummary.getSections().forEach(section -> {
          if (section.getSpokenEntities() != null) {
            section.getSpokenEntities().forEach(spokenEntity -> {
              if (spokenEntity.getVariableId().equals(id)) {
                spokenEntity.setValue(value);
                spokenEntity.setVariableInstanceId(instanceId);
                spokenEntity.setEdited(isEdited);
              }
            });
          }
        });
      }

      summaryDoc.setGenAiSummary(genAiSummary);
      return true;
    } else {
      log.info("update GenAI summary: genAI summary empty");
      return false;
    }
  }

  public boolean checkAndUpdateGenAISummary(final SummaryDoc summaryDoc,final String experienceId, final String variableId, final String value,
      final String instanceId, final boolean isEdited) {
    Optional<GenAISummaryCache> genAiConfigCacheOpt = genAiSummaryConfigCacheHelper.getGenAIConfigCacheByExperience(experienceId);

    if (genAiConfigCacheOpt.isPresent()) {
      GenAISummaryCache genAiConfigCache = genAiConfigCacheOpt.get();
      Set<String> variableIdSet = new HashSet<>();
      if (genAiConfigCache.getSectionMap()!=null && !genAiConfigCache.getSectionMap().isEmpty()) {
        genAiConfigCache.getSectionMap().values().forEach(spokenEntitiesList ->
            spokenEntitiesList.forEach(spokenEntity ->
                variableIdSet.add(spokenEntity.getVariableId())
            )
        );
      }
      if (variableIdSet.contains(variableId)) {
        log.info("checkAndUpdateGenAISummary :: update GenAI summary for variableId: {}", variableId);
        updateGenAISummary(summaryDoc, variableId, value, instanceId, isEdited);
        return true;
      }
      return false;
    } else {
      log.info("checkAndUpdateGenAISummary:: GenAI configuration not found for experienceId: {}", experienceId);

    return false;
    }
  }

  public static ErrorResponse getErrorResponse(final String errorCode) {
    return ErrorResponse.builder().code(errorCode).message(ErrorCodes.getMessage(errorCode))
          .build();
  }

  public static List<ErrorResponse> validateUpdateSummaryTemplateRequest(
      final SummaryTemplate summaryTemplate) {
    List<ErrorResponse> errors = new ArrayList<>();
    if (StringUtil.isTrulyEmpty(summaryTemplate.getConversationId())) {
      errors.add(SummaryHelper.getErrorResponse(ErrorCodes.CONVERSATION_ID_EMPTY));
    }
    if (StringUtil.isTrulyEmpty(summaryTemplate.getSummaryTemplateId())) {
      errors.add(SummaryHelper.getErrorResponse(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY));
    }
    if (summaryTemplate.getVersion() <=0 ) {
      errors.add(SummaryHelper.getErrorResponse(ErrorCodes.INCORRECT_VERSION));
    }
    if (!errors.isEmpty()) {
      log.error("Errors occurred while validating UpdateSummaryTemplateRequest: {}", errors);
    }
    return errors;
  }

}
