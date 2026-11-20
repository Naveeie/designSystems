package com.uniphore.uassist.summary.util;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class ReportResponse {
  private String conversationId;
  private String status;
  private String errorMessage;

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}

