package com.uniphore.uassist.summary.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = DispositionLevels.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class DispositionLevels {

  private String dispositionId;
  private String businessProcessId;
  private int count;
  private String environment;
  private String tenantId;

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
