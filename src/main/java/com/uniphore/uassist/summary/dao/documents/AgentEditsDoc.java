package com.uniphore.uassist.summary.dao.documents;

import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document("AgentEdits")
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
@Data
@JsonInclude(value = Include.NON_NULL)
public class AgentEditsDoc {

  private String conversationId;
  private String tenantId;
  private String environment;
  private String businessProcessId;
  private String intentId;
  private String instanceId;
  private String language;
  private String text;
  private String genAiText;
  private String genAiTextEdited;
  private String plainText;
  private String editedText;
  private String editedPlainText;
  private String action;
  private TimeStampDoc timestamp;
  private String agentId;


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
