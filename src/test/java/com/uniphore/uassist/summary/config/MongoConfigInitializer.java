package com.uniphore.uassist.summary.config;

import com.uniphore.uassist.summary.utils.TestConstant;
import org.junit.AfterClass;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

public class MongoConfigInitializer {
  public static class MongoInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final MongoDBContainer MONGO_DB_CONTAINER =
        new MongoDBContainer(DockerImageName.parse("mongo:6.0")).withLabel(
                TestConstant.MONGO_CONTAINER_LABEL_KEY, TestConstant.CONTAINER_LABEL_VALUE)
            .withReuse(true)
            .withImagePullPolicy(PullPolicy.alwaysPull()).waitingFor(Wait.forListeningPort());;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
      MONGO_DB_CONTAINER.start();
      String mongoUri = "mongodb://" + MONGO_DB_CONTAINER.getContainerIpAddress() + ":" +
          MONGO_DB_CONTAINER.getMappedPort(27017) + "/summary";
      String mongoUrl=String.format("%s=%s", "spring.data.mongodb.uri",mongoUri);
      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, mongoUrl);
    }

    @AfterClass
    public static void cleanup() {
      if (MONGO_DB_CONTAINER != null)
        MONGO_DB_CONTAINER.stop();
    }
  }
}
