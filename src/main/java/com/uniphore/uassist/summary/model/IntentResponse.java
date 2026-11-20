package com.uniphore.uassist.summary.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = IntentResponse.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class IntentResponse {
  private String intentId;
  private String summary;
  private String disposition;
  private String source;
  private String timestamp;
  private String instanceId;


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
