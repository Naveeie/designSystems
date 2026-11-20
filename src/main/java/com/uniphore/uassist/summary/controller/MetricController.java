package com.uniphore.uassist.summary.controller;

import com.uniphore.platform.telemetry.api.monitor.Monitor;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.service.MetricService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotEmpty;
import java.io.IOException;
import java.util.List;

@RestController("MetricController")
@Validated
@Slf4j
@RequestMapping("/v2")
public class MetricController {

@Autowired
private MetricService metricService;

  @Value("${summary.csv.upload-path}")
  private String storageLocation;

  @GetMapping(path = "/report-by-convid")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<ByteArrayResource> getSummaryDetailedReport(
      @NotEmpty(
          message = ErrorCodes.CONVERSATION_ID_NOT_EMPTY)
      @RequestParam(
          value = Constants.CONVERSATION_IDS) final List<String> conversationIds) throws IOException {
    return this.metricService.getSummaryDetailedReport(conversationIds);
  }

}
