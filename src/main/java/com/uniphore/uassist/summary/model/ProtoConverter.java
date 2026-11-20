package com.uniphore.uassist.summary.model;

import java.util.*;
import java.util.stream.Collectors;

import com.uniphore.conversations.v1.ConversationReviewed;
import com.uniphore.conversations.v1.Feature;
import com.uniphore.summary.v1.*;
import com.uniphore.uassist.summary.dao.documents.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import com.uniphore.conversations.v1.Conversation;
import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.util.DateUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProtoConverter {

  private static List<Participant> getParticipants(
      List<com.uniphore.conversations.v1.Participant> participantList) {

    if (CollectionUtils.isEmpty(participantList)) {
      return new ArrayList<>();
    }
    return participantList.stream().map(
            participant -> Participant.newBuilder().setParticipantId(participant.getParticipantId())
                .setType(participant.getType()).setCtiAgentId(participant.getCtiAgentId()).build())
        .collect(Collectors.toList());
  }

  private static List<Participant> getParticipantList(List<ParticipantDoc> participantList) {

    if (CollectionUtils.isEmpty(participantList)) {
      return new ArrayList<>();
    }

    return participantList.stream().map(
            participant -> Participant.newBuilder().setParticipantId(participant.getParticipantId())
                .setType(participant.getType()).setCtiAgentId(participant.getCtiAgentId()).build())
        .collect(Collectors.toList());
  }

  public static Summary headerTemplateToSummaryProto(
      ConversationEventEnvelope conversationEventEnvelope, String headerTemplate,
      Map<String, String> entityInstanceIdMap, Map<String, String> variableInstanceIdMap) {
    Conversation conversation =
        conversationEventEnvelope.getConversationStarted().getConversation();
    return Summary.newBuilder().setConversationId(conversationEventEnvelope.getConversationId())
        .setAgentEdit(false).setTenantId(conversationEventEnvelope.getTenantId())
        .setEnvironment(conversationEventEnvelope.getEnvironment())
        .setExperienceId(conversation.getExperienceId()).setLanguage(conversation.getLanguage())
        .setTimestamp(DateUtil.getCurrentTimestamp())
        .addAllParticipants(getParticipants(conversation.getParticipantsList())).setHeader(
            getHeaderSummary(headerTemplate, conversationEventEnvelope.getTimestamp(),
                entityInstanceIdMap, variableInstanceIdMap)).build();
  }

  public static Summary headerTemplateToSummaryProto(SummaryDoc summaryDoc,
      AgentRequestPayload agentRequestPayload, com.google.protobuf.Timestamp timestamp) {
    return Summary.newBuilder().setConversationId(summaryDoc.getConversationId()).setAgentEdit(true)
        .setTenantId(summaryDoc.getTenantId()).setEnvironment(summaryDoc.getEnvironment())
        .setExperienceId(summaryDoc.getExperienceId()).setLanguage(summaryDoc.getLanguage())
        .setTimestamp(DateUtil.getCurrentTimestamp())
        .addAllParticipants(getParticipantList(summaryDoc.getParticipants())).setHeader(
            getHeaderSummary(agentRequestPayload.getHeaderSummary(), timestamp, new HashMap<>(),
                new HashMap<>())).build();
  }

  public static Summary intentTemplateToSummaryProto(SummaryDoc summaryDoc,
      List<IntentSummaryDoc> intentSummaryDocs, List<AgentStepPojo> agentSteps) {

    List<Section> sections = buildGenAiSectionDocToProto(summaryDoc);

    GenAiSummary genAiSummary = GenAiSummary.newBuilder().addAllSections(sections).build();

    if (null == agentSteps) {
      agentSteps = new ArrayList<>();
    }
    List<AgentStep> agentStepsProto = agentSteps.stream().map(
        as -> AgentStep.newBuilder().setId(as.getId()).setText(as.getText()).setIndex(as.getIndex())
            .build()).collect(Collectors.toList());

    return Summary.newBuilder().setConversationId(summaryDoc.getConversationId())
        .setAgentEdit(false).setTenantId(summaryDoc.getTenantId())
        .setExperienceId(summaryDoc.getExperienceId()).setEnvironment(summaryDoc.getEnvironment())
        .setTimestamp(DateUtil.getCurrentTimestamp())
        .addAllParticipants(getParticipantList(summaryDoc.getParticipants()))
        .setGenAiSummary(genAiSummary)
        .addAllIntentSummaries(getIntentSummaryList(intentSummaryDocs))
        .addAllAgentSteps(agentStepsProto).build();
  }

  private static List<IntentSummary> getIntentSummaryList(
      List<IntentSummaryDoc> intentSummaryDocs) {
    return intentSummaryDocs.stream().map(intentSummaryDoc -> getIntentSummary(intentSummaryDoc,
            intentSummaryDoc.getEntityInstanceIdMap(), intentSummaryDoc.getVariableInstanceIdMap()))
        .collect(Collectors.toList());
  }

  public static IntentSummary getIntentSummary(IntentSummaryDoc intentSummaryDoc,
      Map<String, String> entityInstanceIdMap, Map<String, String> variableInstanceIdMap) {
    Source source = Constants.AGENT.equals(intentSummaryDoc.getSource()) ? Source.AGENT : Source.AI;

    return IntentSummary.newBuilder().setIntentId(intentSummaryDoc.getIntentId())
        .setTemplate(intentSummaryDoc.getTemplate()).setSource(source)
        .putAllMapEntityIdInstanceId(entityInstanceIdMap)
        .putAllMapVariableIdInstanceId(variableInstanceIdMap)
        .setTimestamp(DateUtil.getCurrentTimestamp()).setIntentId(intentSummaryDoc.getIntentId())
        .setInstanceId(intentSummaryDoc.getInstanceId())
        .setTimestamp(DateUtil.toGoogleTimestamp(intentSummaryDoc.getTimestamp()))
        .setPlainText(HtmlParser.extractPlainText(intentSummaryDoc.getTemplate())).build();
  }

  public static Header getHeaderSummary(String headerTemplate,
      com.google.protobuf.Timestamp timestamp, Map<String, String> entityInstanceIdMap,
      Map<String, String> variableInstanceIdMap) {
    return Header.newBuilder().setTemplate(headerTemplate).setTimestamp(timestamp)
        .setPlainText(HtmlParser.extractPlainText(headerTemplate))
        .putAllMapEntityIdInstanceId(entityInstanceIdMap)
        .putAllMapVariableIdInstanceId(variableInstanceIdMap).build();
  }

  public static AgentNotes getAgentNotes(String agentNote, TimeStampDoc timestamp) {

    return AgentNotes.newBuilder().setText(agentNote)
        .setTimestamp(DateUtil.toGoogleTimestamp(timestamp))
        .setPlainText(HtmlParser.extractPlainText(agentNote)).build();
  }

  public static Summary getSummaryProto(SummaryDoc summaryDoc, Header header,
      Map<String, IntentSummary> intentSummaryMap, List<AgentStepPojo> agentSteps,
      Boolean agentEdits) {

    Collection<IntentSummary> intentSummaries = intentSummaryMap.values();
    if (CollectionUtils.isEmpty(intentSummaries)) {
      intentSummaries = new ArrayList<com.uniphore.summary.v1.IntentSummary>();
    }

    if (null == header) {
      return Summary.newBuilder().setConversationId(summaryDoc.getConversationId())
          .setExperienceId(summaryDoc.getExperienceId()).setAgentEdit(agentEdits)
          .setTenantId(summaryDoc.getTenantId()).setEnvironment(summaryDoc.getEnvironment())
          .setEnvironment(summaryDoc.getEnvironment()).setTimestamp(DateUtil.getCurrentTimestamp())
          .addAllParticipants(getParticipantList(summaryDoc.getParticipants()))
          .addAllIntentSummaries(intentSummaries).build();
    }

    List<Section> sections = buildGenAiSectionDocToProto(summaryDoc);

    GenAiSummary genAiSummary = GenAiSummary.newBuilder().addAllSections(sections).build();

    if (null == agentSteps) {
      agentSteps = new ArrayList<>();
    }
    List<AgentStep> agentStepsProto = agentSteps.stream().map(
        as -> AgentStep.newBuilder().setId(as.getId()).setText(as.getText()).setIndex(as.getIndex())
            .build()).collect(Collectors.toList());

    return Summary.newBuilder().setConversationId(summaryDoc.getConversationId())
        .setAgentEdit(agentEdits).setTenantId(summaryDoc.getTenantId())
        .setExperienceId(summaryDoc.getExperienceId()).setEnvironment(summaryDoc.getEnvironment())
        .setTimestamp(DateUtil.getCurrentTimestamp())
        .addAllParticipants(getParticipantList(summaryDoc.getParticipants()))
        .setGenAiSummary(genAiSummary).addAllIntentSummaries(intentSummaries).setHeader(header)
        .addAllAgentSteps(agentStepsProto).build();
  }

  public static Summary intentTemplateToSummaryProto(SummaryDoc summaryDoc,
      AgentRequestPayload agentRequestPayload, com.google.protobuf.Timestamp timestamp,
      List<AgentStepPojo> agentSteps) {
    Source source =
        Constants.AGENT.equals(agentRequestPayload.getSource()) ? Source.AGENT : Source.AI;

    List<IntentSummary> intentSummaries = new ArrayList<>();
    if (StringUtils.isNotEmpty(agentRequestPayload.getIntentId()) && StringUtils.isNotEmpty(
        agentRequestPayload.getInstanceId())) {
      intentSummaries.add(IntentSummary.newBuilder().setIntentId(agentRequestPayload.getIntentId())
          .setTemplate(Commons.getValidString(agentRequestPayload.getIntentSummary()))
          .setInstanceId(agentRequestPayload.getInstanceId())
          .setTimestamp(DateUtil.getCurrentTimestamp())
          .setPlainText(HtmlParser.extractPlainText(agentRequestPayload.getIntentSummary()))
          .setDispositionId(Commons.getValidString(agentRequestPayload.getDisposition()))
          .setSource(source).setTimestamp(timestamp).build());
    }

    List<Section> sections = buildGenAiSectionDocToProto(summaryDoc);

    GenAiSummary genAiSummary = GenAiSummary.newBuilder().addAllSections(sections).build();

    if (null == agentSteps) {
      agentSteps = new ArrayList<>();
    }
    List<AgentStep> agentStepsProto = agentSteps.stream().map(
        as -> AgentStep.newBuilder().setId(as.getId()).setText(as.getText()).setIndex(as.getIndex())
            .build()).collect(Collectors.toList());

    return Summary.newBuilder().setConversationId(summaryDoc.getConversationId()).setAgentEdit(true)
        .setTenantId(summaryDoc.getTenantId()).setEnvironment(summaryDoc.getEnvironment())
        .addAllParticipants(getParticipantList(summaryDoc.getParticipants()))
        .setTimestamp(DateUtil.getCurrentTimestamp()).setGenAiSummary(genAiSummary)
        .addAllIntentSummaries(intentSummaries).setExperienceId(summaryDoc.getExperienceId())
        .addAllAgentSteps(agentStepsProto).build();
  }

  private static List<Section> buildGenAiSectionDocToProto(SummaryDoc summaryDoc) {
    List<Section> sectionList = new ArrayList<>();
    if (Objects.nonNull(summaryDoc.getGenAiSummary())) {
      summaryDoc.getGenAiSummary().getSections().forEach(s -> {
        List<SpokenEntity> spokenEntities = Optional.ofNullable(s.getSpokenEntities()).map(
            x -> x.stream().map(se -> SpokenEntity.newBuilder().setVariableId(se.getVariableId())
                .setValue(se.getValue()).setLabel(se.getLabel()).setIsEdited(se.isEdited())
                .setVariableInstanceId(se.getVariableInstanceId())
                .setIsLabelUnmentioned(se.isLabelUnmentioned()).setPlacement(se.getPlacement())
                .build()).collect(Collectors.toList())).orElse(new ArrayList<>());
        Section section = Section.newBuilder().setId(s.getId())
            .setText(s.getText() != null ? s.getText() : Constants.EMPTY)
            .addAllSpokenEntities(spokenEntities).build();
        sectionList.add(section);
      });
    }
    log.debug("section list with sk:{}", sectionList);
    return sectionList;
  }

  public static Summary intentDeleteToSummaryProto(SummaryDoc summaryDoc,
      AgentRequestPayload agentRequestPayload, com.google.protobuf.Timestamp timestamp) {
    List<IntentSummary> intentSummaries = new ArrayList<>();
    intentSummaries.add(
        IntentSummary.newBuilder().setIntentId(StringUtils.EMPTY).setTemplate(StringUtils.EMPTY)
            .setInstanceId(agentRequestPayload.getInstanceId()).setPlainText(StringUtils.EMPTY)
            .setDispositionId(StringUtils.EMPTY).setSource(Source.AGENT).setIsDeleted(true)
            .setTimestamp(timestamp).build());

    return Summary.newBuilder().setConversationId(summaryDoc.getConversationId()).setAgentEdit(true)
        .setTenantId(summaryDoc.getTenantId()).setEnvironment(summaryDoc.getEnvironment())
        .setExperienceId(summaryDoc.getExperienceId())
        .addAllParticipants(getParticipantList(summaryDoc.getParticipants()))
        .setTimestamp(DateUtil.getCurrentTimestamp())
        .setGenAiSummary(GenAiSummary.newBuilder().addAllSections(Collections.emptyList()).build())
        .addAllIntentSummaries(intentSummaries).addAllAgentSteps(Collections.emptyList()).build();
  }

  public static Summary getSummaryProto(SummaryDoc summaryDoc, List<AgentStepPojo> agentSteps) {
    Summary summary = null;
    try {
      List<IntentSummary> intentSummaries = new ArrayList<>();
      if (summaryDoc.getIntentSummaries() != null) {
        for (IntentSummaryDoc intentSummaryDoc : summaryDoc.getIntentSummaries().values()) {
          intentSummaries.add(
              getIntentSummary(intentSummaryDoc, intentSummaryDoc.getEntityInstanceIdMap(),
                  intentSummaryDoc.getVariableInstanceIdMap()));
        }
      }

      AgentNotes agentNotes =
          summaryDoc.getAgentNotes() != null
              ? getAgentNotes(summaryDoc.getAgentNotes().getText(),
              summaryDoc.getAgentNotes().getTimestamp())
              : getAgentNotes(StringUtils.EMPTY, summaryDoc.getTimestamp());
      Header header = summaryDoc.getHeaderSummary() != null
          ? getHeaderSummary(summaryDoc.getHeaderSummary().getTemplate(),
          DateUtil.toGoogleTimestamp(summaryDoc.getHeaderSummary().getTimestamp()),
          summaryDoc.getHeaderSummary().getEntityInstanceIdMap(),
          summaryDoc.getHeaderSummary().getVariableInstanceIdMap())
          : getHeaderSummary(StringUtils.EMPTY,
              DateUtil.toGoogleTimestamp(summaryDoc.getTimestamp()), new HashMap<>(),
              new HashMap<>());
      List<Section> sections = buildGenAiSectionDocToProto(summaryDoc);

      GenAiSummary genAiSummary = GenAiSummary.newBuilder().addAllSections(sections).build();

      if (null == agentSteps) {
        agentSteps = new ArrayList<>();
      }
      List<AgentStep> agentStepsProto = agentSteps.stream().map(
          as -> AgentStep.newBuilder().setId(as.getId()).setText(as.getText())
              .setIndex(as.getIndex()).build()).collect(Collectors.toList());

      summary =
          Summary.newBuilder().setAgentEdit(false).setConversationId(summaryDoc.getConversationId())
              .setTenantId(summaryDoc.getTenantId()).setEnvironment(summaryDoc.getEnvironment())
              .addAllParticipants(getParticipantList(summaryDoc.getParticipants()))
              .setExperienceId(summaryDoc.getExperienceId()).addAllIntentSummaries(intentSummaries)
              .setGenAiSummary(genAiSummary).setTimestamp(DateUtil.getCurrentTimestamp())
              .setTimestamp(DateUtil.getCurrentTimestamp()).setHeader(header)
              .setAgentNotes(agentNotes).addAllAgentSteps(agentStepsProto).build();

    } catch (Exception e) {
      log.error("Exception at getSummaryProto {}", e);
    }

    return summary;
  }

  public static Summary getSummaryProtoForReviewed(SummaryDoc summaryDoc,
      List<AgentStepPojo> agentSteps) {
    Summary summary = null;
    try {
      List<IntentSummary> intentSummaries = new ArrayList<>();
      if (summaryDoc.getIntentSummaries() != null) {
        for (IntentSummaryDoc intentSummaryDoc : summaryDoc.getIntentSummaries().values()) {
          intentSummaries.add(
              getIntentSummary(intentSummaryDoc, intentSummaryDoc.getEntityInstanceIdMap(),
                  intentSummaryDoc.getVariableInstanceIdMap()));
        }
      }

      AgentNotes agentNotes =
          summaryDoc.getAgentNotes() != null
              ? getAgentNotes(summaryDoc.getAgentNotes().getText(),
              summaryDoc.getAgentNotes().getTimestamp())
              : getAgentNotes(StringUtils.EMPTY, summaryDoc.getTimestamp());
      Header header = summaryDoc.getHeaderSummary() != null
          ? getHeaderSummary(summaryDoc.getHeaderSummary().getTemplate(),
          DateUtil.toGoogleTimestamp(summaryDoc.getHeaderSummary().getTimestamp()),
          summaryDoc.getHeaderSummary().getEntityInstanceIdMap(),
          summaryDoc.getHeaderSummary().getVariableInstanceIdMap())
          : getHeaderSummary(StringUtils.EMPTY,
              DateUtil.toGoogleTimestamp(summaryDoc.getTimestamp()), new HashMap<>(),
              new HashMap<>());
      List<Section> sections = buildGenAiSectionDocToProto(summaryDoc);
      Map<String, VariableLabels> unmapVariableLabelsInGenAISummaries =
          buildUnmapVariableLabelsInGenAISummaries(summaryDoc);
      log.debug("unmapVariableLabelsInGenAISummaries:: {}", unmapVariableLabelsInGenAISummaries);
      GenAiSummary genAiSummary = GenAiSummary.newBuilder()
          .putAllUnmentionedLabelInGenAiSummaries(unmapVariableLabelsInGenAISummaries)
          .setIsEdited(summaryDoc.isGenaiSummaryEdit()).addAllSections(sections).build();

      List<String> reviewedAgentSteps =
          agentSteps.stream().sorted(Comparator.comparingInt(AgentStepPojo::getIndex))
              .map(AgentStepPojo::getText).collect(Collectors.toList());

      summary = Summary.newBuilder().setConversationId(summaryDoc.getConversationId())
          .setAgentEdit(summaryDoc.isTemplateSummaryEdit()).setTenantId(summaryDoc.getTenantId())
          .setEnvironment(summaryDoc.getEnvironment()).setTimestamp(DateUtil.getCurrentTimestamp())
          .addAllParticipants(getParticipantList(summaryDoc.getParticipants()))
          .setExperienceId(summaryDoc.getExperienceId()).addAllIntentSummaries(intentSummaries)
          .setGenAiSummary(genAiSummary).setHeader(header).setAgentNotes(agentNotes)
          .addAllReviewedAgentSteps(reviewedAgentSteps).build();

    } catch (Exception e) {
      log.error("Exception at getSummaryProtoForReviewed {}", e);
    }

    return summary;
  }

  private static Map<String, VariableLabels> buildUnmapVariableLabelsInGenAISummaries(
      SummaryDoc summaryDoc) {
    log.debug("buildUnmapVariableLabelsInGenAISummaries:: {}", summaryDoc);
    return Optional.ofNullable(summaryDoc.getGenAiSummary())
        .map(GenAiSummaryDoc::getUnmappedVariableInGenAiSummaries).map(
            unmappedVariables -> unmappedVariables.entrySet().stream().collect(
                Collectors.toMap(Map.Entry::getKey,
                    entry -> VariableLabels.newBuilder().addAllSections(entry.getValue()).build())))
        .orElse(new HashMap<>());
  }

  public static ConversationReviewed buildConversationReviewedProto(SummaryDoc summaryDoc,
      List<AgentStepPojo> agentSteps, long submittedTime) {
    Summary summary = getSummaryProtoForReviewed(summaryDoc, agentSteps);
    log.debug("Building conversation reviewed proto for summary: {}", summary);
    ConversationReviewed message = ConversationReviewed.newBuilder().setType(Feature.FEATURE_CALL_SUMMARIZATION)
        .setSummaryReviewed(summary).setSubmittedTime(submittedTime).build();
    log.debug("Built conversation reviewed proto: {}", message);
    return message;
  }
}
