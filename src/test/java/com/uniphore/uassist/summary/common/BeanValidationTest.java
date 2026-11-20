package com.uniphore.uassist.summary.common;

import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.AgentRequestPayload;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BeanValidationTest {

  @Test
  public void testValidateIntentInstanceIdWithValidInstanceId() {
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setInstanceId("validInstanceId");
    BeanValidation.validateIntentInstanceId(payload);
  }

  @Test
  public void testValidateIntentInstanceIdWithEmptyInstanceId() {
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setInstanceId("");
    CustomException thrown = assertThrows(CustomException.class, () -> {
      BeanValidation.validateIntentInstanceId(payload);
    });
    assertEquals(ErrorCodes.INSTANCE_ID_NOT_EMPTY, thrown.getCode());
    assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
  }

  @Test
  public void testValidateIntentIdWithValidIntentId() {
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setIntentId("validIntentId");
    BeanValidation.validateIntentId(payload);
  }

  @Test
  public void testValidateIntentIdWithEmptyIntentId() {
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setIntentId("");
    CustomException thrown = assertThrows(CustomException.class, () -> {
      BeanValidation.validateIntentId(payload);
    });
    assertEquals(ErrorCodes.INTENT_ID_NOT_EMPTY, thrown.getCode());
    assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
  }

  @Test
  public void testValidateAgentEditRequestWithValidInputs() {
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setIntentSummary("summary");
    payload.setDisposition("disposition");
    payload.setInstanceId("validInstanceId");
    BeanValidation.validateAgentEditRequst(payload);
  }

  @Test
  public void testValidateAgentEditRequestWithEmptyInstanceId() {
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setIntentSummary("summary");
    payload.setDisposition("disposition");
    CustomException thrown = assertThrows(CustomException.class, () -> {
      BeanValidation.validateAgentEditRequst(payload);
    });

    assertEquals(ErrorCodes.INSTANCE_ID_NOT_EMPTY, thrown.getCode());
    assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
  }

  @Test
  public void testValidateBusinessProcessIdsWithValidList() {
    List<String> businessProcessIds = List.of("bp1", "bp2");
    BeanValidation.validateBusinessProcessIds(businessProcessIds);
  }

  @Test
  public void testValidateBusinessProcessIdsWithEmptyList() {
    List<String> businessProcessIds = Collections.emptyList();
    CustomException thrown = assertThrows(CustomException.class, () -> {
      BeanValidation.validateBusinessProcessIds(businessProcessIds);
    });
    assertEquals(ErrorCodes.BUSINESS_PROCESS_ID_NOT_EMPTY, thrown.getCode());
    assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
  }
}
