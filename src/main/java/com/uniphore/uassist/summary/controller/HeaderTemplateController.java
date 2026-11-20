package com.uniphore.uassist.summary.controller;

import com.uniphore.platform.telemetry.api.monitor.Monitor;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.uassist.summary.common.BeanValidation;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.HeaderTemplate;
import com.uniphore.uassist.summary.service.HeaderTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@RestController
@Validated
public class HeaderTemplateController {

	@Autowired
	private HeaderTemplateService headerTemplateService;

	@PutMapping(path = "/v1/header-template", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Object> upsertHeaderTemplate(@RequestBody @Valid final HeaderTemplate headerTemplateReq,
			@RequestAttribute(Constants.X_USERNAME) final String userName) {
		headerTemplateReq.setUserId(userName);
		return new ResponseEntity<>(this.headerTemplateService.upsertHeaderTemplate(headerTemplateReq), HttpStatus.OK);
	}

	@GetMapping(path = "/v1/header-template")
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Object> fetchHeaderTemplate(
			@RequestParam("businessProcessId") @NotEmpty(message = ErrorCodes.BUSINESS_PROCESS_ID_NOT_EMPTY) final String businessProcessId) {
		return new ResponseEntity<>(this.headerTemplateService.fetchHeaderTemplate(businessProcessId), HttpStatus.OK);
	}

	@GetMapping(path = "/v1/header-templates")
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Object> fetchHeaderTemplate(
			@RequestParam("businessProcessIds") final List<String> businessProcessIds) {
	  BeanValidation.validateBusinessProcessIds(businessProcessIds);
	  return new ResponseEntity<>(this.headerTemplateService.fetchHeaderTemplates(businessProcessIds), HttpStatus.OK);
	}

}
