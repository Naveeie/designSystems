package com.uniphore.uassist.summary.dao;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryUpdateEventLog;
import com.uniphore.uassist.summary.model.SummaryEventAggrByEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SummaryRuntimeDaoTest {

  @Mock
  private SummaryRepository summaryRepository;

  @Mock
  private SummaryUpdateLogDao summaryUpdateLogDao;

  @InjectMocks
  private SummaryRuntimeDao summaryRuntimeDao;

  @Mock
  private MongoTemplate mongoTemplate;

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
  void tearDown() throws Exception {
    reset(summaryRepository, summaryUpdateLogDao);
  }

  @Test
  void testSaveSummaryDoc() {
    SummaryDoc summaryDoc = new SummaryDoc();
    summaryDoc.setTenantId("3e37631c-78ac-4137-8415-72dcd90b19fd");
    summaryDoc.setEnvironment("dev");
    summaryDoc.setConversationId("conv1");
    Query q = new Query(
        Criteria.where("conversationId").is(summaryDoc.getConversationId()).and("tenantId")
            .is(summaryDoc.getTenantId()));
    summaryRuntimeDao.saveSummaryDoc(summaryDoc);
    verify(mongoTemplate).findAndReplace(eq(q), eq(summaryDoc), any(FindAndReplaceOptions.class));
  }

  @Test
  void testSaveSummaryEventDoc() {
    SummaryUpdateEventLog summaryUpdateEventLog = new SummaryUpdateEventLog();
    summaryUpdateEventLog.setTenantId("3e37631c-78ac-4137-8415-72dcd90b19fd");
    summaryUpdateEventLog.setEnvironment("dev");
    summaryUpdateEventLog.setConversationId("conv1");
    summaryRuntimeDao.saveSummaryEventDoc(summaryUpdateEventLog);
    verify(summaryUpdateLogDao).save(summaryUpdateEventLog);
  }

  @Test
  void testGetSummaryDoc() {
    SummaryDoc summaryDoc = new SummaryDoc();
    summaryDoc.setConversationId("conv1");
    when(summaryRepository.findById("conv1")).thenReturn(Optional.of(summaryDoc));
    Optional<SummaryDoc> result = summaryRuntimeDao.getSummaryDoc("conv1");
    assertThat(result).isPresent();
    assertThat(result.get().getConversationId()).isEqualTo("conv1");
  }

  @Test
  void testGetSummaryEventLog() {
    List<SummaryUpdateEventLog> logs = new ArrayList<>();
    SummaryUpdateEventLog log1 = new SummaryUpdateEventLog();
    log1.setConversationId("conv1");
    logs.add(log1);
    when(summaryUpdateLogDao.findEventLogByConversationId("conv1")).thenReturn(logs);
    List<SummaryUpdateEventLog> result = summaryRuntimeDao.getSummaryEventLog("conv1");
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getConversationId()).isEqualTo("conv1");
  }

  @Test
  void testGetSummaryAggrByEventForConvId() {
    List<SummaryEventAggrByEvent> aggrByEvents = new ArrayList<>();
    SummaryEventAggrByEvent eventAggr = new SummaryEventAggrByEvent();
    aggrByEvents.add(eventAggr);
    when(summaryUpdateLogDao.getAggrEventDataForConversationId("conv1")).thenReturn(aggrByEvents);
    List<SummaryEventAggrByEvent> result =
        summaryRuntimeDao.getSummaryAggrByEventForConvId("conv1");
    assertThat(result).hasSize(1);
  }

  @Test
  void testGetSummaryDocsByExtConversationIds() {
    List<String> extConversationIds = List.of("extConv1", "extConv2");
    List<SummaryDoc> summaryDocs = new ArrayList<>();
    SummaryDoc doc1 = new SummaryDoc();
    doc1.setExtConversationId("extConv1");
    SummaryDoc doc2 = new SummaryDoc();
    doc2.setExtConversationId("extConv2");
    summaryDocs.add(doc1);
    summaryDocs.add(doc2);
    when(summaryRepository.findByExtConversationIdIn(eq(extConversationIds),
        eq("3e37631c-78ac-4137-8415-72dcd90b19fd"), eq("dev"))).thenReturn(summaryDocs);
    List<SummaryDoc> result =
        summaryRuntimeDao.getSummaryDocsByExtConversationIds(extConversationIds);
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getExtConversationId()).isEqualTo("extConv1");
    assertThat(result.get(1).getExtConversationId()).isEqualTo("extConv2");
  }

  @Test
  void testGetSummaryByExtId() {
    SummaryDoc summaryDoc = new SummaryDoc();
    summaryDoc.setExtConversationId("extConv1");
    when(summaryRepository.findByExtConversationIdAndTenantIdAndEnvironment("extConv1",
        "3e37631c-78ac-4137-8415-72dcd90b19fd", "dev")).thenReturn(summaryDoc);
    SummaryDoc result = summaryRuntimeDao.getSummaryByExtId("extConv1");
    assertThat(result).isNotNull();
    assertThat(result.getExtConversationId()).isEqualTo("extConv1");
  }

  @Test
  void testDeleteSummaryDoc(){
    String conversationId = "conv1";
    SummaryDoc summaryDoc=new SummaryDoc();
    summaryDoc.setConversationId(conversationId);

    summaryRuntimeDao.deleteSummaryDoc(conversationId);

    Query expectedQuery = new Query(Criteria.where("conversationId").is(conversationId)
        .and("tenantId").is("3e37631c-78ac-4137-8415-72dcd90b19fd")
        .and("environment").is("dev"));

    verify(mongoTemplate).remove(eq(expectedQuery), eq(SummaryDoc.class));

  }

  @Test
  void testGetSummaryDocs() {
    List<String> conversationIds = List.of("conv1", "conv2", "conv3");
    List<SummaryDoc> summaryDocs = new ArrayList<>();
    SummaryDoc doc1 = new SummaryDoc();
    doc1.setConversationId("conv1");
    SummaryDoc doc2 = new SummaryDoc();
    doc2.setConversationId("conv2");
    SummaryDoc doc3 = new SummaryDoc();
    doc3.setConversationId("conv3");
    summaryDocs.add(doc1);
    summaryDocs.add(doc2);
    summaryDocs.add(doc3);
    when(summaryRepository.findAllById(conversationIds)).thenReturn(summaryDocs);
    List<SummaryDoc> result = summaryRuntimeDao.getSummaryDocs(conversationIds);
    assertThat(result).hasSize(3);
    assertThat(result.get(0).getConversationId()).isEqualTo("conv1");
    assertThat(result.get(1).getConversationId()).isEqualTo("conv2");
    assertThat(result.get(2).getConversationId()).isEqualTo("conv3");
  }

  @Test
  void testGetSummaryDocsEmpty() {
    List<String> conversationIds = new ArrayList<>();
    List<SummaryDoc> summaryDocs = new ArrayList<>();
    when(summaryRepository.findAllById(conversationIds)).thenReturn(summaryDocs);
    List<SummaryDoc> result = summaryRuntimeDao.getSummaryDocs(conversationIds);
    assertThat(result).isEmpty();
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
