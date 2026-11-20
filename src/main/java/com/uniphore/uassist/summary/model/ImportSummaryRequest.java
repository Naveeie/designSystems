package com.uniphore.uassist.summary.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ImportSummaryRequest {

  @JsonProperty("expId")
  private String experienceId;

  @JsonProperty("reqId")
  private String requestId;

  @JsonProperty("feature")
  private String feature;

  @JsonProperty("config")
  private JsonNode config;

  @JsonProperty("dependency")
  private Map<String, List<Dependency>> dependencies;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Dependency {
    @JsonProperty("oldId")
    private String oldId;
    @JsonProperty("newId")
    private String newId;
  }
}
