package com.uniphore.uassist.summary.common;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.platform.telemetry.helper.PlatformTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class CustomMetricsPublisherTest {

  @Mock
  private PlatformTelemetry platformTelemetry;

  @Mock
  @Qualifier("summaryTypePlatformTelemetry")
  private PlatformTelemetry summaryTypePlatformTelemetry;

  @InjectMocks
  private CustomMetricsPublisher customMetricsPublisher;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  public void testHistogramCount() {
    String eventName = "event";
    long count = 10L;
    customMetricsPublisher.histogramCount(eventName, count);
    verify(platformTelemetry).summary(
        eq(String.format(CustomMetricsPublisher.S_S, eventName, Constant.COUNT)), eq(10.0));
  }

  @Test
  public void testUpdateAndLatencyMap() {
    String eventName = "event";
    long eventStartTime = System.currentTimeMillis();
    Map<String, String> tags = new HashMap<>();
    tags.put("key", "value");
    customMetricsPublisher.summaryBucket = "1,2,3"; // Set the bucket value
    customMetricsPublisher.updateAndLatencyMap(eventName, eventStartTime, tags);
    verify(platformTelemetry).counterMap(eq(eventName), eq(tags));
  }

  @Test
  public void testCounterUpdateMap() {
    String eventName = "event";
    Map<String, String> tags = new HashMap<>();
    tags.put("key", "value");
    customMetricsPublisher.counterUpdateMap(eventName, tags);
    verify(platformTelemetry).counterMap(eq(eventName), eq(tags));
  }

  @Test
  public void testCounterUpdate() {
    String eventName = "event";
    String tags = "tags";
    customMetricsPublisher.counterUpdate(eventName, tags);
    verify(platformTelemetry).counter(eq(eventName), eq(tags));
  }

  @Test
  public void testCounterUpdateType() {
    String eventName = "event";
    String type = "type";
    String headerType = "headerType";
    String intentType = "intentType";
    customMetricsPublisher.counterUpdateType(eventName, type, headerType, intentType);
    verify(summaryTypePlatformTelemetry).counter(eq(eventName), eq(type), eq(headerType),
        eq(intentType));
  }
}

