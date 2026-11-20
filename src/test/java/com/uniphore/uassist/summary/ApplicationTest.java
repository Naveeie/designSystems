package com.uniphore.uassist.summary;

import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.config.KafkaConfigTest;
import com.uniphore.uassist.summary.config.RedisConfigTest;
import com.uniphore.uassist.summary.utils.TestConstant;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@ContextConfiguration(initializers = {RedisConfigTest.RedisContainerInitializer.class,
    KafkaConfigTest.KafkaContainerInitializer.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles({TestConstant.PROFILE_NAME, TestConstant.PROFILE_REDIS, TestConstant.PROFILE_MONGO,
    TestConstant.PROFILE_KAFKA})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@WebAppConfiguration
@AutoConfigureWebMvc
@TestInstance(Lifecycle.PER_CLASS)
public class ApplicationTest {

  @MockBean
  private RestTemplate restTemplate;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  public LaunchDarklyHelper launchDarklyHelper;

  @MockBean
  private CollectorRegistry collectorRegistry;

  @Autowired
  private MeterRegistry meterRegistry;

  public static final ObjectMapper objectMapper = new ObjectMapper();

  @MockBean
  private OpenTelemetry openTelemetry;

  protected MockMvc mockMvc;

  @BeforeAll
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
    this.mockTenantContext();
    this.mockUserId(HttpStatus.OK);
    GlobalOpenTelemetry.resetForTest();
  }

  public void mockTenantContext() {
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  private void mockUserId(final HttpStatus status) {
    Mockito.when(this.restTemplate.exchange(ArgumentMatchers.any(String.class),
        ArgumentMatchers.any(HttpMethod.class), ArgumentMatchers.any(),
        ArgumentMatchers.eq(new ParameterizedTypeReference<Long>() {
        }))).thenReturn(new ResponseEntity<>(1l, status));
  }
}
