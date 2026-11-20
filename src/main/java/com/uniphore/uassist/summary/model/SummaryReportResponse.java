package com.uniphore.uassist.summary.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = SummaryResponse.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class SummaryReportResponse {

  private HeaderSummary headerSummary;

  private List<IntentSummary> intentSummaries;


  @AllArgsConstructor
  @NoArgsConstructor
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  @Data
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  public static class HeaderSummary {
    private String text;
    private List<Entity> entities;
    private List<Entity> variables;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }


  @AllArgsConstructor
  @NoArgsConstructor
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  @Data
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  public static class IntentSummary {

    private String intentId;
    private String instanceId;
    private long startTime;
    private long endTime;
    private String submittedSummary;
    private String systemSummary;
    private boolean isEdited;
    private List<Entity> entities;
    private List<Entity> variables;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }


  @AllArgsConstructor
  @NoArgsConstructor
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  @Data
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  public static class Entity {
    private String value;
    private String id;
    private String instanceId;
    private String detectedBy;
    private long eventTime;
    private String aiValue;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }
}
