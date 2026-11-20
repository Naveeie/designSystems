package com.uniphore.uassist.summary.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExportImportSummary {

  @JsonProperty("intentTemplate")
  private List<IntentTemplate> intentTemplate;

  @JsonProperty("headerTemplate")
  private HeaderTemplate headerTemplate;

  @JsonProperty("genAISummaryConfig")
  private GenAISummaryConfig genAISummaryConfig;
}
