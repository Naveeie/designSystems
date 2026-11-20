package com.uniphore.uassist.summary.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = IntentSummaryReviewedRequest.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class IntentSummaryReviewedRequest {

  @JsonProperty("intent-summaries")
  List<IntentSummary> intentSummaries;


  @AllArgsConstructor
  @NoArgsConstructor
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  @Data
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  public static class IntentSummary {
    private String intentId;
    private String instanceId;
    private String template;
    private String plainText;
    private String disposition;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }

}
