package com.uniphore.uassist.summary.redisstream;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class ConfigChangeEvent {
  @JsonProperty("id")
  private String id;
  @JsonProperty("tenantId")
  private String tenantId;
  @JsonProperty("env")
  private String env;
  @JsonProperty("businessProcessId")
  private String businessProcessId;
  @JsonProperty("experienceId")
  private String experienceId;
  @JsonProperty("removeEntityIds")
  private Set<String> removeEntityIds;
  @JsonProperty("removeVariableIds")
  private Set<String> removeVariableIds;
  private String eventType;
  
  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
