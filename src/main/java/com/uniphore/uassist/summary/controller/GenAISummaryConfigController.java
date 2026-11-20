package com.uniphore.uassist.summary.controller;

import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import com.uniphore.uassist.summary.service.GenAISummaryConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Validated
public class GenAISummaryConfigController {

  @Autowired
  private GenAISummaryConfigService genAISummaryConfigService;

  /**
   * @param disposition
   * @param userName
   * @return
   * @throws Exception
   */

  @Value("#{'${gen-ai.language.support}'.split(',')}")
  private List<String> genAISummaryTargetLanguage;

  @GetMapping(path = "/v1/getGenAISumConfig", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> getGenAISummaryConfig(@RequestParam("experienceId") @NotNull(
      message = ErrorCodes.EXPERIENCE_ID_NOT_EMPTY) final String expId) throws Exception {
    GenAISummaryConfig config = genAISummaryConfigService.getGenAISummaryConfig(expId);
    if (null != config) {
      return new ResponseEntity<>(config, HttpStatus.OK);
    } else {
      return new ResponseEntity<>(config, HttpStatus.NO_CONTENT);
    }
  }

  @PutMapping(path = "/v1/upsertGenAISummaryConfig", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> upsertGenAISummaryConfig(
      @RequestBody @Valid final GenAISummaryConfig genAISummaryConfig,
      @RequestAttribute(Constants.X_USERNAME) final String userName) throws Exception {
    genAISummaryConfig.setUserId(userName);
    GenAISummaryConfig genAISumConf =
        genAISummaryConfigService.upsertGenAISummaryConfig(genAISummaryConfig);
    if (null != genAISumConf) {
      return new ResponseEntity<>(genAISumConf, HttpStatus.OK);
    }
    throw new CustomException(ErrorCodes.GENAISUMMARYCONFIG_NOT_CREATED,
        HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @GetMapping(path = "/v1/getIsOnDemand", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> getIsOnDemand(@RequestParam("experienceId") @NotEmpty @NotNull(
      message = ErrorCodes.EXPERIENCE_ID_NOT_EMPTY) final String expId) throws Exception {
    boolean isOnDemand = genAISummaryConfigService.getIsOnDemand(expId);
    Map<String, Object> responseMap =
        Collections.singletonMap("data", Boolean.toString(isOnDemand));
    return ResponseEntity.ok(responseMap);
  }

  @GetMapping(path = "/v1/entityUsedInGenAi")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Map<String, Boolean>> validateEntityUsedInGenAISummary(
      @RequestParam("entityIds") final List<String> entityIdsToValidate,
      @RequestParam("experienceId") @NotNull(
          message = ErrorCodes.EXPERIENCE_ID_NOT_EMPTY) final String expId) {
    return ResponseEntity.ok(
        this.genAISummaryConfigService.validateEntityUsedInGenAISummary(entityIdsToValidate,
            expId));
  }

  @GetMapping(path = "/v1/genAISummaryTargetLanguage")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> getGenAISummaryTargetLanguage() {
    Map<String, String> targetLangaugeMap = new HashMap<>();
    genAISummaryTargetLanguage.forEach(e -> {
      targetLangaugeMap.put(this.getLanguageName(e), e);
    });
    return new ResponseEntity<>(targetLangaugeMap, HttpStatus.OK);
  }

  private String getLanguageName(String code) {
    switch (code.toLowerCase()) {
      case "en-us":
        return "English";
      case "fr-fr":
        return "French";
      case "es-us":
        return "Spanish";
      case "it-it":
        return "Italian";
      default:
        return "Unknown";
    }
  }

}
