package com.uniphore.uassist.summary.dao.documents;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.uniphore.uassist.summary.common.TemplateConfigurer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Document("Summary")
@CompoundIndex(name = "summary_compound_index",
    def = "{'tenantId' : 1, 'environment': 1, 'conversationId': 1}")
@CompoundIndex(name = "summary_compound_index_ext_conv_id",
    def = "{'tenantId' : 1, 'environment': 1, 'extConversationId': 1}")
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
@Data
@JsonInclude(value = Include.NON_NULL)
public class SummaryDoc implements Serializable {

  private static final long serialVersionUID = 1L;
  @Id
  @NotNull
  private String conversationId;
  @NotNull
  private String tenantId;
  private String environment;
  private String businessProcessId;
  private String language;
  private String experienceId;
  private HeaderSummaryDoc headerSummary;

  //instanceId of intent and intentsummarydoc
  private Map<String, IntentSummaryDoc> intentSummaries;
  private Map<String, Slot> entities;
  private Map<String, Slot> variables;
  private AgentNotesDoc agentNotes;
  private List<ParticipantDoc> participants;
  @Indexed
  private TimeStampDoc timestamp;

  private GenAiSummaryDoc genAiSummary;
  private GenAiSummaryDoc genAiGeneratedSummary;
  private Map<String, IntentSummaryDoc> systemIntentSummaries;
  @Indexed
  private String extConversationId;

  private boolean templateSummaryEdit;
  private boolean genaiSummaryEdit;
  private Map<String, Object> metadata;
  private String intentLabel;
  private String templateId;
  private Integer templateVersion;
  private TemplateConfigurer templateAssignedBy;


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }


  @AllArgsConstructor
  @NoArgsConstructor
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  @Data
  public static class Slot {
    private String value;
    private String id;
    private String instanceId;
    private boolean isEdited;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }
}
