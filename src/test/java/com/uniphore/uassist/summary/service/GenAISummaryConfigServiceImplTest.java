package com.uniphore.uassist.summary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.cache.GenAISummaryConfigCacheHelper;
import com.uniphore.uassist.summary.cache.HashLocalCache;
import com.uniphore.uassist.summary.dao.GenAISummaryConfigDao;
import com.uniphore.uassist.summary.dao.documents.GenAISummaryConfigDoc;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.helper.DataHelper;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import com.uniphore.uassist.summary.utils.TestConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

import static com.uniphore.uassist.summary.utils.TestConstant.USERNAME;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class GenAISummaryConfigServiceImplTest extends ApplicationTest {
  @MockBean
  private GenAISummaryConfig genAISummaryConfig;

  private GenAISummaryConfigDoc genAISummaryConfigDoc;

  @Autowired
  @Qualifier("genAISummaryConfigLocalCache")
  private HashLocalCache<GenAISummaryConfig> genAISummaryConfigLocalCache;
  @Autowired
  private GenAISummaryConfigCacheHelper genAISummaryConfigCache;
  @Autowired
  private GenAISummaryConfigService genAISummaryConfigService;
  @MockBean
  private GenAISummaryConfigDao genAISummaryConfigDao;
  @Autowired
  private DataHelper dataHelper;

  @Value("${summary.csv.upload-path}")
  private String storageLocation;
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
  void setupData() {
    try {
      TenantContext.setTenantContext("3e37631c-78ac-4137-8415-72dcd90b19fd", USERNAME);
      genAISummaryConfig = this.dataHelper.inputGenAIConfigResponse(TestConstant.GEN_AI_CONFIG_JSON);
      GenAISummaryConfig genAISummaryConfigNoFormat =
          this.dataHelper.inputGenAIConfigResponse(TestConstant.GEN_AI_CONFIG_NO_FORMAT);
      genAISummaryConfigDoc = this.dataHelper.inputGenAIConfigDocResponse(TestConstant.GEN_AI_SUMMARY_CONFIG_DOC);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  @Order(1)
  void testUpsertGenAISummaryConfig() {
    try {
      String config =
          "{\"summaryFormat\":{\"agent-action\":\"number-list\",\"call-reasons\":\"bullet-point\",\"call-outcome\":\"paragraph\"},\"isOnDemandSummaryEnabled\":\"false\",\"isAcwSummaryEnabled\":true,\"isFormatEnabled\":\"true\",\"confidenceLevel\":\"don't show\",\"sectionMap\":{\"agent-actions\":[{\"entityId\":\"11111\",\"variableId\":\"22222\",\"label\":\"label1\",\"placement\":\"end of the section\",\"isShowLabel\":true}],\"call-reasons\":[{\"entityId\":\"33333\",\"variableId\":\"44444\",\"label\":\"label2\",\"placement\":\"end of the section\",\"isShowLabel\":true}],\"call-outcome\":[{\"entityId\":\"55555\",\"variableId\":\"66666\",\"label\":\"label3\",\"placement\":\"end of the section\",\"isShowLabel\":true}]}}";
      GenAISummaryConfig.builder().experienceId(TestConstant.EXPERIENCE_ID).config(config).tenantId(TestConstant.TENANT_ID)
          .environment("dev").userId("User123").build();
      genAISummaryConfigDoc = GenAISummaryConfigDoc.builder().environment("dev").config("config")
          .experienceId(TestConstant.EXPERIENCE_ID).userId("User123").id("id").tenantId(TestConstant.TENANT_ID).build();
      Mockito.when(this.genAISummaryConfigDao.upsertGenAISummaryConfig(genAISummaryConfig))
          .thenReturn(genAISummaryConfigDoc);
      this.genAISummaryConfigService.upsertGenAISummaryConfig(genAISummaryConfig);
    } catch (final Exception e) {
      log.error("exception at testUpsertGenAISummaryConfig : {}", e);
      Assertions.fail("error at testUpsertGenAISummaryConfig: " + e.getMessage());
    }
  }

  @Test
  @Order(2)
  void testUpsertGenAISummaryConfigWithNoFormat() {
    try {
      String config =
          "{\"isOnDemandSummaryEnabled\":false,\"isAcwSummaryEnabled\":true,\"isFormatEnabled\":false,\"confidenceLevel\":\"don't show\",\"sectionMap\":{\"summary\":[{\"entityId\":\"11111\",\"variableId\":\"22222\",\"label\":\"label1\",\"placement\":\"end of the section\",\"isShowLabel\":true}]}}";
      genAISummaryConfig = GenAISummaryConfig.builder().experienceId(TestConstant.EXPERIENCE_ID).config(config)
          .tenantId(TestConstant.TENANT_ID).environment("dev").userId("User123").build();
      genAISummaryConfigDoc = GenAISummaryConfigDoc.builder().environment("dev").config("config")
          .experienceId(TestConstant.EXPERIENCE_ID).userId("User123").id("id").tenantId(TestConstant.TENANT_ID).build();
      Mockito.when(this.genAISummaryConfigDao.upsertGenAISummaryConfig(genAISummaryConfig))
          .thenReturn(genAISummaryConfigDoc);
      this.genAISummaryConfigService.upsertGenAISummaryConfig(genAISummaryConfig);
    } catch (final Exception e) {
      log.error("exception at testUpsertGenAISummaryConfigWithNoFormat : {}", e);
      Assertions.fail("error at testUpsertGenAISummaryConfigWithNoFormat: " + e.getMessage());
    }
  }

  @Test
  @Order(3)
  void testUpsertGenAISummaryConfigWithError() {
    try {
      Mockito.when(this.genAISummaryConfigService.upsertGenAISummaryConfig(null))
          .thenThrow(new RuntimeException("Internal server error"));
      Assertions.assertThrows(NullPointerException.class, () -> {
        this.genAISummaryConfigService.upsertGenAISummaryConfig(null);
      });
    } catch (final Exception e) {
      log.error("exception at testUpsertGenAISummaryConfigWithError : {}", e);
      Assertions.fail("error at testUpsertGenAISummaryConfigWithError: " + e.getMessage());
    }

  }

  @Test
  @Order(4)
  void testGetGenAISummaryConfig() {
    try{
    this.genAISummaryConfigService.getGenAISummaryConfig(TestConstant.EXPERIENCE_ID);
  } catch (final Exception e) {
    log.error("exception at testGetGenAISummaryConfig: {}", e);
    Assertions.fail("error at testGetGenAISummaryConfig: " + e.getMessage());
  }
}

  @Test
  @Order(5)
  void testGetGenAISummaryConfigWithError() {
    try {
      Assertions.assertThrows(CustomException.class, () -> {
        this.genAISummaryConfigService.getGenAISummaryConfig(null);
      });
    } catch (final Exception e) {
      log.error("exception at testGetGenAISummaryConfigWithError : {}", e);
      Assertions.fail("error at testGetGenAISummaryConfigWithError: " + e.getMessage());
    }
  }

  @Test
  @Order(6)
  void testGetIsOnDemand() {
    try{
    String key =
        "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.genAISummaryConfig";
    this.genAISummaryConfigLocalCache.put(key, "expId", genAISummaryConfig);
    Assertions.assertThrows(CustomException.class, () -> {
      this.genAISummaryConfigService.getIsOnDemand(TestConstant.EXPERIENCE_ID);
    });
    } catch (final Exception e) {
      log.error("exception at testFetchHeaderTemplate: {}", e);
      Assertions.fail("error at testFetchHeaderTemplate: " + e.getMessage());
    }
  }

  @Test
  @Order(7)
  void testGetIsOnDemandWithError() {
    try{
    Assertions.assertThrows(CustomException.class, () -> {
      this.genAISummaryConfigService.getIsOnDemand(null);
    });
    } catch (final Exception e) {
      log.error("exception at testGetIsOnDemandWithError: {}", e);
      Assertions.fail("error at testGetIsOnDemandWithError: " + e.getMessage());
    }
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
