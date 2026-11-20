package com.uniphore.uassist.summary.conf;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.*;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.prometheus.client.CollectorRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OpenTelemetryConfig {

  @Value("${spring.application.name:uassist-summary}")
  private String servicename;

  @Value("${opentelemetry.exporter.metrics-endpoint}")
  private String metricsEndpoint;

  @Value("${telemetry.bucket.summary}")
  private String summaryBucket;

  @Bean
  public CollectorRegistry collectorRegistry() {
    return CollectorRegistry.defaultRegistry;
  }

  @Bean
  public OpenTelemetry initOpenTelemetry() {
    Resource resource =
        Resource.getDefault().merge(Resource.builder().put("SERVICE", servicename).build());

    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setMeterProvider(
            SdkMeterProvider.builder().setResource(resource).registerMetricReader(
                PeriodicMetricReader.builder(
                        OtlpHttpMetricExporter.builder().setEndpoint(metricsEndpoint).build())
                    .setInterval(Duration.ofMillis(100)).build()).registerView(
                InstrumentSelector.builder().setMeterName("histogram").setName("histogram")
                    .setType(InstrumentType.HISTOGRAM).build(),
                View.builder().setName("histogram").setAggregation(Aggregation.sum()).build()).build())
        .buildAndRegisterGlobal();

    Runtime.getRuntime().addShutdownHook(new Thread(openTelemetrySdk::close));

    return openTelemetrySdk;
  }

}
