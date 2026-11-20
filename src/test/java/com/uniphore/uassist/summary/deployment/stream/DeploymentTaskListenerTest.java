package com.uniphore.uassist.summary.deployment.stream;

import com.uniphore.environments.v1.DeploymentTask;
import com.uniphore.environments.v1.ResourceComponents;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.deployment.service.DeploymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeploymentTaskListenerTest {

  private DeploymentService deploymentService;
  private DeploymentTaskListener deploymentTaskListener;

  @BeforeEach
  void setup() {
    deploymentService = mock(DeploymentService.class);
    deploymentTaskListener = new DeploymentTaskListener(deploymentService);
  }

  @Test
  void testDeploymentRequest_validMessageWithSummaries() throws Exception {
    DeploymentTask task = DeploymentTask.newBuilder()
        .setTenantId("tenant-123")
        .setTargetEnvironment("dev")
        .putResources(Constants.SUMMARIES, ResourceComponents.newBuilder().addIds("1,2,3").build())
        .build();

    byte[] taskBytes = task.toByteArray();
    Consumer<byte[]> consumer = deploymentTaskListener.deploymentRequest();

    // Act
    consumer.accept(taskBytes);

    // Assert
    verify(deploymentService, times(1))
        .deploySummaries(eq(task), eq(Constants.SUMMARIES));
  }

  @Test
  void testDeploymentRequest_validMessageWithoutSummaries() throws Exception {
    DeploymentTask task = DeploymentTask.newBuilder()
        .setTenantId("tenant-123")
        .setTargetEnvironment("dev")
        .build();

    byte[] taskBytes = task.toByteArray();
    Consumer<byte[]> consumer = deploymentTaskListener.deploymentRequest();

    // Act
    consumer.accept(taskBytes);

    // Assert
    verify(deploymentService, never()).deploySummaries(any(), any());
  }

  @Test
  void testDeploymentRequest_invalidMessage() {
    byte[] invalidBytes = "invalid-protobuf".getBytes();
    Consumer<byte[]> consumer = deploymentTaskListener.deploymentRequest();

    // Act
    consumer.accept(invalidBytes);

    // Assert
    verify(deploymentService, never()).deploySummaries(any(), any());
  }
}
