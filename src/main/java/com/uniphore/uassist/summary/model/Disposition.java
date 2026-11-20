package com.uniphore.uassist.summary.model;

import java.util.Map;
import jakarta.validation.constraints.NotEmpty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.uniphore.uassist.summary.execption.ErrorCodes;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = Disposition.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class Disposition {

  private String dispositionId;
  private String name;
  private String description;
  private Map<Long, String> values;
  @NotEmpty(message = ErrorCodes.BUSINESS_PROCESS_ID_NOT_EMPTY)
  private String businessProcessId;
  private String environment;
  private String tenantId;
  @JsonIgnore
  private String userId;

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
