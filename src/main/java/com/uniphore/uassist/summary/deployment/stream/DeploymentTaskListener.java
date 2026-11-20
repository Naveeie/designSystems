package com.uniphore.uassist.summary.deployment.stream;

import com.google.protobuf.InvalidProtocolBufferException;
import com.uniphore.environments.v1.DeploymentTask;
import com.uniphore.platform.telemetry.context.ContextSetter;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.deployment.service.DeploymentService;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@Slf4j
public class DeploymentTaskListener {

  private final DeploymentService deploymentService;

  public DeploymentTaskListener(DeploymentService deploymentService) {
    this.deploymentService = deploymentService;
  }

  @Bean
  public Consumer<byte[]> deploymentRequest() {
    return record -> {

      try {
        log.debug("Listening to Deployment-events");
        DeploymentTask task = DeploymentTask.newBuilder().build().getParserForType().parseFrom(
            record);
        ContextSetter.setContextWithSpan("uassist-summary", "deployment-events", task.getTenantId(),
            task.getTargetEnvironment());

        handleDeploymentTask(task);
      } catch (InvalidProtocolBufferException e) {
        log.error("ProtocolBuffer TypeCasting for : {}", "deploymentRequest-in", e);
      }
    };
  }

  private void handleDeploymentTask(DeploymentTask task) {
    if (task.getResourcesMap().containsKey(Constants.SUMMARIES)) {
      this.deploymentService.deploySummaries(task, Constants.SUMMARIES);
    }
  }
}
