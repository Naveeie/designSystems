package com.uniphore.uassist.summary.config;

import org.junit.AfterClass;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.PullPolicy;
import com.uniphore.uassist.summary.utils.TestConstant;

public class RedisConfigTest {

  public static class RedisContainerInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(TestConstant.REDIS_DOCKER_IMAGE).withExposedPorts(6379)
            .withLabel(TestConstant.REDIS_CONTAINER_LABEL_KEY, TestConstant.CONTAINER_LABEL_VALUE)
            .withImagePullPolicy(PullPolicy.alwaysPull()).withReuse(true);

    @Override
    public void initialize(final ConfigurableApplicationContext context) {
      REDIS.start();
      final String redisContainer = String.format("%s=%s:%d", TestConstant.REDIS_HOST_PORT,
          REDIS.getContainerIpAddress(), REDIS.getFirstMappedPort());
      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, redisContainer);
    }

    @AfterClass
    public static void cleanUp() {
      if (REDIS != null) {
        REDIS.stop();
      }
    }
  }
}
