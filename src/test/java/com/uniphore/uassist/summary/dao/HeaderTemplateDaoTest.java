package com.uniphore.uassist.summary.dao;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.cache.HeaderTemplateCacheHelper;
import com.uniphore.uassist.summary.dao.documents.HeaderTemplateDoc;
import com.uniphore.uassist.summary.model.HeaderTemplate;
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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class HeaderTemplateDaoTest {

  @Mock
  private HeaderTemplateRepository headerTemplateRepo;

  @Mock
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;

  @InjectMocks
  private HeaderTemplateDao headerTemplateDao;

  @Container
  public static MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:5"));

  static {
    container.start();
  }

  @DynamicPropertySource
  static void mongoDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", container::getReplicaSetUrl);
  }

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  void testUpsertHeaderTemplate() {
    HeaderTemplate headerTemplate = new HeaderTemplate();
    headerTemplate.setBusinessProcessId("bp123");
    HeaderTemplateDoc headerTemplateDoc = HeaderTemplateDoc.builder().build();
    when(headerTemplateRepo.findHeaderTemplateByBusinessProcessIdAndTenantIdAndEnvironment(
        any(String.class), any(String.class), any(String.class))).thenReturn(
        Optional.of(headerTemplateDoc));
    when(headerTemplateRepo.save(any(HeaderTemplateDoc.class))).thenReturn(headerTemplateDoc);
    HeaderTemplateDoc savedDoc = headerTemplateDao.upsertHeaderTemplate(headerTemplate);
    assertThat(savedDoc).isNotNull();
    verify(headerTemplateRepo).save(any(HeaderTemplateDoc.class));
  }

  @Test
  void testFetchHeaderTemplate() {
    HeaderTemplateDoc headerTemplateDoc = HeaderTemplateDoc.builder().build();
    when(headerTemplateRepo.findHeaderTemplateByBusinessProcessIdAndTenantIdAndEnvironment(
        any(String.class), any(String.class), any(String.class))).thenReturn(
        Optional.of(headerTemplateDoc));
    HeaderTemplate fetchedHeaderTemplate = headerTemplateDao.fetchHeaderTemplate("bp123");
    assertThat(fetchedHeaderTemplate).isNotNull();
  }

  @Test
  void testFetchHeaderTemplates() {
    HeaderTemplateDoc headerTemplateDoc1 = HeaderTemplateDoc.builder().build();
    HeaderTemplateDoc headerTemplateDoc2 = HeaderTemplateDoc.builder().build();
    List<HeaderTemplateDoc> docs = Arrays.asList(headerTemplateDoc1, headerTemplateDoc2);
    when(headerTemplateRepo.findHeaderTemplateByBusinessProcessIdInAndTenantIdAndEnvironment(
        anyList(), any(String.class), any(String.class))).thenReturn(docs);
    List<HeaderTemplate> headerTemplates =
        headerTemplateDao.fetchHeaderTemplates(Arrays.asList("bp123", "bp456"));
    assertThat(headerTemplates).hasSize(2);
  }

  @Test
  void testCopyHeaderTemplatesToTargetEnvironment() {
    HeaderTemplateDoc headerTemplateDoc1 = HeaderTemplateDoc.builder().build();
    headerTemplateDoc1.setTemplate("template1");
    HeaderTemplateDoc headerTemplateDoc2 = HeaderTemplateDoc.builder().build();
    headerTemplateDoc2.setTemplate("template2");
    List<HeaderTemplateDoc> headerTemplateDocs =
        Arrays.asList(headerTemplateDoc1, headerTemplateDoc2);
    when(headerTemplateRepo.findHeaderTemplateByTenantIdAndEnvironment(any(String.class),
        any(String.class))).thenReturn(headerTemplateDocs);
    headerTemplateDao.copyHeaderTemplatesToTargetEnvironment("tenantId", "sourceEnv", "targetEnv",
        "userId");
    verify(headerTemplateRepo).deleteHeaderTemplateByTenantIdAndEnvironment("tenantId",
        "targetEnv");
    verify(headerTemplateRepo).saveAll(anyList());
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
