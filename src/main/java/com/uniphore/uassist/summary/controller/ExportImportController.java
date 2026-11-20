package com.uniphore.uassist.summary.controller;

import java.util.concurrent.CompletableFuture;

import jakarta.servlet.http.HttpServletRequest;

import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.platform.telemetry.context.ContextSetter;
import com.uniphore.ri.auth.constant.Constant;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.ri.common.validation.UIMessages;
import com.uniphore.uassist.summary.model.ExportImportSummary;
import com.uniphore.uassist.summary.model.ImportSummaryRequest;
import com.uniphore.uassist.summary.service.ExportImportService;

import lombok.extern.slf4j.Slf4j;

@RestController("ExportImportController")
@Validated
@Slf4j
public class ExportImportController {

  @Autowired
  private ExportImportService exportImportService;

  @GetMapping(path = "/export/{expId}/summary")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Void> exportSummary(
      @NotNull(
          message = UIMessages.MUST_NOT_BE_EMPTY) @PathVariable("expId") final String experienceId,
      @NotNull(
          message = UIMessages.MUST_NOT_BE_EMPTY) @RequestParam("requestId") final String requestId) {
    String tenantId = TenantUtils.getTenantDetails().getTenantId();
    String environment = TenantUtils.getTenantDetails().getEnvironment();
    CompletableFuture.runAsync(() -> {
      try {
        ContextSetter.setContext(tenantId, environment);
        this.exportImportService.exportSummaryInThread(experienceId, requestId);
      } catch (Exception e) {
        log.error("error in exporting summary ,requestId :{}", requestId, e);
      } finally {
        ContextSetter.clearContext();
      }
    });
    return ResponseEntity.ok().build();
  }

  @PostMapping(path = "/import/summary")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<String> importSummaryByExperience(@RequestBody byte[] requestBody,
      final HttpServletRequest request) {
    try {
      String tenantId = TenantUtils.getTenantDetails().getTenantId();
      String environment = TenantUtils.getTenantDetails().getEnvironment();
      String authToken = request.getHeader(Constant.AUTHORIZATION);
      ObjectMapper objectMapper = new ObjectMapper();
      ImportSummaryRequest importRequest =
          objectMapper.readValue(requestBody, ImportSummaryRequest.class);
      CompletableFuture.runAsync(() -> {
        try {
          ContextSetter.setContext(tenantId, environment);
          exportImportService.importSummaryInThread(importRequest, authToken);
        } catch (Exception e) {
          log.error("error in importing summary for requestId: {}", importRequest.getRequestId(),
              e);
        } finally {
          ContextSetter.clearContext();
        }
      });
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      log.error("Failed to map body to import request object ", e);
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping(path = "/rollback/{expId}/summary")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Void> rollbackSummary(
      @NotNull(message = UIMessages.MUST_NOT_BE_EMPTY) final String expId,
      @NotNull(message = UIMessages.MUST_NOT_BE_EMPTY) final String requestId,
      @RequestBody byte[] requestBody) {
    String tenantId = TenantUtils.getTenantDetails().getTenantId();
    String environment = TenantUtils.getTenantDetails().getEnvironment();
    CompletableFuture.runAsync(() -> {
      try {
        ContextSetter.setContext(tenantId, environment);
        ObjectMapper objectMapper = new ObjectMapper();
        ExportImportSummary exportRequest =
            objectMapper.readValue(requestBody, ExportImportSummary.class);
        this.exportImportService.rollbackSummaryInThread(exportRequest, expId, requestId);
      } catch (Exception e) {
        log.error("error in rollbacking summary for requestId :{}", requestId, e);
      } finally {
        ContextSetter.clearContext();
      }
    });
    return ResponseEntity.ok().build();
  }
}
