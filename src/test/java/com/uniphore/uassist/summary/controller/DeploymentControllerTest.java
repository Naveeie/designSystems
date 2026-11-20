package com.uniphore.uassist.summary.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.featureflag.Tenant;
import com.uniphore.uassist.summary.deployment.service.DeploymentService;
import com.uniphore.uassist.summary.utils.TestConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DeploymentControllerTest extends ApplicationTest {

  @Autowired
  private CacheOperations<TypeReference<Tenant>> tenantCache;
  @MockBean
  private LaunchDarklyHelper ldHelper;
  @MockBean
  private DeploymentService deploymentService;
  @Container
  public static MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:5"));

  static {
    container.start();
  }

  @DynamicPropertySource
  static void mongoDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", container::getReplicaSetUrl);
  }

  @BeforeAll
  void setupData() throws IOException {
    TenantContext.setTenantContext(TestConstant.TENANT_ID, TestConstant.USERNAME);
    tenantCache.put(TestConstant.TENANT_CACHE_KEY, TestConstant.TENANT_ID,
        new TypeReference<Tenant>() {
        });
  }

  @Test
  @Order(1)
  void testStartConfigCopy() {
    try {
      Mockito.doNothing().when(deploymentService)
          .deploySummaries(Mockito.any(), Mockito.anyString());

      ResultActions result = mockMvc.perform(
          post("/v1/summary/deploySummary").param("srcEnv", "dev").param("destEnv", "prod")
              .contentType(MediaType.APPLICATION_JSON));

      result.andExpect(status().isOk()).andExpect(content().string("Successfully moved config"));

      Mockito.verify(deploymentService, Mockito.times(1))
          .deploySummaries(Mockito.any(), Mockito.anyString());
    } catch (Exception e) {
      log.error("exception occurred while starting config copy: ", e);
      Assertions.fail("exception occurred while starting config copy", e);
    }
  }
}
