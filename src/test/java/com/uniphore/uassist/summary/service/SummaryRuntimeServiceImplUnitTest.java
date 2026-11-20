package com.uniphore.uassist.summary.service;

import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.conversations.v1.Intent;
import com.uniphore.conversations.v1.IntentType;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.ri.common.validation.OutputProcessor;
import com.uniphore.summary.v1.Summary;
import com.uniphore.uassist.summary.cache.*;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.CustomMetricsPublisher;
import com.uniphore.uassist.summary.common.TemplateConfigurer;
import com.uniphore.uassist.summary.dao.AgentEditsRepository;
import com.uniphore.uassist.summary.dao.AgentStepsRepo;
import com.uniphore.uassist.summary.dao.documents.*;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.execption.ErrorResponse;
import com.uniphore.uassist.summary.model.AgentStepPojo;
import com.uniphore.uassist.summary.model.CacheRefreshBean;
import com.uniphore.uassist.summary.model.SummaryTemplate;
import com.uniphore.uassist.summary.util.SummaryHelper;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.uassist.summary.common.Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(value = MockitoExtension.class)
@Tag(value = "unit")
public class SummaryRuntimeServiceImplUnitTest {

  @Mock
  private LaunchDarklyHelper launchDarklyHelper;

  @Mock
  private SummaryHelper summaryHelper;

  @Mock
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;

  @Mock
  private CacheRefreshHelper cacheRefreshHelper;

  @Mock
  private GenAISummaryConfigCacheHelper genAISummaryConfigCacheHelper;

  @Mock
  private CustomMetricsPublisher customMetricsPublisher;

  @Mock
  private AgentEditsRepository agentEditsRepository;

  @Mock
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  @Mock
  private AgentStepsCacheHelper agentStepsCacheHelper;

  @Mock
  private AgentStepsRepo agentStepsRepo;

  @Mock
  private GenAISummaryService genAISummaryService;

  @InjectMocks
  private SummaryRuntimeServiceImpl summaryRuntimeServiceImpl;

  public static final String UASSIST_IGNORE_EMPTY_INTENT_FF = "uassist-ignore-empty-intent-ff";

  // Test for method handleConversationStartedEvent starts here
  @Test
  void givenMultiSegmentSupportIsEnabled_andSummaryDocIsNotEmpty_thenReturn()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setConversationId("conversationId")
            .setBusinessProcessId("businessProcessId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.launchDarklyHelper.isFFEnabled(MULTI_SEGMENT_SUPPORT, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId()))
        .thenReturn(SummaryDoc.builder().build());

    Method method =
        SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleConversationStartedEvent",
            ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);

    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);
    Mockito.verify(this.headerTemplateCacheHelper, Mockito.never())
        .getHeaderTemplate(request.getBusinessProcessId());
  }

  @Test
  void givenMultiSegmentSupportIsEnabled_andSummaryDocThrowsException_andTemplateBasedSummaryIsDisabled_thenSaveAndPublishSuccessfully()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.launchDarklyHelper.isFFEnabled(MULTI_SEGMENT_SUPPORT, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(Constants.UASSIST_STATE_V2_UPDATE_FF,
            FF_CONTEXT_KIND))
        .thenReturn(Boolean.FALSE);
    Mockito.doThrow(new CustomException(ErrorCodes.CONVERSATION_NOT_FOUND, HttpStatus.BAD_REQUEST))
        .when(this.summaryHelper).getSummaryDoc(request.getConversationId());
    Mockito.when(this.headerTemplateCacheHelper.getHeaderTemplate(request.getBusinessProcessId()))
        .thenReturn(null);
    Mockito.when(this.cacheRefreshHelper.refreshHeaderTemplate(Mockito.any(CacheRefreshBean.class)))
        .thenReturn(StringUtils.EMPTY);
    Mockito.when(this.summaryHelper.getSummaryUpdateEventLog(Mockito.anyString(), Mockito.anyLong(),
            Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(SummaryUpdateEventLog.builder().build());
    Mockito.doReturn(false).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    ;
    Mockito.doNothing().when(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
    Mockito.doNothing().when(this.customMetricsPublisher)
        .updateAndLatencyMap(Mockito.anyString(), Mockito.anyLong(), Mockito.anyMap());

    Method method =
        SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleConversationStartedEvent",
            ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
    Mockito.verify(this.customMetricsPublisher)
        .updateAndLatencyMap(Mockito.anyString(), Mockito.anyLong(), Mockito.anyMap());
  }

  @Test
  void givenMultiSegmentSupportIsEnabled_andSummaryDocIsEmpty_andTemplateBasedSummaryIsDisabled_thenSaveAndPublishSuccessfully()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.launchDarklyHelper.isFFEnabled(MULTI_SEGMENT_SUPPORT, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(Constants.UASSIST_STATE_V2_UPDATE_FF,
            FF_CONTEXT_KIND))
        .thenReturn(Boolean.FALSE);
    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(null);
    Mockito.when(this.headerTemplateCacheHelper.getHeaderTemplate(request.getBusinessProcessId()))
        .thenReturn(null);
    Mockito.when(this.cacheRefreshHelper.refreshHeaderTemplate(Mockito.any(CacheRefreshBean.class)))
        .thenReturn(StringUtils.EMPTY);
    Mockito.when(this.summaryHelper.getSummaryUpdateEventLog(Mockito.anyString(), Mockito.anyLong(),
            Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(SummaryUpdateEventLog.builder().build());
    Mockito.doReturn(false).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    ;
    Mockito.doNothing().when(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
    Mockito.doNothing().when(this.customMetricsPublisher)
        .updateAndLatencyMap(Mockito.anyString(), Mockito.anyLong(), Mockito.anyMap());

    Method method =
        SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleConversationStartedEvent",
            ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
    Mockito.verify(this.customMetricsPublisher)
        .updateAndLatencyMap(Mockito.anyString(), Mockito.anyLong(), Mockito.anyMap());
  }

  @Test
  void givenMultiSegmentSupportIsEnabled_andSummaryDocIsEmpty_andTemplateBasedSummaryIsEnabled_andGenAiSummaryCacheIsEmpty_thenSaveAndPublishSuccessfully()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.launchDarklyHelper.isFFEnabled(MULTI_SEGMENT_SUPPORT, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(Constants.UASSIST_STATE_V2_UPDATE_FF,
            FF_CONTEXT_KIND))
        .thenReturn(Boolean.FALSE);
    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(null);
    Mockito.when(this.headerTemplateCacheHelper.getHeaderTemplate(request.getBusinessProcessId()))
        .thenReturn(null);
    Mockito.when(this.cacheRefreshHelper.refreshHeaderTemplate(Mockito.any(CacheRefreshBean.class)))
        .thenReturn(StringUtils.EMPTY);
    Mockito.when(this.summaryHelper.getSummaryUpdateEventLog(Mockito.anyString(), Mockito.anyLong(),
            Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(SummaryUpdateEventLog.builder().build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    ;
    Mockito.when(
            this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(Mockito.anyString()))
        .thenReturn(Optional.empty());
    Mockito.doNothing().when(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));

    Method method =
        SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleConversationStartedEvent",
            ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
    Mockito.verify(this.customMetricsPublisher)
        .updateAndLatencyMap(Mockito.anyString(), Mockito.anyLong(), Mockito.anyMap());
  }

  @Test
  void givenTemplateBasedSummaryIsEnabled_andGenAiSummaryCacheIsNotEmpty_andIntentTemplateMappingsIsEmpty_thenSaveAndPublishSuccessfully()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.launchDarklyHelper.isFFEnabled(MULTI_SEGMENT_SUPPORT, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(Constants.UASSIST_STATE_V2_UPDATE_FF,
            FF_CONTEXT_KIND))
        .thenReturn(Boolean.FALSE);
    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(null);
    Mockito.when(this.headerTemplateCacheHelper.getHeaderTemplate(request.getBusinessProcessId()))
        .thenReturn(null);
    Mockito.when(this.cacheRefreshHelper.refreshHeaderTemplate(Mockito.any(CacheRefreshBean.class)))
        .thenReturn(StringUtils.EMPTY);
    Mockito.when(this.summaryHelper.getSummaryUpdateEventLog(Mockito.anyString(), Mockito.anyLong(),
            Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(SummaryUpdateEventLog.builder().build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    ;
    Mockito.when(
            this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(Mockito.anyString()))
        .thenReturn(Optional.of(GenAISummaryCache.builder().build()));
    Mockito.doNothing().when(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));

    Method method =
        SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleConversationStartedEvent",
            ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
    Mockito.verify(this.customMetricsPublisher)
        .updateAndLatencyMap(Mockito.anyString(), Mockito.anyLong(), Mockito.anyMap());
  }

  @Test
  void givenTemplateBasedSummaryIsEnabled_andGenAiSummaryCacheIsNotEmpty_andIntentTemplateMappingsIsNotEmpty_thenSaveAndPublishSuccessfully()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.launchDarklyHelper.isFFEnabled(MULTI_SEGMENT_SUPPORT, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(Constants.UASSIST_STATE_V2_UPDATE_FF,
            FF_CONTEXT_KIND))
        .thenReturn(Boolean.FALSE);
    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(null);
    Mockito.when(this.headerTemplateCacheHelper.getHeaderTemplate(request.getBusinessProcessId()))
        .thenReturn(null);
    Mockito.when(this.cacheRefreshHelper.refreshHeaderTemplate(Mockito.any(CacheRefreshBean.class)))
        .thenReturn(StringUtils.EMPTY);
    Mockito.when(this.summaryHelper.getSummaryUpdateEventLog(Mockito.anyString(), Mockito.anyLong(),
            Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(SummaryUpdateEventLog.builder().build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    ;
    Mockito.when(
            this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(Mockito.anyString()))
        .thenReturn(Optional.of(GenAISummaryCache.builder().callTypeTemplateMappings(
            Collections.singletonList(
                GenAISummaryCache.CallTypeTemplateMapping.builder().intentLabel("default")
                    .templateId("templateId").version(1).build())).build()));
    Mockito.doNothing().when(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));

    Method method =
        SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleConversationStartedEvent",
            ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
    Mockito.verify(this.customMetricsPublisher)
        .updateAndLatencyMap(Mockito.anyString(), Mockito.anyLong(), Mockito.anyMap());
  }

  @Test
  void whenHandleConversationStartedEvent_invokedWithNullArguments_thenThrowsException()
      throws NoSuchMethodException {
    long eventStartTime = System.currentTimeMillis();

    Method method =
        SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleConversationStartedEvent",
            ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    Assertions.assertThrows(Exception.class, () -> {
      method.invoke(summaryRuntimeServiceImpl, null, eventStartTime);
    });
  }
  // Test for method handleConversationStartedEvent ends here

  // Test for method handleIntentsAddedEvent starts here
  @Test
  void givenSummaryDocIsEmpty_andTemplateBasedGenAISummaryDisabled_andFlowSelectedIntentsEnabled_andIntentSummaryDocsIsEmpty_andAgentStepsIsEmpty_thenJustLog()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").setIntentsAdded(
                ConversationEventEnvelope.IntentsAdded.newBuilder().addIntents(
                    Intent.newBuilder().setIntentType(IntentType.INTENT_TYPE_CALL_REASON)
                        .setDetectedBy("agent").build()).build()).build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId()))
        .thenReturn(SummaryDoc.builder().build());
    Mockito.doReturn(false).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    ;
    Mockito.when(this.launchDarklyHelper.isFFEnabled(ENABLE_DUPLICATE_INTENTS, FF_CONTEXT_KIND))
        .thenReturn(Boolean.FALSE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(UASSIST_ENABLE_FLOW_SELECTED_INTENTS_FF,
        FF_CONTEXT_KIND)).thenReturn(Boolean.TRUE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.launchDarklyHelper, Mockito.never())
        .isFFEnabled(UASSIST_IGNORE_EMPTY_INTENT_FF, FF_CONTEXT_KIND);
  }

  @Test
  void givenSummaryDocNotEmpty_andTemplateBasedGenAISummaryDisabled_andDuplicateIntentsDisabled_andIntentSummaryDocsIsEmpty_andAgentStepsIsEmpty_thenJustLog()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").setIntentsAdded(
                ConversationEventEnvelope.IntentsAdded.newBuilder().addIntents(
                    Intent.newBuilder().setLabel("intentLabel")
                        .setIntentType(IntentType.INTENT_TYPE_CALL_REASON).setDetectedBy("agent")
                        .build()).build()).build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().intentSummaries(
                Map.of("intentLabel", IntentSummaryDoc.builder().intentId("intentLabel").build()))
            .build());
    Mockito.doReturn(false).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    ;
    Mockito.when(this.launchDarklyHelper.isFFEnabled(ENABLE_DUPLICATE_INTENTS, FF_CONTEXT_KIND))
        .thenReturn(Boolean.FALSE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(UASSIST_ENABLE_FLOW_SELECTED_INTENTS_FF,
        FF_CONTEXT_KIND)).thenReturn(Boolean.FALSE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.launchDarklyHelper, Mockito.never())
        .isFFEnabled(UASSIST_IGNORE_EMPTY_INTENT_FF, FF_CONTEXT_KIND);
  }

  @Test
  void givenSummaryDocNotEmpty_andTemplateBasedGenAISummaryDisabled_andDuplicateIntentsDisabled_andIntentSummaryDocsNotEmpty_andAgentStepsIsEmpty_thenSaveAndPublish()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").setIntentsAdded(
                ConversationEventEnvelope.IntentsAdded.newBuilder().addIntents(
                    Intent.newBuilder().setLabel("intentLabel")
                        .setIntentType(IntentType.INTENT_TYPE_CALL_REASON).setDetectedBy(Constants.USER)
                        .build()).build()).build();
    long eventStartTime = System.currentTimeMillis();
    Map<String, IntentSummaryDoc> intentSummaries = new HashMap<>();
    intentSummaries.put("intentLabel", IntentSummaryDoc.builder().intentId("intentLabel").build());
    SummaryDoc summaryDoc =
        SummaryDoc.builder().conversationId("convId").tenantId("tenantId").experienceId("expId")
            .environment("dev").participants(Collections.singletonList(
                ParticipantDoc.builder().participantId("participantId").ctiAgentId("ctiAgentId")
                    .type("type").build())).intentSummaries(intentSummaries).entities(Map.of("slot1",
                SummaryDoc.Slot.builder().id("id").isEdited(Boolean.TRUE).instanceId("instanceId")
                    .build())).variables(Map.of("slot1",
                SummaryDoc.Slot.builder().id("id").isEdited(Boolean.TRUE).instanceId("instanceId")
                    .build())).genAiSummary(GenAiSummaryDoc.builder().sections(
                    Collections.singletonList(GenAiSummaryDoc.Section.builder().id("id").text("text")
                        .spokenEntities(Collections.singletonList(
                            GenAiSummaryDoc.SpokenEntity.builder().variableId("variableId").value("value")
                                .label("label").isEdited(Boolean.TRUE).variableInstanceId("varInstanceId")
                                .isLabelUnmentioned(Boolean.TRUE).placement("placement").build())).build()))
                .build()).build();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId()))
        .thenReturn(summaryDoc);
    Mockito.doReturn(false).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    ;
    Mockito.when(this.launchDarklyHelper.isFFEnabled(ENABLE_DUPLICATE_INTENTS, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(UASSIST_ENABLE_FLOW_SELECTED_INTENTS_FF,
        FF_CONTEXT_KIND)).thenReturn(Boolean.FALSE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(
            this.launchDarklyHelper.isFFEnabled(UASSIST_IGNORE_EMPTY_INTENT_FF, FF_CONTEXT_KIND))
        .thenReturn(Boolean.FALSE);
    Mockito.doNothing().when(this.intentTemplateCacheHelper)
        .pushIntentToIntentBoundaryStack(Mockito.anyString(), Mockito.anyString());
    Mockito.when(this.summaryHelper.getIntentTemplate(Mockito.anyString(), Mockito.anyString(),
        Mockito.anyString(), Mockito.anyString(), Mockito.any(SummaryDoc.class),
        Mockito.anyBoolean())).thenReturn("");
    Mockito.when(this.agentEditsRepository.save(Mockito.any(AgentEditsDoc.class)))
        .thenReturn(AgentEditsDoc.builder().build());
    Mockito.when(this.summaryHelper.getSummaryUpdateEventLog(Mockito.anyString(), Mockito.anyLong(),
            Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(SummaryUpdateEventLog.builder().id("id").build());
    Mockito.doNothing().when(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
    Mockito.doNothing().when(this.customMetricsPublisher)
        .updateAndLatencyMap(Mockito.anyString(), Mockito.anyLong(), Mockito.anyMap());

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper, Mockito.times(1))
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
  }

  @Test
  void givenSummaryDocNotEmpty_andTemplateBasedGenAISummaryDisabled_andDuplicateIntentsDisabled_andIntentSummaryDocsIsEmpty_andAgentStepsNotEmpty_thenSaveAndPublish()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").setIntentsAdded(
                ConversationEventEnvelope.IntentsAdded.newBuilder().addIntents(
                    Intent.newBuilder().setLabel("intentLabel")
                        .setIntentType(IntentType.INTENT_TYPE_AGENT_STEPS).setDetectedBy("agent")
                        .build()).build()).build();
    long eventStartTime = System.currentTimeMillis();
    Map<String, IntentSummaryDoc> intentSummaries = new HashMap<>();
    intentSummaries.put("intentLabel", IntentSummaryDoc.builder().intentId("intentLabel").build());
    SummaryDoc summaryDoc =
        SummaryDoc.builder().conversationId("convId").tenantId("tenantId").experienceId("expId")
            .environment("dev").participants(Collections.singletonList(
                ParticipantDoc.builder().participantId("participantId").ctiAgentId("ctiAgentId")
                    .type("type").build())).intentSummaries(intentSummaries).entities(Map.of("slot1",
                SummaryDoc.Slot.builder().id("id").isEdited(Boolean.TRUE).instanceId("instanceId")
                    .build())).variables(Map.of("slot1",
                SummaryDoc.Slot.builder().id("id").isEdited(Boolean.TRUE).instanceId("instanceId")
                    .build())).genAiSummary(GenAiSummaryDoc.builder().sections(
                    Collections.singletonList(GenAiSummaryDoc.Section.builder().id("id").text("text")
                        .spokenEntities(Collections.singletonList(
                            GenAiSummaryDoc.SpokenEntity.builder().variableId("variableId").value("value")
                                .label("label").isEdited(Boolean.TRUE).variableInstanceId("varInstanceId")
                                .isLabelUnmentioned(Boolean.TRUE).placement("placement").build())).build()))
                .build()).build();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId()))
        .thenReturn(summaryDoc);
    Mockito.doReturn(false).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    ;
    Mockito.when(this.launchDarklyHelper.isFFEnabled(ENABLE_DUPLICATE_INTENTS, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(UASSIST_ENABLE_FLOW_SELECTED_INTENTS_FF,
        FF_CONTEXT_KIND)).thenReturn(Boolean.TRUE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(SYSTEM_TEMPLATE_SUMMARY_FLAG, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(this.launchDarklyHelper.isFFEnabled(ENABLE_AGENT_STEPS, FF_CONTEXT_KIND))
        .thenReturn(Boolean.TRUE);
    Mockito.when(this.agentStepsCacheHelper.getNextIndex(Mockito.anyString())).thenReturn(1);
    Mockito.when(this.agentStepsCacheHelper.add(Mockito.anyString(), Mockito.anyList())).thenReturn(
        Collections.singletonList(AgentStepPojo.builder().id("id").text("text").index(1).build()));
    Mockito.when(this.agentStepsRepo.save(Mockito.any(AgentStepsDoc.class)))
        .thenReturn(AgentStepsDoc.builder().build());
    Mockito.when(this.summaryHelper.getSummaryUpdateEventLog(Mockito.anyString(), Mockito.anyLong(),
            Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(SummaryUpdateEventLog.builder().id("id").build());
    Mockito.doNothing().when(this.summaryHelper)
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
    Mockito.doNothing().when(this.customMetricsPublisher)
        .updateAndLatencyMap(Mockito.anyString(), Mockito.anyLong(), Mockito.anyMap());

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper, Mockito.times(1))
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
  }

  @Test
  void givenSummaryDocNotEmpty_andTemplateBasedGenAISummaryEnabled_andGenAISummaryGenerated_thenReturn()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().genAiGeneratedSummary(GenAiSummaryDoc.builder().build()).build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    ;

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper, Mockito.never())
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
  }

  @Test
  void givenSummaryDocNotEmpty_andTemplateBasedGenAISummaryEnabled_andGenAISummaryIsEmpty_andTemplateAssignedByIsEmpty_thenDoNothing()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId()))
        .thenReturn(SummaryDoc.builder().build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper, Mockito.never())
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
  }

  @Test
  void givenSummaryDocNotEmpty_andTemplateBasedGenAISummaryEnabled_andGenAISummaryIsEmpty_andTemplateAssignedByAgentSelection_thenReturn()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().templateAssignedBy(TemplateConfigurer.AGENT_SELECTION).build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper, Mockito.never())
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
  }

  @Test
  void givenSummaryDocNotEmpty_andTemplateBasedGenAISummaryEnabled_andGenAISummaryIsEmpty_andTemplateAssignedByTemplateMappingAPI_thenReturn()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().templateAssignedBy(TemplateConfigurer.TEMPLATE_MAPPING_API).build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper, Mockito.never())
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
  }

  @Test
  void givenTemplateBasedGenAISummaryEnabled_andGenAISummaryIsEmpty_andTemplateAssignedByIsConvStartedEvent_andGenAIConfigIsEmpty_thenDoNothing()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().templateAssignedBy(TemplateConfigurer.CONVERSATION_START_EVENT)
            .build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    Mockito.when(
            this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(Mockito.anyString()))
        .thenReturn(Optional.empty());

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper, Mockito.never())
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
  }

  @Test
  void givenTemplateBasedGenAISummaryEnabled_andGenAISummaryIsEmpty_andTemplateAssignedByIsConvStartedEvent_andGenAIConfigNotEmpty_andTemplateMappingsIsEmpty_thenDoNothing()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().templateAssignedBy(TemplateConfigurer.CONVERSATION_START_EVENT)
            .build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    Mockito.when(
            this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(Mockito.anyString()))
        .thenReturn(Optional.of(GenAISummaryCache.builder().build()));

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper, Mockito.never())
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
  }

  @Test
  void givenTemplateBasedGenAISummaryEnabled_andGenAISummaryIsEmpty_andTemplateAssignedByIsConvStartedEvent_andGenAIConfigNotEmpty_andTemplateMappingsNotEmpty_andIntentNotPartOfConfig_thenDoNothing()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").setIntentsAdded(
                ConversationEventEnvelope.IntentsAdded.newBuilder()
                    .addIntents(Intent.newBuilder().setLabel("intentLabel").build()).build()).build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().templateAssignedBy(TemplateConfigurer.CONVERSATION_START_EVENT)
            .build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    Mockito.when(
            this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(Mockito.anyString()))
        .thenReturn(Optional.of(GenAISummaryCache.builder().callTypeTemplateMappings(
            Collections.singletonList(
                GenAISummaryCache.CallTypeTemplateMapping.builder().intentLabel("default")
                    .templateId("templateId").version(1).build())).build()));

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper, Mockito.never())
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
  }

  @Test
  void givenTemplateBasedGenAISummaryEnabled_andGenAISummaryIsEmpty_andTemplateAssignedByIsConvStartedEvent_andGenAIConfigNotEmpty_andTemplateMappingsNotEmpty_andIntentPartOfConfig_thenDoNothing()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").setIntentsAdded(
                ConversationEventEnvelope.IntentsAdded.newBuilder()
                    .addIntents(Intent.newBuilder().setLabel("default").build()).build()).build();
    long eventStartTime = System.currentTimeMillis();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().templateAssignedBy(TemplateConfigurer.CONVERSATION_START_EVENT)
            .build());
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    Mockito.when(
            this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(Mockito.anyString()))
        .thenReturn(Optional.of(GenAISummaryCache.builder().callTypeTemplateMappings(
            Collections.singletonList(
                GenAISummaryCache.CallTypeTemplateMapping.builder().intentLabel("default")
                    .templateId("templateId").version(1).build())).build()));

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);

    Mockito.verify(this.summaryHelper, Mockito.never())
        .saveAndPublish(Mockito.any(SummaryDoc.class), Mockito.any(Summary.class),
            Mockito.any(SummaryUpdateEventLog.class));
  }

  @Test
  void whenHandleIntentsAddedEvent_invokedWithIncompleteArguments_thenThrowsException()
      throws NoSuchMethodException {
    ConversationEventEnvelope request =
        ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
            .setConversationId("conversationId").build();
    long eventStartTime = System.currentTimeMillis();

    Method method = SummaryRuntimeServiceImpl.class.getDeclaredMethod("handleIntentsAddedEvent",
        ConversationEventEnvelope.class, long.class);
    method.setAccessible(Boolean.TRUE);
    Assertions.assertThrows(Exception.class, () -> {
      method.invoke(summaryRuntimeServiceImpl, request, eventStartTime);
    });
  }
  // Test for method handleIntentsAddedEvent ends here

  // Test for method updateSummaryTemplateRequest starts here
  static Stream<Arguments> updateSummaryTemplateRequest() {
    return Stream.of(Arguments.of(SummaryTemplate.builder().build(), Commons.getErrorResponse(
                List.of(ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                        .message("Conversation ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                        .message("Summary Template ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is empty, Summary Template ID is empty and Version is zero"),
        Arguments.of(SummaryTemplate.builder().conversationId(null).build(),
            Commons.getErrorResponse(List.of(
                    ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                        .message("Conversation ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                        .message("Summary Template ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is null, Summary Template ID is empty and Version is zero"),
        Arguments.of(SummaryTemplate.builder().summaryTemplateId(null).build(),
            Commons.getErrorResponse(List.of(
                    ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                        .message("Conversation ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                        .message("Summary Template ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is empty, Summary Template ID is null and Version is zero"),
        Arguments.of(SummaryTemplate.builder().conversationId(null).summaryTemplateId(null).build(),
            Commons.getErrorResponse(List.of(
                    ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                        .message("Conversation ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                        .message("Summary Template ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is null, Summary Template ID is null and Version is zero"),
        Arguments.of(SummaryTemplate.builder().conversationId("null").build(),
            Commons.getErrorResponse(List.of(
                    ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                        .message("Conversation ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                        .message("Summary Template ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is null string, Summary Template ID is empty and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("null").summaryTemplateId("null").build(),
            Commons.getErrorResponse(List.of(
                    ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                        .message("Conversation ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                        .message("Summary Template ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is null string, Summary Template ID is null string and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("undefined").summaryTemplateId("null").build(),
            Commons.getErrorResponse(List.of(
                    ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                        .message("Conversation ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                        .message("Summary Template ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is undefined string, Summary Template ID is null string and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("undefined").summaryTemplateId("undefined")
                .build(), Commons.getErrorResponse(List.of(
                    ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                        .message("Conversation ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                        .message("Summary Template ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is undefined string, Summary Template ID is undefined string and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
                .build(), Commons.getErrorResponse(List.of(
                    ErrorResponse.builder().code(ErrorCodes.SUMMARY_TEMPLATE_ID_EMPTY)
                        .message("Summary Template ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is valid, Summary Template ID is empty and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
                .build(), Commons.getErrorResponse(List.of(
                    ErrorResponse.builder().code(ErrorCodes.CONVERSATION_ID_EMPTY)
                        .message("Conversation ID is empty").build(),
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is empty, Summary Template ID is valid and Version is zero"),
        Arguments.of(
            SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
                .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(-1).build(),
            Commons.getErrorResponse(List.of(
                    ErrorResponse.builder().code(ErrorCodes.INCORRECT_VERSION)
                        .message("Version should be a whole number and greater than zero").build()),
                HttpStatus.BAD_REQUEST),
            "Conversation ID is valid, Summary Template ID is valid and Version is -1"));
  }

  @ParameterizedTest(name = "{index} => {2}")
  @MethodSource("updateSummaryTemplateRequest")
  void test_UpdateSummaryTemplate_IncorrectRequest(SummaryTemplate request,
      ResponseEntity<Map<String, Object>> expectedResponse, String testCaseName) {
    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeServiceImpl.updateSummaryTemplate(request);

    assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());

    Map<String, Object> actualResponseBody = (Map<String, Object>) actualResponse.getBody();
    List<ErrorResponse> actualErrorResponses =
        (List<ErrorResponse>) actualResponseBody.get(Constants.ERRORS);

    Map<String, Object> expectedResponseBody = (Map<String, Object>) expectedResponse.getBody();
    List<ErrorResponse> expectedErrorResponses =
        (List<ErrorResponse>) expectedResponseBody.get(Constants.ERRORS);

    for (int i = 0; i < Objects.requireNonNull(actualErrorResponses).size(); i++) {
      assertEquals(expectedErrorResponses.get(i).getCode(), actualErrorResponses.get(i).getCode());
      assertEquals(expectedErrorResponses.get(i).getMessage(),
          actualErrorResponses.get(i).getMessage());
    }
  }

  @Test
  void givenSummaryDocIsEmptyInCache_andIsEmptyInDB_thenThrowsCustomException() {
    SummaryTemplate request =
        SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
            .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(1).build();
    Mockito.doThrow(new CustomException(ErrorCodes.CONVERSATION_NOT_FOUND, HttpStatus.BAD_REQUEST))
        .when(this.summaryHelper).getSummaryDoc(request.getConversationId());

    ResponseEntity<Map<String, Object>> expectedResponse =
        OutputProcessor.failureUIResponse(HttpStatus.BAD_REQUEST,
            String.format("Summary is unavailable for conversationId: %s",
                request.getConversationId()));

    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeServiceImpl.updateSummaryTemplate(request);

    assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
    assertEquals(expectedResponse.getBody(), actualResponse.getBody());
  }

  @Test
  void givenSummaryIsEmptyInCache_andIsEmptyInDB_thenReturnBadRequest() {
    SummaryTemplate request =
        SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
            .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(1).build();
    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(null);

    ResponseEntity<Map<String, Object>> expectedResponse =
        OutputProcessor.failureUIResponse(HttpStatus.BAD_REQUEST,
            String.format("Summary is unavailable for conversationId: %s",
                request.getConversationId()));

    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeServiceImpl.updateSummaryTemplate(request);

    assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
    assertEquals(expectedResponse.getBody(), actualResponse.getBody());
  }

  @Test
  void givenGenAISummaryIsAlreadyGenerated_andSectionsNotEmpty_thenReturnPreConditionFailed() {
    SummaryTemplate request =
        SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
            .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(1).build();
    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().genAiSummary(GenAiSummaryDoc.builder().sections(
                Collections.singletonList(GenAiSummaryDoc.Section.builder().text("text").build()))
            .build()).build());

    ResponseEntity<Map<String, Object>> expectedResponse =
        OutputProcessor.failureUIResponse(HttpStatus.PRECONDITION_FAILED, String.format(
            "Updating Summary template ID and Version is not allowed at the moment as Gen-AI summary"
                + " has already been generated for the current conversationId: %s",
            request.getConversationId()));

    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeServiceImpl.updateSummaryTemplate(request);

    assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
    assertEquals(expectedResponse.getBody(), actualResponse.getBody());
  }

  @Test
  void givenGenAISummaryIsAlreadyGenerated_andUnmappedVariableInGenAiSummariesNotEmpty_thenReturnPreConditionFailed() {
    SummaryTemplate request =
        SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
            .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(1).build();
    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().genAiSummary(GenAiSummaryDoc.builder()
            .unmappedVariableInGenAiSummaries(Map.of("key", Set.of("value"))).build()).build());

    ResponseEntity<Map<String, Object>> expectedResponse =
        OutputProcessor.failureUIResponse(HttpStatus.PRECONDITION_FAILED, String.format(
            "Updating Summary template ID and Version is not allowed at the moment as Gen-AI summary"
                + " has already been generated for the current conversationId: %s",
            request.getConversationId()));

    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeServiceImpl.updateSummaryTemplate(request);

    assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
    assertEquals(expectedResponse.getBody(), actualResponse.getBody());
  }

  @Test
  void givenGenAISummaryIsNotGenerated_andAgentManuallySelectedIntent_thenReturnOk() {
    SummaryTemplate request =
        SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
            .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(1)
            .isAgentSelected(Boolean.TRUE).build();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().templateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").templateVersion(1)
            .templateAssignedBy(TemplateConfigurer.AGENT_SELECTION).build());
    Mockito.doNothing().when(this.summaryHelper).saveSummaryDoc(Mockito.any(SummaryDoc.class));

    ResponseEntity<Map<String, Object>> expectedResponse =
        OutputProcessor.successUIResponse(HttpStatus.OK, null);

    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeServiceImpl.updateSummaryTemplate(request);

    assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
    assertEquals(expectedResponse.getBody(), actualResponse.getBody());
  }

  @Test
  void givenGenAISummaryIsNotGenerated_andNonAgentTryingToSetTemplateIdVersion_butTemplateIdVersionIsFinalizedByAgent_thenReturnPreConditionFailed() {
    SummaryTemplate request =
        SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
            .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(1)
            .isAgentSelected(Boolean.FALSE).build();
    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId())).thenReturn(
        SummaryDoc.builder().templateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").templateVersion(1)
            .templateAssignedBy(TemplateConfigurer.AGENT_SELECTION).build());

    ResponseEntity<Map<String, Object>> expectedResponse =
        OutputProcessor.failureUIResponse(HttpStatus.PRECONDITION_FAILED, String.format(
            "Updating Summary template ID and Version is not allowed at the moment as it has been finalized"
                + " by agent for the current conversationId: %s", request.getConversationId()));

    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeServiceImpl.updateSummaryTemplate(request);

    assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
    assertEquals(expectedResponse.getBody(), actualResponse.getBody());
  }

  @Test
  void givenGenAISummaryIsNotGenerated_andTemplateIdAndVersionIsPassed_thenReturnOk() {
    SummaryTemplate request =
        SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
            .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(1)
            .isAgentSelected(Boolean.FALSE).build();
    SummaryDoc summaryDoc =
        SummaryDoc.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763").build();
    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId()))
        .thenReturn(summaryDoc);
    Mockito.doNothing().when(this.summaryHelper).saveSummaryDoc(summaryDoc);

    ResponseEntity<Map<String, Object>> expectedResponse =
        OutputProcessor.successUIResponse(HttpStatus.OK, null);

    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeServiceImpl.updateSummaryTemplate(request);

    assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
  }
  // Test for method updateSummaryTemplateRequest ends here

  @Test
  void createGenerateSummaryIndependentTest(){
    String convId="test123";
    String tenantId="test123";
    String templateId="test123";
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, tenantId);
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
    int versaion=1;
    Mockito.when(this.genAISummaryService.createGenAiSummary(convId,templateId,tenantId,versaion)).thenReturn(GenAiSummaryDoc.builder()
        .build());
    this.summaryRuntimeServiceImpl.createGenAiSummaryIndependent(convId,templateId,versaion);
  }

  @Test
  void givenValidIntentLabel_whenUpdateSummaryTemplate_thenIntentLabelIsSet() {
    SummaryTemplate request =
        SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
            .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(1)
            .intentLabel("testIntentLabel").isAgentSelected(Boolean.FALSE).build();
    SummaryDoc summaryDoc =
        SummaryDoc.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763").build();

    Mockito.when(this.summaryHelper.getSummaryDoc(request.getConversationId()))
        .thenReturn(summaryDoc);
    Mockito.doNothing().when(this.summaryHelper).saveSummaryDoc(Mockito.any(SummaryDoc.class));

    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeServiceImpl.updateSummaryTemplate(request);

    assertEquals(HttpStatus.OK, actualResponse.getStatusCode());
    assertEquals("testIntentLabel", summaryDoc.getIntentLabel());
    Mockito.verify(this.summaryHelper, Mockito.times(1)).saveSummaryDoc(summaryDoc);
  }

}
