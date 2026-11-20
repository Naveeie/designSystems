package com.uniphore.uassist.summary.deployment.service;

import com.uniphore.environments.v1.*;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.ri.deployment.service.DeploymentCommonService;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.dao.DispositionDao;
import com.uniphore.uassist.summary.dao.GenAISummaryConfigDao;
import com.uniphore.uassist.summary.dao.HeaderTemplateDao;
import com.uniphore.uassist.summary.dao.IntentTemplateDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class DeploymentService extends DeploymentCommonService {

  @Autowired
  private IntentTemplateDao intentTempDao;
  @Autowired
  private HeaderTemplateDao headerTempDao;
  @Autowired
  private DispositionDao dispositionDao;

  @Autowired
  private GenAISummaryConfigDao getAIConfigDao;
@Autowired
  private LaunchDarklyHelper ldHelper;

  String userId = "1";

  public void deploySummaries(final DeploymentTask task, final String event) {
    Map<String, ResourceDeploymentStatus> status = new HashMap<>();
    ResourceDeploymentStatus summariesDeploymentStatus;

    boolean ffEnabled = this.ldHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_DEPLOY_FF,
        LaunchDarklyConstants.FF_CONTEXT_KIND);
    if (!ffEnabled) {
      summariesDeploymentStatus = ResourceDeploymentStatus.newBuilder().setCode("1")
          .setStatus(Status.STATUS_SUCCESS).setMessage("Summaries deployment success").build();
      status.put(event, summariesDeploymentStatus);
      this.publishDeploymentResponse(this.buildDeploymentStatus(task, status,Plugin.PLUGIN_U_ASSIST_SUMMARY));
      return; // return and do not process further as deploy FF is set to false
    }

    try {
      /* String userId = this.platformService.fetchUserId(task.getUserId()); */
      ResourceComponents featureComponents = task.getResourcesMap().get(event);
      if (featureComponents.getAll()) {
        this.intentTempDao.copyIntentTemplatesToTargetEnvironment(task.getTenantId(),
            task.getSourceEnvironment(), task.getTargetEnvironment(), userId);
        log.debug("Intent Template Deployment Success for the tenantId: {}", task.getTenantId());

        this.headerTempDao.copyHeaderTemplatesToTargetEnvironment(task.getTenantId(),
            task.getSourceEnvironment(), task.getTargetEnvironment(), userId);
        log.debug("Header Template Deployment Success for the tenantId: {}", task.getTenantId());
        this.dispositionDao.copyDispositionsToTargetEnvironment(task.getTenantId(),
            task.getSourceEnvironment(), task.getTargetEnvironment(), userId);
        log.debug("Dipositions Deployment Success for the tenantId: {}", task.getTenantId());

        log.debug("GenAISummaryConfig Deployment Started for the tenantId: {}", task.getTenantId());
        this.getAIConfigDao.copyGenAISummaryConfigToTargetEnvironment(task.getTenantId(),
            task.getSourceEnvironment(), task.getTargetEnvironment(), userId);
        log.debug("GenAISummaryConfig Deployment Success for the tenantId: {}", task.getTenantId());

      }
      summariesDeploymentStatus = ResourceDeploymentStatus.newBuilder().setCode("1")
          .setStatus(Status.STATUS_SUCCESS).setMessage("Summaries deployment success").build();
      status.put(event, summariesDeploymentStatus);
      this.publishDeploymentResponse(this.buildDeploymentStatus(task, status,Plugin.PLUGIN_U_ASSIST_SUMMARY));
      this.logger(task, event, Constants.SUCCESS);
    } catch (Exception e) {
      this.logger(task, event, "failed with error: " + e);
      summariesDeploymentStatus =
          ResourceDeploymentStatus.newBuilder().setCode("0").setStatus(Status.STATUS_FAILURE)
              .setMessage("Summaries deployment failed with exception" + e.getMessage()).build();
      status.put(event, summariesDeploymentStatus);
      this.publishDeploymentResponse(this.buildDeploymentStatus(task, status,Plugin.PLUGIN_U_ASSIST_SUMMARY));
      this.logger(task, event, Constants.FAILURE);
    }
  }

}
