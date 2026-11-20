package com.uniphore.uassist.summary.common;

import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.platform.telemetry.helper.PlatformTelemetry;
import com.uniphore.uassist.summary.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

@Component("customMetricsPublisher")
@Slf4j
public class CustomMetricsPublisher{
  
  public static final String S_S = "%s_%s";
  public static final String REGEX_COMMA = ",";
  @Value("${telemetry.bucket.summary}")
  String summaryBucket;

  @Autowired
  private PlatformTelemetry platformTelemetry;

  @Autowired
  @Qualifier("summaryTypePlatformTelemetry")
  private PlatformTelemetry summaryTypePlatformTelemetry;

  public void histogramCount(final String eventName, final long count) {
    log.debug("histogram metric push: {}, tenant context: {}", eventName, TenantContext.getMap());
    this.platformTelemetry.summary(String.format(S_S, eventName, Constant.COUNT), count);
  }

  public void updateAndLatencyMap(final String eventName, final long eventStartTime,
      final Map<String, String> tags) {
    log.debug("updateAndLatency metric push: {}, tenant context: {}", eventName,
        TenantContext.getMap());
    this.platformTelemetry.counterMap(eventName, tags);
    this.platformTelemetry.histogramMap(String.format(S_S, eventName, Constants.LATENCY),
        Arrays.stream(summaryBucket.split(REGEX_COMMA)).map(String::trim)
            .mapToDouble(Double::parseDouble).toArray(), eventStartTime, DateUtil.TIME.millis(),
        tags);
  }

  public void counterUpdateMap(final String eventName, final Map<String, String> tags) {
    log.debug("counterUpdate metric  push: {}, tenant context:  {}", eventName,
        TenantContext.getMap());
    this.platformTelemetry.counterMap(eventName, tags);
  }

  public void counterUpdate(final String eventName, final String tags) {
    log.debug("counterUpdate metric push: {}, tenant context: {}", eventName,
        TenantContext.getMap());
    this.platformTelemetry.counter(eventName, tags);
  }

  public void counterUpdateType(final String eventName, final String type, final String headerType,
      String intentType) {
    log.debug("counterUpdateType metric push: {}, tenant context: {}", eventName,
        TenantContext.getMap());
    this.summaryTypePlatformTelemetry.counter(eventName, type, headerType, intentType);
  }
}
