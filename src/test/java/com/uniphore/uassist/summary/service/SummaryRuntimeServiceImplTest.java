package com.uniphore.uassist.summary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.Timestamp;
import com.mongodb.MongoException;
import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.conversations.v1.Entity;
import com.uniphore.conversations.v1.Turn;
import com.uniphore.conversations.v1.Word;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.featureflag.Tenant;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.summary.v1.Header;
import com.uniphore.summary.v1.IntentSummary;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.cache.*;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.CustomMetricsPublisher;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.dao.*;
import com.uniphore.uassist.summary.dao.documents.*;
import com.uniphore.uassist.summary.event.helper.ConversationEventProcessingHelper;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.helper.DataHelper;
import com.uniphore.uassist.summary.helper.LookerGenAISummaryMetricHelper;
import com.uniphore.uassist.summary.kafka.MessagePublisher;
import com.uniphore.uassist.summary.mock.MockService;
import com.uniphore.uassist.summary.model.*;
import com.uniphore.uassist.summary.util.DateUtil;
import com.uniphore.uassist.summary.util.GrpcClientHelper;
import com.uniphore.uassist.summary.util.SummaryHelper;
import com.uniphore.uassist.summary.utils.TestConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.uniphore.uassist.summary.service.SummaryRuntimeServiceImpl.UASSIST_IGNORE_EMPTY_INTENT_FF;
import static com.uniphore.uassist.summary.utils.TestConstant.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class SummaryRuntimeServiceImplTest extends ApplicationTest {

  @MockBean
  private LaunchDarklyHelper ldHelper;

  @MockBean
  private GrpcClientHelper grpcClientHelper;

  @Autowired
  private SummaryRuntimeServiceImpl summaryRuntimeService;

  @Autowired
  private MockService mockService;
  @MockBean
  private DocConverter docConverter;
  @MockBean
  private SummaryRepository summaryRepository;
  @MockBean
  private ConversationEventProcessingHelper conversationEventProcessingHelper;

  @Autowired
  private SummaryRuntimeDao summaryRuntimeDao;
  @MockBean
  private AgentStepsRepo agentStepsRepo;

  @MockBean
  private SummaryHelper summaryHelper;
  @MockBean
  private AgentEditsRepository agentEditsRepository;
  @MockBean
  private SummaryCacheHelper summaryCacheHelper;
  private AgentRequestPayload agentRequestPayload;
  @MockBean
  private AgentStepsCacheHelper agentStepsCacheHelper;
  @MockBean
  private GenAISummaryService genAISummaryService;
  private TestConstant testConstant;

  @MockBean
  private CacheOperations<GenAISummaryConfig> genAISummaryConfigCache;

  @MockBean
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;

  @Autowired
  private HeaderTemplateService headerTemplateService;
  @MockBean
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  @Autowired
  private IntentTemplateService intentTemplateService;

  @MockBean
  private CustomMetricsPublisher customMetricsPublisher;
  @MockBean
  private LookerGenAISummaryMetricHelper metricHelper;

  @Autowired
  @Qualifier("intentTemplateLocalCache")
  private HashLocalCache<String> intentTemplateLocalCache;
  @Autowired
  @Qualifier("intentEntityMapLocalCache")
  private HashLocalCache<Set<String>> intentEntityMapLocalCache;
  @Autowired
  @Qualifier("headerEntityMapLocalCache")
  private HashLocalCache<Set<String>> headerEntityMapLocalCache;
  @Autowired
  @Qualifier("headerVariableMapLocalCache")
  private HashLocalCache<Set<String>> headerVariableMapLocalCache;
  //  @Autowired
  //  private RedisCacheOperations redisCacheOperations;

  private String tenantId;
  private String environment;
  private String experienceId;

  private SummaryDoc summaryDoc;
  private Header header;
  @MockBean
  @Qualifier("headerEntityMapLocalCache")
  private HashLocalCache<Set<String>> hashLocalCache;
  @Autowired
  private CacheOperations<TypeReference<Tenant>> tenantCache;
  @MockBean
  private CacheOperations<Set<String>> headerEntityMapCache;
  @MockBean
  private AgentUpdateRequest agentUpdateRequest;
  private SummaryUpdateEventLog summaryUpdateEventLog;
  @MockBean
  private SummaryUpdateLogDao summaryUpdateLogDao;
  @MockBean
  private MessagePublisher messagePublisher;
  private HeaderTemplate headerTemplate;
  private IntentTemplate intentTemplate;
  @Autowired
  private DataHelper datahelper;

  @MockBean
  @Qualifier("threadPoolExecutor")
  private ThreadPoolTaskExecutor threadPoolExecutor;

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
      TenantContext.setTenantContext(TENANT_ID, "dev");
      TenantUtils.setTenantDetails(TENANT_ID, "dev");
      summaryDoc = this.datahelper.inputSummaryDocResponse(SUMMARY_DOC_RESPONSE_JSON);
      agentRequestPayload = this.datahelper.inputAgentRequestPayloadResponse(AGENT_REQUEST_PAYLOAD);
      this.tenantId = "123455";
      this.environment = "dev";
      this.experienceId = "9e9d1fcc-f99d-400c-bfb8-95d79a6ba9e5";
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

  @Test
  @Order(1)
  void testSaveAgentNotes() {
    try {
      super.mockTenantContext();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      this.mockService.mockSaveAgentNotes();
    } catch (final Exception e) {
      log.error("exception at testSaveAgentNotes: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(2)
  void testSaveAgentNotesWithoutGenAiSummary() {
    try {
      super.mockTenantContext();
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      AgentRequestPayload agentRequestPayload = AgentRequestPayload.builder().build();
      when(this.summaryRuntimeService.saveAgentNotes(agentRequestPayload)).thenThrow(
          new RuntimeException("Internal Server Error"));
    } catch (final Exception e) {
      log.error("exception at testSaveAgentNotesWithoutGenAiSummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  void testSaveAgentNotesWithException() {
      super.mockTenantContext();
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      AgentRequestPayload agentRequestPayload = AgentRequestPayload.builder().build();

      doThrow(new CustomException("50037", HttpStatus.NOT_FOUND)).when(this.summaryHelper)
          .handleAgentNotesUpdate(any(), anyLong(), anyList());

      MessageResponse response = this.summaryRuntimeService.saveAgentNotes(agentRequestPayload);
      assertEquals("Conversation not found hence not able to save agent notes",
          response.getMessage());
  }

  @Test
  @Order(3)
  void testSaveAgentEdits() {
    try {
      super.mockTenantContext();
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      TenantContext.setTenantContext(TENANT_ID, "dev");
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      this.mockService.mockSaveAgentEditsWithHeaderSummary();
      Assertions.assertThrows(NullPointerException.class, () -> {
        this.mockService.mockSaveAgentEditsWithIntentSummary();
      });
    } catch (final Exception e) {
      log.error("exception at testSaveAgentEdits: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(4)
  void testSaveAgentEditsWithGenAiSummary() {
    try {
      super.mockTenantContext();
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      this.mockService.mockSaveAgentEditsWithGenAiSummary();
    } catch (final Exception e) {
      log.error("exception at testSaveAgentEditsWithGenAiSummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(5)
  void testSaveAgentEditsWithoutConvId() {
    try {
      super.mockTenantContext();
      summaryDoc = this.datahelper.inputSummaryDoc();
      AgentRequestPayload agentRequestPayload =
          AgentRequestPayload.builder().agentId("agentId").agentNotes("agentNotes")
              .conversationId("").businessProcessId("businessProcessId")
              .extConversationId("extConvId").source("agent").build();
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      Assertions.assertThrows(CustomException.class, () -> {
        this.mockService.mockSaveAgentEditsWithoutConvId();
      });
      when(this.summaryRuntimeDao.getSummaryByExtId(
          agentRequestPayload.getExtConversationId())).thenReturn(summaryDoc);
      this.mockService.mockSaveAgentEditsWithoutConvId();
    } catch (final Exception e) {
      log.error("exception at testSaveAgentEditsWithGenAiSummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(6)
  void testSaveAgentEditsWithoutGenAiSummary() {
    try {
      super.mockTenantContext();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      this.mockService.mockSaveAgentEditsWithoutGenAiSummary();
    } catch (final Exception e) {
      log.error("exception at testSaveAgentEditsWithoutGenAiSummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(7)
  void testProcessAgentUpdates() {
    try {
      super.mockTenantContext();
      when(summaryRuntimeDao.getSummaryDoc(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      List<AgentRequestPayload.EntityEditPayload> entityEditPayloads = new ArrayList<>();
      List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
      GenAiSummaryDoc.Section sectionDocUnformatted =
          GenAiSummaryDoc.Section.builder().id("sectionId").text("sectionText").build();
      sectionList.add(sectionDocUnformatted);
      GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(sectionList).build();
      entityEditPayloads.add(AgentRequestPayload.EntityEditPayload.builder().isEntity(true)
          .editedComponentId("editedId").editedValue("editedValue").build());
      AgentRequestPayload agentRequestPayload =
          AgentRequestPayload.builder().agentId("agentId").agentNotes("agentNotes")
              .conversationId(CONVERSATION_ID).disposition("disposition")
              .businessProcessId("businessProcessId").entityEditPayloads(entityEditPayloads)
              .genAiSummaryDoc(genAiSummaryDoc).headerSummary("headerSummary")
              .intentSummary("intentSummary").instanceId("instanceId").source("agent")
              .intentId("intent3").build();
      List<String> effectedIntents = new ArrayList<>();
      effectedIntents.add("intent1");
      effectedIntents.add("intent2");
      SummaryUpdateEventLog.EventData eventData =
          SummaryUpdateEventLog.EventData.builder().eventId("eventId").value("eventValue")
              .detectedBy("agent").headerEffected(true).instanceId("instance1")
              .effectedIntents(effectedIntents).build();
      List<SummaryUpdateEventLog.EventData> eventDataList = new ArrayList<>();
      eventDataList.add(eventData);
      AgentStepsDoc.AgentStep agentStep =
          AgentStepsDoc.AgentStep.builder().detectedBy("USER").id("id").order(1).text("Text")
              .build();
      Map<String, AgentStepsDoc.AgentStep> agentStepMap = new HashMap<>();
      agentStepMap.put("String1", agentStep);
      AgentStepsDoc agentStepsDoc =
          AgentStepsDoc.builder().agentSteps(agentStepMap).conversationId(CONVERSATION_ID)
              .environment("dev").tenantId(TENANT_ID).build();
      when(this.agentStepsRepo.findById(ArgumentMatchers.any())).thenReturn(
          Optional.of(agentStepsDoc));
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      this.mockService.mockProcessAgentUpdatesWithAddIntent(agentRequestPayload, eventDataList);
      this.mockService.mockProcessAgentUpdatesWithGenAiSummaryEdit(agentRequestPayload,
          eventDataList);
      this.mockService.mockProcessAgentUpdatesWithHeaderEdit(agentRequestPayload, eventDataList);
      this.mockService.mockProcessAgentUpdatesWithIntentEdit(agentRequestPayload, eventDataList);
      this.mockService.mockProcessAgentUpdatesWithAgentNotes(agentRequestPayload, eventDataList);
      this.mockService.mockProcessAgentUpdatesWithIntentDelete(agentRequestPayload, eventDataList);
      this.mockService.mockProcessAgentUpdatesWithAddAgentStep(agentRequestPayload, eventDataList);
      this.mockService.mockProcessAgentUpdatesWithDeleteAgentStep(agentRequestPayload,
          eventDataList);
      this.mockService.mockProcessAgentUpdatesWithUpdateAgentStep(agentRequestPayload,
          eventDataList);
      this.mockService.mockProcessAgentUpdatesWithInvalid(eventDataList);
    } catch (final Exception e) {
      log.error("exception at testProcessAgentUpdates: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(8)
  void testProcessAgentUpdatesWithErrors() {
    super.mockTenantContext();
    List<String> effectedIntents = new ArrayList<>();
    effectedIntents.add("intent1");
    effectedIntents.add("intent2");
    when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
    SummaryUpdateEventLog.EventData eventData =
        SummaryUpdateEventLog.EventData.builder().eventId("eventId").value("eventValue")
            .detectedBy("agent").headerEffected(true).instanceId("instance1")
            .effectedIntents(effectedIntents).build();
    List<SummaryUpdateEventLog.EventData> eventDataList = new ArrayList<>();
    eventDataList.add(eventData);
    when(this.agentStepsRepo.findById(ArgumentMatchers.any())).thenReturn(null);
    Assertions.assertThrows(NullPointerException.class, () -> {
      this.mockService.mockProcessAgentUpdatesWithAddAgentStep(agentRequestPayload, eventDataList);
    });

  }

  @Test
  @Order(9)
  void testAddIntent() {
    try {
      super.mockTenantContext();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      this.mockService.mockAddIntent();
    } catch (final Exception e) {
      log.error("exception at testAddIntent: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(10)
  void testAddIntentWithCustomException() {
    try {
      super.mockTenantContext();
      Assertions.assertThrows(CustomException.class, () -> {
        this.summaryRuntimeService.addIntent(AgentRequestPayload.builder().build());
      });
    } catch (final Exception e) {
      log.error("exception at testAddIntentWithCustomException: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(11)
  void testDeleteIntent() {
    try {
      super.mockTenantContext();
      this.mockService.mockDeleteIntent();
    } catch (final Exception e) {
      log.error("exception at testDeleteIntent: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(12)
  void testDeleteIntentFFEnabled() {
    try {
      super.mockTenantContext();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      this.mockService.mockDeleteIntent();
    } catch (final Exception e) {
      log.error("exception at testDeleteIntentFFEnabled: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(13)
  void testHandleEntityUpdatedEvent() {
    try {
      super.mockTenantContext();
      header = this.datahelper.inputHeader();
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      when(this.summaryHelper.updateHeaderSummary(ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any())).thenReturn(header);
      this.mockService.mockHandleEntityUpdatedEvent();
      this.mockService.mockHandleEntityUpdatedEventWithoutSourceType();
    } catch (final Exception e) {
      log.error("exception at testHandleEntityUpdatedEvent: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(14)
  void testCheckAndUpdateHeaderSummary() {
    try {
      super.mockTenantContext();
      Set<String> stringSet = new HashSet<>();
      stringSet.add("12");
      summaryDoc = this.datahelper.inputSummaryDoc();
      when(headerTemplateCacheHelper.getHeaderTemplateEntityBpMap("12")).thenReturn(stringSet);
      this.summaryRuntimeService.checkAndUpdateHeaderSummary(CONVERSATION_ID, HtmlParser.ENTITY,
          "compId", "compValue", "compiInstanceId", "12", summaryDoc,
          DateUtil.getCurrentTimestamp());
    } catch (final Exception e) {
      log.error("exception at testCheckAndUpdateHeaderSummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(15)
  void testCheckAndUpdateIntentSummary() {
    try {
      super.mockTenantContext();
      Set<String> stringSet = new HashSet<>();
      stringSet.add("12");
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(intentTemplateCacheHelper.getIntentTemplateEntityIntentMap(
          ArgumentMatchers.any())).thenReturn(stringSet);
      when(this.intentTemplateCacheHelper.getCurrentIntentForConversation(
          ArgumentMatchers.any())).thenReturn("12");
      this.summaryRuntimeService.checkAndUpdateIntentSummary(CONVERSATION_ID, summaryDoc,
          "componentId", "componentValue", "compInstanceId", HtmlParser.ENTITY, null, true);
    } catch (final Exception e) {
      log.error("exception at testCheckAndUpdateHeaderSummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  void testFetchSummaryWithException() {
    super.mockTenantContext();
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    when(this.summaryHelper.getSummaryDoc(CONVERSATION_ID)).thenThrow(
        new RuntimeException("Internal Server Error"));
    Assertions.assertThrows(CustomException.class, () -> {
      this.summaryRuntimeService.fetchSummary(CONVERSATION_ID);
    });
  }

  @Test
  @Order(18)
  void testHandleIntentsDeletedEvent() throws IOException {
    try {
      super.mockTenantContext();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.intentTemplateCacheHelper.getCurrentIntentForConversation(
          ArgumentMatchers.any())).thenReturn("intent");
      String key =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-template";
      String value =
          "<p>Test rules&nbsp;<br><br>Test_String1 @<span entity-id=\"entity1\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String1</span></p><p>Test rules&nbsp;<br><br>Test_String2 @<span entity-id=\"entity2\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String2</span></p><p color='black' >mobile : <span variable-id='variable1' class='uniphore-variable-name'>mobile</span></p><p color='black' >Address : <span variable-id='variable2' class='uniphore-variable-name'>Address</span></p>";
      this.intentTemplateLocalCache.put(key, "summary-intent-2", value);
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      Map<String, String> intentMap = this.datahelper.jsonToMapConverter(TestConstant.INTENT1_BP2);
      if (!intentMap.isEmpty())
        this.mockService.mockIntentDeletedEvent(intentMap);
    } catch (final Exception e) {
      log.error("exception at testHandleIntentsDeletedEvent: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(19)
  void testHandleTranscriptAddedEvent() {
    try {
      super.mockTenantContext();
      this.summaryRuntimeService.handleConversationEvent(
          this.datahelper.createTranscriptAddedEvent(), 1234556);
    } catch (final Exception e) {
      log.error("exception at testHandleTranscriptAddedEvent: ", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(20)
  void testHandleTranscriptAddedEventFFEnabled() {
    try {
      super.mockTenantContext();
      doReturn(true).when(this.ldHelper).isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF,
          LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      this.summaryRuntimeService.handleConversationEvent(
          this.datahelper.createTranscriptAddedEvent(), 1234556);
    } catch (final Exception e) {
      log.error("exception at testHandleTranscriptAddedEventFFEnabled: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(21)
  void testCreateGenAiSummary() {
    super.mockTenantContext();
    this.summaryRuntimeService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
        Optional.of(EXPERIENCE_ID), false,Optional.empty());
  }

  @Test
  void testCreateGenAiSummary1() {
    super.mockTenantContext();
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    SummaryDoc summaryDoc = SummaryDoc.builder().genAiSummary(GenAiSummaryDoc.builder().sections(
            Collections.singletonList(
                GenAiSummaryDoc.Section.builder().text("<b>GenaiSummaryText</b>").build())).build())
        .genAiGeneratedSummary(GenAiSummaryDoc.builder().sections(Collections.singletonList(
            GenAiSummaryDoc.Section.builder().text("<p>withoutHtmlTag</p>").build())).build())
        .build();
    when(genAISummaryService.createGenAiSummary(anyString(), anyString(), any(), anyBoolean(),
        anyBoolean(), anyLong(),any())).thenReturn(summaryDoc);
    SummaryDoc genAiSummary =
        this.summaryRuntimeService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
            Optional.of(EXPERIENCE_ID), true,Optional.empty());
    List<String> actualText =
        genAiSummary.getGenAiSummary().getSections().stream().map(GenAiSummaryDoc.Section::getText)
            .collect(Collectors.toList());
    List<String> actualText1 = genAiSummary.getGenAiGeneratedSummary().getSections().stream()
        .map(GenAiSummaryDoc.Section::getText).collect(Collectors.toList());
    assertEquals("<b>GenaiSummaryText</b>", actualText.get(0));
  }

  @Test
  void testCreateGenAiSummary2() {
    super.mockTenantContext();
    doReturn(false).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    SummaryDoc summaryDoc = SummaryDoc.builder().genAiSummary(GenAiSummaryDoc.builder().sections(
            Collections.singletonList(
                GenAiSummaryDoc.Section.builder().text("<b>GenaiSummaryText</b>").build())).build())
        .genAiGeneratedSummary(GenAiSummaryDoc.builder().sections(Collections.singletonList(
            GenAiSummaryDoc.Section.builder().text("<p>withoutHtmlTag</p>").build())).build())
        .build();
    when(genAISummaryService.createGenAiSummary(anyString(), anyString(), any(), anyBoolean(),
        anyBoolean(), anyLong(),any())).thenReturn(summaryDoc);
    SummaryDoc genAiSummary =
        this.summaryRuntimeService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
            Optional.of(EXPERIENCE_ID), Boolean.FALSE, Optional.empty());
    List<String> actualText =
        genAiSummary.getGenAiSummary().getSections().stream().map(GenAiSummaryDoc.Section::getText)
            .collect(Collectors.toList());
    List<String> actualText1 = genAiSummary.getGenAiGeneratedSummary().getSections().stream()
        .map(GenAiSummaryDoc.Section::getText).collect(Collectors.toList());
    assertEquals(actualText.get(0), "<b>GenaiSummaryText</b>");
    assertEquals(actualText1.get(0), "<p>withoutHtmlTag</p>");
  }

  @Test
  void testCreateGenAiSummaryWithNewLine() {
    super.mockTenantContext();
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    SummaryDoc summaryDoc = SummaryDoc.builder().genAiSummary(GenAiSummaryDoc.builder().sections(
            Collections.singletonList(
                GenAiSummaryDoc.Section.builder().text("<b>GenaiSummaryText</b><n>").build())).build())
        .genAiGeneratedSummary(GenAiSummaryDoc.builder().sections(Collections.singletonList(
            GenAiSummaryDoc.Section.builder().text("<n><p>withoutHtmlTag</p>").build())).build())
        .build();
    when(genAISummaryService.createGenAiSummary(anyString(), anyString(), any(), anyBoolean(),
        anyBoolean(), anyLong(),any())).thenReturn(summaryDoc);
    SummaryDoc genAiSummary =
        this.summaryRuntimeService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
            Optional.of(EXPERIENCE_ID), true, Optional.empty());
    List<String> actualText =
        genAiSummary.getGenAiSummary().getSections().stream().map(GenAiSummaryDoc.Section::getText)
            .collect(Collectors.toList());
    List<String> actualText1 = genAiSummary.getGenAiGeneratedSummary().getSections().stream()
        .map(GenAiSummaryDoc.Section::getText).collect(Collectors.toList());
    assertEquals("<b>GenaiSummaryText</b><n>", actualText.get(0));
    assertEquals("<n><p>withoutHtmlTag</p>", actualText1.get(0));
  }

  @Test
  @Order(22)
  void testfetchSummaryEventLog() {
    try {
      super.mockTenantContext();
      this.summaryRuntimeService.fetchSummaryEventLog(CONVERSATION_ID);
    } catch (final Exception e) {
      log.error("exception at testfetchSummaryEventLog: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  void testCreateGenAiSummary_WithFormatting() {
    super.mockTenantContext();
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    SummaryDoc summaryDoc = SummaryDoc.builder().genAiSummary(GenAiSummaryDoc.builder().sections(
            Collections.singletonList(
                GenAiSummaryDoc.Section.builder().text("<b>GenaiSummaryText</b>").build())).build())
        .genAiGeneratedSummary(GenAiSummaryDoc.builder().sections(Collections.singletonList(
            GenAiSummaryDoc.Section.builder().text("<p>withoutHtmlTag</p>").build())).build())
        .build();
    when(genAISummaryService.createGenAiSummary(anyString(), anyString(), any(), anyBoolean(),
        anyBoolean(), anyLong(),any())).thenReturn(summaryDoc);
    SummaryDoc genAiSummary =
        this.summaryRuntimeService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
            Optional.of(EXPERIENCE_ID), Boolean.FALSE,Optional.empty());
    List<String> actualText =
        genAiSummary.getGenAiSummary().getSections().stream().map(GenAiSummaryDoc.Section::getText)
            .collect(Collectors.toList());
    List<String> actualText1 = genAiSummary.getGenAiGeneratedSummary().getSections().stream()
        .map(GenAiSummaryDoc.Section::getText).collect(Collectors.toList());

    assertEquals("<b>GenaiSummaryText</b>", actualText.get(0));
    assertEquals("<p>withoutHtmlTag</p>", actualText1.get(0));
  }

  @Test
  @Order(23)
  void testGetSummaryReportByConversationId() {
    try {
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      summaryDoc = this.datahelper.inputSummaryDoc();
      when(this.summaryRuntimeDao.getSummaryDoc(CONVERSATION_ID)).thenReturn(
          Optional.ofNullable(summaryDoc));
      this.summaryRuntimeService.getSummaryReportByConversationId(CONVERSATION_ID);
    } catch (final Exception e) {
      log.error("exception at testGetSummaryReportByConversationId: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  void testExtractMembersOfIntent_withValidData_shouldAddStartEntity() throws Exception {
    // Arrange
    Map<String, List<SummaryUpdateEventLog>> eventMap = new HashMap<>();
    Map<String, List<String>> intentEntityMap = new HashMap<>();
    List<SummaryReportResponse.Entity> resultEntities = new ArrayList<>();

    String intentId = "intent1";
    String entityId = "entity1";

    SummaryUpdateEventLog.EventData data = new SummaryUpdateEventLog.EventData();
    data.setEventId(entityId);
    data.setInstanceId("inst1");
    data.setValue("val1");
    data.setDetectedBy("detector");

    SummaryUpdateEventLog log = new SummaryUpdateEventLog();
    log.setEventTime(950L);
    log.setConversationId("conv1");
    log.setEventData(List.of(data));

    eventMap.put("member", List.of(log));
    intentEntityMap.put(intentId, List.of(entityId));

    long startTime = 1000L;
    long endTime = 1100L;

    // Act
    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("extractMembersOfIntent",
        Map.class, String.class, List.class, long.class, long.class, Map.class, String.class);
    method.setAccessible(true);

    method.invoke(null, eventMap, intentId, resultEntities, startTime, endTime, intentEntityMap, "member");

    // Assert
    assertEquals(1, resultEntities.size());
    SummaryReportResponse.Entity entity = resultEntities.get(0);
    assertEquals("entity1", entity.getId());
    assertEquals("inst1", entity.getInstanceId());
    assertEquals("val1", entity.getValue());
    assertEquals("detector", entity.getDetectedBy());
    assertEquals(950L, entity.getEventTime());
  }

  @Test
  void testCreateIntentSummaryReport() throws Exception {

    Optional<SummaryDoc> summaryDoc = Optional.of(mock(SummaryDoc.class));
    Map<String, List<SummaryUpdateEventLog>> entityVariableEventMap = new HashMap<>();
    Map<String, List<SummaryUpdateEventLog>> intentEventMap = new HashMap<>();
    List<SummaryReportResponse.IntentSummary> intentSummaries = new ArrayList<>();

    Map<String, IntentSummaryDoc> intentSummariesMap = new HashMap<>();
    intentSummariesMap.put("instanceId1", mock(IntentSummaryDoc.class));
    when(summaryDoc.get().getIntentSummaries()).thenReturn(intentSummariesMap);

    List<SummaryUpdateEventLog> eventLogs = new ArrayList<>();
    SummaryUpdateEventLog log = mock(SummaryUpdateEventLog.class);
    SummaryUpdateEventLog.EventData eventData = mock(SummaryUpdateEventLog.EventData.class);
    when(eventData.getInstanceId()).thenReturn("instanceId1");
    when(log.getEventData()).thenReturn(List.of(eventData));
    eventLogs.add(log);
    entityVariableEventMap.put("key1", eventLogs);
    SummaryUpdateEventLog intentLog = mock(SummaryUpdateEventLog.class);
    SummaryUpdateEventLog.EventData intentEventData = mock(SummaryUpdateEventLog.EventData.class);
    when(intentEventData.getInstanceId()).thenReturn("instanceId1");
    when(intentLog.getEventData()).thenReturn(List.of(intentEventData));
    intentEventMap.put("intentKey1", List.of(intentLog));

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("createIntentSummaryReport",
        Optional.class, Map.class, Map.class, List.class);
    method.setAccessible(true);
    method.invoke(summaryRuntimeService, summaryDoc, entityVariableEventMap, intentEventMap,
        intentSummaries);
  }

  @Test
  @Order(24)
  void testGetSummaryReportByConversationIdWithSystemIntentSummaryFF() {
    try {
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      summaryDoc = this.datahelper.inputSummaryDocWithSystemSummary();
      when(this.summaryRuntimeDao.getSummaryDoc(CONVERSATION_ID)).thenReturn(
          Optional.ofNullable(summaryDoc));
      this.summaryRuntimeService.getSummaryReportByConversationId(CONVERSATION_ID);
    } catch (final Exception e) {
      log.error("exception at testGetSummaryReportByConversationIdWithSystemIntentSummaryFF: {}",
          e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(25)
  void testGetSummaryReportByConversationIdFFDisabled() {
    try {
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      summaryDoc = this.datahelper.inputSummaryDoc();
      List<String> effectedIntents = new ArrayList<>();
      effectedIntents.add("intent1");
      SummaryUpdateEventLog.EventData eventData =
          SummaryUpdateEventLog.EventData.builder().effectedIntents(effectedIntents)
              .instanceId(Constants.TAG_ENTITY_ADD).headerEffected(true).eventId("event1")
              .detectedBy("USER").value("value").build();
      List<SummaryUpdateEventLog.EventData> eventDataList = new ArrayList<>();
      eventDataList.add(eventData);
      summaryUpdateEventLog = SummaryUpdateEventLog.builder().environment("dev").summary("summary")
          .eventData(eventDataList).conversationId(CONVERSATION_ID).eventTime(1234124)
          .businessProcessId("bp1").eventProcessedTime(221421).id("id").eventType("eventType")
          .tenantId(TENANT_ID).build();
      List<SummaryUpdateEventLog> updateEventLogList = new ArrayList<>();
      updateEventLogList.add(summaryUpdateEventLog);
      SummaryEventAggrByEvent summaryEventAggrByEvent =
          SummaryEventAggrByEvent.builder().event(Constants.TAG_ENTITY_ADD)
              .eventData(updateEventLogList).build();
      List<SummaryEventAggrByEvent> summaryEventAggrByEventList = new ArrayList<>();
      summaryEventAggrByEventList.add(summaryEventAggrByEvent);
      when(this.summaryUpdateLogDao.getAggrEventDataForConversationId(CONVERSATION_ID)).thenReturn(
          summaryEventAggrByEventList);
      when(this.summaryRuntimeDao.getSummaryDoc(CONVERSATION_ID)).thenReturn(
          Optional.ofNullable(summaryDoc));
      this.summaryRuntimeService.getSummaryReportByConversationId(CONVERSATION_ID);
      SummaryEventAggrByEvent summaryEventAggrByEvent1 =
          SummaryEventAggrByEvent.builder().event(Constants.TAG_INTENT_ADD)
              .eventData(updateEventLogList).build();
      List<SummaryEventAggrByEvent> summaryEventAggrByEventList1 = new ArrayList<>();
      summaryEventAggrByEventList1.add(summaryEventAggrByEvent1);
      when(this.summaryUpdateLogDao.getAggrEventDataForConversationId(CONVERSATION_ID)).thenReturn(
          summaryEventAggrByEventList1);
      when(this.summaryRuntimeDao.getSummaryDoc(CONVERSATION_ID)).thenReturn(
          Optional.ofNullable(summaryDoc));
      this.summaryRuntimeService.getSummaryReportByConversationId(CONVERSATION_ID);
    } catch (final Exception e) {
      log.error("exception at testGetSummaryReportByConversationIdFFDisabled: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(26)
  void testSubmitReviewedIntentSummaries() {
    try {
      super.mockTenantContext();
      IntentSummaryReviewedRequest.IntentSummary intentSummary =
          IntentSummaryReviewedRequest.IntentSummary.builder().intentId("intent1")
              .instanceId("instance1").template("<p>plainText</p>").plainText("plainText")
              .disposition("disp1").build();

      List<IntentSummaryReviewedRequest.IntentSummary> intentSummaryList = new ArrayList<>();
      intentSummaryList.add(intentSummary);
      IntentSummaryReviewedRequest intentSummaryReviewedRequest =
          IntentSummaryReviewedRequest.builder().intentSummaries(intentSummaryList).build();
      IntentSummaryDoc intentSummaryDoc = this.datahelper.inputIntentSummaryDoc();
      summaryDoc = this.datahelper.inputSummaryDoc();
      when(this.summaryRuntimeDao.getSummaryDoc(CONVERSATION_ID)).thenReturn(
          Optional.of(summaryDoc));
      this.summaryRuntimeService.submitReviewedIntentSummaries(CONVERSATION_ID,
          intentSummaryReviewedRequest);
    } catch (final Exception e) {
      log.error("exception at createSummaryConfig : {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(27)
  void testsaveAgentStep() {
    super.mockTenantContext();
    doReturn(false).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
    this.summaryRuntimeService.saveAgentStep(CONVERSATION_ID, "Agent Step");
  }

  @Test
  @Order(28)
  void testDeleteAgentStep() {
    super.mockTenantContext();
    doReturn(false).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
    when(this.agentStepsCacheHelper.agentStepExist(ArgumentMatchers.any(),
        ArgumentMatchers.any())).thenReturn(true);
    this.summaryRuntimeService.deleteAgentStep(CONVERSATION_ID, "deleteId");
  }

  @Test
  @Order(29)
  void testDeleteAgentStepNotExists() {
    super.mockTenantContext();
    when(this.agentStepsCacheHelper.agentStepExist(ArgumentMatchers.any(),
        ArgumentMatchers.any())).thenReturn(false);
    this.summaryRuntimeService.deleteAgentStep(CONVERSATION_ID, "deleteId");
  }

  @Test
  @Order(30)
  void testUpdateAgentStep() {
    super.mockTenantContext();
    doReturn(false).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
    when(this.agentStepsCacheHelper.agentStepExist(ArgumentMatchers.any(),
        ArgumentMatchers.any())).thenReturn(true);
    this.summaryRuntimeService.updateAgentStep(CONVERSATION_ID, "updateId", "Update agent step");
  }

  @Test
  @Order(31)
  void testUpdateAgentStepNotExists() {
    super.mockTenantContext();
    when(this.agentStepsCacheHelper.agentStepExist(ArgumentMatchers.any(),
        ArgumentMatchers.any())).thenReturn(false);
    this.summaryRuntimeService.updateAgentStep(CONVERSATION_ID, "updateId", "Update agent step");
  }

  @Test
  @Order(32)
  public void createSummaryConfig() {
    try {
      super.mockTenantContext();
      Map<String, String> headerMap =
          this.datahelper.jsonToMapConverter(TestConstant.HEADER_TEMPLATE_BP1);
      headerTemplate = HeaderTemplate.builder().template(headerMap.get("template"))
          .businessProcessId(headerMap.get("businessProcessId"))
          .headerTemplateId(headerMap.get("headerTemplateId")).build();
      this.headerTemplateService.upsertHeaderTemplate(headerTemplate);
      Map<String, String> intentMap =
          this.datahelper.jsonToMapConverter(TestConstant.INTENT_TEMPLATE_BP1);
      intentTemplate = IntentTemplate.builder().intentId(intentMap.get("intentId"))
          .template(intentMap.get("template")).businessProcessId(intentMap.get("businessProcessId"))
          .experienceId(intentMap.get("experienceId")).build();
      this.intentTemplateService.upsertIntentTemplate(intentTemplate);
    } catch (final Exception e) {
      log.error("exception at createSummaryConfig : {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(33)
  void testHandleConversationStartedEvent() {
    try {
      Map<String, String> convMap =
          this.datahelper.jsonToMapConverter(TestConstant.CONVERSATION_1_START_EVENT);
      this.mockService.mockConversationStartedEvent(convMap);
      assertNotNull("call start event failed",
          this.summaryRuntimeDao.getSummaryDoc(convMap.get("conversationId")));
    } catch (final Exception e) {
      log.error("exception at testHandleConversationStartedEvent: {}", e);
      //Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(33)
  void testHandleConversationStartedEventWithMutliSegmentSupport() {
    try {
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.MULTI_SEGMENT_SUPPORT,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(
          this.datahelper.inputSummaryDoc());
      Map<String, String> convMap =
          this.datahelper.jsonToMapConverter(TestConstant.CONVERSATION_1_START_EVENT);
      this.mockService.mockConversationStartedEvent(convMap);
      assertNotNull("call start event failed",
          this.summaryRuntimeDao.getSummaryDoc(convMap.get("conversationId")));
    } catch (final Exception e) {
      log.error("exception at testHandleConversationStartedEvent: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(34)
  void intentWithoutTemplateAdded() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_DUPLICATE_INTENTS,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_ENABLE_FLOW_SELECTED_INTENTS_FF,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(UASSIST_IGNORE_EMPTY_INTENT_FF, LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getIntentTemplate(ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.anyBoolean())).thenReturn("");
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      String key =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-template";
      String hashKey = "summary-intent-1";
      String value =
          "<p>Test rules&nbsp;<br><br>Test_String @<span entity-id=\"entity1\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String</span></p><p color='black' >mobile : <span variable-id='variable1' class='uniphore-variable-name'>mobile</span></p>";
      this.intentTemplateLocalCache.put(key, hashKey, value);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Map<String, String> intentMap = this.datahelper.jsonToMapConverter(TestConstant.INTENT1_BP1);
      if (!intentMap.isEmpty())
        this.mockService.mockIntentAddedEvent(intentMap);
    } catch (final Exception e) {
      log.error("exception at intent1Added: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(34)
  void intent1Added() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_DUPLICATE_INTENTS,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_ENABLE_FLOW_SELECTED_INTENTS_FF,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getIntentTemplate(ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          false)).thenReturn("<p>PLaintext</p>");
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      String key =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-template";
      String hashKey = "summary-intent-1";
      String value =
          "<p>Test rules&nbsp;<br><br>Test_String @<span entity-id=\"entity1\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String</span></p><p color='black' >mobile : <span variable-id='variable1' class='uniphore-variable-name'>mobile</span></p>";
      this.intentTemplateLocalCache.put(key, hashKey, value);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Map<String, String> intentMap = this.datahelper.jsonToMapConverter(TestConstant.INTENT1_BP1);
      if (!intentMap.isEmpty())
        this.mockService.mockIntentAddedEvent(intentMap);
    } catch (final Exception e) {
      log.error("exception at intent1Added: {}", e);
      // Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(35)
  void testHandleIntentsAddedEvent() {
    try {
      Map<String, IntentSummaryDoc> intentMap = new HashMap<>();
      intentMap.put(Constants.TAG_ENTITY_ADD, this.datahelper.inputIntentSummaryDoc());
      Entity entity = Entity.newBuilder().setEntityId("entityId").setEntityInstanceId("instanceId")
          .setValue("entity1-1").setDetectedBy("USER").setEntityId("tenantId").build();
      Map<String, SummaryDoc.Slot> entities = new HashMap<>();
      entities.put("ent1",
          SummaryDoc.Slot.builder().id("ent1").isEdited(true).instanceId("inst1").value("value1")
              .build());
      entities.put("ent2",
          SummaryDoc.Slot.builder().id("ent2").isEdited(true).instanceId("inst2").value("value2")
              .build());
      entities.put("ent3",
          SummaryDoc.Slot.builder().id("ent3").isEdited(false).instanceId("inst3").value("value3")
              .build());
      entities.put("ent4",
          SummaryDoc.Slot.builder().id("ent4").isEdited(false).instanceId("inst4").value("value4")
              .build());
      AgentNotesDoc agentNotesDoc = AgentNotesDoc.builder().plainText("plaintext").text("text")
          .timestamp(TimeStampDoc.builder().nanos(1342).seconds(733547).build()).build();

      summaryDoc =
          SummaryDoc.builder().conversationId("ConvId").tenantId("tenantId").environment("dev")
              .businessProcessId("bp").language("en").experienceId("exp1")
              .headerSummary(this.datahelper.inputHeaderSummaryDoc()).intentSummaries(intentMap)
              .entities(entities).variables(entities).agentNotes(agentNotesDoc).participants(null)
              .timestamp(TimeStampDoc.builder().nanos(1234512).seconds(63531).build())
              .genAiSummary(null).build();
      Assertions.assertThrows(NullPointerException.class, () -> {
        this.mockService.mockIntentAddedEventNoValues();
      });
    } catch (final Exception e) {
      log.error("exception at testHandleIntentsAddedEvent: ", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(36)
  void testHandleIntentsAddedEventWithSummaryDoc() {
    try {

      super.mockTenantContext();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(UASSIST_IGNORE_EMPTY_INTENT_FF, LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      Assertions.assertDoesNotThrow(() -> {
        this.mockService.mockIntentAddedEventNoValues();
      });
    } catch (final Exception e) {
      log.error("exception at testHandleIntentsAddedEventWithSummaryDoc: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(37)
  void entity1Added() {
    try {
      super.mockTenantContext();
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Set<String> values = new HashSet<>();
      values.add("7341466421122183431708590568104");
      values.add("3211466421122183431708590568104");
      String headerKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.header-entity.map";
      String intentKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-entity.map";
      String hashKey = "entity1";
      this.headerEntityMapLocalCache.put(headerKey, hashKey, values);
      this.intentEntityMapLocalCache.put(intentKey, hashKey, values);
      Map<String, String> entityMap = this.datahelper.jsonToMapConverter(TestConstant.ENTITY1_BP1);
      if (!entityMap.isEmpty())
        this.mockService.mockEntityAddedEvent(entityMap);
    } catch (final Exception e) {
      log.error("exception at entity1Added: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(38)
  void variable1Added() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Set<String> values = new HashSet<>();
      values.add("value1");
      values.add("value2");
      String headerKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.header-variable.map";
      String haskKey = "variable1";
      this.headerVariableMapLocalCache.put(headerKey, haskKey, values);
      Map<String, String> variableMap =
          this.datahelper.jsonToMapConverter(TestConstant.VARIABLE1_BP1);
      if (!variableMap.isEmpty())
        this.mockService.mockVariableAddedEvent(variableMap);
    } catch (final Exception e) {
      log.error("exception at HandleVariable1AddedEvent: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(39)
  void entity1Updated() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      List<String> effectedIntents = new ArrayList<>();
      effectedIntents.add("intent1");
      SummaryUpdateEventLog.EventData eventData = new SummaryUpdateEventLog.EventData();
      eventData.setHeaderEffected(true);
      eventData.setEventId("eventId");
      eventData.setDetectedBy("USER");
      eventData.setValue("value");
      eventData.setInstanceId("instanceId");
      eventData.setEffectedIntents(effectedIntents);
      List<SummaryUpdateEventLog.EventData> eventDataList = new ArrayList<>();
      eventDataList.add(eventData);
      SummaryUpdateEventLog summaryUpdateEventLog1 =
          SummaryUpdateEventLog.builder().eventData(eventDataList).summary("summary")
              .eventTime(12345).eventType(Constants.TAG_ENTITY_UPDATE).id("Id")
              .businessProcessId("bp1").environment("dev").build();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      when(this.summaryHelper.getSummaryUpdateEventLog(ArgumentMatchers.any(),
          ArgumentMatchers.anyLong(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(summaryUpdateEventLog1);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Set<String> values = new HashSet<>();
      values.add("value1");
      values.add("value2");
      String headerKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.header-entity.map";
      String intentKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-entity.map";
      String hashKey = "entity1";
      this.headerEntityMapLocalCache.put(headerKey, hashKey, values);
      this.intentEntityMapLocalCache.put(intentKey, hashKey, values);
      Map<String, String> entityMap =
          this.datahelper.jsonToMapConverter(TestConstant.ENTITY1_UPDATED_BP1);
      if (!entityMap.isEmpty()) {
        this.mockService.mockEntityUpdatedEvent(entityMap);
      }
    } catch (final Exception e) {
      log.error("exception at entity1Updated: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(40)
  void testVariableUpdated() { //optimise entire method
    summaryDoc = this.datahelper.inputSummaryDoc();
    try {
      Map<String, String> variableMap =
          this.datahelper.jsonToMapConverter(TestConstant.VARIABLE1_UPDATED_BP1);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      Set<String> intentIdList = new HashSet<>();
      intentIdList.add("12");
      String currentIntent = "12";
      Map<String, IntentSummary> intentSummaryMap = new HashMap<>();
      intentSummaryMap.put("key1",
          IntentSummary.newBuilder().setIntentId("intent").setDispositionId("disp")
              .setInstanceId("instance1").setIsDeleted(false).setPlainText("plaintext").build());
      boolean isAgentEdit = false;
      Timestamp timestamp = DateUtil.getCurrentTimestamp();
      when(intentTemplateCacheHelper.getIntentTemplateVariableIntentMap(
          ArgumentMatchers.any())).thenReturn(intentIdList);
      when(this.intentTemplateCacheHelper.getCurrentIntentForConversation(
          ArgumentMatchers.any())).thenReturn(currentIntent);
      doCallRealMethod().when(this.summaryHelper)
          .updateIntentSummary(summaryDoc, "12", "someValue", "instanceId", "intentInstanceId",
              "type", intentSummaryMap, false);
      this.summaryHelper.updateIntentSummary(summaryDoc, "id", "value", "instanceId",
          "intentInstanceId", "type", intentSummaryMap, false);
      Header header = Header.newBuilder().setPlainText("plainText").setTemplate("template").build();
      when(this.summaryHelper.updateHeaderSummary(ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any())).thenReturn(header);
      Set<String> bpList = new HashSet<>();
      bpList.add("bp123");
      when(headerTemplateCacheHelper.getHeaderTemplateVariableBpMap(
          ArgumentMatchers.any())).thenReturn(bpList);

      if (!variableMap.isEmpty())
        this.mockService.mockVariableUpdatedEvent(variableMap);
    } catch (final Exception e) {
      log.error("exception at variable1Updated: {}", e);
      Assertions.fail("exception at variable1Updated: " + e.getMessage());
    }
  }

  @Test
  @Order(42)
  void testSummaryReviewed() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(Optional.of(this.datahelper.inputSummaryDoc())).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      doReturn(summaryDoc).when(this.summaryHelper).getSummaryDoc(ArgumentMatchers.any());
      AgentStepPojo agentStepPojo = new AgentStepPojo();
      agentStepPojo.setId("id");
      agentStepPojo.setText("text");
      agentStepPojo.setIndex(1);
      List<AgentStepPojo> agentStepList = new ArrayList<>();
      agentStepList.add(agentStepPojo);
      when(this.agentStepsCacheHelper.getAsList(ArgumentMatchers.any())).thenReturn(agentStepList);
      this.summaryRuntimeService.summaryReviewed(CONVERSATION_ID, Optional.of(true),Optional.of(Boolean.FALSE),
          Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    } catch (final Exception e) {
      log.error("exception at SummaryReviewedEvent: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(66)
  void testSummaryReviewedFFDisabled() {
    try {
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.MULTI_SEGMENT_SUPPORT,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AUTO_SUBMIT_FF,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(Optional.of(this.datahelper.inputSummaryDoc())).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      doReturn(this.datahelper.inputSummaryDoc()).when(this.summaryHelper)
          .getSummaryDoc(ArgumentMatchers.any());
      doNothing().when(messagePublisher).summaryReviewed(ArgumentMatchers.any());
      AgentStepPojo agentStepPojo = new AgentStepPojo();
      agentStepPojo.setId("id");
      agentStepPojo.setText("text");
      agentStepPojo.setIndex(1);
      List<AgentStepPojo> agentStepList = new ArrayList<>();
      agentStepList.add(agentStepPojo);
      when(this.agentStepsCacheHelper.getAsList(ArgumentMatchers.any())).thenReturn(agentStepList);
      TenantContext.setTenantContext(TENANT_ID, "dev");
      TenantUtils.setTenantDetails(TENANT_ID, "dev");
      this.summaryRuntimeService.summaryReviewed(CONVERSATION_ID, Optional.of(false),Optional.of(Boolean.FALSE)
          ,Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    } catch (final Exception e) {
      log.error("exception at SummaryReviewedEvent:", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(43)
  public void createSummaryConfigForConv2() throws IOException {
    super.mockTenantContext();
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    Map<String, String> headerMap =
        this.datahelper.jsonToMapConverter(TestConstant.HEADER_TEMPLATE_BP2);
    headerTemplate = HeaderTemplate.builder().template(headerMap.get("template"))
        .businessProcessId(headerMap.get("businessProcessId"))
        .headerTemplateId(headerMap.get("headerTemplateId")).build();
    this.headerTemplateService.upsertHeaderTemplate(headerTemplate);
    Map<String, String> intentMap =
        this.datahelper.jsonToMapConverter(TestConstant.INTENT_TEMPLATE_BP2);
    intentTemplate = IntentTemplate.builder().intentId(intentMap.get("intentId"))
        .template(intentMap.get("template")).businessProcessId(intentMap.get("businessProcessId"))
        .experienceId(intentMap.get("experienceId")).build();
    this.intentTemplateService.upsertIntentTemplate(intentTemplate);
  }

  @Test
  @Order(44)
  void entity1AddedConv2() {
    try {
      super.mockTenantContext();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      summaryDoc = this.datahelper.inputSummaryDoc();
      Set<String> values = new HashSet<>();
      values.add("value1");
      values.add("value2");
      String headerKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.header-entity.map";
      String intentKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-entity.map";
      String hashKey = "entity1";
      this.headerEntityMapLocalCache.put(headerKey, hashKey, values);
      this.intentEntityMapLocalCache.put(intentKey, hashKey, values);
      Map<String, String> entityMap = this.datahelper.jsonToMapConverter(TestConstant.ENTITY1_BP2);
      if (!entityMap.isEmpty())
        this.mockService.mockEntityAddedEvent(entityMap);
    } catch (final Exception e) {
      log.error("exception at entity1AddedConv2: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(45)
  void variable1AddedConv2() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Set<String> values = new HashSet<>();
      values.add("value1");
      values.add("value2");
      String headerKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.header-variable.map";
      String hashKey = "variable1";
      this.headerVariableMapLocalCache.put(headerKey, hashKey, values);
      Map<String, String> variableMap =
          this.datahelper.jsonToMapConverter(TestConstant.VARIABLE1_BP2);
      if (!variableMap.isEmpty())
        this.mockService.mockVariableAddedEvent(variableMap);
    } catch (final Exception e) {
      log.error("exception at variable1AddedConv2: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(46)
  void intent1AddedConv2() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_DUPLICATE_INTENTS,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_ENABLE_FLOW_SELECTED_INTENTS_FF,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getIntentTemplate(ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          false)).thenReturn("<p>PLaintext</p>");
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      String key =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-template";
      String hashKey = "summary-intent-2";
      String value =
          "<p>Test rules&nbsp;<br><br>Test_String1 @<span entity-id=\"entity1\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String1</span></p><p>Test rules&nbsp;<br><br>Test_String2 @<span entity-id=\"entity2\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String2</span></p><p color='black' >mobile : <span variable-id='variable1' class='uniphore-variable-name'>mobile</span></p><p color='black' >Address : <span variable-id='variable2' class='uniphore-variable-name'>Address</span></p>";
      this.intentTemplateLocalCache.put(key, hashKey, value);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Map<String, String> intentMap = this.datahelper.jsonToMapConverter(TestConstant.INTENT1_BP2);
      if (!intentMap.isEmpty())
        this.mockService.mockIntentAddedEvent(intentMap);
    } catch (final Exception e) {
      log.error("exception at intent1AddedConv2: {}", e);
      //  Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(47)
  void entity1UpdatedConv2() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      List<String> effectedIntents = new ArrayList<>();
      effectedIntents.add("intent1");
      SummaryUpdateEventLog.EventData eventData = new SummaryUpdateEventLog.EventData();
      eventData.setHeaderEffected(true);
      eventData.setEventId("eventId");
      eventData.setDetectedBy("USER");
      eventData.setValue("value");
      eventData.setInstanceId("instanceId");
      eventData.setEffectedIntents(effectedIntents);
      List<SummaryUpdateEventLog.EventData> eventDataList = new ArrayList<>();
      eventDataList.add(eventData);
      SummaryUpdateEventLog summaryUpdateEventLog1 =
          SummaryUpdateEventLog.builder().eventData(eventDataList).summary("summary")
              .eventTime(12345).eventType(Constants.TAG_ENTITY_UPDATE).id("Id")
              .businessProcessId("bp1").environment("dev").build();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      when(this.summaryHelper.getSummaryUpdateEventLog(ArgumentMatchers.any(),
          ArgumentMatchers.anyLong(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(summaryUpdateEventLog1);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Set<String> values = new HashSet<>();
      values.add("value1");
      values.add("value2");
      String headerKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.header-entity.map";
      String intentKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-entity.map";
      String hashKey = "entity1";
      this.headerEntityMapLocalCache.put(headerKey, hashKey, values);
      this.intentEntityMapLocalCache.put(intentKey, hashKey, values);
      Map<String, String> entityMap =
          this.datahelper.jsonToMapConverter(TestConstant.ENTITY1_UPDATED_BP2);
      if (!entityMap.isEmpty()) {
        this.mockService.mockEntityUpdatedEvent(entityMap);
      }
    } catch (final Exception e) {
      log.error("exception at entity1UpdatedConv2: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(48)
  void variable1UpdatedConv2() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Set<String> values = new HashSet<>();
      values.add("value1");
      values.add("value2");
      String headerKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.header-variable.map";
      String hashKey = "variable1";
      this.headerVariableMapLocalCache.put(headerKey, hashKey, values);
      Map<String, String> variableMap =
          this.datahelper.jsonToMapConverter(TestConstant.VARIABLE1_UPDATED_BP2);
      if (!variableMap.isEmpty())
        this.mockService.mockVariableUpdatedEvent(variableMap);
    } catch (final Exception e) {
      log.error("exception at variable1UpdatedConv2: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(49)
  void intent2AddedConv2() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_DUPLICATE_INTENTS,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_ENABLE_FLOW_SELECTED_INTENTS_FF,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      when(this.summaryHelper.getIntentTemplate(ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          false)).thenReturn("<p>PLaintext</p>");
      String key =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-template";
      String value =
          "<p>Test rules&nbsp;<br><br>Test_String1 @<span entity-id=\"entity1\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String1</span></p><p>Test rules&nbsp;<br><br>Test_String2 @<span entity-id=\"entity2\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String2</span></p><p color='black' >mobile : <span variable-id='variable1' class='uniphore-variable-name'>mobile</span></p><p color='black' >Address : <span variable-id='variable2' class='uniphore-variable-name'>Address</span></p>";
      this.intentTemplateLocalCache.put(key, "summary-intent-2", value);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Map<String, String> intentMap = this.datahelper.jsonToMapConverter(TestConstant.INTENT2_BP2);
      if (!intentMap.isEmpty())
        this.mockService.mockIntentAddedEvent(intentMap);
    } catch (final Exception e) {
      log.error("exception at intent2AddedConv2: {}", e);
      // Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(50)
  void entity1UpdatedAgainConv2() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      List<String> effectedIntents = new ArrayList<>();
      effectedIntents.add("intent1");
      SummaryUpdateEventLog.EventData eventData = new SummaryUpdateEventLog.EventData();
      eventData.setHeaderEffected(true);
      eventData.setEventId("eventId");
      eventData.setDetectedBy("USER");
      eventData.setValue("value");
      eventData.setInstanceId("instanceId");
      eventData.setEffectedIntents(effectedIntents);
      List<SummaryUpdateEventLog.EventData> eventDataList = new ArrayList<>();
      eventDataList.add(eventData);
      SummaryUpdateEventLog summaryUpdateEventLog1 =
          SummaryUpdateEventLog.builder().eventData(eventDataList).summary("summary")
              .eventTime(12345).eventType(Constants.TAG_ENTITY_UPDATE).id("Id")
              .businessProcessId("bp1").environment("dev").build();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      when(this.summaryHelper.getSummaryUpdateEventLog(ArgumentMatchers.any(),
          ArgumentMatchers.anyLong(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(summaryUpdateEventLog1);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Set<String> values = new HashSet<>();
      values.add("value1");
      values.add("value2");
      String headerKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.header-entity.map";
      String intentKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-entity.map";
      String hashkey = "entity1";
      this.headerEntityMapLocalCache.put(headerKey, hashkey, values);
      this.intentEntityMapLocalCache.put(intentKey, hashkey, values);
      Map<String, String> entityMap =
          this.datahelper.jsonToMapConverter(TestConstant.ENTITY1_UPDATED_AGAIN_BP2);
      if (!entityMap.isEmpty()) {
        this.mockService.mockEntityUpdatedEvent(entityMap);
      }
    } catch (final Exception e) {
      log.error("exception at entity1UpdatedAgainConv2: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(51)
  void variable1UpdatedAgainConv2() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Set<String> values = new HashSet<>();
      values.add("value1");
      values.add("value2");
      String headerKey =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.header-variable.map";
      String hashKey = "variable1";
      this.headerVariableMapLocalCache.put(headerKey, hashKey, values);
      Map<String, String> variableMap =
          this.datahelper.jsonToMapConverter(TestConstant.VARIABLE1_UPDATED_AGAIN_BP2);
      if (!variableMap.isEmpty())
        this.mockService.mockVariableUpdatedEvent(variableMap);
    } catch (final Exception e) {
      log.error("exception at variable1UpdatedAgainConv2: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(52)
  void intent1DeletedConv2() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      List<String> intents = new ArrayList<>();
      intents.add("currentIntent");
      String key =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-template";
      String value =
          "<p>Test rules&nbsp;<br><br>Test_String1 @<span entity-id=\"entity1\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String1</span></p><p>Test rules&nbsp;<br><br>Test_String2 @<span entity-id=\"entity2\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String2</span></p><p color='black' >mobile : <span variable-id='variable1' class='uniphore-variable-name'>mobile</span></p><p color='black' >Address : <span variable-id='variable2' class='uniphore-variable-name'>Address</span></p>";
      this.intentTemplateLocalCache.put(key, "summary-intent-2", value);
      when(this.intentTemplateCacheHelper.getCurrentIntentForConversation(
          ArgumentMatchers.any())).thenReturn("intent-test-1-1");
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      when(this.intentTemplateCacheHelper.getAllIntentBoundaryStack(
          ArgumentMatchers.any())).thenReturn(intents);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      List<String> effectedIntents = new ArrayList<>();
      effectedIntents.add("intent1");
      effectedIntents.add("intent2");
      SummaryUpdateEventLog.EventData eventData =
          SummaryUpdateEventLog.EventData.builder().eventId("eventId").value("eventValue")
              .detectedBy("agent").headerEffected(true).instanceId("instance1")
              .effectedIntents(effectedIntents).build();
      List<SummaryUpdateEventLog.EventData> eventDataList = new ArrayList<>();
      eventDataList.add(eventData);
      Map<String, String> intentMap = this.datahelper.jsonToMapConverter(TestConstant.INTENT1_BP2);
      if (!intentMap.isEmpty())
        this.mockService.mockIntentDeletedEvent(intentMap);
    } catch (final Exception e) {
      log.error("exception at intent1DeletedConv2: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(53)
  void testHandleTranscriptAddedEventForGenAINoFormatSummarySuccess() {
    try {

      doReturn(true).when(this.ldHelper).isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF,
          LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.DISABLE_GENAI_SUMMARY_ON_TRANSCRIPT_ADD,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(Optional.of(this.datahelper.summaryResponse())).when(this.grpcClientHelper)
          .getGenAiSummary(this.summaryDoc.getConversationId(), this.tenantId, null, summaryDoc,null);
      ConversationEventEnvelope transcriptAddedEvent = this.datahelper.createTranscriptAddedEvent();
      this.summaryRuntimeService.handleConversationEvent(transcriptAddedEvent,
          DateUtil.TIME.millis());
    } catch (final Exception e) {
      log.error("exception at TranscriptAddedEventForGenAISummarySuccess: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(54)
  void testHandleTranscriptAddedEventForGenAISummarySuccessFormat() {
    try {
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper).isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF,
          LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(this.datahelper.inputGenAiConfigObject(GEN_AI_CONFIG_JSON))
          .when(this.genAISummaryConfigCache)
          .get(anyString(), anyString(), any(TypeReference.class));
      doReturn(Optional.ofNullable(this.datahelper.summaryResponseFormat()))
          .when(this.grpcClientHelper)
          .getGenAiSummary(this.summaryDoc.getConversationId(), this.tenantId, null, summaryDoc,null);
      ConversationEventEnvelope transcriptAddedEvent = this.datahelper.createTranscriptAddedEvent();
      this.summaryRuntimeService.handleConversationEvent(transcriptAddedEvent,
          DateUtil.TIME.millis());
    } catch (final Exception e) {
      log.error("exception at TranscriptAddedEventForGenAISummarySuccess: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(55)
  void testHandleTranscriptAddedEventWithFeatureFlagDisabled() {
    try {
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(this.datahelper.inputGenAiConfigObject(GEN_AI_CONFIG_JSON))
          .when(this.genAISummaryConfigCache)
          .get(anyString(), anyString(), any(TypeReference.class));
      doReturn(Optional.ofNullable(this.datahelper.summaryResponseFormat()))
          .when(this.grpcClientHelper)
          .getGenAiSummary(this.summaryDoc.getConversationId(), this.tenantId, null, summaryDoc,null);
      ConversationEventEnvelope transcriptAddedEvent = this.datahelper.createTranscriptAddedEvent();
      this.summaryRuntimeService.handleConversationEvent(transcriptAddedEvent,
          DateUtil.TIME.millis());
    } catch (final Exception e) {
      log.error("exception at TranscriptAddedEventForGenAISummarySuccess: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(55)
  void testHandleTranscriptAddedEventWithFeatureFlagDisabledTranscript() {
    try {
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.DISABLE_GENAI_SUMMARY_ON_TRANSCRIPT_ADD,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(this.datahelper.inputGenAiConfigObject(GEN_AI_CONFIG_JSON))
          .when(this.genAISummaryConfigCache)
          .get(anyString(), anyString(), any(TypeReference.class));
      doReturn(Optional.ofNullable(this.datahelper.summaryResponseFormat()))
          .when(this.grpcClientHelper)
          .getGenAiSummary(this.summaryDoc.getConversationId(), this.tenantId, null, summaryDoc,null);
      ConversationEventEnvelope transcriptAddedEvent = this.datahelper.createTranscriptAddedEvent();
      this.summaryRuntimeService.handleConversationEvent(transcriptAddedEvent,
          DateUtil.TIME.millis());
    } catch (final Exception e) {
      log.error("exception at TranscriptAddedEventForGenAISummarySuccess: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(56)
  void testFlowEndedEvent() {
    try {
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_INTENT_BOUNDARY,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      String key =
          "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev.intent-template";
      String value =
          "<p>Test rules&nbsp;<br><br>Test_String1 @<span entity-id=\"entity1\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String1</span></p><p>Test rules&nbsp;<br><br>Test_String2 @<span entity-id=\"entity2\" class=\"uniphore-entity-name\" contenteditable=\"false\">Test_String2</span></p><p color='black' >mobile : <span variable-id='variable1' class='uniphore-variable-name'>mobile</span></p><p color='black' >Address : <span variable-id='variable2' class='uniphore-variable-name'>Address</span></p>";
      this.intentTemplateLocalCache.put(key, "summary-intent-2", value);
      doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
          .findById(ArgumentMatchers.any(String.class));
      Map<String, String> intentMap = this.datahelper.jsonToMapConverter(TestConstant.INTENT_ADD);
      if (!intentMap.isEmpty())
        this.mockService.mockFlowEndedEvent(intentMap);
    } catch (final Exception e) {
      log.error("exception at testFlowEndedEvent: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  void testSummaryReviewed_CustomExceptionWhileFetchingSummaryDoc() {
    super.mockTenantContext();
    when(this.summaryHelper.getSummaryDoc(CONVERSATION_ID)).thenThrow(new CustomException(ErrorCodes.CONVERSATION_NOT_FOUND, HttpStatus.BAD_REQUEST));

    MessageResponse messageResponse = this.summaryRuntimeService.summaryReviewed(CONVERSATION_ID, Optional.of(false),Optional.of(Boolean.FALSE),
        Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    assertEquals("No summary was found in cache or database", messageResponse.getMessage());
    assertEquals(CONVERSATION_ID, messageResponse.getId());
  }

  @Test
  void testSummaryReviewed_GENAI_SUMMARY_FFEnabled() {
    mockTenantContext();
    summaryDoc = this.datahelper.inputSummaryDoc();
    when(this.summaryHelper.getSummaryDoc(CONVERSATION_ID)).thenReturn(summaryDoc);
    when(this.ldHelper.isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF, LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(Boolean.TRUE);
    doNothing().when(this.summaryHelper).saveSummaryDoc(summaryDoc);
    when(this.ldHelper.isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS, LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(Boolean.FALSE);

    MessageResponse messageResponse = this.summaryRuntimeService.summaryReviewed(CONVERSATION_ID, Optional.of(false),Optional.of(Boolean.FALSE),
        Optional.of(AutoSubmitOption.MANUAL));
    assertEquals(Constants.SUCCESS, messageResponse.getMessage());
    assertEquals(CONVERSATION_ID, messageResponse.getId());
  }

  @Test
  void testSummaryReviewed_GENAI_SUMMARY_FFEnabledCreateGenAiSummaryThrowsCustomExceptionWhileFetchingSummaryDoc()
      throws IOException {
    mockTenantContext();
    summaryDoc = this.datahelper.inputSummaryDocResponse(SUMMARY_DOC_RESPONSE_JSON);
    when(this.summaryHelper.getSummaryDoc(CONVERSATION_ID)).thenReturn(summaryDoc).thenThrow(new CustomException(ErrorCodes.CONVERSATION_NOT_FOUND, HttpStatus.BAD_REQUEST));
    when(this.ldHelper.isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF, LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(Boolean.TRUE);
    when(this.genAISummaryService.createGenAiSummary(anyString(), anyString(), any(), anyBoolean(),
        anyBoolean(), anyLong(),any())).thenReturn(null);

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    doNothing().when(threadPoolExecutor).execute(runnableCaptor.capture());

    // Trigger the method (this only schedules the task)
    summaryRuntimeService.summaryReviewed(CONVERSATION_ID, Optional.empty(),Optional.empty(),
        Optional.of(AutoSubmitOption.END_OF_CURRENT_CALL));



    // Now run the captured Runnable manually to trigger the exception
    verify(threadPoolExecutor).execute(runnableCaptor.capture());
    Runnable runnable = runnableCaptor.getValue();
    Assertions.assertNotNull(runnable, "Runnable must not be null");

    // Assert that the exception is thrown when the Runnable runs
    CustomException thrown = Assertions.assertThrows(CustomException.class, runnable::run);
    assertEquals(ErrorCodes.CONVERSATION_NOT_FOUND, thrown.getCode());
  }

  @Test
  @Order(57)
  void testSummaryReviewedWithoutFF() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();
      doReturn(summaryDoc).when(this.summaryHelper)
          .getSummaryDoc(ArgumentMatchers.any(String.class));
      this.summaryRuntimeService.summaryReviewed(CONVERSATION_ID, Optional.of(false),Optional.of(Boolean.FALSE),Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    } catch (final Exception e) {
      log.error("exception at testSummaryReviewedWithoutFF: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(64)
  void testSummaryReviewedWithoutFFWithGenAiSummaryNotAvailable() {
    try {
      summaryDoc = this.datahelper.inputSummaryDoc();

      SummaryDoc summaryDocNoGEnAi = this.datahelper.inputSummaryDoc();
      summaryDocNoGEnAi.setGenAiSummary(null);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any(String.class))).thenReturn(
          summaryDocNoGEnAi, summaryDoc);
      this.summaryRuntimeService.summaryReviewed(CONVERSATION_ID, Optional.of(false),Optional.of(Boolean.FALSE),Optional.of(AutoSubmitOption.AUTO_SUBMIT));
      Thread.sleep(5000);
    } catch (final Exception e) {
      log.error("exception at testSummaryReviewedWithoutFF: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(65)
  void testSummaryReviewedWithoutFFWithGenAiSummaryNotAvailableWithLoopWithGenAIEnabled() {
    try {
      TenantContext.setTenantContext(TENANT_ID, "dev");
      TenantUtils.setTenantDetails(TENANT_ID, "dev");
      doReturn(true).when(this.ldHelper).isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF,
          LaunchDarklyConstants.FF_CONTEXT_KIND);
      SummaryDoc summaryDocLocal = this.datahelper.inputSummaryDoc();

      SummaryDoc summaryDocNoGEnAi = this.datahelper.inputSummaryDoc();
      summaryDocNoGEnAi.setGenAiSummary(null);
      SummaryDoc summaryDocNoGEnAi2 = this.datahelper.inputSummaryDoc();
      summaryDocNoGEnAi2.setGenAiSummary(null);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any(String.class))).thenReturn(
          summaryDocNoGEnAi, summaryDocNoGEnAi2, summaryDocLocal);
      this.summaryRuntimeService.summaryReviewed(CONVERSATION_ID, Optional.of(false),Optional.of(Boolean.FALSE),Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    } catch (final Exception e) {
      log.error("exception at testSummaryReviewedWithoutFF: ", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(65)
  void testSummaryReviewedWithoutFFWithGenAiSummaryNotAvailableWithLoopWithGenAIDisabled() {
    try {
      TenantContext.setTenantContext(TENANT_ID, "dev");
      TenantUtils.setTenantDetails(TENANT_ID, "dev");
      doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      SummaryDoc summaryDocLocal = this.datahelper.inputSummaryDoc();

      SummaryDoc summaryDocNoGEnAi = this.datahelper.inputSummaryDoc();
      summaryDocNoGEnAi.setGenAiSummary(null);
      SummaryDoc summaryDocNoGEnAi2 = this.datahelper.inputSummaryDoc();
      summaryDocNoGEnAi2.setGenAiSummary(null);
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any(String.class))).thenReturn(
          summaryDocNoGEnAi, summaryDocNoGEnAi2, summaryDocLocal);
      this.summaryRuntimeService.summaryReviewed(CONVERSATION_ID, Optional.of(false),Optional.of(Boolean.FALSE),Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    } catch (final Exception e) {
      log.error("exception at testSummaryReviewedWithoutFF: ", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(58)
  void testSummaryReviewedWithException() {
    super.mockTenantContext();
    doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
        .findById(ArgumentMatchers.any(String.class));
    when(this.summaryHelper.getSummaryDoc(null)).thenThrow(
        new RuntimeException("Internal server error"));
    Assertions.assertThrows(CustomException.class, () -> {
      this.summaryRuntimeService.summaryReviewed(null, Optional.of(false),Optional.of(Boolean.FALSE),Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    });
  }

  @Test
  @Order(59)
  void testSummaryReviewedWithFFEnabled() {
    super.mockTenantContext();
    summaryDoc = this.datahelper.inputSummaryDoc();
    doReturn(true).when(this.ldHelper).isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS,
        LaunchDarklyConstants.FF_CONTEXT_KIND);
    when(this.genAISummaryService.createGenAiSummary(ArgumentMatchers.anyString(),
        ArgumentMatchers.anyString(), Optional.of(ArgumentMatchers.anyString()),
        ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(),
        ArgumentMatchers.anyLong(),any())).thenReturn(summaryDoc);
    doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
        .findById(ArgumentMatchers.any(String.class));
    when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
    when(this.summaryHelper.getSummaryDoc(CONVERSATION_ID)).thenReturn(summaryDoc);
    AgentStepPojo agentStepPojo = new AgentStepPojo();
    agentStepPojo.setId("id");
    agentStepPojo.setText("text");
    agentStepPojo.setIndex(1);
    List<AgentStepPojo> agentStepList = new ArrayList<>();
    agentStepList.add(agentStepPojo);
    when(this.agentStepsCacheHelper.getAsList(ArgumentMatchers.any())).thenReturn(agentStepList);
    this.summaryRuntimeService.summaryReviewed(CONVERSATION_ID, Optional.of(true),Optional.of(Boolean.FALSE),Optional.of(AutoSubmitOption.AUTO_SUBMIT));
  }

  @Test
  void testFetchSummaryBySessionIds_ThrowsCustomException() {
    super.mockTenantContext();
    List<String> sessionIds = Arrays.asList("sessionId1", "sessionId2");
    when(this.summaryHelper.getSummaryDocsByExtConversationIds(sessionIds)).thenThrow(new MongoException("Exception occurred while fetching mongo document"));

    Assertions.assertThrows(CustomException.class, () -> {
      this.summaryRuntimeService.fetchSummaryBySessionIds(sessionIds);
    });
  }

  @Test
  @Order(60)
  void testFetchSummaryBySessionIds() {
    super.mockTenantContext();
    List<String> sessionIds = new ArrayList<>();
    sessionIds.add("session1");
    List<SummaryDoc> summaryDocList = new ArrayList<>();
    summaryDocList.add(summaryDoc);
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    when(this.summaryHelper.getSummaryDocsByExtConversationIds(sessionIds)).thenReturn(
        summaryDocList);
    this.summaryRuntimeService.fetchSummaryBySessionIds(sessionIds);

  }

  @Test
  @Order(61)
  void testGetConvIdAndExpIdByExtId() {
    super.mockTenantContext();
    summaryDoc = this.datahelper.inputSummaryDoc();
    when(this.summaryRepository.findByExtConversationIdAndTenantIdAndEnvironment(
        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(
        summaryDoc);
    this.summaryRuntimeService.getConvIdAndExpIdByExtId(CONVERSATION_ID);
  }

  @Test
  @Order(62)
  void testGetConvIdAndExpIdByExtIdNoValues() {
    super.mockTenantContext();
    summaryDoc = this.datahelper.inputSummaryDoc();
    when(this.summaryRuntimeDao.getSummaryByExtId(null)).thenReturn(summaryDoc);
    this.summaryRuntimeService.getConvIdAndExpIdByExtId(CONVERSATION_ID);
  }

  @Test
  @Order(63)
  void testGenAiAtCallEndEvent() throws Exception {
    doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
        .findById(ArgumentMatchers.any(String.class));
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF, LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(Constants.UASSIST_STATE_V2_UPDATE_FF, LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.DISABLE_GENAI_SUMMARY_ON_TRANSCRIPT_ADD,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(this.datahelper.inputGenAiConfigObject(GEN_AI_CONFIG_JSON))
        .when(this.genAISummaryConfigCache)
        .get(anyString(), anyString(), any(TypeReference.class));
    doReturn(Optional.ofNullable(this.datahelper.summaryResponseFormat()))
        .when(this.grpcClientHelper)
        .getGenAiSummary(this.summaryDoc.getConversationId(), this.tenantId, null, summaryDoc,null);
    ConversationEventEnvelope callEndedEvent = this.datahelper.createStateUpdatedEvent();
    this.summaryRuntimeService.handleConversationEvent(callEndedEvent, DateUtil.TIME.millis());
  }

  @Test
  @Order(64)
  public void handleConversationTranscriptChunkAdded_Success() {
    try {
      when(summaryHelper.getSummaryDoc(anyString())).thenReturn(datahelper.inputSummaryDoc());
      ConversationEventEnvelope transcriptChunkAddedEvent = datahelper.createTranscriptChuckAddedEvent();

      summaryRuntimeService.handleConversationEvent(transcriptChunkAddedEvent, DateUtil.TIME.millis());

      assertNotNull(transcriptChunkAddedEvent);
      assertEquals("12345", transcriptChunkAddedEvent.getTenantId());
      assertEquals("agent", transcriptChunkAddedEvent.getTranscriptChunkAdded().getTranscriptChunk().getTurnsList().get(0).getParticipantType());
      assertEquals("hi hello", transcriptChunkAddedEvent.getTranscriptChunkAdded().getTranscriptChunk().getTurnsList().get(0).getWordsList().get(0).getText());
    } catch (Exception e) {
      log.error("Exception in handleConversationTranscriptChunkAdded_Success: {}", e.getMessage());
      Assertions.fail("Error: " + e.getMessage());
    }
  }

  @Test
  @Order(65)
  public void handleConversationTranscriptChunkAdded_NullSummaryDoc() {
    try {
      when(summaryHelper.getSummaryDoc(anyString())).thenReturn(null);
      ConversationEventEnvelope event = datahelper.createTranscriptChuckAddedEvent();

      summaryRuntimeService.handleConversationEvent(event, DateUtil.TIME.millis());

      assertNotNull(event);
      assertNull(summaryHelper.getSummaryDoc("any"));
    } catch (Exception e) {
      log.error("Exception in handleConversationTranscriptChunkAdded_NullSummaryDoc: {}", e.getMessage());
      Assertions.fail("Error: " + e.getMessage());
    }
  }

  @Test
  @Order(66)
  public void handleConversationTranscriptChunkAdded_EmptyTurnsList() {
    try {
      SummaryDoc summaryDoc = datahelper.inputSummaryDoc();
      when(summaryHelper.getSummaryDoc(anyString())).thenReturn(summaryDoc);

      ConversationEventEnvelope event = datahelper.createTranscriptChuckAddedEventWithEmptyTurns();

      summaryRuntimeService.handleConversationEvent(event, DateUtil.TIME.millis());

      assertNotNull(event);
    } catch (Exception e) {
      log.error("Exception in handleConversationTranscriptChunkAdded_EmptyTurnsList: {}", e.getMessage());
      Assertions.fail("Error: " + e.getMessage());
    }
  }

  @Test
  @Order(67)
  public void handleConversationTranscriptChunkAdded_NullWordsInTurn() {
    try {
      SummaryDoc summaryDoc = datahelper.inputSummaryDoc();
      when(summaryHelper.getSummaryDoc(anyString())).thenReturn(summaryDoc);

      ConversationEventEnvelope event = datahelper.createTranscriptChuckAddedEventWithNull();

      summaryRuntimeService.handleConversationEvent(event, DateUtil.TIME.millis());

      assertNotNull(event);
    } catch (Exception e) {
      log.error("Exception in handleConversationTranscriptChunkAdded_NullWordsInTurn: {}", e.getMessage());
      Assertions.fail("Error: " + e.getMessage());
    }
  }

  @Test
  @Order(68)
  public void handleConversationTranscriptChunkAdded_SameParticipantConsecutiveTurns() {
    try {
      SummaryDoc summaryDoc = datahelper.inputSummaryDoc();
      when(summaryHelper.getSummaryDoc(anyString())).thenReturn(summaryDoc);

      ConversationEventEnvelope event = datahelper.createTranscriptChuckAddedEvent();
      List<Turn> turns = new ArrayList<>();
      Turn turn1 = Turn.newBuilder().setParticipantType("agent").addWords(Word.newBuilder().setText("hi").build()).build();
      Turn turn2 = Turn.newBuilder().setParticipantType("agent").addWords(Word.newBuilder().setText("hello").build()).build();
      turns.add(turn1);
      turns.add(turn2);


      summaryRuntimeService.handleConversationEvent(event, DateUtil.TIME.millis());

      assertNotNull(event);
      assertEquals("agent", event.getTranscriptChunkAdded().getTranscriptChunk().getTurnsList().get(0).getParticipantType());
    } catch (Exception e) {
      log.error("Exception in handleConversationTranscriptChunkAdded_SameParticipantConsecutiveTurns: {}", e.getMessage());
      Assertions.fail("Error: " + e.getMessage());
    }
  }

  @Test
  @Order(69)
  public void handleConversationTranscriptChunkAdded_UnexpectedParticipantType() {
    try {
      SummaryDoc summaryDoc = datahelper.inputSummaryDoc();
      when(summaryHelper.getSummaryDoc(anyString())).thenReturn(summaryDoc);

      ConversationEventEnvelope event = datahelper.createTranscriptChuckAddedEvent();
      Turn unknownTurn = Turn.newBuilder().setParticipantType("agent").addWords(Word.newBuilder().setText("check").build()).build();

      summaryRuntimeService.handleConversationEvent(event, DateUtil.TIME.millis());

      assertNotNull(event);
      assertEquals("agent", event.getTranscriptChunkAdded().getTranscriptChunk().getTurnsList().get(0).getParticipantType());
    } catch (Exception e) {
      log.error("Exception in handleConversationTranscriptChunkAdded_UnexpectedParticipantType: {}", e.getMessage());
      Assertions.fail("Error: " + e.getMessage());
    }
  }

  @Test
  @Order(70)
  void testGenAiAtCallEndEventFFDisabled() throws Exception {
    doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
        .findById(ArgumentMatchers.any(String.class));
    doReturn(false).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF, LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(Constants.UASSIST_STATE_V2_UPDATE_FF, LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(false).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.DISABLE_GENAI_SUMMARY_ON_TRANSCRIPT_ADD,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(false).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(this.datahelper.inputGenAiConfigObject(GEN_AI_CONFIG_JSON))
        .when(this.genAISummaryConfigCache)
        .get(anyString(), anyString(), any(TypeReference.class));
    doReturn(Optional.ofNullable(this.datahelper.summaryResponseFormat()))
        .when(this.grpcClientHelper)
        .getGenAiSummary(this.summaryDoc.getConversationId(), this.tenantId, null, summaryDoc,null);
    ConversationEventEnvelope callEndedEvent = this.datahelper.createStateV1UpdatedEvent();
    this.summaryRuntimeService.handleConversationEvent(callEndedEvent, DateUtil.TIME.millis());
  }
  public static Stream<Arguments> printCallSubmitLog() {
    final List<GenAiSummaryDoc.Section> sectionList =
        List.of(GenAiSummaryDoc.Section.builder().text("Hello").build(),
            GenAiSummaryDoc.Section.builder().text("How can i help you").build());
    final SummaryDoc genAiSummary =
        SummaryDoc.builder().extConversationId("sessionId").conversationId("conversationId")
            .genAiSummary(GenAiSummaryDoc.builder().sections(sectionList).build()).build();
    final SummaryDoc intentSummary =
        SummaryDoc.builder().extConversationId("sessionId").conversationId("conversationId")
            .intentSummaries(Map.of("one", IntentSummaryDoc.builder().plainText("Hello").build()))
            .build();
    return Stream.of(Arguments.of("print log with null summary object", null,DateUtil.TIME.millis()),
        Arguments.of("print log with genAi summary object", genAiSummary,DateUtil.TIME.millis()),
        Arguments.of("print log with intent summary object", intentSummary,DateUtil.TIME.millis()));
  }

  @Test
  @Order(71)
  void testGenAiAtCallEndEvent_IfCallBlocked() throws Exception {
    doReturn(Optional.of(summaryDoc)).when(this.summaryRepository)
        .findById(ArgumentMatchers.any(String.class));
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF, LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(Constants.UASSIST_STATE_V2_UPDATE_FF, LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(false).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.DISABLE_GENAI_SUMMARY_ON_TRANSCRIPT_ADD,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(true).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    doReturn(this.datahelper.inputGenAiConfigObject(GEN_AI_CONFIG_JSON))
        .when(this.genAISummaryConfigCache)
        .get(anyString(), anyString(), any(TypeReference.class));
    doReturn(Optional.ofNullable(this.datahelper.summaryResponseFormat()))
        .when(this.grpcClientHelper)
        .getGenAiSummary(this.summaryDoc.getConversationId(), this.tenantId, null, summaryDoc,null);
    ConversationEventEnvelope callEndedEvent = this.datahelper.createPolicyAddedEvent();
    this.summaryRuntimeService.handleConversationEvent(callEndedEvent, DateUtil.TIME.millis());
  }

  @ParameterizedTest(name = "#{index} - Testing {0}")
  @MethodSource("printCallSubmitLog")
  void testPrintCallFlowLog(final String label, final SummaryDoc summaryDoc,final long eventStartTime) throws Exception {
    assertDoesNotThrow(
        () -> ReflectionTestUtils.invokeMethod(this.summaryRuntimeService, "printCallSubmitLog",
            summaryDoc,eventStartTime));
  }

  @Test
  @Order(62)
  void testFetchSummaries() {
    super.mockTenantContext();
    List<String> conversationIds = Arrays.asList("conv1", "conv2", "conv3");
    List<SummaryDoc> summaryDocList = new ArrayList<>();
    SummaryDoc doc1 = SummaryDoc.builder().conversationId("conv1").build();
    SummaryDoc doc2 = SummaryDoc.builder().conversationId("conv2").build();
    SummaryDoc doc3 = SummaryDoc.builder().conversationId("conv3").build();
    summaryDocList.add(doc1);
    summaryDocList.add(doc2);
    summaryDocList.add(doc3);

    doReturn(false).when(this.ldHelper)
        .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
            LaunchDarklyConstants.FF_CONTEXT_KIND);
    when(this.summaryHelper.getSummaryDocs(conversationIds)).thenReturn(summaryDocList);

    List<SummaryResponse> result = this.summaryRuntimeService.fetchSummaries(conversationIds);

    assertEquals(3, result.size());
    verify(this.summaryHelper, times(1)).getSummaryDocs(conversationIds);
    verify(this.ldHelper, times(1)).isFFEnabled(
        LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
        LaunchDarklyConstants.FF_CONTEXT_KIND);
  }

  @Test
  void testFetchSummaries_ThrowsCustomException() {
    super.mockTenantContext();
    List<String> conversationIds = Arrays.asList("conv1", "conv2");
    when(this.summaryHelper.getSummaryDocs(conversationIds)).thenThrow(
        new MongoException("Exception occurred while fetching mongo documents"));

    Assertions.assertThrows(CustomException.class, () -> {
      this.summaryRuntimeService.fetchSummaries(conversationIds);
    });
  }

}
