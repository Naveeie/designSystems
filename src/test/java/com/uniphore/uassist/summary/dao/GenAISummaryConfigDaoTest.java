package com.uniphore.uassist.summary.dao;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.cache.GenAISummaryConfigCacheHelper;
import com.uniphore.uassist.summary.dao.documents.GenAISummaryConfigDoc;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GenAISummaryConfigDaoTest {

  @Mock
  private GenAISummaryConfigRepo genAISummaryConfigRepo;

  @Mock
  private GenAISummaryConfigCacheHelper genAISummaryConfigCache;

  @InjectMocks
  private GenAISummaryConfigDao genAISummaryConfigDao;

  @Mock
  private MongoTemplate mongoTemplate;

  @Container
  public static MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:5"));

  static {
    container.start();
  }

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @DynamicPropertySource
  static void mongoDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", container::getReplicaSetUrl);
  }

  @Test
  void testUpsertGenAISummaryConfig() {
    GenAISummaryConfig genAISummaryConfig = new GenAISummaryConfig();
    genAISummaryConfig.setExperienceId("exp123");
    GenAISummaryConfigDoc existingDoc = GenAISummaryConfigDoc.builder().build();
    existingDoc.setExperienceId("exp123");
    GenAISummaryConfigDoc savedDoc = GenAISummaryConfigDoc.builder().build();
    savedDoc.setExperienceId("exp123");
    when(genAISummaryConfigRepo.findGenAISummaryConfigByExperienceIdAndTenantIdAndEnvironment(
        anyString(), anyString(), anyString())).thenReturn(Optional.of(existingDoc));
    when(genAISummaryConfigRepo.save(any(GenAISummaryConfigDoc.class))).thenReturn(savedDoc);
    GenAISummaryConfigDoc result =
        genAISummaryConfigDao.upsertGenAISummaryConfig(genAISummaryConfig);
    assertThat(result).isNotNull();
    assertThat(result.getExperienceId()).isEqualTo("exp123");
    verify(genAISummaryConfigRepo,
        times(1)).findGenAISummaryConfigByExperienceIdAndTenantIdAndEnvironment(anyString(),
        anyString(), anyString());
    verify(genAISummaryConfigRepo, times(1)).save(any(GenAISummaryConfigDoc.class));
  }

  @Test
  void testFetchGenAISummaryConfigByExpId() {
    GenAISummaryConfigDoc doc = GenAISummaryConfigDoc.builder().build();
    doc.setExperienceId("exp123");
    when(genAISummaryConfigRepo.findGenAISummaryConfigByExperienceIdAndTenantIdAndEnvironment(
        anyString(), anyString(), anyString())).thenReturn(Optional.of(doc));
    GenAISummaryConfig result = genAISummaryConfigDao.fetchGenAISummaryConfigByExpId("exp123");
    assertThat(result).isNotNull();
    assertThat(result.getExperienceId()).isEqualTo("exp123");
    verify(genAISummaryConfigRepo,
        times(1)).findGenAISummaryConfigByExperienceIdAndTenantIdAndEnvironment(anyString(),
        anyString(), anyString());
  }

  @Test
  void testCopyGenAISummaryConfigToTargetEnvironment() {
    GenAISummaryConfigDoc doc = GenAISummaryConfigDoc.builder().build();
    doc.setExperienceId("exp123");
    doc.setEnvironment("sourceEnv");
    List<GenAISummaryConfigDoc> docs = List.of(doc);
    when(genAISummaryConfigRepo.findGenAISummaryConfigByTenantIdAndEnvironment(anyString(),
        anyString())).thenReturn(docs);
    when(genAISummaryConfigRepo.saveAll(anyList())).thenReturn(docs);
    genAISummaryConfigDao.copyGenAISummaryConfigToTargetEnvironment("tenantId", "sourceEnv",
        "targetEnv", "userId");
    verify(genAISummaryConfigRepo, times(1)).deleteGenAISummaryConfigByTenantIdAndEnvironment(
        anyString(), anyString());
    verify(genAISummaryConfigRepo, times(1)).findGenAISummaryConfigByTenantIdAndEnvironment(
        anyString(), anyString());
    verify(genAISummaryConfigRepo, times(1)).saveAll(anyList());
    verify(genAISummaryConfigCache, times(1)).remove("targetEnv");
    verify(genAISummaryConfigCache, times(1)).setForDeployment(any(GenAISummaryConfig.class),
        eq("targetEnv"));
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
