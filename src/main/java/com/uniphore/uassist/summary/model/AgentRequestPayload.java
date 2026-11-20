package com.uniphore.uassist.summary.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;
import com.uniphore.uassist.summary.execption.ErrorCodes;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = AgentRequestPayload.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class AgentRequestPayload {

  private String conversationId;
  private String extConversationId;

  @NotEmpty(message = ErrorCodes.BUSINESS_PROCESS_ID_NOT_EMPTY)
  private String businessProcessId;
  private String agentNotes;
  private String headerSummary;
  private String intentId;
  private String instanceId;
  private String intentSummary;
  private String disposition;
  private String source;
  private String agentId;
  private GenAiSummaryDoc genAiSummaryDoc;
  private List<EntityEditPayload> entityEditPayloads;


  @lombok.Data
  @lombok.AllArgsConstructor
  @lombok.NoArgsConstructor
  @JsonDeserialize(builder = EntityEditPayload.Builder.class)
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  public static class EntityEditPayload {
    private boolean isEntity;
    private String editedComponentId;
    private String editedValue;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
