package com.uniphore.uassist.summary.dao.documents;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Document("AgentSteps")
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndex(name = "summary_compound_index",
    def = "{'tenantId' : 1, 'environment': 1, 'conversationId': 1}")
@Builder(builderClassName = "Builder", toBuilder = true)
@Data
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class AgentStepsDoc implements Serializable {
  @Id
  private String conversationId;
  private String environment;
  private String tenantId;
  private Map<String, AgentStep> agentSteps;


  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  public static class AgentStep {
    private String id;
    private int order;
    private String text;
    private String detectedBy;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
