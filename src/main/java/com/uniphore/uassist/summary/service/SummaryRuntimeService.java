package com.uniphore.uassist.summary.service;

import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryUpdateEventLog;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.model.*;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SummaryRuntimeService {
  public MessageResponse saveAgentNotes(AgentRequestPayload agentNotes);

  public MessageResponse saveAgentEdits(AgentRequestPayload agentRequestPayload);

  void processAgentUpdates(final AgentUpdateRequest agentUpdateRequest,
      List<SummaryUpdateEventLog.EventData> eventData);

  public MessageResponse addIntent(AgentRequestPayload agentRequestPayload);

  public MessageResponse deleteIntent(AgentRequestPayload agentRequestPayload);

  public MessageResponse summaryReviewed(String conversationId, Optional<Boolean> autosubmit,Optional<Boolean> forcedsubmit,Optional<AutoSubmitOption> autoSubmitOption);

  public SummaryResponse fetchSummary(String conversationId);

  public List<SummaryResponse> fetchSummaries(List<String> conversationIds);

  public void handleConversationEvent(ConversationEventEnvelope conversationEventEnvelope,
      final long eventStartTime);

  SummaryDoc createGenAiSummary(final String conversationId, final String tenantId,
      Optional<String> experienceIdOpt, boolean formatEnable,final Optional<String> callType);

  List<SummaryUpdateEventLog> fetchSummaryEventLog(final String conversationId);

  SummaryReportResponse getSummaryReportByConversationId(String conversationId);

  MessageResponse submitReviewedIntentSummaries(String conversationId,
      IntentSummaryReviewedRequest intentSummaryReviewedRequest);

  String saveAgentStep(String convId, String text);

  boolean deleteAgentStep(String convId, String id);

  boolean updateAgentStep(String convId, String id, String text);

  List<SummaryResponse> fetchSummaryBySessionIds(List<String> sessionIds);

  String[] getConvIdAndExpIdByExtId(final String extConversationId);

  ResponseEntity<Map<String, Object>> updateSummaryTemplate(final SummaryTemplate summaryTemplate);

  GenAiSummaryDoc createGenAiSummaryIndependent(String conversationId, String templateId,int version);
}
