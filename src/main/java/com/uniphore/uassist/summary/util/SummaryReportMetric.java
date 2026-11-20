package com.uniphore.uassist.summary.util;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import com.uniphore.summary.v1.Header;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class SummaryReportMetric {
  private String conversationId;
  private String headerOrIntent;
  private String intentId;
  private String intentName;
  private String timestamp;
  private String sysGenSummary;
  private String agentSummary;
  private String countOfEntities;
  private String countOfEditedEntities;
  private String countOfVariables;
  private String countOfEditedVariables;
  private String disposition;
  private String detectedBy;
  private String expId;
  private String source;


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
