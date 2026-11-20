package com.uniphore.uassist.summary.util;

import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.llminference.v1.*;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.cache.GenAISummaryCache;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.CustomMetricsPublisher;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.helper.DataHelper;
import com.uniphore.uassist.summary.helper.LookerGenAISummaryMetricHelper;
import com.uniphore.uassist.summary.utils.TestConstant;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.Assert;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.uniphore.featureflag.LaunchDarklyConstants.FF_CONTEXT_KIND;
import static com.uniphore.uassist.summary.common.Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class GrpcClientHelperTest extends ApplicationTest {

  @MockBean
  private CustomMetricsPublisher customMetricsPublisher;

  @Mock
  private ManagedChannel managedChannel;

  @Mock
  private LLMInferenceServiceGrpc.LLMInferenceServiceBlockingStub llmInferenceClient;

  @Mock
  private LookerGenAISummaryMetricHelper lookerGenAISummaryMetricHelper;

  @InjectMocks
  private GrpcClientHelper grpcClientHelper;

  @Mock
  private LaunchDarklyHelper ldHelper;

  @Autowired
  private DataHelper dataHelper;
  private GenAISummaryCache genAISummaryCache;
  private SummaryDoc summaryDoc;

  @BeforeEach
  public void setup() {
    ReflectionTestUtils.setField(grpcClientHelper, "genAiInterfaceHost", "localhost");
    ReflectionTestUtils.setField(grpcClientHelper, "genAiInterfacePort", 8080);
    ReflectionTestUtils.setField(grpcClientHelper, "grpcMaxInboundMessageSize", 1024);
    ReflectionTestUtils.setField(grpcClientHelper, "grpcMaxOutboundMessageSize", 1024);
    ReflectionTestUtils.setField(grpcClientHelper, "grpcMaxIdleTime", 60);
    ReflectionTestUtils.setField(grpcClientHelper, "grpcKeepAliveTime", 30);
    ReflectionTestUtils.setField(grpcClientHelper, "grpcKeepAliveTimeout", 20);
    ReflectionTestUtils.setField(grpcClientHelper, "grpcCallDeadlineSeconds", 30);
    ReflectionTestUtils.setField(grpcClientHelper, "grpcMaxRetires", 3);

    grpcClientHelper.Init();
    ReflectionTestUtils.setField(grpcClientHelper, "llmInferenceClient", llmInferenceClient);
    lenient().when(llmInferenceClient.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
        .thenReturn(llmInferenceClient);

    genAISummaryCache =
        GenAISummaryCache.builder().isOnDemandSummaryEnabled(false).acwSummaryEnabled(true)
            .formatEnabled(false).sectionMap(Map.of("summary", List.of(
                GenAISummaryCache.SpokenEntity.builder().entityId("11111").variableId("22222")
                    .label("label1").placement("end of section").showLabel(true).build(),
                GenAISummaryCache.SpokenEntity.builder().entityId("abc0d45f33f1-449d8316ed60bf8a-6b57")
                    .variableId("abc0d45f33f1-449d8316ed60bf8a-6b57").label("ssn-label")
                    .placement("end of section").showLabel(true).build(),
                GenAISummaryCache.SpokenEntity.builder().entityId("4617cb7c25c2-4b7487a02e0cd2d3-9559")
                    .variableId("4617cb7c25c2-4b7487a02e0cd2d3-9559").label("phone-upgrade-label")
                    .placement("end of section").showLabel(true).build(),
                GenAISummaryCache.SpokenEntity.builder().entityId("a0ef0fd05ad9-453eb1601f28ba15-b128")
                    .variableId("a0ef0fd05ad9-453eb1601f28ba15-b128").label("wireless-number-label")
                    .placement("end of section").showLabel(true).build()))).build();

    // Use dataHelper to create a SummaryDoc
    try {
      summaryDoc = this.dataHelper.inputSummaryDocResponse(TestConstant.SUMMARY_DOC_RESPONSE_JSON);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  void givenTemplateBasedGenAISummaryFFIsEnabled_thenReturnLLMRequestWithTemplateIdAndVersion()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    String conversationId = "convId";
    String tenantId = "tenantId";
    GenAISummaryCache genAISummaryCache = GenAISummaryCache.builder().build();
    SummaryDoc summaryDoc =
        SummaryDoc.builder().templateId("templateId").templateVersion(1).build();
    String callType = "callType";

    Mockito.doReturn(true).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    Method method =
        GrpcClientHelper.class.getDeclaredMethod("createGenerativeSummaryRequest", String.class,
            String.class, GenAISummaryCache.class, SummaryDoc.class, String.class);
    method.setAccessible(Boolean.TRUE);

    CreateGenerativeSummaryRequest actualResponse =
        (CreateGenerativeSummaryRequest) method.invoke(this.grpcClientHelper, conversationId,
            tenantId, genAISummaryCache, summaryDoc, callType);

    Assertions.assertEquals("convId", actualResponse.getConversationId());
    Assertions.assertEquals("templateId", actualResponse.getTemplateId());
    Assertions.assertEquals(1, actualResponse.getVersion());
  }

  @Test
  public void testGetGenAiSummary_Request() {
    try {
      CreateGenerativeSummaryRequest request =
          CreateGenerativeSummaryRequest.newBuilder().setConversationId("testConversationId")
              .setTenantId("testTenantId").setCallType("test-call-type").build();
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.LLM_GEN_AI_SUMMARY_REQUEST), Mockito.anyMap());
      lenient().when(
              llmInferenceClient.createGenerativeSummary(any(CreateGenerativeSummaryRequest.class)))
          .thenReturn(CreateGenerativeSummaryResponse.newBuilder().build());

      Optional<CreateGenerativeSummaryResponse> response =
          grpcClientHelper.getGenAiSummary(request, "text-exp-id");
    } catch (Exception e) {
      Assertions.fail();
    }
  }

  @Test
  public void testGetGenAiSummary_AppliesConfiguredCallDeadline() {
    try {
      CreateGenerativeSummaryRequest request =
          CreateGenerativeSummaryRequest.newBuilder().setConversationId("testConversationId")
              .setTenantId("testTenantId").setCallType("test-call-type").build();
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.LLM_GEN_AI_SUMMARY_REQUEST), Mockito.anyMap());
      lenient().when(
              llmInferenceClient.createGenerativeSummary(any(CreateGenerativeSummaryRequest.class)))
          .thenReturn(CreateGenerativeSummaryResponse.newBuilder().build());

      grpcClientHelper.getGenAiSummary(request, "text-exp-id");

      Mockito.verify(llmInferenceClient).withDeadlineAfter(30L, TimeUnit.SECONDS);
    } catch (Exception e) {
      Assertions.fail();
    }
  }

  @Test
  public void testGetGenAiSummaryStream_AppliesConfiguredCallDeadline() {
    try {
      CreateGenerativeSummaryRequest request =
          CreateGenerativeSummaryRequest.newBuilder().setConversationId("testConversationId")
              .setTenantId("testTenantId").setCallType("test-call-type").build();
      lenient().when(llmInferenceClient.createGenerativeSummaryStream(
              any(CreateGenerativeSummaryRequest.class)))
          .thenReturn(Collections.emptyIterator());

      grpcClientHelper.getGenAiSummaryStream(request);

      Mockito.verify(llmInferenceClient).withDeadlineAfter(30L, TimeUnit.SECONDS);
    } catch (Exception e) {
      Assertions.fail();
    }
  }

  @Test
  public void testGetGenAiSummary_Request_Exception() {
    try {
      CreateGenerativeSummaryRequest request =
          CreateGenerativeSummaryRequest.newBuilder().setConversationId("testConversationId")
              .setTenantId("testTenantId").build();
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.LLM_GEN_AI_SUMMARY_REQUEST), Mockito.anyMap());
      lenient().when(
              llmInferenceClient.createGenerativeSummary(any(CreateGenerativeSummaryRequest.class)))
          .thenThrow(new RuntimeException("Test Exception"));

      Optional<CreateGenerativeSummaryResponse> response =
          grpcClientHelper.getGenAiSummary(request, "text-exp-id");

    } catch (Exception e) {
      Assertions.assertTrue(true);
    }

  }

  @Test
  public void testGetGenAiSummary_Parameters() {
    try {
      String conversationId = "testConversationId";
      String tenantId = "testTenantId";
      Mockito.doReturn(false).when(this.ldHelper).isFFEnabled(Constants.UASSIST_GENAI_OPT_IN_OUT_FF,
          LaunchDarklyConstants.FF_CONTEXT_KIND);
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      Mockito.doReturn(false).when(ldHelper)
          .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.LLM_GEN_AI_SUMMARY_REQUEST), Mockito.anyMap());
      lenient().when(
              llmInferenceClient.createGenerativeSummary(any(CreateGenerativeSummaryRequest.class)))
          .thenReturn(CreateGenerativeSummaryResponse.newBuilder().build());
      SummaryDoc summaryDoc = SummaryDoc.builder().environment("env").experienceId("expId").build();

      Optional<CreateGenerativeSummaryResponse> response =
          grpcClientHelper.getGenAiSummary(conversationId, tenantId, null, summaryDoc, null);
      Assert.notNull(response, "response is emmpty");
    } catch (Exception e) {
      Assertions.fail();
    }

  }

  @Test
  public void testGetGenAiSummary_WithTargetLangFFEnable() {
    try {
      String conversationId = "testConversationId";
      String tenantId = "testTenantId";
      Mockito.doReturn(false).when(this.ldHelper).isFFEnabled(Constants.UASSIST_GENAI_OPT_IN_OUT_FF,
          LaunchDarklyConstants.FF_CONTEXT_KIND);
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GENAI_TARGET_LANGUAGE_FF,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.LLM_GEN_AI_SUMMARY_REQUEST), Mockito.anyMap());
      lenient().when(
              llmInferenceClient.createGenerativeSummary(any(CreateGenerativeSummaryRequest.class)))
          .thenReturn(CreateGenerativeSummaryResponse.newBuilder().build());
      Mockito.doReturn(false).when(ldHelper)
          .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

      SummaryDoc summaryDoc = SummaryDoc.builder().environment("env").experienceId("expId").build();

      Optional<CreateGenerativeSummaryResponse> response =
          grpcClientHelper.getGenAiSummary(conversationId, tenantId,
              GenAISummaryCache.builder().targetLanguage("en-us").build(), summaryDoc,
              "test-calltype");
    } catch (Exception e) {
      Assertions.fail();
    }

  }

  @Test
  public void testGetGenAiSummaryWithSpokenEntities() {
    try {
      GenAISummaryCache genAISummaryCache1 =
          GenAISummaryCache.builder().isOnDemandSummaryEnabled(false).acwSummaryEnabled(true)
              .formatEnabled(false).sectionMap(Map.of("summary", List.of(
                  GenAISummaryCache.SpokenEntity.builder().entityId("abc0d45f33f1")
                      .variableId("abc0d45f33f1-449d8316ed60bf8a").label("label1")
                      .placement("end of the section").showLabel(true).type("PERSON").build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("abc0d45f33f1-449d8316ed60bf8a-6b57")
                      .variableId("abc0d45f33f1-449d8316ed60bf8a-6b57").label("ssn-label")
                      .placement("end of the section").showLabel(true).type("PERSON").build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("4617cb7c25c2-4b7487a02e0cd2d3-9559")
                      .variableId("4617cb7c25c2-4b7487a02e0cd2d3-9559").label("phone-upgrade-label")
                      .placement("end of the section").showLabel(true).type("PERSON").build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("a0ef0fd05ad9-453eb1601f28ba15-b128")
                      .variableId("a0ef0fd05ad9-453eb1601f28ba15-b128").label("wireless-number-label")
                      .placement("end of the section").showLabel(true).type("RULE").build()))).build();
      SummaryDoc summaryDoc1 =
          this.dataHelper.inputSummaryDocResponse(TestConstant.SUMMARY_DOC_RESPONSE_JSON);

      CreateGenerativeSummaryResponse mockResponse = CreateGenerativeSummaryResponse.newBuilder()
          .addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("call-reasons").build()).setText(
                  "The customer is calling to inquire about different flight options for a trip from Cincinnati to Traverse City, Michigan.")
              .build()).addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("agent-actions").build()).setText(
                  "The agent checked for flights on American Airlines, checked the cost of a flight with a layover, and checked the cost of a flight with a non-stop option. The agent also suggested that the customer check the cost of flights on the company's website.")
              .build()).addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("call-outcome").build()).setText(
                  "The outcome of the conversation was that the agent was unable to provide a specific answer to the customer's question about the cost of a flight with a layover but recommended checking the cost on the company's website.")
              .build()).addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("customer-sentiment").build()).setText(
                  "The customer seemed satisfied with the agent's resolution, although he was unable to get the exact information he was looking for.")
              .build()).build();
      CreateGenerativeSummaryRequest request =
          CreateGenerativeSummaryRequest.newBuilder().setConversationId("cfr3sh3hjl1dk9u130hl")
              .setTenantId("3e37631c-78ac-4137-8415-72dcd90b19fd").addSections(
                  Section.newBuilder().setId("call-reasons").addEntities(
                          SummaryEntity.newBuilder().setEntityLabel("label1").setEntityType("PERSON")
                              .setEntityValue("variable1").build()).addEntities(
                          SummaryEntity.newBuilder().setEntityLabel("ssn-label").setEntityType("PERSON")
                              .setEntityValue("ssn-value").build()).addEntities(
                          SummaryEntity.newBuilder().setEntityLabel("phone-upgrade-label")
                              .setEntityType("PERSON").setEntityValue("phone-upgrade-value").build())
                      .addEntities(SummaryEntity.newBuilder().setEntityLabel("wireless-number-label")
                          .setEntityValue("wireless-number-value").build()).build())
              .addSections(Section.newBuilder().setId("agent-actions").build())
              .addSections(Section.newBuilder().setId("call-outcome").build())
              .addSections(Section.newBuilder().setId("customer-sentiment").build()).build();
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.LLM_GEN_AI_SUMMARY_REQUEST), Mockito.anyMap());

      lenient().when(
              llmInferenceClient.createGenerativeSummary(any(CreateGenerativeSummaryRequest.class)))
          .thenReturn(mockResponse);

      Optional<CreateGenerativeSummaryResponse> response =
          grpcClientHelper.getGenAiSummary(summaryDoc1.getConversationId(),
              summaryDoc1.getTenantId(), genAISummaryCache1, summaryDoc1, null);
    } catch (Exception e) {
      Assertions.fail(e);
    }
  }

  @Test
  void getStreamingSummaryIter_ReturnsResponseIterator() {
    try {
      String conversationId = "testConversationId";
      String tenantId = "testTenantId";
      GenAISummaryCache genAISummaryCache = new GenAISummaryCache();
      SummaryDoc summaryDoc = new SummaryDoc();
      CreateGenerativeSummaryRequest request =
          CreateGenerativeSummaryRequest.newBuilder().setConversationId(conversationId)
              .setTenantId(tenantId).build();

      lenient().when(this.llmInferenceClient.createGenerativeSummaryStream(
              any(CreateGenerativeSummaryRequest.class)))
          .thenReturn(new Iterator<CreateGenerativeSummaryResponse>() {
            @Override
            public boolean hasNext() {
              return false;
            }

            @Override
            public CreateGenerativeSummaryResponse next() {
              return null;
            }
          });

      Optional<Iterator<CreateGenerativeSummaryResponse>> response =
          grpcClientHelper.getStreamingSummaryIter(conversationId, tenantId, genAISummaryCache,
              summaryDoc, null);

      assertTrue(response.isPresent());
    } catch (Exception e) {
      Assertions.fail();
    }

  }

  @Test
  void getStreamingSummaryIter_ReturnsResponseIteratorWithError() {
    try {
      String conversationId = "testConversationId";
      String tenantId = "testTenantId";
      GenAISummaryCache genAISummaryCache = new GenAISummaryCache();
      SummaryDoc summaryDoc = new SummaryDoc();
      CreateGenerativeSummaryRequest request =
          CreateGenerativeSummaryRequest.newBuilder().setConversationId(conversationId)
              .setTenantId(tenantId).build();

      lenient().when(this.llmInferenceClient.createGenerativeSummaryStream(
          any(CreateGenerativeSummaryRequest.class))).thenThrow(new RuntimeException());

      Optional<Iterator<CreateGenerativeSummaryResponse>> response =
          grpcClientHelper.getStreamingSummaryIter(conversationId, tenantId, genAISummaryCache,
              summaryDoc, null);

      assertFalse(response.isPresent());
    } catch (Exception e) {
      Assertions.assertTrue(true);
    }

  }

  @Test
  public void testGetGenAiSummaryWithSpokenEntities_WithSummaryOptsInDisabled() {
    try {
      GenAISummaryCache genAISummaryCache1 =
          GenAISummaryCache.builder().isOnDemandSummaryEnabled(false).acwSummaryEnabled(true)
              .formatEnabled(false).sectionMap(Map.of("summary", List.of(
                  GenAISummaryCache.SpokenEntity.builder().entityId("abc0d45f33f1")
                      .variableId("abc0d45f33f1-449d8316ed60bf8a").label("label1")
                      .placement("end of the section").showLabel(true).type("PERSON").build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("abc0d45f33f1-449d8316ed60bf8a-6b57")
                      .variableId("abc0d45f33f1-449d8316ed60bf8a-6b57").label("ssn-label")
                      .placement("end of the section").showLabel(true).type("PERSON").build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("4617cb7c25c2-4b7487a02e0cd2d3-9559")
                      .variableId("4617cb7c25c2-4b7487a02e0cd2d3-9559").label("phone-upgrade-label")
                      .placement("end of the section").showLabel(true).type("PERSON").build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("a0ef0fd05ad9-453eb1601f28ba15-b128")
                      .variableId("a0ef0fd05ad9-453eb1601f28ba15-b128").label("wireless-number-label")
                      .placement("end of the section").showLabel(true).type("RULE").build())))
              .summaryOptIn(GenAISummaryCache.SummaryOptIn.builder().isAgentStepsEnabled(false)
                  .isCallReasonEnabled(false).isOutcomeEnabled(false)
                  .isCustomerSatisfactionEnabled(false).build()).build();
      SummaryDoc summaryDoc1 =
          this.dataHelper.inputSummaryDocResponse(TestConstant.SUMMARY_DOC_RESPONSE_JSON);

      CreateGenerativeSummaryResponse mockResponse = CreateGenerativeSummaryResponse.newBuilder()
          .addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("call-reasons").build()).setText(
                  "The customer is calling to inquire about different flight options for a trip from Cincinnati to Traverse City, Michigan.")
              .build()).addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("agent-actions").build()).setText(
                  "The agent checked for flights on American Airlines, checked the cost of a flight with a layover, and checked the cost of a flight with a non-stop option. The agent also suggested that the customer check the cost of flights on the company's website.")
              .build()).addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("call-outcome").build()).setText(
                  "The outcome of the conversation was that the agent was unable to provide a specific answer to the customer's question about the cost of a flight with a layover but recommended checking the cost on the company's website.")
              .build()).addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("customer-sentiment").build()).setText(
                  "The customer seemed satisfied with the agent's resolution, although he was unable to get the exact information he was looking for.")
              .build()).build();
      CreateGenerativeSummaryRequest request =
          CreateGenerativeSummaryRequest.newBuilder().setConversationId("cfr3sh3hjl1dk9u130hl")
              .setTenantId("3e37631c-78ac-4137-8415-72dcd90b19fd").addSections(
                  Section.newBuilder().setId("call-reasons").addEntities(
                          SummaryEntity.newBuilder().setEntityLabel("label1").setEntityType("PERSON")
                              .setEntityValue("variable1").build()).addEntities(
                          SummaryEntity.newBuilder().setEntityLabel("ssn-label").setEntityType("PERSON")
                              .setEntityValue("ssn-value").build()).addEntities(
                          SummaryEntity.newBuilder().setEntityLabel("phone-upgrade-label")
                              .setEntityType("PERSON").setEntityValue("phone-upgrade-value").build())
                      .addEntities(SummaryEntity.newBuilder().setEntityLabel("wireless-number-label")
                          .setEntityValue("wireless-number-value").build()).build())
              .addSections(Section.newBuilder().setId("agent-actions").build())
              .addSections(Section.newBuilder().setId("call-outcome").build())
              .addSections(Section.newBuilder().setId("customer-sentiment").build()).build();
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.LLM_GEN_AI_SUMMARY_REQUEST), Mockito.anyMap());

      lenient().when(
              llmInferenceClient.createGenerativeSummary(any(CreateGenerativeSummaryRequest.class)))
          .thenReturn(mockResponse);

      Optional<CreateGenerativeSummaryResponse> response =
          grpcClientHelper.getGenAiSummary(summaryDoc1.getConversationId(),
              summaryDoc1.getTenantId(), genAISummaryCache1, summaryDoc1, null);
    } catch (Exception e) {
      Assertions.fail(e);
    }
  }

  @Test
  public void testGetGenAiSummaryWithSpokenEntities_WithSummaryOptsInEnabled() {
    try {
      GenAISummaryCache genAISummaryCache1 =
          GenAISummaryCache.builder().isOnDemandSummaryEnabled(false).acwSummaryEnabled(true)
              .formatEnabled(false).sectionMap(Map.of("summary", List.of(
                  GenAISummaryCache.SpokenEntity.builder().entityId("abc0d45f33f1")
                      .variableId("abc0d45f33f1-449d8316ed60bf8a").label("label1")
                      .placement("end of section").showLabel(true).type("PERSON").build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("abc0d45f33f1-449d8316ed60bf8a-6b57")
                      .variableId("abc0d45f33f1-449d8316ed60bf8a-6b57").label("ssn-label")
                      .placement("end of section").showLabel(true).type("PERSON").build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("4617cb7c25c2-4b7487a02e0cd2d3-9559")
                      .variableId("4617cb7c25c2-4b7487a02e0cd2d3-9559").label("phone-upgrade-label")
                      .placement("in-line").showLabel(true).type("PERSON").build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("a0ef0fd05ad9-453eb1601f28ba15-b128")
                      .variableId("a0ef0fd05ad9-453eb1601f28ba15-b128").label("wireless-number-label")
                      .placement("end of the section with mention label").showLabel(true).type("RULE")
                      .build()))).summaryOptIn(
                  GenAISummaryCache.SummaryOptIn.builder().isAgentStepsEnabled(true)
                      .isCallReasonEnabled(true).isOutcomeEnabled(true)
                      .isCustomerSatisfactionEnabled(true).build()).build();
      SummaryDoc summaryDoc1 =
          this.dataHelper.inputSummaryDocResponse(TestConstant.SUMMARY_DOC_RESPONSE_JSON);

      CreateGenerativeSummaryResponse mockResponse = CreateGenerativeSummaryResponse.newBuilder()
          .addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("call-reasons").build()).setText(
                  "The customer is calling to inquire about different flight options for a trip from Cincinnati to Traverse City, Michigan.")
              .build()).addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("agent-actions").build()).setText(
                  "The agent checked for flights on American Airlines, checked the cost of a flight with a layover, and checked the cost of a flight with a non-stop option. The agent also suggested that the customer check the cost of flights on the company's website.")
              .build()).addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("call-outcome").build()).setText(
                  "The outcome of the conversation was that the agent was unable to provide a specific answer to the customer's question about the cost of a flight with a layover but recommended checking the cost on the company's website.")
              .build()).addSectionText(SectionText.newBuilder()
              .setSection(Section.newBuilder().setId("customer-sentiment").build()).setText(
                  "The customer seemed satisfied with the agent's resolution, although he was unable to get the exact information he was looking for.")
              .build()).build();
      CreateGenerativeSummaryRequest request =
          CreateGenerativeSummaryRequest.newBuilder().setConversationId("cfr3sh3hjl1dk9u130hl")
              .setTenantId("3e37631c-78ac-4137-8415-72dcd90b19fd").addSections(
                  Section.newBuilder().setId("call-reasons").addEntities(
                          SummaryEntity.newBuilder().setEntityLabel("label1").setEntityType("PERSON")
                              .setEntityValue("variable1").build()).addEntities(
                          SummaryEntity.newBuilder().setEntityLabel("ssn-label").setEntityType("PERSON")
                              .setEntityValue("ssn-value").build()).addEntities(
                          SummaryEntity.newBuilder().setEntityLabel("phone-upgrade-label")
                              .setEntityType("PERSON").setEntityValue("phone-upgrade-value").build())
                      .addEntities(SummaryEntity.newBuilder().setEntityLabel("wireless-number-label")
                          .setEntityValue("wireless-number-value").build()).build())
              .addSections(Section.newBuilder().setId("agent-actions").build())
              .addSections(Section.newBuilder().setId("call-outcome").build())
              .addSections(Section.newBuilder().setId("customer-sentiment").build()).build();

      lenient().when(
              llmInferenceClient.createGenerativeSummary(any(CreateGenerativeSummaryRequest.class)))
          .thenReturn(mockResponse);

      Optional<CreateGenerativeSummaryResponse> response =
          grpcClientHelper.getGenAiSummary(summaryDoc1.getConversationId(),
              summaryDoc1.getTenantId(), genAISummaryCache1, summaryDoc1, null);
      assertNotNull(response);
    } catch (Exception e) {
      Assertions.fail(e);
    }
  }

  @Test
  public void testcreateGenerativeSummaryRequestNg() {
    String convId="test123";
    String tenantId="test123";
    String templateId="test123";
    int version=1;
    CreateGenerativeSummaryRequest generativeSummaryRequestNg =
        this.grpcClientHelper.createGenerativeSummaryRequestNg(convId, tenantId, templateId,
            version);
    assertNotNull(generativeSummaryRequestNg);
  }



}
