package com.uniphore.uassist.summary.controller;

import com.uniphore.platform.telemetry.api.monitor.Monitor;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.service.DispositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@RestController
@Validated
public class DispositionController {

	@Autowired
	private DispositionService dispositionService;
	/**
	 * @param disposition
	 * @param userName
	 * @return
	 * @throws Exception
	 */
	@PutMapping(path = "/v1/disposition", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Object> upsertDisposition(@RequestBody @Valid final Disposition disposition,
			@RequestAttribute(Constants.X_USERNAME) final String userName) throws Exception {
		disposition.setUserId(userName);
		return new ResponseEntity<>(dispositionService.upsertDisposition(disposition), HttpStatus.OK);
	}

	@GetMapping(path = "/v1/disposition", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Object> getIntentTemplate(
			@RequestParam("businessProcessId") @NotNull(message = ErrorCodes.BUSINESS_PROCESS_ID_NOT_EMPTY) final String businessProcessId)
			throws Exception {
		return new ResponseEntity<>(dispositionService.getDisposition(businessProcessId), HttpStatus.OK);
	}

	@GetMapping(path = "/v1/disposition-levels", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Object> getIntentTemplate(
			@RequestParam("businessProcessIds") @NotNull(message = ErrorCodes.BUSINESS_PROCESS_ID_NOT_EMPTY) final List<String> businessProcessIds)
			throws Exception {
		return new ResponseEntity<>(dispositionService.getDispositionLevels(businessProcessIds), HttpStatus.OK);
	}

}
