package com.uniphore.uassist.summary.controller;

import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.ri.common.utils.StringUtil;
import com.uniphore.ri.common.validation.OutputProcessor;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.event.pojo.EventMetricsResponse;
import com.uniphore.uassist.summary.event.service.ConversationEventQueueManager;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.*;
import com.uniphore.uassist.summary.service.SummaryRuntimeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.*;

import static com.uniphore.featureflag.LaunchDarklyConstants.FF_CONTEXT_KIND;

@RestController
@Validated
@Slf4j
public class SummaryRuntimeController {

  @Autowired
  private SummaryRuntimeService summaryRuntimeService;

  @Autowired
  private ConversationEventQueueManager conversationEventQueueManager;

  @Autowired
  private LaunchDarklyHelper ldHelper;

  @PutMapping(path = "/v1/agent-notes", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> saveAgentNotes(
      @RequestBody @Valid final AgentRequestPayload agentNotes,
      @RequestAttribute(Constants.X_USERNAME) final String userName) throws Exception {
    if (StringUtil.isTrulyEmpty(agentNotes.getConversationId())) {
      throw new CustomException(ErrorCodes.CONVERSATION_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
    }
    agentNotes.setAgentId(userName);
    MessageResponse response = summaryRuntimeService.saveAgentNotes(agentNotes);
    if (!Constants.SAVE.equals(response.getMessage())) {
      return new ResponseEntity<>(response.getMessage(), HttpStatus.BAD_REQUEST);
    }
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PutMapping(path = "/v1/agent-edits", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> saveAgentEdits(
      @RequestBody @Valid final AgentRequestPayload agentRequestPayload,
      @RequestAttribute(Constants.X_USERNAME) final String userName) throws Exception {
    if ((agentRequestPayload.getConversationId() == null || agentRequestPayload.getConversationId()
        .isEmpty()) && (agentRequestPayload.getExtConversationId() == null
        || agentRequestPayload.getExtConversationId().isEmpty())) {
      throw new CustomException(ErrorCodes.CONVERSATION_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
    }
    agentRequestPayload.setAgentId(userName);
    return new ResponseEntity<>(summaryRuntimeService.saveAgentEdits(agentRequestPayload),
        HttpStatus.OK);
  }

  @PostMapping(path = "/v1/agent/intent", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> addIntent(
      @RequestBody @Valid final AgentRequestPayload agentRequestPayload,
      @RequestAttribute(Constants.X_USERNAME) final String userName) throws Exception {
    if (agentRequestPayload.getConversationId() == null || agentRequestPayload.getConversationId()
        .isEmpty()) {
      throw new CustomException(ErrorCodes.CONVERSATION_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
    }
    agentRequestPayload.setAgentId(userName);
    return new ResponseEntity<>(summaryRuntimeService.addIntent(agentRequestPayload),
        HttpStatus.OK);
  }

  @DeleteMapping(path = "/v1/agent/intent/{instanceId}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> deleteIntent(@PathVariable("instanceId") @NotNull(
      message = ErrorCodes.INSTANCE_ID_NOT_EMPTY) final String instanceId,
      @RequestParam("conversationId") @NotEmpty(
          message = ErrorCodes.CONVERSATION_ID_NOT_EMPTY) final String conversationId,
      @RequestAttribute(Constants.X_USERNAME) final String userName) throws Exception {
    AgentRequestPayload agentRequestPayload = new AgentRequestPayload();
    agentRequestPayload.setConversationId(conversationId);
    agentRequestPayload.setInstanceId(instanceId);
    agentRequestPayload.setAgentId(userName);
    return new ResponseEntity<>(summaryRuntimeService.deleteIntent(agentRequestPayload),
        HttpStatus.OK);
  }

  @PostMapping(path = "/v1/agent/summary-reviewed", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> summaryReviewed(@RequestParam("conversationId") @NotEmpty(
          message = ErrorCodes.CONVERSATION_ID_NOT_EMPTY) final String conversationId,
      @RequestParam(value = "autosubmit", required = false) final Optional<Boolean> autosubmit,
      @RequestParam(value = "forcedsubmit", required = false) final Optional<Boolean> forcedsubmit,
      @RequestParam(value = "autoSubmitOption",
          required = false) final Optional<AutoSubmitOption> autoSubmitOption) {
    try {
      if (StringUtil.isTrulyEmpty(conversationId)) {
        return new ResponseEntity<>("Conversation ID is null/empty/undefined",
            HttpStatus.BAD_REQUEST);
      }
      log.debug("Submit summary = IsAutobumit:{}" + autosubmit);
      final MessageResponse messageResponse =
          summaryRuntimeService.summaryReviewed(conversationId, autosubmit,forcedsubmit,autoSubmitOption);
      if (Constants.SUCCESS.equalsIgnoreCase(messageResponse.getMessage())) {
        return new ResponseEntity<>(messageResponse, HttpStatus.OK);
      } else {
        return new ResponseEntity<>(messageResponse, HttpStatus.BAD_REQUEST);
      }
    } catch (CustomException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
    } catch (Exception e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping(path = "/v1/summaries")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> fetchSummary(@RequestParam("conversationId") @NotEmpty(
      message = ErrorCodes.CONVERSATION_ID_NOT_EMPTY) final String conversationId) {
    try {
      SummaryResponse summaryResponse = this.summaryRuntimeService.fetchSummary(conversationId);
      return new ResponseEntity<>(summaryResponse, HttpStatus.OK);
    } catch (CustomException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.NO_CONTENT);
    } catch (Exception e) {
      log.error("Error while fetching summaries", e);
      return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @PostMapping(path = "/v1/summaries-list")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> fetchSummaries(@NotEmpty @RequestBody List<String> conversationIds) {
    try {
        List<SummaryResponse> summaryResponses = this.summaryRuntimeService.fetchSummaries(conversationIds);
        return ResponseEntity.ok(summaryResponses);
    } catch (CustomException e) {
      return ResponseEntity.noContent().build();
    } catch (Exception e) {
      log.error("Error while fetching summaries", e);
      return ResponseEntity.internalServerError().body(e.getMessage());
    }
  }

  @GetMapping(path = "/v1/event-logs")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> fetchSummaryEventLog(@RequestParam("conversationId") @NotEmpty(
      message = ErrorCodes.CONVERSATION_ID_NOT_EMPTY) final String conversationId) {
    return new ResponseEntity<>(this.summaryRuntimeService.fetchSummaryEventLog(conversationId),
        HttpStatus.OK);
  }

  @GetMapping(path = "/v1/gen-ai-summaries")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Map<String, GenAiSummaryDoc>> fetchGenAiSummary(
      @RequestParam("conversation_id") final Optional<String> conversationId,
      @RequestParam("experience_id") final Optional<String> experienceId,
      @RequestParam("session_id") final Optional<String> extConversationId,
      @RequestParam(value = "call_type", required = false) final Optional<String> callType,
      @RequestParam(value = "format_enable", defaultValue = "false") final boolean formatEnable) {

    String[] convAndExpId =
        this.normalizeConvAndExpId(conversationId.orElse(null), experienceId.orElse(null),
            extConversationId);

    Map<String, GenAiSummaryDoc> responseMap = new HashMap<>();
    SummaryDoc genAiSummaryDoc = this.summaryRuntimeService.createGenAiSummary(convAndExpId[0],
        TenantUtils.getTenantDetails().getTenantId(), Optional.of(convAndExpId[1]), formatEnable,
        callType);
    if (genAiSummaryDoc == null || genAiSummaryDoc.getGenAiSummary() == null) {
      responseMap.put(Constants.SUMMARY,
          GenAiSummaryDoc.builder().sections(new ArrayList<>()).build());
    } else {
      responseMap.put(Constants.SUMMARY, genAiSummaryDoc.getGenAiSummary());
    }
    return new ResponseEntity<>(responseMap, HttpStatus.OK);
  }

  @GetMapping(path = "/v1/generate-summary")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Map<String, GenAiSummaryDoc>> GenerateSummary(
      @RequestParam("conversation_id") final String conversationId,
      @RequestParam("template_id") final String template_id,
   @RequestParam("version") final Integer version) {
    Map<String, GenAiSummaryDoc> responseMap = new HashMap<>();
    try{
      GenAiSummaryDoc genAiSummaryDoc = this.summaryRuntimeService.createGenAiSummaryIndependent(conversationId,template_id,version);
      if (genAiSummaryDoc == null) {
        responseMap.put(Constants.SUMMARY,
            GenAiSummaryDoc.builder().sections(new ArrayList<>()).build());
      } else {
        responseMap.put(Constants.SUMMARY, genAiSummaryDoc);
      }
    }catch (Exception e) {
      log.error("Error while generating template summary api", e);
      responseMap.put(Constants.SUMMARY,
          GenAiSummaryDoc.builder().sections(new ArrayList<>()).build());
    }
    return new ResponseEntity<>(responseMap, HttpStatus.OK);
  }

  @GetMapping(path = "/v1/event-metrics")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<EventMetricsResponse> fetchProcessMetrics() {
    return new ResponseEntity<>(this.conversationEventQueueManager.getMetrics(), HttpStatus.OK);
  }

  @GetMapping(path = "/v1/summary-report")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<SummaryReportResponse> fetchSummaryOutline(
      @RequestParam("conversationId") @NotEmpty(
          message = ErrorCodes.CONVERSATION_ID_NOT_EMPTY) final String conversationId) {
    return new ResponseEntity<>(
        this.summaryRuntimeService.getSummaryReportByConversationId(conversationId), HttpStatus.OK);
  }

  @PutMapping(path = "/v1/agent/intents-reviewed/{conversationId}")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Object> intentReviewed(@PathVariable("conversationId") @NotEmpty(
      message = ErrorCodes.CONVERSATION_ID_NOT_EMPTY) final String conversationId,
      @RequestBody final IntentSummaryReviewedRequest intentSummaryReviewedRequest) {
    try {
      return new ResponseEntity<>(
          this.summaryRuntimeService.submitReviewedIntentSummaries(conversationId,
              intentSummaryReviewedRequest), HttpStatus.OK);
    } catch (Exception e) {
      log.error("added error log for intentReviewed: convId: {}, error: {}", e);
      return new ResponseEntity<>(
          new MessageResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.name()),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @PostMapping(path = "/v1/agent/agent-steps/{conversationId}")
  public ResponseEntity<Map<String, Object>> addAgentStepManual(
      @PathVariable("conversationId") final String convId,
      @RequestParam("text") final String text) {
    String id = this.summaryRuntimeService.saveAgentStep(convId, text);
    log.info("added agent step with id {} to conversation id {}", id, convId);
    return ResponseEntity.ok(Collections.singletonMap(Constants.DATA, id));
  }

  @DeleteMapping(path = "/v1/agent/agent-steps/{conversationId}")
  public ResponseEntity<Map<String, Object>> deleteAgentStepManual(
      @PathVariable("conversationId") final String convId, @RequestParam("id") final String id) {
    boolean deleted = this.summaryRuntimeService.deleteAgentStep(convId, id);
    if (deleted) {
      log.info("deleted agent step with id {} for conversation id {}", id, convId);
      return ResponseEntity.ok(Collections.singletonMap(Constants.DATA, id));
    }

    logAgentStepNotFound(convId, id);
    return ResponseEntity.badRequest().body(Collections.singletonMap(Constants.DATA,
        String.format("Agent step with id %s not found", id)));
  }

  @PutMapping(path = "/v1/agent/agent-steps/{conversationId}")
  public ResponseEntity<Map<String, Object>> editAgentStepsManual(
      @PathVariable("conversationId") final String convId, @RequestParam("id") final String id,
      @RequestParam("text") final String text) {
    if (this.summaryRuntimeService.updateAgentStep(convId, id, text)) {
      return ResponseEntity.ok(Collections.singletonMap(Constants.DATA, id));
    }
    logAgentStepNotFound(convId, id);
    return ResponseEntity.badRequest().body(Collections.singletonMap(Constants.DATA,
        String.format("Agent step with id %s not found", id)));
  }

  private static void logAgentStepNotFound(String convId, String id) {
    log.info("failed to find agent step with id {} for conversation id {}", id, convId);
  }

  @GetMapping(path = "/v1/summary")
  public ResponseEntity<Map<String, Object>> getSummariesBySessionId(
      @RequestParam("sessionIds") @NotEmpty(
          message = ErrorCodes.CONVERSATION_ID_NOT_EMPTY) final List<String> sessionIds) {

    if (log.isDebugEnabled()) {
      log.debug("summaries requested for session ids: {}", sessionIds);
    } else {
      log.info("summaries requested for {} session ids", sessionIds.size());
    }
    List<SummaryResponse> summaries =
        this.summaryRuntimeService.fetchSummaryBySessionIds(sessionIds);
    if (summaries == null || summaries.isEmpty()) {
      if (log.isDebugEnabled()) {
        log.debug("No summaries found for session ids {}", sessionIds);
      }
      return ResponseEntity.badRequest().body(
          Collections.singletonMap(Constants.ERROR, "No summary found for provided session ids!"));
    }
    if (log.isDebugEnabled()) {
      log.debug("Found {} summaries for session ids", summaries.size());
    }
    return ResponseEntity.ok(Collections.singletonMap(Constants.DATA, summaries));
  }

  private String[] normalizeConvAndExpId(final String conversationId, final String experienceId,
      final Optional<String> extConversationId) {
    if ((conversationId == null || conversationId.isEmpty()) && (experienceId == null
        || experienceId.isEmpty())) {
      // if no params are provided, then throw an error.
      if (!extConversationId.isPresent()) {
        throw new CustomException(ErrorCodes.CONVERSATION_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
      }
      // convert session id to conversation and experience id.
      String[] result =
          this.summaryRuntimeService.getConvIdAndExpIdByExtId(extConversationId.get());
      if (null == result || result.length != 2) {
        // result should exist and have exactly 2 values, else it is wrong.
        throw new CustomException(ErrorCodes.CONVERSATION_NOT_FOUND, HttpStatus.BAD_REQUEST);
      }
      return result;
    } else if (!extConversationId.isPresent()) {
      if (conversationId == null || conversationId.isEmpty()) {
        throw new CustomException(ErrorCodes.CONVERSATION_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
      } else if (experienceId == null || experienceId.isEmpty()) {
        throw new CustomException(ErrorCodes.EXPERIENCE_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
      }
      // if both are present, set the data.
      return new String[] {conversationId, experienceId};
    } else {
      // fallback case where data is present for conv and exp ids
      return new String[] {conversationId, experienceId};
    }
  }

  @PatchMapping(path = "/v1/conversations/{conversationId}/summary-template")
  public ResponseEntity<Map<String, Object>> updateSummaryTemplate(
      @PathVariable(name = "conversationId") @NotBlank(
          message = ErrorCodes.CONVERSATION_ID_EMPTY) String conversationId,
      @RequestBody @Valid SummaryTemplate request) {
    if (!this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
        FF_CONTEXT_KIND)) {
      log.info(
          "NG template based GEN-AI summary generation feature is disabled for tenantId: {}, request: {}",
          TenantUtils.getTenantDetails().getTenantId(), request);
      return OutputProcessor.failureUIResponse(HttpStatus.FORBIDDEN,
          "NG template based GEN-AI summary generation feature is disabled");
    }
    request.setConversationId(conversationId);
    log.info("Request received to updateSummaryTemplate: {}", request);
    return summaryRuntimeService.updateSummaryTemplate(request);
  }
}
