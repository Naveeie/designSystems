package com.uniphore.uassist.summary.controller;

import com.uniphore.environments.v1.DeploymentTask;
import com.uniphore.environments.v1.ResourceComponents;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.platform.telemetry.api.monitor.Monitor;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.deployment.service.DeploymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController("DeploymentController")
@Validated
@RequestMapping("/v1")
public class DeploymentController {

  @Autowired
  private DeploymentService deploymentService;

  @PostMapping(path = "/summary/deploySummary", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> startConfigCopy(@RequestParam("srcEnv") final String srcEnv,
      @RequestParam("destEnv") final String destEnv) {
    Map<String, ResourceComponents> map = new HashMap<>();
    ResourceComponents resourceComponents = ResourceComponents.newBuilder().setAll(true).build();
    map.put(Constants.SUMMARIES, resourceComponents);
    DeploymentTask deploymentTask = DeploymentTask.newBuilder().setSourceEnvironment(srcEnv)
        .setTenantId(TenantContext.getMap().get(TenantConstant.META_KEY_TENANT_ID))
        .setTargetEnvironment(destEnv).putAllResources(map).build();
    this.deploymentService.deploySummaries(deploymentTask, Constants.SUMMARIES);
    return new ResponseEntity<>("Successfully moved config", HttpStatus.OK);
  }
}
