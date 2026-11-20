package com.uniphore.uassist.summary.util;

import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.summary.v1.GenAiSummary;
import com.uniphore.summary.v1.Section;
import com.uniphore.summary.v1.Summary;
import com.uniphore.uassist.summary.cache.SummaryCacheHelper;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.SummaryUpdateLogDao;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryUpdateEventLog;
import com.uniphore.uassist.summary.kafka.MessagePublisher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.uniphore.featureflag.LaunchDarklyConstants.FF_CONTEXT_KIND;
import static com.uniphore.uassist.summary.common.Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY;

@ExtendWith(MockitoExtension.class)
@Tag(value = "unit")
public class SummaryHelperUnitTest {

  @Mock
  private SummaryCacheHelper summaryCacheHelper;

  @Mock
  private LaunchDarklyHelper launchDarklyHelper;

  @Mock
  private SummaryRuntimeDao summaryRuntimeDao;

  @Mock
  private SummaryUpdateLogDao summaryUpdateLogDao;

  @Mock
  private MessagePublisher messagePublisher;

  @InjectMocks
  private SummaryHelper summaryHelper;

  @Test
  void givenSummaryDocSavedInCacheAndDB_andTemplateBasedGenAISummaryFeatureIsDisabled_andDataPublishedToSummaryUpdateTopic_andSummaryEventLogFFIsDisabled_thenDontSaveSummaryUpdateEventLog() {
    SummaryDoc summaryDoc = SummaryDoc.builder().conversationId("convId").build();
    Summary summaryProto = Summary.newBuilder().build();
    SummaryUpdateEventLog summaryUpdateEventLog =
        SummaryUpdateEventLog.builder().eventType("eventType").build();

    Mockito.doNothing().when(this.summaryCacheHelper).set(summaryDoc);
    Mockito.doNothing().when(this.summaryRuntimeDao).saveSummaryDoc(summaryDoc);
    Mockito.doReturn(false).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    Mockito.doNothing().when(this.messagePublisher)
        .summaryUpdate(summaryProto, summaryUpdateEventLog.getEventType());
    Mockito.when(
            this.launchDarklyHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
                LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(Boolean.FALSE)
        .thenReturn(Boolean.FALSE);

    this.summaryHelper.saveAndPublish(summaryDoc, summaryProto, summaryUpdateEventLog);

    Mockito.verify(this.summaryRuntimeDao, Mockito.never())
        .saveSummaryEventDoc(summaryUpdateEventLog);
  }

  @Test
  void givenSummaryDocSavedInCacheAndDB_andTemplateBasedGenAISummaryFeatureIsDisabled_andDataPublishedToSummaryUpdateTopic_andSummaryEventLogFFIsEnabled_thenSaveSummaryUpdateEventLog() {
    SummaryDoc summaryDoc = SummaryDoc.builder().conversationId("convId").build();
    Summary summaryProto = Summary.newBuilder().build();
    SummaryUpdateEventLog summaryUpdateEventLog =
        SummaryUpdateEventLog.builder().eventType("eventType").build();

    Mockito.doNothing().when(this.summaryCacheHelper).set(summaryDoc);
    Mockito.doNothing().when(this.summaryRuntimeDao).saveSummaryDoc(summaryDoc);
    Mockito.doReturn(false).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    Mockito.doNothing().when(this.messagePublisher)
        .summaryUpdate(summaryProto, summaryUpdateEventLog.getEventType());
    Mockito.when(
        this.launchDarklyHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
            LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(Boolean.TRUE);
    Mockito.doNothing().when(this.summaryRuntimeDao).saveSummaryEventDoc(summaryUpdateEventLog);

    this.summaryHelper.saveAndPublish(summaryDoc, summaryProto, summaryUpdateEventLog);

    Mockito.verify(this.summaryRuntimeDao, Mockito.times(1))
        .saveSummaryEventDoc(summaryUpdateEventLog);
  }

  @Test
  void givenSummaryDocSavedInCacheAndDB_andTemplateBasedGenAISummaryFeatureIsEnabled_andIntentLabelIsEmpty_andDataPublishedToSummaryUpdateTopic_andSummaryEventLogFFIsEnabled_thenSaveSummaryUpdateEventLog() {
    SummaryDoc summaryDoc =
        SummaryDoc.builder().conversationId("convId").build();
    Summary summaryProto = Summary.newBuilder().setGenAiSummary(
        GenAiSummary.newBuilder().addSections(Section.newBuilder().build()).build()).build();
    SummaryUpdateEventLog summaryUpdateEventLog =
        SummaryUpdateEventLog.builder().eventType("eventType").build();

    Mockito.doNothing().when(this.summaryCacheHelper).set(summaryDoc);
    Mockito.doNothing().when(this.summaryRuntimeDao).saveSummaryDoc(summaryDoc);
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    Mockito.doNothing().when(this.messagePublisher)
        .summaryUpdate(summaryProto, summaryUpdateEventLog.getEventType());
    Mockito.when(
        this.launchDarklyHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
            LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(Boolean.TRUE);
    Mockito.doNothing().when(this.summaryRuntimeDao).saveSummaryEventDoc(summaryUpdateEventLog);

    this.summaryHelper.saveAndPublish(summaryDoc, summaryProto, summaryUpdateEventLog);

    Mockito.verify(this.summaryRuntimeDao, Mockito.times(1))
        .saveSummaryEventDoc(summaryUpdateEventLog);
  }

  @Test
  void givenSummaryDocSavedInCacheAndDB_andTemplateBasedGenAISummaryFeatureIsEnabled_andDataPublishedToSummaryUpdateTopic_andSummaryEventLogFFIsEnabled_thenSaveSummaryUpdateEventLog() {
    SummaryDoc summaryDoc =
        SummaryDoc.builder().intentLabel("default").conversationId("convId").build();
    Summary summaryProto = Summary.newBuilder().setGenAiSummary(
        GenAiSummary.newBuilder().addSections(Section.newBuilder().build()).build()).build();
    SummaryUpdateEventLog summaryUpdateEventLog =
        SummaryUpdateEventLog.builder().eventType("eventType").build();

    Mockito.doNothing().when(this.summaryCacheHelper).set(summaryDoc);
    Mockito.doNothing().when(this.summaryRuntimeDao).saveSummaryDoc(summaryDoc);
    Mockito.doReturn(true).when(launchDarklyHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
    Mockito.doNothing().when(this.messagePublisher)
        .summaryUpdate(Mockito.any(Summary.class), Mockito.anyString());
    Mockito.when(
        this.launchDarklyHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
            LaunchDarklyConstants.FF_CONTEXT_KIND)).thenReturn(Boolean.TRUE);
    Mockito.doNothing().when(this.summaryRuntimeDao).saveSummaryEventDoc(summaryUpdateEventLog);

    this.summaryHelper.saveAndPublish(summaryDoc, summaryProto, summaryUpdateEventLog);

    Mockito.verify(this.summaryRuntimeDao, Mockito.times(1))
        .saveSummaryEventDoc(summaryUpdateEventLog);
  }

  @Test
  void givenNullArguments_SaveAndPublishThrowsException() {
    Assertions.assertThrows(Exception.class, () -> {
      summaryHelper.saveAndPublish(null, null, null);
    });
  }

}
