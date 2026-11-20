package com.uniphore.uassist.summary.deployment.service;

import com.uniphore.environments.v1.DeploymentTask;
import com.uniphore.environments.v1.ResourceComponents;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.ri.common.stream.CommonStreamConfig;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.conf.DataStreamConfig;
import com.uniphore.uassist.summary.dao.DispositionDao;
import com.uniphore.uassist.summary.dao.GenAISummaryConfigDao;
import com.uniphore.uassist.summary.dao.HeaderTemplateDao;
import com.uniphore.uassist.summary.dao.IntentTemplateDao;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.MessageChannel;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeploymentServiceTest {

  @Mock
  private IntentTemplateDao intentTempDao;

  @Mock
  private HeaderTemplateDao headerTempDao;

  @Mock
  private DispositionDao dispositionDao;

  @Mock
  private GenAISummaryConfigDao getAIConfigDao;

  @Mock
  private LaunchDarklyHelper ldHelper;

  @InjectMocks
  private DeploymentService deploymentService;

  @Mock
  private DataStreamConfig dataStreamConfig;

  @Mock
  private MessageChannel deploymentResponseOut;

  @Mock
  private CommonStreamConfig streamConfig;

  @Mock
  private MessageChannel messageChannel;

  @Mock
  private StreamBridge streamBridge;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
    when(messageChannel.send(any())).thenReturn(true);
  }

  @Test
  void testDeploySummariesWithFeatureFlagEnabled() {
    // Arrange
    DeploymentTask task = DeploymentTask.newBuilder()
        .putResources(Constants.SUMMARIES, ResourceComponents.newBuilder().setAll(true).build())
        .setTenantId("tenant1").setSourceEnvironment("env1").setTargetEnvironment("env2").build();
    when(ldHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_DEPLOY_FF,
        LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(true);
    deploymentService.deploySummaries(task, Constants.SUMMARIES);
    verify(intentTempDao).copyIntentTemplatesToTargetEnvironment(task.getTenantId(),
        task.getSourceEnvironment(), task.getTargetEnvironment(), deploymentService.userId);
    verify(headerTempDao).copyHeaderTemplatesToTargetEnvironment(task.getTenantId(),
        task.getSourceEnvironment(), task.getTargetEnvironment(), deploymentService.userId);
    verify(dispositionDao).copyDispositionsToTargetEnvironment(task.getTenantId(),
        task.getSourceEnvironment(), task.getTargetEnvironment(), deploymentService.userId);
    verify(getAIConfigDao).copyGenAISummaryConfigToTargetEnvironment(task.getTenantId(),
        task.getSourceEnvironment(), task.getTargetEnvironment(), deploymentService.userId);
  }

  @Test
  void testDeploySummariesWithFeatureFlagDisabled() {
    DeploymentTask task = DeploymentTask.newBuilder()
        .putResources(Constants.SUMMARIES, ResourceComponents.newBuilder().setAll(true).build())
        .setTenantId("tenant1").setSourceEnvironment("env1").setTargetEnvironment("env2").build();
    when(ldHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_DEPLOY_FF,
        LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(false);
    deploymentService.deploySummaries(task, Constants.SUMMARIES);
    verify(intentTempDao, never()).copyIntentTemplatesToTargetEnvironment(any(), any(), any(),
        any());
    verify(headerTempDao, never()).copyHeaderTemplatesToTargetEnvironment(any(), any(), any(),
        any());
    verify(dispositionDao, never()).copyDispositionsToTargetEnvironment(any(), any(), any(), any());
    verify(getAIConfigDao, never()).copyGenAISummaryConfigToTargetEnvironment(any(), any(), any(),
        any());
  }

  @Test
  void testDeploySummariesWithException() {
    DeploymentTask task = DeploymentTask.newBuilder()
        .putResources(Constants.SUMMARIES, ResourceComponents.newBuilder().setAll(true).build())
        .setTenantId("tenant1").setSourceEnvironment("env1").setTargetEnvironment("env2").build();
    when(ldHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_DEPLOY_FF,
        LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(true);
    doThrow(new RuntimeException("Database error")).when(intentTempDao)
        .copyIntentTemplatesToTargetEnvironment(any(), any(), any(), any());
    deploymentService.deploySummaries(task, Constants.SUMMARIES);
    verify(intentTempDao).copyIntentTemplatesToTargetEnvironment(any(), any(), any(), any());
    verify(headerTempDao, never()).copyHeaderTemplatesToTargetEnvironment(any(), any(), any(),
        any());
    verify(dispositionDao, never()).copyDispositionsToTargetEnvironment(any(), any(), any(), any());
    verify(getAIConfigDao, never()).copyGenAISummaryConfigToTargetEnvironment(any(), any(), any(),
        any());
  }
}
