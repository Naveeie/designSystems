package com.uniphore.uassist.summary.model;

import com.uniphore.conversations.v1.Conversation;
import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.conversations.v1.ConversationState;
import com.uniphore.conversations.v1.State;
import com.uniphore.conversations.v1.StateV2;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.dao.documents.*;
import com.uniphore.uassist.summary.util.DateUtil;
import com.uniphore.uassist.summary.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DocConverter {
  public static SummaryDoc getSummaryDoc(String conversationId, String businessProcessId,
      String headerTemplate) {

    return SummaryDoc.builder().conversationId(conversationId)
        .tenantId(TenantContext.getMap().get(TenantConstant.META_KEY_TENANT_ID))
        .environment(TenantContext.getMap().get(TenantConstant.META_KEY_ENVIRONMENT))
        .businessProcessId(businessProcessId).headerSummary(
            HeaderSummaryDoc.builder().template(headerTemplate)
                .plainText(HtmlParser.extractPlainText(headerTemplate)).build()).build();
  }

  public static SummaryDoc getSummaryDoc(ConversationEventEnvelope conversationEventEnvelope,
      String headerTemplate, boolean isStateV2FfEnabled) {

    HeaderSummaryDoc headerSummaryDoc = HeaderSummaryDoc.builder().template(headerTemplate)
        .plainText(HtmlParser.extractPlainText(headerTemplate)).entityInstanceIdMap(new HashMap<>())
        .variableInstanceIdMap(new HashMap<>())
        .timestamp(DateUtil.toTimestamp(conversationEventEnvelope.getTimestamp())).build();

    Conversation conversation =
        conversationEventEnvelope.getConversationStarted().getConversation();
    com.google.protobuf.Timestamp telephonyCallStartTime =
        resolveCallActiveTimestamp(conversation, isStateV2FfEnabled).orElse(
            conversationEventEnvelope.getTimestamp());
    List<ParticipantDoc> participants = new ArrayList<>();
    for (com.uniphore.conversations.v1.Participant participant : conversation.getParticipantsList()) {
      participants.add(ParticipantDoc.builder().participantId(participant.getParticipantId())
          .type(participant.getType()).ctiAgentId(participant.getCtiAgentId()).build());
    }
    return SummaryDoc.builder().conversationId(conversationEventEnvelope.getConversationId())
        .tenantId(conversationEventEnvelope.getTenantId())
        .environment(conversationEventEnvelope.getEnvironment())
        .genAiSummary(GenAiSummaryDoc.builder().sections(new ArrayList<>()).build())
        .businessProcessId(conversationEventEnvelope.getBusinessProcessId())
        .timestamp(DateUtil.toTimestamp(telephonyCallStartTime))
        .headerSummary(headerSummaryDoc).language(conversation.getLanguage())
        .entities(new HashMap<>()).variables(new HashMap<>())
        .experienceId(conversationEventEnvelope.getExperienceId())
        .intentSummaries(new LinkedHashMap<String, IntentSummaryDoc>()).participants(participants)
        .extConversationId(conversation.getExternalConversationId()).build();
  }

  private static Optional<com.google.protobuf.Timestamp> resolveCallActiveTimestamp(
      Conversation conversation, boolean isStateV2FfEnabled) {
    if (isStateV2FfEnabled) {
      return conversation.getStatesV2List().stream()
          .filter(state -> state.getState() == ConversationState.CONVERSATION_STATE_ACTIVE)
          .map(StateV2::getTimestamp).findFirst();
    }
    return conversation.getStatesList().stream()
        .filter(state -> state.getState() == ConversationState.CONVERSATION_STATE_ACTIVE)
        .map(State::getTimestamp).findFirst();
  }

  public static SummaryDoc agentNotestoSummaryDoc(SummaryDoc summaryDoc,
      AgentRequestPayload agentNotes, com.google.protobuf.Timestamp timestamp) {

    AgentNotesDoc agentNotesDoc = AgentNotesDoc.builder().text(agentNotes.getAgentNotes())
        .plainText(agentNotes.getAgentNotes()).timestamp(DateUtil.toTimestamp(timestamp)).build();
    summaryDoc.setAgentNotes(agentNotesDoc);
    return summaryDoc;
  }

  public static SummaryDoc addIntenttoSummaryDoc(SummaryDoc summaryDoc,
      AgentRequestPayload agentRequestPayload, com.google.protobuf.Timestamp timestamp,
      String instanceId) {

    if (null == summaryDoc.getIntentSummaries()) {
      summaryDoc.setIntentSummaries(new LinkedHashMap<String, IntentSummaryDoc>());
    }

    summaryDoc.getIntentSummaries().put(instanceId,
        IntentSummaryDoc.builder().intentId(agentRequestPayload.getIntentId())
            .source(agentRequestPayload.getSource()).instanceId(instanceId)
            .template(agentRequestPayload.getIntentSummary()).variableInstanceIdMap(
                summaryDoc.getVariables().values().stream()
                    .collect(Collectors.toMap(SummaryDoc.Slot::getId, SummaryDoc.Slot::getInstanceId)))
            .entityInstanceIdMap(summaryDoc.getEntities().values().stream()
                .collect(Collectors.toMap(SummaryDoc.Slot::getId, SummaryDoc.Slot::getInstanceId)))
            .plainText(HtmlParser.extractPlainText(agentRequestPayload.getIntentSummary()))
            .timestamp(DateUtil.toTimestamp(timestamp)).entityIdIsEdited(new HashMap<>()).build());
    return summaryDoc;
  }

  public static SummaryDoc agentEditstoSummaryDoc(SummaryDoc summaryDoc,
      AgentRequestPayload agentRequestPayload, com.google.protobuf.Timestamp timestamp) {
    if (StringUtils.isNotEmpty(agentRequestPayload.getIntentSummary())) {
      summaryDoc.getIntentSummaries().computeIfPresent(agentRequestPayload.getInstanceId(),
          (k, existingIntentSummaryDoc) -> IntentSummaryDoc.builder()
              .intentId(agentRequestPayload.getIntentId())
              .source(existingIntentSummaryDoc.getSource())
              .instanceId(agentRequestPayload.getInstanceId())
              .template(agentRequestPayload.getIntentSummary())
              .variableInstanceIdMap(existingIntentSummaryDoc.getVariableInstanceIdMap())
              .entityInstanceIdMap(existingIntentSummaryDoc.getEntityInstanceIdMap())
              .entityIdIsEdited(existingIntentSummaryDoc.getEntityIdIsEdited())
              .plainText(HtmlParser.extractPlainText(agentRequestPayload.getIntentSummary()))
              .timestamp(DateUtil.toTimestamp(timestamp)).build());
    }
    if (StringUtils.isNotEmpty(agentRequestPayload.getHeaderSummary())) {
      summaryDoc.setHeaderSummary(
          HeaderSummaryDoc.builder().template(agentRequestPayload.getHeaderSummary())
              .plainText(HtmlParser.extractPlainText(agentRequestPayload.getHeaderSummary()))
              .timestamp(DateUtil.toTimestamp(timestamp)).build());
    }
    if (StringUtils.isNotEmpty(agentRequestPayload.getDisposition())) {
      summaryDoc.getIntentSummaries().computeIfPresent(agentRequestPayload.getInstanceId(),
          (k, existingIntentSummaryDoc) -> IntentSummaryDoc.builder()
              .intentId(agentRequestPayload.getIntentId())
              .source(existingIntentSummaryDoc.getSource())
              .instanceId(existingIntentSummaryDoc.getInstanceId())
              .template(existingIntentSummaryDoc.getTemplate())
              .variableInstanceIdMap(existingIntentSummaryDoc.getVariableInstanceIdMap())
              .entityInstanceIdMap(existingIntentSummaryDoc.getEntityInstanceIdMap())
              .plainText(HtmlParser.extractPlainText(existingIntentSummaryDoc.getTemplate()))
              .disposition(agentRequestPayload.getDisposition())
              .entityIdIsEdited(existingIntentSummaryDoc.getEntityIdIsEdited())
              .timestamp(DateUtil.toTimestamp(timestamp)).build());
    }
    if (ObjectUtils.isNotEmpty(agentRequestPayload.getGenAiSummaryDoc())) {
      summaryDoc.setGenAiSummary(agentRequestPayload.getGenAiSummaryDoc());
    }
    return summaryDoc;
  }

  public static AgentEditsDoc agentAgentEditsDoc(SummaryDoc summaryDoc,
      AgentRequestPayload agentRequestPayload, com.google.protobuf.Timestamp timestamp,
      String agentAction) {

    AgentEditsDoc agentEditsDoc = null;
    if (Commons.AgentAction.INTENT_EDIT.toString().equals(agentAction)) {
      agentEditsDoc = getAgentEditsDoc(summaryDoc.getConversationId(), summaryDoc.getTenantId(),
          summaryDoc.getEnvironment(), summaryDoc.getBusinessProcessId(),
          agentRequestPayload.getIntentId(), agentRequestPayload.getInstanceId(),
          summaryDoc.getIntentSummaries().get(agentRequestPayload.getInstanceId()).getTemplate(),
          agentRequestPayload.getIntentSummary(), timestamp, agentAction,
          agentRequestPayload.getAgentId(), summaryDoc.getGenAiSummary().toString(), null);
    } else if (Commons.AgentAction.HEADER_EDIT.toString().equals(agentAction)) {
      agentEditsDoc = getAgentEditsDoc(summaryDoc.getConversationId(), summaryDoc.getTenantId(),
          summaryDoc.getEnvironment(), summaryDoc.getBusinessProcessId(), null, null,
          summaryDoc.getHeaderSummary().getTemplate(), agentRequestPayload.getHeaderSummary(),
          timestamp, agentAction, agentRequestPayload.getAgentId(),
          summaryDoc.getGenAiSummary().toString(), null);
    } else if (Commons.AgentAction.INTENT_ADD.toString().equals(agentAction)
        || Commons.AgentAction.INTENT_DELETE.toString().equals(agentAction)) {
      agentEditsDoc = getAgentEditsDoc(summaryDoc.getConversationId(), summaryDoc.getTenantId(),
          summaryDoc.getEnvironment(), summaryDoc.getBusinessProcessId(),
          agentRequestPayload.getIntentId(), agentRequestPayload.getInstanceId(), null, null,
          timestamp, agentAction, agentRequestPayload.getAgentId(),
          summaryDoc.getGenAiSummary().toString(), null);
    } else if (Commons.AgentAction.GEN_AI_SUMMARY_EDIT.toString().equals(agentAction)) {
      agentEditsDoc = getAgentEditsDoc(summaryDoc.getConversationId(), summaryDoc.getTenantId(),
          summaryDoc.getEnvironment(), summaryDoc.getBusinessProcessId(),
          agentRequestPayload.getIntentId(), agentRequestPayload.getInstanceId(), null, null,
          timestamp, agentAction, agentRequestPayload.getAgentId(),
          summaryDoc.getGenAiSummary().toString(),
          agentRequestPayload.getGenAiSummaryDoc().toString());
    }
    return agentEditsDoc;
  }

  public static AgentEditsDoc agentAgentEditsDoc(SummaryDoc summaryDoc,
      IntentSummaryDoc intentSummaryDoc, com.google.protobuf.Timestamp timestamp,
      String agentAction) {

    AgentEditsDoc agentEditsDoc = AgentEditsDoc.builder().build();
    if (Commons.AgentAction.INTENT_ADD.toString().equals(agentAction)
        || Commons.AgentAction.INTENT_DELETE.toString().equals(agentAction)) {
      agentEditsDoc = getAgentEditsDoc(summaryDoc.getConversationId(), summaryDoc.getTenantId(),
          summaryDoc.getEnvironment(), summaryDoc.getBusinessProcessId(),
          intentSummaryDoc.getIntentId(), intentSummaryDoc.getInstanceId(), null, null, timestamp,
          agentAction, "", summaryDoc.getGenAiSummary().toString(), null);
    }
    return agentEditsDoc;
  }

  private static AgentEditsDoc getAgentEditsDoc(String conversationId, String tenantId,
      String environment, String businessProcessId, String intentId, String instanceId, String text,
      String editedText, com.google.protobuf.Timestamp timestamp, String agentAction,
      String agentId, String genAiSummary, String genAiSummaryEdited) {

    return AgentEditsDoc.builder().conversationId(conversationId).tenantId(tenantId)
        .environment(environment).intentId(intentId).instanceId(instanceId)
        .businessProcessId(businessProcessId).text(text)
        .plainText(StringUtils.isEmpty(text) ? text : HtmlParser.extractPlainText(text))
        .editedText(editedText).editedPlainText(
            StringUtils.isEmpty(editedText) ? editedText : HtmlParser.extractPlainText(editedText))
        .timestamp(DateUtil.toTimestamp(timestamp)).action(agentAction).agentId(agentId)
        .genAiText(genAiSummary).genAiTextEdited(genAiSummaryEdited).build();
  }

  public static SummaryDoc agentDeleteIntenttoSummaryDoc(SummaryDoc summaryDoc, String id) {

    Map<String, IntentSummaryDoc> map = summaryDoc.getIntentSummaries();
    if (null == map) {
      map = new LinkedHashMap<String, IntentSummaryDoc>();
    } else {
      map.remove(id);
    }
    summaryDoc.setIntentSummaries(map);

    return summaryDoc;
  }

  public static HeaderTemplateDoc toHeaderTemplateDoc(
      final Optional<HeaderTemplateDoc> headerTemplateOpt, final HeaderTemplate headerTemplate,
      String timestamp) {
    HeaderTemplateDoc headerTemplateDoc = null;
    if (headerTemplateOpt.isPresent()) {
      headerTemplateDoc = headerTemplateOpt.get();
    } else {
      headerTemplateDoc = HeaderTemplateDoc.builder().fieldId(RandomUtil.random())
          .businessProcessId(headerTemplate.getBusinessProcessId())
          .tenantId(TenantContext.getMap().get(TenantConstant.META_KEY_TENANT_ID))
          .environment(TenantContext.getMap().get(TenantConstant.META_KEY_ENVIRONMENT))
          .userId(headerTemplate.getUserId()).createdTimestamp(timestamp).build();
    }
    headerTemplateDoc.setUserId(headerTemplate.getUserId());
    headerTemplateDoc.setModifiedTimestamp(timestamp);
    headerTemplateDoc.setTemplate(headerTemplate.getTemplate());
    return headerTemplateDoc;
  }

  public static HeaderTemplate toHeaderTemplate(
      final Optional<HeaderTemplateDoc> headerTemplateOpt) {
    if (headerTemplateOpt.isPresent()) {
      HeaderTemplateDoc headerTemplateDoc = headerTemplateOpt.get();
      return HeaderTemplate.builder().businessProcessId(headerTemplateDoc.getBusinessProcessId())
          .headerTemplateId(headerTemplateDoc.getFieldId())
          .tenantId(headerTemplateDoc.getTenantId()).environment(headerTemplateDoc.getEnvironment())
          .template(headerTemplateDoc.getTemplate())
          .parsedTemplate(HtmlParser.extractText(headerTemplateDoc.getTemplate())).build();
    }
    return HeaderTemplate.builder().build();
  }

  public static List<HeaderTemplate> toHeaderTemplates(
      final List<HeaderTemplateDoc> headerTemplateDocs) {
    List<HeaderTemplate> headerTemplates = new ArrayList<>();
    for (HeaderTemplateDoc headerTemplateDoc : headerTemplateDocs) {
      headerTemplates.add(
          HeaderTemplate.builder().businessProcessId(headerTemplateDoc.getBusinessProcessId())
              .headerTemplateId(headerTemplateDoc.getFieldId())
              .tenantId(headerTemplateDoc.getTenantId())
              .environment(headerTemplateDoc.getEnvironment())
              .template(headerTemplateDoc.getTemplate())
              .parsedTemplate(HtmlParser.extractText(headerTemplateDoc.getTemplate())).build());
    }
    return headerTemplates;
  }

  public static IntentTemplateDoc toIntentTemplateDoc(
      final Optional<IntentTemplateDoc> intentTemplateOpt, final IntentTemplate intentTemplate,
      final String timestamp) {
    IntentTemplateDoc intentTemplateDoc = null;
    if (intentTemplateOpt.isPresent()) {
      intentTemplateDoc = intentTemplateOpt.get();
      intentTemplateDoc.setTemplate(intentTemplate.getTemplate());
      intentTemplateDoc.setModifiedTimestamp(timestamp);
      intentTemplateDoc.setUserId(intentTemplate.getUserId());
    } else {
      intentTemplateDoc = IntentTemplateDoc.builder().intentId(intentTemplate.getIntentId())
          .fieldId(RandomUtil.random()).template(intentTemplate.getTemplate())
          .businessProcessId(intentTemplate.getBusinessProcessId())
          .experienceId(intentTemplate.getExperienceId())
          .environment(TenantContext.getMap().get(TenantConstant.META_KEY_ENVIRONMENT))
          .tenantId(TenantContext.getMap().get(TenantConstant.META_KEY_TENANT_ID))
          .userId(intentTemplate.getUserId()).createdTimestamp(timestamp)
          .modifiedTimestamp(timestamp).build();
    }
    return intentTemplateDoc;
  }

  public static IntentTemplate toIntentTemplate(
      final Optional<IntentTemplateDoc> intentTemplateOpt) {
    if (intentTemplateOpt.isPresent()) {
      IntentTemplateDoc intentTemplateDoc = intentTemplateOpt.get();
      return IntentTemplate.builder().intentTemplateId(intentTemplateDoc.getFieldId())
          .intentId(intentTemplateDoc.getIntentId()).template(intentTemplateDoc.getTemplate())
          .parsedTemplate(HtmlParser.extractText(intentTemplateDoc.getTemplate()))
          .businessProcessId(intentTemplateDoc.getBusinessProcessId())
          .experienceId(intentTemplateDoc.getExperienceId())
          .environment(intentTemplateDoc.getEnvironment()).tenantId(intentTemplateDoc.getTenantId())
          .build();
    }
    return IntentTemplate.builder().build();
  }

  public static List<IntentTemplate> toIntentTemplates(
      final List<IntentTemplateDoc> intentTemplateDocs) {
    List<IntentTemplate> intentTemplates = new ArrayList<>();
    for (IntentTemplateDoc intentTemplateDoc : intentTemplateDocs) {
      intentTemplates.add(IntentTemplate.builder().intentTemplateId(intentTemplateDoc.getFieldId())
          .intentId(intentTemplateDoc.getIntentId())
          .parsedTemplate(HtmlParser.extractText(intentTemplateDoc.getTemplate()))
          .template(intentTemplateDoc.getTemplate())
          .businessProcessId(intentTemplateDoc.getBusinessProcessId())
          .experienceId(intentTemplateDoc.getExperienceId())
          .environment(intentTemplateDoc.getEnvironment()).tenantId(intentTemplateDoc.getTenantId())
          .build());
    }
    return intentTemplates;
  }

  public static DispositionDoc toDispositionDoc(final Optional<DispositionDoc> dispositionOpt,
      Disposition disposition, String timestamp) {
    DispositionDoc dispositionDoc = null;
    if (dispositionOpt.isPresent()) {
      dispositionDoc = dispositionOpt.get();
      dispositionDoc.setName(disposition.getName());
      dispositionDoc.setDescription(disposition.getDescription());
      dispositionDoc.setValues(disposition.getValues());
      dispositionDoc.setUserId(disposition.getUserId());
      dispositionDoc.setModifiedTimestamp(timestamp);
    } else {
      dispositionDoc = DispositionDoc.builder().fieldId(RandomUtil.random())
          .businessProcessId(disposition.getBusinessProcessId()).name(disposition.getName())
          .description(disposition.getDescription()).values(disposition.getValues())
          .environment(TenantContext.getMap().get(TenantConstant.META_KEY_ENVIRONMENT))
          .tenantId(TenantContext.getMap().get(TenantConstant.META_KEY_TENANT_ID))
          .userId(disposition.getUserId()).createdTimestamp(timestamp).modifiedTimestamp(timestamp)
          .build();
    }
    return dispositionDoc;
  }

  public static Disposition toDisposition(final Optional<DispositionDoc> dispositionOpt) {
    if (dispositionOpt.isPresent()) {
      DispositionDoc dispositionDoc = dispositionOpt.get();
      return Disposition.builder().dispositionId(dispositionDoc.getFieldId())
          .businessProcessId(dispositionDoc.getBusinessProcessId()).name(dispositionDoc.getName())
          .description(dispositionDoc.getDescription()).values(dispositionDoc.getValues())
          .environment(dispositionDoc.getEnvironment()).tenantId(dispositionDoc.getTenantId())
          .build();
    }
    return Disposition.builder().build();
  }

  public static List<DispositionLevels> toDispositionLevels(
      final List<DispositionDoc> dispositionDocs) {
    List<DispositionLevels> dispositionLevels = new ArrayList<>();
    for (DispositionDoc dispositionDoc : dispositionDocs) {
      dispositionLevels.add(DispositionLevels.builder().dispositionId(dispositionDoc.getFieldId())
          .businessProcessId(dispositionDoc.getBusinessProcessId())
          .count(dispositionDoc.getValues().size()).environment(dispositionDoc.getEnvironment())
          .tenantId(dispositionDoc.getTenantId()).build());
    }
    return dispositionLevels;
  }

  public static SummaryResponse getSummary(SummaryDoc summaryDoc) {
    SummaryResponse summary = null;
    try {
      List<IntentResponse> intentSummaries = new ArrayList<>();
      if (summaryDoc.getIntentSummaries() != null) {
        for (IntentSummaryDoc intentSummaryDoc : summaryDoc.getIntentSummaries().values()) {
          intentSummaries.add(getIntentSummary(intentSummaryDoc));
        }
      }

      String agentNotes = summaryDoc.getAgentNotes() != null
          ? summaryDoc.getAgentNotes().getText()
          : StringUtils.EMPTY;

      HeaderResponse header = summaryDoc.getHeaderSummary() != null
          ? getHeaderSummary(summaryDoc.getHeaderSummary().getTemplate(),
          summaryDoc.getHeaderSummary().getTimestamp())
          : getHeaderSummary(StringUtils.EMPTY, summaryDoc.getTimestamp());

      summary = SummaryResponse.builder().conversationId(summaryDoc.getConversationId())
          .tenantId(summaryDoc.getTenantId()).environment(summaryDoc.getEnvironment())
          .participants(getParticipantList(summaryDoc.getParticipants())).intents(intentSummaries)
          .header(header).agentNote(agentNotes).language(summaryDoc.getLanguage())
          .businessProcessId(summaryDoc.getBusinessProcessId())
          .genAiSummary(summaryDoc.getGenAiSummary())
          .genAIGeneratedSummary(summaryDoc.getGenAiGeneratedSummary()).build();

    } catch (Exception e) {
      log.error("fetch Summary {}, {}", e.getMessage(), e.getLocalizedMessage());
    }

    return summary;
  }

  public static SummaryResponse getSystemSummary(SummaryDoc summaryDoc) {
    SummaryResponse summary = null;
    try {
      List<IntentResponse> intentSummaries = new ArrayList<>();
      if (summaryDoc.getIntentSummaries() != null) {
        for (IntentSummaryDoc intentSummaryDoc : summaryDoc.getIntentSummaries().values()) {
          intentSummaries.add(getIntentSummary(intentSummaryDoc));
        }
      }
      List<IntentResponse> systemIntentSummaries = new ArrayList<>();
      if (summaryDoc.getSystemIntentSummaries() != null) {
        for (IntentSummaryDoc intentSummaryDoc : summaryDoc.getSystemIntentSummaries().values()) {
          systemIntentSummaries.add(getIntentSummary(intentSummaryDoc));
        }
      }

      String agentNotes = summaryDoc.getAgentNotes() != null
          ? summaryDoc.getAgentNotes().getText()
          : StringUtils.EMPTY;

      HeaderResponse header = summaryDoc.getHeaderSummary() != null
          ? getHeaderSummary(summaryDoc.getHeaderSummary().getTemplate(),
          summaryDoc.getHeaderSummary().getTimestamp())
          : getHeaderSummary(StringUtils.EMPTY, summaryDoc.getTimestamp());

      summary = SummaryResponse.builder().conversationId(summaryDoc.getConversationId())
          .tenantId(summaryDoc.getTenantId()).environment(summaryDoc.getEnvironment())
          .participants(getParticipantList(summaryDoc.getParticipants())).intents(intentSummaries)
          .header(header).agentNote(agentNotes).language(summaryDoc.getLanguage())
          .businessProcessId(summaryDoc.getBusinessProcessId())
          .genAiSummary(summaryDoc.getGenAiSummary()).systemIntents(systemIntentSummaries).build();

    } catch (Exception e) {
      log.error("fetch SystemSummary {}, {}", e.getMessage(), e.getLocalizedMessage());
    }

    return summary;
  }

  public static HeaderResponse getHeaderSummary(String headerTemplate, TimeStampDoc timestamp) {
    return HeaderResponse.builder().summary(headerTemplate)
        .timestamp(DateUtil.getTimestamp(timestamp).toString()).build();
  }

  public static IntentResponse getIntentSummary(IntentSummaryDoc intentSummaryDoc) {

    return IntentResponse.builder().intentId(intentSummaryDoc.getIntentId())
        .instanceId(intentSummaryDoc.getInstanceId()).summary(intentSummaryDoc.getTemplate())
        .source(intentSummaryDoc.getSource()).disposition(intentSummaryDoc.getDisposition())
        .timestamp(DateUtil.getTimestamp(intentSummaryDoc.getTimestamp()).toString()).build();
  }

  private static List<ParticipantResponse> getParticipantList(
      List<ParticipantDoc> participantList) {

    if (CollectionUtils.isEmpty(participantList)) {
      return new ArrayList<ParticipantResponse>();
    }

    return participantList.stream().map(
            participant -> ParticipantResponse.builder().participantId(participant.getParticipantId())
                .type(participant.getType()).ctiAgentId(participant.getCtiAgentId()).build())
        .collect(Collectors.toList());
  }

  public static GenAISummaryConfigDoc toGenAISummaryConfigDoc(
      final Optional<GenAISummaryConfigDoc> genAISummaryConfigOpt,
      GenAISummaryConfig genAISummaryConfig, String timestamp) {
    GenAISummaryConfigDoc genAISummaryConfigDoc = null;
    if (genAISummaryConfigOpt.isPresent()) {
      genAISummaryConfigDoc = genAISummaryConfigOpt.get();
      genAISummaryConfigDoc.setConfig(genAISummaryConfig.getConfig());
      genAISummaryConfigDoc.setUserId(genAISummaryConfig.getUserId());
      genAISummaryConfigDoc.setModifiedTimestamp(timestamp);
    } else {
      genAISummaryConfigDoc =
          GenAISummaryConfigDoc.builder().experienceId(genAISummaryConfig.getExperienceId())
              .environment(TenantContext.getMap().get(TenantConstant.META_KEY_ENVIRONMENT))
              .tenantId(TenantContext.getMap().get(TenantConstant.META_KEY_TENANT_ID))
              .userId(genAISummaryConfig.getUserId()).createdTimestamp(timestamp)
              .modifiedTimestamp(timestamp).config(genAISummaryConfig.getConfig()).build();
    }
    return genAISummaryConfigDoc;
  }

  public static GenAISummaryConfig toGenAISummaryConfig(
      final Optional<GenAISummaryConfigDoc> genAISummaryConfigOpt) {
    if (genAISummaryConfigOpt.isPresent()) {
      GenAISummaryConfigDoc genAISummaryConfigDoc = genAISummaryConfigOpt.get();
      return GenAISummaryConfig.builder().experienceId(genAISummaryConfigDoc.getExperienceId())
          .config(genAISummaryConfigDoc.getConfig())
          .environment(genAISummaryConfigDoc.getEnvironment())
          .tenantId(genAISummaryConfigDoc.getTenantId()).build();
    }
    return GenAISummaryConfig.builder().build();
  }

  public static AgentStepsDoc toAgentStepsDoc(ConversationEventEnvelope conversationEventEnvelope,
      List<AgentStepPojo> allAgentSteps) {
    return AgentStepsDoc.builder().conversationId(conversationEventEnvelope.getConversationId())
        .environment(conversationEventEnvelope.getEnvironment())
        .tenantId(conversationEventEnvelope.getTenantId()).agentSteps(allAgentSteps.stream()
            .collect(Collectors.toMap(AgentStepPojo::getId,
                step -> AgentStepsDoc.AgentStep.builder().id(step.getId()).order(step.getIndex())
                    .text(step.getText()).detectedBy(Constants.DETECTED_BY_AI).build()))).build();
  }
}
