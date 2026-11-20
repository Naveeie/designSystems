package com.uniphore.uassist.summary;

import com.uniphore.platform.telemetry.helper.PlatformTelemetry;
import com.uniphore.platform.telemetry.helper.TelemetryTypeEnum;
import com.uniphore.ri.auth.httpclient.HttpClientConfig;
import com.uniphore.ri.auth.httpclient.RestExchangeInterceptor;
import com.uniphore.ri.auth.httpclient.RestTemplateConfig;
import com.uniphore.ri.auth.httpclient.SocketFactoryConfig;
import com.uniphore.uassist.summary.common.Constants;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.prometheus.client.CollectorRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.HttpClientErrorException.TooManyRequests;
import org.springframework.web.client.HttpServerErrorException.BadGateway;
import org.springframework.web.client.HttpServerErrorException.GatewayTimeout;
import org.springframework.web.client.HttpServerErrorException.ServiceUnavailable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@ComponentScan(
    {"com.uniphore.uassist.summary", "com.uniphore.platform.cache", "com.uniphore.encryption",
        "com.uniphore.featureflag", "com.uniphore.ri.auth", "com.uniphore.platform.telemetry",
        "com.uniphore.ri.common.usermgmt"})
@EnableScheduling
public class SummaryApplication {

  @Autowired
  private RestExchangeInterceptor restExchangeInterceptor;

  @Autowired
  private HttpClientConfig httpClientConfig;

  @Autowired
  private OpenTelemetry openTelemetry;

  @Autowired
  private MeterRegistry meterRegistry;

  @Autowired
  private CollectorRegistry collectorRegistry;

  @Value("${app.http.connection.retries.max-count:10}")
  private int maxRetries;

  @Value("${app.http.connection.retries.backoff-period:3000}")
  private int backOffPeriod;

  @Value("${spring.application.name}")
  private String servicename;

  public static void main(String[] args) {
    SpringApplication.run(SummaryApplication.class, args);
  }

  @Bean
  RestTemplate restTemplate(final RestTemplateBuilder templateBuilder)
      throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
    final SocketFactoryConfig socketFactoryConfig = new SocketFactoryConfig();
    final SSLConnectionSocketFactory defaultSocketFactory =
        socketFactoryConfig.defaultSocketFactory();
    final CloseableHttpClient closeableHttpClient =
        this.httpClientConfig.httpClient(defaultSocketFactory);
    final RestTemplate restTemplate =
        new RestTemplateConfig().restTemplate(templateBuilder, closeableHttpClient);
    restTemplate.getInterceptors().add(this.restExchangeInterceptor);
    return restTemplate;
  }

  @Bean
  public RetryTemplate retryTemplate() {

    Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
    retryableExceptions.put(TooManyRequests.class, true);
    retryableExceptions.put(BadGateway.class, true);
    retryableExceptions.put(GatewayTimeout.class, true);
    retryableExceptions.put(ServiceUnavailable.class, true);
    retryableExceptions.put(ResourceAccessException.class, true);
    retryableExceptions.put(RestClientException.class, true);
    RetryPolicy retryPolicy = new SimpleRetryPolicy(this.maxRetries, retryableExceptions);
    FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
    backOffPolicy.setBackOffPeriod(this.backOffPeriod);

    RetryTemplate retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(retryPolicy);
    retryTemplate.setBackOffPolicy(backOffPolicy);
    retryTemplate.setThrowLastExceptionOnExhausted(false);
    return retryTemplate;
  }

  @Bean(name = "platformTelemetry")
  public PlatformTelemetry platformTelemetry() {
    List<String> tags = new LinkedList<>();
    tags.add(Constants.TAG_TYPE);
    return new PlatformTelemetry(tags, tags, this.servicename, meterRegistry, collectorRegistry,
        openTelemetry, TelemetryTypeEnum.OPEN_TELEMETRY);
  }

  @Bean
  public PlatformTelemetry summaryTypePlatformTelemetry() {
    List<String> tags = new LinkedList<>();
    tags.add(Constants.TAG_TYPE);
    tags.add(Constants.HEADER);
    tags.add(Constants.INTENT);
    return new PlatformTelemetry(tags, tags, this.servicename, meterRegistry, collectorRegistry,
        openTelemetry, TelemetryTypeEnum.OPEN_TELEMETRY);
  }
}
