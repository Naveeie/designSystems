package com.uniphore.uassist.summary.config;

import com.uniphore.uassist.summary.conf.OpenTelemetryConfig;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenTelemetryConfigTest {

  private OpenTelemetryConfig openTelemetryConfig;

  @BeforeEach
  public void setUp() {
    openTelemetryConfig = new OpenTelemetryConfig();
    Field fieldServiceName = ReflectionUtils.findField(OpenTelemetryConfig.class, "servicename");
    assert fieldServiceName != null;
    ReflectionUtils.makeAccessible(fieldServiceName);
    ReflectionUtils.setField(fieldServiceName, openTelemetryConfig, "test-service");
    Field metricsEndpoint = ReflectionUtils.findField(OpenTelemetryConfig.class, "metricsEndpoint");
    assert metricsEndpoint != null;
    ReflectionUtils.makeAccessible(metricsEndpoint);
    ReflectionUtils.setField(metricsEndpoint, openTelemetryConfig, "http://localhost:4317");
    Field summaryBucket = ReflectionUtils.findField(OpenTelemetryConfig.class, "summaryBucket");
    assert summaryBucket != null;
    ReflectionUtils.makeAccessible(summaryBucket);
    ReflectionUtils.setField(summaryBucket, openTelemetryConfig, "100,200,300");
  }

  @Test
  public void testInitOpenTelemetry() {
    OpenTelemetry openTelemetry = openTelemetryConfig.initOpenTelemetry();
    assertNotNull(openTelemetry);
  }
}
