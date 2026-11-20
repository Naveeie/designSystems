package com.uniphore.uassist.summary.integrationtests.cucumberglue;

import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.uassist.summary.common.CustomMetricsPublisher;
import com.uniphore.uassist.summary.config.KafkaConfigTest;
import com.uniphore.uassist.summary.config.MongoConfigInitializer;
import com.uniphore.uassist.summary.config.RedisConfigTest;
import com.uniphore.uassist.summary.redisstream.RedisStreamConfig;
import com.uniphore.uassist.summary.util.GrpcClientHelper;
import com.uniphore.uassist.summary.utils.TestConstant;
import io.cucumber.spring.CucumberContextConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles({TestConstant.PROFILE_NAME, TestConstant.PROFILE_REDIS, TestConstant.PROFILE_MONGO,
    TestConstant.PROFILE_KAFKA})
@ContextConfiguration(initializers = {RedisConfigTest.RedisContainerInitializer.class,
    KafkaConfigTest.KafkaContainerInitializer.class, MongoConfigInitializer.MongoInitializer.class})
@CucumberContextConfiguration
@Slf4j
@MockBean({LaunchDarklyHelper.class, GrpcClientHelper.class, CustomMetricsPublisher.class})

public class ApplicationContextTest {

  @MockBean
  private CollectorRegistry collectorRegistry;

  @Autowired
  private MeterRegistry meterRegistry;

  @MockBean
  private OpenTelemetry openTelemetry;
  @Autowired
  private RedisStreamConfig redisStreamConfig;
}
