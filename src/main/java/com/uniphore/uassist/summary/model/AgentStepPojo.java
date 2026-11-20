package com.uniphore.uassist.summary.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
@Data
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class AgentStepPojo implements Serializable {

  private static final long serialVersionUID = 1L;

  private String id;
  private String text;
  private int index;

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
