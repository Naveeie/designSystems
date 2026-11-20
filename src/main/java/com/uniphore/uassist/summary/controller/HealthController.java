package com.uniphore.uassist.summary.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController("HealthController")
public class HealthController {

  @GetMapping(path = "/health/startup", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public void startupProbe() {
    log.trace("Received HealthController-StartUp probe");
  }

  @GetMapping(path = "/health/liveness", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public void livenessProbe() {
    log.trace("Received HealthController-liveness probe");
  }

  @GetMapping(path = "/health/readiness", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public void readinessProbe() {
    log.trace("Received HealthController-readiness probe");
  }
}
