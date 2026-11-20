package com.uniphore.uassist.summary.common;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.AgentRequestPayload;

public final class BeanValidation {
  private BeanValidation() {}

  public static void validateIntentInstanceId(AgentRequestPayload agentRequestPayload) {
    if (StringUtils.isEmpty(agentRequestPayload.getInstanceId())) {
      throw new CustomException(ErrorCodes.INSTANCE_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
    }
  }

  public static void validateIntentId(AgentRequestPayload agentRequestPayload) {
    if (StringUtils.isEmpty(agentRequestPayload.getIntentId())) {
      throw new CustomException(ErrorCodes.INTENT_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
    }
  }

  public static void validateAgentEditRequst(AgentRequestPayload agentRequestPayload) {

    if (StringUtils.isNotEmpty(agentRequestPayload.getIntentSummary()) || StringUtils.isNotEmpty(
        agentRequestPayload.getDisposition())) {
      validateIntentInstanceId(agentRequestPayload);
    }
  }

  public static void validateBusinessProcessIds(List<String> businessProcessIds) {
    if (CollectionUtils.isEmpty(businessProcessIds)) {
      throw new CustomException(ErrorCodes.BUSINESS_PROCESS_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
    }
  }
}
