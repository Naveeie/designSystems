package com.uniphore.uassist.summary.model;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = SummaryResponse.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class SummaryResponse {

  private String conversationId;
  private String businessProcessId;
  private String tenantId;
  private String environment;
  private String agentNote;
  private String language;
  private HeaderResponse header;
  private List<IntentResponse> intents;
  private List<ParticipantResponse> participants;
  private GenAiSummaryDoc genAiSummary;
  private GenAiSummaryDoc genAIGeneratedSummary;
  private List<IntentResponse> systemIntents;

}
