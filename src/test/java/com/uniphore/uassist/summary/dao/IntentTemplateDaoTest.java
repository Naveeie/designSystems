package com.uniphore.uassist.summary.dao;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.cache.IntentTemplateCacheHelper;
import com.uniphore.uassist.summary.dao.documents.IntentTemplateDoc;
import com.uniphore.uassist.summary.model.IntentTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.*;

public class IntentTemplateDaoTest {

  @Mock
  private IntentTemplateRepo intentTemplateRepo;

  @Mock
  private HeaderTemplateRepository headerTemplateRepo;

  @Mock
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  @InjectMocks
  private IntentTemplateDao intentTemplateDao;

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

  @AfterEach
  void tearDown() {
    reset(intentTemplateRepo, headerTemplateRepo, intentTemplateCacheHelper);
  }

  @Test
  void testUpsertIntentTemplate() {
    IntentTemplate intentTemplate = new IntentTemplate();
    intentTemplate.setIntentId("intent1");
    String tenantId = "tenant1";
    String environment = "env1";
    IntentTemplateDoc existingDoc = IntentTemplateDoc.builder().build();
    existingDoc.setIntentId("intent1");
    when(intentTemplateRepo.findIntentTemplateByIntentIdAndTenantIdAndEnvironment("intent1",
        tenantId, environment)).thenReturn(Optional.of(existingDoc));
    when(intentTemplateRepo.save(any(IntentTemplateDoc.class))).thenAnswer(
        invocation -> invocation.getArguments()[0]);
    IntentTemplateDoc result = intentTemplateDao.upsertIntentTemplate(intentTemplate);
    assertThat(result).isNotNull();
    assertThat(result.getIntentId()).isEqualTo("intent1");
    verify(intentTemplateRepo).save(any(IntentTemplateDoc.class));
  }

  @Test
  void testFetchIntentTemplate() {
    IntentTemplateDoc intentTemplateDoc = IntentTemplateDoc.builder().build();
    intentTemplateDoc.setIntentId("intent1");
    when(intentTemplateRepo.findIntentTemplateByIntentIdAndTenantIdAndEnvironment("intent1",
        "3e37631c-78ac-4137-8415-72dcd90b19fd", "dev")).thenReturn(Optional.of(intentTemplateDoc));
    IntentTemplate result = intentTemplateDao.fetchIntentTemplate("intent1");
    assertThat(result).isNotNull();
    assertThat(result.getIntentId()).isEqualTo("intent1");
  }

  @Test
  void testFetchIntentTemplates() {
    List<IntentTemplateDoc> docs = new ArrayList<>();
    IntentTemplateDoc doc1 = IntentTemplateDoc.builder().build();
    doc1.setIntentId("intent1");
    IntentTemplateDoc doc2 = IntentTemplateDoc.builder().build();
    doc2.setIntentId("intent2");
    docs.add(doc1);
    docs.add(doc2);
    when(intentTemplateRepo.findIntentTemplateByIntentIdInAndTenantIdAndEnvironment(anyList(),
        eq("3e37631c-78ac-4137-8415-72dcd90b19fd"), eq("dev"))).thenReturn(docs);
    List<IntentTemplate> result =
        intentTemplateDao.fetchIntentTemplates(List.of("intent1", "intent2"));
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getIntentId()).isEqualTo("intent1");
    assertThat(result.get(1).getIntentId()).isEqualTo("intent2");
  }

  @Test
  void testCopyIntentTemplatesToTargetEnvironment() {
    List<IntentTemplateDoc> sourceDocs = new ArrayList<>();
    IntentTemplateDoc doc1 = IntentTemplateDoc.builder().build();
    doc1.setIntentId("intent1");
    sourceDocs.add(doc1);
    when(intentTemplateRepo.findIntentTemplateByTenantIdAndEnvironment(
        "3e37631c-78ac-4137-8415-72dcd90b19fd", "dev")).thenReturn(sourceDocs);
    when(intentTemplateRepo.saveAll(anyList())).thenReturn(sourceDocs);
    intentTemplateDao.copyIntentTemplatesToTargetEnvironment("3e37631c-78ac-4137-8415-72dcd90b19fd",
        "sourceEnv", "targetEnv", "userId");
    verify(intentTemplateRepo).deleteIntentTemplateByTenantIdAndEnvironment(
        "3e37631c-78ac-4137-8415-72dcd90b19fd", "targetEnv");
    verify(intentTemplateRepo).saveAll(anyList());
  }

  @Test
  void testFetchTemplates() {
    List<String> intentTemplates = List.of("intentTemplate1", "intentTemplate2");
    List<String> headerTemplates = List.of("headerTemplate1", "headerTemplate2");
    when(intentTemplateRepo.findAllTemplateFields("3e37631c-78ac-4137-8415-72dcd90b19fd",
        "dev")).thenReturn(intentTemplates);
    when(headerTemplateRepo.findAllTemplateFields("3e37631c-78ac-4137-8415-72dcd90b19fd",
        "dev")).thenReturn(headerTemplates);
    List<String> result = intentTemplateDao.fetchTemplates();
    assertThat(result).containsExactlyInAnyOrder("intentTemplate1", "intentTemplate2",
        "headerTemplate1", "headerTemplate2");
  }
  @Test
  void testFetchIntentTemplatesByExpId() {
    String expId = "exp123";
    List<IntentTemplateDoc> docs = new ArrayList<>();
    IntentTemplateDoc doc = IntentTemplateDoc.builder().build();
    doc.setIntentId("intent1");
    docs.add(doc);

    when(intentTemplateRepo.findIntentTemplateByExperienceIdAndTenantIdAndEnvironmentAndDeletedFalse(
        eq(expId), eq("3e37631c-78ac-4137-8415-72dcd90b19fd"), eq("dev")))
        .thenReturn(docs);

    List<IntentTemplate> result = intentTemplateDao.fetchIntentTemplatesByExpId(expId);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getIntentId()).isEqualTo("intent1");
  }

  @Test
  void testDeleteIntentTemplatesByExpId() {
    String expId = "exp123";
    doNothing().when(intentTemplateRepo)
        .deleteIntentTemplateByExperienceIdAndTenantIdAndEnvironment(
            eq(expId), eq("3e37631c-78ac-4137-8415-72dcd90b19fd"), eq("dev"));

    intentTemplateDao.deleteIntentTemplatesByExpId(expId);

    verify(intentTemplateRepo, times(1))
        .deleteIntentTemplateByExperienceIdAndTenantIdAndEnvironment(
            eq(expId), eq("3e37631c-78ac-4137-8415-72dcd90b19fd"), eq("dev"));
  }
  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
