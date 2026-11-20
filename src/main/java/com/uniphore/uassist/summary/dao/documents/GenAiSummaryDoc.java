package com.uniphore.uassist.summary.dao.documents;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Document("GenAiSummaryDoc")
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
@Data
public class GenAiSummaryDoc {
  List<Section> sections;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, Set<String>> unmappedVariableInGenAiSummaries;

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }


  @AllArgsConstructor
  @NoArgsConstructor
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  @Data
  public static class Section {
    private String id;
    private String text;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<SpokenEntity> spokenEntities;

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }

  @AllArgsConstructor
  @NoArgsConstructor
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  @Data
  public static class SpokenEntity {
    private String variableId;
    private String variableInstanceId;
    private String value;
    private String label;
    private String placement;
    @JsonProperty("isLabelUnmentioned")
    private boolean isLabelUnmentioned;
    private boolean isEdited;

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }
}
