package com.uniphore.uassist.summary.util;

import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.llminference.v1.CreateGenerativeSummaryResponse;
import com.uniphore.llminference.v1.PresentationFormat;
import com.uniphore.llminference.v1.Section;
import com.uniphore.llminference.v1.SectionText;
import com.uniphore.summary.v1.Summary;
import com.uniphore.uassist.summary.cache.GenAISummaryCache;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.CustomMetricsPublisher;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.helper.LookerGenAISummaryMetricHelper;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.uassist.summary.common.Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY;
import static com.uniphore.uassist.summary.util.GenAIConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenAiSummaryHelperTest {

  @Mock
  private CustomMetricsPublisher customMetricsPublisher;

  @Mock
  private LaunchDarklyHelper ldHelper;

  @Mock
  private SummaryHelper summaryHelper;

  @InjectMocks
  private GenAiSummaryHelper genAiSummaryHelper;

  @Mock
  private LookerGenAISummaryMetricHelper lookerGenAISummaryMetricHelper;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    genAiSummaryHelper.init();
  }

  @Test
  void persistStreamResponse_shouldAppendTextToExistingSection() {
    String conversationId = "testConversationId";
    genAiSummaryHelper.getGenAiConversationSectionMap()
        .put(conversationId, new HashMap<>(Map.of("section1", "existingText")));
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("section1"))
            .setText("newText").build()).build();
    genAiSummaryHelper.persistStreamResponse(response, conversationId);
    assertEquals("existingTextnewText",
        genAiSummaryHelper.getGenAiConversationSectionMap().get(conversationId).get("section1"));
  }

  @Test
  void persistStreamResponse_shouldAddNewSection() {
    String conversationId = "testConversationId";
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("section2"))
            .setText("newText").build()).build();
    genAiSummaryHelper.persistStreamResponse(response, conversationId);
    assertEquals("newText",
        genAiSummaryHelper.getGenAiConversationSectionMap().get(conversationId).get("section2"));
  }

  @Test
  void createFinalResponse_shouldCreateResponseWithAllSections() {
    Map<String, String> sectionMap = Map.of("section1", "text1", "section2", "text2");
    CreateGenerativeSummaryResponse response = genAiSummaryHelper.createFinalResponse(sectionMap);
    assertEquals(2, response.getSectionTextCount());
    response.getSectionTextList().forEach(s -> {
      if (s.getSection().getId().equals("section1")) {
        assertEquals("text1", s.getText());
      } else if (s.getSection().getId().equals("section2")) {
        assertEquals("text2", s.getText());
      } else {
        fail("Unexpected section id");
      }
    });
  }

  @Test
  void formattedSummaryDoc_shouldReturnFormattedSummary() {
    String conversationId = "testConversationId";
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("section1"))
            .setText("text1").build()).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getConversationId()).thenReturn(conversationId);
    when(config.isFormatEnabled()).thenReturn(true);
    when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    GenAiSummaryDoc result =
        genAiSummaryHelper.formattedSummaryDoc(conversationId, Optional.of(response), config,
            summaryDoc);
    assertNotNull(result);
    assertEquals(1, result.getSections().size());
    assertEquals("section1", result.getSections().get(0).getId());
    assertEquals("<div>text1 </div>", result.getSections().get(0).getText());
  }

  @Test
  void formattedSummaryDoc_shouldReturnUnformattedSummary() {
    String conversationId = "testConversationId";
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("section1"))
            .setText("text1").build()).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getTemplateId()).thenReturn("qfv");
    when(summaryDoc.getConversationId()).thenReturn(conversationId);
    when(config.isFormatEnabled()).thenReturn(true);
    when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
    Mockito.doReturn(true).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    GenAiSummaryDoc result =
        genAiSummaryHelper.formattedSummaryDoc(conversationId, Optional.of(response), config,
            summaryDoc);
    assertNotNull(result);
    assertEquals(1, result.getSections().size());
    assertEquals("section1", result.getSections().get(0).getId());
    assertEquals("text1", result.getSections().get(0).getText());
  }

  @Test
  void processStream_shouldHandleEmptyResponseIterator() {
    genAiSummaryHelper.init();
    Iterator<CreateGenerativeSummaryResponse> responseIterator = Collections.emptyIterator();
    String conversationId = "testConversationId";
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getGenAiSummary()).thenReturn(
        GenAiSummaryDoc.builder().sections(Collections.emptyList()).build());
    when(summaryDoc.getConversationId()).thenReturn("conv1");
    when(config.isFormatEnabled()).thenReturn(true);

    genAiSummaryHelper.processStream(responseIterator, conversationId, config, summaryDoc,
        System.currentTimeMillis());

    verify(summaryDoc, never()).setGenAiSummary(any());
    verify(summaryDoc, never()).setGenAiGeneratedSummary(any());
    verify(summaryHelper, never()).publishNoLog(any(), eq(Constants.GEN_AI));
  }

  @Test
  void processStream_shouldProcessSingleResponse() {
    genAiSummaryHelper.init();
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.CALL_REASON))
                .setText("Call reason text.").build()).build();
    Iterator<CreateGenerativeSummaryResponse> responseIterator = List.of(response).iterator();
    String conversationId = "testConversationId";
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getGenAiSummary()).thenReturn(
        GenAiSummaryDoc.builder().sections(Collections.emptyList()).build());
    when(summaryDoc.getConversationId()).thenReturn("conv1");
    when(config.isFormatEnabled()).thenReturn(true);

    when(config.getSummaryOptIn()).thenReturn(GenAISummaryCache.SummaryOptIn.builder()
        .isAgentStepsEnabled(true).isCallReasonEnabled(true).isOutcomeEnabled(true)
        .isCustomerSatisfactionEnabled(true).build());

    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    genAiSummaryHelper.processStream(responseIterator, conversationId, config, summaryDoc,
        System.currentTimeMillis());

    verify(summaryDoc, atLeastOnce()).setGenAiSummary(any());
    verify(summaryDoc, atLeastOnce()).setGenAiGeneratedSummary(any());
    verify(summaryHelper, atLeastOnce()).publishNoLog(any(), eq(Constants.GEN_AI));
  }
  @Test
  void processStream_toPublish() {
    genAiSummaryHelper.init();
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.CALL_REASON))
                .setText("Call reason text.").build()).build();
    Iterator<CreateGenerativeSummaryResponse> responseIterator = List.of(response).iterator();
    String conversationId = "testConversationId";
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getTemplateId()).thenReturn("templatedId");
    when(summaryDoc.getGenAiSummary()).thenReturn(
        GenAiSummaryDoc.builder().sections(Collections.emptyList()).build());
    when(summaryDoc.getConversationId()).thenReturn("conv1");
    when(config.isFormatEnabled()).thenReturn(true);

    when(config.getSummaryOptIn()).thenReturn(GenAISummaryCache.SummaryOptIn.builder()
        .isAgentStepsEnabled(true).isCallReasonEnabled(true).isOutcomeEnabled(true)
        .isCustomerSatisfactionEnabled(true).build());

    Mockito.doReturn(true).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    genAiSummaryHelper.processStream(responseIterator, conversationId, config, summaryDoc,
        System.currentTimeMillis());

    verify(summaryDoc, atLeastOnce()).setGenAiSummary(any());
    verify(summaryDoc, atLeastOnce()).setGenAiGeneratedSummary(any());
    verify(summaryHelper, atLeastOnce()).publishNoLog(any(), eq(Constants.GEN_AI));
  }

  @Test
  void processStream_shouldHandleNullResponseInIterator() {
    genAiSummaryHelper.init();
    Iterator<CreateGenerativeSummaryResponse> responseIterator =
        Collections.singletonList((CreateGenerativeSummaryResponse) null).iterator();
    String conversationId = "testConversationId";
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);

    genAiSummaryHelper.processStream(responseIterator, conversationId, config, summaryDoc,
        System.currentTimeMillis());

    verify(summaryDoc, never()).setGenAiSummary(any());
    verify(summaryDoc, never()).setGenAiGeneratedSummary(any());
    verify(summaryHelper, never()).publishNoLog(any(), eq(Constants.GEN_AI));
  }

  @Test
  void processStream_shouldProcessMultipleResponses() {
    genAiSummaryHelper.init();
    CreateGenerativeSummaryResponse response1 = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.CALL_REASON))
                .setText("Call reason text.").build()).build();
    CreateGenerativeSummaryResponse response2 = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.AGENT_ACTIONS))
                .setText("Agent actions text.").build()).build();
    Iterator<CreateGenerativeSummaryResponse> responseIterator =
        List.of(response1, response2).iterator();
    String conversationId = "testConversationId";
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getGenAiSummary()).thenReturn(
        GenAiSummaryDoc.builder().sections(Collections.emptyList()).build());
    when(summaryDoc.getConversationId()).thenReturn("conv1");
    when(config.isFormatEnabled()).thenReturn(true);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    genAiSummaryHelper.processStream(responseIterator, conversationId, config, summaryDoc,
        System.currentTimeMillis());

    verify(summaryDoc, atLeastOnce()).setGenAiSummary(any());
    verify(summaryDoc, atLeastOnce()).setGenAiGeneratedSummary(any());
    verify(summaryHelper, atLeastOnce()).publishNoLog(any(), eq(Constants.GEN_AI));
  }

  @Test
  void processStream_shouldRemoveRequestIdFromMapAfterProcessing() {
    genAiSummaryHelper.init();
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.CALL_REASON))
                .setText("Call reason text.").build()).build();
    Iterator<CreateGenerativeSummaryResponse> responseIterator = List.of(response).iterator();
    String conversationId = "testConversationId";
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getGenAiSummary()).thenReturn(
        GenAiSummaryDoc.builder().sections(Collections.emptyList()).build());
    when(summaryDoc.getConversationId()).thenReturn("conv1");
    when(config.isFormatEnabled()).thenReturn(true);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    genAiSummaryHelper.processStream(responseIterator, conversationId, config, summaryDoc,
        System.currentTimeMillis());

    assertFalse(genAiSummaryHelper.getGenAiConversationSectionMap().containsKey(conversationId));
  }

  @Test
  void processStream_ForEOF() {
    genAiSummaryHelper.init();
    CreateGenerativeSummaryResponse response1 = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.CALL_REASON))
                .setText("Call text.").build()).build();
    CreateGenerativeSummaryResponse response2 = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.EOF_IN_ID))
                .setText("").build()).build();
    Iterator<CreateGenerativeSummaryResponse> responseIterator =
        List.of(response1, response2).iterator();
    String conversationId = "testConversationId";
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getGenAiSummary()).thenReturn(
        GenAiSummaryDoc.builder().sections(Collections.emptyList()).build());
    when(summaryDoc.getConversationId()).thenReturn("conv1");
    when(config.isFormatEnabled()).thenReturn(true);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    genAiSummaryHelper.processStream(responseIterator, conversationId, config, summaryDoc,
        System.currentTimeMillis());

    assertFalse(genAiSummaryHelper.getGenAiConversationSectionMap().containsKey(conversationId));
  }

  @Test
  void whenTemplateBasedGenAISummaryFFIsEnabled_andPresentationFormatIsUnFormatted_thenReturnGenAiSummary() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId("Action Item").build())
                .setText("The customer needs to verify their 10-digit wireless number and last 4 digits of their social security number.").build())
        .setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_UNFORMATTED).build();
    GenAISummaryCache genAISummaryCache = GenAISummaryCache.builder().build();
    SummaryDoc summaryDoc = SummaryDoc.builder().conversationId("convId").build();

    when(this.ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS,
        FF_CONTEXT_KIND)).thenReturn(Boolean.FALSE);
    when(this.ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND)).thenReturn(
        Boolean.FALSE);
    when(this.ldHelper.isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
        FF_CONTEXT_KIND)).thenReturn(Boolean.TRUE);

    GenAiSummaryDoc genAiSummaryDoc = this.genAiSummaryHelper.formatGenAISummary(response, genAISummaryCache, summaryDoc);
    assertEquals(1, genAiSummaryDoc.getSections().size());
    assertEquals(Constants.SUMMARY, genAiSummaryDoc.getSections().get(0).getId());
    assertEquals(" <n>The customer needs to verify their 10-digit wireless number and last 4 digits of their social security number.<n>", genAiSummaryDoc.getSections().get(0).getText());
  }

  @Test
  void formatGenAISummary_ShouldFormatSectionsWhenConfigIsEnabled() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("section1"))
            .setText("text1").build()).build();
    Map<String, List<GenAISummaryCache.SpokenEntity>> sectionMap = new HashMap<>();
    sectionMap.put("section1", Collections.emptyList());
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(config.isFormatEnabled()).thenReturn(true);
    when(config.getSectionMap()).thenReturn(sectionMap);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND)).thenReturn(true);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    GenAiSummaryDoc result = genAiSummaryHelper.formatGenAISummary(response, config, summaryDoc);

    assertNotNull(result);
    assertEquals(1, result.getSections().size());
    assertEquals("section1", result.getSections().get(0).getId());
    assertEquals("<div>text1 </div>", result.getSections().get(0).getText());
  }

  @Test
  void formatGenAISummary_ShouldAddUnformattedSectionWhenConfigIsDisabled() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("section1"))
            .setText("text1").build()).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(config.isFormatEnabled()).thenReturn(false);
    when(config.getSectionMap()).thenReturn(Collections.emptyMap());
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND)).thenReturn(true);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    GenAiSummaryDoc result = genAiSummaryHelper.formatGenAISummary(response, config, summaryDoc);

    assertNotNull(result);
    assertEquals(1, result.getSections().size());
    assertEquals(Constants.SUMMARY, result.getSections().get(0).getId());
    assertEquals("<div> <n>text1<n></div>", result.getSections().get(0).getText());
  }

  @Test
  void formatGenAISummary_ShouldAddUnformattedSectionWhenConfigIsDisabled1() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("section1"))
            .setText("text1 The following entities were not found: entity1, entity2.").build())
        .build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(config.isFormatEnabled()).thenReturn(false);
    when(config.getSectionMap()).thenReturn(Collections.emptyMap());
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND)).thenReturn(true);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    GenAiSummaryDoc result = genAiSummaryHelper.formatGenAISummary(response, config, summaryDoc);

    assertNotNull(result);
    assertEquals(1, result.getSections().size());
    assertEquals(Constants.SUMMARY, result.getSections().get(0).getId());
    assertEquals("<div> <n>text1 <n></div>", result.getSections().get(0).getText());
  }

  @Test
  void formatGenAISummary_ShouldAddSpokenEntitiesWhenEnhancementsEnabled() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("section1"))
            .setText("text1").build()).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(config.isFormatEnabled()).thenReturn(true);
    when(config.getSectionMap()).thenReturn(Map.of("section1", List.of(
        GenAISummaryCache.SpokenEntity.builder().variableId("variableId").label("label").build())));
    when(summaryDoc.getVariables()).thenReturn(
        Map.of("variableId", new SummaryDoc.Slot("variableId", "instanceId", "value", false)));
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND)).thenReturn(false);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    GenAiSummaryDoc result = genAiSummaryHelper.formatGenAISummary(response, config, summaryDoc);

    assertNotNull(result);
    assertEquals(1, result.getSections().size());
    assertEquals("section1", result.getSections().get(0).getId());
    assertEquals("text1 ", result.getSections().get(0).getText());
    assertEquals(1, result.getSections().get(0).getSpokenEntities().size());
    assertEquals("instanceId",
        result.getSections().get(0).getSpokenEntities().get(0).getVariableId());
  }

  @Test
  void formatGenAISummary_ShouldHandleEmptySectionTextList() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder().build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(config.isFormatEnabled()).thenReturn(true);
    when(config.getSectionMap()).thenReturn(Collections.emptyMap());
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        false);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    GenAiSummaryDoc result = genAiSummaryHelper.formatGenAISummary(response, config, summaryDoc);

    assertNotNull(result);
    assertTrue(result.getSections().isEmpty());
  }

  @Test
  void removeAndBuildUndetectedSpokenEntity_ShouldRemoveEntitiesAndFormatText() {
    String input =
        "The customer's issues and concerns are: 1. Upgrade options for their wireless phone.";
    Set<String> entitySet = new HashSet<>();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(PARAGRAPH);

    String result = genAiSummaryHelper.removeAndBuildUndetectedSpokenEntity(input, entitySet,
        Constants.CALL_REASON, config);

    assertEquals(
        "The customer's issues and concerns are: 1. Upgrade options for their wireless phone.",
        result.trim());
  }

  @Test
  void removeAndBuildUndetectedSpokenEntity_ShouldHandleEmptyInput() {
    String input = "";
    Set<String> entitySet = new HashSet<>();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(PARAGRAPH);

    String result = genAiSummaryHelper.removeAndBuildUndetectedSpokenEntity(input, entitySet,
        Constants.CALL_REASON, config);

    assertTrue(entitySet.isEmpty());
  }

  @Test
  void removeAndBuildUndetectedSpokenEntity_ShouldHandleNullFormatString() {
    String input = "Entity1, Entity2, Entity3. The following entities were not found: Entity4<n>";
    Set<String> entitySet = new HashSet<>();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.getSummaryFormat()).thenReturn(null);

    String result = genAiSummaryHelper.removeAndBuildUndetectedSpokenEntity(input, entitySet,
        Constants.CALL_REASON, config);

    assertEquals("Entity1, Entity2, Entity3.<n>", result.trim());
    assertTrue(entitySet.contains("Entity4"));
  }

  @Test
  @Disabled
  void removeAndBuildUndetectedSpokenEntity_ShouldHandleDifferentFormats() {
    String input = "Entity1.\n<Entity2>.\nEntity3.";
    Set<String> entitySet = new HashSet<>();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(NUMBER_LIST);

    String result = genAiSummaryHelper.removeAndBuildUndetectedSpokenEntity(input, entitySet,
        Constants.CALL_REASON, config);

    assertEquals("Entity1.\n<Entity2>.\nEntity3.", result);
  }

  @Test
  void formatSummaryTextByUserConfig_ShouldFormatAsParagraph() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(PARAGRAPH);
    String summaryText = "First sentence. Second sentence.";
    String header = Constants.CALL_REASON;

    String result = genAiSummaryHelper.formatSummaryTextByUserConfig(config, summaryText, header);

    assertEquals("First sentence.\nSecond sentence. ", result);
  }

  @Test
  void formatSummaryTextByUserConfig_ShouldFormatAsBulletPoint() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(BULLET_POINT);
    String summaryText = "First point. Second point.";
    String header = Constants.CALL_REASON;

    String result = genAiSummaryHelper.formatSummaryTextByUserConfig(config, summaryText, header);

    assertEquals("<n> \u2022 First point.<n>\n<n> \u2022 Second point.<n> ", result);
  }

  @Test
  void formatSummaryTextByUserConfig_ShouldFormatAsNumberList() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(NUMBER_LIST);
    String summaryText = "First item. Second item.";
    String header = Constants.CALL_REASON;

    String result = genAiSummaryHelper.formatSummaryTextByUserConfig(config, summaryText, header);

    assertEquals("<n> 1. First item.<n>\n<n> 2. Second item.<n> ", result);
  }

  @Test
  void formatSummaryTextByUserConfig_ShouldHandleEmptySummaryText() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(PARAGRAPH);
    String summaryText = "";
    String header = Constants.CALL_REASON;

    String result = genAiSummaryHelper.formatSummaryTextByUserConfig(config, summaryText, header);

    assertEquals(" ", result);
  }

  @Test
  void formatSummaryTextByUserConfig_ShouldHandleNullFormatString() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.getSummaryFormat()).thenReturn(null);
    String summaryText = "First sentence. Second sentence.";
    String header = Constants.CALL_REASON;

    String result = genAiSummaryHelper.formatSummaryTextByUserConfig(config, summaryText, header);

    assertEquals("First sentence.\nSecond sentence. ", result);
  }

  @Test
  void aggregateMaps_ShouldAggregateNonEmptyMaps() {
    Map<String, Set<String>> map1 = Map.of("key1", Set.of("value1"), "key2", Set.of("value2"));
    Map<String, Set<String>> map2 = Map.of("key1", Set.of("value3"), "key3", Set.of("value4"));
    List<Map<String, Set<String>>> maps = List.of(map1, map2);

    Map<String, Set<String>> result = genAiSummaryHelper.aggregateMaps(maps);

    assertEquals(4, result.size());
    assertTrue(result.get("value1").contains("key1"));
    assertTrue(result.get("value2").contains("key2"));
    assertTrue(result.get("value3").contains("key1"));
    assertTrue(result.get("value4").contains("key3"));
  }

  @Test
  void aggregateMaps_ShouldHandleEmptyMaps() {
    List<Map<String, Set<String>>> maps = List.of(Collections.emptyMap(), Collections.emptyMap());

    Map<String, Set<String>> result = genAiSummaryHelper.aggregateMaps(maps);

    assertTrue(result.isEmpty());
  }

  @Test
  void aggregateMaps_ShouldHandleMapsWithEmptyValues() {
    Map<String, Set<String>> map1 = Map.of("key1", Set.of());
    Map<String, Set<String>> map2 = Map.of("key2", Set.of("value2"));
    List<Map<String, Set<String>>> maps = List.of(map1, map2);

    Map<String, Set<String>> result = genAiSummaryHelper.aggregateMaps(maps);

    assertEquals(1, result.size());
    assertTrue(result.get("value2").contains("key2"));
  }

  @Test
  void aggregateMaps_ShouldRemoveTrailingPeriods() {
    Map<String, Set<String>> map1 = Map.of("key1", Set.of("value1."));
    Map<String, Set<String>> map2 = Map.of("key1", Set.of("value2."));
    List<Map<String, Set<String>>> maps = List.of(map1, map2);

    Map<String, Set<String>> result = genAiSummaryHelper.aggregateMaps(maps);

    assertEquals(2, result.size());
    assertTrue(result.get("value1").contains("key1"));
    assertTrue(result.get("value2").contains("key1"));
  }

  @Test
  void shouldAddSpokenEntitiesWhenEnhancementsEnabled() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(config.getSectionMap()).thenReturn(Map.of("sectionId", List.of(
        new GenAISummaryCache.SpokenEntity("entity1", "d5bbd20d6c63-46c98c5b2c9e36ea-53b5",
            "wireless phone upgrade", "Phn upgrade", "In-line", true, true, "type1"),
        new GenAISummaryCache.SpokenEntity("entity2", "d5bbd20d6c63-46c98c5b2c9e36ea-53b0",
            "caller name", "Caller name", "End of Section", true, true, "type2"))));
    when(summaryDoc.getVariables()).thenReturn(Map.of("d5bbd20d6c63-46c98c5b2c9e36ea-53b5",
        new SummaryDoc.Slot("upgrade iphone 12", "d5bbd20d6c63-46c98c5b2c9e36ea-53b5", "9876",
            true), "d5bbd20d6c63-46c98c5b2c9e36ea-53b0",
        new SummaryDoc.Slot("john", "d5bbd20d6c63-46c98c5b2c9e36ea-53b0", "3456", true)));
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND)).thenReturn(false);

    GenAiSummaryDoc.Section sectionDocUnformatted =
        GenAiSummaryDoc.Section.builder().id("sectionId").text("text").build();
    String text =
        "<n>The customer, identified by <Caller name> and <Phn upgrade>, inquired about wireless phone upgrade options.<n> <n>The agent introduced herself, verified the customer's phone number, and confirmed the last four digits of their social security number, presented upgrade options, processed the upgrade order, and added the new phone to the customer's monthly bill.<n>  <n>The conversation concluded with the agent successfully upgrading the customer's phone and processing the order.<n> The following entities were not found: Entity4<n>";
    Section section = Section.newBuilder().setId("sectionId").build();
    Map<String, Set<String>> unmappedVariableInGenAiSummaries = new HashMap<>();
    Set<String> entityset = new HashSet<>();

    genAiSummaryHelper.addSpokenEntities(config, summaryDoc, sectionDocUnformatted, true, text,
        section, unmappedVariableInGenAiSummaries);

    assertEquals(2, sectionDocUnformatted.getSpokenEntities().size());
    assertEquals("d5bbd20d6c63-46c98c5b2c9e36ea-53b5",
        sectionDocUnformatted.getSpokenEntities().get(0).getVariableId());
  }

  @Test
  void shouldHandleNullSectionMap() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(config.getSectionMap()).thenReturn(null);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        true);

    GenAiSummaryDoc.Section sectionDocUnformatted =
        GenAiSummaryDoc.Section.builder().id("sectionId").text("text")
            .spokenEntities(new ArrayList<>()).build();
    String text = "text";
    Section section = Section.newBuilder().setId("sectionId").build();
    Map<String, Set<String>> unmappedVariableInGenAiSummaries = new HashMap<>();
    Set<String> entityset = new HashSet<>();
    genAiSummaryHelper.addSpokenEntities(config, summaryDoc, sectionDocUnformatted, true, text,
        section, unmappedVariableInGenAiSummaries);

    assertTrue(sectionDocUnformatted.getSpokenEntities().isEmpty());
  }

  @Test
  void shouldHandleEmptyVariableMap() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(config.getSectionMap()).thenReturn(Map.of("sectionId", List.of(
        GenAISummaryCache.SpokenEntity.builder().variableId("variableId").label("label").build())));
    when(summaryDoc.getVariables()).thenReturn(Collections.emptyMap());
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND)).thenReturn(true);

    GenAiSummaryDoc.Section sectionDocUnformatted =
        GenAiSummaryDoc.Section.builder().id("sectionId").text("text").build();
    String text = "text";
    Section section = Section.newBuilder().setId("sectionId").build();
    Map<String, Set<String>> unmappedVariableInGenAiSummaries = new HashMap<>();
    genAiSummaryHelper.addSpokenEntities(config, summaryDoc, sectionDocUnformatted, true, text,
        section, unmappedVariableInGenAiSummaries);

    assertEquals(1, sectionDocUnformatted.getSpokenEntities().size());
  }

  @Test
  void shouldHandleEmptyEntitySet() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    Map<String, List<GenAISummaryCache.SpokenEntity>> sectionMap = new HashMap<>();
    sectionMap.put("sectionId",
        List.of(GenAISummaryCache.SpokenEntity.builder().placement("In-line").build()));
    when(config.getSectionMap()).thenReturn(Collections.emptyMap());
    when(summaryDoc.getVariables()).thenReturn(Collections.emptyMap());
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND)).thenReturn(true);

    GenAiSummaryDoc.Section sectionDocUnformatted =
        GenAiSummaryDoc.Section.builder().id("sectionId").text("text").spokenEntities(
            List.of(GenAiSummaryDoc.SpokenEntity.builder().placement("In-line").build())).build();
    String text = "text";
    Section section = Section.newBuilder().setId("sectionId").build();
    Map<String, Set<String>> unmappedVariableInGenAiSummaries = new HashMap<>();

    genAiSummaryHelper.addSpokenEntities(config, summaryDoc, sectionDocUnformatted, true, text,
        section, unmappedVariableInGenAiSummaries);

    assertTrue(sectionDocUnformatted.getSpokenEntities().isEmpty());
  }

  @Test
  void testAddSpokenEntities_genAiEntitiesToLLMEnabledTrue_withEntitySet() {
    Section section = mock(Section.class);
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    GenAiSummaryDoc.Section sectionDocUnformatted = mock(GenAiSummaryDoc.Section.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    Map<String, Set<String>> unmappedVariableInGenAiSummaries = new HashMap<>();
    GenAISummaryCache.SpokenEntity spokenEntity = mock(GenAISummaryCache.SpokenEntity.class);
    Map<String, List<GenAISummaryCache.SpokenEntity>> sectionMap = new HashMap<>();
    sectionMap.put("id1", List.of(spokenEntity));
    when(config.getSectionMap()).thenReturn(Collections.emptyMap());
    when(config.getSummaryFormat()).thenReturn(null);
    when(sectionDocUnformatted.getId()).thenReturn("sectionId");
    when(summaryDoc.getVariables()).thenReturn(Collections.emptyMap());
    when(sectionDocUnformatted.getId()).thenReturn("id1");
    when(config.getSectionMap()).thenReturn(sectionMap);
    when(spokenEntity.getLabel()).thenReturn("Entity4");
    when(section.getId()).thenReturn("sectionId");

    genAiSummaryHelper.addSpokenEntities(config, summaryDoc, sectionDocUnformatted, true,
        "Entity1, Entity2, Entity3. The following entities were not found: Entity4<n>", section,
        unmappedVariableInGenAiSummaries);

    ArgumentCaptor<List<GenAiSummaryDoc.SpokenEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(sectionDocUnformatted).setSpokenEntities(captor.capture());
    verify(sectionDocUnformatted).setText(anyString());
  }

  @Test
  void formatGenAISummary_ShouldAddUnformattedSectionWhenConfigIsDisabledWithFFEnabled() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId("customer-sentiment"))
                .setText("text1").build()).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(config.isFormatEnabled()).thenReturn(false);
    when(config.getSectionMap()).thenReturn(Collections.emptyMap());
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND)).thenReturn(true);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    GenAiSummaryDoc result = genAiSummaryHelper.formatGenAISummary(response, config, summaryDoc);

    assertNotNull(result);
    assertEquals(1, result.getSections().size());
    assertEquals(Constants.CUSTOMER_SENTIMENT, result.getSections().get(0).getId());
    assertEquals("<div><n>text1<n></div>", result.getSections().get(0).getText());
  }

  @Test
  void formatGenAISummary_ShouldAddUnformattedSectionWhenConfigIsDisabledWithFFDisabled() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId("customer-sentiment"))
                .setText("text1").build()).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(config.isFormatEnabled()).thenReturn(false);
    when(config.getSectionMap()).thenReturn(Collections.emptyMap());
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(
        true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND)).thenReturn(false);
    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    GenAiSummaryDoc result = genAiSummaryHelper.formatGenAISummary(response, config, summaryDoc);

    assertNotNull(result);
    assertEquals(1, result.getSections().size());
    assertEquals(Constants.CUSTOMER_SENTIMENT, result.getSections().get(0).getId());
    assertEquals("<n>text1<n>", result.getSections().get(0).getText());
  }

  @Test
  void formatSummaryStream_shouldFormatSingleSectionCorrectly() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.CALL_REASON))
                .setText("Call reason text.").build()).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(true);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    AtomicReference<String> currentSectionId = new AtomicReference<>("Phone upgrade");

    Pair<GenAiSummaryDoc, String> result =
        genAiSummaryHelper.formatSummaryStream(response, config, new HashMap<>(), summaryDoc,1);

    assertNotNull(result);
    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals(Constants.CALL_REASON, result.getLeft().getSections().get(0).getId());
    assertEquals("Call reason text.", result.getLeft().getSections().get(0).getText());
    assertEquals(Constants.CALL_REASON, result.getRight());
  }

  @Test
  void formatSummaryStream_shouldHandleMultipleSectionsInOrder() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.CALL_REASON))
                .setText("Call reason text.").build()).addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.AGENT_ACTIONS))
                .setText("Agent actions text.").build()).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(true);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    AtomicReference<String> currentSectionId = new AtomicReference<>("Phone upgrade");

    Pair<GenAiSummaryDoc, String> result =
        genAiSummaryHelper.formatSummaryStream(response, config, new HashMap<>(), summaryDoc,2);

    assertNotNull(result);
    assertNotNull(result.getLeft());
    assertEquals(2, result.getLeft().getSections().size());
    assertEquals(Constants.CALL_REASON, result.getLeft().getSections().get(0).getId());
    assertEquals("Call reason text.", result.getLeft().getSections().get(0).getText());
    assertEquals(Constants.AGENT_ACTIONS, result.getLeft().getSections().get(1).getId());
    assertEquals("Agent actions text.", result.getLeft().getSections().get(1).getText());
    assertEquals(Constants.AGENT_ACTIONS, result.getRight());
  }

  @Test
  void formatSummaryStream_shouldHandleUnformattedStreamCorrectly() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(
            SectionText.newBuilder().setSection(Section.newBuilder().setId(Constants.CALL_REASON))
                .setText("Unformatted text.").build()).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(false);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    AtomicReference<String> currentSectionId = new AtomicReference<>("Phone upgrade");

    Pair<GenAiSummaryDoc, String> result =
        genAiSummaryHelper.formatSummaryStream(response, config, new HashMap<>(), summaryDoc,4);

    assertNotNull(result);
    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals(Constants.SUMMARY, result.getLeft().getSections().get(0).getId());
    assertEquals("Unformatted text.", result.getLeft().getSections().get(0).getText());
    assertEquals(Constants.SUMMARY, result.getRight());
  }

  @Test
  void formatSummaryStream_shouldHandleEmptyResponse() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder().build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(true);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    AtomicReference<String> currentSectionId = new AtomicReference<>("Phone upgrade");

    Pair<GenAiSummaryDoc, String> result =
        genAiSummaryHelper.formatSummaryStream(response, config, new HashMap<>(), summaryDoc,3);

    assertNotNull(result);
    assertNotNull(result.getLeft());
    assertTrue(result.getLeft().getSections().isEmpty());
  }

  @Test
  void formatSummaryStream_shouldHandleEmptySectionIdGracefully() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId(""))
            .setText("Text with empty section ID.").build()).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(false);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    AtomicReference<String> currentSectionId = new AtomicReference<>("Phone upgrade");

    Pair<GenAiSummaryDoc, String> result =
        genAiSummaryHelper.formatSummaryStream(response, config, new HashMap<>(), summaryDoc,9);

    assertNotNull(result);
    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals("summary", result.getLeft().getSections().get(0).getId());
    assertEquals("Text with empty section ID.", result.getLeft().getSections().get(0).getText());
    assertEquals("summary", result.getRight());
  }

  @Test
  void testFormatSummaryStream_withTemplateBasedBullet() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("summary"))
            .setText("Text with empty.\n\n section ID.").build())
        .setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_BULLET).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(false);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getTemplateId()).thenReturn("template-123");
    when(summaryDoc.getConversationId()).thenReturn("conv1");

    when(ldHelper.isFFEnabled(eq(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY), any()))
        .thenReturn(true);
    AtomicReference<String> currentSectionId = new AtomicReference<>("Phone upgrade");

    Pair<GenAiSummaryDoc, String> result =
        genAiSummaryHelper.formatSummaryStream(response, config, new HashMap<>(), summaryDoc,1);

    assertNotNull(result);
    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals("summary", result.getLeft().getSections().get(0).getId());
    assertEquals("• Text with empty. section ID.<n>", result.getLeft().getSections().get(0).getText());
    assertEquals("summary", result.getRight());
  }

  @Test
  void testFormatSummaryStream_withTemplateBasedParagraph() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("summary"))
            .setText("").build())
        .setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_PARAGRAPH).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(false);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getTemplateId()).thenReturn("template-123");
    when(summaryDoc.getConversationId()).thenReturn("conv1");

    when(ldHelper.isFFEnabled(eq(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY), any()))
        .thenReturn(true);

    Pair<GenAiSummaryDoc, String> result =
        genAiSummaryHelper.formatSummaryStream(response, config, new HashMap<>(), summaryDoc, 1);

    assertNotNull(result);
    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals("summary", result.getLeft().getSections().get(0).getId());
    // empty token: no sentence boundary → nothing appended (no trailing space)
    assertEquals("", result.getLeft().getSections().get(0).getText());
    assertEquals("summary", result.getRight());
  }

  @Test
  void testFormatSummaryStream_withTemplateBasedParagraph_multiSentence() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("summary"))
            .setText("First sentence. Second sentence.").build())
        .setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_PARAGRAPH).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(false);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getTemplateId()).thenReturn("template-123");
    when(summaryDoc.getConversationId()).thenReturn("conv1");

    when(ldHelper.isFFEnabled(eq(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY), any()))
        .thenReturn(true);

    Pair<GenAiSummaryDoc, String> result =
        genAiSummaryHelper.formatSummaryStream(response, config, new HashMap<>(), summaryDoc, 1);

    assertNotNull(result);
    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals("summary", result.getLeft().getSections().get(0).getId());
    // token ends with '.' → sentence boundary → <n> appended so UI renders a new paragraph line
    assertEquals("First sentence. Second sentence.<n>", result.getLeft().getSections().get(0).getText());
    assertEquals("summary", result.getRight());
  }

  @Test
  void testFormatSummaryStream_withTemplateBasedNumeric() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("summary"))
            .setText("Text with empty section ID.").build())
        .setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_NUMERIC).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(false);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getTemplateId()).thenReturn("template-123");
    when(summaryDoc.getConversationId()).thenReturn("conv1");

    when(ldHelper.isFFEnabled(eq(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY), any()))
        .thenReturn(true);
    AtomicReference<String> currentSectionId = new AtomicReference<>("Phone upgrade");

    Pair<GenAiSummaryDoc, String> result =
        genAiSummaryHelper.formatSummaryStream(response, config, new HashMap<>(), summaryDoc,1);

    assertNotNull(result);
    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals("summary", result.getLeft().getSections().get(0).getId());
    assertEquals("1.Text with empty section ID.<n>", result.getLeft().getSections().get(0).getText());
    assertEquals("summary", result.getRight());
  }

  @Test
  void testFormatSummaryStream_withUnformatted() {
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId(""))
            .setText("Text with empty section ID.").build())
        .setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_UNFORMATTED).build();
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(false);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getTemplateId()).thenReturn("template-123");
    when(summaryDoc.getConversationId()).thenReturn("conv1");

    when(ldHelper.isFFEnabled(eq(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY), any()))
        .thenReturn(true);
    AtomicReference<String> currentSectionId = new AtomicReference<>("Phone upgrade");

    Pair<GenAiSummaryDoc, String> result =
        genAiSummaryHelper.formatSummaryStream(response, config, new HashMap<>(), summaryDoc,9);

    assertNotNull(result);
    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals("summary", result.getLeft().getSections().get(0).getId());
    assertEquals("Text with empty section ID.", result.getLeft().getSections().get(0).getText());
  }

  @Test
  void bufferSummary_shouldInitializeBufferWhenNull() {
    GenAiSummaryDoc formattedGenAiSummary = GenAiSummaryDoc.builder()
        .sections(List.of(GenAiSummaryDoc.Section.builder().id("section1").text("text1").build()))
        .build();
    Pair<GenAiSummaryDoc, Boolean> result =
        genAiSummaryHelper.bufferSummary(null, 10, formattedGenAiSummary);

    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals("section1", result.getLeft().getSections().get(0).getId());
    assertEquals("text1", result.getLeft().getSections().get(0).getText());
    assertFalse(result.getRight());
  }

  @Test
  void bufferSummary_shouldAddNewSectionToExistingBuffer() {
    List<GenAiSummaryDoc.Section> section1 = new ArrayList<>();
    section1.add((GenAiSummaryDoc.Section.builder().id("section1").text("text1").build()));
    GenAiSummaryDoc bufferedGenAiSummary = GenAiSummaryDoc.builder().sections(section1).build();
    List<GenAiSummaryDoc.Section> section2 = new ArrayList<>();
    section2.add((GenAiSummaryDoc.Section.builder().id("section2").text("text2").build()));
    GenAiSummaryDoc formattedGenAiSummary = GenAiSummaryDoc.builder().sections(section2).build();

    Pair<GenAiSummaryDoc, Boolean> result =
        genAiSummaryHelper.bufferSummary(bufferedGenAiSummary, 10, formattedGenAiSummary);

    assertNotNull(result.getLeft());
    assertEquals(2, result.getLeft().getSections().size());
    assertEquals("section2", result.getLeft().getSections().get(1).getId());
    assertEquals("text2", result.getLeft().getSections().get(1).getText());
    assertFalse(result.getRight());
  }

  @Test
  void bufferSummary_shouldAppendTextToExistingSection() {
    GenAiSummaryDoc bufferedGenAiSummary = GenAiSummaryDoc.builder()
        .sections(List.of(GenAiSummaryDoc.Section.builder().id("section1").text("text1").build()))
        .build();
    GenAiSummaryDoc formattedGenAiSummary = GenAiSummaryDoc.builder()
        .sections(List.of(GenAiSummaryDoc.Section.builder().id("section1").text("text2").build()))
        .build();

    Pair<GenAiSummaryDoc, Boolean> result =
        genAiSummaryHelper.bufferSummary(bufferedGenAiSummary, 10, formattedGenAiSummary);

    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals("section1", result.getLeft().getSections().get(0).getId());
    assertEquals("text1text2", result.getLeft().getSections().get(0).getText());
    assertFalse(result.getRight());
  }

  @Test
  void bufferSummary_shouldReturnTrueWhenBufferSizeExceeded() {
    List<GenAiSummaryDoc.Section> section1 = new ArrayList<>();
    section1.add((GenAiSummaryDoc.Section.builder().id("section1").text("text1").build()));
    GenAiSummaryDoc bufferedGenAiSummary = GenAiSummaryDoc.builder().sections(section1).build();
    List<GenAiSummaryDoc.Section> section2 = new ArrayList<>();
    section2.add((GenAiSummaryDoc.Section.builder().id("section2").text("text2 text 3").build()));
    GenAiSummaryDoc formattedGenAiSummary = GenAiSummaryDoc.builder().sections(section2).build();

    Pair<GenAiSummaryDoc, Boolean> result =
        genAiSummaryHelper.bufferSummary(bufferedGenAiSummary, 3, formattedGenAiSummary);

    assertNotNull(result.getLeft());
    assertTrue(result.getRight());
  }

  @Test
  void bufferSummary_shouldHandleEmptyFormattedGenAiSummary() {
    GenAiSummaryDoc bufferedGenAiSummary = GenAiSummaryDoc.builder()
        .sections(List.of(GenAiSummaryDoc.Section.builder().id("section1").text("text1").build()))
        .build();
    GenAiSummaryDoc formattedGenAiSummary =
        GenAiSummaryDoc.builder().sections(Collections.emptyList()).build();

    Pair<GenAiSummaryDoc, Boolean> result =
        genAiSummaryHelper.bufferSummary(bufferedGenAiSummary, 10, formattedGenAiSummary);

    assertNotNull(result.getLeft());
    assertEquals(1, result.getLeft().getSections().size());
    assertEquals("section1", result.getLeft().getSections().get(0).getId());
    assertEquals("text1", result.getLeft().getSections().get(0).getText());
    assertFalse(result.getRight());
  }

  @Test
  void handleFormattedStream_shouldFormatAsBulletPoints() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    Map<String, Integer> currentNumberIndex = new HashMap<>();
    SectionText sectionText = SectionText.newBuilder()
        .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())
        .setText("First sentence.\n Second sentence.").build();
    GenAiSummaryDoc genAiSummary = GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(BULLET_POINT);

    genAiSummaryHelper.handleFormattedStream(config, currentNumberIndex, sectionText, genAiSummary);

    assertEquals(1, genAiSummary.getSections().size());
    assertEquals(Constants.CALL_REASON, genAiSummary.getSections().get(0).getId());
    assertEquals("<n>\u2022 First sentence.<n>\n<n> \u2022\n Second sentence.<n>\n<n> \u2022",
        genAiSummary.getSections().get(0).getText());
  }

  @Test
  void handleFormattedStream_shouldFormatAsNumberedList() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    Map<String, Integer> currentNumberIndex = new HashMap<>();
    SectionText sectionText = SectionText.newBuilder()
        .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())
        .setText("First item.\n Second item.").build();
    GenAiSummaryDoc genAiSummary = GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(NUMBER_LIST);

    genAiSummaryHelper.handleFormattedStream(config, currentNumberIndex, sectionText, genAiSummary);

    assertEquals(1, genAiSummary.getSections().size());
    assertEquals(Constants.CALL_REASON, genAiSummary.getSections().get(0).getId());
    assertEquals("<n>1. First item.<n>\n<n>2. \n Second item.<n>\n<n>3. ",
        genAiSummary.getSections().get(0).getText());
  }

  @Test
  void handleFormattedStream_shouldHandleParagraphFormat() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    Map<String, Integer> currentNumberIndex = new HashMap<>();
    SectionText sectionText =
        SectionText.newBuilder().setSection(Section.newBuilder().setId("section1").build())
            .setText("This is a paragraph.").build();
    GenAiSummaryDoc genAiSummary = GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(PARAGRAPH);

    genAiSummaryHelper.handleFormattedStream(config, currentNumberIndex, sectionText, genAiSummary);

    assertEquals(1, genAiSummary.getSections().size());
    assertEquals("section1", genAiSummary.getSections().get(0).getId());
    assertEquals("This is a paragraph.", genAiSummary.getSections().get(0).getText());
  }

  @Test
  void handleFormattedStream_shouldHandleEmptyText() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    Map<String, Integer> currentNumberIndex = new HashMap<>();
    SectionText sectionText =
        SectionText.newBuilder().setSection(Section.newBuilder().setId("section1").build())
            .setText("").build();
    GenAiSummaryDoc genAiSummary = GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    when(config.getSummaryFormat()).thenReturn(mock(GenAISummaryCache.SummaryFormat.class));
    when(config.getSummaryFormat().getCallReason()).thenReturn(PARAGRAPH);

    genAiSummaryHelper.handleFormattedStream(config, currentNumberIndex, sectionText, genAiSummary);

    assertEquals(1, genAiSummary.getSections().size());
    assertEquals("section1", genAiSummary.getSections().get(0).getId());
    assertEquals("", genAiSummary.getSections().get(0).getText());
  }

  @Test
  void handleFormattedStream_shouldHandleNullFormatString() {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    Map<String, Integer> currentNumberIndex = new HashMap<>();
    SectionText sectionText =
        SectionText.newBuilder().setSection(Section.newBuilder().setId("section1").build())
            .setText("Text with no format.").build();
    GenAiSummaryDoc genAiSummary = GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    when(config.getSummaryFormat()).thenReturn(null);

    genAiSummaryHelper.handleFormattedStream(config, currentNumberIndex, sectionText, genAiSummary);

    assertEquals(1, genAiSummary.getSections().size());
    assertEquals("section1", genAiSummary.getSections().get(0).getId());
    assertEquals("Text with no format.", genAiSummary.getSections().get(0).getText());
  }

  @Test
  void formatDotSpaceToTagN_shouldAddTagNAfterDotWhenNotFollowedByDigit() {
    String input = "This is a test. Another sentence.";
    String expected = "This is a test.<n> Another sentence.";
    String result = genAiSummaryHelper.formatDotSpaceToTagN(input);
    assertEquals(expected, result);
  }

  @Test
  void formatDotSpaceToTagN_shouldNotAddTagNWhenDotIsFollowedByDigit() {
    String input = "Version 1.2.3 is released.";
    String expected = "Version 1.2.3 is released.";
    String result = genAiSummaryHelper.formatDotSpaceToTagN(input);
    assertEquals(expected, result);
  }

  @Test
  void formatDotSpaceToTagN_shouldHandleEmptyString() {
    String input = "";
    String expected = "";
    String result = genAiSummaryHelper.formatDotSpaceToTagN(input);
    assertEquals(expected, result);
  }

  @Test
  void formatDotSpaceToTagN_shouldHandleStringWithoutDots() {
    String input = "This is a test without dots";
    String expected = "This is a test without dots";
    String result = genAiSummaryHelper.formatDotSpaceToTagN(input);
    assertEquals(expected, result);
  }

  @Test
  void formatDotSpaceToTagN_shouldHandleStringWithMultipleDots() {
    String input = "Sentence one. Sentence two. Sentence three.";
    String expected = "Sentence one.<n> Sentence two.<n> Sentence three.";
    String result = genAiSummaryHelper.formatDotSpaceToTagN(input);
    assertEquals(expected, result);
  }

  @Test
  void formatDotSpaceToTagN_shouldHandleUnicodeDot() {
    String input = "Unicode dot\u002E Another sentence.";
    String expected = "Unicode dot.<n> Another sentence.";
    String result = genAiSummaryHelper.formatDotSpaceToTagN(input);
    assertEquals(expected, result);
  }

  @Test
  void formatDotSpaceToTagN_shouldNotAddTagNForConsecutiveDots() {
    String input = "This is a test... Another sentence.";
    String expected = "This is a test...<n> Another sentence.";
    String result = genAiSummaryHelper.formatDotSpaceToTagN(input);
    assertEquals(expected, result);
  }

  @Test
  void formatDotSpaceToTagN_shouldHandleStringEndingWithDot() {
    String input = "This is a test.";
    String expected = "This is a test.";
    String result = genAiSummaryHelper.formatDotSpaceToTagN(input);
    assertEquals(expected, result);
  }

  @Test
  void testprocessStreamWithoutUassistConfig_paragraph() {
    String convId="test123";
    // Mock section objects
    Section section1 = Section.newBuilder().setId("section-1").build();
    Section section2 = Section.newBuilder().setId("section-2").build();
    Section section3 = Section.newBuilder().setId("section-3").build();
    Section eof = Section.newBuilder().setId("EOF").build();

    // Mock SectionText entries 1
    CreateGenerativeSummaryResponse st1 =
        CreateGenerativeSummaryResponse.newBuilder().addSectionText(SectionText.newBuilder().setText("paragraph text 1. Paragraph text 2.")
            .setSection(section1).build()).setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_PARAGRAPH).build();

    // Mock SectionText entries 2
    CreateGenerativeSummaryResponse st2 =
        CreateGenerativeSummaryResponse.newBuilder().addSectionText(SectionText.newBuilder().setText("number text 1. Number text 2.")
            .setSection(section2).build()).setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_NUMERIC).build();

    // Mock SectionText entries 3
    CreateGenerativeSummaryResponse st3 =
        CreateGenerativeSummaryResponse.newBuilder().addSectionText(SectionText.newBuilder().setText("bullet text 1. Bullet text 2.")
            .setSection(section3).build()).setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_BULLET).build();

    // Mock SectionText entries 1
    CreateGenerativeSummaryResponse st4 =
        CreateGenerativeSummaryResponse.newBuilder().addSectionText(SectionText.newBuilder().setText("Paragraph text 3. Paragraph text 4")
            .setSection(section1).build()).setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_PARAGRAPH).build();

    // Mock SectionText entries 2
    CreateGenerativeSummaryResponse st5 =
        CreateGenerativeSummaryResponse.newBuilder().addSectionText(SectionText.newBuilder().setText("Number text 3. Number text 4")
            .setSection(section2).build()).setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_NUMERIC).build();

    // Mock SectionText entries 3
    CreateGenerativeSummaryResponse st6 =
        CreateGenerativeSummaryResponse.newBuilder().addSectionText(SectionText.newBuilder().setText("Bullet text 3. Bullet text 4")
            .setSection(section3).build()).setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_BULLET).build();

    // Mock SectionText entries 3
    CreateGenerativeSummaryResponse st7 =
        CreateGenerativeSummaryResponse.newBuilder().addSectionText(SectionText.newBuilder()
            .setSection(eof).build()).build();

    Iterator<CreateGenerativeSummaryResponse> responseIterator=Arrays.asList(
        st1, st2, st3,st4,st5,st6,st7
    ).iterator();

    GenAiSummaryDoc genAiSummaryDoc =
        this.genAiSummaryHelper.processStreamWithoutUassistConfig(responseIterator, convId);

    assertEquals(3, genAiSummaryDoc.getSections().size());
    assertEquals("section-1", genAiSummaryDoc.getSections().get(0).getId());
    assertEquals("section-2", genAiSummaryDoc.getSections().get(1).getId());
    assertEquals("section-3", genAiSummaryDoc.getSections().get(2).getId());
  }

  @Test
  void publishAsNewTraceRoot_shouldStartDetachedSpanAndDelegateToSummaryHelper() {
    Tracer tracer = mock(Tracer.class);
    Tracer.SpanBuilder builder = mock(Tracer.SpanBuilder.class);
    Span span = mock(Span.class);
    Scope scope = mock(Scope.class);
    when(tracer.buildSpan("uassist.summary.publish")).thenReturn(builder);
    when(builder.ignoreActiveSpan()).thenReturn(builder);
    when(builder.withTag(anyString(), anyString())).thenReturn(builder);
    when(builder.start()).thenReturn(span);
    when(tracer.activateSpan(span)).thenReturn(scope);

    Summary summaryProto = Summary.newBuilder().build();

    try (MockedStatic<GlobalTracer> gt = Mockito.mockStatic(GlobalTracer.class)) {
      gt.when(GlobalTracer::get).thenReturn(tracer);
      genAiSummaryHelper.publishAsNewTraceRoot(summaryProto, Constants.GEN_AI, "conv-1");
    }

    // Fresh span, detached from any active trace, tagged for correlation.
    verify(tracer).buildSpan("uassist.summary.publish");
    verify(builder).ignoreActiveSpan();
    verify(builder).withTag("conversationId", "conv-1");
    verify(builder).withTag("eventType", Constants.GEN_AI);
    verify(builder).start();
    verify(tracer).activateSpan(span);
    // Delegated to the actual publisher and closed cleanly.
    verify(summaryHelper).publishNoLog(summaryProto, Constants.GEN_AI);
    verify(scope).close();
    verify(span).finish();
  }

  @Test
  void publishAsNewTraceRoot_shouldFinishSpanEvenWhenPublisherThrows() {
    Tracer tracer = mock(Tracer.class);
    Tracer.SpanBuilder builder = mock(Tracer.SpanBuilder.class);
    Span span = mock(Span.class);
    Scope scope = mock(Scope.class);
    when(tracer.buildSpan("uassist.summary.publish")).thenReturn(builder);
    when(builder.ignoreActiveSpan()).thenReturn(builder);
    when(builder.withTag(anyString(), anyString())).thenReturn(builder);
    when(builder.start()).thenReturn(span);
    when(tracer.activateSpan(span)).thenReturn(scope);

    Summary summaryProto = Summary.newBuilder().build();
    doThrow(new RuntimeException("kafka down")).when(summaryHelper)
        .publishNoLog(summaryProto, Constants.GEN_AI);

    try (MockedStatic<GlobalTracer> gt = Mockito.mockStatic(GlobalTracer.class)) {
      gt.when(GlobalTracer::get).thenReturn(tracer);
      assertThrows(RuntimeException.class,
          () -> genAiSummaryHelper.publishAsNewTraceRoot(summaryProto, Constants.GEN_AI, "conv-2"));
    }

    // Span must always be finished so traces aren't left dangling.
    verify(scope).close();
    verify(span).finish();
  }

  @Test
  void publishAsNewTraceRoot_shouldStillPublishWhenTracerBuildThrows() {
    // Best-effort tracing: a broken tracer must not block the Kafka publish.
    Tracer tracer = mock(Tracer.class);
    when(tracer.buildSpan("uassist.summary.publish"))
        .thenThrow(new RuntimeException("tracer misconfigured"));

    Summary summaryProto = Summary.newBuilder().build();

    try (MockedStatic<GlobalTracer> gt = Mockito.mockStatic(GlobalTracer.class)) {
      gt.when(GlobalTracer::get).thenReturn(tracer);
      genAiSummaryHelper.publishAsNewTraceRoot(summaryProto, Constants.GEN_AI, "conv-3");
    }

    // Publish still went through despite the tracer exception.
    verify(summaryHelper).publishNoLog(summaryProto, Constants.GEN_AI);
  }

  // Regression guard: GenAI summary line breaks landing mid-word/mid-number.
  //
  // The LLM streams ONE TOKEN PER CHUNK and punctuation arrives alone, so "USD 25.30" is streamed
  // as ' USD' | ' ' | '2' | '5' | '.' | '3' | '0' (verified in staging debug logs). PR #573 appended
  // <n> to any chunk ending in '.', which put a line break inside every decimal and abbreviation.
  // The chunk alone carries no context, so the guard judges the accumulated section text instead.
  // EBUG-8104's line break must survive on genuine sentence ends.

  /**
   * Streams chunks through the formatter in production order: persistStreamResponse accumulates the
   * section text first, then the formatter reads that tail. Returns the concatenated deltas.
   */
  private String streamParagraphChunks(String... chunks) {
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(false);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getTemplateId()).thenReturn("template-123");
    when(summaryDoc.getConversationId()).thenReturn("conv-stream");
    when(ldHelper.isFFEnabled(eq(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY), any())).thenReturn(true);

    String requestId = "req-stream";
    StringBuilder rendered = new StringBuilder();
    for (int i = 0; i < chunks.length; i++) {
      CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
          .addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("summary")).setText(chunks[i]).build())
          .setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_PARAGRAPH).build();
      genAiSummaryHelper.persistStreamResponse(response, requestId);
      Pair<GenAiSummaryDoc, String> result = genAiSummaryHelper.formatSummaryStream(response,
          config, new HashMap<>(), summaryDoc, i == 0 ? 1 : 0, requestId);
      rendered.append(result.getLeft().getSections().get(0).getText());
    }
    return rendered.toString();
  }

  @Test
  void streamingParagraph_honorificMustNotStartNewLine() {
    // Reported: "Mr. Parker" rendered as "Mr." / "Parker". The '.' arrives as its own chunk.
    assertEquals(" Mr. Parker", streamParagraphChunks(" Mr", ".", " Parker"));
  }

  @Test
  void streamingParagraph_currencyDecimalMustNotStartNewLine() {
    // Reported: "$31.67" rendered as "$31." / "67".
    assertEquals(" $31.67 total", streamParagraphChunks(" $", "31", ".", "67", " total"));
  }

  @Test
  void streamingParagraph_replaysStagingUsChunksCleanly() {
    // Exact chunk sequence logged in staging for conv d102b868-0884-4dc7-8bfb-449adaf3a86f
    // (Formatting text. formatType='paragraph', isFirstWord=181..187).
    assertEquals(" USD 25.30",
        streamParagraphChunks(" USD", " ", "2", "5", ".", "3", "0"));
  }

  @Test
  void streamingParagraph_genuineSentenceEndStillBreaks() {
    // EBUG-8104 must survive: a real sentence end still gets its <n>.
    assertEquals(" email would be sent.<n>",
        streamParagraphChunks(" email", " would", " be", " sent", "."));
  }

  @Test
  void streamingParagraph_suppressionIsShapeBasedNotAWordList() {
    // None of these titles is enumerated anywhere; they match by shape. Replacing
    // NOT_SENTENCE_END_REGEX with a hardcoded list should fail here.
    for (String title : new String[] {" Rev", " Capt", " Sgt", " Lt", " Hon", " Msgr", " Adm",
        " Blvd", " Sen", " Col"}) {
      genAiSummaryHelper.init(); // fresh accumulation per title
      assertEquals(title + ". Parker", streamParagraphChunks(title, ".", " Parker"),
          "no line break expected after " + title.trim() + ".");
    }
  }

  @Test
  void streamingParagraph_shortLowercaseWordStillEndsSentence() {
    // Short but lowercase, so a real sentence end. A word list had to special-case this.
    assertEquals(" answer was no.<n>", streamParagraphChunks(" answer", " was", " no", "."));
  }

  @Test
  void streamingParagraph_bareParagraphBreakMustNotBecomeAPeriod() {
    // Reported: summaries containing a line with only ".". A paragraph break arrives as its own
    // blank chunk and was being rewritten to ". ". It must separate without inventing a full stop —
    // and the chunk after a break can arrive with no leading space, so a space is still needed.
    assertEquals(" floor.<n> The agent",
        streamParagraphChunks(" floor", ".", "\n\n", "The agent"));
  }

  @Test
  void streamingParagraph_fallsBackToChunkWhenNoAccumulatedText() {
    // No requestId (the 5-arg overload) => no tail available => guard falls back to the chunk.
    GenAISummaryCache config = mock(GenAISummaryCache.class);
    when(config.isFormatEnabled()).thenReturn(false);
    SummaryDoc summaryDoc = mock(SummaryDoc.class);
    when(summaryDoc.getTemplateId()).thenReturn("template-123");
    when(summaryDoc.getConversationId()).thenReturn("conv-fallback");
    when(ldHelper.isFFEnabled(eq(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY), any())).thenReturn(true);
    CreateGenerativeSummaryResponse response = CreateGenerativeSummaryResponse.newBuilder()
        .addSectionText(SectionText.newBuilder().setSection(Section.newBuilder().setId("summary"))
            .setText(" done.").build())
        .setPresentationFormat(PresentationFormat.PRESENTATION_FORMAT_PARAGRAPH).build();

    Pair<GenAiSummaryDoc, String> result = genAiSummaryHelper.formatSummaryStream(response, config,
        new HashMap<>(), summaryDoc, 1);

    assertEquals(" done.<n>", result.getLeft().getSections().get(0).getText());
  }
}
