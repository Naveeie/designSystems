package com.uniphore.uassist.summary.model;

import jakarta.validation.constraints.NotEmpty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.uniphore.uassist.summary.execption.ErrorCodes;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = IntentTemplate.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class IntentTemplate {

  private String intentTemplateId;
  @NotEmpty(message = ErrorCodes.INTENT_ID_NOT_EMPTY)
  private String intentId;
  private String template;
  @NotEmpty(message = ErrorCodes.EXPERIENCE_ID_NOT_EMPTY)
  private String experienceId;
  @NotEmpty(message = ErrorCodes.BUSINESS_PROCESS_ID_NOT_EMPTY)
  private String businessProcessId;
  private String tenantId;
  private String environment;
  private String parsedTemplate;
  @JsonIgnore
  private String userId;
  @JsonIgnore
  private boolean deleted;

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
