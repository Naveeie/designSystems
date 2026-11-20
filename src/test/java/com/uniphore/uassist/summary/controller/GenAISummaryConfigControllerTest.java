package com.uniphore.uassist.summary.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.dao.GenAISummaryConfigDao;
import com.uniphore.uassist.summary.dao.documents.GenAISummaryConfigDoc;
import com.uniphore.featureflag.Tenant;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import com.uniphore.uassist.summary.service.GenAISummaryConfigService;
import com.uniphore.uassist.summary.utils.TestConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class GenAISummaryConfigControllerTest extends ApplicationTest {
  @Container
  public static MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:5"));
  private static final String TENANT_ID = "3e37631c-78ac-4137-8415-72dcd90b19fd";
  private static final String TENANT_CACHE_KEY = "uniphore.conf.uassist.tenants";
  final ObjectMapper objectMapper = new ObjectMapper();
  @Autowired
  private GenAISummaryConfigService genAISummaryConfigService;
  @MockBean
  private GenAISummaryConfigDao genAISummaryConfigDao;
  private GenAISummaryConfig genAISummaryConfig;
  @Autowired
  private CacheOperations<TypeReference<Tenant>> tenantCache;
  private static final String USERNAME = "dev";
  private static final String GEN_AI_CONFIG_JSON = "gen-ai-config.json";
  private static final String CLASSPATH_JSON_CONFIG = "src/test/resources/json/runtime/";

  static {
    container.start();
  }

  @DynamicPropertySource
  static void mongoDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", container::getReplicaSetUrl);
  }

  @BeforeAll
  void setupData() throws IOException {
    TenantContext.setTenantContext("3e37631c-78ac-4137-8415-72dcd90b19fd", USERNAME);
    genAISummaryConfig = this.inputGenAISummaryConfigResponse(GEN_AI_CONFIG_JSON);
  }

  @Test
  @Order(1)
  void testGetGenAISummaryConfig() {
    try {
      Mockito.when(genAISummaryConfigDao.fetchGenAISummaryConfigByExpId(ArgumentMatchers.any()))
          .thenReturn(genAISummaryConfig);
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      final ResultActions action = this.mockMvc.perform(
          get("/v1/getGenAISumConfig").param("experienceId", "1234")
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting Gen AI summary config ", e);
    }
  }

  @Test
  @Order(2)
  void testGetGenAISummaryConfigNoContent() {
    try {
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      final ResultActions action = this.mockMvc.perform(
          get("/v1/getGenAISumConfig").param("experienceId", "")
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(204, action.andReturn().getResponse().getStatus(), "Status code 204");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting Gen AI summary config with no content ", e);
    }
  }

  /*
   * Test case to get Gen AI summary config with wrong url path
   */
  @Test
  @Order(3)
  void testGetGenAISummaryConfigWrongUrl() {
    try {
      Mockito.when(genAISummaryConfigDao.fetchGenAISummaryConfigByExpId(ArgumentMatchers.any()))
          .thenReturn(genAISummaryConfig);
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      final ResultActions action = this.mockMvc.perform(
          get("/v1/getGenAISum").param("experienceId", "1234")
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(404, action.andReturn().getResponse().getStatus(), "Status code 404");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting Gen AI summary config with no content ", e);
    }
  }

  @Test
  @Order(4)
  void testUpsertGenAISummaryConfig() {
    Mockito.when(genAISummaryConfigDao.upsertGenAISummaryConfig(
        ArgumentMatchers.any(GenAISummaryConfig.class))).thenReturn(
        GenAISummaryConfigDoc.builder().config(genAISummaryConfig.getConfig())
            .environment(genAISummaryConfig.getEnvironment())
            .experienceId(genAISummaryConfig.getExperienceId())
            .userId(genAISummaryConfig.getUserId()).build());
    try {
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      final ResultActions action = this.mockMvc.perform(
          put("/v1/upsertGenAISummaryConfig").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(genAISummaryConfig))
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while upsert gen AI summary config ", e);
    }
  }

  @Test
  @Order(5)
  void testUpsertGenAISummaryConfigWithInternalServerError() {
    try {
      Mockito.when(this.genAISummaryConfigDao.upsertGenAISummaryConfig(ArgumentMatchers.any()))
          .thenReturn(null);
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      final ResultActions action = this.mockMvc.perform(
          put("/v1/upsertGenAISummaryConfig").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(genAISummaryConfig))
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(500, action.andReturn().getResponse().getStatus(), TestConstant.STATUS_500);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while upsert gen AI summary config with error state ", e);
    }
  }

  @Test
  @Order(6)
  void testGetIsOnDemand() {
    try {
      Mockito.when(genAISummaryConfigDao.fetchGenAISummaryConfigByExpId(ArgumentMatchers.any()))
          .thenReturn(genAISummaryConfig);
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      final ResultActions action = this.mockMvc.perform(
          get("/v1/getIsOnDemand").param("experienceId", "1234")
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting is on demand ", e);
    }
  }

  @Test
  @Order(7)
  void testValidateEntityUsedInGenAISummary() {
    String entityIds = "11111,22222";
    try {
      Mockito.when(genAISummaryConfigDao.fetchGenAISummaryConfigByExpId(ArgumentMatchers.any()))
          .thenReturn(genAISummaryConfig);
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      final ResultActions action = this.mockMvc.perform(
          get("/v1/entityUsedInGenAi").param("experienceId", "1234").param("entityIds", entityIds)
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting Gen AI summary config ", e);
    }
  }

  @Test
  @Order(8)
  void testValidateEntityUsedInGenAISummary_IfNoEntityIdsMatch() {
    String entityIds = "1111,2222";
    try {
      Mockito.when(genAISummaryConfigDao.fetchGenAISummaryConfigByExpId(ArgumentMatchers.any()))
          .thenReturn(genAISummaryConfig);
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      final ResultActions action = this.mockMvc.perform(
          get("/v1/entityUsedInGenAi").param("experienceId", "1234").param("entityIds", entityIds)
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting Gen AI summary config ", e);
    }
  }

  @Test
  @Order(9)
  void testGetGenAISummaryTargetLanguage() {
    try {
      final ResultActions action =
          this.mockMvc.perform(get("/v1/genAISummaryTargetLanguage").contentType(MediaType.APPLICATION_JSON));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting Gen AI summary target language", e);
    }
  }

  private GenAISummaryConfig inputGenAISummaryConfigResponse(final String fileName)
      throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<GenAISummaryConfig>() {
        });
  }
}
