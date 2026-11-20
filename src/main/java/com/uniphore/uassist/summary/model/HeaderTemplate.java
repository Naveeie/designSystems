package com.uniphore.uassist.summary.model;

import jakarta.validation.constraints.NotEmpty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import lombok.NoArgsConstructor;

@lombok.Data
@lombok.AllArgsConstructor
@NoArgsConstructor
@JsonDeserialize(builder = HeaderTemplate.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class HeaderTemplate {

  private String headerTemplateId;
  private String tenantId;
  private String environment;
  @NotEmpty(message = ErrorCodes.BUSINESS_PROCESS_ID_NOT_EMPTY)
  private String businessProcessId;
  private String template;
  private String parsedTemplate;
  @JsonIgnore
  private String userId;

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }

}
