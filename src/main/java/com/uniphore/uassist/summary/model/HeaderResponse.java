package com.uniphore.uassist.summary.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = HeaderResponse.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class HeaderResponse {
  private String summary;
  private String timestamp;
  
  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
