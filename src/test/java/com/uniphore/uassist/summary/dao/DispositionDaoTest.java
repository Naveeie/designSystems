package com.uniphore.uassist.summary.dao;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.cache.DispositionCacheHelper;
import com.uniphore.uassist.summary.dao.documents.DispositionDoc;
import com.uniphore.uassist.summary.model.Disposition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class DispositionDaoTest {

  @Mock
  private DispositionRepo dispositionRepo;

  @Mock
  private DispositionCacheHelper dispositionCache;

  @InjectMocks
  private DispositionDao dispositionDao;

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
  void testUpsertDisposition() {
    Disposition disposition = new Disposition();
    disposition.setBusinessProcessId("bp123");
    DispositionDoc existingDoc = DispositionDoc.builder().build();
    when(dispositionRepo.findDispositionByBusinessProcessIdAndTenantIdAndEnvironment("bp123",
        "3e37631c-78ac-4137-8415-72dcd90b19fd", "dev")).thenReturn(
        Optional.ofNullable(existingDoc));
    DispositionDoc savedDoc = DispositionDoc.builder().build();
    when(dispositionRepo.save(any(DispositionDoc.class))).thenReturn(savedDoc);
    DispositionDoc result = dispositionDao.upsertDisposition(disposition);
    assertNotNull(result);
    verify(dispositionRepo, times(1)).findDispositionByBusinessProcessIdAndTenantIdAndEnvironment(
        "bp123", "3e37631c-78ac-4137-8415-72dcd90b19fd", "dev");
    verify(dispositionRepo, times(1)).save(any(DispositionDoc.class));
  }

  @Test
  void testFetchDispositionByBusinessProcess() {
    DispositionDoc doc = DispositionDoc.builder().build();
    when(dispositionRepo.findDispositionByBusinessProcessIdAndTenantIdAndEnvironment("bp123",
        "3e37631c-78ac-4137-8415-72dcd90b19fd", "dev")).thenReturn(Optional.ofNullable(doc));
    Disposition result = dispositionDao.fetchDispositionByBusinessProcess("bp123");
    assertNotNull(result);
    verify(dispositionRepo, times(1)).findDispositionByBusinessProcessIdAndTenantIdAndEnvironment(
        "bp123", "3e37631c-78ac-4137-8415-72dcd90b19fd", "dev");
  }

  @Test
  void testCopyDispositionsToTargetEnvironment() {
    String tenantId = "tenantId123";
    String sourceEnv = "sourceEnv";
    String targetEnv = "targetEnv";
    String userId = "userId";

    DispositionDoc doc = DispositionDoc.builder().build();
    when(dispositionRepo.findDispositionByTenantIdAndEnvironment(tenantId, sourceEnv)).thenReturn(
        List.of(doc));
    dispositionDao.copyDispositionsToTargetEnvironment(tenantId, sourceEnv, targetEnv, userId);
    verify(dispositionRepo, times(1)).deleteDispositionByTenantIdAndEnvironment(tenantId,
        targetEnv);
    verify(dispositionRepo, times(1)).saveAll(anyList());
    verify(dispositionCache, times(1)).set(any());
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
