package com.uniphore.uassist.summary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.conversationmetric.v1.MetricScope;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.featureflag.Tenant;
import com.uniphore.llminference.v1.*;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.cache.GenAISummaryCache;
import com.uniphore.uassist.summary.cache.GenAISummaryConfigCacheHelper;
import com.uniphore.uassist.summary.cache.SummaryCacheHelper;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.CustomMetricsPublisher;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryUpdateEventLog;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.helper.DataHelper;
import com.uniphore.uassist.summary.helper.LookerGenAISummaryMetricHelper;
import com.uniphore.uassist.summary.model.ProtoConverter;
import com.uniphore.uassist.summary.util.GenAiSummaryHelper;
import com.uniphore.uassist.summary.util.GrpcClientHelper;
import com.uniphore.uassist.summary.util.SummaryHelper;
import com.uniphore.uassist.summary.utils.TestConstant;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.llminference.v1.PresentationFormat.PRESENTATION_FORMAT_NUMERIC;
import static com.uniphore.uassist.summary.utils.TestConstant.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers

public class GenAISummaryServiceTest extends ApplicationTest {
  @MockBean
  private SummaryDoc summaryDoc;
  @Value("${app.redis.summary-cache.ttl:3600000}")
  private long cachettl;
  @Autowired
  private GenAISummaryService genAISummaryService;

  @Autowired
  private CacheOperations<TypeReference<Tenant>> tenantCache;
  @MockBean
  private SummaryRuntimeDao summaryRuntimeDao;
  @MockBean
  private LaunchDarklyHelper ldHelper;
  @MockBean
  private SummaryHelper summaryHelper;
  @MockBean
  private GenAISummaryConfigCacheHelper genAiSummaryCacheHelper;
  @MockBean
  private GrpcClientHelper grpcClientHelper;
  private CreateGenerativeSummaryResponse createGenerativeSummaryResponse;
  @MockBean
  private SummaryCacheHelper summaryCacheHelper;
  @MockBean
  private CustomMetricsPublisher customMetricsPublisher;
  @Autowired
  private DataHelper dataHelper;
  @MockBean
  private ProtoConverter protoConverter;
  @MockBean
  private GenAiSummaryHelper genAiSummaryHelper;
  @MockBean
  private LookerGenAISummaryMetricHelper metricHelper;

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
      summaryDoc = this.dataHelper.inputSummaryDocResponse(TestConstant.SUMMARY_DOC_RESPONSE_JSON);
      Section section = Section.newBuilder().setId("sectionId").build();
      SectionText sectionText = SectionText.newBuilder().setSection(section).build();
      createGenerativeSummaryResponse =
          CreateGenerativeSummaryResponse.newBuilder().addSectionText(sectionText).build();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  void testCreateGenAiSummary_SummaryDocNull() {
    try {
      tenantCache.put(TestConstant.TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      Assertions.assertThrows(NullPointerException.class,
          () -> this.genAISummaryService.createGenAiSummary(TestConstant.CONVERSATION_ID, TENANT_ID,
              null, true, true, System.currentTimeMillis(), null));
    } catch (final Exception e) {
      log.error("exception at testCreateGenAiSummary_SummaryDocNull: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(1)
  void testCreateGenAISummaryShortCallEnableWithBothConditionTrue() {
    try {
      tenantCache.put(TestConstant.TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(UASSIST_GEN_AI_FORMAT_DISABLED, FF_CONTEXT_KIND);
      summaryDoc = this.dataHelper.inputSummaryDoc();
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(true).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(1).durationType("minutes")
                      .turns(GenAISummaryCache.Turns.builder().channel("customer").threshold(1).build())
                      .build()).build();
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      when(this.genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(
          ArgumentMatchers.any())).thenReturn(Optional.of(genAISummaryCache));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));
      SummaryUpdateEventLog summaryUpdateEventLog =
          SummaryUpdateEventLog.builder().id("id123").conversationId("123")
              .eventType(Constants.EVENT_TYPE).summary("summary").eventTime(3795253)
              .tenantId(TENANT_ID).environment("dev").businessProcessId("bp123").build();
      when(this.summaryHelper.getSummaryUpdateEventLog(ArgumentMatchers.any(), anyLong(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any())).thenReturn(summaryUpdateEventLog);
      this.genAISummaryService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
          Optional.of(EXPERIENCE_ID), true, true, System.currentTimeMillis(), null);
    } catch (final Exception e) {
      log.error("exception at testCreateGenAISummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(1)
  void testCreateGenAISummaryShortCallEnable() {
    try {
      tenantCache.put(TestConstant.TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(UASSIST_GEN_AI_FORMAT_DISABLED, FF_CONTEXT_KIND);
      summaryDoc = this.dataHelper.inputSummaryDoc();

      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(true).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(90).durationType("seconds")
                      .turns(GenAISummaryCache.Turns.builder().channel("both").threshold(5).build())
                      .build()).build();
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      when(this.genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(
          ArgumentMatchers.any())).thenReturn(Optional.of(genAISummaryCache));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          true);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));
      SummaryUpdateEventLog summaryUpdateEventLog =
          SummaryUpdateEventLog.builder().id("id123").conversationId("123")
              .eventType(Constants.EVENT_TYPE).summary("summary").eventTime(3795253)
              .tenantId(TENANT_ID).environment("dev").businessProcessId("bp123").build();
      when(this.summaryHelper.getSummaryUpdateEventLog(ArgumentMatchers.any(), anyLong(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any())).thenReturn(summaryUpdateEventLog);
      this.genAISummaryService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
          Optional.of(EXPERIENCE_ID), true, true, System.currentTimeMillis(), null);
    } catch (final Exception e) {
      log.error("exception at testCreateGenAISummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(2)
  void testCreateGenAISummary() {
    try {
      tenantCache.put(TestConstant.TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(UASSIST_GEN_AI_FORMAT_DISABLED, FF_CONTEXT_KIND);
      summaryDoc = this.dataHelper.inputSummaryDoc();
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).build();
      doNothing().when(metricHelper)
          .pushGenAICounterMetric(ArgumentMatchers.eq("test"), ArgumentMatchers.eq("convId"),
              ArgumentMatchers.eq("expId"), ArgumentMatchers.eq("200"), ArgumentMatchers.any(),ArgumentMatchers.any());
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      when(this.genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(
          ArgumentMatchers.any())).thenReturn(Optional.of(genAISummaryCache));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          true);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));
      SummaryUpdateEventLog summaryUpdateEventLog =
          SummaryUpdateEventLog.builder().id("id123").conversationId("123")
              .eventType(Constants.EVENT_TYPE).summary("summary").eventTime(3795253)
              .tenantId(TENANT_ID).environment("dev").businessProcessId("bp123").build();
      when(this.summaryHelper.getSummaryUpdateEventLog(ArgumentMatchers.any(), anyLong(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any())).thenReturn(summaryUpdateEventLog);
      this.genAISummaryService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
          Optional.of(EXPERIENCE_ID), true, true, System.currentTimeMillis(), null);
    } catch (final Exception e) {
      log.error("exception at testCreateGenAISummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(3)
  void testCreateGenAISummaryWithEntitiesEnhancementsEnabled() {
    try {
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      String tenantId = "3e37631c-78ac-4137-8415-72dcd90b19fd";
      Optional<String> experienceIdOpt = Optional.of("9e9d1fcc-f99d-400c-bfb8-95d79a6ba9e5");
      boolean publish = false;
      SummaryDoc summaryDoc1 =
          this.dataHelper.inputSummaryDocResponse(TestConstant.SUMMARY_DOC_RESPONSE_JSON);
      // Mock dependencies
      when(ldHelper.isFFEnabled(UASSIST_GEN_AI_FORMAT_DISABLED,
          FF_CONTEXT_KIND)).thenReturn(false);
      when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS,
          FF_CONTEXT_KIND)).thenReturn(true);
      when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
          FF_CONTEXT_KIND)).thenReturn(true);
      when(summaryHelper.getSummaryDoc(conversationId)).thenReturn(summaryDoc1);
      doNothing().when(metricHelper)
          .pushGenAICounterMetric(ArgumentMatchers.eq("test"), ArgumentMatchers.eq("convId"),
              ArgumentMatchers.eq("expId"), ArgumentMatchers.eq("200"), ArgumentMatchers.any(),ArgumentMatchers.any());
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().isOnDemandSummaryEnabled(false).acwSummaryEnabled(true)
              .formatEnabled(false).sectionMap(Map.of("summary", List.of(
                  GenAISummaryCache.SpokenEntity.builder().entityId("11111").variableId("22222")
                      .label("label1").placement("end of the section").showLabel(true).build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("abc0d45f33f1-449d8316ed60bf8a-6b57")
                      .variableId("abc0d45f33f1-449d8316ed60bf8a-6b57").label("ssn-label")
                      .placement("end of the section").showLabel(true).build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("4617cb7c25c2-4b7487a02e0cd2d3-9559")
                      .variableId("4617cb7c25c2-4b7487a02e0cd2d3-9559").label("phone-upgrade-label")
                      .placement("end of the section").showLabel(true).build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("a0ef0fd05ad9-453eb1601f28ba15-b128")
                      .variableId("a0ef0fd05ad9-453eb1601f28ba15-b128").label("wireless-number-label")
                      .placement("end of the section").showLabel(true).build()))).build();
      CreateGenerativeSummaryResponse createGenerativeSummaryResponse =
          CreateGenerativeSummaryResponse.newBuilder().addSectionText(SectionText.newBuilder()
              .setText(
                  "The customer is calling to inquire about different flight options for a trip from Cincinnati to Traverse City, Michigan.")
              .setSection(Section.newBuilder().setId("call-reasons").build())).addSectionText(
              SectionText.newBuilder().setText(
                      "The agent checked for flights on American Airlines, checked the cost of a flight with a layover, and checked the cost of a flight with a non-stop option. The agent also suggested that the customer check the cost of flights on the company's website.")
                  .setSection(Section.newBuilder().setId("agent-actions").build())).addSectionText(
              SectionText.newBuilder().setText(
                      "The outcome of the conversation was that the agent was unable to provide a specific answer to the customer's question about the cost of a flight with a layover but recommended checking the cost on the company's website.")
                  .setSection(Section.newBuilder().setId("call-outcome").build())).addSectionText(
              SectionText.newBuilder().setText(
                      "The customer seemed satisfied with the agent's resolution, although he was unable to get the exact information he was looking for.")
                  .setSection(Section.newBuilder().setId("customer-sentiment").build())).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(anyString())).thenReturn(
          Optional.of(genAISummaryCache));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(conversationId)).thenReturn(true);
      when(
          grpcClientHelper.getGenAiSummary(conversationId, tenantId, genAISummaryCache, summaryDoc1,
              null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryUpdateEventLog summaryUpdateEventLog =
          SummaryUpdateEventLog.builder().id("id123").conversationId(conversationId)
              .eventType(Constants.EVENT_TYPE).summary("summary").eventTime(3795253)
              .tenantId(tenantId).environment("dev").businessProcessId("bp123").build();

      when(summaryHelper.getSummaryUpdateEventLog(anyString(), anyLong(), anyString(), anyList(),
          anyString(), anyString())).thenReturn(summaryUpdateEventLog);
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.CREATE_GEN_AI_SUMMARY), Mockito.anyMap());
      summaryDoc.setConversationId(conversationId);
      genAISummaryService.createGenAiSummary(conversationId, tenantId, experienceIdOpt, publish,
          true, System.currentTimeMillis(), null);
    } catch (final Exception e) {
      log.error("exception at testCreateGenAISummarywithFF: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(4)
  void testCreateGenAISummaryWithEntitiesEnhancementsEnabledAndGenAiEntitiesToLLMEnabled() {
    try {
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      String tenantId = "3e37631c-78ac-4137-8415-72dcd90b19fd";
      Optional<String> experienceIdOpt = Optional.of("9e9d1fcc-f99d-400c-bfb8-95d79a6ba9e5");
      boolean publish = false;
      SummaryDoc summaryDoc1 =
          this.dataHelper.inputSummaryDocResponse(TestConstant.SUMMARY_DOC_RESPONSE_JSON);
      // Mock dependencies
      when(ldHelper.isFFEnabled(UASSIST_GEN_AI_FORMAT_DISABLED,
          FF_CONTEXT_KIND)).thenReturn(false);
      when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS,
          FF_CONTEXT_KIND)).thenReturn(true);
      when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
          FF_CONTEXT_KIND)).thenReturn(true);
      when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_STREAM_SUMMARY_FF,
          FF_CONTEXT_KIND)).thenReturn(true);

      when(summaryHelper.getSummaryDoc(conversationId)).thenReturn(summaryDoc1);
      doNothing().when(metricHelper)
          .pushGenAICounterMetric(ArgumentMatchers.eq("test"), ArgumentMatchers.eq("convId"),
              ArgumentMatchers.eq("expId"), ArgumentMatchers.eq("200"),ArgumentMatchers.any(), ArgumentMatchers.any());
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().isOnDemandSummaryEnabled(false).summaryFormat(
                  GenAISummaryCache.SummaryFormat.builder().callReason("number-list")
                      .agentSteps("bullet-point").outcome("paragraph").build()).acwSummaryEnabled(true)
              .formatEnabled(false).sectionMap(Map.of("summary", List.of(
                  GenAISummaryCache.SpokenEntity.builder().entityId("11111").variableId("22222")
                      .label("label1").placement("end of the section").showLabel(true).build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("abc0d45f33f1-449d8316ed60bf8a-6b57")
                      .variableId("abc0d45f33f1-449d8316ed60bf8a-6b57").label("ssn-label")
                      .placement("end of the section").showLabel(true).build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("4617cb7c25c2-4b7487a02e0cd2d3-9559")
                      .variableId("4617cb7c25c2-4b7487a02e0cd2d3-9559").label("phone-upgrade-label")
                      .placement("end of the section").showLabel(true).build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("a0ef0fd05ad9-453eb1601f28ba15-b128")
                      .variableId("a0ef0fd05ad9-453eb1601f28ba15-b128").label("wireless-number-label")
                      .placement("end of the section").showLabel(true).build()))).build();
      CreateGenerativeSummaryResponse createGenerativeSummaryResponse =
          getCreateGenerativeSummaryResponse();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(anyString())).thenReturn(
          Optional.of(genAISummaryCache));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(conversationId)).thenReturn(false);
      when(
          grpcClientHelper.getGenAiSummary(conversationId, tenantId, genAISummaryCache, summaryDoc1,
              null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryUpdateEventLog summaryUpdateEventLog =
          SummaryUpdateEventLog.builder().id("id123").conversationId(conversationId)
              .eventType(Constants.EVENT_TYPE).summary("summary").eventTime(3795253)
              .tenantId(tenantId).environment("dev").businessProcessId("bp123").build();
      when(summaryHelper.getSummaryDocById(conversationId)).thenReturn(Optional.of(summaryDoc1));
      when(summaryHelper.getSummaryUpdateEventLog(anyString(), anyLong(), anyString(), anyList(),
          anyString(), anyString())).thenReturn(summaryUpdateEventLog);
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.CREATE_GEN_AI_SUMMARY), Mockito.anyMap());
      summaryDoc.setConversationId(conversationId);
      genAISummaryService.createGenAiSummary(conversationId, tenantId, experienceIdOpt, publish,
          true, System.currentTimeMillis(), null);

      // Verify Redis DELETE
      verify(summaryHelper, times(1)).removeSummaryDoc(eq(conversationId));

    } catch (final Exception e) {
      log.error("exception at testCreateGenAISummarywithFF: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(5)
  void testCreateGenAISummaryWithEntitiesEnhancementsEnabledAndGenAiEntitiesToLLMEnabledUnFormatted() {
    try {
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      String tenantId = "3e37631c-78ac-4137-8415-72dcd90b19fd";
      Optional<String> experienceIdOpt = Optional.of("9e9d1fcc-f99d-400c-bfb8-95d79a6ba9e5");
      boolean publish = false;
      SummaryDoc summaryDoc1 =
          this.dataHelper.inputSummaryDocResponse(TestConstant.SUMMARY_DOC_RESPONSE_JSON);
      // Mock dependencies
      when(ldHelper.isFFEnabled(UASSIST_GEN_AI_FORMAT_DISABLED,
          FF_CONTEXT_KIND)).thenReturn(false);
      when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS,
          FF_CONTEXT_KIND)).thenReturn(true);
      when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
          FF_CONTEXT_KIND)).thenReturn(true);
      when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_STREAM_SUMMARY_FF,
          FF_CONTEXT_KIND)).thenReturn(true);
      when(summaryHelper.getSummaryDoc(conversationId)).thenReturn(summaryDoc1);
      doNothing().when(metricHelper)
          .pushGenAICounterMetric(ArgumentMatchers.eq("test"), ArgumentMatchers.eq("convId"),
              ArgumentMatchers.eq("expId"), ArgumentMatchers.eq("200"),ArgumentMatchers.any(), ArgumentMatchers.any());
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().isOnDemandSummaryEnabled(false).acwSummaryEnabled(true)
              .formatEnabled(false).sectionMap(Map.of("summary", List.of(
                  GenAISummaryCache.SpokenEntity.builder().entityId("11111").variableId("22222")
                      .label("label1").placement("end of the section").showLabel(true).build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("abc0d45f33f1-449d8316ed60bf8a-6b57")
                      .variableId("abc0d45f33f1-449d8316ed60bf8a-6b57").label("ssn-label")
                      .placement("end of the section").showLabel(true).build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("4617cb7c25c2-4b7487a02e0cd2d3-9559")
                      .variableId("4617cb7c25c2-4b7487a02e0cd2d3-9559").label("phone-upgrade-label")
                      .placement("end of the section").showLabel(true).build(),
                  GenAISummaryCache.SpokenEntity.builder()
                      .entityId("a0ef0fd05ad9-453eb1601f28ba15-b128")
                      .variableId("a0ef0fd05ad9-453eb1601f28ba15-b128").label("wireless-number-label")
                      .placement("end of the section").showLabel(true).build()))).build();
      CreateGenerativeSummaryResponse createGenerativeSummaryResponse =
          getCreateGenerativeSummaryResponse();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(anyString())).thenReturn(
          Optional.of(genAISummaryCache));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(conversationId)).thenReturn(true);
      when(
          grpcClientHelper.getGenAiSummary(conversationId, tenantId, genAISummaryCache, summaryDoc1,
              null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryUpdateEventLog summaryUpdateEventLog =
          SummaryUpdateEventLog.builder().id("id123").conversationId(conversationId)
              .eventType(Constants.EVENT_TYPE).summary("summary").eventTime(3795253)
              .tenantId(tenantId).environment("dev").businessProcessId("bp123").build();

      when(summaryHelper.getSummaryUpdateEventLog(anyString(), anyLong(), anyString(), anyList(),
          anyString(), anyString())).thenReturn(summaryUpdateEventLog);
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.CREATE_GEN_AI_SUMMARY), Mockito.anyMap());
      summaryDoc.setConversationId(conversationId);
      genAISummaryService.createGenAiSummary(conversationId, tenantId, experienceIdOpt, publish,
          true, System.currentTimeMillis(), null);
    } catch (final Exception e) {
      log.error("exception at testCreateGenAISummarywithFF: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  private @NotNull CreateGenerativeSummaryResponse getCreateGenerativeSummaryResponse() {
    CreateGenerativeSummaryResponse createGenerativeSummaryResponse =
        CreateGenerativeSummaryResponse.newBuilder().addSectionText(SectionText.newBuilder()
            .setText(
                "The customer is calling to inquire about different flight options for a trip from Cincinnati to Traverse City, Michigan. The following entities were not found: presented upgrade options, chosen upgrade option, phone number, social identification number, phone opted.")
            .setSection(Section.newBuilder().setId("call-reasons").build())).addSectionText(
            SectionText.newBuilder().setText(
                    "The agent checked for flights on American Airlines, checked the cost of a flight with a layover, and checked the cost of a flight with a non-stop option. The agent also suggested that the customer check the cost of flights on the company's website. The following entities were not found: presented upgrade options, chosen upgrade option, phone number, social identification number, phone opted.")
                .setSection(Section.newBuilder().setId("agent-actions").build())).addSectionText(
            SectionText.newBuilder().setText(
                    "The outcome of the conversation was that the agent was unable to provide a specific answer to the customer's question about the cost of a flight with a layover but recommended checking the cost on the company's website. The following entities were not found: phone-upgrade-label.")
                .setSection(Section.newBuilder().setId("outcome").build())).addSectionText(
            SectionText.newBuilder().setText(
                    "The customer seemed satisfied with the agent's resolution, although he was unable to get the exact information he was looking for.")
                .setSection(Section.newBuilder().setId("customer-sentiment").build())).build();
    return createGenerativeSummaryResponse;
  }

  @Test
  @Order(6)
  void testCreateGenAiSummaryWithoutModelOpt() {
    try {
      tenantCache.put(TestConstant.TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDoc(CONVERSATION_ID)).thenReturn(summaryDoc);
      doNothing().when(metricHelper)
          .pushGenAICounterMetric(ArgumentMatchers.eq("test"), ArgumentMatchers.eq("convId"),
              ArgumentMatchers.eq("expId"), ArgumentMatchers.eq("200"), ArgumentMatchers.any(),ArgumentMatchers.any());
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().isOnDemandSummaryEnabled(true).acwSummaryEnabled(true)
              .formatEnabled(true).summaryFormat(
                  GenAISummaryCache.SummaryFormat.builder().outcome("outcome")
                      .callReason("bullet-point").agentSteps(Constants.ADD_AGENT_STEP).build()).build();
      when(this.genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(EXPERIENCE_ID)).thenReturn(
          Optional.of(genAISummaryCache));
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.CREATE_GEN_AI_SUMMARY), Mockito.anyMap());
      this.genAISummaryService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
          Optional.of(EXPERIENCE_ID), true, true, System.currentTimeMillis(), null);
    } catch (final Exception e) {
      log.error("exception at testCreateGenAISummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(7)
  void testCreateGenAiSummaryFormatDisabled() throws Exception {
    tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
    });
    summaryDoc = this.dataHelper.inputSummaryDoc();
    when(this.summaryHelper.getSummaryDoc(CONVERSATION_ID)).thenReturn(summaryDoc);
    GenAISummaryCache genAISummaryCache =
        GenAISummaryCache.builder().isOnDemandSummaryEnabled(true).acwSummaryEnabled(true)
            .formatEnabled(false).summaryFormat(
                GenAISummaryCache.SummaryFormat.builder().outcome("outcome").callReason("number-list")
                    .agentSteps(Constants.ADD_AGENT_STEP).build()).build();
    when(this.genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(EXPERIENCE_ID)).thenReturn(
        Optional.of(genAISummaryCache));
    doNothing().when(metricHelper)
        .pushGenAICounterMetric(ArgumentMatchers.eq("test"), ArgumentMatchers.eq("convId"),
            ArgumentMatchers.eq("expId"), ArgumentMatchers.eq("200"),ArgumentMatchers.any(), ArgumentMatchers.any());
    createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
        SectionText.newBuilder().setText("text")
            .setSection(Section.newBuilder().setId(Constants.CUSTOMER_SENTIMENT).build())).build();
    when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
        summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));
    this.genAISummaryService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
        Optional.of(EXPERIENCE_ID), true, false, System.currentTimeMillis(), null);
  }

  @Test
  @Order(8)
  void testCreateGenAiSummaryFormatSummary() throws Exception {
    tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
    });
    summaryDoc = this.dataHelper.inputSummaryDoc();
    when(this.summaryHelper.getSummaryDoc(CONVERSATION_ID)).thenReturn(summaryDoc);
    GenAISummaryCache genAISummaryCache =
        GenAISummaryCache.builder().isOnDemandSummaryEnabled(true).acwSummaryEnabled(true)
            .formatEnabled(true).summaryFormat(
                GenAISummaryCache.SummaryFormat.builder().outcome("outcome").callReason("number-list")
                    .agentSteps(Constants.ADD_AGENT_STEP).build()).build();
    when(this.genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(EXPERIENCE_ID)).thenReturn(
        Optional.of(genAISummaryCache));
    doNothing().when(metricHelper)
        .pushGenAICounterMetric(ArgumentMatchers.eq("test"), ArgumentMatchers.eq("convId"),
            ArgumentMatchers.eq("expId"), ArgumentMatchers.eq("200"),ArgumentMatchers.any(), ArgumentMatchers.any());
    createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
        SectionText.newBuilder().setText("text")
            .setSection(Section.newBuilder().setId(Constants.AGENT_ACTIONS).build())).build();
    when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
        summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));
    this.genAISummaryService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
        Optional.of(EXPERIENCE_ID), true, false, System.currentTimeMillis(), null);
  }

  @Test
  @Order(9)
  void testResetGenAIRequested() {
    try {
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      this.genAISummaryService.resetGenAIRequested(CONVERSATION_ID);
    } catch (Exception e) {
      log.error("exception at testResetGenAIRequested : {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(10)
  public void testCreateGenAISummaryWithEntitiesEnhancementsEnabledAndFormatEnabled() {
    try {
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      String tenantId = "3e37631c-78ac-4137-8415-72dcd90b19fd";
      Optional<String> experienceIdOpt = Optional.of("9e9d1fcc-f99d-400c-bfb8-95d79a6ba9e5");
      boolean publish = false;

      SummaryDoc summaryDoc1 =
          this.dataHelper.inputSummaryDocResponse(TestConstant.SUMMARY_DOC_RESPONSE_JSON);
      when(summaryHelper.getSummaryDoc(conversationId)).thenReturn(summaryDoc1);

      // Mock dependencies
      when(ldHelper.isFFEnabled(UASSIST_GEN_AI_FORMAT_DISABLED,
          FF_CONTEXT_KIND)).thenReturn(false);
      when(ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS,
          FF_CONTEXT_KIND)).thenReturn(true);

      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().isOnDemandSummaryEnabled(false).acwSummaryEnabled(true)
              .formatEnabled(true).summaryFormat(
                  GenAISummaryCache.SummaryFormat.builder().callReason("number-list")
                      .agentSteps("bullet-point").outcome("paragraph").build()).sectionMap(
                  Map.of("call-reasons", List.of(GenAISummaryCache.SpokenEntity.builder()
                          .entityId("33490498778017231721721698379634")
                          .variableId("abc0d45f33f1-449d8316ed60bf8a-6b57").name("Social Security Number")
                          .label("SSN number").placement("end of section").showLabel(true).build()),
                      "agent-actions", List.of(GenAISummaryCache.SpokenEntity.builder()
                          .entityId("42699425466129049731721698389887")
                          .variableId("4617cb7c25c2-4b7487a02e0cd2d3-9559").name("Phone Upgrade")
                          .label("Phone upgrade").placement("end of section").showLabel(true).build()),
                      "call-outcome", List.of(GenAISummaryCache.SpokenEntity.builder()
                          .entityId("38899027094608489141721698382727")
                          .variableId("a0ef0fd05ad9-453eb1601f28ba15-b128")
                          .name("Customer Wireless Number").label("Wireless number")
                          .placement("end of section").showLabel(true).build()))).build();

      CreateGenerativeSummaryResponse createGenerativeSummaryResponse =
          CreateGenerativeSummaryResponse.newBuilder().addSectionText(SectionText.newBuilder()
              .setText(
                  "The customer is calling to inquire about different flight options for a trip from Cincinnati to Traverse City, Michigan.")
              .setSection(Section.newBuilder().setId("call-reasons").build())).addSectionText(
              SectionText.newBuilder().setText(
                      "The agent checked for flights on American Airlines, checked the cost of a flight with a layover, and checked the cost of a flight with a non-stop option. The agent also suggested that the customer check the cost of flights on the company's website.")
                  .setSection(Section.newBuilder().setId("agent-actions").build())).addSectionText(
              SectionText.newBuilder().setText(
                      "The outcome of the conversation was that the agent was unable to provide a specific answer to the customer's question about the cost of a flight with a layover but recommended checking the cost on the company's website.")
                  .setSection(Section.newBuilder().setId("call-outcome").build())).addSectionText(
              SectionText.newBuilder().setText(
                      "The customer seemed satisfied with the agent's resolution, although he was unable to get the exact information he was looking for.")
                  .setSection(Section.newBuilder().setId("customer-sentiment").build())).build();

      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(anyString())).thenReturn(
          Optional.of(genAISummaryCache));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(conversationId)).thenReturn(true);
      when(
          grpcClientHelper.getGenAiSummary(conversationId, tenantId, genAISummaryCache, summaryDoc1,
              null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryUpdateEventLog summaryUpdateEventLog =
          SummaryUpdateEventLog.builder().id("id123").conversationId(conversationId)
              .eventType(Constants.EVENT_TYPE).summary("summary").eventTime(3795253)
              .tenantId(tenantId).environment("dev").businessProcessId("bp123").build();

      when(summaryHelper.getSummaryUpdateEventLog(anyString(), anyLong(), anyString(), anyList(),
          anyString(), anyString())).thenReturn(summaryUpdateEventLog);
      Mockito.doNothing().when(customMetricsPublisher)
          .counterUpdateMap(Mockito.eq(Constants.CREATE_GEN_AI_SUMMARY), Mockito.anyMap());
      genAISummaryService.createGenAiSummary(conversationId, tenantId, experienceIdOpt, publish,
          false, System.currentTimeMillis(), null);

      // Verify results
      /*
       * assertNotNull(summaryDoc.getGenAiSummary());
       * assertTrue(summaryDoc.getGenAiSummary().getSections().stream().anyMatch(
       * section -> section.getSpokenEntities() != null));
       *
       * // Verify that each expected spoken entity is present in the sections
       * List<GenAiSummaryDoc.SpokenEntity> spokenEntities =
       * summaryDoc.getGenAiSummary().getSections().stream()
       * .flatMap(section -> section.getSpokenEntities().stream())
       * .collect(Collectors.toList());
       *
       * assertTrue(spokenEntities.stream().anyMatch(entity ->
       * "ssn-value".equals(entity.getValue())));
       * assertTrue(spokenEntities.stream().anyMatch(entity ->
       * "phone-upgrade-value".equals(entity.getValue())));
       * assertTrue(spokenEntities.stream().anyMatch(entity ->
       * "wireless-number-value".equals(entity.getValue())));
       * assertTrue(spokenEntities.stream().anyMatch(entity ->
       * "SSN number".equals(entity.getLabel())));
       * assertTrue(spokenEntities.stream().anyMatch(entity ->
       * "Phone upgrade".equals(entity.getLabel())));
       * assertTrue(spokenEntities.stream().anyMatch(entity ->
       * "Wireless number".equals(entity.getLabel())));
       *
       * verify(summaryHelper).saveSummaryDoc(any());
       */
    } catch (final Exception e) {
      log.error(
          "exception at testCreateGenAISummaryWithEntitiesEnhancementsEnabledAndFormatEnabled: {}",
          e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(11)
  void createGenAiSummary_UsesStreamingWhenFeatureFlagEnabled() throws Exception {
    String conversationId = "testConversationId";
    String tenantId = "testTenantId";
    GenAISummaryCache config = new GenAISummaryCache();
    SummaryDoc summaryDoc = new SummaryDoc();
    Iterator<CreateGenerativeSummaryResponse> mockIterator = Mockito.mock(Iterator.class);
    CreateGenerativeSummaryResponse mockResponse =
        CreateGenerativeSummaryResponse.newBuilder().build();

    Mockito.when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(true);
    Mockito.when(
        grpcClientHelper.getStreamingSummaryIter(conversationId, tenantId, config, summaryDoc,
            null)).thenReturn(Optional.of(mockIterator));
    Mockito.doNothing().when(genAiSummaryHelper)
        .processStream(mockIterator, conversationId, config, summaryDoc,
            System.currentTimeMillis());

    Optional<Iterator<CreateGenerativeSummaryResponse>> result =
        grpcClientHelper.getStreamingSummaryIter(conversationId, tenantId, config, summaryDoc,
            null);

    assertTrue(result.isPresent());
  }

  @Order(12)
  @Test
  void createGenAiSummary_UsesNonStreamingWhenFeatureFlagDisabled() throws Exception {
    String conversationId = "testConversationId";
    String tenantId = "testTenantId";
    GenAISummaryCache config = new GenAISummaryCache();
    SummaryDoc summaryDoc = new SummaryDoc();
    CreateGenerativeSummaryResponse mockResponse =
        CreateGenerativeSummaryResponse.newBuilder().build();

    Mockito.when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND))
        .thenReturn(false);
    Mockito.when(
            grpcClientHelper.getGenAiSummary(conversationId, tenantId, config, summaryDoc, null))
        .thenReturn(Optional.of(mockResponse));

    Optional<CreateGenerativeSummaryResponse> result =
        grpcClientHelper.getGenAiSummary(conversationId, tenantId, config, summaryDoc, null);

    Assertions.assertTrue(result.isPresent());
    Assertions.assertEquals(mockResponse, result.get());
  }

  @Test
  void testCreateGenAiSummary_HandlesInterruptedException() throws Exception {
    // Arrange: Simulate InterruptedException in checkSummaryInDbWithRetries

    GenAISummaryService spyService = spy(genAISummaryService);
    doThrow(new InterruptedException("Thread interrupted")).when(spyService)
        .checkSummaryInDbWithRetries(anyString());
    when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
        Optional.of(summaryDoc));
    GenAISummaryCache genAISummaryCache = GenAISummaryCache.builder().build();
    when(this.genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
        Optional.of(genAISummaryCache));
    when(this.summaryCacheHelper.acquireGenAiSummaryGenerationLock(any())).thenReturn(true);

    // Act
    SummaryDoc result =
        spyService.createGenAiSummary(summaryDoc.getConversationId(), summaryDoc.getTenantId(), Optional.of("expId"), false,
            true, 123L, null);
  }

  @Test
  @Order(13)
  void testCreateGenAISummary_1() {
    try {
      tenantCache.put(TestConstant.TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(UASSIST_GEN_AI_FORMAT_DISABLED, FF_CONTEXT_KIND);
      summaryDoc = this.dataHelper.inputSummaryDoc();
      SummaryDoc summaryDoc1 = mock(SummaryDoc.class);
      when(summaryDoc1.getGenAiSummary()).thenReturn(mock(GenAiSummaryDoc.class));
      when(summaryRuntimeDao.getSummaryDoc(anyString())).thenReturn(
          Optional.ofNullable(summaryDoc1));
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).build();
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      when(this.genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(
          ArgumentMatchers.any())).thenReturn(Optional.of(genAISummaryCache));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          true);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));
      doNothing().when(metricHelper)
          .pushGenAICounterMetric(ArgumentMatchers.eq("test"), ArgumentMatchers.eq("convId"),
              ArgumentMatchers.eq("expId"), ArgumentMatchers.eq("200"), ArgumentMatchers.any(),ArgumentMatchers.any());
      SummaryUpdateEventLog summaryUpdateEventLog =
          SummaryUpdateEventLog.builder().id("id123").conversationId("123")
              .eventType(Constants.EVENT_TYPE).summary("summary").eventTime(3795253)
              .tenantId(TENANT_ID).environment("dev").businessProcessId("bp123").build();
      when(this.summaryHelper.getSummaryUpdateEventLog(ArgumentMatchers.any(), anyLong(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any())).thenReturn(summaryUpdateEventLog);
      this.genAISummaryService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
          Optional.of(EXPERIENCE_ID), true, false, System.currentTimeMillis(), null);
    } catch (final Exception e) {
      log.error("exception at testCreateGenAISummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(14)
  public void testShortCallDisabledShouldGenerateSummary() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(false).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(90).durationType("seconds")
                      .durationType("seconds")
                      .turns(GenAISummaryCache.Turns.builder().channel("both").threshold(5).build())
                      .build()).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(this.summaryRuntimeDao.getSummaryDoc(ArgumentMatchers.anyString())).thenReturn(
          Optional.of(summaryDoc));
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(false);
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  public void testCreateGenAISummaryIfConfigNotFoundInCache() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(false).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(90).durationType("seconds")
                      .durationType("seconds")
                      .turns(GenAISummaryCache.Turns.builder().channel("both").threshold(5).build())
                      .build()).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.empty());
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(this.summaryRuntimeDao.getSummaryDoc(ArgumentMatchers.anyString())).thenReturn(
          Optional.of(summaryDoc));
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(false);
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      Assertions.assertNotNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  public void testCreateGenAISummaryIfExpNotPresent() {
    try {
      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.empty(), true,
              false, System.currentTimeMillis(), null);

      Assertions.assertNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(15)
  public void testShortCallEnabledCallDurationBelowThreshold() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(true).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(90).durationType("seconds")
                      .durationType("seconds")
                      .turns(GenAISummaryCache.Turns.builder().channel("agent").threshold(6).build())
                      .build()).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(this.summaryRuntimeDao.getSummaryDoc(ArgumentMatchers.anyString())).thenReturn(
          Optional.of(summaryDoc));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          true);
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(false);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(16)
  public void testShortCallEnabledCallDurationBelowThresholdFormatDisabled() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .formatEnabled(true).isOnDemandSummaryEnabled(true).shortCallEnabled(true)
              .shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(90).durationType("seconds")
                      .durationType("seconds").turns(
                          GenAISummaryCache.Turns.builder().channel("agent").threshold(6).build())
                      .build()).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(this.summaryRuntimeDao.getSummaryDoc(ArgumentMatchers.anyString())).thenReturn(
          Optional.of(summaryDoc));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          true);
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(false);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              true, System.currentTimeMillis(), null);

      assertNotNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(17)
  public void testShortCallEnabledFormatEnableIsFalse() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().acwSummaryEnabled(false).formatEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(true).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(90).durationType("seconds")
                      .durationType("seconds")
                      .turns(GenAISummaryCache.Turns.builder().channel("agent").threshold(6).build())
                      .build()).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(this.summaryRuntimeDao.getSummaryDoc(ArgumentMatchers.anyString())).thenReturn(
          Optional.of(summaryDoc));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          true);
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(18)
  public void testShortCallEnabledFormatDisabled() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().acwSummaryEnabled(false).formatEnabled(true)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(true).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(90).durationType("seconds")
                      .durationType("seconds")
                      .turns(GenAISummaryCache.Turns.builder().channel("agent").threshold(6).build())
                      .build()).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(this.summaryRuntimeDao.getSummaryDoc(ArgumentMatchers.anyString())).thenReturn(
          Optional.of(summaryDoc));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          true);
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(19)
  public void testShortCallEnabledFormatEnable() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(true).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(5).durationType("seconds")
                      .durationType("seconds")
                      .turns(GenAISummaryCache.Turns.builder().channel("agent").threshold(5).build())
                      .build()).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(this.summaryRuntimeDao.getSummaryDoc(ArgumentMatchers.anyString())).thenReturn(
          Optional.of(summaryDoc));
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      Optional<Iterator<CreateGenerativeSummaryResponse>> createGenerativeSummaryResponseIterator =
          Optional.of(Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                  .addSectionText(SectionText.newBuilder().setText("text")
                      .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build())
              .iterator());

      when(grpcClientHelper.getStreamingSummaryIter(anyString(), anyString(), any(), any(),
          any())).thenReturn(createGenerativeSummaryResponseIterator);
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(20)
  public void testSummaryDocGenerated() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(true).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(5).durationType("seconds")
                      .durationType("seconds")
                      .turns(GenAISummaryCache.Turns.builder().channel("agent").threshold(5).build())
                      .build()).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(summaryRuntimeDao.getSummaryDoc(anyString())).thenReturn(
          Optional.ofNullable(summaryDoc));
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          true);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      Optional<Iterator<CreateGenerativeSummaryResponse>> createGenerativeSummaryResponseIterator =
          Optional.of(Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                  .addSectionText(SectionText.newBuilder().setText("text")
                      .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build())
              .iterator());

      when(grpcClientHelper.getStreamingSummaryIter(anyString(), anyString(), any(), any(),
          any())).thenReturn(createGenerativeSummaryResponseIterator);
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(21)
  public void testShortCallEnableFalseCondition() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(true).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(5).durationType("minutes")
                      .message("msg")
                      .turns(GenAISummaryCache.Turns.builder().channel("agent").threshold(7).build())
                      .build()).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(summaryRuntimeDao.getSummaryDoc(anyString())).thenReturn(
          Optional.ofNullable(summaryDoc));
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      Optional<Iterator<CreateGenerativeSummaryResponse>> createGenerativeSummaryResponseIterator =
          Optional.of(Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                  .addSectionText(SectionText.newBuilder().setText("text")
                      .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build())
              .iterator());

      when(grpcClientHelper.getStreamingSummaryIter(anyString(), anyString(), any(), any(),
          any())).thenReturn(createGenerativeSummaryResponseIterator);
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              true, System.currentTimeMillis(), null);

      assertNotNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(22)
  public void testShortCallEnableFalseConditionForElse() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(true).shortCallConfig(
                  GenAISummaryCache.ShortCallConfig.builder().duration(5).durationType("minutes")
                      .message("msg")
                      .turns(GenAISummaryCache.Turns.builder().channel("agent").threshold(7).build())
                      .build()).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(summaryRuntimeDao.getSummaryDoc(anyString())).thenReturn(
          Optional.ofNullable(summaryDoc));
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      Optional<Iterator<CreateGenerativeSummaryResponse>> createGenerativeSummaryResponseIterator =
          Optional.of(Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                  .addSectionText(SectionText.newBuilder().setText("text")
                      .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build())
              .iterator());


      when(grpcClientHelper.getStreamingSummaryIter(anyString(), anyString(), any(), any(),
          any())).thenReturn(createGenerativeSummaryResponseIterator);
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(23)
  void testCreateGenAISummary_callType() {
    try {
      tenantCache.put(TestConstant.TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(UASSIST_GEN_AI_FORMAT_DISABLED, FF_CONTEXT_KIND);
      summaryDoc = this.dataHelper.inputSummaryDoc();
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).build();
      doNothing().when(metricHelper)
          .pushGenAICounterMetric(ArgumentMatchers.eq("test"), ArgumentMatchers.eq("convId"),
              ArgumentMatchers.eq("expId"), ArgumentMatchers.eq("200"),ArgumentMatchers.any(), ArgumentMatchers.any());
      when(this.summaryHelper.getSummaryDoc(ArgumentMatchers.any())).thenReturn(summaryDoc);
      when(this.genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(
          ArgumentMatchers.any())).thenReturn(Optional.of(genAISummaryCache));
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          true);
      createGenerativeSummaryResponse = CreateGenerativeSummaryResponse.newBuilder().addSectionText(
          SectionText.newBuilder().setText("text")
              .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();
      when(this.grpcClientHelper.getGenAiSummary(CONVERSATION_ID, TENANT_ID, genAISummaryCache,
          summaryDoc, null)).thenReturn(Optional.of(createGenerativeSummaryResponse));
      SummaryUpdateEventLog summaryUpdateEventLog =
          SummaryUpdateEventLog.builder().id("id123").conversationId("123")
              .eventType(Constants.EVENT_TYPE).summary("summary").eventTime(3795253)
              .tenantId(TENANT_ID).environment("dev").businessProcessId("bp123").build();
      when(this.summaryHelper.getSummaryUpdateEventLog(ArgumentMatchers.any(), anyLong(),
          ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any())).thenReturn(summaryUpdateEventLog);
      this.genAISummaryService.createGenAiSummary(CONVERSATION_ID, TENANT_ID,
          Optional.of(EXPERIENCE_ID), true, true, System.currentTimeMillis(), "intent-type");
    } catch (final Exception e) {
      log.error("exception at testCreateGenAISummary: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  public void testFormattingFeatureBranch() throws Exception {
    GenAISummaryCache genAISummaryCache =
        GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
            .isOnDemandSummaryEnabled(true).build();
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_FORMAT_DISABLED, FF_CONTEXT_KIND)).thenReturn(true);

    // other flags disabled
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(false);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(false);
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(false);
    Optional<CreateGenerativeSummaryResponse> aiSummary=
        Optional.of(CreateGenerativeSummaryResponse.newBuilder().setPresentationFormat(PRESENTATION_FORMAT_NUMERIC).addSectionText(
            SectionText.newBuilder().setText("hello").setSection(Section.newBuilder().addEntities(
                SummaryEntity.newBuilder().setEntityType("enity1").build()).build()).build()).build());
    GenAiSummaryDoc genAiSummaryDoc= GenAiSummaryDoc.builder().sections(List.of(
        GenAiSummaryDoc.Section.builder().text("hello").spokenEntities(List.of(
            GenAiSummaryDoc.SpokenEntity.builder().variableId("ksd").build())).build())).build();

    when(grpcClientHelper.getGenAiSummary(anyString(),anyString(),any(),any(),anyString())).thenReturn(aiSummary);
    when(genAiSummaryHelper.formattedSummaryDoc(anyString(),any(),any(),any())).thenReturn(genAiSummaryDoc);
    GenAiSummaryDoc result = genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, genAISummaryCache, "call");
    assertEquals("hello",result.getSections().get(0).getText());
  }

  @Test
  public void testTemplateBasedStreamingBranch() throws Exception {
    GenAISummaryCache genAISummaryCache =
        GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
            .isOnDemandSummaryEnabled(true).build();
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(true);
    summaryDoc = dataHelper.inputSummaryDoc();

    // other flags disabled
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(false);
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(false);

    Optional<Iterator<CreateGenerativeSummaryResponse>> createGenerativeSummaryResponseIterator =
        Optional.of(Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                .addSectionText(SectionText.newBuilder().setText("text")
                    .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build())
            .iterator());

    when(grpcClientHelper.getStreamingSummaryIter(anyString(),anyString(),any(),any(),anyString()))
        .thenReturn(createGenerativeSummaryResponseIterator);

    GenAiSummaryDoc result = genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, genAISummaryCache, "call");
    assertEquals("text section",result.getSections().get(0).getText());
  }

  @Test
  public void testEntityEnhancedBranch() throws Exception {
    GenAISummaryCache genAISummaryCache =
        GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
            .isOnDemandSummaryEnabled(true).build();
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(true);

    // other flags disabled
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(false);
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(false);

    Optional<Iterator<CreateGenerativeSummaryResponse>> createGenerativeSummaryResponseIterator =
        Optional.of(Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                .addSectionText(SectionText.newBuilder().setText("text")
                    .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build())
            .iterator());

    GenAiSummaryDoc genAiSummaryDoc= GenAiSummaryDoc.builder().sections(List.of(
        GenAiSummaryDoc.Section.builder().text("hello").spokenEntities(List.of(
            GenAiSummaryDoc.SpokenEntity.builder().variableId("ksd").build())).build())).build();
    when(genAiSummaryHelper.formattedSummaryDoc(anyString(),any(),any(),any())).thenReturn(genAiSummaryDoc);

    when(grpcClientHelper.getStreamingSummaryIter(anyString(),anyString(),any(),any(),anyString()))
        .thenReturn(createGenerativeSummaryResponseIterator);

    GenAiSummaryDoc result = genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, genAISummaryCache, "call");
    assertEquals("hello", result.getSections().get(0).getText());
  }

  @Test
  public void testGenericStreamingBranch() throws Exception {
    GenAISummaryCache genAISummaryCache =
        GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
            .isOnDemandSummaryEnabled(true).build();
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(true);
    summaryDoc = dataHelper.inputSummaryDoc();

    // other flags disabled
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(false);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(false);

    Optional<Iterator<CreateGenerativeSummaryResponse>> createGenerativeSummaryResponseIterator =
        Optional.of(Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                .addSectionText(SectionText.newBuilder().setText("text")
                    .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build())
            .iterator());

    when(grpcClientHelper.getStreamingSummaryIter(anyString(),anyString(),any(),any(),anyString()))
        .thenReturn(createGenerativeSummaryResponseIterator);

    GenAiSummaryDoc result = genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, genAISummaryCache, "call");
    assertEquals("text section", result.getSections().get(0).getText());
  }

  @Test
  public void testDefaultNonStreamingBranch() throws Exception {
    GenAISummaryCache genAISummaryCache =
        GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
            .isOnDemandSummaryEnabled(true).build();
    // All flags disabled
    when(ldHelper.isFFEnabled(anyString(), eq(FF_CONTEXT_KIND))).thenReturn(false);
    Optional<CreateGenerativeSummaryResponse> aiSummary=
        Optional.of(CreateGenerativeSummaryResponse.newBuilder().setPresentationFormat(PRESENTATION_FORMAT_NUMERIC).addSectionText(
            SectionText.newBuilder().setText("hello").setSection(Section.newBuilder().addEntities(
                SummaryEntity.newBuilder().setEntityType("enity1").build()).build()).build()).build());
    GenAiSummaryDoc genAiSummaryDoc= GenAiSummaryDoc.builder().sections(List.of(
        GenAiSummaryDoc.Section.builder().text("hello").spokenEntities(List.of(
            GenAiSummaryDoc.SpokenEntity.builder().variableId("ksd").build())).build())).build();

    when(grpcClientHelper.getGenAiSummary(anyString(),anyString(),any(),any(),anyString())).thenReturn(aiSummary);
    when(genAiSummaryHelper.formattedSummaryDoc(anyString(),any(),any(),any())).thenReturn(genAiSummaryDoc);

    GenAiSummaryDoc result = genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, genAISummaryCache, "call");
    assertEquals("hello", result.getSections().get(0).getText());
  }

  @Test
  public void testTemplateEntityStreamAllEnabledWithTemplate() throws Exception {
    GenAISummaryCache cache = GenAISummaryCache.builder().build();
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(true);

    summaryDoc = dataHelper.inputSummaryDoc();

    Iterator<CreateGenerativeSummaryResponse> iterator =
        Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                .addSectionText(SectionText.newBuilder().setText("template streaming")).build())
            .iterator();

    when(grpcClientHelper.getStreamingSummaryIter(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(iterator));

    GenAiSummaryDoc result =
        genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, cache, "call");
    assertEquals("text section", result.getSections().get(0).getText());
  }

  @Test
  public void testTemplateEntityStreamAllEnabledWithoutTemplate() throws Exception {
    GenAISummaryCache cache = GenAISummaryCache.builder().build();
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(true);

    summaryDoc = dataHelper.inputSummaryDocWithNullTemplateId();

    GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder()
        .sections(List.of(GenAiSummaryDoc.Section.builder().text("entity enhanced").build()))
        .build();

    when(grpcClientHelper.getGenAiSummary(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(CreateGenerativeSummaryResponse.newBuilder().build()));
    when(genAiSummaryHelper.formattedSummaryDoc(any(), any(), any(), any()))
        .thenReturn(genAiSummaryDoc);

    GenAiSummaryDoc result =
        genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, cache, "call");
    assertEquals("entity enhanced", result.getSections().get(0).getText());
  }

  @Test
  public void testTemplateEntityNoStreamWithTemplate() throws Exception {
    GenAISummaryCache cache = GenAISummaryCache.builder().build();
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(false);
    summaryDoc = dataHelper.inputSummaryDoc();

    Iterator<CreateGenerativeSummaryResponse> iterator =
        Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                .addSectionText(SectionText.newBuilder().setText("template only")).build())
            .iterator();

    when(grpcClientHelper.getStreamingSummaryIter(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(iterator));

    GenAiSummaryDoc result =
        genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, cache, "call");
    assertEquals("text section", result.getSections().get(0).getText());
  }

  @Test
  public void testTemplateEntityNoStreamWithoutTemplate() throws Exception {
    GenAISummaryCache cache = GenAISummaryCache.builder().build();
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(false);
    summaryDoc = dataHelper.inputSummaryDocWithNullTemplateId();

    GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder()
        .sections(List.of(GenAiSummaryDoc.Section.builder().text("entity branch").build()))
        .build();

    when(grpcClientHelper.getGenAiSummary(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(CreateGenerativeSummaryResponse.newBuilder().build()));
    when(genAiSummaryHelper.formattedSummaryDoc(any(), any(), any(), any()))
        .thenReturn(genAiSummaryDoc);

    GenAiSummaryDoc result =
        genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, cache, "call");
    assertEquals("entity branch", result.getSections().get(0).getText());
  }

  @Test
  public void testTemplateNoEntityStreamWithTemplate() throws Exception {
    GenAISummaryCache cache = GenAISummaryCache.builder().build();
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(false);
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(true);
    summaryDoc = dataHelper.inputSummaryDoc();


    Iterator<CreateGenerativeSummaryResponse> iterator =
        Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                .addSectionText(SectionText.newBuilder().setText("template stream")).build())
            .iterator();

    when(grpcClientHelper.getStreamingSummaryIter(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(iterator));

    GenAiSummaryDoc result =
        genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, cache, "call");
    assertEquals("text section", result.getSections().get(0).getText());
  }

  @Test
  public void testTemplateNoEntityStreamWithoutTemplate() throws Exception {
    GenAISummaryCache cache = GenAISummaryCache.builder().build();
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(false);
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(true);
    summaryDoc = dataHelper.inputSummaryDocWithNullTemplateId();

    Iterator<CreateGenerativeSummaryResponse> iterator =
        Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                .addSectionText(SectionText.newBuilder().setText("generic stream")).build())
            .iterator();

    when(grpcClientHelper.getStreamingSummaryIter(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(iterator));

    GenAiSummaryDoc result =
        genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, cache, "call");
    assertEquals("text section", result.getSections().get(0).getText());
  }

  @Test
  public void testTemplateNoEntityNoStreamWithTemplate() throws Exception {
    GenAISummaryCache cache = GenAISummaryCache.builder().build();
    when(ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)).thenReturn(true);
    when(ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND)).thenReturn(false);
    when(ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND)).thenReturn(false);
    summaryDoc = dataHelper.inputSummaryDoc();

    Iterator<CreateGenerativeSummaryResponse> iterator =
        Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                .addSectionText(SectionText.newBuilder().setText("template final")).build())
            .iterator();

    when(grpcClientHelper.getStreamingSummaryIter(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(iterator));

    GenAiSummaryDoc result =
        genAISummaryService.processGenAiSummary("cId", "tId", summaryDoc, 123L, cache, "call");
    assertEquals("text section", result.getSections().get(0).getText());
  }

  @Test
  @Order(13)
  void testCreateGenAiSummary_PositiveFlow() throws Exception {
    String convId="test123";
    String tenantId="test123";
    String templateId="test123";
    int version=1;
    Iterator<CreateGenerativeSummaryResponse> mockStream =
        Mockito.mock(Iterator.class);
    GenAiSummaryDoc expectedSummary =
        GenAiSummaryDoc.builder().build();
    Mockito.when(this.grpcClientHelper.createGenerativeSummaryRequestNg(convId,tenantId,templateId,version)).thenReturn(
        CreateGenerativeSummaryRequest.newBuilder().build()
    );
    Mockito.when(this.grpcClientHelper.getGenAiSummaryStream(any())).thenReturn(mockStream);
    Mockito.when(genAiSummaryHelper.processStreamWithoutUassistConfig(
            mockStream, convId))
        .thenReturn(expectedSummary);
    GenAiSummaryDoc genAiSummary =
        this.genAISummaryService.createGenAiSummary(convId, templateId, tenantId, version);
    Assertions.assertNotNull(genAiSummary);
  }

  @Test
  @Order(13)
  void testCreateGenAiSummary_Error() throws Exception {
    String convId="test123";
    String tenantId="test123";
    String templateId="test123";
    int version=1;
    GenAiSummaryDoc expectedSummary =
        GenAiSummaryDoc.builder().build();
    Mockito.when(this.grpcClientHelper.createGenerativeSummaryRequestNg(convId,tenantId,templateId,version)).thenReturn(
        CreateGenerativeSummaryRequest.newBuilder().build()
    );
    Mockito.when(this.grpcClientHelper.getGenAiSummaryStream(any())).thenThrow(new RuntimeException());

    GenAiSummaryDoc genAiSummary =
        this.genAISummaryService.createGenAiSummary(convId, templateId, tenantId, version);
    Assertions.assertNotNull(genAiSummary);
  }

  @Test
  @Order(24)
  public void testProcessStreamingSummary_RetriesOnceOnUnavailable_ThenSucceeds() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(false).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);

      Optional<Iterator<CreateGenerativeSummaryResponse>> createGenerativeSummaryResponseIterator =
          Optional.of(Collections.singletonList(CreateGenerativeSummaryResponse.newBuilder()
                  .addSectionText(SectionText.newBuilder().setText("text")
                      .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build())
              .iterator());

      when(grpcClientHelper.getStreamingSummaryIter(anyString(), anyString(), any(), any(), any()))
          .thenThrow(new StatusRuntimeException(
              Status.UNAVAILABLE.withDescription("Keepalive failed. The connection is likely gone")))
          .thenReturn(createGenerativeSummaryResponseIterator);

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
      verify(grpcClientHelper, times(2)).getStreamingSummaryIter(anyString(), anyString(), any(),
          any(), any());
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(25)
  public void testProcessStreamingSummary_UnavailableTwice_RetryCappedAtOneAttempt() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(false).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);

      when(grpcClientHelper.getStreamingSummaryIter(anyString(), anyString(), any(), any(), any()))
          .thenThrow(new StatusRuntimeException(
              Status.UNAVAILABLE.withDescription("Keepalive failed. The connection is likely gone")));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      // Outer createGenAiSummary catches the propagated exception and returns the (unpublished)
      // summaryDoc rather than rethrowing - the point of this test is the capped invocation count.
      assertNotNull(result);
      verify(grpcClientHelper, times(2)).getStreamingSummaryIter(anyString(), anyString(), any(),
          any(), any());
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(26)
  public void testProcessStreamingSummary_NonUnavailableStatus_DoesNotRetry() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(false).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDoc();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);

      when(grpcClientHelper.getStreamingSummaryIter(anyString(), anyString(), any(), any(), any()))
          .thenThrow(new StatusRuntimeException(Status.UNKNOWN.withDescription(
              "Unexpected <class 'aiohttp.client_exceptions.ContentTypeError'>: 504")));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
      verify(grpcClientHelper, times(1)).getStreamingSummaryIter(anyString(), anyString(), any(),
          any(), any());
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  private void stubNonStreamingSummaryFeatureFlags() {
    when(ldHelper.isFFEnabled(eq(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY),
        eq(FF_CONTEXT_KIND))).thenReturn(false);
    when(ldHelper.isFFEnabled(eq(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS),
        eq(FF_CONTEXT_KIND))).thenReturn(true);
    when(ldHelper.isFFEnabled(eq(UASSIST_STREAM_SUMMARY_FF), eq(FF_CONTEXT_KIND))).thenReturn(
        false);
  }

  @Test
  @Order(27)
  public void testBuildNonStreamingSummary_RetriesOnceOnUnavailable_ThenSucceeds() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(false).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDocWithNullTemplateId();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      stubNonStreamingSummaryFeatureFlags();
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);

      CreateGenerativeSummaryResponse successResponse =
          CreateGenerativeSummaryResponse.newBuilder().addSectionText(
              SectionText.newBuilder().setText("text")
                  .setSection(Section.newBuilder().setId(Constants.CALL_REASON).build())).build();

      when(grpcClientHelper.getGenAiSummary(anyString(), anyString(), any(), any(), any()))
          .thenThrow(new StatusRuntimeException(
              Status.UNAVAILABLE.withDescription("Keepalive failed. The connection is likely gone")))
          .thenReturn(Optional.of(successResponse));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
      verify(grpcClientHelper, times(2)).getGenAiSummary(anyString(), anyString(), any(), any(),
          any());
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(28)
  public void testBuildNonStreamingSummary_UnavailableTwice_RetryCappedAtOneAttempt() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(false).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDocWithNullTemplateId();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      stubNonStreamingSummaryFeatureFlags();
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);

      when(grpcClientHelper.getGenAiSummary(anyString(), anyString(), any(), any(), any()))
          .thenThrow(new StatusRuntimeException(
              Status.UNAVAILABLE.withDescription("Keepalive failed. The connection is likely gone")));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
      verify(grpcClientHelper, times(2)).getGenAiSummary(anyString(), anyString(), any(), any(),
          any());
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @Test
  @Order(29)
  public void testBuildNonStreamingSummary_NonUnavailableStatus_DoesNotRetry() {
    try {
      GenAISummaryCache genAISummaryCache =
          GenAISummaryCache.builder().formatEnabled(true).acwSummaryEnabled(false)
              .isOnDemandSummaryEnabled(true).shortCallEnabled(false).build();
      when(genAiSummaryCacheHelper.getGenAIConfigCacheByExperience(any())).thenReturn(
          Optional.of(genAISummaryCache));
      summaryDoc = this.dataHelper.inputSummaryDocWithNullTemplateId();
      when(this.summaryHelper.getSummaryDocById(ArgumentMatchers.any())).thenReturn(
          Optional.of(summaryDoc));
      stubNonStreamingSummaryFeatureFlags();
      when(summaryCacheHelper.acquireGenAiSummaryGenerationLock(ArgumentMatchers.any())).thenReturn(
          false);

      when(grpcClientHelper.getGenAiSummary(anyString(), anyString(), any(), any(), any()))
          .thenThrow(new StatusRuntimeException(Status.UNKNOWN.withDescription(
              "Unexpected <class 'aiohttp.client_exceptions.ContentTypeError'>: 504")));

      SummaryDoc result =
          genAISummaryService.createGenAiSummary("conv123", "tenant1", Optional.of("exp123"), true,
              false, System.currentTimeMillis(), null);

      assertNotNull(result);
      verify(grpcClientHelper, times(1)).getGenAiSummary(anyString(), anyString(), any(), any(),
          any());
    } catch (Exception e) {
      fail("Exception occurred: " + e.getMessage());
    }
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
