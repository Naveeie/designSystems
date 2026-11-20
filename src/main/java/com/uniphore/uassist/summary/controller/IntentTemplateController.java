package com.uniphore.uassist.summary.controller;

import com.uniphore.platform.telemetry.api.monitor.Monitor;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.IntentTemplate;
import com.uniphore.uassist.summary.service.IntentTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@RestController
@Validated
public class IntentTemplateController {

	@Autowired
	private IntentTemplateService intentTemplateService;

	@PutMapping(path = "/v1/intent-template", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Object> upsertIntentTemplate(@RequestBody @Valid final IntentTemplate intentTemplateReq,
			@RequestAttribute(Constants.X_USERNAME) final String userName) throws Exception {
		intentTemplateReq.setUserId(userName);
		return new ResponseEntity<>(intentTemplateService.upsertIntentTemplate(intentTemplateReq), HttpStatus.OK);
	}

	@GetMapping(path = "/v1/intent-template", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Object> getIntentTemplate(
			@RequestParam("intentId") @NotNull(message = ErrorCodes.INTENT_ID_NOT_EMPTY) final String intentId)
			throws Exception {
		return new ResponseEntity<>(intentTemplateService.getIntentTemplate(intentId), HttpStatus.OK);
	}

	@GetMapping(path = "/v1/intent-templates", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Object> getIntentTemplates(
			@RequestParam("intentIds") @NotNull(message = ErrorCodes.INTENT_ID_NOT_EMPTY) final List<String> intentIds)
			throws Exception {
		return new ResponseEntity<>(intentTemplateService.getIntentTemplates(intentIds), HttpStatus.OK);
	}

	@GetMapping(path = "/v1/validate/{variableId}", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Object> isVariableUsedInSummary(
			@PathVariable("variableId") @NotEmpty(message = ErrorCodes.VARIABLE_ID_NOT_EMPTY) final String variableId)
			throws Exception {
		return new ResponseEntity<>(intentTemplateService.isVariableUsedInSummary(variableId),
				HttpStatus.OK);
	}
}
