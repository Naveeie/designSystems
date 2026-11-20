package com.uniphore.uassist.summary.util;

import com.google.protobuf.Timestamp;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.summary.v1.GenAiSummary;
import com.uniphore.summary.v1.Header;
import com.uniphore.summary.v1.IntentSummary;
import com.uniphore.summary.v1.Summary;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.cache.*;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.dao.AgentEditsRepository;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.documents.*;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.execption.ErrorResponse;
import com.uniphore.uassist.summary.kafka.MessagePublisher;
import com.uniphore.uassist.summary.model.AgentRequestPayload;
import com.uniphore.uassist.summary.model.DocConverter;
import com.uniphore.uassist.summary.model.ProtoConverter;
import com.uniphore.uassist.summary.model.SummaryTemplate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.stream.Stream;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.uassist.summary.common.Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
public class SummaryHelperTest extends ApplicationTest {

  @Mock
  private SummaryRuntimeDao summaryRuntimeDao;

  @Mock
  private MessagePublisher messagePublisher;

  @Mock
  private SummaryCacheHelper summaryCacheHelper;

  @Mock
  private LaunchDarklyHelper launchDarklyHelper;

  @Mock
  private AgentEditsRepository agentEditsRepository;

  @Mock
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  @Mock
  private CacheRefreshHelper cacheRefreshHelper;

  @Mock
  private GenAISummaryConfigCacheHelper genAiSummaryConfigCacheHelper;

  @InjectMocks
  private SummaryHelper summaryHelper;

  private SummaryDoc summaryDoc;
  private Map<String, IntentSummary> intentSummaryMap;
  private List<String> effectedIntents;

  @BeforeEach
  void setUpDoc() {
    summaryDoc = new SummaryDoc();
    intentSummaryMap = new HashMap<>();
    effectedIntents = new ArrayList<>();

    // Mock IntentSummaryDoc and related objects
    IntentSummaryDoc intentSummaryDoc = new IntentSummaryDoc();
    intentSummaryDoc.setIntentId("intent-123");
    intentSummaryDoc.setTimestamp(TimeStampDoc.builder().seconds(54321L).nanos(54321).build());
    intentSummaryDoc.setInstanceId("instance-123");
    intentSummaryDoc.setTemplate("Original Template");
    intentSummaryDoc.setEntityInstanceIdMap(new HashMap<>());
    intentSummaryDoc.setVariableInstanceIdMap(new HashMap<>());
    intentSummaryDoc.setEntityIdIsEdited(new HashMap<>());

    Map<String, IntentSummaryDoc> intentSummaries = new HashMap<>();
    intentSummaries.put("intent-123", intentSummaryDoc);

    summaryDoc.setIntentSummaries(intentSummaries);
    summaryDoc.setSystemIntentSummaries(new HashMap<>(intentSummaries));

    HeaderSummaryDoc headerSummaryDoc = new HeaderSummaryDoc();
    headerSummaryDoc.setTemplate("Original Template");
    headerSummaryDoc.setEntityInstanceIdMap(new HashMap<>());
    headerSummaryDoc.setVariableInstanceIdMap(new HashMap<>());
    headerSummaryDoc.setPlainText("plain tet");
    headerSummaryDoc.setTimestamp(TimeStampDoc.builder().seconds(43L).nanos(432).build());

    summaryDoc.setHeaderSummary(headerSummaryDoc);
  }

  @Test
  void testUpdateHeaderSummary_WhenHeaderSummaryExists() {

    Timestamp time = Timestamp.newBuilder().setSeconds(432L).setNanos(332).build();

    try (MockedStatic<ProtoConverter> mockedStatic = mockStatic(ProtoConverter.class)) {
      mockedStatic.when(() -> ProtoConverter.getHeaderSummary(any(), any(), any(), any()))
          .thenReturn(mock(Header.class));

      summaryHelper.updateHeaderSummary(summaryDoc, "header-123", "value-123", "instance-123",
          "VARIABLE", time);
    }

  }

  @Test
  void testHandleIntentTemplateUpdate_WhenSystemSummaryEnabledAndNotAgentEdit() {
    when(launchDarklyHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);

    try (MockedStatic<ProtoConverter> mockedStatic = mockStatic(ProtoConverter.class)) {
      mockedStatic.when(() -> ProtoConverter.getIntentSummary(any(), any(), any()))
          .thenReturn(mock(IntentSummary.class));

      summaryHelper.handleIntentTemplateUpdate("session-123", intentSummaryMap, effectedIntents,
          "component-123", "value-123", "type-123", summaryDoc, "intent-123", false);

    }

    assertFalse(effectedIntents.isEmpty());

  }

  @Test
  @Order(1)
  void testRemoveSummaryDoc() {
    String conversationId = "test-conversation-id";
    summaryHelper.removeSummaryDoc(conversationId);
    verify(summaryCacheHelper, times(1)).remove(conversationId);
  }

  @Test
  @Order(2)
  void testGetSummaryDoc_ExistsInCache() throws CustomException {
    String conversationId = "test-conversation-id";
    SummaryDoc summaryDoc = new SummaryDoc();
    when(summaryCacheHelper.find(conversationId)).thenReturn(summaryDoc);

    SummaryDoc result = summaryHelper.getSummaryDoc(conversationId);
    assertNotNull(result);
    assertEquals(summaryDoc, result);
  }

  @Test
  @Order(3)
  void testGetSummaryDoc_NotFoundInCache() throws CustomException {
    String conversationId = "test-conversation-id";
    when(summaryCacheHelper.find(conversationId)).thenReturn(null);
    when(summaryRuntimeDao.getSummaryDoc(conversationId)).thenReturn(Optional.of(new SummaryDoc()));

    SummaryDoc result = summaryHelper.getSummaryDoc(conversationId);
    assertNotNull(result);
  }

  @Test
  @Order(4)
  void testGetSummaryDoc_NotFound() {
    String conversationId = "test-conversation-id";
    when(summaryCacheHelper.find(conversationId)).thenReturn(null);
    when(summaryRuntimeDao.getSummaryDoc(conversationId)).thenReturn(Optional.empty());

    CustomException thrown =
        assertThrows(CustomException.class, () -> summaryHelper.getSummaryDoc(conversationId));
    assertEquals(ErrorCodes.CONVERSATION_NOT_FOUND, thrown.getCode());
    assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
  }

  @Test
  @Order(5)
  void testSaveSummaryDoc() {
    SummaryDoc summaryDoc = new SummaryDoc();
    summaryHelper.saveSummaryDoc(summaryDoc);
    verify(summaryCacheHelper, times(1)).set(summaryDoc);
    verify(summaryRuntimeDao, times(1)).saveSummaryDoc(summaryDoc);
  }

  @Test
  @Order(6)
  void testCreateEventLogBasedOnAgentAction_HeaderEdit() {
    AgentUpdateRequest agentUpdateRequest = new AgentUpdateRequest();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    agentUpdateRequest.setAgentAction(Commons.AgentAction.HEADER_EDIT);
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setHeaderSummary("Test Header Summary");
    agentUpdateRequest.setAgentRequestPayload(payload);

    summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);

    assertEquals(1, eventData.size());
    assertEquals("HEADER_EDIT", eventData.get(0).getEventId());
  }

  @Test
  @Order(7)
  void testCreateEventLogBasedOnAgentAction_GenAISummaryEdit() {
    AgentUpdateRequest agentUpdateRequest = new AgentUpdateRequest();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    agentUpdateRequest.setAgentAction(Commons.AgentAction.GEN_AI_SUMMARY_EDIT);
    AgentRequestPayload payload = new AgentRequestPayload();
    GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().build();
    payload.setGenAiSummaryDoc(genAiSummaryDoc);
    agentUpdateRequest.setAgentRequestPayload(payload);

    summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);

    assertEquals(1, eventData.size());
    assertEquals("GEN_AI_SUMMARY_EDIT", eventData.get(0).getEventId());
  }

  @Test
  @Order(8)
  void testCreateEventLogBasedOnAgentAction_AgentNotes() {
    AgentUpdateRequest agentUpdateRequest = new AgentUpdateRequest();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    agentUpdateRequest.setAgentAction(Commons.AgentAction.AGENT_NOTES);
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setHeaderSummary("Test Header Summary");
    agentUpdateRequest.setAgentRequestPayload(payload);

    summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);

    assertEquals(1, eventData.size());
    assertEquals("AGENT_NOTES", eventData.get(0).getEventId());
  }

  @Test
  @Order(9)
  void testCreateEventLogBasedOnAgentAction_IntentAdd() {
    AgentUpdateRequest agentUpdateRequest = new AgentUpdateRequest();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    agentUpdateRequest.setAgentAction(Commons.AgentAction.INTENT_ADD);
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setIntentId("INTENT_ADD");
    payload.setInstanceId("test-instance-id");
    agentUpdateRequest.setAgentRequestPayload(payload);
    summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);

    assertEquals(1, eventData.size());
    assertEquals("INTENT_ADD", eventData.get(0).getEventId());
  }

  @Test
  @Order(10)
  void testCreateEventLogBasedOnAgentAction_IntentDelete() {
    AgentUpdateRequest agentUpdateRequest = new AgentUpdateRequest();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    agentUpdateRequest.setAgentAction(Commons.AgentAction.INTENT_DELETE);
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setIntentId("INTENT_DELETE");
    payload.setInstanceId("test-instance-id");
    agentUpdateRequest.setAgentRequestPayload(payload);

    summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);

    assertEquals(1, eventData.size());
    assertEquals("INTENT_DELETE", eventData.get(0).getEventId());
  }

  @Test
  @Order(11)
  void testCreateEventLogBasedOnAgentAction_AddAgentStep() {
    AgentUpdateRequest agentUpdateRequest = AgentUpdateRequest.builder().build();
    AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
        AgentUpdateRequest.AgentStepUpdate.builder().id("ADD_AGENT_STEP").value("create").build();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    agentUpdateRequest.setAgentAction(Commons.AgentAction.ADD_AGENT_STEP);
    AgentRequestPayload payload = new AgentRequestPayload();
    agentUpdateRequest.setAgentStepUpdate(agentStepUpdate);
    agentUpdateRequest.setAgentStepUpdate(agentStepUpdate);
    agentUpdateRequest.setAgentRequestPayload(payload);

    summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);

    assertEquals(1, eventData.size());
    assertEquals("create", eventData.get(0).getEventId());
  }

  @Test
  @Order(12)
  void testCreateEventLogBasedOnAgentAction_DeleteAgentStep() {
    AgentUpdateRequest agentUpdateRequest = AgentUpdateRequest.builder().build();
    AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
        AgentUpdateRequest.AgentStepUpdate.builder().id("DELETE_AGENT_STEP").value("delete")
            .build();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    agentUpdateRequest.setAgentAction(Commons.AgentAction.DELETE_AGENT_STEP);
    AgentRequestPayload payload = new AgentRequestPayload();
    agentUpdateRequest.setAgentStepUpdate(agentStepUpdate);
    agentUpdateRequest.setAgentStepUpdate(agentStepUpdate);
    agentUpdateRequest.setAgentRequestPayload(payload);

    summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);

    assertEquals(1, eventData.size());
    assertEquals("delete", eventData.get(0).getEventId());
  }

  @Test
  @Order(13)
  void testCreateEventLogBasedOnAgentAction_UpdateAgentStep() {
    AgentUpdateRequest agentUpdateRequest = AgentUpdateRequest.builder().build();
    AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
        AgentUpdateRequest.AgentStepUpdate.builder().id("UPDATE_AGENT_STEP").value("update")
            .build();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    agentUpdateRequest.setAgentAction(Commons.AgentAction.UPDATE_AGENT_STEP);
    AgentRequestPayload payload = new AgentRequestPayload();
    agentUpdateRequest.setAgentStepUpdate(agentStepUpdate);
    agentUpdateRequest.setAgentStepUpdate(agentStepUpdate);
    agentUpdateRequest.setAgentRequestPayload(payload);

    summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);

    assertEquals(1, eventData.size());
    assertEquals("update", eventData.get(0).getEventId());
  }

  @Test
  @Order(14)
  void testCreateEventLogBasedOnAgentAction_IntentEdit() {
    AgentUpdateRequest agentUpdateRequest = new AgentUpdateRequest();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    agentUpdateRequest.setAgentAction(Commons.AgentAction.INTENT_EDIT);
    AgentRequestPayload payload = new AgentRequestPayload();
    payload.setIntentId("INTENT_EDIT");
    payload.setInstanceId("test-instance-id");
    agentUpdateRequest.setAgentRequestPayload(payload);
    summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);

    assertEquals(1, eventData.size());
    assertEquals("INTENT_EDIT", eventData.get(0).getEventId());
  }

  @Test
  @Order(16)
  void testSaveSummaryEventDoc_WhenFeatureFlagDisabled() {
    SummaryUpdateEventLog summaryUpdateEventLog = SummaryUpdateEventLog.builder().build();
    when(launchDarklyHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
        LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(false);

    summaryHelper.saveSummaryEventDoc(summaryUpdateEventLog);

    verify(summaryRuntimeDao, never()).saveSummaryEventDoc(summaryUpdateEventLog);
    verify(launchDarklyHelper, times(1)).isFFEnabled(
        LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES, LaunchDarklyConstants.FF_CONTEXT_KIND);
  }

  @Test
  @Order(17)
  void testSaveSummaryEventDoc_WhenFeatureFlagEnabled() {
    SummaryUpdateEventLog summaryUpdateEventLog = SummaryUpdateEventLog.builder().build();

    when(launchDarklyHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
        LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(true);

    summaryHelper.saveSummaryEventDoc(summaryUpdateEventLog);

    verify(summaryRuntimeDao, times(1)).saveSummaryEventDoc(summaryUpdateEventLog);
  }

  @Test
  @Order(19)
  void testCreateEventLogBasedOnAgentAction_InvalidAction() {
    AgentUpdateRequest agentUpdateRequest = new AgentUpdateRequest();
    List<SummaryUpdateEventLog.EventData> eventData = new ArrayList<>();
    agentUpdateRequest.setAgentAction(Commons.AgentAction.INVALID);

    summaryHelper.createEventLogBasedOnAgentAction(agentUpdateRequest, eventData);

    assertTrue(true);
  }

  @Test
  @Order(20)
  void testHandleIntentDeleteByAgent_WhenIntentSummaryNotFound() {
    AgentRequestPayload agentRequestPayload = mock(AgentRequestPayload.class);
    when(agentRequestPayload.getConversationId()).thenReturn("conv-123");
    when(agentRequestPayload.getInstanceId()).thenReturn("instance-123");
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getIntentSummaries()).thenReturn(new HashMap<>());

    assertThrows(CustomException.class, () -> {
      summaryHelper.handleIntentDeleteByAgent(agentRequestPayload, null, 0L, null);
      verify(summaryRuntimeDao, never()).saveSummaryDoc(any(SummaryDoc.class));
    });
  }

  @Test
  @Order(21)
  void testGetSummaryUpdateEventLog_WithValidInput() {
    String conversationId = "conv-123";
    long eventTime = 1234567890L;
    String eventType = "SUMMARY_UPDATED";
    List<SummaryUpdateEventLog.EventData> eventDataList = new ArrayList<>();
    eventDataList.add(SummaryUpdateEventLog.EventData.builder().value("value1").build());
    String summaryString = "This is a summary.";
    String businessId = "business-456";

    SummaryUpdateEventLog result =
        summaryHelper.getSummaryUpdateEventLog(conversationId, eventTime, eventType, eventDataList,
            summaryString, businessId);

    assertEquals(eventTime, result.getEventTime());
    assertEquals(eventType, result.getEventType());
    assertEquals("dev", result.getEnvironment());
    assertEquals("3e37631c-78ac-4137-8415-72dcd90b19fd", result.getTenantId());
    assertEquals(conversationId, result.getConversationId());
    assertEquals(summaryString, result.getSummary());
    assertEquals(businessId, result.getBusinessProcessId());
  }

  @Test
  @Order(22)
  void testGetSummaryDocsByExtConversationIds() {
    List<String> sessionIds = Arrays.asList("conv-1", "conv-2", "conv-3");
    List<SummaryDoc> expectedSummaryDocs = Arrays.asList(
        SummaryDoc.builder().conversationId("conv-1").extConversationId("session-1").build(),
        SummaryDoc.builder().conversationId("conv-1").extConversationId("session-1").build(),
        SummaryDoc.builder().conversationId("conv-1").extConversationId("session-1").build());

    when(summaryRuntimeDao.getSummaryDocsByExtConversationIds(sessionIds)).thenReturn(
        expectedSummaryDocs);

    List<SummaryDoc> actualSummaryDocs =
        summaryHelper.getSummaryDocsByExtConversationIds(sessionIds);

    assertEquals(expectedSummaryDocs, actualSummaryDocs);
    verify(summaryRuntimeDao, times(1)).getSummaryDocsByExtConversationIds(sessionIds);
  }

  @Test
  @Order(23)
  void testRemoveGenAISummaryRequest_WhenConversationIdIsValid() {
    String conversationId = "conv-123";

    summaryHelper.removeGenAISummaryRequest(conversationId);

    verify(summaryCacheHelper, times(1)).removeGenAIRequested(conversationId);
  }

  @Test
  @Order(24)
  void testRemoveGenAISummaryRequest_WhenConversationIdIsNull() {
    summaryHelper.removeGenAISummaryRequest(null);

    verify(summaryCacheHelper, never()).removeGenAIRequested(anyString());
  }

  @Test
  @Order(25)
  void testHandleIntentDeleteByAgent() {
    AgentRequestPayload agentRequestPayload =
        AgentRequestPayload.builder().conversationId("convId").instanceId("instance789")
            .businessProcessId("bpId").build();
    Result result = getResult();
    SummaryDoc summaryDoc = buildSummaryDoc(result);
    SummaryUpdateEventLog.EventData eventData =
        SummaryUpdateEventLog.EventData.builder().value("value123").eventId("event456")
            .instanceId("instance789").effectedIntents(Arrays.asList("intent1", "intent2"))
            .headerEffected(false).detectedBy("user").build();

    when(intentTemplateCacheHelper.getCurrentIntentForConversation(any())).thenReturn("convId");
    when(summaryCacheHelper.find(any())).thenReturn(summaryDoc);

    summaryHelper.handleIntentDeleteByAgent(agentRequestPayload, Timestamp.getDefaultInstance(),
        System.currentTimeMillis(), Arrays.asList(eventData));

    verify(summaryCacheHelper, times(1)).find(any());
    verify(intentTemplateCacheHelper, times(1)).getCurrentIntentForConversation("convId");
  }

  @Test
  @Order(26)
  void testHandleIntentAddByAgent() {
    AgentRequestPayload agentRequestPayload =
        AgentRequestPayload.builder().conversationId("convId").instanceId("instance789")
            .businessProcessId("bpId").build();
    Result result = getResult();
    SummaryDoc summaryDoc = buildSummaryDoc(result);
    SummaryUpdateEventLog.EventData eventData =
        SummaryUpdateEventLog.EventData.builder().value("value123").eventId("event456")
            .instanceId("instance789").effectedIntents(Arrays.asList("intent1", "intent2"))
            .headerEffected(false).detectedBy("user").build();

    when(summaryCacheHelper.find(any())).thenReturn(summaryDoc);

    summaryHelper.handleIntentAddByAgent(agentRequestPayload, Timestamp.getDefaultInstance(),
        System.currentTimeMillis(), Collections.singletonList(eventData));

    verify(summaryCacheHelper, times(1)).find(any());
  }

  @Test
  @Order(27)
  void testHandleSummaryTemplateEdits() {
    AgentRequestPayload agentRequestPayload =
        AgentRequestPayload.builder().conversationId("convId").instanceId("instance789")
            .businessProcessId("bpId").intentSummary("intent-summary").entityEditPayloads(
                Arrays.asList(
                    AgentRequestPayload.EntityEditPayload.builder().editedComponentId("componentId")
                        .editedValue("editedValue").isEntity(true).build())).build();
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(launchDarklyHelper.isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE,
        FF_CONTEXT_KIND)).thenReturn(true);
    when(summaryCacheHelper.find(any())).thenReturn(summaryDoc);

    summaryHelper.handleSummaryTemplateEdits(agentRequestPayload, Commons.AgentAction.INTENT_EDIT,
        Timestamp.getDefaultInstance(), System.currentTimeMillis(), new ArrayList<>());

    verify(summaryCacheHelper, times(1)).find(any());
    verify(launchDarklyHelper, times(1)).isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE,
        FF_CONTEXT_KIND);
  }

  @Test
  void testHandleSummaryTemplateEdits1() {
    AgentRequestPayload agentRequestPayload =
        AgentRequestPayload.builder().conversationId("convId").instanceId("instance789")
            .businessProcessId("bpId").intentSummary("intent-summary").entityEditPayloads(
                Arrays.asList(
                    AgentRequestPayload.EntityEditPayload.builder().editedComponentId("componentId")
                        .editedValue("editedValue").isEntity(true).build())).build();
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(launchDarklyHelper.isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE,
        FF_CONTEXT_KIND)).thenReturn(true);
    when(summaryCacheHelper.find(any())).thenReturn(summaryDoc);

    try (MockedStatic<ProtoConverter> mockedStatic = mockStatic(ProtoConverter.class)) {
      mockedStatic.when(() -> ProtoConverter.headerTemplateToSummaryProto(any(), any(), any()))
          .thenReturn(mock(Summary.class));
      summaryHelper.handleSummaryTemplateEdits(agentRequestPayload, Commons.AgentAction.HEADER_EDIT,
          Timestamp.getDefaultInstance(), System.currentTimeMillis(), new ArrayList<>());
    }
    verify(summaryCacheHelper, times(1)).find(any());
    verify(launchDarklyHelper, times(1)).isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE,
        FF_CONTEXT_KIND);
  }

  @Test
  void testHandleSummaryTemplateEdits2() {
    AgentRequestPayload agentRequestPayload =
        AgentRequestPayload.builder().conversationId("convId").instanceId("instance789")
            .businessProcessId("bpId").intentSummary("intent-summary").entityEditPayloads(
                Collections.singletonList(
                    AgentRequestPayload.EntityEditPayload.builder().editedComponentId("componentId")
                        .editedValue("editedValue").isEntity(true).build())).genAiSummaryDoc(
                GenAiSummaryDoc.builder().sections(
                    List.of(GenAiSummaryDoc.Section.builder().id("summary").text("summary").build(),
                        GenAiSummaryDoc.Section.builder().id("customer-sentiment")
                            .text("customer sentiment").build())).build()).build();
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);
    when(launchDarklyHelper.isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE,
        FF_CONTEXT_KIND)).thenReturn(true);
    when(launchDarklyHelper.isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
        FF_CONTEXT_KIND)).thenReturn(true);
    when(summaryCacheHelper.find(any())).thenReturn(summaryDoc);
    try (MockedStatic<DocConverter> mockedStatic = mockStatic(DocConverter.class);
        MockedStatic<ProtoConverter> protoMockedStatic = mockStatic(ProtoConverter.class);) {
      protoMockedStatic.when(() -> ProtoConverter.intentTemplateToSummaryProto(any(), any(), any()))
          .thenReturn(mock(Summary.class));
      mockedStatic.when(
          () -> DocConverter.agentAgentEditsDoc((SummaryDoc) any(), (AgentRequestPayload) any(),
              any(), any())).thenReturn(mock(AgentEditsDoc.class));

      summaryHelper.handleSummaryTemplateEdits(agentRequestPayload,
          Commons.AgentAction.GEN_AI_SUMMARY_EDIT, Timestamp.getDefaultInstance(),
          System.currentTimeMillis(), new ArrayList<>());
    }
  }

  @Test
  void testHandleSummaryTemplateEdits_WithNullAgentRequestPayload(){
  AgentRequestPayload.EntityEditPayload entityEditPayload1 =
      AgentRequestPayload.EntityEditPayload.builder().editedComponentId("componentId")
          .editedValue("editedValue").isEntity(true).build();
    AgentRequestPayload.EntityEditPayload entityEditPayload2 = null;
    GenAiSummaryDoc.Section section =
        GenAiSummaryDoc.Section.builder().text("text").id("123").build();
    List<GenAiSummaryDoc.Section> sections = new ArrayList<>();
    sections.add(section);
    AgentRequestPayload agentRequestPayload =
        AgentRequestPayload.builder().conversationId("convId").instanceId("instance789")
            .genAiSummaryDoc(GenAiSummaryDoc.builder().sections(sections).build())
            .businessProcessId("bpId").intentSummary("intent-summary").entityEditPayloads(Arrays.asList(entityEditPayload1,entityEditPayload2)).build();
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(launchDarklyHelper.isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE,
        FF_CONTEXT_KIND)).thenReturn(true);
    when(launchDarklyHelper.isFFEnabled(SUMMARY_EVENT_LOG_ENABLES, FF_CONTEXT_KIND)).thenReturn(
        true);
    when(summaryCacheHelper.find(any())).thenReturn(summaryDoc);
      summaryHelper.handleSummaryTemplateEdits(agentRequestPayload,
          Commons.AgentAction.GEN_AI_SUMMARY_EDIT, Timestamp.getDefaultInstance(),
          System.currentTimeMillis(), new ArrayList<>());

    verify(this.agentEditsRepository).save(any());
    verify(this.summaryRuntimeDao,times(3)).saveSummaryDoc(summaryDoc);

  }

  @Test
  @Order(28)
  void testHandleAgentNotesUpdate() {
    AgentRequestPayload agentRequestPayload =
        AgentRequestPayload.builder().conversationId("convId").instanceId("instance789")
            .businessProcessId("bpId").intentSummary("intent-summary").entityEditPayloads(
                Arrays.asList(
                    AgentRequestPayload.EntityEditPayload.builder().editedComponentId("componentId")
                        .editedValue("editedValue").isEntity(true).build())).build();
    Result result = getResult();
    SummaryDoc summaryDoc = buildSummaryDoc(result);

    when(summaryCacheHelper.find(any())).thenReturn(summaryDoc);

    summaryHelper.handleAgentNotesUpdate(agentRequestPayload, System.currentTimeMillis(),
        new ArrayList<>());

    verify(summaryCacheHelper, times(1)).find(any());
  }

  @Test
  @Order(29)
  @Disabled
  void testHandleIntentTemplateUpdate_IfFFEnable() {
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(launchDarklyHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND)).thenReturn(
        true);

    summaryHelper.handleIntentTemplateUpdate("convId", new HashMap<>(), new ArrayList<>(),
        "entity1", "componentValue", "ENTITY", summaryDoc, "instance789", true);

    verify(launchDarklyHelper, times(1)).isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND);
  }

  @Test
  @Order(30)
  @Disabled
  void testHandleIntentTemplateUpdate_IfFFEnable_VariableUpdate() {
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(launchDarklyHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND)).thenReturn(
        true);

    summaryHelper.handleIntentTemplateUpdate("convId", new HashMap<>(), new ArrayList<>(),
        "intent1", "componentValue", "VARIABLE", summaryDoc, "instance789", false);

    verify(launchDarklyHelper, times(1)).isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND);
  }

  @Test
  @Order(31)
  @Disabled
  void testHandleIntentTemplateUpdate_IfFFDisable() {
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(launchDarklyHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND)).thenReturn(
        false);

    summaryHelper.handleIntentTemplateUpdate("convId", new HashMap<>(), new ArrayList<>(),
        "entity1", "componentValue", "ENTITY", summaryDoc, "instance789", true);

    verify(launchDarklyHelper, times(1)).isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND);
  }

  @Test
  @Order(32)
  @Disabled
  void testUpdateHeaderSummary_EntityUpdate() {
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    Header response =
        summaryHelper.updateHeaderSummary(summaryDoc, "id", "value", "instance789", "ENTITY",
            Timestamp.getDefaultInstance());

    assertNotNull(response);
  }

  @Test
  @Order(33)
  @Disabled
  void testUpdateHeaderSummary_VariableUpdate() {
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    Header response =
        summaryHelper.updateHeaderSummary(summaryDoc, "id", "value", "instance789", "VARIABLE",
            Timestamp.getDefaultInstance());

    assertNotNull(response);
  }

  @Test
  @Order(34)
  void testUpdateHeaderSummary_Negative_1() {
    SummaryDoc summaryDoc = SummaryDoc.builder().headerSummary(null).build();

    Header response =
        summaryHelper.updateHeaderSummary(summaryDoc, "id", "value", "instance789", "variable",
            Timestamp.getDefaultInstance());

    assertNull(response);
  }

  @Test
  @Order(35)
  void testUpdateGenAISummary() {
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(summaryCacheHelper.find(any())).thenReturn(summaryDoc);

    boolean response =
        summaryHelper.updateGenAISummary(summaryDoc, "varId", "value", "instance789", true);

    assertTrue(response);
  }

  @Test
  @Order(36)
  void testUpdateGenAISummary_Negative_1() {
    SummaryDoc summaryDoc = SummaryDoc.builder().genAiSummary(null).build();

    boolean response =
        summaryHelper.updateGenAISummary(summaryDoc, "varId", "value", "instance789", true);

    assertFalse(response);
  }

  @Test
  @Order(37)
  void testHandleSummaryTemplateEdits_IfFFEnable() {
    AgentRequestPayload agentRequestPayload =
        AgentRequestPayload.builder().conversationId("convId").instanceId("instance789")
            .businessProcessId("bpId").intentSummary("intent-summary")
            .entityEditPayloads(new ArrayList<>()).build();
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(launchDarklyHelper.isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE,
        FF_CONTEXT_KIND)).thenReturn(true);
    when(summaryCacheHelper.find(any())).thenReturn(summaryDoc);

    summaryHelper.handleSummaryTemplateEdits(agentRequestPayload, Commons.AgentAction.INTENT_EDIT,
        Timestamp.getDefaultInstance(), System.currentTimeMillis(), new ArrayList<>());

    verify(summaryCacheHelper, times(1)).find(any());
    verify(launchDarklyHelper, times(1)).isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE,
        FF_CONTEXT_KIND);
  }

  @Test
  @Order(37)
  void testHandleSummaryTemplateEdits_IfFFEnableUASSIST_AGENT_EDIT_TEMPLATE_UPDATE_false() {
    AgentRequestPayload agentRequestPayload =
        AgentRequestPayload.builder().conversationId("convId").instanceId("instance789")
            .businessProcessId("bpId").intentSummary("intent-summary")
            .entityEditPayloads(new ArrayList<>()).build();
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(launchDarklyHelper.isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE,
        FF_CONTEXT_KIND)).thenReturn(false);
    when(summaryCacheHelper.find(any())).thenReturn(summaryDoc);

    summaryHelper.handleSummaryTemplateEdits(agentRequestPayload, Commons.AgentAction.INTENT_EDIT,
        Timestamp.getDefaultInstance(), System.currentTimeMillis(), new ArrayList<>());

    verify(summaryCacheHelper, times(1)).find(any());
    verify(launchDarklyHelper, times(1)).isFFEnabled(UASSIST_AGENT_EDIT_TEMPLATE_UPDATE,
        FF_CONTEXT_KIND);
  }

  @Test
  @Order(38)
  void testCheckAndUpdateGenAISummary() {
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    Map<String, List<GenAISummaryCache.SpokenEntity>> map = new HashMap<>();
    map.put("call-outcome", Arrays.asList(
        GenAISummaryCache.SpokenEntity.builder().label("name").variableId("varId").build()));
    GenAISummaryCache genAISummaryCache = GenAISummaryCache.builder().sectionMap(map).build();

    when(genAiSummaryConfigCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
        Optional.of(genAISummaryCache));

    boolean response =
        summaryHelper.checkAndUpdateGenAISummary(summaryDoc, "expId", "varId", "john", "1234",
            true);

    assertTrue(response);
    verify(genAiSummaryConfigCacheHelper, times(1)).getGenAIConfigCacheByExperience("expId");
  }

  @Test
  @Order(39)
  void testCheckAndUpdateGenAISummary_VariableIdNotContains() {
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    Map<String, List<GenAISummaryCache.SpokenEntity>> map = new HashMap<>();
    map.put("call-outcome", Arrays.asList(
        GenAISummaryCache.SpokenEntity.builder().label("name").variableId("varId").build()));
    GenAISummaryCache genAISummaryCache = GenAISummaryCache.builder().sectionMap(map).build();

    when(genAiSummaryConfigCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
        Optional.of(genAISummaryCache));

    boolean response =
        summaryHelper.checkAndUpdateGenAISummary(summaryDoc, "expId", "varIdNew", "john", "1234",
            true);

    assertFalse(response);
    verify(genAiSummaryConfigCacheHelper, times(1)).getGenAIConfigCacheByExperience("expId");
  }

  @Test
  @Order(40)
  void testCheckAndUpdateGenAISummary_GenAiSummaryNotPresentInCache() {
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(genAiSummaryConfigCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
        Optional.empty());

    boolean response =
        summaryHelper.checkAndUpdateGenAISummary(summaryDoc, "expId", "varId", "john", "12345",
            true);

    assertFalse(response);
    verify(genAiSummaryConfigCacheHelper, times(1)).getGenAIConfigCacheByExperience("expId");
  }

  @Test
  @Order(41)
  void testCheckAndUpdateGenAISummary_GenAiSummaryNotPresentInCache_noSections() {
    Result result = getResult();
    SummaryDoc summaryDoc = getSummaryDoc(result);

    when(genAiSummaryConfigCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
        Optional.of(GenAISummaryCache.builder().sectionMap(null).build()));

    boolean response =
        summaryHelper.checkAndUpdateGenAISummary(summaryDoc, "expId", "varId", "john", "12345",
            true);

    assertFalse(response);
    verify(genAiSummaryConfigCacheHelper, times(1)).getGenAIConfigCacheByExperience("expId");
  }

  @Test
  @Order(41)
  @Disabled
  void testgetIntentTemplate() {
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

    when(intentTemplateCacheHelper.getIntentTemplate("int1")).thenReturn(null);
    String intentTemplate = summaryHelper.getIntentTemplate("int1", "tenant1", "dev", "conv1",
        SummaryDoc.builder().entities(entities).variables(entities).build(), true);
    assertNull(intentTemplate);
  }

  @Order(42)
  @Test
  void publishNoLog_PublishesSummarySuccessfully() {
    Summary summaryProto = Summary.newBuilder().setConversationId("conv-123").build();
    String eventType = "SUMMARY_UPDATED";

    summaryHelper.publishNoLog(summaryProto, eventType);

    verify(messagePublisher, times(1)).summaryUpdate(summaryProto, eventType);
  }

  @Order(43)
  @Test
  void publishNoLog_HandlesExceptionGracefully() {
    Summary summaryProto = Summary.newBuilder().setConversationId("conv-123").build();
    String eventType = "SUMMARY_UPDATED";
    doThrow(new RuntimeException("Test Exception")).when(messagePublisher)
        .summaryUpdate(any(), any());

    summaryHelper.publishNoLog(summaryProto, eventType);

    verify(messagePublisher, times(1)).summaryUpdate(summaryProto, eventType);
    //    verify(log, times(1)).error(eq("error at save and publish summary: event: {}, error: "), eq(eventType), any(Exception.class));
  }

  @Test
  @Order(44)
  void testSummaryDocByConvId_success() {
    String conversationId = "conv-123";
    when(this.summaryCacheHelper.find(conversationId)).thenReturn(SummaryDoc.builder().build());
    Optional<SummaryDoc> summaryDoc1=summaryHelper.getSummaryDocById(conversationId);
    assertTrue(summaryDoc1.isPresent());
  }

  @Test
  @Order(45)
  void testSummaryDocByConvId_success_cache_empty() {
    String conversationId = "conv-123";
    when(this.summaryCacheHelper.find(conversationId)).thenReturn(null);
    when(this.summaryRuntimeDao.getSummaryDoc(conversationId)).thenReturn(Optional.of(SummaryDoc.builder().build()));
    Optional<SummaryDoc> summaryDoc1=summaryHelper.getSummaryDocById(conversationId);
    assertTrue(summaryDoc1.isPresent());
  }

  @Test
  @Order(46)
  void testSummaryDocByConvId_no_summary() {
    String conversationId = "conv-123";
    when(this.summaryCacheHelper.find(conversationId)).thenReturn(null);
    when(this.summaryRuntimeDao.getSummaryDoc(conversationId)).thenReturn(Optional.empty());
    Optional<SummaryDoc> summaryDoc1=summaryHelper.getSummaryDocById(conversationId);
    assertFalse(summaryDoc1.isPresent());
  }

  private SummaryDoc buildSummaryDoc(Result result) {
    return SummaryDoc.builder().conversationId("conv123").tenantId("tenant456").environment("prod")
        .businessProcessId("bp789").language("en").experienceId("exp101")
        .headerSummary(new HeaderSummaryDoc()).intentSummaries(result.systemIntentSummaries)
        .entities(result.entities).variables(result.variables).agentNotes(new AgentNotesDoc())
        .participants(Arrays.asList(
            ParticipantDoc.builder().participantId("participantId").type("agent")
                .ctiAgentId("ctiAgentId").build(),
            ParticipantDoc.builder().participantId("participantId").type("customer")
                .ctiAgentId("ctiCustId").build())).timestamp(new TimeStampDoc()).genAiSummary(
            GenAiSummaryDoc.builder().sections(Arrays.asList(
                GenAiSummaryDoc.Section.builder().id("call-outcome").text("genai-summary")
                    .spokenEntities(Arrays.asList(
                        GenAiSummaryDoc.SpokenEntity.builder().variableInstanceId("1243")
                            .isEdited(false).label("name").value("value").variableId("varId")
                            .placement("inline").build())).build())).build())
        .genAiGeneratedSummary(new GenAiSummaryDoc())
        .systemIntentSummaries(result.systemIntentSummaries).extConversationId("extConv123")
        .build();
  }

  private static @NotNull Result getResult() {
    Map<String, Boolean> map = new HashMap<>();
    map.put("intent1", true);
    Map<String, String> entityInstanceIdMap = new HashMap<>();
    entityInstanceIdMap.put("entity1", "instance789");
    Map<String, String> variableInstanceIdMap = new HashMap<>();
    variableInstanceIdMap.put("variable", "instance789");
    Map<String, IntentSummaryDoc> systemIntentSummaries = new HashMap<>();
    systemIntentSummaries.put("instance789", IntentSummaryDoc.builder()
        .template("<span entity-id=\"entity1\" class=\"uniphore-entity-name\">customer name</span>")
        .instanceId("instance789").intentId("intentId").entityInstanceIdMap(entityInstanceIdMap)
        .entityIdIsEdited(map).variableInstanceIdMap(variableInstanceIdMap)
        .timestamp(TimeStampDoc.builder().nanos(28199).build()).source("agent").build());
    Map<String, SummaryDoc.Slot> entities = new HashMap<>();
    entities.put("variable1",
        SummaryDoc.Slot.builder().id("entity1").value("value").instanceId("instance789").build());
    Map<String, SummaryDoc.Slot> variables = new HashMap<>();
    variables.put("variable1",
        SummaryDoc.Slot.builder().id("varId").value("value").instanceId("instance789").build());
    Result result = new Result(systemIntentSummaries, entities, variables);
    return result;
  }

  private static class Result {
    public final Map<String, IntentSummaryDoc> systemIntentSummaries;
    public final Map<String, SummaryDoc.Slot> entities;
    public final Map<String, SummaryDoc.Slot> variables;

    public Result(Map<String, IntentSummaryDoc> systemIntentSummaries,
        Map<String, SummaryDoc.Slot> entities, Map<String, SummaryDoc.Slot> variables) {
      this.systemIntentSummaries = systemIntentSummaries;
      this.entities = entities;
      this.variables = variables;
    }
  }

  private static SummaryDoc getSummaryDoc(Result result) {
    Map<String, String> entityInstanceIdMap = new HashMap<>();
    entityInstanceIdMap.put("entity1", "instance789");
    Map<String, String> variableInstanceIdMap = new HashMap<>();
    variableInstanceIdMap.put("variable", "instance789");
    SummaryDoc summaryDoc =
        SummaryDoc.builder().conversationId("conv123").tenantId("tenant456").environment("prod")
            .businessProcessId("bp789").language("en").experienceId("exp101").headerSummary(
                HeaderSummaryDoc.builder().template(
                        "<span entity-id=\"1\" class=\"uniphore-entity-name\">customer name</span>")
                    .plainText("customer name").timestamp(TimeStampDoc.builder().nanos(218991).build())
                    .entityInstanceIdMap(entityInstanceIdMap)
                    .variableInstanceIdMap(variableInstanceIdMap).build())
            .intentSummaries(result.systemIntentSummaries).entities(result.entities)
            .variables(result.variables).agentNotes(new AgentNotesDoc()).participants(Arrays.asList(
                ParticipantDoc.builder().participantId("participantId").type("agent")
                    .ctiAgentId("ctiAgentId").build(),
                ParticipantDoc.builder().participantId("participantId").type("customer")
                    .ctiAgentId("ctiCustId").build())).timestamp(new TimeStampDoc()).genAiSummary(
                GenAiSummaryDoc.builder().sections(Arrays.asList(
                    GenAiSummaryDoc.Section.builder().id("call-outcome").text("genai-summary")
                        .spokenEntities(Arrays.asList(
                            GenAiSummaryDoc.SpokenEntity.builder().variableInstanceId("1243")
                                .isEdited(false).label("name").value("value").variableId("varId")
                                .placement("inline").build())).build())).build())
            .genAiGeneratedSummary(new GenAiSummaryDoc())
            .systemIntentSummaries(result.systemIntentSummaries).extConversationId("extConv123")
            .build();
    return summaryDoc;
  }

  @Test
  @Disabled
  void testHandleIntentTemplateUpdateFFEnabled() {

    String sessionId = "session1";
    String currentIntent = "intent1";
    String componentId = "component1";
    String componentValue = "value1";
    String type = "type1";
    boolean isAgentEdit = false;
    TimeStampDoc timestamp = mock(TimeStampDoc.class);

    Map<String, Boolean> entityIdIsEdited = new HashMap<>();
    Map<String, String> entityInstanceIdMap = new HashMap<>();
    Map<String, String> variableInstanceIdMap = new HashMap<>();
    Map<String, IntentSummary> intentSummaryMap = new HashMap<>();
    List<String> effectedIntents = new ArrayList<>();

    SummaryDoc summaryDoc = new SummaryDoc();
    summaryDoc.setIntentSummaries(new HashMap<>());
    summaryDoc.setSystemIntentSummaries(new HashMap<>());

    IntentSummaryDoc systemIntentSummary = new IntentSummaryDoc();
    systemIntentSummary.setTemplate("template1");
    summaryDoc.getSystemIntentSummaries().put(currentIntent, systemIntentSummary);

    IntentSummaryDoc intentSummary = new IntentSummaryDoc();
    intentSummary.setIntentId("intent1");
    intentSummary.setEntityIdIsEdited(entityIdIsEdited);
    intentSummary.setEntityInstanceIdMap(entityInstanceIdMap);
    intentSummary.setVariableInstanceIdMap(variableInstanceIdMap);
    intentSummary.setInstanceId("instance1");
    intentSummary.setTemplate("template1");
    intentSummary.setTimestamp(timestamp);
    summaryDoc.getIntentSummaries().put(currentIntent, intentSummary);

    when(launchDarklyHelper.isFFEnabled("SYSTEM_TEMPLATE_SUMMARY_FLAG",
        "FF_CONTEXT_KIND")).thenReturn(true);

    assertThrows(NullPointerException.class,
        () -> summaryHelper.handleIntentTemplateUpdate(sessionId, intentSummaryMap, effectedIntents,
            componentId, componentValue, type, summaryDoc, currentIntent, isAgentEdit));
  }

  static Stream<Arguments> summaryTemplateProvider() {
    return Stream.of(Arguments.of(SummaryTemplate.builder().build(), List.of(
                ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                    .message("Conversation ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                    .message("Summary Template ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                    .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is empty, Summary Template ID is empty and Version is zero"),
        Arguments.of(SummaryTemplate.builder().conversationId(null).build(), List.of(
                ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                    .message("Conversation ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                    .message("Summary Template ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                    .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is null, Summary Template ID is empty and Version is zero"),
        Arguments.of(SummaryTemplate.builder().summaryTemplateId(null).build(), List.of(
                ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                    .message("Conversation ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                    .message("Summary Template ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                    .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is empty, Summary Template ID is null and Version is zero"),
        Arguments.of(SummaryTemplate.builder().conversationId(null).summaryTemplateId(null).build(),
            List.of(ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                    .message("Conversation ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                    .message("Summary Template ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                    .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is null, Summary Template ID is null and Version is zero"),
        Arguments.of(SummaryTemplate.builder().conversationId("null").build(), List.of(
                ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                    .message("Conversation ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                    .message("Summary Template ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                    .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is null string, Summary Template ID is empty and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("null").summaryTemplateId("null").build(),
            List.of(ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                    .message("Conversation ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                    .message("Summary Template ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                    .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is null string, Summary Template ID is null string and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("undefined").summaryTemplateId("null").build(),
            List.of(ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                    .message("Conversation ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                    .message("Summary Template ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                    .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is undefined string, Summary Template ID is null string and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("undefined").summaryTemplateId("undefined")
                .build(), List.of(ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                    .message("Conversation ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                    .message("Summary Template ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                    .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is undefined string, Summary Template ID is undefined string and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
                .build(), List.of(ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                    .message("Summary Template ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                    .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is valid, Summary Template ID is empty and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
                .build(), List.of(ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                    .message("Conversation ID is empty").build(),
                ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                    .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is empty, Summary Template ID is valid and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
                .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(-1).build(),
            List.of(ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                .message("Version should be a whole number and greater than zero").build()),
            "Conversation ID is valid, Summary Template ID is valid and Version is -1"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
                .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(1).build(),
            new ArrayList<>(),
            "Conversation ID is valid, Summary Template ID is valid and Version is valid"));
  }

  @ParameterizedTest(name = "{index} => {2}")
  @MethodSource("summaryTemplateProvider")
  void test_Validate_UpdateSummaryTemplate_Request(SummaryTemplate request,
      List<ErrorResponse> expectedResponses, String testCaseName) {
    final List<ErrorResponse> responses =
        SummaryHelper.validateUpdateSummaryTemplateRequest(request);
    if (testCaseName.equalsIgnoreCase(
        "Conversation ID is valid, Summary Template ID is valid and Version is valid")) {
      assertEquals(0, responses.size());
      return;
    }
    for (int i = 0; i < responses.size(); i++) {
      assertEquals(expectedResponses.get(i).getCode(), responses.get(i).getCode());
      assertEquals(expectedResponses.get(i).getMessage(), responses.get(i).getMessage());
    }
  }

  @Test
  @Order(47)
  void testGetSummaryDocs_AllInCache() {
    List<String> conversationIds = Arrays.asList("conv1", "conv2", "conv3");
    SummaryDoc doc1 = SummaryDoc.builder().conversationId("conv1").build();
    SummaryDoc doc2 = SummaryDoc.builder().conversationId("conv2").build();
    SummaryDoc doc3 = SummaryDoc.builder().conversationId("conv3").build();

    when(summaryCacheHelper.find("conv1")).thenReturn(doc1);
    when(summaryCacheHelper.find("conv2")).thenReturn(doc2);
    when(summaryCacheHelper.find("conv3")).thenReturn(doc3);

    List<SummaryDoc> result = summaryHelper.getSummaryDocs(conversationIds);

    assertEquals(3, result.size());
    assertEquals("conv1", result.get(0).getConversationId());
    assertEquals("conv2", result.get(1).getConversationId());
    assertEquals("conv3", result.get(2).getConversationId());
    verify(summaryCacheHelper, times(1)).find("conv1");
    verify(summaryCacheHelper, times(1)).find("conv2");
    verify(summaryCacheHelper, times(1)).find("conv3");
    verify(summaryRuntimeDao, never()).getSummaryDocs(any());
  }

  @Test
  @Order(48)
  void testGetSummaryDocs_PartiallyInCache() {
    List<String> conversationIds = Arrays.asList("conv1", "conv2", "conv3");
    SummaryDoc doc1 = SummaryDoc.builder().conversationId("conv1").build();
    SummaryDoc doc3FromDb = SummaryDoc.builder().conversationId("conv3").build();

    when(summaryCacheHelper.find("conv1")).thenReturn(doc1);
    when(summaryCacheHelper.find("conv2")).thenReturn(null);
    when(summaryCacheHelper.find("conv3")).thenReturn(null);
    when(summaryRuntimeDao.getSummaryDocs(Arrays.asList("conv2", "conv3"))).thenReturn(
        Arrays.asList(doc3FromDb));

    List<SummaryDoc> result = summaryHelper.getSummaryDocs(conversationIds);

    assertEquals(2, result.size());
    assertEquals("conv1", result.get(0).getConversationId());
    assertEquals("conv3", result.get(1).getConversationId());
    verify(summaryCacheHelper, times(1)).find("conv1");
    verify(summaryCacheHelper, times(1)).find("conv2");
    verify(summaryCacheHelper, times(1)).find("conv3");
    verify(summaryRuntimeDao, times(1)).getSummaryDocs(Arrays.asList("conv2", "conv3"));
  }

}
