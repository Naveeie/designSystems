package com.uniphore.uassist.summary.model;

import com.uniphore.uassist.summary.execption.ErrorCodes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@AllArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
@Data
@Jacksonized
@NoArgsConstructor
public class SummaryTemplate {

  private String conversationId;
  @NotBlank(message = ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
  private String summaryTemplateId;
  @Min(value = 1, message = ErrorCodes.INCORRECT_VERSION)
  private int version;
  private boolean isAgentSelected;
  private String intentLabel;
}
