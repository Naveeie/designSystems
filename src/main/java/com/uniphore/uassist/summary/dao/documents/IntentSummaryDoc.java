package com.uniphore.uassist.summary.dao.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Document("IntentSummary")
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
@Data
@JsonInclude(value = Include.NON_NULL)
public class IntentSummaryDoc {
  @Id
  private String intentId;
  private String instanceId;
  // HTML Version text
  private String template;
  // Parsed HTML text to plain text
  private String plainText;
  private String disposition;
  private String source;
  private TimeStampDoc timestamp;
  private Map<String, Boolean> entityIdIsEdited;
  private Map<String,String> entityInstanceIdMap;
  private Map<String,String> variableInstanceIdMap;


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
