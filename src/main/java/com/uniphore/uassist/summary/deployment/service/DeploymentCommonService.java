package com.uniphore.uassist.summary.deployment.service;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import com.google.protobuf.Timestamp;
import com.uniphore.environments.v1.DeploymentTask;
import com.uniphore.environments.v1.DeploymentTaskStatus;
import com.uniphore.environments.v1.Plugin;
import com.uniphore.environments.v1.ResourceDeploymentStatus;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.conf.DataStreamConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class DeploymentCommonService {

  @Autowired
  private StreamBridge streamBridge;

  public void publishDeploymentResponse(DeploymentTaskStatus status) {
    log.debug("deployment status response sent: Id: {} status: {}", status.getDeploymentId(),
        status.getResourcesMap().toString());

    streamBridge.send(DataStreamConfig.OUTPUT_DEPLOYMENT_RESPONSE,
        MessageBuilder.withPayload(status.toByteArray())
            .setHeader(KafkaHeaders.KEY, status.getDeploymentId())
            .build());
  }

  public DeploymentTaskStatus buildDeploymentStatus(final DeploymentTask task,
      final Map<String, ResourceDeploymentStatus> statusMap) {
    return DeploymentTaskStatus.newBuilder().setDeploymentId(task.getDeploymentId())
        .setPlugin(Plugin.PLUGIN_U_ASSIST_SUMMARY).setTenantId(task.getTenantId())
        .setSendTime(Timestamp.newBuilder().setNanos(LocalDateTime.now().getNano()).build())
        .setSourceEnvironment(task.getSourceEnvironment())
        .setTargetEnvironment(task.getTargetEnvironment()).putAllResources(statusMap).build();
  }

  public void logger(DeploymentTask task, final String event, final String status) {
    log.info(
        "deploying event: {} deploymentId: {} engine tenantId: {}, source env: {}, target env: {}, status: {} ",
        event, task.getDeploymentId(), TenantUtils.getTenantDetails().getTenantId(),
        task.getSourceEnvironment(), task.getTargetEnvironment(), status);
  }
}
