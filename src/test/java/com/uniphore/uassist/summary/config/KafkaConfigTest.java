package com.uniphore.uassist.summary.config;

import org.junit.AfterClass;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;
import com.uniphore.uassist.summary.utils.TestConstant;

public class KafkaConfigTest {

  public static class KafkaContainerInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse(TestConstant.KAFKA_DOCKER_IMAGE))
            .withLabel(TestConstant.KAFKA_CONTAINER_LABEL_KEY, TestConstant.CONTAINER_LABEL_VALUE)
            .withExposedPorts(2181, 9093).withReuse(true).withReuse(true)
            .withImagePullPolicy(PullPolicy.alwaysPull());

    @Override
    public void initialize(ConfigurableApplicationContext context) {
      KAFKA.start();
      String kafkaContainerBroker =
          TestConstant.KAFKA_BROKER_KEY + "=" + KAFKA.getBootstrapServers();
      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, kafkaContainerBroker);
    }

    @AfterClass
    public static void cleanup() {
      if (KAFKA != null) KAFKA.stop();
    }
  }

}
